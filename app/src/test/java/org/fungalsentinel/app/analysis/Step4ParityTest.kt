package org.fungalsentinel.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Ignore
import org.junit.Test

/**
 * Step 4 cross-platform reconciliation: given an **identical** point table, do Android's
 * [ConcentrationRegressor] and Python FSSA's regressor produce the same
 * (slope, intercept, R²) triple?
 *
 * Background: a user reported "the same batch of data gives R²=0.999 on Android but only
 * 0.3 on Python". To tell **algorithmic divergence** (a real bug) apart from
 * **configuration divergence** (the two ends default to different fluorophores / emission
 * bands, so the point table fed into the regressor was never the same to begin with), the
 * only way is to pin the point table to constants and let only the regressor run.
 *
 * Every point table is inlined as a constant -- the test must be self-contained and must
 * not depend on external files or on the Python repository path.
 * Data source: a measured export from the Python side for `FSSA_Documents/10.15`
 * (positioning/spd/B0/ B1=100 / B2=80 / B3=20 mg/L / S).
 *
 * Reconciliation verdict (see the report for details):
 * - EGFP 499–519 unmixing and YPET 517–537 legacy **agree**, difference ≤ 6.2e-6.
 * - The other two **do not match**, and the Python side contradicts itself (the same YPET
 *   point table yields two different R² on the legacy and unmixing rows). They are marked
 *   [Ignore] rather than loosening the assertions to accommodate them.
 */
class Step4ParityTest {

    // ── Measured point tables from the Python side (concentration mg/L, area) ────────────

    /** EGFP 499–519 nm / unmixing. Python R² = 0.999041, slope = 8.54909e-4, intercept = 0.171123. */
    private val egfp499Unmixing = listOf(
        100.0 to 0.257531,
        80.0 to 0.238292,
        20.0 to 0.188527
    )

    /** YPET 517–537 nm / legacy. Python R² = 0.285521. */
    private val ypet517Legacy = listOf(
        100.0 to 0.267636,
        80.0 to 0.293935,
        20.0 to 0.257038
    )

    /** FLUORESCEIN 507.5–532.5 nm / unmixing. Python R² = 0.279218. */
    private val fluorescein507Unmixing = listOf(
        100.0 to 0.355239,
        80.0 to 0.356321,
        20.0 to 0.298523
    )

    /** YPET 517–537 nm / unmixing. Python R² = 0.638251. Point table is identical to [ypet517Legacy]. */
    private val ypet517Unmixing = ypet517Legacy

    // ── The two agreeing groups: assertions are live ─────────────────────────────────────

    /**
     * The group behind the "Android 0.999" the user saw. Recomputing with Android OLS gives
     * R² = 0.9990410056, Python reports 0.999041 -- a difference of 5.6e-9, so the algorithm
     * is the same.
     */
    @Test
    fun egfp499UnmixingMatchesPython() {
        val result = ConcentrationRegressor.fit(egfp499Unmixing)
        assertNotNull(result)

        assertEquals("R²", 0.999041, result!!.r2, 1e-5)
        assertEquals("slope", 8.54909e-4, result.slope, 1e-8)
        assertEquals("intercept", 0.171123, result.intercept, 1e-6)
        assertFalse("Android only performs a linear fit", result.isLogarithmic)
    }

    /**
     * The group behind the "Python 0.3" the user saw. Recomputing with Android OLS gives
     * R² = 0.2855148672, Python reports 0.285521 -- a difference of 6.1e-6 (Python only
     * reports 6 decimal places). The algorithm is the same.
     *
     * This case is the single most important piece of evidence in this reconciliation:
     * **feed Python's low-scoring point table into Android and Android also gets 0.2855**.
     * The gap between 0.999 and 0.3 comes 100% from the point table, not from the regressor.
     */
    @Test
    fun ypet517LegacyMatchesPython() {
        val result = ConcentrationRegressor.fit(ypet517Legacy)
        assertNotNull(result)

        assertEquals("R²", 0.285521, result!!.r2, 1e-5)
        // Python did not export slope/intercept for this group, so pin Android's own OLS
        // result here as a regression guard
        assertEquals("slope", 2.4381538462e-4, result.slope, 1e-12)
        assertEquals("intercept", 0.2566153077, result.intercept, 1e-9)
    }

    // ── The two non-matching groups: marked Ignore, assertions not loosened to fit ───────

    /**
     * FLUORESCEIN 507.5–532.5 / unmixing.
     *
     * Android OLS computes R² = 0.9344412901 on this point table, Python reports 0.279218,
     * a difference of **+0.655223** (orders of magnitude beyond floating-point error, and
     * far beyond the 1e-3 tolerance).
     *
     * Verdict: under unweighted OLS this point table **cannot** yield 0.279 -- the three
     * points are nearly collinear (0.355 / 0.356 / 0.299), and any OLS variant will give a
     * very high R². Getting R² down to 0.28 would require a fourth point (after appending a
     * scan of (0, y0) it would need y0 ≈ 0.572, but this row is labelled unmixing, and a
     * blank after unmixing should be close to 0, which does not match) or a completely
     * different point table.
     * It is therefore judged to be **an inconsistent export on the Python side**, and a
     * colleague needs to trace back where the point table for that row came from.
     */
    @Test
    @Ignore("Python reports 0.279218, Android OLS gets 0.9344412901, difference +0.655223 — suspected Python-side point table and R² come from different sources, needs investigation")
    fun fluorescein507UnmixingMatchesPython() {
        val result = ConcentrationRegressor.fit(fluorescein507Unmixing)
        assertNotNull(result)
        assertEquals("R²", 0.279218, result!!.r2, 1e-3)
    }

    /**
     * YPET 517–537 / unmixing.
     *
     * The point table is **bit-for-bit identical** to [ypet517Legacy], yet Python gives two
     * R² values on the two rows (legacy 0.285521 / unmixing 0.638251). R² is a pure function
     * of the point table, and the same point table cannot have two values -- this directly
     * proves that **Python's `metric_mode` affects more than the metric value: the point
     * table the regressor actually consumes also differs from these three exported points**.
     *
     * Android gets R² = 0.2855148672 on this point table, a difference of **-0.352736** from
     * Python's 0.638251.
     *
     * Hypotheses ruled out by follow-up scans (none of them reproduces 0.638251 from these
     * three points):
     * - Appending a (0, y0) zero point: the upper bound of R² over y0 in [-2, 4] is 0.635753
     *   (y0 ≈ 0.216), still below 0.638251 -- so "Python treats the blank as a 0 mg/L
     *   standard point" explains most of the lift but not all of it;
     * - Any two-point subset of the three: R² is always 1.0 (see [twoPointFitIsAlwaysPerfect]);
     * - Taking log10 of x: R² = 0.393405.
     */
    @Test
    @Ignore("Python legacy/unmixing use the same point table but report two R² values (0.285521 / 0.638251), the export is inconsistent — needs investigation")
    fun ypet517UnmixingMatchesPython() {
        val result = ConcentrationRegressor.fit(ypet517Unmixing)
        assertNotNull(result)
        assertEquals("R²", 0.638251, result!!.r2, 1e-3)
    }

    // ── Regressor behaviour evidence (answering "is R² always 1.0 when n=2?" etc.) ───────

    /**
     * With n = 2, two points define a line, so residuals are always 0 → R² is always 1.0.
     *
     * This is the degeneracy of the R² metric under two-point calibration: it carries no
     * information about fit quality. A user who only uses two standard points will see
     * R² = 1.0000 no matter how dirty the data is, and `warn_low_r2` will never fire.
     */
    @Test
    fun twoPointFitIsAlwaysPerfect() {
        val result = ConcentrationRegressor.fit(listOf(100.0 to 0.35, 20.0 to 0.31))
        assertNotNull(result)
        assertEquals("R² is always 1 when n=2", 1.0, result!!.r2, 1e-12)
    }

    /** n = 1 cannot be fitted and returns null (the ViewModel takes the `st4_failed_groups` branch when points.size < 2). */
    @Test
    fun singlePointIsRejected() {
        assertEquals(null, ConcentrationRegressor.fit(listOf(100.0 to 0.35)))
        assertEquals(null, ConcentrationRegressor.fit(emptyList()))
    }

    /**
     * Android has no logarithmic fit: `isLogarithmic` is always false and `warnings` is
     * always empty.
     *
     * In other words, the `result_formula_log` / `warn_log_switch` paths are dead branches in
     * the production code -- if the Python side has automatic switching such as `metric_mode`,
     * Android has no counterpart.
     */
    @Test
    fun regressorNeverSwitchesToLogarithmic() {
        // Deliberately use a point table that fits poorly linearly but has a good log shape
        val logShaped = listOf(
            1.0 to 0.10,
            10.0 to 0.30,
            100.0 to 0.50
        )
        val result = ConcentrationRegressor.fit(logShaped)
        assertNotNull(result)
        assertFalse("Android does not perform a log fit", result!!.isLogarithmic)
        assertEquals("Android produces no regression warnings", emptyList<String>(), result.warnings)
    }

    /**
     * The fit is **unweighted**: duplicating the whole point table (equivalent to weighting
     * every point by 2) does not change the result.
     *
     * This pins down "Android does not apply 1/x or 1/y² weighting" -- weighting is one of the
     * easiest places for the two ends to silently diverge.
     */
    @Test
    fun fitIsUnweighted() {
        val once = ConcentrationRegressor.fit(ypet517Legacy)
        val twice = ConcentrationRegressor.fit(ypet517Legacy + ypet517Legacy)
        assertNotNull(once)
        assertNotNull(twice)
        assertEquals("slope", once!!.slope, twice!!.slope, 1e-15)
        assertEquals("intercept", once.intercept, twice.intercept, 1e-15)
        assertEquals("R²", once.r2, twice.r2, 1e-15)
    }
}
