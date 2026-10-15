package org.fungalsentinel.app.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
 * - SubIFD 递归解析 CFA (Tag 50706)，兼容 0-based / 1-based 编码
 * - file:// 与 content:// 双协议读取
 * - forceRbSwap：Step 1 检测到 R/B 标签互换后由 ViewModel 置位，解码源头交换，保证 Step 2/3 标签一致
 */
object FrameProfileExtractor {
    private const val MAX_FILE_BYTES = 80_000_000
    private const val MAX_PIXELS = 40_000_000L
    private const val DEFAULT_BLACK_LEVEL = 64.0f

    // 绝大多数手机 (Redmi, Samsung, Huawei, OnePlus) 主摄为 RGGB：0=R, 1=G, 2=B
    private val DEFAULT_CFA = intArrayOf(0, 1, 1, 2)

    /** 【v2.2.6】R/B 标签交换开关（Step 1 反序自检结论） */
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

    fun extract(context: Context, uri: Uri): FrameProfile? = runCatching {
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
        val p = profileFromDng(bytes, exifExposure) ?: profileFromBitmap(bytes, exifExposure)
        // 【v2.2.6】源头交换 R/B，保证全链路通道标签一致
        p?.let { if (forceRbSwap) it.copy(redProfile = it.blueProfile, blueProfile = it.redProfile) else it }
    }.getOrNull()

    private fun readExposure(bytes: ByteArray): Double = runCatching {
        android.media.ExifInterface(ByteArrayInputStream(bytes))
            .getAttribute(android.media.ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull() ?: 0.0
    }.getOrDefault(0.0)

    private fun srgbToLinear(v8: Int): Float {
        val c = v8 / 255.0
        return (if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)).toFloat()
    }

    /** 已停用：方向由波长映射斜率符号自适应处理，避免样品颜色干扰质心判断 */
    private fun autoRotateRgb(rgb: FloatArray, w: Int, h: Int): Triple<FloatArray, Int, Int> =
        Triple(rgb, w, h)

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

    private fun profileFromBitmap(bytes: ByteArray, exposure: Double): FrameProfile? = runCatching {
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
        val (rs, re) = autoDetectXRoi(colE, finalW)
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
        FrameProfile(finalW, finalH, rP, gP, bP, colE, exposure, "bitmap", rs, re)
    }.getOrNull()

    private data class RawGrid(
        val width: Int, val height: Int, val samples: IntArray,
        val blackLevel: Float, val tiffExposure: Double, val cfa: IntArray
    )

    private fun profileFromDng(bytes: ByteArray, fallbackExposure: Double): FrameProfile? = runCatching {
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
        val rgb0 = FloatArray(w * h * 3)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = (y * w + x) * 3
                val ci = cfa[(y and 1) * 2 + (x and 1)].coerceIn(0, 2)
                val rawVal = max(0f, grid.samples[y * w + x].toFloat() - black) / ef
                rgb0[idx + ci] = rawVal
                for (c in 0..2) {
                    if (c == ci) continue
                    rgb0[idx + c] = neigh(grid, x, y, w, h, black, ef, cfa, c)
                }
            }
        }
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
        val (rs, re) = autoDetectXRoi(colE, finalW)
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
        FrameProfile(finalW, finalH, rP, gP, bP, colE, exp, "dng", rs, re)
    }.getOrNull()

    private fun neigh(grid: RawGrid, x: Int, y: Int, w: Int, h: Int, black: Float, ef: Float, cfa: IntArray, c: Int): Float {
        var sum = 0f; var cnt = 0
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx; val ny = y + dy
            if (nx !in 0 until w || ny !in 0 until h) continue
            if (cfa[(ny and 1) * 2 + (nx and 1)] == c) {
                sum += max(0f, grid.samples[ny * w + nx].toFloat() - black) / ef
                cnt++
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
            val cfa = tags[50706] as? IntArray
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

            val samples = IntArray(width * height)
            when (bits) {
                8 -> {
                    val cnt = minOf(samples.size, pos)
                    for (i in 0 until cnt) samples[i] = data[i].toInt() and 0xFF
                }
                16 -> {
                    val sb = ByteBuffer.wrap(data, 0, pos).order(order)
                    val cnt = minOf(samples.size, pos / 2)
                    for (i in 0 until cnt) samples[i] = sb.short.toInt() and 0xFFFF
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
                            samples[ry * width + x] = (v ushr bitIdx) and mask
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
                    50706 -> tags[50706] = cfaPattern(type, cnt, valPos, inline)
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