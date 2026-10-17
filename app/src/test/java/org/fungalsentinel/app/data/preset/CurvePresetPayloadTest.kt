package org.fungalsentinel.app.data.preset

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CurvePresetPayloadTest {
    private fun preset() = CurvePresetPayload(
        fluorophore = "EGFP",
        slope = 2.0,
        intercept = 1.0,
        rSquared = 0.99,
        standardPoints = listOf(
            CurveStandardPoint(0.0, 1.0, "a.u."),
            CurveStandardPoint(1.0, 3.0, "a.u.")
        ),
        wavelengthsNm = listOf(500.0, 510.0, 520.0),
        referenceEmission = listOf(0.0, 10.0, 0.0),
        referenceAuc = 100.0,
        windowMinNm = 500.0,
        windowMaxNm = 520.0,
        rbSwapped = false,
        spdCalibrationJson = null
    )

    @Test
    fun referenceEmissionInterpolatesOnDifferentWavelengthGrid() {
        assertArrayEquals(
            doubleArrayOf(5.0, 10.0, 5.0, 0.0),
            preset().emissionAt(doubleArrayOf(505.0, 510.0, 515.0, 525.0)),
            1e-9
        )
    }

    @Test
    fun rejectsUnorderedReferenceWavelengths() {
        val invalid = preset().copy(wavelengthsNm = listOf(500.0, 520.0, 510.0))
        assertThrows(IllegalArgumentException::class.java) { invalid.validate() }
    }

    @Test
    fun rejectsDuplicateStandardConcentrations() {
        val invalid = preset().copy(
            standardPoints = listOf(
                CurveStandardPoint(1.0, 1.0, "a.u."),
                CurveStandardPoint(1.0, 3.0, "a.u.")
            )
        )
        assertThrows(IllegalArgumentException::class.java) { invalid.validate() }
    }
}
