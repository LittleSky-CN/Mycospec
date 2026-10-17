package org.fungalsentinel.app.data.preset

import org.json.JSONArray
import org.json.JSONObject

data class CurveStandardPoint(
    val concentration: Double,
    val signal: Double,
    val unit: String
)

data class CurvePresetPayload(
    val fluorophore: String,
    val slope: Double,
    val intercept: Double,
    val rSquared: Double,
    val standardPoints: List<CurveStandardPoint>,
    val wavelengthsNm: List<Double>,
    val referenceEmission: List<Double>,
    val referenceAuc: Double,
    val windowMinNm: Double,
    val windowMaxNm: Double,
    val rbSwapped: Boolean,
    val spdCalibrationJson: String?
) {
    fun toJson(): String {
        validate()
        val json = JSONObject()
        json.put("version", VERSION)
        json.put("fluorophore", fluorophore)
        json.put("slope", slope)
        json.put("intercept", intercept)
        json.put("rSquared", rSquared)
        json.put("standardPoints", JSONArray().apply {
            standardPoints.forEach { point ->
                put(JSONObject().apply {
                    put("concentration", point.concentration)
                    put("signal", point.signal)
                    put("unit", point.unit)
                })
            }
        })
        json.put("wavelengthsNm", JSONArray(wavelengthsNm))
        json.put("referenceEmission", JSONArray(referenceEmission))
        json.put("referenceAuc", referenceAuc)
        json.put("windowMinNm", windowMinNm)
        json.put("windowMaxNm", windowMaxNm)
        json.put("rbSwapped", rbSwapped)
        if (spdCalibrationJson != null) json.put("spdCalibrationJson", spdCalibrationJson)
        return json.toString()
    }

    fun emissionAt(axisNm: DoubleArray): DoubleArray {
        validate()
        return DoubleArray(axisNm.size) { i ->
            val wavelength = axisNm[i]
            when {
                !wavelength.isFinite() -> throw IllegalArgumentException("Wavelength axis must be finite")
                wavelength < wavelengthsNm.first() || wavelength > wavelengthsNm.last() -> 0.0
                else -> {
                    val upper = wavelengthsNm.binarySearch(wavelength).let {
                        if (it >= 0) it else -it - 1
                    }
                    if (upper == 0) referenceEmission.first()
                    else if (upper >= wavelengthsNm.size) referenceEmission.last()
                    else {
                        val lower = upper - 1
                        val fraction = (wavelength - wavelengthsNm[lower]) /
                                (wavelengthsNm[upper] - wavelengthsNm[lower])
                        referenceEmission[lower] +
                                fraction * (referenceEmission[upper] - referenceEmission[lower])
                    }
                }
            }
        }
    }

    fun validate() {
        require(fluorophore.isNotBlank()) { "Curve preset has no fluorophore" }
        require(slope.isFinite() && slope != 0.0 && intercept.isFinite() && rSquared.isFinite()) {
            "Curve preset has invalid regression coefficients"
        }
        require(standardPoints.size >= 2) { "Curve preset needs at least two standard points" }
        require(standardPoints.all {
            it.concentration.isFinite() && it.signal.isFinite() && it.unit.isNotBlank()
        }) { "Curve preset contains invalid standard points" }
        require(standardPoints.map { it.concentration }.distinct().size >= 2) {
            "Curve preset must contain at least two distinct standard concentrations"
        }
        require(wavelengthsNm.size >= 3 && referenceEmission.size == wavelengthsNm.size) {
            "Curve preset reference arrays must contain at least three matching wavelengths"
        }
        require(wavelengthsNm.all { it.isFinite() } && referenceEmission.all { it.isFinite() }) {
            "Curve preset reference spectrum must contain finite values"
        }
        require(wavelengthsNm.zipWithNext().all { (left, right) -> right > left }) {
            "Curve preset wavelengths must be strictly increasing"
        }
        require(referenceAuc.isFinite() && referenceAuc > 0.0) {
            "Curve preset reference area must be positive"
        }
        require(windowMinNm.isFinite() && windowMaxNm.isFinite() && windowMinNm < windowMaxNm) {
            "Curve preset has an invalid unmixing window"
        }
        require(wavelengthsNm.first() <= windowMinNm && wavelengthsNm.last() >= windowMaxNm) {
            "Curve preset reference spectrum does not cover its unmixing window"
        }
        require(wavelengthsNm.count { it in windowMinNm..windowMaxNm } >= 3) {
            "Curve preset unmixing window must cover at least three wavelengths"
        }
        spdCalibrationJson?.let {
            require(it.isNotBlank() && JSONObject(it).has("wavelengths")) {
                "Curve preset contains invalid SPD calibration"
            }
        }
    }

    companion object {
        private const val VERSION = 1

        fun fromJson(value: String): CurvePresetPayload {
            val json = JSONObject(value)
            require(json.optInt("version") == VERSION) { "Unsupported curve preset version" }
            val pointsJson = json.getJSONArray("standardPoints")
            val points = List(pointsJson.length()) { index ->
                val point = pointsJson.getJSONObject(index)
                CurveStandardPoint(
                    concentration = point.getDouble("concentration"),
                    signal = point.getDouble("signal"),
                    unit = point.getString("unit")
                )
            }
            fun readDoubles(key: String): List<Double> {
                val array = json.getJSONArray(key)
                return List(array.length()) { array.getDouble(it) }
            }
            val payload = CurvePresetPayload(
                fluorophore = json.getString("fluorophore"),
                slope = json.getDouble("slope"),
                intercept = json.getDouble("intercept"),
                rSquared = json.getDouble("rSquared"),
                standardPoints = points,
                wavelengthsNm = readDoubles("wavelengthsNm"),
                referenceEmission = readDoubles("referenceEmission"),
                referenceAuc = json.getDouble("referenceAuc"),
                windowMinNm = json.getDouble("windowMinNm"),
                windowMaxNm = json.getDouble("windowMaxNm"),
                rbSwapped = json.optBoolean("rbSwapped", false),
                spdCalibrationJson = json.optString("spdCalibrationJson").takeIf { it.isNotBlank() }
            )
            payload.validate()
            return payload
        }
    }
}
