package org.fungalsentinel.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.fungalsentinel.app.data.database.AppDatabase
import org.fungalsentinel.app.data.preset.PresetDatabase
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Application entry.
 *
 * Responsibilities:
 * 1. Initialize the experiment database (Room) and the preset database (Room).
 * 2. Provide a local-only rolling diagnostic log (<= 2 MB, never uploaded).
 * 3. Capture uncaught exceptions into the diagnostic log before the process dies.
 * 4. Offer log export (share sheet) and clear actions for the Settings page.
 */
class FssaApplication : Application() {

    // ── Databases ──
    lateinit var database: AppDatabase
        private set

    lateinit var presetDatabase: PresetDatabase
        private set

    // ── Diagnostic log state ──
    private val logQueue = ConcurrentLinkedQueue<String>()

    private val _logSize = MutableStateFlow(0L)
    val logSize: StateFlow<Long> = _logSize.asStateFlow()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "FSSA"
        private const val MAX_LOG_SIZE_BYTES = 2L * 1024 * 1024   // 2 MB rolling cap
        private const val LOG_FILE_NAME = "fssa_diagnostic.log"
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Databases
        database = AppDatabase.getInstance(this)
        presetDatabase = PresetDatabase.getInstance(this)

        // 2. Global crash capture: write the stack trace into the diagnostic log
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                logException("UncaughtException on thread '${thread.name}'", throwable)
                flushLogsToDisk()   // synchronous: give the log a chance to land
            } catch (_: Exception) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // 3. Trim oversized log from a previous run, then announce startup
        appScope.launch { trimLogFileIfNeeded() }
        logInfo(
            "Application started",
            mapOf(
                "version" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                "device" to android.os.Build.MODEL
            )
        )
    }

    // ─────────────────────────────────────────────
    // Logging API (call from anywhere in the app)
    // ─────────────────────────────────────────────
    fun logInfo(message: String, metadata: Map<String, Any> = emptyMap()) {
        writeLog("INFO", message, metadata)
    }

    fun logWarning(message: String, metadata: Map<String, Any> = emptyMap()) {
        writeLog("WARN", message, metadata)
    }

    fun logError(
        message: String,
        throwable: Throwable? = null,
        metadata: Map<String, Any> = emptyMap()
    ) {
        writeLog("ERROR", message, metadata)
        if (throwable != null) Log.e(TAG, message, throwable)
    }

    fun logException(tag: String, throwable: Throwable, metadata: Map<String, Any> = emptyMap()) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        writeLog("EXCEPTION", "[$tag] ${throwable.message}\n$sw", metadata)
    }

    private fun writeLog(level: String, message: String, metadata: Map<String, Any>) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val meta = if (metadata.isEmpty()) ""
        else " | " + metadata.entries.joinToString(separator = " ") { "${it.key}=${it.value}" }
        logQueue.offer("[$timestamp] [$level] $message$meta\n")
        Log.d(TAG, "$level: $message")
        appScope.launch { flushLogsToDisk() }
    }

    // ─────────────────────────────────────────────
    // Disk handling
    // ─────────────────────────────────────────────
    private fun flushLogsToDisk() {
        try {
            val logFile = File(filesDir, LOG_FILE_NAME)
            FileOutputStream(logFile, true).use { fos ->
                while (true) {
                    val line = logQueue.poll() ?: break
                    fos.write(line.toByteArray())
                }
            }
            _logSize.value = logFile.length()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write diagnostic log", e)
        }
    }

    private suspend fun trimLogFileIfNeeded() {
        val logFile = File(filesDir, LOG_FILE_NAME)
        if (logFile.exists() && logFile.length() > MAX_LOG_SIZE_BYTES) {
            // Keep the newest 80% so recent context survives the trim
            val bytes = logFile.readBytes()
            val keep = bytes.takeLast((MAX_LOG_SIZE_BYTES * 0.8).toInt()).toByteArray()
            logFile.writeBytes(keep)
        }
        _logSize.value = logFile.length()
    }

    // ─────────────────────────────────────────────
    // User-facing actions (Settings page)
    // ─────────────────────────────────────────────
    fun getLogFile(): File = File(filesDir, LOG_FILE_NAME)

    fun clearLog() {
        appScope.launch {
            val logFile = File(filesDir, LOG_FILE_NAME)
            if (logFile.exists()) logFile.delete()
            logQueue.clear()
            _logSize.value = 0L
            logInfo("Diagnostic log cleared by user")
        }
    }

    /**
     * Share/export the diagnostic log through the system share sheet.
     * The log is copied into cacheDir first so FileProvider can serve it
     * (cache-path is already declared in res/xml/file_paths.xml).
     */
    fun shareLog(context: Context) {
        val src = getLogFile()
        if (!src.exists()) {
            logWarning("Share requested but diagnostic log does not exist")
            return
        }
        try {
            val copy = File(cacheDir, src.name)
            src.copyTo(copy, overwrite = true)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", copy)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Save diagnostic log"))
            logInfo("Diagnostic log shared by user", mapOf("bytes" to src.length()))
        } catch (e: Exception) {
            logError("Failed to share diagnostic log", e)
        }
    }
}