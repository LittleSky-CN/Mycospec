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
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
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
import org.fungalsentinel.app.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.annotation.SuppressLint

enum class DngPolicy { ALL, SAMPLES_ONLY, NONE }

data class CameraParams(
    val exposureMs: Long = 100L,
    val iso: Int = 100,
    val focusDiopter: Float = 0f,
    val wbTemperature: Int? = null,
    val autoExposure: Boolean = true,
    val autoFocus: Boolean = true,
    val hotPixelMode: Int = CameraMetadata.HOT_PIXEL_MODE_FAST
)

data class CaptureMetadata(
    val exposureTimeNs: Long, val iso: Int, val focusDistance: Float,
    val whiteBalance: Int?, val sensorWidth: Int, val sensorHeight: Int,
    val saturationRatio: Float, val timestamp: Long
)

class CameraManager(private val context: Context) {
    companion object {
        private const val TAG = "CameraManager"
        private val EXPOSURE_RANGE_MS = 1L..600_000L
        private val ISO_RANGE = 50..1600
        private val FOCUS_RANGE_D = 0f..5f
        private const val RESULT_WAIT_MS = 3000L     // 【v2.2.6】10s → 3s，缩短阻塞窗口
        private const val POLL_INTERVAL_MS = 20L
        private const val CAPTURE_TIMEOUT_MS = 30_000L
    }

    private val androidCameraManager: AndroidCameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as AndroidCameraManager
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var processThread: HandlerThread? = null
    private var processHandler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var rawImageReader: ImageReader? = null
    private var cameraCharacteristics: CameraCharacteristics? = null
    private var currentParams = CameraParams()
    private val captureStateLock = Any()
    private val cameraOpenLock = Any()
    private var cameraOpenGeneration = 0L
    private var cameraOpening = false

    var dngPolicy: DngPolicy = DngPolicy.ALL

    @Volatile private var pendingCaptureResult: TotalCaptureResult? = null
    @Volatile private var captureFailed = false
    @Volatile private var lastPreviewResult: TotalCaptureResult? = null
    @Volatile private var captureInFlight = false   // 【v2.2.6】单帧在途互斥，防连点排队卡死
    @Volatile private var captureTimeout: Runnable? = null

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()
    private val _lastMetadata = MutableStateFlow<CaptureMetadata?>(null)
    val lastMetadata: StateFlow<CaptureMetadata?> = _lastMetadata.asStateFlow()

    var onDngSaved: ((File, CaptureMetadata) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onSaturationWarning: ((Float) -> Unit)? = null

    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startThreads() {
        if (backgroundThread == null) { backgroundThread = HandlerThread("CameraSession").also { it.start() }; backgroundHandler = Handler(backgroundThread!!.looper) }
        if (processThread == null) { processThread = HandlerThread("RawProcess").also { it.start() }; processHandler = Handler(processThread!!.looper) }
    }

    private fun stopThreads() {
        listOf(backgroundThread, processThread).forEach { t -> t?.quitSafely(); try { t?.join() } catch (_: InterruptedException) {} }
        backgroundThread = null; backgroundHandler = null; processThread = null; processHandler = null
    }

    @SuppressLint("MissingPermission")
    fun openCamera(previewSurface: Surface) {
        if (!hasCameraPermission()) { onError?.invoke("Camera permission not granted"); return }
        val generation = synchronized(cameraOpenLock) {
            if (cameraOpening || cameraDevice != null) return
            cameraOpening = true
            cameraOpenGeneration += 1
            this.previewSurface = previewSurface
            cameraOpenGeneration
        }
        startThreads()
        _isReady.value = false
        try {
            val rawCameraId = findRawCapableCamera() ?: throw IllegalStateException("No RAW-capable camera found")
            cameraCharacteristics = androidCameraManager.getCameraCharacteristics(rawCameraId)
            val rawSize = chooseRawSize()
            rawImageReader = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2).apply {
                setOnImageAvailableListener({ reader -> processHandler?.post { processRawImage(reader) } }, processHandler)
            }
            androidCameraManager.openCamera(rawCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    val accepted = synchronized(cameraOpenLock) {
                        if (generation != cameraOpenGeneration) false
                        else {
                            cameraOpening = false
                            cameraDevice = camera
                            true
                        }
                    }
                    if (accepted) createCaptureSession(generation) else camera.close()
                }
                override fun onDisconnected(camera: CameraDevice) {
                    synchronized(cameraOpenLock) {
                        if (generation == cameraOpenGeneration) {
                            cameraOpening = false
                            if (cameraDevice === camera) cameraDevice = null
                        }
                    }
                    camera.close()
                    _isReady.value = false
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    synchronized(cameraOpenLock) {
                        if (generation == cameraOpenGeneration) {
                            cameraOpening = false
                            if (cameraDevice === camera) cameraDevice = null
                        }
                    }
                    camera.close(); _isReady.value = false
                    captureInFlight = false
                    onError?.invoke("Camera device error: $error")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            synchronized(cameraOpenLock) {
                if (generation == cameraOpenGeneration) cameraOpening = false
            }
            onError?.invoke("Camera access error: ${e.message}")
        }
    }

    private fun findRawCapableCamera(): String? {
        for (id in androidCameraManager.cameraIdList) {
            val caps = androidCameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: continue
            if (caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) return id
        }
        return null
    }

    private fun chooseRawSize(): Size {
        val map = cameraCharacteristics!!.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: throw IllegalStateException("No stream config")
        return map.getOutputSizes(ImageFormat.RAW_SENSOR)?.maxByOrNull { it.width * it.height }
            ?: throw IllegalStateException("No RAW sizes")
    }

    @Suppress("DEPRECATION")
    private fun createCaptureSession(generation: Long) {
        try {
            val device = synchronized(cameraOpenLock) {
                if (generation != cameraOpenGeneration) return
                cameraDevice
            } ?: return
            device.createCaptureSession(
                listOf(previewSurface!!, rawImageReader!!.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        val accepted = synchronized(cameraOpenLock) {
                            if (generation != cameraOpenGeneration || cameraDevice !== device) false
                            else {
                                captureSession = session
                                true
                            }
                        }
                        if (!accepted) {
                            session.close()
                            return
                        }
                        startPreview()
                        _isReady.value = true
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        session.close()
                        onError?.invoke("Session configuration failed")
                    }
                }, backgroundHandler
            )
        } catch (e: CameraAccessException) { onError?.invoke("Session creation failed: ${e.message}") }
    }

    private fun startPreview() {
        try {
            val session = captureSession ?: return
            val builder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                .apply { addTarget(previewSurface!!); applyManualControls(this) }
            session.setRepeatingRequest(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
                    lastPreviewResult = result
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) { onError?.invoke("Preview start failed: ${e.message}") }
    }

    private fun applyManualControls(builder: CaptureRequest.Builder) {
        val chars = cameraCharacteristics ?: return
        if (currentParams.autoExposure) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
        } else {
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
        if (currentParams.autoFocus) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        } else {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            val focusRange = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            val focusD = currentParams.focusDiopter.coerceIn(FOCUS_RANGE_D)
            builder.set(
                CaptureRequest.LENS_FOCUS_DISTANCE,
                if (focusD == 0f) 0f else (1f / focusD).coerceAtMost(focusRange)
            )
        }
        if (currentParams.wbTemperature != null) {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
            builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
            val neutral = arrayOf(
                android.util.Rational(1, 1), android.util.Rational(0, 1), android.util.Rational(0, 1),
                android.util.Rational(0, 1), android.util.Rational(1, 1), android.util.Rational(0, 1),
                android.util.Rational(0, 1), android.util.Rational(0, 1), android.util.Rational(1, 1)
            )
            builder.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, android.hardware.camera2.params.ColorSpaceTransform(neutral))
        } else {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO)
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_FAST)
            builder.set(CaptureRequest.SHADING_MODE, CameraMetadata.SHADING_MODE_FAST)
            builder.set(CaptureRequest.HOT_PIXEL_MODE, currentParams.hotPixelMode)
        }
    }

    fun updateParams(params: CameraParams) {
        currentParams = params
        if (_isReady.value && !captureInFlight) startPreview()
    }

    /** 【v2.2.6】OnePlus/ColorOS 会话阻塞规避：拍摄前 stopRepeating，处理完重启预览；互斥锁防排队 */
    fun captureRaw() {
        val session = captureSession ?: run { onError?.invoke("Session not ready"); return }
        val device = cameraDevice ?: run { onError?.invoke("Camera not open"); return }
        val started = synchronized(captureStateLock) {
            if (captureInFlight) false
            else {
                captureInFlight = true
                _isCapturing.value = true
                true
            }
        }
        if (!started) { onError?.invoke("Capture busy — previous frame still processing"); return }
        try {
            pendingCaptureResult = null; captureFailed = false
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                .apply { addTarget(rawImageReader!!.surface); applyManualControls(this) }.build()
            try {
                session.stopRepeating()
            } catch (e: CameraAccessException) {
                onError?.invoke("Could not prepare camera for capture: ${e.message}")
                finishCaptureCycle()
                return
            }
            lateinit var timeout: Runnable
            timeout = Runnable {
                val didTimeout = synchronized(captureStateLock) {
                    if (captureTimeout !== timeout || !captureInFlight) false
                    else {
                        captureTimeout = null
                        captureInFlight = false
                        _isCapturing.value = false
                        true
                    }
                }
                if (!didTimeout) return@Runnable
                onError?.invoke(context.getString(R.string.cam_err_capture_timeout))
                try {
                    session.abortCaptures()
                } catch (e: CameraAccessException) {
                    onError?.invoke(context.getString(R.string.cam_err_capture_recovery, e.message))
                }
                if (_isReady.value) startPreview()
            }
            synchronized(captureStateLock) { captureTimeout = timeout }
            if (backgroundHandler?.postDelayed(timeout, CAPTURE_TIMEOUT_MS) != true) {
                onError?.invoke("Could not schedule capture timeout")
                finishCaptureCycle()
                return
            }
            session.capture(request, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
                    pendingCaptureResult = result
                }
                override fun onCaptureFailed(s: CameraCaptureSession, r: CaptureRequest, f: android.hardware.camera2.CaptureFailure) {
                    captureFailed = true
                    onError?.invoke("Capture failed: reason=${f.reason}")
                    finishCaptureCycle()
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            onError?.invoke("Capture dispatch failed: ${e.message}")
            finishCaptureCycle()
        }
    }

    private fun cancelCaptureTimeout() {
        val timeout = synchronized(captureStateLock) {
            captureTimeout.also { captureTimeout = null }
        }
        timeout?.let { backgroundHandler?.removeCallbacks(it) }
    }

    private fun finishCaptureCycle() {
        cancelCaptureTimeout()
        synchronized(captureStateLock) {
            captureInFlight = false
            _isCapturing.value = false
        }
        if (_isReady.value) startPreview()
    }

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
        val image: Image = reader.acquireLatestImage() ?: run { finishCaptureCycle(); return }
        if (!captureInFlight) {
            image.close()
            return
        }
        cancelCaptureTimeout()
        try {
            val result = waitForResult() ?: lastPreviewResult
            if (result == null) {
                onError?.invoke("No capture result available (callback lost)")
                return
            }
            val saturation = computeSaturationRatio(image)
            if (saturation >= 0.01f) { onSaturationWarning?.invoke(saturation); return }

            val metadata = CaptureMetadata(
                exposureTimeNs = if (currentParams.autoExposure)
                    result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: (currentParams.exposureMs * 1_000_000L)
                else currentParams.exposureMs * 1_000_000L,
                iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: currentParams.iso,
                focusDistance = currentParams.focusDiopter,
                whiteBalance = currentParams.wbTemperature,
                sensorWidth = image.width, sensorHeight = image.height,
                saturationRatio = saturation, timestamp = System.currentTimeMillis()
            )
            _lastMetadata.value = metadata
            if (dngPolicy != DngPolicy.NONE) saveDng(image, metadata, result)
        } catch (e: Exception) {
            onError?.invoke("RAW processing failed: ${e.message}")
        } finally {
            image.close()
            finishCaptureCycle()
        }
    }

    /** 【v2.2.6】白电平自适应：按传感器实际位深判定饱和，兼容 10/12/14/16-bit */
    private fun computeSaturationRatio(image: Image): Float {
        val white = cameraCharacteristics?.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL) ?: 16383
        val threshold = (white * 0.98f).toInt()
        val plane = image.planes[0]; val buffer = plane.buffer
        val rowStride = plane.rowStride; val pixelStride = plane.pixelStride
        val width = image.width; val height = image.height
        var saturated = 0L; val step = 8
        for (y in 0 until height step step) {
            val rowStart = y * rowStride
            for (x in 0 until width step step) {
                val off = rowStart + x * pixelStride
                if (off + 1 < buffer.limit()) {
                    val v = (buffer.get(off).toInt() and 0xFF) or ((buffer.get(off + 1).toInt() and 0xFF) shl 8)
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
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "MycoSpec_RAW_$ts.dng")
        try {
            val dngCreator = DngCreator(chars, result)
            dngCreator.setOrientation(android.media.ExifInterface.ORIENTATION_NORMAL)
            FileOutputStream(file).use { dngCreator.writeImage(it, image) }
            dngCreator.close()
            file.canonicalFile.setLastModified(System.currentTimeMillis())
            if (file.length() < 1024) {
                onError?.invoke("DNG file too small (${file.length()} bytes) — possible write failure")
                file.delete()
                return
            }
            onDngSaved?.invoke(file, metadata)
            Log.d(TAG, "DNG saved: ${file.absolutePath} (${file.length()} bytes)")
        } catch (e: Exception) {
            onError?.invoke("DNG save failed: ${e.message}")
            file.delete()
        }
    }

    fun close() {
        cancelCaptureTimeout()
        synchronized(cameraOpenLock) {
            cameraOpenGeneration += 1
            cameraOpening = false
        }
        try { captureSession?.close(); cameraDevice?.close(); rawImageReader?.close() } catch (_: Exception) {}
        captureSession = null
        synchronized(cameraOpenLock) { cameraDevice = null }
        rawImageReader = null; previewSurface = null
        synchronized(captureStateLock) {
            captureInFlight = false
            _isCapturing.value = false
        }
        _isReady.value = false
        stopThreads()
    }
}