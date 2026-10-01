package org.fungalsentinel.app

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.fungalsentinel.app.data.database.AppDatabase
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * FSSA Application。
 *
 * 职责：
 * 1. 初始化 Room 数据库
 * 2. 提供全局诊断日志系统（仅本地，不自动上传）
 * 3. 捕获未处理异常并写入日志
 */
class FssaApplication : Application() {

    lateinit var database: AppDatabase
        private set

    // ── 诊断日志系统 ──
    private val logQueue = ConcurrentLinkedQueue<String>()
    private val _logSize = MutableStateFlow(0L)
    val logSize: StateFlow<Long> = _logSize.asStateFlow()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "FSSA"
        private const val MAX_LOG_SIZE_BYTES = 2L * 1024 * 1024  // 2 MB
        private const val LOG_FILE_NAME = "fssa_diagnostic.log"
    }

    override fun onCreate() {
        super.onCreate()

        // 1. 初始化数据库
        database = AppDatabase.getInstance(this)

        // 2. 安装全局异常处理器
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                logException("UncaughtException", throwable, mapOf("thread" to thread.name))
                // 给日志一点时间落盘
                Thread.sleep(500)
            } catch (_: Exception) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // 3. 启动日志清理协程
        appScope.launch { trimLogFileIfNeeded() }

        logInfo("Application started")
    }

    // ─────────────────────────────────────────────
    // 日志 API
    // ─────────────────────────────────────────────
    fun logInfo(message: String, metadata: Map<String, Any> = emptyMap()) {
        writeLog("INFO", message, metadata)
    }

    fun logWarning(message: String, metadata: Map<String, Any> = emptyMap()) {
        writeLog("WARN", message, metadata)
    }

    fun logError(message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap()) {
        writeLog("ERROR", message, metadata, throwable)
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        }
    }

    fun logException(tag: String, throwable: Throwable, metadata: Map<String, Any> = emptyMap()) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        writeLog("EXCEPTION", "[$tag] ${throwable.message}", metadata, throwable)
    }

    private fun writeLog(
        level: String,
        message: String,
        metadata: Map<String, Any>,
        throwable: Throwable? = null
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val metaStr = if (metadata.isEmpty()) "" else " | ${metadata.entries.joinToString { "${it.key}=${it.value}" }}"
        val line = "[$timestamp] [$level] $message$metaStr\n"

        logQueue.offer(line)
        Log.d(TAG, "$level: $message")

        appScope.launch { flushLogsToDisk() }
    }

    // ─────────────────────────────────────────────
    // 日志落盘
    // ─────────────────────────────────────────────
    private fun flushLogsToDisk() {
        val logFile = File(filesDir, LOG_FILE_NAME)
        try {
            FileOutputStream(logFile, true).use { fos ->
                while (logQueue.isNotEmpty()) {
                    val line = logQueue.poll() ?: break
                    fos.write(line.toByteArray())
                }
            }
            _logSize.value = logFile.length()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log", e)
        }
    }

    private suspend fun trimLogFileIfNeeded() {
        val logFile = File(filesDir, LOG_FILE_NAME)
        if (logFile.exists() && logFile.length() > MAX_LOG_SIZE_BYTES) {
            // 保留后半部分（最新日志）
            val bytes = logFile.readBytes()
            val keep = bytes.takeLast((MAX_LOG_SIZE_BYTES * 0.8).toInt()).toByteArray()
            logFile.writeBytes(keep)
            _logSize.value = logFile.length()
        }
    }

    // ─────────────────────────────────────────────
    // 用户操作：保存 / 清空日志
    // ─────────────────────────────────────────────
    fun getLogFile(): File = File(filesDir, LOG_FILE_NAME)

    fun clearLog() {
        appScope.launch {
            val logFile = File(filesDir, LOG_FILE_NAME)
            if (logFile.exists()) logFile.delete()
            _logSize.value = 0L
            logInfo("Diagnostic log cleared by user")
        }
    }
}