package org.fungalsentinel.app.analysis

import kotlin.math.max
import kotlin.math.sqrt
import org.fungalsentinel.app.data.model.Fluorophore
import org.fungalsentinel.app.data.model.canonicalFluorophore

data class SpectralCandidate(
    val sampleMean: DoubleArray,
    val blankMean: DoubleArray
)

data class ReferenceEmission(
    val candidateIndex: Int,
    val emission: DoubleArray,
    val auc: Double
)

data class UnmixResult(
    val alpha: Double,
    val scatterScale: Double,
    val offset: Double,
    val r2: Double
)

object SpectralUnmixer {
    private const val RESPONSE_FLOOR_FRACTION = 0.20

    fun wavelengthAxis(width: Int, slope: Double, intercept: Double): DoubleArray {
        require(width > 0) { "Profile width must be positive" }
        require(slope.isFinite() && slope != 0.0 && intercept.isFinite()) {
            "Wavelength mapping must be finite and non-singular"
        }
        return DoubleArray(width) { (it - intercept) / slope }
    }

    fun extractChannelSpectra(
        frames: List<FrameProfile>,
        wavelengths: DoubleArray,
        fluorophore: String,
        spd: SpdCalibrationResult?
    ): List<DoubleArray> {
        require(frames.isNotEmpty()) { "At least one frame is required" }
        require(wavelengths.size >= 2) { "At least two wavelength samples are required" }
        require(wavelengths.all { it.isFinite() }) { "Wavelengths must be finite" }
        val increasing = wavelengths.first() < wavelengths.last()
        require(wavelengths.indices.drop(1).all { index ->
            if (increasing) wavelengths[index] > wavelengths[index - 1]
            else wavelengths[index] < wavelengths[index - 1]
        }) {
            "Wavelength axis must be strictly ordered"
        }
        val channel = when (canonicalFluorophore(fluorophore)) {
            Fluorophore.MCHERRY -> 0
            Fluorophore.CFP, Fluorophore.MTURQUOISE2 -> 2
            else -> 1
        }
        val response = spd?.let {
            when (channel) {
                0 -> it.respR
                2 -> it.respB
                else -> it.respG
            }
        }
        val responseWavelengths = spd?.wavelengths
        if (response != null) {
            require(responseWavelengths != null && response.size == responseWavelengths.size) {
                "SPD response and wavelength arrays must match"
            }
            require(response.isNotEmpty() && response.all { it.isFinite() && it >= 0f }) {
                "SPD response must be finite and non-negative"
            }
            require(responseWavelengths.all { it.isFinite() }) {
                "SPD wavelengths must be finite"
            }
            val spdWavelengthsIncreasing =
                responseWavelengths.size < 2 || responseWavelengths.first() < responseWavelengths.last()
            require(responseWavelengths.indices.drop(1).all { index ->
                if (spdWavelengthsIncreasing) responseWavelengths[index] > responseWavelengths[index - 1]
                else responseWavelengths[index] < responseWavelengths[index - 1]
            }) {
                "SPD wavelengths must be strictly ordered"
            }
        }

        val orderedResponseWavelengths = responseWavelengths?.let {
            if (it.size < 2 || it.first() < it.last()) it else it.reversedArray()
        }
        val orderedResponse = response?.let {
            if (responseWavelengths!!.size < 2 || responseWavelengths.first() < responseWavelengths.last()) it else it.reversedArray()
        }
        val responseAtAxis = if (orderedResponse == null) null else DoubleArray(wavelengths.size) { index ->
            interpolate(wavelengths[index], orderedResponseWavelengths!!, orderedResponse)
        }
        val responseFloor = responseAtAxis?.let { values ->
            val maximum = values.maxOrNull() ?: 0.0
            require(maximum > 0f) { "No positive calibrated response is available" }
            max(1e-9, maximum * RESPONSE_FLOOR_FRACTION)
        }

        return frames.map { frame ->
            val profile = when (channel) {
                0 -> frame.redProfile
                2 -> frame.blueProfile
                else -> frame.greenProfile
            }
            require(profile.size == wavelengths.size) {
                "Frame profile width does not match the wavelength axis"
            }
            require(profile.all { it.isFinite() }) { "Frame profile values must be finite" }
            DoubleArray(wavelengths.size) { index ->
                val value = profile[index].toDouble()
                if (responseAtAxis == null) {
                    value
                } else {
                    val responseAtWavelength = responseAtAxis[index]
                    if (responseAtWavelength > responseFloor!!) value / responseAtWavelength else 0.0
                }
            }
        }
    }

    fun meanSpectra(spectra: List<DoubleArray>): DoubleArray {
        require(spectra.isNotEmpty()) { "At least one spectrum is required" }
        val width = spectra.first().size
        require(width > 0 && spectra.all { it.size == width }) {
            "Spectra must have matching non-empty lengths"
        }
        return DoubleArray(width) { index -> spectra.sumOf { it[index] } / spectra.size }
    }

    fun selectReferenceEmission(
        wavelengths: DoubleArray,
        candidates: List<SpectralCandidate>,
        emissionMask: BooleanArray
    ): ReferenceEmission {
        require(wavelengths.isNotEmpty() && emissionMask.size == wavelengths.size) {
            "Wavelengths and emission mask must have matching lengths"
        }
        require(candidates.isNotEmpty()) { "At least one standard spectrum is required" }

        var best: ReferenceEmission? = null
        candidates.forEachIndexed { index, candidate ->
            require(candidate.sampleMean.size == wavelengths.size &&
                    candidate.blankMean.size == wavelengths.size) {
                "Standard spectra must match the wavelength axis"
            }
            val emission = DoubleArray(wavelengths.size) { i ->
                max(0.0, candidate.sampleMean[i] - candidate.blankMean[i])
            }
            val auc = emissionAuc(wavelengths, emission, emissionMask)
            val currentBest = best
            if (auc > 0.0 && (currentBest == null || auc > currentBest.auc)) {
                best = ReferenceEmission(index, emission, auc)
            }
        }
        return best ?: throw IllegalArgumentException(
            "No standard has positive blank-subtracted emission in the selected window"
        )
    }

    fun emissionAuc(
        wavelengths: DoubleArray,
        emission: DoubleArray,
        mask: BooleanArray
    ): Double {
        require(wavelengths.size == emission.size && mask.size == wavelengths.size) {
            "Wavelengths, emission, and mask must have matching lengths"
        }
        val indices = wavelengths.indices.filter { mask[it] }.sortedBy { wavelengths[it] }
        if (indices.size < 2) return 0.0
        var area = 0.0
        for (i in 0 until indices.lastIndex) {
            val left = indices[i]
            val right = indices[i + 1]
            area += (emission[left] + emission[right]) * 0.5 *
                    (wavelengths[right] - wavelengths[left])
        }
        return area
    }

    fun unmixSpectrum(
        sample: DoubleArray,
        referenceEmission: DoubleArray,
        blank: DoubleArray,
        windowMask: BooleanArray
    ): UnmixResult {
        require(sample.size == referenceEmission.size &&
                sample.size == blank.size && sample.size == windowMask.size) {
            "Sample, reference, blank, and window arrays must have matching lengths"
        }
        val indices = sample.indices.filter { windowMask[it] }
        require(indices.size >= 3) { "Unmixing window must contain at least three samples" }
        val y = DoubleArray(indices.size) { sample[indices[it]] }
        val columns = arrayOf(
            DoubleArray(indices.size) { referenceEmission[indices[it]] },
            DoubleArray(indices.size) { blank[indices[it]] },
            DoubleArray(indices.size) { 1.0 }
        )
        require(y.all { it.isFinite() } && columns.all { column -> column.all { it.isFinite() } }) {
            "Unmixing inputs must be finite"
        }

        val coefficients = leastSquares(columns, y)
        val yMean = y.average()
        var residualSum = 0.0
        var totalSum = 0.0
        for (i in y.indices) {
            val predicted = coefficients[0] * columns[0][i] +
                    coefficients[1] * columns[1][i] + coefficients[2]
            residualSum += (y[i] - predicted) * (y[i] - predicted)
            totalSum += (y[i] - yMean) * (y[i] - yMean)
        }
        val r2 = if (totalSum > 0.0) 1.0 - residualSum / totalSum else 0.0
        return UnmixResult(coefficients[0], coefficients[1], coefficients[2], r2)
    }


    fun integrationMask(
        wavelengths: DoubleArray,
        peakWavelength: Double,
        fwhm: Double
    ): BooleanArray {
        require(peakWavelength.isFinite() && fwhm.isFinite() && fwhm > 0.0) {
            "Emission peak and FWHM must be finite and positive"
        }
        val halfWidth = fwhm / 2.0
        return BooleanArray(wavelengths.size) {
            wavelengths[it] >= peakWavelength - halfWidth &&
                    wavelengths[it] <= peakWavelength + halfWidth
        }
    }

    fun unmixingMask(
        wavelengths: DoubleArray,
        referenceBlank: DoubleArray,
        peakWavelength: Double
    ): BooleanArray {
        require(wavelengths.size == referenceBlank.size) {
            "Reference blank must match the wavelength axis"
        }
        val scatterIndices = wavelengths.indices.filter { wavelengths[it] > 460.0 && wavelengths[it] < 520.0 }
        val lower = if (scatterIndices.isNotEmpty()) {
            val scatterIndex = scatterIndices.maxByOrNull { referenceBlank[it] }!!
            minOf(max(wavelengths[scatterIndex] + 2.0, peakWavelength - 30.0), peakWavelength - 5.0)
        } else {
            max(420.0, peakWavelength - 20.0)
        }
        val upper = if (scatterIndices.isNotEmpty()) peakWavelength + 90.0
        else minOf(680.0, peakWavelength + 90.0)
        return BooleanArray(wavelengths.size) { wavelengths[it] >= lower && wavelengths[it] <= upper }
    }

    private fun leastSquares(columns: Array<DoubleArray>, y: DoubleArray): DoubleArray {
        val columnCount = columns.size
        val permutation = IntArray(columnCount) { it }
        val residualColumns = Array(columnCount) { columns[it].copyOf() }
        val q = Array(columnCount) { DoubleArray(y.size) }
        val r = Array(columnCount) { DoubleArray(columnCount) }
        val initialScale = columns.maxOf { column -> sqrt(column.sumOf { it * it }) }
        val tolerance = max(y.size, columnCount) * Math.ulp(1.0) * initialScale
        var rank = columnCount

        for (k in 0 until columnCount) {
            var pivot = k
            var pivotNorm = -1.0
            for (j in k until columnCount) {
                val norm = sqrt(residualColumns[j].sumOf { it * it })
                if (norm > pivotNorm) {
                    pivot = j
                    pivotNorm = norm
                }
            }
            if (pivot != k) {
                val column = residualColumns[k]
                residualColumns[k] = residualColumns[pivot]
                residualColumns[pivot] = column
                val originalIndex = permutation[k]
                permutation[k] = permutation[pivot]
                permutation[pivot] = originalIndex
                for (i in 0 until k) {
                    val value = r[i][k]
                    r[i][k] = r[i][pivot]
                    r[i][pivot] = value
                }
            }
            if (pivotNorm <= tolerance) {
                rank = k
                break
            }
            r[k][k] = pivotNorm
            for (i in y.indices) q[k][i] = residualColumns[k][i] / pivotNorm
            for (j in k + 1 until columnCount) {
                val firstProjection = dot(q[k], residualColumns[j])
                for (i in y.indices) residualColumns[j][i] -= firstProjection * q[k][i]
                val correction = dot(q[k], residualColumns[j])
                for (i in y.indices) residualColumns[j][i] -= correction * q[k][i]
                r[k][j] = firstProjection + correction
            }
        }
        require(rank > 0) { "Unmixing model has no usable wavelength variation" }

        val permutedCoefficients = DoubleArray(columnCount)
        for (i in rank - 1 downTo 0) {
            var value = dot(q[i], y)
            for (j in i + 1 until rank) value -= r[i][j] * permutedCoefficients[j]
            permutedCoefficients[i] = value / r[i][i]
        }
        return DoubleArray(columnCount) { permutedCoefficients[permutation.indexOf(it)] }
    }

    private fun interpolate(x: Double, xp: FloatArray, fp: FloatArray): Double {
        if (xp.size == 1 || x <= xp[0]) return fp[0].toDouble()
        if (x >= xp.last()) return fp.last().toDouble()
        var low = 0
        var high = xp.lastIndex
        while (low < high - 1) {
            val middle = (low + high) / 2
            if (xp[middle] <= x) low = middle else high = middle
        }
        val span = xp[high] - xp[low]
        val fraction = if (span == 0f) 0.0 else (x - xp[low]) / span
        return fp[low] + (fp[high] - fp[low]) * fraction
    }

    private fun dot(left: DoubleArray, right: DoubleArray): Double {
        var sum = 0.0
        for (i in left.indices) sum += left[i] * right[i]
        return sum
    }

}
