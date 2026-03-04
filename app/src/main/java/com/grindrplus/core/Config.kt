package com.grindrplus.core

import com.grindrplus.core.model.CloneSettings
import com.grindrplus.core.model.GlobalSettings
import org.json.JSONObject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


class Config(
    val getConfig: () -> String,
    val onChange: (value: GlobalSettings) -> Unit = {}
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }

        fun deserialize(value: String): GlobalSettings =
            json.decodeFromString(value)

        fun serialize(settings: GlobalSettings): String =
            json.encodeToString(settings)

    }

//    private var localConfig = JSONObject()
    var settings: GlobalSettings = GlobalSettings()
        private set


    init {
        import(getConfig())
    }


    fun update(updater: (settings: GlobalSettings) -> GlobalSettings) {
        val newSettings = updater(settings)

        if (newSettings != settings) {
            settings = newSettings
            configUpdated()
        }
    }

    fun updateClone(packageName: String, cloneUpdater: (cloneSettings: CloneSettings) -> CloneSettings) {
        update {
            val newClones = cloneUpdater(it.getClone(packageName))
            it.withClone(packageName, newClones)
        }
    }

    private fun configUpdated() {
        onChange(settings)
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

    fun import(content: String) {
        try {
            val configUpgraded = migrateToMultiCloneFormat(JSONObject(content))

            settings = deserialize(configUpgraded.toString())

            configUpdated()
        } catch (e: Exception) {
            Logger.e("Failed to import valid configuration: ${e.message}", LogSource.MANAGER)
            throw e
        }
    }

    fun registerClones(existingClones: List<String>) {
        for (packageName in existingClones) {
            if (!settings.clones.containsKey(packageName)) {
                settings = settings.withClone(packageName, CloneSettings())
            }
        }
    }
}