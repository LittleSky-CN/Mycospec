package org.fungalsentinel.app.analysis

import kotlin.collections.sumOf
import kotlin.math.pow

object ConcentrationRegressor {
    data class RegressionResult(
        val slope: Double,
        val intercept: Double,
        val r2: Double,
        val isLogarithmic: Boolean,
        val warnings: List<String>
    )

    fun fit(points: List<Pair<Double, Double>>): RegressionResult? {
        if (points.size < 2) return null

        if (points.any { !it.first.isFinite() || !it.second.isFinite() }) return null
        return linearFit(points)
    }

    /**
     * Predicts the concentration from a regression result
     * @param result the regression result
     * @param area the measured area / signal intensity
     * @return the concentration predicted by the linear OLS model
     */
    fun predict(result: RegressionResult, area: Double): Double {
        return (area - result.intercept) / result.slope
    }

    private fun linearFit(points: List<Pair<Double, Double>>): RegressionResult? {
        val n = points.size
        val sumX = points.sumOf { it.first }
        val sumY = points.sumOf { it.second }
        val sumXY = points.sumOf { it.first * it.second }
        val sumX2 = points.sumOf { it.first * it.first }

        val denominator = n * sumX2 - sumX * sumX
        if (denominator == 0.0) return null
        val slope = (n * sumXY - sumX * sumY) / denominator
        val intercept = (sumY - slope * sumX) / n

        // compute R²
        val yMean = sumY / n
        val ssTot = points.sumOf { (it.second - yMean).pow(2) }
        val ssRes = points.sumOf { (it.second - (slope * it.first + intercept)).pow(2) }
        val r2 = if (ssTot > 0) 1 - (ssRes / ssTot) else 0.0

        return RegressionResult(slope, intercept, r2, isLogarithmic = false, warnings = emptyList())
    }
}