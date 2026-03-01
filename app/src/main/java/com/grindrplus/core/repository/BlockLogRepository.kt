package com.grindrplus.core.repository

import android.content.Context
import com.grindrplus.core.LogSource
import com.grindrplus.core.Logger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class BlockLogRepository(
    val context: Context
) {
    private val blockEventsFile by lazy { File(context.getExternalFilesDir(null), "block_events.json") }
    private val blockEventsLock = ReentrantLock()
    private val ioExecutor = Executors.newSingleThreadExecutor()


    init {
        ioExecutor.execute {
            try {
                if (!blockEventsFile.exists()) {
                    blockEventsFile.createNewFile()
                    blockEventsFile.writeText("[]")
                }
            } catch (e: Exception) {
                Logger.e("Failed to initialize files: ${e.message}", LogSource.BRIDGE)
                Logger.writeRaw(e.stackTraceToString())
            }
        }
    }

    fun logBlockEvent(
        profileId: String,
        displayName: String,
        isBlock: Boolean,
        packageName: String
    ) {
        ioExecutor.execute {
            try {
                blockEventsLock.withLock {
                    if (!blockEventsFile.exists()) {
                        blockEventsFile.createNewFile()
                        blockEventsFile.writeText("[]")
                    }

                    val eventsArray = JSONArray(blockEventsFile.readText().ifBlank { "[]" })
                    val event = JSONObject().apply {
                        put("profileId", profileId)
                        put("displayName", displayName)
                        put("eventType", if (isBlock) "block" else "unblock")
                        put("timestamp", System.currentTimeMillis())
                        put("packageName", packageName)
                    }
                    eventsArray.put(event)
                    blockEventsFile.writeText(eventsArray.toString(4))
                    Logger.d(
                        "Logged ${if (isBlock) "block" else "unblock"} event " +
                                "for profile ${profileId.take(profileId.length - 4) + "****"}",
                        LogSource.BRIDGE
                    )
                }
            } catch (e: Exception) {
                Logger.e("Error logging block event", LogSource.BRIDGE)
            }
        }
    }

    fun getBlockEvents(): String {
        return try {
            if (!blockEventsFile.exists()) {
                blockEventsFile.createNewFile()
                "[]"
            } else {
                blockEventsFile.readText().ifBlank { "[]" }
            }
        } catch (e: Exception) {
            Logger.e("Error reading block events file", LogSource.BRIDGE)
            Logger.writeRaw(e.stackTraceToString())
            "[]"
        }
    }

    fun clearBlockEvents() {
        blockEventsLock.withLock {
            try {
                if (blockEventsFile.exists()) {
                    blockEventsFile.delete()
                    blockEventsFile.createNewFile()
                    blockEventsFile.writeText("[]")
                }
            } catch (e: Exception) {
                Logger.e("Error clearing block events file", LogSource.BRIDGE)
                Logger.writeRaw(e.stackTraceToString())
            }
        }
    }
}