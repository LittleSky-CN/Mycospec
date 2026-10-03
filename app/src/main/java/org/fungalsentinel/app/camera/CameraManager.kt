package org.fungalsentinel.app.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager as AndroidCameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.CaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DngPolicy { ALL, SAMPLES_ONLY, NONE }

data class CameraParams(
    val exposureMs: Long = 100L,
    val iso: Int = 100,
    val focusDiopter: Float = 0f,
    val wbTemperature: Int? = null,
    val autoExposure: Boolean = true,          // NEW: AE toggle from Settings
    val hotPixelMode: Int = CameraMetadata.HOT_PIXEL_MODE_FAST
)

data class CaptureMetadata(
    val exposureTimeNs: Long,
    val iso: Int,
    val focusDistance: Float,
    val whiteBalance: Int?,
    val sensorWidth: Int,
    val sensorHeight: Int,
    val saturationRatio: Float,
    val timestamp: Long
)

class CameraManager(private val context: Context) {

    companion object {
        private const val TAG = "CameraManager"
        private val EXPOSURE_RANGE_MS = 1L..600_000L     // 1 ms .. 10 min
        private val ISO_RANGE = 50..1600
        private val FOCUS_RANGE_D = 0f..5f
        private const val RESULT_WAIT_MS = 2500L
        private const val POLL_INTERVAL_MS = 25L
    }

    private val androidCameraManager: AndroidCameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as AndroidCameraManager

    // Thread A: camera open / session / capture callbacks
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    // Thread B: image processing & result waiting (FIX: never block Thread A)
    private var processThread: HandlerThread? = null
    private var processHandler: Handler? = null

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var rawImageReader: ImageReader? = null
    private var cameraCharacteristics: CameraCharacteristics? = null

    private var currentParams = CameraParams()
    var dngPolicy: DngPolicy = DngPolicy.ALL

    @Volatile private var pendingCaptureResult: TotalCaptureResult? = null
    @Volatile private var captureFailed = false

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _lastMetadata = MutableStateFlow<CaptureMetadata?>(null)
    val lastMetadata: StateFlow<CaptureMetadata?> = _lastMetadata.asStateFlow()

    var onDngSaved: ((File, CaptureMetadata) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onSaturationWarning: ((Float) -> Unit)? = null

    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun startThreads() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("CameraSession").also { it.start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
        if (processThread == null) {
            processThread = HandlerThread("RawProcess").also { it.start() }
            processHandler = Handler(processThread!!.looper)
        }
    }

    private fun stopThreads() {
        listOf(backgroundThread, processThread).forEach { t ->
            t?.quitSafely()
            try { t?.join() } catch (_: InterruptedException) {}
        }
        backgroundThread = null; backgroundHandler = null
        processThread = null; processHandler = null
    }

    fun openCamera(previewSurface: Surface) {
        if (!hasCameraPermission()) {
            onError?.invoke("Camera permission not granted"); return
        }
        startThreads()
        this.previewSurface = previewSurface
        _isReady.value = false
        try {
            val rawCameraId = findRawCapableCamera() ?: run {
                onError?.invoke("No RAW-capable camera found on this device"); return
            }
            cameraCharacteristics = androidCameraManager.getCameraCharacteristics(rawCameraId)
            val rawSize = chooseRawSize()
            rawImageReader = ImageReader.newInstance(
                rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2
            ).apply {
                // Images are processed on Thread B
                setOnImageAvailableListener(
                    { reader -> processHandler?.post { processRawImage(reader) } },
                    processHandler
                )
            }
            androidCameraManager.openCamera(rawCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera; createCaptureSession()
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close(); cameraDevice = null; _isReady.value = false
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close(); cameraDevice = null; _isReady.value = false
                    onError?.invoke("Camera device error: $error")
                }
            }, backgroundHandler)
        } catch (e: SecurityException) {
            onError?.invoke("Security exception: ${e.message}")
        } catch (e: CameraAccessException) {
            onError?.invoke("Camera access error: ${e.message}")
        }
    }

    private fun findRawCapableCamera(): String? {
        for (id in androidCameraManager.cameraIdList) {
            val ch = androidCameraManager.getCameraCharacteristics(id)
            val caps = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: continue
            val back = ch.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            if (back && caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) return id
        }
        for (id in androidCameraManager.cameraIdList) {
            val caps = androidCameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: continue
            if (caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) return id
        }
        return null
    }

    private fun chooseRawSize(): Size {
        val chars = cameraCharacteristics!!
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: throw IllegalStateException("No stream configuration map")
        val rawSizes = map.getOutputSizes(ImageFormat.RAW_SENSOR)
            ?: throw IllegalStateException("No RAW_SENSOR sizes")
        return rawSizes.maxByOrNull { it.width * it.height }!!
    }

    @Suppress("DEPRECATION")
    private fun createCaptureSession() {
        val device = cameraDevice ?: return
        val reader = rawImageReader ?: return
        val preview = previewSurface ?: return
        try {
            device.createCaptureSession(
                listOf(preview, reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session; startPreview(); _isReady.value = true
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        onError?.invoke("Session configuration failed")
                    }
                },
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            onError?.invoke("Session creation failed: ${e.message}")
        }
    }

    private fun startPreview() {
        val session = captureSession ?: return
        val preview = previewSurface ?: return
        val device = cameraDevice ?: return
        try {
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(preview); applyManualControls(this)
            }
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: CameraAccessException) {
            onError?.invoke("Preview start failed: ${e.message}")
        }
    }

    private fun applyManualControls(builder: CaptureRequest.Builder) {
        val chars = cameraCharacteristics ?: return
        if (currentParams.autoExposure) {
            // AE ON: camera decides exposure time
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
        } else {
            // Manual exposure (1 ms .. 10 min, from Settings slider)
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            builder.set(
                CaptureRequest.SENSOR_EXPOSURE_TIME,
                currentParams.exposureMs.coerceIn(EXPOSURE_RANGE_MS) * 1_000_000L
            )
            val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                ?: Range(ISO_RANGE.first, ISO_RANGE.last)
            builder.set(
                CaptureRequest.SENSOR_SENSITIVITY,
                currentParams.iso.coerceIn(ISO_RANGE).coerceIn(isoRange.lower..isoRange.upper)
            )
        }
        val focusRange = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val focusD = currentParams.focusDiopter.coerceIn(FOCUS_RANGE_D)
        builder.set(
            CaptureRequest.LENS_FOCUS_DISTANCE,
            if (focusD == 0f) 0f else (1f / focusD).coerceAtMost(focusRange)
        )
        builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
        if (currentParams.wbTemperature != null) {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
            builder.set(
                CaptureRequest.COLOR_CORRECTION_MODE,
                CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX
            )
            val neutral = arrayOf(
                android.util.Rational(1, 1), android.util.Rational(0, 1), android.util.Rational(0, 1),
                android.util.Rational(0, 1), android.util.Rational(1, 1), android.util.Rational(0, 1),
                android.util.Rational(0, 1), android.util.Rational(0, 1), android.util.Rational(1, 1)
            )
            builder.set(
                CaptureRequest.COLOR_CORRECTION_TRANSFORM,
                android.hardware.camera2.params.ColorSpaceTransform(neutral)
            )
        } else {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO)
        }
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_FAST)
        builder.set(CaptureRequest.SHADING_MODE, CameraMetadata.SHADING_MODE_FAST)
        builder.set(CaptureRequest.HOT_PIXEL_MODE, currentParams.hotPixelMode)
    }

    fun updateParams(params: CameraParams) {
        currentParams = params
        if (_isReady.value) startPreview()
    }

    fun captureRaw() {
        val device = cameraDevice ?: run { onError?.invoke("Camera not opened"); return }
        val session = captureSession ?: run { onError?.invoke("Session not ready"); return }
        val reader = rawImageReader ?: run { onError?.invoke("RAW reader not ready"); return }
        pendingCaptureResult = null
        captureFailed = false
        try {
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface); applyManualControls(this)
            }.build()
            // Callback delivers on Thread A; waiting happens on Thread B -> no deadlock
            session.capture(request, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult
                ) {
                    pendingCaptureResult = result
                }
                override fun onCaptureFailed(
                    s: CameraCaptureSession, r: CaptureRequest,
                    failure: android.hardware.camera2.CaptureFailure
                ) {
                    captureFailed = true
                    onError?.invoke("Capture failed: reason=${failure.reason}")
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            onError?.invoke("Capture dispatch failed: ${e.message}")
        }
    }

    /** Runs on Thread B; polls the volatile result delivered by Thread A. */
    private fun waitForResult(): TotalCaptureResult? {
        val deadline = System.currentTimeMillis() + RESULT_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            pendingCaptureResult?.let { return it }
            if (captureFailed) return null
            try { Thread.sleep(POLL_INTERVAL_MS) } catch (_: InterruptedException) { return null }
        }
        return pendingCaptureResult
    }

    private fun processRawImage(reader: ImageReader) {
        val image: Image = reader.acquireLatestImage() ?: return
        try {
            val result = waitForResult()
            if (result == null) {
                onError?.invoke("No capture result for this frame — discarded"); return
            }
            val saturation = computeSaturationRatio(image)
            if (saturation >= 0.01f) { onSaturationWarning?.invoke(saturation); return }
            val metadata = CaptureMetadata(
                exposureTimeNs = if (currentParams.autoExposure) {
                    result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L
                } else currentParams.exposureMs * 1_000_000L,
                iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: currentParams.iso,
                focusDistance = currentParams.focusDiopter,
                whiteBalance = currentParams.wbTemperature,
                sensorWidth = image.width,
                sensorHeight = image.height,
                saturationRatio = saturation,
                timestamp = System.currentTimeMillis()
            )
            _lastMetadata.value = metadata
            if (dngPolicy != DngPolicy.NONE) saveDng(image, metadata, result)
        } catch (e: Exception) {
            onError?.invoke("RAW processing failed: ${e.message}")
        } finally {
            image.close()
        }
    }

    private fun computeSaturationRatio(image: Image): Float {
        val plane = image.planes[0]; val buffer = plane.buffer
        val rowStride = plane.rowStride; val pixelStride = plane.pixelStride
        val width = image.width; val height = image.height
        val threshold = (16383 * 0.98f).toInt()
        var saturated = 0L; val step = 8
        for (y in 0 until height step step) {
            val rowStart = y * rowStride
            for (x in 0 until width step step) {
                val off = rowStart + x * pixelStride
                if (off + 1 < buffer.limit()) {
                    val v = (buffer.get(off).toInt() and 0xFF) or
                            ((buffer.get(off + 1).toInt() and 0xFF) shl 8)
                    if (v >= threshold) saturated++
                }
            }
        }
        val sampled = (width.toLong() * height / (step * step)).coerceAtLeast(1)
        return saturated.toFloat() / sampled.toFloat()
    }

    private fun saveDng(image: Image, metadata: CaptureMetadata, result: TotalCaptureResult) {
        val chars = cameraCharacteristics ?: return
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val file = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "FSSA_RAW_$ts.dng"
        )
        try {
            val dngCreator = DngCreator(chars, result)
            dngCreator.setOrientation(android.media.ExifInterface.ORIENTATION_NORMAL)
            FileOutputStream(file).use { dngCreator.writeImage(it, image) }
            dngCreator.close()
            onDngSaved?.invoke(file, metadata)
            Log.d(TAG, "DNG saved: ${file.absolutePath}")
        } catch (e: Exception) {
            onError?.invoke("DNG save failed: ${e.message}"); file.delete()
        }
    }

    fun close() {
        try { captureSession?.close(); cameraDevice?.close(); rawImageReader?.close() } catch (_: Exception) {}
        captureSession = null; cameraDevice = null; rawImageReader = null; previewSurface = null
        _isReady.value = false
        stopThreads()
    }
}