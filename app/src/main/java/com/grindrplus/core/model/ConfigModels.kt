package com.grindrplus.core.model

import kotlinx.serialization.Serializable

@Serializable
data class GlobalSettings(
    val first_launch: Boolean = true,
    val analytics: Boolean = true,
    val discreet_icon: Boolean = false,
    val material_you: Boolean = false,
    val debug_mode: Boolean = false,
    val disable_permission_checks: Boolean = false,
    val custom_manifest: String = "",
    val maps_api_key: String = "",
    val last_push_id: String = "-1",

    val favorites_import_threshold: Int = 500,
    val calculator_first_launch: Boolean = true,

    val clones: Map<String, CloneSettings> = mapOf()
) {

    fun getClonePackageNames(): List<String> {
        return clones.keys.toList()
    }

    fun getClone(packageName: String): CloneSettings {
        return clones[packageName] ?: CloneSettings()
    }

    fun withClone(packageName: String, cloneSettings: CloneSettings): GlobalSettings {
        return this.copy(
            clones = clones + (packageName to cloneSettings)
        )
    }
}

@Serializable
data class CloneSettings(
    val hooks: Map<String, HookTaskSettings> = mapOf(),
    val tasks: Map<String, HookTaskSettings> = mapOf(),

    val show_bmi_in_profile: Boolean = true,

    val enable_cookie_tap: Boolean = false,
    val enable_vip_flag: Boolean = false,
    val enable_interest_section: Boolean = true,
    val disable_profile_swipe: Boolean = false,

    val force_old_anti_block_behavior: Boolean = false,
    val anti_block_use_toasts: Boolean = false,

    val current_location: String? = null,
    val current_location_name: String? = null,


    val command_prefix: String = "/",
    val date_format: String = "MM/dd/yyyy",
    val online_indicator: Int = 3,
    val favorites_grid_columns: Int = 3,
    val forced_coordinates: String = "",

    val reset_database: Boolean = false,
    val do_gui_safety_checks: Boolean = true,
    val android_device_id: String = "",
) {

    fun isHookEnabled(name: String): Boolean {
        return hooks[name]?.enabled ?: false
    }

    fun isTaskEnabled(id: String): Boolean {
        return tasks[id]?.enabled ?: false
    }

    // copy-constructors
    fun withHookInit(name: String, description: String, state: Boolean): CloneSettings {
        if (name in hooks)
            return this

        return this.copy(
            hooks = this.hooks + (name to HookTaskSettings(description, state))
        )
    }

    fun withTaskInit(taskId: String, description: String, state: Boolean): CloneSettings {
        if (taskId in tasks)
            return this

        return this.copy(
            tasks = this.tasks + (taskId to HookTaskSettings(description, state))
        )
    }

    fun withHookEnabled(name: String, enabled: Boolean): CloneSettings {
        val hook = hooks[name] ?: return this

        return this.copy(
            hooks = this.hooks + (name to hook.copy(enabled = enabled))
        )
    }

    fun withTaskEnabled(id: String, enabled: Boolean): CloneSettings {
        val task = tasks[id] ?: return this

        return this.copy(
            tasks = this.tasks + (id to task.copy(enabled = enabled))
        )
    }

}

@Serializable
data class HookTaskSettings(
    val description: String = "",
    val enabled: Boolean = false
)