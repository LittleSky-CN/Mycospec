package org.fungalsentinel.app.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

data class FrameProfile(
    val width: Int, val height: Int,
    val redProfile: FloatArray, val greenProfile: FloatArray, val blueProfile: FloatArray,
    val columnEnergy: FloatArray, val exposureSeconds: Double, val sourceKind: String,
    val xRoiStart: Int, val xRoiEnd: Int
)

/**
 * Extracts 1-D channel profiles (dispersion axis = Y) from JPEG/PNG or uncompressed DNG.
 * Aligned with Python image_processing.py v1.4.3:
 * - Black level subtraction (64.0) BEFORE exposure normalization
 * - 99.5th percentile X-ROI detection with moving-average smoothing
 * - ROI column MEAN (not sum) to preserve spectral resolution
 * - Bilinear demosaicing for DNG (approximating cv2.COLOR_BayerRG2RGB)
 */
object FrameProfileExtractor {
    private const val MAX_FILE_BYTES = 80_000_000
    private const val MAX_PIXELS = 40_000_000L
    private const val BLACK_LEVEL = 64.0f

    fun extract(context: Context, uri: Uri): FrameProfile? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.size < 64 || bytes.size > MAX_FILE_BYTES) return null
        val exposure = readExposure(bytes)
        profileFromBitmap(bytes, exposure) ?: profileFromDng(bytes, exposure)
    }.getOrNull()

    private fun readExposure(bytes: ByteArray): Double = runCatching {
        android.media.ExifInterface(ByteArrayInputStream(bytes))
            .getAttribute(android.media.ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull() ?: 1.0
    }.getOrDefault(1.0)

    /** 99.5th percentile X-ROI detection (Python: auto_detect_x_roi) */
    private fun autoDetectXRoi(colProfile: FloatArray, width: Int): Pair<Int, Int> {
        if (colProfile.isEmpty()) return (width * 0.35).toInt() to (width * 0.65).toInt()
        val windowSize = max(11, width / 300 * 2 + 1)
        val smoothed = movingAverage(colProfile, windowSize)
        val sorted = smoothed.sorted()
        val base = sorted[(sorted.size * 0.20).toInt().coerceAtMost(sorted.lastIndex)]
        val peak = smoothed.maxOrNull() ?: return (width * 0.35).toInt() to (width * 0.65).toInt()
        if (peak <= base) return (width * 0.35).toInt() to (width * 0.65).toInt()
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
        if (segments.isEmpty()) return (width * 0.35).toInt() to (width * 0.65).toInt()
        val longest = segments.maxByOrNull { it.second - it.first } ?: return (width * 0.35).toInt() to (width * 0.65).toInt()
        return max(0, longest.first - 80) to min(width, longest.second + 80)
    }

    private fun movingAverage(x: FloatArray, window: Int): FloatArray {
        if (window <= 1) return x.copyOf()
        val w = if (window % 2 == 0) window + 1 else window
        val half = w / 2
        val padded = FloatArray(x.size + 2 * half)
        for (i in 0 until half) padded[i] = x[0]
        for (i in 0 until x.size) padded[half + i] = x[i]
        for (i in 0 until half) padded[half + x.size + i] = x[x.lastIndex]
        val kernel = FloatArray(w) { 1f / w }
        val out = FloatArray(x.size)
        for (i in 0 until x.size) {
            var sum = 0f
            for (j in 0 until w) sum += padded[i + j] * kernel[j]
            out[i] = sum
        }
        return out
    }

    private fun profileFromBitmap(bytes: ByteArray, exposure: Double): FrameProfile? = runCatching {
        val decoded: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { d, _, _ ->
                d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
            }
        } else {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        decoded ?: return null
        val bm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            decoded.config == Bitmap.Config.HARDWARE
        ) {
            val soft = decoded.copy(Bitmap.Config.ARGB_8888, false)
            decoded.recycle()
            soft
        } else decoded
        val w = bm.width; val h = bm.height
        if (w <= 0 || h <= 0 || w.toLong() * h > MAX_PIXELS) { bm.recycle(); return null }

        // Pass 1: column energy for ROI detection (with black level subtraction)
        val colE = FloatArray(w)
        val row = IntArray(w)
        for (y in 0 until h) {
            bm.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val c = row[x]
                val r = ((c shr 16) and 0xFF).toFloat() - BLACK_LEVEL
                val g = ((c shr 8) and 0xFF).toFloat() - BLACK_LEVEL
                val b = (c and 0xFF).toFloat() - BLACK_LEVEL
                colE[x] += max(0f, r) + max(0f, g) + max(0f, b)
            }
        }
        val (rs, re) = autoDetectXRoi(colE, w)
        val roiW = (re - rs + 1).coerceAtLeast(1)

        // Pass 2: ROI-restricted column MEAN per channel (Python: mean(axis=1))
        val rP = FloatArray(h); val gP = FloatArray(h); val bP = FloatArray(h)
        for (y in 0 until h) {
            bm.getPixels(row, 0, w, 0, y, w, 1)
            for (x in rs..re) {
                val c = row[x]
                val r = ((c shr 16) and 0xFF).toFloat() - BLACK_LEVEL
                val g = ((c shr 8) and 0xFF).toFloat() - BLACK_LEVEL
                val b = (c and 0xFF).toFloat() - BLACK_LEVEL
                rP[y] += max(0f, r)
                gP[y] += max(0f, g)
                bP[y] += max(0f, b)
            }
            val expNorm = if (exposure > 0) exposure.toFloat() else 1f
            rP[y] = (rP[y] / roiW) / expNorm
            gP[y] = (gP[y] / roiW) / expNorm
            bP[y] = (bP[y] / roiW) / expNorm
        }
        bm.recycle()
        FrameProfile(w, h, rP, gP, bP, colE, exposure, "bitmap", rs, re)
    }.getOrNull()

    private data class RawGrid(val width: Int, val height: Int, val samples: IntArray)

    private fun profileFromDng(bytes: ByteArray, exposure: Double): FrameProfile? = runCatching {
        val grid = TiffReader(bytes).readRaw() ?: return null
        // Bilinear demosaicing (approximating cv2.COLOR_BayerRG2RGB)
        val rgb = demosaicBilinear(grid)
        val w = grid.width; val h = grid.height

        // Column energy for ROI detection
        val colE = FloatArray(w)
        for (y in 0 until h) {
            val base = y * w
            for (x in 0 until w) {
                val idx = base * 3 + x * 3
                colE[x] += max(0f, rgb[idx] - BLACK_LEVEL) +
                        max(0f, rgb[idx + 1] - BLACK_LEVEL) +
                        max(0f, rgb[idx + 2] - BLACK_LEVEL)
            }
        }
        val (rs, re) = autoDetectXRoi(colE, w)
        val roiW = (re - rs + 1).coerceAtLeast(1)

        // ROI-restricted column MEAN per channel
        val rP = FloatArray(h); val gP = FloatArray(h); val bP = FloatArray(h)
        val expNorm = if (exposure > 0) exposure.toFloat() else 1f
        for (y in 0 until h) {
            val base = y * w
            for (x in rs..re) {
                val idx = base * 3 + x * 3
                rP[y] += max(0f, rgb[idx] - BLACK_LEVEL)
                gP[y] += max(0f, rgb[idx + 1] - BLACK_LEVEL)
                bP[y] += max(0f, rgb[idx + 2] - BLACK_LEVEL)
            }
            rP[y] = (rP[y] / roiW) / expNorm
            gP[y] = (gP[y] / roiW) / expNorm
            bP[y] = (bP[y] / roiW) / expNorm
        }
        FrameProfile(w, h, rP, gP, bP, colE, exposure, "dng", rs, re)
    }.getOrNull()

    /** Simple bilinear demosaicing for RGGB Bayer pattern */
    private fun demosaicBilinear(grid: RawGrid): FloatArray {
        val w = grid.width; val h = grid.height
        val rgb = FloatArray(w * h * 3)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = (y * w + x) * 3
                val ry = y and 1; val cx = x and 1
                // RGGB: (0,0)=R, (0,1)=G, (1,0)=G, (1,1)=B
                val rawVal = grid.samples[y * w + x].toFloat()
                when {
                    ry == 0 && cx == 0 -> { // R site
                        rgb[idx] = rawVal
                        rgb[idx + 1] = avgNeighbors(grid, x, y, w, h, isGreen = true)
                        rgb[idx + 2] = avgNeighbors(grid, x, y, w, h, isBlue = true)
                    }
                    ry == 0 && cx == 1 -> { // G site (top row)
                        rgb[idx] = avgNeighbors(grid, x, y, w, h, isRed = true)
                        rgb[idx + 1] = rawVal
                        rgb[idx + 2] = avgNeighbors(grid, x, y, w, h, isBlue = true)
                    }
                    ry == 1 && cx == 0 -> { // G site (left col)
                        rgb[idx] = avgNeighbors(grid, x, y, w, h, isRed = true)
                        rgb[idx + 1] = rawVal
                        rgb[idx + 2] = avgNeighbors(grid, x, y, w, h, isBlue = true)
                    }
                    else -> { // B site
                        rgb[idx] = avgNeighbors(grid, x, y, w, h, isRed = true)
                        rgb[idx + 1] = avgNeighbors(grid, x, y, w, h, isGreen = true)
                        rgb[idx + 2] = rawVal
                    }
                }
            }
        }
        return rgb
    }

    private fun avgNeighbors(grid: RawGrid, x: Int, y: Int, w: Int, h: Int,
                             isRed: Boolean = false, isGreen: Boolean = false, isBlue: Boolean = false): Float {
        var sum = 0f; var cnt = 0
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx; val ny = y + dy
            if (nx !in 0 until w || ny !in 0 until h) continue
            val nry = ny and 1; val ncx = nx and 1
            val isR = (nry == 0 && ncx == 0)
            val isG = (nry == 0 && ncx == 1) || (nry == 1 && ncx == 0)
            val isB = (nry == 1 && ncx == 1)
            if ((isRed && isR) || (isGreen && isG) || (isBlue && isB)) {
                sum += grid.samples[ny * w + nx].toFloat(); cnt++
            }
        }
        return if (cnt > 0) sum / cnt else 0f
    }

    private class TiffReader(private val b: ByteArray) {
        private val order = if (b[0] == 0x49.toByte()) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN

        fun readRaw(): RawGrid? = runCatching {
            if (b.size < 8) return null
            if (b[0] != 0x49.toByte() && b[0] != 0x4D.toByte()) return null
            val bb = ByteBuffer.wrap(b).order(order)
            bb.position(2)
            if (bb.short.toInt() != 42) return null
            var off = bb.int.toInt()
            var width = 0; var height = 0; var bits = 16; var compression = 1
            var offsets = IntArray(0); var counts = IntArray(0)
            var guard = 0
            while (off in 1 until b.size - 1 && guard++ < 8) {
                bb.position(off)
                val n = bb.short.toInt() and 0xFFFF
                for (i in 0 until n) {
                    if (bb.remaining() < 12) break
                    val tag = bb.short.toInt() and 0xFFFF
                    val type = bb.short.toInt() and 0xFFFF
                    val cnt = bb.int.toInt()
                    val valPos = bb.position()
                    val inline = bb.int.toInt()
                    when (tag) {
                        256 -> width = first(type, cnt, valPos, inline)
                        257 -> height = first(type, cnt, valPos, inline)
                        258 -> bits = first(type, cnt, valPos, inline)
                        259 -> compression = first(type, cnt, valPos, inline)
                        273 -> offsets = many(type, cnt, valPos, inline)
                        279 -> counts = many(type, cnt, valPos, inline)
                    }
                }
                if (bb.remaining() < 4) break
                val next = bb.int.toInt()
                if (next == 0 || next == off) break
                off = next
            }
            if (width <= 0 || height <= 0 || compression != 1 || offsets.isEmpty()) return null
            if (width.toLong() * height > MAX_PIXELS) return null
            val samples = IntArray(width * height)
            var pos = 0
            for (s in offsets.indices) {
                val start = offsets[s]
                val len = if (s < counts.size) counts[s] else 0
                if (start < 0 || len <= 0 || start + len > b.size) continue
                val sb = ByteBuffer.wrap(b, start, len).order(order)
                val elems = if (bits >= 16) len / 2 else len
                for (i in 0 until elems) {
                    if (pos >= samples.size) break
                    samples[pos++] = if (bits >= 16) sb.short.toInt() and 0xFFFF else sb.get().toInt() and 0xFF
                }
            }
            RawGrid(width, height, samples)
        }.getOrNull()

        private fun first(type: Int, cnt: Int, valPos: Int, inline: Int): Int =
            many(type, cnt, valPos, inline).firstOrNull() ?: inline

        private fun many(type: Int, cnt: Int, valPos: Int, inline: Int): IntArray {
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