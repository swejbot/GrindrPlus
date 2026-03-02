package com.grindrplus.core

import android.content.Context
import org.json.JSONObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class GlobalSettings(
    var first_launch: Boolean = true,
    var analytics: Boolean = true,
    var discreet_icon: Boolean = false,
    var material_you: Boolean = false,
    var debug_mode: Boolean = false,
    var disable_permission_checks: Boolean = false,
    var custom_manifest: String = "",
    var maps_api_key: String = "",
    var last_push_id: String = "",

    var favorites_import_threshold: Int = 500,
    var calculator_first_launch: Boolean = true,

    var clones: MutableMap<String, CloneSettings> = mutableMapOf()
)

@Serializable
data class HookTaskSettings(
    var description: String = "",
    var enabled: Boolean = false
)

@Serializable
data class CloneSettings(
    var hooks: MutableMap<String, HookTaskSettings> = mutableMapOf(),
    var tasks: MutableMap<String, HookTaskSettings> = mutableMapOf(),

    var show_bmi_in_profile: Boolean = true,
    
    var enable_cookie_tap: Boolean = false,
    var enable_vip_flag: Boolean = false,
    var enable_interest_section: Boolean = true,
    var disable_profile_swipe: Boolean = false,
    
    var force_old_anti_block_behavior: Boolean = false,
    var anti_block_use_toasts: Boolean = false,

    var current_location: String = "",
    var current_location_name: String = "",


    var command_prefix: String = "/",
    var date_format: String = "MM/dd/yyyy",
    var online_indicator: Int = 3,
    var favorites_grid_columns: Int = 3,
    var forced_coordinates: String = "",

    var reset_database: Boolean = false,
    var do_gui_safety_checks: Boolean = true,
    var android_device_id: String = "",
) {
    fun initHookSettings(name: String, description: String, state: Boolean) {
        if (!hooks.containsKey(name)) {
            hooks[name] = HookTaskSettings(description, state)
        }
    }

    fun initTaskSettings(taskId: String, description: String, state: Boolean) {
        if (!tasks.containsKey(taskId)) {
            tasks[taskId] = HookTaskSettings(description, state)
        }
    }

    fun isHookEnabled(name: String): Boolean {
        return hooks[name]?.enabled ?: false
    }

    fun setHookEnabled(name: String, enabled: Boolean) {
        hooks[name]?.enabled = enabled
    }

    fun isTaskEnabled(id: String): Boolean {
        return tasks[id]?.enabled ?: false
    }

    fun setTaskEnabled(id: String, enabled: Boolean) {
        tasks[id]?.enabled = enabled
    }

}

class Config(
    val getConfig: () -> String,
    var currentPackageName: String = Constants.GRINDR_PACKAGE_NAME,
    val onChange: (value: String) -> Unit = {}
) {
    companion object {
        val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    }
//    private var localConfig = JSONObject()
    var settings: GlobalSettings = GlobalSettings()
        private set


    init {
        import(getConfig())
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

        if (!settings.clones.containsKey(packageName)){
            settings.clones[packageName] = CloneSettings()
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
                settings.clones[packageName] = CloneSettings()
            }
        }
    }
}