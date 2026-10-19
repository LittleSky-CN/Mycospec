package org.fungalsentinel.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v2.2.8] restoration boundaries of the Step 1/2/4 provenance when reopening a project from History.
 *
 * `openProject()` mixes two purposes whose correct answers are opposite:
 *
 * | project status   | purpose                    | should live overwrite the provenance?          |
 * |-----------------|--------------------|------------------------|
 * | `IN_PROGRESS`  | continue an unfinished project | **yes** -- the data really comes from an on-site recomputation |
 * | `FINISHED`     | view a historical report   | **no** -- the user came to read last time's report |
 *
 * There is one more boundary: **restore the provenance only (12 fields), never the numbers**.
 * Numbers must match the current real state; provenance is the historical fact of "where this project originally came from".
 *
 * As in `ReportProvenanceTest`: the ViewModel depends on Application + two Room databases and cannot be
 * instantiated in a JVM unit test, so here we assert directly against **the pure functions in the production code** -- namely
 * `shouldRestoreProvenance()` (the gate) and `ReportData.restoreProvenance()` (the move),
 * which are exactly the two that `openProject()` calls.
 */
class ReopenProvenanceTest {

    private val at = "2026-10-17 09:30:00"

    /** Provenance in the snapshot: Steps 1/2/4 were all imported from presets originally. */
    private fun snapshotProvenance() = ReportData(
        step1Source = "Preset · Lab WL 2026",
        step1PresetName = "Lab WL 2026",
        step1PresetFile = "preset://7",
        step1ImportedAt = at,
        step1Params = linkedMapOf("slope_k" to "-2.738911", "cfa" to "BGGR (R/B swapped)"),

        spdSource = "Preset · Lab SPD",
        spdPresetName = "Lab SPD",
        spdPresetFile = "preset://3",
        spdImportedAt = at,
        spdParams = linkedMapOf("drift_nm" to "1.250", "drift_level" to "L1"),

        step4Source = "Preset · Lab Curve",
        step4PresetName = "Lab Curve",
        step4PresetFile = "preset://11",
        step4ImportedAt = at,
        step4Params = linkedMapOf("r2" to "0.9876", "regression_method" to "linear")
    )

    /**
     * the provenance after it was overwritten by `analyzePositioning()` / `analyzeSpd()` on reopen --
     * i.e. the on-site calibration values written by `withLiveWavelength` / `withLiveSpd`.
     */
    private fun liveProvenance() = ReportData(
        step1Source = "Live calibration",
        step1PresetName = null,
        step1PresetFile = null,
        step1ImportedAt = at,
        step1Params = linkedMapOf("slope_k" to "-2.710000", "intercept_b" to "1800.5000", "cfa" to "RGGB"),

        spdSource = "Live calibration · true_spd.csv (bundled)",
        spdPresetName = null,
        spdPresetFile = null,
        spdImportedAt = at,
        spdParams = linkedMapOf("spd_csv" to "true_spd.csv (bundled)"),

        step4Source = "Live standard curve",
        step4PresetName = null,
        step4PresetFile = null,
        step4ImportedAt = at,
        step4Params = linkedMapOf("r2" to "0.9910", "n_standards" to "5")
    )

    // ─────────────────────────────────────────────────────────────────────
    // 1. FINISHED + a preset was imported -> the provenance keeps "Preset -- ..."
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun finishedProject_restoresPresetProvenance() {
        val snapshot = snapshotProvenance()
        val recomputed = liveProvenance()

        assertTrue("a FINISHED project must restore the provenance", shouldRestoreProvenance("FINISHED", snapshot))

        val out = recomputed.restoreProvenance(snapshot)

        // Step 1
        assertEquals("Preset · Lab WL 2026", out.step1Source)
        assertEquals("Lab WL 2026", out.step1PresetName)
        assertEquals("preset://7", out.step1PresetFile)
        assertEquals(at, out.step1ImportedAt)

        // Step 2
        assertEquals("Preset · Lab SPD", out.spdSource)
        assertEquals("Lab SPD", out.spdPresetName)
        assertEquals("preset://3", out.spdPresetFile)
        assertEquals(at, out.spdImportedAt)

        // Step 4
        assertEquals("Preset · Lab Curve", out.step4Source)
        assertEquals("Lab Curve", out.step4PresetName)
        assertEquals("preset://11", out.step4PresetFile)
        assertEquals(at, out.step4ImportedAt)

        // the three *Params are "provenance that carries numbers" and are **deliberately not** in the restore list (12, not 15);
        // they must keep the values written by the recomputation. See the KDoc of restoreProvenance in ProjectViewModel.
        assertEquals(recomputed.step1Params, out.step1Params)
        assertEquals(recomputed.spdParams, out.spdParams)
        assertEquals(recomputed.step4Params, out.step4Params)
        assertNotEquals(snapshot.step1Params, out.step1Params)
        assertNotEquals(snapshot.spdParams, out.spdParams)
        assertNotEquals(snapshot.step4Params, out.step4Params)
    }

    /** The provenance fields must **really** come from the snapshot rather than coincidentally equal the recomputed values. */
    @Test
    fun finishedProject_restoreActuallyMovesTheValues() {
        val snapshot = snapshotProvenance()
        val recomputed = liveProvenance()

        for (pair in listOf(
            "step1Source" to (recomputed.step1Source to snapshot.step1Source),
            "spdSource" to (recomputed.spdSource to snapshot.spdSource),
            "step4Source" to (recomputed.step4Source to snapshot.step4Source),
            "step1PresetName" to (recomputed.step1PresetName to snapshot.step1PresetName),
            "spdPresetName" to (recomputed.spdPresetName to snapshot.spdPresetName),
            "step4PresetName" to (recomputed.step4PresetName to snapshot.step4PresetName)
        )) {
            assertNotEquals("precondition: the recomputed and snapshot values of ${pair.first} must differ first", pair.second.first, pair.second.second)
        }

        val out = recomputed.restoreProvenance(snapshot)
        assertEquals(snapshot.step1Source, out.step1Source)
        assertEquals(snapshot.spdSource, out.spdSource)
        assertEquals(snapshot.step4Source, out.step4Source)
    }

    // ─────────────────────────────────────────────────────────────────────
    // 2. IN_PROGRESS + a preset was imported -> the provenance stays live and is not restored
    //    (this specifically prevents writing the gate condition backwards)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun inProgressProject_doesNotRestore() {
        val snapshot = snapshotProvenance()

        assertFalse(
            "IN_PROGRESS means continuing the project, so the provenance must be overwritten by the on-site recomputation",
            shouldRestoreProvenance("IN_PROGRESS", snapshot)
        )
        // no other non-FINISHED status restores either, so that the code does not just compare against the single string "IN_PROGRESS".
        assertFalse(shouldRestoreProvenance(null, snapshot))
        assertFalse(shouldRestoreProvenance("ARCHIVED", snapshot))
        assertFalse(shouldRestoreProvenance("FINISHED ", snapshot)) // a trailing space does not count
    }

    // ─────────────────────────────────────────────────────────────────────
    // 3. restored == null -> do not restore, and the restore function itself must not crash or invent values
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun nullSnapshot_doesNotRestore() {
        assertFalse(shouldRestoreProvenance("FINISHED", null))
        assertFalse(shouldRestoreProvenance("IN_PROGRESS", null))
    }

    @Test
    fun restoreFromBlankProvenance_isInertAndNonNull() {
        // even when the provenance in the snapshot is all defaults, restoration must copy it faithfully, without crashing or fabricating.
        val receiver = snapshotProvenance()
        val out = receiver.restoreProvenance(ReportData())

        assertEquals(STEP_SOURCE_NOT_RUN, out.step1Source)
        assertEquals(STEP_SOURCE_NOT_RUN, out.step4Source)
        assertNull(out.step1PresetName)
        assertNull(out.step1PresetFile)
        assertNull(out.step1ImportedAt)
        assertNull(out.spdPresetName)
        assertNull(out.step4PresetName)

        // the three *Params are not in the restore list, so they must keep the receiver's values instead of being cleared to emptyMap.
        assertEquals(receiver.step1Params, out.step1Params)
        assertEquals(receiver.spdParams, out.spdParams)
        assertEquals(receiver.step4Params, out.step4Params)
    }

    // ─────────────────────────────────────────────────────────────────────
    // 4. the most important one: restore the provenance only, never the numbers
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun restoreProvenance_leavesNumericFieldsAlone() {
        // current = what analyzePositioning / analyzeSpd / analyzeSample computed after reopening
        val recomputed = ReportData(
            slope = -2.71,
            intercept = 1800.5,
            gErrorNm = 0.123,
            wavelengthQuality = "Excellent",
            mappingFormula = "Pixel(p) = -2.710000 * Wavelength(nm) + 1800.5000",
            redProfile = listOf(1f, 2f, 3f),
            spdLambda = listOf(400f, 401f),
            spdFused = listOf(9f),
            spdStatus = "Calibrated",
            samplePeakNm = "509.0",
            correctedAreaValue = 0.5,
            curveSlope = 12.5,
            curveIntercept = 3.25,
            regressionFormula = "Unmixed area = 12.5000 * Conc + 3.25",
            predictedConcentration = "1.234",
            replicates = 3,
            // the three *Params take part as well: recomputed value vs snapshot value
            step1Params = linkedMapOf("slope_k" to "-2.710000", "cfa" to "RGGB"),
            spdParams = linkedMapOf("spd_csv" to "true_spd.csv (bundled)"),
            step4Params = linkedMapOf("r2" to "0.9910")
        )
        // from = the snapshot, whose numbers all differ from the recomputed results
        val snapshot = ReportData(
            slope = -2.739,
            intercept = 1807.68,
            gErrorNm = 0.999,
            wavelengthQuality = "PRESET",
            mappingFormula = "Pixel(p) = -2.738911 * Wavelength(nm) + 1807.6813",
            redProfile = listOf(7f, 8f, 9f),
            spdLambda = listOf(700f),
            spdFused = listOf(1f),
            spdStatus = "Pending",
            samplePeakNm = "700.0",
            correctedAreaValue = 42.0,
            curveSlope = 99.0,
            curveIntercept = -5.0,
            regressionFormula = "formula from the snapshot",
            predictedConcentration = "999.000",
            replicates = 9,
            // provenance part: comes from the preset
            step1Source = "Preset · Lab WL 2026",
            step1PresetName = "Lab WL 2026",
            step1PresetFile = "preset://7",
            step1ImportedAt = at,
            step1Params = linkedMapOf("slope_k" to "-2.738911", "cfa" to "BGGR (R/B swapped)"),
            spdSource = "Preset · Lab SPD",
            spdPresetName = "Lab SPD",
            spdPresetFile = "preset://3",
            spdImportedAt = at,
            spdParams = linkedMapOf("drift_nm" to "1.250"),
            step4Source = "Preset · Lab Curve",
            step4PresetName = "Lab Curve",
            step4PresetFile = "preset://11",
            step4ImportedAt = at,
            step4Params = linkedMapOf("r2" to "0.9876")
        )

        val out = recomputed.restoreProvenance(snapshot)

        // pin every one down: the numbers must be the **recomputed** values, not the snapshot's.
        assertEquals("slope must be the recomputed value", recomputed.slope, out.slope)
        assertEquals("intercept must be the recomputed value", recomputed.intercept, out.intercept)
        assertEquals("gErrorNm must be the recomputed value", recomputed.gErrorNm, out.gErrorNm)
        assertEquals("wavelengthQuality must be the recomputed value", recomputed.wavelengthQuality, out.wavelengthQuality)
        assertEquals("mappingFormula must be the recomputed value", recomputed.mappingFormula, out.mappingFormula)
        assertEquals("redProfile must be the recomputed value", recomputed.redProfile, out.redProfile)
        assertEquals("spdLambda must be the recomputed value", recomputed.spdLambda, out.spdLambda)
        assertEquals("spdFused must be the recomputed value", recomputed.spdFused, out.spdFused)
        assertEquals("spdStatus must be the recomputed value", recomputed.spdStatus, out.spdStatus)
        assertEquals("samplePeakNm must be the recomputed value", recomputed.samplePeakNm, out.samplePeakNm)
        assertEquals("correctedAreaValue must be the recomputed value", recomputed.correctedAreaValue, out.correctedAreaValue)
        assertEquals("curveSlope must be the recomputed value", recomputed.curveSlope, out.curveSlope)
        assertEquals("curveIntercept must be the recomputed value", recomputed.curveIntercept, out.curveIntercept)
        assertEquals("regressionFormula must be the recomputed value", recomputed.regressionFormula, out.regressionFormula)
        assertEquals("predictedConcentration must be the recomputed value", recomputed.predictedConcentration, out.predictedConcentration)
        assertEquals("replicates must be the recomputed value", recomputed.replicates, out.replicates)

        // the three *Params are likewise "provenance that carries numbers" and must keep the recomputed values.
        assertEquals("step1Params must be the recomputed value", recomputed.step1Params, out.step1Params)
        assertEquals("spdParams must be the recomputed value", recomputed.spdParams, out.spdParams)
        assertEquals("step4Params must be the recomputed value", recomputed.step4Params, out.step4Params)

        // strongest assertion: the whole reportData equals "the recomputed result with only those 12 provenance fields replaced by the snapshot's".
        // this one covers the remaining dozens of fields; any out-of-bounds move makes it fail.
        assertEquals(
            "after restoration it must equal exactly: recomputed result + the 12 snapshot provenance fields, no more and no less",
            recomputed.copy(
                step1Source = snapshot.step1Source,
                step1PresetName = snapshot.step1PresetName,
                step1PresetFile = snapshot.step1PresetFile,
                step1ImportedAt = snapshot.step1ImportedAt,
                spdSource = snapshot.spdSource,
                spdPresetName = snapshot.spdPresetName,
                spdPresetFile = snapshot.spdPresetFile,
                spdImportedAt = snapshot.spdImportedAt,
                step4Source = snapshot.step4Source,
                step4PresetName = snapshot.step4PresetName,
                step4PresetFile = snapshot.step4PresetFile,
                step4ImportedAt = snapshot.step4ImportedAt
            ),
            out
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // 5. Case B: preset imported only, no positioning frame captured
    //
    // this pins down the bottom-right cell of the table above -- after dropping *Params, this kind of project
    // **still shows the preset parameters** in its report, because `analyzePositioning()` returns early at :1228 and
    // nobody ever overwrites the preset params in the baseline.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun caseB_presetOnlyNoFrames_stillShowsPresetParams() {
        val snapshot = snapshotProvenance()

        // (1) baseline: openProject starts from the snapshot and only replaces fluorophore / band with the project entity's values
        val baseline = reopenBaseline(snapshot, "EGFP", 500 to 540)

        // (2) the positioning frame is empty -> analyzePositioning() returns early at :1228 and touches no field
        //     (this step cannot run the ViewModel directly on the JVM, so it is expressed equivalently as
        //      "no recomputation write was applied to the baseline".)
        assertTrue("a FINISHED project takes the restore path", shouldRestoreProvenance("FINISHED", snapshot))

        // (3) restore: move only the 12 provenance fields and not params, so the preset values in the baseline survive unchanged
        val out = baseline.restoreProvenance(snapshot)

        assertEquals("in Case B params are still the preset values", snapshot.step1Params, out.step1Params)
        assertEquals("-2.738911", out.step1Params["slope_k"])
        assertEquals("BGGR (R/B swapped)", out.step1Params["cfa"])
        assertEquals(snapshot.spdParams, out.spdParams)
        assertEquals(snapshot.step4Params, out.step4Params)

        // the provenance rows are the preset ones too
        assertEquals("Preset · Lab WL 2026", out.step1Source)
        assertEquals("Lab WL 2026", out.step1PresetName)
        assertEquals("preset://7", out.step1PresetFile)
        assertEquals(at, out.step1ImportedAt)

        // fluorophore / band come from the project entity, not from the snapshot
        assertEquals("EGFP", out.sampleFluorophore)
        assertEquals("500–540 nm", out.sampleBand)
    }

    /** The other half of Case B: when the snapshot is null the baseline falls back to an empty report, without crashing or leaving residue. */
    @Test
    fun caseB_nullSnapshot_fallsBackToBlankReport() {
        val out = reopenBaseline(null, "MCHERRY", 590 to 630)
        assertEquals(ReportData().copy(sampleFluorophore = "MCHERRY", sampleBand = "590–630 nm"), out)
        assertEquals(STEP_SOURCE_NOT_RUN, out.step1Source)
        assertEquals(emptyMap<String, String>(), out.step1Params)
    }

    /** The baseline must keep the snapshot content verbatim rather than cherry-picking fields. */
    @Test
    fun reopenBaseline_preservesTheWholeSnapshot() {
        val snapshot = snapshotProvenance().copy(
            slope = -2.739,
            redProfile = listOf(7f, 8f, 9f),
            regressionFormula = "formula from the snapshot"
        )
        val out = reopenBaseline(snapshot, "YPET", 500 to 530)

        assertEquals(snapshot.copy(sampleFluorophore = "YPET", sampleBand = "500–530 nm"), out)
        assertEquals(-2.739, out.slope!!, 0.0)
        assertEquals(listOf(7f, 8f, 9f), out.redProfile)
        assertEquals("formula from the snapshot", out.regressionFormula)
    }
}
