package com.grindrplus.core.repository

import android.content.Context
import com.grindrplus.core.LogSource
import com.grindrplus.core.Logger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class LogRepository(
    val context: Context
) {
    private val logFile by lazy { File(context.getExternalFilesDir(null), "grindrplus.log") }
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val logLock = ReentrantLock()
    private val MAX_LOG_SIZE = 5 * 1024 * 1024


    init {
        ioExecutor.execute {
            try {
                if (!logFile.exists()) {
                    logFile.createNewFile()
                }
            } catch (e: Exception) {
                Logger.e("Failed to initialize files: ${e.message}", LogSource.BRIDGE)
                Logger.writeRaw(e.stackTraceToString())
            }
        }
    }

    fun log(level: String, source: String, message: String, hookName: String?) {
        ioExecutor.execute {
            try {
                checkAndManageLogSize()
                val formattedLog = formatLogEntry(level, source, message, hookName)
                appendToLog(formattedLog)
            } catch (e: Exception) {
                Logger.e("Error writing log entry", LogSource.BRIDGE)
                Logger.writeRaw(e.stackTraceToString())
            }
        }
    }

    fun writeRawLog(content: String) {
        ioExecutor.execute {
            try {
                checkAndManageLogSize()
                appendToLog(content + (if (!content.endsWith("\n")) "\n" else ""))
            } catch (e: Exception) {
                Logger.e("Error writing raw log entry", LogSource.BRIDGE)
                Logger.writeRaw(e.stackTraceToString())
            }
        }
    }

    fun clearLogs() {
            Logger.d("clearLogs() called")
            try {
                logLock.withLock {
                    if (logFile.exists()) {
                        logFile.delete()
                        logFile.createNewFile()
                    }
                }
            } catch (e: Exception) {
                Logger.e("Error clearing log file", LogSource.BRIDGE)
                Logger.writeRaw(e.stackTraceToString())
            }
        }

    private fun formatLogEntry(
        level: String,
        source: String,
        message: String,
        hookName: String?
    ): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            .format(Date())

        return if (hookName != null) {
            "[$timestamp][$source][$level][$hookName] $message\n"
        } else {
            "[$timestamp][$source][$level] $message\n"
        }
    }

    private fun checkAndManageLogSize() {
        logLock.withLock {
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                val backupFile = File("${logFile.absolutePath}.bak")
                backupFile.takeIf { it.exists() }?.delete()

                logFile.renameTo(backupFile)
                logFile.createNewFile()

                val rotationMessage = "I/${
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(Date())
                }/system: Log file rotated due to size limit\n"
                logFile.appendText(rotationMessage)
            }
        }
    }

    private fun appendToLog(content: String) {
        logLock.withLock {
            if (!logFile.exists()) {
                logFile.createNewFile()
            }
            logFile.appendText(content)
        }
    }


    companion object {
        private const val TAG = "BridgeService"
        const val CHANNEL_BLOCKS = "grindr_plus_blocks"
        const val CHANNEL_UNBLOCKS = "grindr_plus_unblocks"
        const val CHANNEL_GENERAL = "grindr_plus_general"
    }
}