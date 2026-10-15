package org.fungalsentinel.app.analysis

import kotlin.collections.sumOf
import kotlin.math.log10
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

        // 先尝试线性拟合
        val linearResult = linearFit(points)

        // 如果线性 R² < 0.95，尝试对数拟合
        if (linearResult.r2 < 0.95) {
            val logResult = logarithmicFit(points)
            if (logResult.r2 > linearResult.r2) {
                return logResult.copy(
                    warnings = listOf("Low Linear R² (${String.format("%.2f", linearResult.r2)}), switched to Logarithmic fit.")
                )
            }
        }

        return linearResult
    }

    /**
     * 根据回归结果预测浓度
     * @param result 回归结果
     * @param area 测得的面积/信号强度
     * @return 预测的实际浓度值 (已自动处理对数/线性转换)
     */
    fun predict(result: RegressionResult, area: Double): Double {
        return if (result.isLogarithmic) {
            // 对于对数拟合：Area = slope * log10(Conc) + intercept
            // 所以：log10(Conc) = (Area - intercept) / slope
            // Conc = 10^((Area - intercept) / slope)
            val logConc = (area - result.intercept) / result.slope
            10.0.pow(logConc)
        } else {
            // 对于线性拟合：Area = slope * Conc + intercept
            // 所以：Conc = (Area - intercept) / slope
            (area - result.intercept) / result.slope
        }
    }

    private fun linearFit(points: List<Pair<Double, Double>>): RegressionResult {
        val n = points.size
        val sumX = points.sumOf { it.first }
        val sumY = points.sumOf { it.second }
        val sumXY = points.sumOf { it.first * it.second }
        val sumX2 = points.sumOf { it.first * it.first }

        val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        val intercept = (sumY - slope * sumX) / n

        // 计算 R²
        val yMean = sumY / n
        val ssTot = points.sumOf { (it.second - yMean).pow(2) }
        val ssRes = points.sumOf { (it.second - (slope * it.first + intercept)).pow(2) }
        val r2 = if (ssTot > 0) 1 - (ssRes / ssTot) else 0.0

        return RegressionResult(slope, intercept, r2, isLogarithmic = false, warnings = emptyList())
    }

    private fun logarithmicFit(points: List<Pair<Double, Double>>): RegressionResult {
        // 使用 log10 而不是 ln，与 UI 显示的 "log10(Conc)" 保持一致
        val logPoints = points.map { (log10(it.first) to it.second) }
        return linearFit(logPoints).copy(isLogarithmic = true)
    }
}