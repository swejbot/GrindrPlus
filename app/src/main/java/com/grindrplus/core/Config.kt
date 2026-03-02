package com.grindrplus.core

import android.content.Context
import com.grindrplus.core.model.CloneSettings
import com.grindrplus.core.model.GlobalSettings
import org.json.JSONObject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


class Config(
    val getConfig: () -> String,
    var currentPackageName: String = Constants.GRINDR_PACKAGE_NAME,
    val onChange: (value: String) -> Unit = {}
) {
    companion object {
        val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    }
//    private var localConfig = JSONObject()
    var settings: GlobalSettings = GlobalSettings()
        private set


    init {
        import(getConfig())
    }


    fun update(updater: (settings: GlobalSettings) -> GlobalSettings) {
        settings = updater(settings)
        configUpdated()
    }

    fun updateClone(packageName: String, updater: (cloneSettings: CloneSettings) -> CloneSettings) {
        val newClones = updater(getCloneSettings(packageName))
        settings = settings.withClone(packageName, newClones)
        configUpdated()
    }

    private fun configUpdated() {
        onChange(export())
    }

    private fun migrateToMultiCloneFormat(localConfig: JSONObject): JSONObject {
        val GLOBAL_SETTINGS = listOf("first_launch", "analytics", "discreet_icon", "material_you", "debug_mode", "disable_permission_checks", "custom_manifest", "maps_api_key", "last_push_id")
        fun isGlobalSetting(name: String): Boolean {
            return name in GLOBAL_SETTINGS
        }

        if (!localConfig.has("clones")) {
            Logger.d("Migrating to multi-clone format", LogSource.MANAGER)
            val cloneSettings = JSONObject()

            if (localConfig.has("hooks")) {
                val defaultPackageConfig = JSONObject()
                defaultPackageConfig.put("hooks", localConfig.get("hooks"))
                cloneSettings.put(Constants.GRINDR_PACKAGE_NAME, defaultPackageConfig)

                val keysToMove = mutableListOf<String>()
                val keys = localConfig.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key != "hooks" && !isGlobalSetting(key)) {
                        defaultPackageConfig.put(key, localConfig.get(key))
                        keysToMove.add(key)
                    }
                }
                keysToMove.forEach { localConfig.remove(it) }
            } else {
                cloneSettings.put(Constants.GRINDR_PACKAGE_NAME, JSONObject().put("hooks", JSONObject()))
            }

            localConfig.put("clones", cloneSettings)
        }

        return localConfig
    }

    private fun ensurePackageExists(packageName: String) {
        Logger.d("Ensuring package $packageName exists in config", LogSource.MANAGER)

        if (!settings.clones.containsKey(packageName)) {
            settings = settings.withClone(packageName, CloneSettings())
            configUpdated()
        }
    }

    fun getClonePackageNames(context: Context): List<String> {
        Logger.d("Getting available packages", LogSource.MANAGER)
        return settings.clones.keys.toList()
    }

    fun getCloneSettings(packageName: String): CloneSettings {
        return settings.clones[packageName] ?: CloneSettings()
    }

    fun import(content: String) {
        try {
            val configUpgraded = migrateToMultiCloneFormat(JSONObject(content))
            ensurePackageExists(currentPackageName)

            settings = json.decodeFromString<GlobalSettings>(configUpgraded.toString())

            configUpdated()
        } catch (e: Exception) {
            Logger.e("Failed to import valid configuration: ${e.message}", LogSource.MANAGER)
            throw e
        }
    }

    fun export(): String {
        return json.encodeToString(settings)
    }

    fun registerClones(existingClones: List<String>) {
        for (packageName in existingClones) {
            if (!settings.clones.containsKey(packageName)) {
                settings = settings.withClone(packageName, CloneSettings())
            }
        }
    }
}