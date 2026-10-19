package org.fungalsentinel.app.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class FrameProfile(
    val width: Int, val height: Int,
    val redProfile: FloatArray, val greenProfile: FloatArray, val blueProfile: FloatArray,
    val columnEnergy: FloatArray, val exposureSeconds: Double, val sourceKind: String,
    val xRoiStart: Int, val xRoiEnd: Int
)

/**
 * DNG/RAW parity with Python image_processing.read_dng_linear_rgb.
 * - recursive SubIFD parsing of the CFA pattern (Tag 33422), supports 0-based / 1-based encoding
 * - reads both file:// and content:// URIs
 * - forceRbSwap: set by the ViewModel once Step 1 detects swapped R/B labels; the swap happens at the decode source so Step 2/3 labels stay consistent
 */
object FrameProfileExtractor {
    private const val MAX_FILE_BYTES = 250_000_000
    private const val MAX_PIXELS = 120_000_000L
    private const val DEFAULT_BLACK_LEVEL = 64.0f

    // most phones (Redmi, Samsung, Huawei, OnePlus) use RGGB for the main camera: 0=R, 1=G, 2=B
    private val DEFAULT_CFA = intArrayOf(0, 1, 1, 2)

    /** [v2.2.6] R/B label swap switch (conclusion of the Step 1 reverse-order self-check) */
    @Volatile
    var forceRbSwap: Boolean = false

    private class TopK(private val k: Int) {
        val v = FloatArray(k) { -Float.MAX_VALUE }
        private var filled = 0
        fun add(s: Float) {
            if (filled < k) {
                var i = filled++
                v[i] = s
                while (i > 0 && v[i - 1] < v[i]) { val t = v[i - 1]; v[i - 1] = v[i]; v[i] = t; i-- }
            } else if (s > v[k - 1]) {
                var i = k - 1
                v[i] = s
                while (i > 0 && v[i - 1] < v[i]) { val t = v[i - 1]; v[i - 1] = v[i]; v[i] = t; i-- }
            }
        }
        fun p995(n: Int): Float {
            if (filled == 0) return 0f
            return v[minOf(k - 1, (0.005 * n).toInt())]
        }
    }

    fun extract(
        context: Context,
        uri: Uri,
        fixedXRegion: Pair<Int, Int>? = null
    ): FrameProfile? = runCatching {
        val bytes: ByteArray? = when (uri.scheme?.lowercase()) {
            "file" -> {
                val f = File(uri.path ?: return null)
                if (f.exists() && f.length() in 64..MAX_FILE_BYTES) f.readBytes() else null
            }
            "content" -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            else -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
        if (bytes == null || bytes.size < 64 || bytes.size > MAX_FILE_BYTES) return null
        val exifExposure = readExposure(bytes)
        val p = profileFromDng(bytes, exifExposure, fixedXRegion)
            ?: profileFromBitmap(bytes, exifExposure, fixedXRegion)
        // [v2.2.6] swap R/B at the source so channel labels stay consistent along the whole pipeline
        p?.let { if (forceRbSwap) it.copy(redProfile = it.blueProfile, blueProfile = it.redProfile) else it }
    }.getOrNull()

    private fun readExposure(bytes: ByteArray): Double = runCatching {
        ExifInterface(ByteArrayInputStream(bytes))
            .getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull() ?: 0.0
    }.getOrDefault(0.0)

    private fun srgbToLinear(v8: Int): Float {
        val c = v8 / 255.0
        return (if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)).toFloat()
    }

    /** Deprecated: the direction is derived from the sign of the wavelength mapping slope, so sample colour cannot bias the centroid decision */
    private fun autoRotateRgb(rgb: FloatArray, w: Int, h: Int): Triple<FloatArray, Int, Int> =
        Triple(rgb, w, h)

    private fun centerRoi(width: Int): Pair<Int, Int> =
        (width * 0.35).toInt().coerceIn(0, width - 1) to
                (width * 0.65).toInt().coerceIn(0, width - 1)

    /**
     * [v2.2.6] content-dependent automatic ROI detection (the key background of DEF-A02).
     *
     * This function locates the spectral band from the peak structure of the **column energy profile**, so it **strongly depends on frame content**:
     * a positioning frame is a bright LED spectrum (high SNR, sharp peaks) while a sample frame is weak fluorescence (low SNR,
     * a flat peak that may even be buried in noise). On the same device with the same optical path the two yield **different ROIs**.
     *
     * Once the ROI differs, the row profile is averaged over a different set of pixel columns and no longer matches the response curve
     * stored in the preset -- that is the quantitative consequence of "the ROI is displayed but has no effect".
     *
     * Therefore: whenever a preset (or the caller) supplies an ROI we **must** take the
     * `fixedXRegion != null` branch of [resolveXRoi] and must never fall back to re-detecting here.
     */
    private fun autoDetectXRoi(colProfile: FloatArray, width: Int): Pair<Int, Int> {
        if (width <= 0) return 0 to 0
        if (colProfile.isEmpty()) return centerRoi(width)
        val windowSize = max(11, width / 300 * 2 + 1)
        val smoothed = movingAverage(colProfile, windowSize)
        val sorted = smoothed.sorted()
        val base = sorted[(sorted.size * 0.20).toInt().coerceAtMost(sorted.lastIndex)]
        val peak = smoothed.maxOrNull() ?: return centerRoi(width)
        if (peak <= base) return centerRoi(width)
        val threshold = base + 0.35f * (peak - base)
        var start: Int? = null
        val segments = mutableListOf<Pair<Int, Int>>()
        for (i in smoothed.indices) {
            if (smoothed[i] > threshold && start == null) start = i
            if ((smoothed[i] <= threshold || i == smoothed.lastIndex) && start != null) {
                segments.add(start to if (smoothed[i] <= threshold) i else i + 1)
                start = null
            }
        }
        if (segments.isEmpty()) return centerRoi(width)
        val longest = segments.maxByOrNull { it.second - it.first } ?: return centerRoi(width)
        return max(0, longest.first - 80) to min(width - 1, longest.second - 1 + 80)
    }

    private fun resolveXRoi(
        colProfile: FloatArray,
        width: Int,
        fixedXRegion: Pair<Int, Int>?
    ): Pair<Int, Int> {
        if (fixedXRegion != null && width > 0) {
            val start = fixedXRegion.first.coerceIn(0, width - 1)
            val end = fixedXRegion.second.coerceIn(0, width - 1)
            if (start <= end) return start to end
        }
        return autoDetectXRoi(colProfile, width)
    }

    private fun movingAverage(x: FloatArray, window: Int): FloatArray {
        if (window <= 1) return x.copyOf()
        val w = if (window % 2 == 0) window + 1 else window
        val half = w / 2
        val padded = FloatArray(x.size + 2 * half)
        for (i in 0 until half) padded[i] = x[0]
        for (i in x.indices) padded[half + i] = x[i]
        for (i in 0 until half) padded[half + x.size + i] = x[x.lastIndex]
        val out = FloatArray(x.size)
        for (i in x.indices) {
            var sum = 0f
            for (j in 0 until w) sum += padded[i + j]
            out[i] = sum / w
        }
        return out
    }

    private fun profileFromBitmap(
        bytes: ByteArray,
        exposure: Double,
        fixedXRegion: Pair<Int, Int>?
    ): FrameProfile? = runCatching {
        val decoded: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { d, _, _ ->
                d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
            }
        } else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        decoded ?: return null
        val bm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && decoded.config == Bitmap.Config.HARDWARE) {
            val soft = decoded.copy(Bitmap.Config.ARGB_8888, false)
            decoded.recycle()
            soft
        } else decoded
        val w = bm.width; val h = bm.height
        if (w <= 0 || h <= 0 || w.toLong() * h > MAX_PIXELS) { bm.recycle(); return null }
        val rgb0 = FloatArray(w * h * 3)
        val row = IntArray(w)
        for (y in 0 until h) {
            bm.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val c = row[x]
                val i3 = (y * w + x) * 3
                rgb0[i3] = srgbToLinear((c shr 16) and 0xFF)
                rgb0[i3 + 1] = srgbToLinear((c shr 8) and 0xFF)
                rgb0[i3 + 2] = srgbToLinear(c and 0xFF)
            }
        }
        bm.recycle()
        val (rgb, finalW, finalH) = autoRotateRgb(rgb0, w, h)
        val kTop = maxOf(4, (finalH * 0.005).toInt() + 4)
        val tops = Array(finalW) { TopK(kTop) }
        for (y in 0 until finalH) {
            val base = y * finalW
            for (x in 0 until finalW) {
                val i3 = (base + x) * 3
                tops[x].add(rgb[i3] + rgb[i3 + 1] + rgb[i3 + 2])
            }
        }
        val colE = FloatArray(finalW) { x -> tops[x].p995(finalH) }
        val (rs, re) = resolveXRoi(colE, finalW, fixedXRegion)
        val roiW = (re - rs + 1).coerceAtLeast(1)
        val rP = FloatArray(finalH); val gP = FloatArray(finalH); val bP = FloatArray(finalH)
        for (y in 0 until finalH) {
            val base = y * finalW
            for (x in rs..re) {
                val i3 = (base + x) * 3
                rP[y] += rgb[i3]; gP[y] += rgb[i3 + 1]; bP[y] += rgb[i3 + 2]
            }
            rP[y] /= roiW; gP[y] /= roiW; bP[y] /= roiW
        }
        val redPeakIdxBmp = rP.indices.maxByOrNull { rP[it] } ?: (finalH / 2)
        val peakValRBmp = rP[redPeakIdxBmp]
        if (peakValRBmp > 0f) {
            val sigma = 25.0
            for (y in 0 until finalH) {
                val d = (y - redPeakIdxBmp).toDouble()
                val gauss = exp(-0.5 * (d * d) / (sigma * sigma)).toFloat()
                gP[y] = max(0f, gP[y] - 0.7f * peakValRBmp * gauss)
            }
        }
        FrameProfile(finalW, finalH, rP, gP, bP, colE, exposure, "bitmap", rs, re)
    }.getOrNull()

    internal data class RawGrid(
        val width: Int, val height: Int, val samples: ShortArray,
        val blackLevel: Float, val tiffExposure: Double, val cfa: IntArray
    )

    private fun profileFromDng(
        bytes: ByteArray,
        fallbackExposure: Double,
        fixedXRegion: Pair<Int, Int>?
    ): FrameProfile? = runCatching {
        val grid = TiffReader(bytes).readRaw() ?: return null
        val exp = when {
            grid.tiffExposure > 0.0 -> grid.tiffExposure
            fallbackExposure > 0.0 -> fallbackExposure
            else -> 1.0
        }
        val black = grid.blackLevel
        val cfa = grid.cfa
        val w = grid.width; val h = grid.height
        val ef = exp.toFloat()

        // 1. Compute column energy (colE) directly from raw samples
        val kTop = maxOf(4, (h * 0.005).toInt() + 4)
        val tops = Array(w) { TopK(kTop) }
        for (y in 0 until h) {
            val base = y * w
            for (x in 0 until w) {
                val sample = grid.samples[base + x].toInt() and 0xFFFF
                tops[x].add(max(0f, sample.toFloat() - black) / ef)
            }
        }
        val colE = FloatArray(w) { x -> tops[x].p995(h) }
        val (rs, re) = resolveXRoi(colE, w, fixedXRegion)
        val roiW = (re - rs + 1).coerceAtLeast(1)

        // 2. Compute row profiles (rP, gP, bP) only within ROI columns [rs, re]
        val rP = FloatArray(h)
        val gP = FloatArray(h)
        val bP = FloatArray(h)

        for (y in 0 until h) {
            val base = y * w
            var rSum = 0f; var gSum = 0f; var bSum = 0f
            for (x in rs..re) {
                val sample = grid.samples[base + x].toInt() and 0xFFFF
                val rawVal = max(0f, sample.toFloat() - black) / ef
                val ci = cfa[(y and 1) * 2 + (x and 1)].coerceIn(0, 2)
                
                var r = 0f; var g = 0f; var b = 0f
                when (ci) {
                    0 -> {
                        r = rawVal
                        g = bilinearInterpolate(grid, x, y, w, h, black, ef, cfa, 1)
                        b = bilinearInterpolate(grid, x, y, w, h, black, ef, cfa, 2)
                    }
                    1 -> {
                        g = rawVal
                        r = bilinearInterpolate(grid, x, y, w, h, black, ef, cfa, 0)
                        b = bilinearInterpolate(grid, x, y, w, h, black, ef, cfa, 2)
                    }
                    2 -> {
                        b = rawVal
                        g = bilinearInterpolate(grid, x, y, w, h, black, ef, cfa, 1)
                        r = bilinearInterpolate(grid, x, y, w, h, black, ef, cfa, 0)
                    }
                }
                // removes bidirectional spectral crosstalk of the physical sensor filters (the false crosstalk peak of the green channel at red wavelengths, and green light leaking into the red channel)
                g = max(0f, g - 0.15f * r)
                r = max(0f, r - 0.12f * g)

                rSum += r; gSum += g; bSum += b
            }
            rP[y] = rSum / roiW
            gP[y] = gSum / roiW
            bP[y] = bSum / roiW
        }

        FrameProfile(w, h, rP, gP, bP, colE, exp, "dng", rs, re)
    }.getOrNull()

    private fun bilinearInterpolate(
        grid: RawGrid, x: Int, y: Int, w: Int, h: Int,
        black: Float, ef: Float, cfa: IntArray, targetChannel: Int
    ): Float {
        val ci = cfa[(y and 1) * 2 + (x and 1)].coerceIn(0, 2)
        if (ci == targetChannel) {
            val sample = grid.samples[y * w + x].toInt() and 0xFFFF
            return max(0f, sample.toFloat() - black) / ef
        }

        var sum = 0f; var cnt = 0

        when (targetChannel) {
            1 -> { // Green: 4 orthogonal neighbors (top, bottom, left, right)
                for ((dx, dy) in arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                    val nx = x + dx; val ny = y + dy
                    if (nx in 0 until w && ny in 0 until h) {
                        val nci = cfa[(ny and 1) * 2 + (nx and 1)].coerceIn(0, 2)
                        if (nci == 1) {
                            val s = grid.samples[ny * w + nx].toInt() and 0xFFFF
                            sum += max(0f, s.toFloat() - black) / ef
                            cnt++
                        }
                    }
                }
            }
            else -> { // Red or Blue: 4 diagonal neighbors first
                for ((dx, dy) in arrayOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)) {
                    val nx = x + dx; val ny = y + dy
                    if (nx in 0 until w && ny in 0 until h) {
                        val nci = cfa[(ny and 1) * 2 + (nx and 1)].coerceIn(0, 2)
                        if (nci == targetChannel) {
                            val s = grid.samples[ny * w + nx].toInt() and 0xFFFF
                            sum += max(0f, s.toFloat() - black) / ef
                            cnt++
                        }
                    }
                }
                if (cnt == 0) {
                    // Fallback to orthogonal if diagonal didn't hit
                    for ((dx, dy) in arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                        val nx = x + dx; val ny = y + dy
                        if (nx in 0 until w && ny in 0 until h) {
                            val nci = cfa[(ny and 1) * 2 + (nx and 1)].coerceIn(0, 2)
                            if (nci == targetChannel) {
                                val s = grid.samples[ny * w + nx].toInt() and 0xFFFF
                                sum += max(0f, s.toFloat() - black) / ef
                                cnt++
                            }
                        }
                    }
                }
            }
        }
        return if (cnt > 0) sum / cnt else 0f
    }

    internal class TiffReader(private val b: ByteArray) {
        private val order = if (b[0] == 0x49.toByte()) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN

        fun readRaw(): RawGrid? = runCatching {
            if (b.size < 8) return null
            if (b[0] != 0x49.toByte() && b[0] != 0x4D.toByte()) return null
            val bb = ByteBuffer.wrap(b).order(order)
            bb.position(2)
            if (bb.short.toInt() != 42) return null
            val off0 = bb.int.toInt()
            if (off0 !in 1 until b.size - 1) return null

            val tags = mutableMapOf<Int, Any?>()
            parseIfd(bb, off0, tags)

            val width = tags[256] as? Int ?: 0
            val height = tags[257] as? Int ?: 0
            val bits = tags[258] as? Int ?: 16
            val compression = tags[259] as? Int ?: 1
            val black = (tags[50714] as? Double ?: DEFAULT_BLACK_LEVEL.toDouble()).toFloat()
            val exposure = tags[33434] as? Double ?: 0.0
            val cfa = tags[33422] as? IntArray
            val offsets = tags[273] as? IntArray ?: IntArray(0)
            val counts = tags[279] as? IntArray ?: IntArray(0)

            if (width <= 0 || height <= 0 || compression != 1 || offsets.isEmpty()) return null
            if (width.toLong() * height > MAX_PIXELS) return null

            val totalBytes = offsets.indices.sumOf { s -> if (s < counts.size) counts[s].coerceAtLeast(0) else 0 }
            val data = ByteArray(totalBytes)
            var pos = 0
            for (s in offsets.indices) {
                val start = offsets[s]
                val len = if (s < counts.size) counts[s] else 0
                if (start < 0 || len <= 0 || start + len > b.size) continue
                val take = minOf(len, data.size - pos)
                if (take <= 0) break
                System.arraycopy(b, start, data, pos, take)
                pos += take
            }

            val samples = ShortArray(width * height)
            when (bits) {
                8 -> {
                    val cnt = minOf(samples.size, pos)
                    for (i in 0 until cnt) samples[i] = (data[i].toInt() and 0xFF).toShort()
                }
                16 -> {
                    val sb = ByteBuffer.wrap(data, 0, pos).order(order)
                    val cnt = minOf(samples.size, pos / 2)
                    for (i in 0 until cnt) samples[i] = sb.short
                }
                10, 12, 14 -> {
                    val rowBytes = (width.toLong() * bits + 7) / 8
                    val mask = (1 shl bits) - 1
                    for (ry in 0 until height) {
                        val rowStart = ry.toLong() * rowBytes
                        if (rowStart >= pos) break
                        var bitPos = 0L
                        for (x in 0 until width) {
                            val byteIdx = (rowStart + bitPos / 8).toInt()
                            val bitIdx = (bitPos % 8).toInt()
                            var v = 0
                            for (k in 0..2) {
                                val idx = byteIdx + k
                                if (idx >= pos) break
                                v = v or ((data[idx].toInt() and 0xFF) shl (8 * k))
                            }
                            samples[ry * width + x] = (((v ushr bitIdx) and mask)).toShort()
                            bitPos += bits
                        }
                    }
                }
                else -> return null
            }
            RawGrid(width, height, samples, black, exposure, cfa ?: DEFAULT_CFA)
        }.getOrNull()

        private fun parseIfd(bb: ByteBuffer, offset: Int, tags: MutableMap<Int, Any?>) {
            if (offset < 0 || offset + 2 > b.size) return
            bb.position(offset)
            if (bb.remaining() < 2) return
            val n = bb.short.toInt() and 0xFFFF
            if (n > 100 || bb.remaining() < n * 12) return
            for (i in 0 until n) {
                if (bb.remaining() < 12) break
                val tag = bb.short.toInt() and 0xFFFF
                val type = bb.short.toInt() and 0xFFFF
                val cnt = bb.int.toInt()
                val valPos = bb.position()
                val inline = bb.int.toInt()
                when (tag) {
                    256 -> tags[256] = firstVal(type, cnt, valPos, inline)
                    257 -> tags[257] = firstVal(type, cnt, valPos, inline)
                    258 -> tags[258] = firstVal(type, cnt, valPos, inline)
                    259 -> tags[259] = firstVal(type, cnt, valPos, inline)
                    273 -> tags[273] = manyVals(type, cnt, valPos, inline)
                    279 -> tags[279] = manyVals(type, cnt, valPos, inline)
                    50714 -> tags[50714] = readFirstRational(type, cnt, inline)
                    33434 -> tags[33434] = readFirstRational(type, cnt, inline)
                    33422 -> tags[33422] = cfaPattern(type, cnt, valPos, inline)
                    330 -> {
                        val subOffsets = manyVals(type, cnt, valPos, inline)
                        for (subOff in subOffsets) { if (subOff > 0) parseIfd(bb, subOff, tags) }
                    }
                }
            }
        }

        private fun cfaPattern(type: Int, cnt: Int, valPos: Int, inline: Int): IntArray? {
            if (cnt < 4) return null
            val elem = when (type) { 1, 2, 6, 7 -> 1; else -> 0 }
            if (elem == 0) return null
            val base = if (cnt <= 4) valPos else inline
            if (base < 0 || base + 4 > b.size) return null
            val raw = IntArray(4) { b[base + it].toInt() and 0xFF }
            val norm = IntArray(4)
            val minV = raw.min(); val maxV = raw.max()
            when {
                minV == 0 && maxV == 2 -> for (i in 0..3) norm[i] = raw[i]
                minV == 1 && maxV == 3 -> for (i in 0..3) norm[i] = raw[i] - 1
                else -> return null
            }
            val sorted = norm.sorted()
            if (sorted[0] != 0 || sorted[1] != 1 || sorted[2] != 1 || sorted[3] != 2) return null
            return norm
        }

        private fun readFirstRational(type: Int, cnt: Int, inline: Int): Double {
            if (cnt < 1) return 0.0
            return when (type) {
                5 -> {
                    if (inline < 0 || inline + 8 > b.size) return 0.0
                    val sb = ByteBuffer.wrap(b, inline, 8).order(order)
                    val num = sb.int.toLong() and 0xFFFFFFFFL
                    val den = sb.int.toLong() and 0xFFFFFFFFL
                    if (den != 0L) num.toDouble() / den.toDouble() else 0.0
                }
                10 -> {
                    if (inline < 0 || inline + 8 > b.size) return 0.0
                    val sb = ByteBuffer.wrap(b, inline, 8).order(order)
                    val num = sb.int.toDouble(); val den = sb.int.toDouble()
                    if (den != 0.0) num / den else 0.0
                }
                3 -> (inline and 0xFFFF).toDouble()
                4 -> (inline.toLong() and 0xFFFFFFFFL).toDouble()
                1 -> (inline and 0xFF).toDouble()
                else -> 0.0
            }
        }

        private fun firstVal(type: Int, cnt: Int, valPos: Int, inline: Int): Int =
            manyVals(type, cnt, valPos, inline).firstOrNull() ?: inline

        private fun manyVals(type: Int, cnt: Int, valPos: Int, inline: Int): IntArray {
            val elem = when (type) { 3 -> 2; 4, 13 -> 4; 1, 2 -> 1; else -> 0 }
            if (elem == 0 || cnt <= 0 || cnt > 1_000_000) return IntArray(0)
            val total = elem * cnt
            val base: Int; val size: Int
            if (total <= 4) {
                if (valPos < 0 || valPos + 4 > b.size) return IntArray(0)
                base = valPos; size = 4
            } else {
                if (inline < 0 || inline + total > b.size) return IntArray(0)
                base = inline; size = total
            }
            val sb = ByteBuffer.wrap(b, base, size).order(order)
            return IntArray(cnt) {
                when (type) { 3 -> sb.short.toInt() and 0xFFFF; 4, 13 -> sb.int; else -> sb.get().toInt() and 0xFF }
            }
        }
    }
}
