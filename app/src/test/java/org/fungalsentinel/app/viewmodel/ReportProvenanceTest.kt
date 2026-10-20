package org.fungalsentinel.app.viewmodel

import org.fungalsentinel.app.analysis.ConcentrationRegressor
import org.fungalsentinel.app.data.preset.CurvePresetPayload
import org.fungalsentinel.app.data.preset.CurveStandardPoint
import org.fungalsentinel.app.data.preset.PresetEntity
import org.fungalsentinel.app.data.preset.PresetTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Provenance ("where did this number come from?") for Step 1 / 2 / 4.
 *
 * Scope note: [ProjectViewModel] needs an `Application` + two Room databases,
 * and the module has no Robolectric / Mockito, so the ViewModel itself cannot be
 * instantiated on the JVM.  The provenance writers in `ProjectViewModel.kt` were
 * therefore extracted as pure top-level extensions over [ReportData], and this
 * suite asserts **those production functions directly** -- the same functions the
 * five call sites invoke.  What remains uncovered here is only the plumbing
 * (coroutine launch, `_uiState` mutation) inside the ViewModel.
 */
class ReportProvenanceTest {

    private val at = "2026-10-17 09:30:00"

    private fun wlPreset(
        id: Long = 7,
        name: String = "Lab WL 2026",
        rbSwapped: Boolean? = true
    ) = PresetEntity(
        id = id,
        name = name,
        type = PresetTypes.WAVELENGTH,
        deviceModel = "Pixel 8",
        slope = -2.738911,
        intercept = 1807.68126,
        roiStart = 10,
        roiEnd = 90,
        rbSwapped = rbSwapped
    )

    private fun spdPreset(
        id: Long = 3,
        name: String = "Lab SPD"
    ) = PresetEntity(
        id = id,
        name = name,
        type = PresetTypes.SPD,
        deviceModel = "Pixel 8"
    )

    private fun curvePayload() = CurvePresetPayload(
        fluorophore = "EGFP",
        slope = 12.5,
        intercept = 3.25,
        rSquared = 0.9876,
        standardPoints = listOf(
            CurveStandardPoint(0.0, 1.0, "µM"),
            CurveStandardPoint(1.0, 15.5, "µM")
        ),
        wavelengthsNm = listOf(500.0, 510.0, 520.0),
        referenceEmission = listOf(0.0, 10.0, 0.0),
        referenceAuc = 100.0,
        windowMinNm = 505.0,
        windowMaxNm = 515.0,
        rbSwapped = false,
        spdCalibrationJson = null
    )

    // ─── Step 1 ────────────────────────────────────────────────────────────

    @Test
    fun step1PresetProvenanceIsRecorded() {
        val r = ReportData().withWavelengthPreset(wlPreset(), at)

        assertTrue("source must say Preset", r.step1Source.startsWith("Preset"))
        assertTrue(r.step1Source.contains("Lab WL 2026"))
        assertEquals("Lab WL 2026", r.step1PresetName)
        assertEquals("preset://7", r.step1PresetFile)
        assertEquals(at, r.step1ImportedAt)

        assertEquals("-2.738911", r.step1Params["slope_k"])
        assertEquals("1807.6813", r.step1Params["intercept_b"])
        assertEquals("10-90", r.step1Params["roi"])
        assertEquals("true", r.step1Params["rb_swapped"])
        assertNotNull(r.step1Params["cfa"])
    }

    @Test
    fun step1LiveCalibrationClearsAnyStalePresetTrace() {
        // The whole point: a live run after a preset load must not leave the
        // old preset name behind in the report.
        val r = ReportData()
            .withWavelengthPreset(wlPreset(), at)
            .withLiveWavelength(
                slope = -2.5, intercept = 1700.0,
                roiStart = 4, roiEnd = 44, rbSwapped = false, at = at
            )

        assertEquals("Live calibration", r.step1Source)
        assertNull(r.step1PresetName)
        assertNull(r.step1PresetFile)
        assertEquals(at, r.step1ImportedAt)
        assertEquals("4-44", r.step1Params["roi"])
        assertEquals("false", r.step1Params["rb_swapped"])
    }

    @Test
    fun step1DefaultTimestampFollowsTheWallClockFormat() {
        // nowStamp() is private, so exercise it through the default argument.
        val r = ReportData().withWavelengthPreset(wlPreset())
        assertNotNull(r.step1ImportedAt)
        assertTrue(
            "expected yyyy-MM-dd HH:mm:ss, got ${r.step1ImportedAt}",
            Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""").matches(r.step1ImportedAt!!)
        )
    }

    // ─── Step 2 ────────────────────────────────────────────────────────────

    @Test
    fun spdPresetProvenanceIsRecorded() {
        val p = PresetEntity(
            id = 3, name = "Lab SPD", type = PresetTypes.SPD, deviceModel = "Pixel 8"
        )
        val r = ReportData().withSpdPreset(p, driftNm = 4.25f, at = at)

        assertTrue("source must say Preset", r.spdSource.startsWith("Preset"))
        assertTrue(r.spdSource.contains("Lab SPD"))
        assertEquals("Lab SPD", r.spdPresetName)
        assertEquals("preset://3", r.spdPresetFile)
        assertEquals(at, r.spdImportedAt)
        assertEquals("4.250", r.spdParams["drift_nm"])
        assertEquals("WARN", r.spdParams["drift_level"])
    }

    @Test
    fun spdPresetRecordsUnknownDriftWhenMappingIsMissing() {
        val p = PresetEntity(
            id = 3, name = "Lab SPD", type = PresetTypes.SPD, deviceModel = "Pixel 8"
        )
        val r = ReportData().withSpdPreset(p, driftNm = null, at = at)

        assertEquals("unknown", r.spdParams["drift_nm"])
        assertEquals("UNKNOWN", r.spdParams["drift_level"])
        assertTrue(r.spdSource.startsWith("Preset"))
    }

    @Test
    fun spdLiveCalibrationClearsPresetAndKeepsTheReferenceFile() {
        val p = PresetEntity(
            id = 3, name = "Lab SPD", type = PresetTypes.SPD, deviceModel = "Pixel 8"
        )
        val r = ReportData()
            .withSpdPreset(p, driftNm = 1.0f, at = at)
            .withLiveSpd("true_spd.csv (bundled)", at)

        assertTrue(r.spdSource.startsWith("Live calibration"))
        // the bundled reference file is the only place it is recorded -- keep it
        assertTrue(r.spdSource.contains("true_spd.csv (bundled)"))
        assertNull(r.spdPresetName)
        assertNull(r.spdPresetFile)
        assertEquals(at, r.spdImportedAt)
        assertEquals("true_spd.csv (bundled)", r.spdParams["spd_csv"])
    }

    // ─── Step 4 ────────────────────────────────────────────────────────────

    @Test
    fun curvePresetProvenanceIsRecorded() {
        val p = PresetEntity(
            id = 11, name = "EGFP master curve", type = PresetTypes.CURVE,
            deviceModel = "Pixel 8"
        )
        val r = ReportData().withCurvePreset(p, curvePayload(), at)

        assertTrue("source must say Preset", r.step4Source.startsWith("Preset"))
        assertTrue(r.step4Source.contains("EGFP master curve"))
        assertEquals("EGFP master curve", r.step4PresetName)
        assertEquals("preset://11", r.step4PresetFile)
        assertEquals(at, r.step4ImportedAt)
        assertEquals("12.500000", r.step4Params["slope"])
        assertEquals("3.2500", r.step4Params["intercept"])
        assertEquals("0.9876", r.step4Params["r2"])
        assertEquals("linear", r.step4Params["regression_method"])
        assertEquals("2", r.step4Params["n_standards"])
    }

    @Test
    fun step4LiveFitClearsAnyStalePresetTrace() {
        val p = PresetEntity(
            id = 11, name = "EGFP master curve", type = PresetTypes.CURVE,
            deviceModel = "Pixel 8"
        )
        val reg = ConcentrationRegressor.RegressionResult(
            slope = 9.0, intercept = -1.5, r2 = 0.998, isLogarithmic = true,
            warnings = emptyList()
        )
        val r = ReportData()
            .withCurvePreset(p, curvePayload(), at)
            .withLiveCurve(reg, nStandards = 5, at = at)

        assertEquals("Live standard curve", r.step4Source)
        assertNull(r.step4PresetName)
        assertNull(r.step4PresetFile)
        assertEquals(at, r.step4ImportedAt)
        assertEquals("log10", r.step4Params["regression_method"])
        assertEquals("0.9980", r.step4Params["r2"])
        assertEquals("5", r.step4Params["n_standards"])
    }

    @Test
    fun clearingTheCurvePresetDropsItsProvenance() {
        val p = PresetEntity(
            id = 11, name = "EGFP master curve", type = PresetTypes.CURVE,
            deviceModel = "Pixel 8"
        )
        val r = ReportData().withCurvePreset(p, curvePayload(), at).withoutCurvePreset()

        assertNull(r.step4PresetName)
        assertNull(r.step4PresetFile)
        assertNull(r.step4ImportedAt)
        assertTrue(r.step4Params.isEmpty())
        assertFalse(r.step4Source.startsWith("Preset"))
    }

    // ─── regression guards on the shared state object ──────────────────────

    @Test
    fun defaultsReportANeutralSourceInsteadOfClaimingLive() {
        val r = ReportData()
        assertEquals("Not run", r.step1Source)
        assertEquals("Not run", r.step4Source)
        assertEquals("Not run", r.spdSource)
        assertNull(r.step1PresetName)
        assertNull(r.spdPresetName)
        assertNull(r.step4PresetName)
        assertTrue(r.step1Params.isEmpty())
        assertTrue(r.spdParams.isEmpty())
        assertTrue(r.step4Params.isEmpty())
    }

    // ─── D2: steps that never ran must not misreport their source ──────────

    @Test
    fun unrunStepsNeverClaimALiveOrBundledSource() {
        val r = ReportData()
        assertFalse(
            "Step 1 never ran but claims to be Live: ${r.step1Source}",
            r.step1Source.startsWith("Live")
        )
        assertFalse(
            "Step 4 never ran but claims to be Live: ${r.step4Source}",
            r.step4Source.startsWith("Live")
        )
        assertFalse(
            "Step 2 never ran but references a reference spectrum file: ${r.spdSource}",
            r.spdSource.contains("true_spd.csv")
        )
        assertFalse(r.step1Source.startsWith("Preset"))
        assertFalse(r.step4Source.startsWith("Preset"))
        assertFalse(r.spdSource.startsWith("Preset"))
    }

    // ─── D1: the SPD rerun triggered by a Step 1 preset must not clear Step 2's preset provenance ────

    @Test
    fun spdPresetProvenanceSurvivesTheStep1PresetRerun() {
        val p = spdPreset()
        val before = ReportData().withSpdPreset(p, driftNm = 1.0f, at = at)
        // This is exactly what the analyzeSpd() cascade at the end of loadWavelengthPreset does:
        // applySpdResult() writes the numbers and withLiveSpd() rewrites the provenance as live.
        val afterRerun = before
            .withLiveSpd("true_spd.csv (bundled)", at)
            .copy(spdStatus = "Calibrated (3 lamp frame(s))")

        val restored = afterRerun.restoreSpdPresetProvenance(before)

        assertEquals("Preset · Lab SPD", restored.spdSource)
        assertEquals("Lab SPD", restored.spdPresetName)
        assertEquals("preset://3", restored.spdPresetFile)
        assertEquals(at, restored.spdImportedAt)
        assertEquals("1.000", restored.spdParams["drift_nm"])
        // The recomputed numbers themselves must be kept; only the provenance is restored
        assertEquals("Calibrated (3 lamp frame(s))", restored.spdStatus)
    }

    @Test
    fun rerunningSpdWithoutAPresetStaysLive() {
        // Reverse guardrail: when there was no preset provenance to begin with (a live rerun by
        // the user), it must stay live.
        val before = ReportData()
        val afterRerun = before.withLiveSpd("true_spd.csv (bundled)", at)

        val restored = afterRerun.restoreSpdPresetProvenance(before)

        assertTrue(restored.spdSource.startsWith("Live calibration"))
        assertTrue(restored.spdSource.contains("true_spd.csv (bundled)"))
        assertNull(restored.spdPresetName)
        assertNull(restored.spdPresetFile)
    }

    // ─── D4: an SPD preset that was never really applied must not leave "Preset · X" ────────

    @Test
    fun spdPresetThatWasNeverAppliedLeavesNoPresetProvenance() {
        val r = ReportData().withSpdPreset(spdPreset(), driftNm = 1.0f, at = at).withoutSpdPreset()

        assertEquals("Not run", r.spdSource)
        assertNull(r.spdPresetName)
        assertNull(r.spdPresetFile)
        assertNull(r.spdImportedAt)
        assertTrue(r.spdParams.isEmpty())
    }

    @Test
    fun provenanceNeverClobbersExistingReportValues() {
        val seeded = ReportData(
            wavelengthQuality = "GOOD",
            mappingFormula = "Pixel(p) = -2.5 * λ + 1700",
            spdFrames = 6,
            spdStatus = "Calibrated (6 lamp frame(s))",
            regressionR2 = "0.9980",
            curveSlope = 9.0
        )
        val r = seeded
            .withWavelengthPreset(wlPreset(), at)
            .withSpdPreset(
                PresetEntity(id = 3, name = "Lab SPD", type = PresetTypes.SPD, deviceModel = "Pixel 8"),
                1.0f, at
            )
            .withCurvePreset(
                PresetEntity(id = 11, name = "Curve", type = PresetTypes.CURVE, deviceModel = "Pixel 8"),
                curvePayload(), at
            )

        assertEquals("GOOD", r.wavelengthQuality)
        assertEquals("Pixel(p) = -2.5 * λ + 1700", r.mappingFormula)
        assertEquals(6, r.spdFrames)
        assertEquals("Calibrated (6 lamp frame(s))", r.spdStatus)
        assertEquals("0.9980", r.regressionR2)
        assertEquals(9.0, r.curveSlope!!, 0.0)
        // ...while the provenance is now populated
        assertNotNull(r.step1PresetName)
        assertNotNull(r.spdPresetFile)
        assertNotNull(r.step4PresetFile)
    }
}
