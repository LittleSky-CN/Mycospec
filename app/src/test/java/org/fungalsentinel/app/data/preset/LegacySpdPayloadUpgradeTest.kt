package org.fungalsentinel.app.data.preset

import org.fungalsentinel.app.analysis.SpectralResponseCalibrator
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A preset written before schema v3 is indexed by PIXEL and names its keys in
 * camelCase (`respR`, `posR`, `wB`, `bandLo`, `totalCount`).
 *
 * Those files predate the wavelength grid but are still valid calibrations, so
 * [SpectralResponseCalibrator.fromJsonStrict] upgrades them onto
 * [SpectralResponseCalibrator.canonicalGrid] instead of rejecting them. These
 * tests pin that upgrade: the geometry has to land on the grid, the derived
 * scalars have to be recomputed rather than copied from the pixel domain, and a
 * payload that is not a legacy preset at all must still be rejected.
 */
class LegacySpdPayloadUpgradeTest {

    private val n = 320
    private val startNm = 400.0
    private val stopNm = 700.0

    private fun lam(i: Int): Double = startNm + i * (stopNm - startNm) / (n - 1)

    /** Gaussian response peaking at [peakNm], so the resampled peak is known. */
    private fun bell(peakNm: Double, height: Float, widthNm: Double): FloatArray =
        FloatArray(n) { i ->
            val d = (lam(i) - peakNm) / widthNm
            (height * Math.exp(-d * d)).toFloat()
        }

    /**
     * A complete pre-v3 payload. [descending] writes the arrays in the order a
     * blue-to-red sensor would scan them, which is what the old writer did when
     * the pixel->nm mapping ran the other way.
     */
    private fun legacyJson(descending: Boolean = false): String {
        val order = if (descending) (n - 1 downTo 0).toList() else (0 until n).toList()
        fun ja(a: FloatArray) = JSONArray().apply { for (i in order) put(a[i].toDouble()) }

        val mask = FloatArray(n) { if (lam(it) in 430.0..660.0) 1f else 0f }
        val flat = FloatArray(n) { 10f }

        val o = JSONObject()
        o.put("version", 2)
        o.put("wavelengths", ja(FloatArray(n) { lam(it).toFloat() }))
        o.put("respR", ja(bell(610.0, 2f, 45.0)))
        o.put("respG", ja(bell(550.0, 3f, 40.0)))
        o.put("respB", ja(bell(470.0, 4f, 35.0)))
        o.put("posR", ja(flat)); o.put("posG", ja(flat)); o.put("posB", ja(flat))
        o.put("corrR", ja(flat)); o.put("corrG", ja(flat)); o.put("corrB", ja(flat))
        o.put("wB", ja(FloatArray(n) { 0.2f }))
        o.put("wG", ja(FloatArray(n) { 0.3f }))
        o.put("wR", ja(FloatArray(n) { 0.5f }))
        o.put("fused", ja(bell(560.0, 1f, 55.0)))
        o.put("mask", JSONArray().apply { for (i in order) put(if (mask[i] > 0f) 1 else 0) })
        o.put("bandLo", 430.0); o.put("bandHi", 660.0)
        o.put("maskedCount", 231); o.put("totalCount", n)
        o.put("thresholdRatio", 0.1)
        o.put("crossingBG", 483.25); o.put("crossingGR", 577.75)
        o.put("agreementPct", 3.125)
        o.put("peakR", JSONArray().put(610.0).put(2.0))
        o.put("peakG", JSONArray().put(550.0).put(3.0))
        o.put("peakB", JSONArray().put(470.0).put(4.0))
        o.put("fusedPeak", JSONArray().put(560.0).put(1.0))
        o.put("exposure", 0.2); o.put("frames", 3)
        o.put("spdSource", "true_spd.csv (legacy)")
        o.put("rbSwapped", true)
        o.put("slope", -2.738911); o.put("intercept", 3198.4744); o.put("gErrorNm", 5.512)
        o.put("xRoiStart", 1204); o.put("xRoiEnd", 2871)
        o.put("sensor_height", 3072)
        return o.toString()
    }

    @Test
    fun legacyPixelIndexedPayloadIsResampledOntoTheCanonicalGrid() {
        val r = SpectralResponseCalibrator.fromJsonStrict(legacyJson())

        assertEquals(401, r.gridPoints)
        assertArrayEquals(SpectralResponseCalibrator.canonicalGrid(), r.wavelengths, 0f)
        // Every grid-indexed array must have been rebuilt, not copied from px.
        assertEquals(401, r.respG.size)
        assertEquals(401, r.fused.size)
        assertEquals(401, r.mask.size)

        // Scalars that only make sense in the wavelength domain are recomputed.
        assertTrue("mask must survive the resample", r.maskedCount > 200)
        assertEquals(430f, r.effectiveBandNm.first, 1.5f)
        assertEquals(660f, r.effectiveBandNm.second, 1.5f)
        assertEquals(550f, r.peakRespG.first, 2f)
        assertEquals(3f, r.peakRespG.second, 0.05f)
        assertEquals(560f, r.fusedPeak.first, 2f)

        // Traceability scalars are carried over unchanged.
        assertEquals(-2.738911, r.slope, 1e-6)
        assertEquals(3198.4744, r.intercept, 1e-4)
        assertEquals(5.512, r.gErrorNm!!, 1e-9)
        assertEquals(1204, r.xRoiStart)
        assertEquals(2871, r.xRoiEnd)
        assertEquals(3072, r.sensorHeight)
        assertEquals("true_spd.csv (legacy)", r.spdSource)
        assertTrue(r.rbSwapped)
    }

    @Test
    fun descendingPixelOrderGivesTheSameCalibration() {
        val ascending = SpectralResponseCalibrator.fromJsonStrict(legacyJson())
        val descending = SpectralResponseCalibrator.fromJsonStrict(legacyJson(descending = true))

        // The old writer stored the arrays in sensor scan order; reversing the
        // axis must not change the calibration itself.
        assertArrayEquals(ascending.wavelengths, descending.wavelengths, 0f)
        assertArrayEquals(ascending.respG, descending.respG, 1e-5f)
        assertArrayEquals(ascending.fused, descending.fused, 1e-5f)
        assertEquals(ascending.maskedCount, descending.maskedCount)
        assertArrayEquals(ascending.mask, descending.mask)
    }

    @Test(expected = IllegalArgumentException::class)
    fun payloadThatIsNotALegacyPresetIsStillRejected() {
        SpectralResponseCalibrator.fromJsonStrict("""{"foo": 1}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun v3PayloadWithTheWrongSchemaVersionIsStillRejected() {
        // Upgrading a legacy payload yields a valid v3 payload; declaring a
        // different schema version must still be refused, not quietly upgraded.
        val upgraded = SpectralResponseCalibrator.fromJsonStrict(legacyJson())
        val o = JSONObject(SpectralResponseCalibrator.toJson(upgraded, deviceModel = "Pixel 8"))
        o.put("schema_version", 2)
        SpectralResponseCalibrator.fromJsonStrict(o.toString())
    }
}
