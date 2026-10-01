package org.fungalsentinel.app.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class FrameProfile(
    val width: Int,
    val height: Int,
    val redProfile: FloatArray,
    val greenProfile: FloatArray,
    val blueProfile: FloatArray,
    val columnEnergy: FloatArray,
    val exposureSeconds: Double,
    val sourceKind: String
)

/**
 * Hardened frame profiler.
 * Every external input path is wrapped: a bad file can never crash the app.
 */
object FrameProfileExtractor {

    private const val MAX_FILE_BYTES = 80_000_000   // 80 MB input cap
    private const val MAX_PIXELS = 40_000_000L      // 40 Mpixel cap (OOM guard)

    fun extract(context: Context, uri: Uri): FrameProfile? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null
        if (bytes.size < 64 || bytes.size > MAX_FILE_BYTES) return null
        val exposure = readExposure(bytes)
        profileFromBitmap(bytes, exposure) ?: profileFromDng(bytes, exposure)
    }.getOrNull()

    private fun readExposure(bytes: ByteArray): Double = runCatching {
        ExifInterface(ByteArrayInputStream(bytes))
            .getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull() ?: 0.0
    }.getOrDefault(0.0)

    // ── Decodable images (JPEG/PNG) ──
    private fun profileFromBitmap(bytes: ByteArray, exposure: Double): FrameProfile? =
        runCatching {
            var bm: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // FIX 1: force SOFTWARE allocator — hardware bitmaps forbid getPixels()
                ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                ) { decoder, _, _ ->
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                }
            } else {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            bm ?: return null

            // Safety net: if a hardware bitmap still slips through, convert it
            if (bm!!.config == Bitmap.Config.HARDWARE) {
                val soft = bm!!.copy(Bitmap.Config.ARGB_8888, false)
                bm!!.recycle()
                bm = soft
            }

            val w = bm!!.width
            val h = bm!!.height
            if (w <= 0 || h <= 0 || w.toLong() * h > MAX_PIXELS) {
                bm!!.recycle(); return null
            }

            val rP = FloatArray(h); val gP = FloatArray(h); val bP = FloatArray(h)
            val colE = FloatArray(w)
            val row = IntArray(w)
            for (y in 0 until h) {
                bm!!.getPixels(row, 0, w, 0, y, w, 1)
                for (x in 0 until w) {
                    val c = row[x]
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    rP[y] += r.toFloat(); gP[y] += g.toFloat(); bP[y] += b.toFloat()
                    colE[x] += (r + g + b) / 3f
                }
            }
            bm!!.recycle()
            FrameProfile(w, h, rP, gP, bP, colE, exposure, "bitmap")
        }.getOrNull()

    // ── Uncompressed DNG (TIFF) ──
    private data class RawGrid(val width: Int, val height: Int, val samples: IntArray)

    private fun profileFromDng(bytes: ByteArray, exposure: Double): FrameProfile? =
        runCatching {
            val grid = TiffReader(bytes).readRaw() ?: return null
            val rP = FloatArray(grid.height)
            val gP = FloatArray(grid.height)
            val bP = FloatArray(grid.height)
            val colE = FloatArray(grid.width)
            for (y in 0 until grid.height) {
                val base = y * grid.width
                val ry = y and 1
                for (x in 0 until grid.width) {
                    val v = grid.samples[base + x]
                    val cx = x and 1
                    colE[x] += v.toFloat()
                    when {
                        ry == 0 && cx == 0 -> rP[y] += v.toFloat()
                        ry == 1 && cx == 1 -> bP[y] += v.toFloat()
                        else -> gP[y] += v.toFloat()
                    }
                }
            }
            for (y in 1 until grid.height step 2) rP[y] = rP[y - 1]
            for (y in 0 until grid.height step 2) if (y + 1 < grid.height) bP[y] = bP[y + 1]
            FrameProfile(grid.width, grid.height, rP, gP, bP, colE, exposure, "dng")
        }.getOrNull()

    // ── Hardened minimal TIFF reader ──
    private class TiffReader(private val b: ByteArray) {
        private val order: ByteOrder =
            if (b[0] == 0x49.toByte()) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN

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
            // FIX 3: OOM guard
            if (width.toLong() * height > MAX_PIXELS) return null

            val samples = IntArray(width * height)
            var pos = 0
            for (s in offsets.indices) {
                val start = offsets[s]
                val len = if (s < counts.size) counts[s] else 0
                // FIX 2: strict bounds check before every wrap
                if (start < 0 || len <= 0 || start + len > b.size) continue
                val sb = ByteBuffer.wrap(b, start, len).order(order)
                val elems = if (bits >= 16) len / 2 else len
                for (i in 0 until elems) {
                    if (pos >= samples.size) break
                    samples[pos++] = if (bits >= 16) sb.short.toInt() and 0xFFFF
                    else sb.get().toInt() and 0xFF
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
            val base: Int
            val size: Int
            if (total <= 4) {
                // FIX 2: validate inline-value window
                if (valPos < 0 || valPos + 4 > b.size) return IntArray(0)
                base = valPos; size = 4
            } else {
                if (inline < 0 || inline + total > b.size) return IntArray(0)
                base = inline; size = total
            }
            val sb = ByteBuffer.wrap(b, base, size).order(order)
            return IntArray(cnt) {
                when (type) {
                    3 -> sb.short.toInt() and 0xFFFF
                    4, 13 -> sb.int
                    else -> sb.get().toInt() and 0xFF
                }
            }
        }
    }
}