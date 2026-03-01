package com.grindrplus.core.repository

import android.content.Context
import com.grindrplus.core.LogSource
import com.grindrplus.core.Logger
import java.io.File

class ConfigRepository(
    val context: Context
) {
    private val configFile by lazy { File(context.getExternalFilesDir(null), "grindrplus.json") }

    fun getConfig(): String {
        Logger.d("getConfig() called")
        return try {
            if (!configFile.exists()) {
                configFile.createNewFile()
                "{}"
            } else {
                configFile.readText().ifBlank { "{}" }
            }
        } catch (e: Exception) {
            Logger.e("Error reading config file", LogSource.BRIDGE)
            Logger.writeRaw(e.stackTraceToString())
            throw e
        }
    }

    fun setConfig(config: String?) {
        if (config == null) {
            Logger.w("setConfig() called with null value, ignoring")
            return
        }

        Logger.d("setConfig() called")
        try {
            if (!configFile.exists()) {
                configFile.createNewFile()
            }

            configFile.writeText(config)

        } catch (e: Exception) {
            Logger.e("Error writing to config file", LogSource.BRIDGE)
            Logger.writeRaw(e.stackTraceToString())
        }
    }
}