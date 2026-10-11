package org.fungalsentinel.app.analysis

import kotlin.math.sqrt

data class RegressionResult(
    val slope: Double, val intercept: Double, val r2: Double, val warnings: List<String>
)

/**
 * Step 4: OLS regression only (Android basic mode, no weighted/LoD per user request).
 * Aligned with Python scipy.stats.linregress for OLS case.
 */
object ConcentrationRegressor {
    fun fit(points: List<Pair<Double, Double>>): RegressionResult? {
        if (points.size < 2) return null
        val n = points.size
        val sx = points.sumOf { it.first }; val sy = points.sumOf { it.second }
        val sxx = points.sumOf { it.first * it.first }; val sxy = points.sumOf { it.first * it.second }
        val denom = n * sxx - sx * sx
        if (denom == 0.0) return null
        val slope = (n * sxy - sx * sy) / denom
        val intercept = (sy - slope * sx) / n
        val meanY = sy / n
        val ssTot = points.sumOf { (it.second - meanY) * (it.second - meanY) }
        val ssRes = points.sumOf { val p = slope * it.first + intercept; (it.second - p) * (it.second - p) }
        val r2 = if (ssTot > 0) 1.0 - ssRes / ssTot else 1.0
        val warnings = buildList {
            if (slope <= 0.0) add("Negative or zero slope — check standard preparation")
            if (r2 < 0.95) add("Low R² (<0.95) — curve may not be reliable")
            if (points.size < 3) add("Only 2 standards — linearity cannot be validated")
        }
        return RegressionResult(slope, intercept, r2, warnings)
    }

    fun predict(r: RegressionResult, area: Double): Double? =
        if (r.slope != 0.0) (area - r.intercept) / r.slope else null
}