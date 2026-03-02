package com.grindrplus.manager.settings

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grindrplus.core.Constants
import com.grindrplus.core.model.CloneSettings
import com.grindrplus.core.model.GlobalSettings
import com.grindrplus.manager.GPApp
import com.grindrplus.manager.settings.SettingsUtils.testMapsApiKey
import com.grindrplus.manager.utils.AppIconManager
import com.grindrplus.utils.HookManager
import com.grindrplus.utils.TaskManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
class SettingsViewModel(
    private val context: Context,
) : ViewModel() {
    private val hookHideList = setOf(
        "Status Dialog",
    )
    private val _selectedPackage = MutableStateFlow<String>(Constants.GRINDR_PACKAGE_NAME)
    val selectedPackage: StateFlow<String> = _selectedPackage

    private val _cloneSettings = MutableStateFlow<CloneSettings>(GPApp.config.settings.getClone(selectedPackage.value))
    val cloneSettings: StateFlow<CloneSettings> = _cloneSettings

    private val _globalSettings = MutableStateFlow<GlobalSettings>(GPApp.config.settings)
    val globalSettings: StateFlow<GlobalSettings> = _globalSettings

    private val _settingGroups = MutableStateFlow<List<SettingGroup>>(emptyList())
    val settingGroups: StateFlow<List<SettingGroup>> = _settingGroups

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _showApiKeyTestDialog = MutableStateFlow(false)
    val showApiKeyTestDialog: StateFlow<Boolean> = _showApiKeyTestDialog

    private val _apiKeyTestTitle = MutableStateFlow("")
    val apiKeyTestTitle: StateFlow<String> = _apiKeyTestTitle

    private val _apiKeyTestMessage = MutableStateFlow("")
    val apiKeyTestMessage: StateFlow<String> = _apiKeyTestMessage

    private val _apiKeyTestRawResponse = MutableStateFlow("")
    val apiKeyTestRawResponse: StateFlow<String> = _apiKeyTestRawResponse

    private val _apiKeyTestLoading = MutableStateFlow(false)
    val apiKeyTestLoading: StateFlow<Boolean> = _apiKeyTestLoading


    fun dismissApiKeyTestDialog() {
        _showApiKeyTestDialog.value = false
    }

    private fun showApiKeyTestDialog(
        isLoading: Boolean,
        title: String,
        message: String,
        rawResponse: String
    ) {
        _apiKeyTestLoading.value = isLoading
        _apiKeyTestTitle.value = title
        _apiKeyTestMessage.value = message
        _apiKeyTestRawResponse.value = rawResponse
        _showApiKeyTestDialog.value = true
    }

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            _isLoading.value = true

            HookManager(GPApp.config, Constants.GRINDR_PACKAGE_NAME).registerHooks(false)
            TaskManager(GPApp.config, Constants.GRINDR_PACKAGE_NAME).registerTasks(false)

            _globalSettings.value = GPApp.config.settings
            _cloneSettings.value = GPApp.config.settings.getClone(selectedPackage.value)

            try {
                val hooks = cloneSettings.value.hooks
                val hookSettings = hooks
                    .filterNot { (hookName, _) -> hookName in hookHideList }
                    .map { (hookName, hook) ->
                        SwitchSetting(
                            id = hookName,
                            title = hookName,
                            description = hook.description,
                            isChecked = hook.enabled,
                            onCheckedChange = { value ->
                                viewModelScope.launch {
                                    GPApp.config.updateClone(selectedPackage.value) { it.withHookEnabled(hookName, value) }
                                    loadSettings()
                                }
                            }
                        )
                    }

                val tasks = cloneSettings.value.tasks
                val taskSettings = tasks.map { (taskId, task) ->
                    SwitchSetting(
                        id = taskId,
                        title = taskId,
                        description = task.description,
                        isChecked = task.enabled,
                        onCheckedChange = { value ->
                            viewModelScope.launch {
                                GPApp.config.updateClone(selectedPackage.value) { it.withTaskEnabled(taskId, value) }
                                loadSettings()
                            }
                        }
                    )
                }

                val otherSettings = mutableListOf(
                    TextSetting(
                        id = "command_prefix",
                        title = "Command Prefix",
                        description = "Change the command prefix (default: /)",
                        value = cloneSettings.value.command_prefix,
                        onValueChange = { value ->
                            viewModelScope.launch {
                                GPApp.config.updateClone(selectedPackage.value) { it.copy(command_prefix = value) }
                                loadSettings()
                            }
                        },
                        validator = { input ->
                            when {
                                input.isBlank() -> "Invalid command prefix"
                                input.length > 1 -> "Command prefix must be a single character"
                                !input.matches(Regex("[^a-zA-Z0-9]")) -> "Command prefix must be a special character"
                                else -> null
                            }
                        }
                    ),
                    TextSetting(
                        id = "date_format",
                        title = "Date Format",
                        description = "Format for displaying dates in the app (default: MM/dd/yyyy)",
                        value = cloneSettings.value.date_format,
                        onValueChange = { value ->
                            viewModelScope.launch {
                                GPApp.config.updateClone(selectedPackage.value) { it.copy(date_format = value) }
                                loadSettings()
                            }
                        },
                        validator = { input ->
                            when {
                                input.isBlank() -> "Date format cannot be empty"
                                !input.contains("MM") && !input.contains("M") -> "Format must include month (M or MM)"
                                !input.contains("dd") && !input.contains("d") -> "Format must include day (d or dd)"
                                !input.contains("yyyy") && !input.contains("yy") -> "Format must include year (yy or yyyy)"
                                else -> null
                            }
                        }
                    ),
                    TextSetting(
                        id = "online_indicator",
                        title = "Online indicator duration (mins)",
                        description = "Control when the green dot disappears after inactivity",
                        value = cloneSettings.value.online_indicator.toString(),
                        onValueChange = {
                            val value = it.toIntOrNull() ?: 5
                            viewModelScope.launch {
                                GPApp.config.updateClone(selectedPackage.value) { it.copy(online_indicator = value) }
                                loadSettings()
                            }
                        },
                        keyboardType = KeyboardType.Number,
                        validator = { input ->
                            val value = input.toIntOrNull()
                            if (value == null || value <= 0) "Duration must be a positive number" else null
                        }
                    ),
                    SwitchSetting(
                        id = "show_bmi_in_profile",
                        title = "Show BMI in Profile",
                        description = "Display BMI in the profile section",
                        isChecked = cloneSettings.value.show_bmi_in_profile,
                        onCheckedChange = { value ->
                            viewModelScope.launch {
                                GPApp.config.updateClone(selectedPackage.value) { it.copy(show_bmi_in_profile = value) }
                                loadSettings()
                            }
                        }
                    ),
                    TextSetting(
                        id = "favorites_grid_columns",
                        title = "Favorites grid columns",
                        description = "Number of columns in the favorites grid (default: 3)",
                        value = cloneSettings.value.favorites_grid_columns.toString(),
                        onValueChange = {
                            val value = it.toIntOrNull() ?: 3
                            viewModelScope.launch {
                                GPApp.config.updateClone(selectedPackage.value) { it.copy(favorites_grid_columns = value) }
                                loadSettings()
                            }
                        },
                        keyboardType = KeyboardType.Number,
                        validator = { input ->
                            val value = input.toIntOrNull()
                            if (value == null || value <= 0) "Number of columns must be a positive number" else null
                        }
                    ),
                    TextSettingWithButtons(
                        id = "android_device_id",
                        title = "Android Device ID",
                        description = "Change the Android Device ID",
                        value = cloneSettings.value.android_device_id,
                        onValueChange = { value ->
                            viewModelScope.launch {
                                GPApp.config.updateClone(selectedPackage.value) { it.copy(android_device_id = value) }
                                loadSettings()
                            }
                        },
                        validator = { input ->
                            when {
                                input.isBlank() -> null
                                input.length != 16 -> "Android Device ID must be 16 characters long"
                                !input.matches(Regex("[0-9a-fA-F]+")) -> "Android Device ID must be a hexadecimal string"
                                else -> null
                            }
                        },
                        buttons = listOf(
                            ButtonAction("Generate") {
                                val uuid = java.util.UUID.randomUUID()
                                val newDeviceId = uuid.toString().replace("-", "").substring(0, 16)
                                GPApp.config.updateClone(selectedPackage.value) { it.copy(android_device_id = newDeviceId) }
                                loadSettings()
                                Toast.makeText(context, "New device ID generated", Toast.LENGTH_SHORT).show()
                            }
                        )

                    ),
                    SwitchSetting(
                        id = "enable_cookie_tap",
                        title = "Enable Cookie Tap",
                        description = "Enable the ability to send cookie taps to other users (they'll see them)",
                        isChecked = cloneSettings.value.enable_cookie_tap,
                        onCheckedChange = { value ->
                            viewModelScope.launch {
                                GPApp.config.updateClone(selectedPackage.value) { it.copy(enable_cookie_tap = value) }
                                loadSettings()
                            }
                        }
                    ),
                    SwitchSetting(
                        id = "enable_vip_flag",
                        title = "Enable Star Section",
                        description = "Enables what looks like a recommendation section next to Browse",
                        isChecked = cloneSettings.value.enable_vip_flag,
                        onCheckedChange = { value ->
                            viewModelScope.launch {
                                GPApp.config.updateClone(selectedPackage.value) { it.copy(enable_vip_flag = value) }
                                loadSettings()
                            }
                        }
                    ),
                    SwitchSetting(
                        id = "enable_interest_section",
                        title = "Enable Interest Section",
                        description = "Show interests section on profiles",
                        isChecked = cloneSettings.value.enable_interest_section,
                        onCheckedChange = { value ->
                            viewModelScope.launch {
                                GPApp.config.updateClone(selectedPackage.value) { it.copy(enable_interest_section = value) }
                                loadSettings()
                            }
                        }
                    ),
                    SwitchSetting(
                        id = "disable_profile_swipe",
                        title = "Disable profile swipe",
                        description = "Disable profile swipe and open profile on click",
                        isChecked = cloneSettings.value.disable_profile_swipe,
                        onCheckedChange = { value ->
                            viewModelScope.launch {
                                GPApp.config.updateClone(selectedPackage.value) { it.copy(disable_profile_swipe = value) }
                                loadSettings()
                            }
                        }
                    ),
                    SwitchSetting(
                        id = "force_old_anti_block_behavior",
                        title = "Force old AntiBlock behavior",
                        description = "Use the old AntiBlock behavior (don't use this, required for testing)",
                        isChecked = cloneSettings.value.force_old_anti_block_behavior,
                        onCheckedChange = { value ->
                            viewModelScope.launch {
                                GPApp.config.updateClone(selectedPackage.value) { it.copy(force_old_anti_block_behavior = value) }
                                loadSettings()
                            }
                        }
                    ),
                    SwitchSetting(
                        id = "anti_block_use_toasts",
                        title = "Use toasts for AntiBlock hook",
                        description = "Instead of receiving Android notifications, use toasts for block/unblock notifications",
                        isChecked = cloneSettings.value.anti_block_use_toasts,
                        onCheckedChange = { value ->
                            viewModelScope.launch {
                                GPApp.config.updateClone(selectedPackage.value) { it.copy(anti_block_use_toasts = value) }
                                loadSettings()
                            }
                        }
                    ),
                    SwitchSetting(
                        id = "reset_database",
                        title = "Reset local database on next start",
                        description = "Will delete all local data on next app start",
                        isChecked = cloneSettings.value.reset_database,
                        onCheckedChange = { value ->
                            viewModelScope.launch {
                                GPApp.config.updateClone(selectedPackage.value) { it.copy(reset_database = value) }
                                loadSettings()
                            }
                        }
                    ),
                    SwitchSetting(
                        id = "do_gui_safety_checks",
                        title = "Do GUI safety checks",
                        description = "Prevent graphic glitches when applying GUI based hooks",
                        isChecked = cloneSettings.value.do_gui_safety_checks,
                        onCheckedChange = { value ->
                            viewModelScope.launch {
                                GPApp.config.updateClone(selectedPackage.value) { it.copy(do_gui_safety_checks = value) }
                                loadSettings()
                            }
                        }
                    )
                )

                val managerSettings = mutableListOf<Setting>(
                    TextSettingWithButtons(
                        id = "maps_api_key",
                        title = "Maps API Key",
                        description = "Use a custom Maps API Key when using Grindr Plus with LSPatch",
                        value = globalSettings.value.maps_api_key,
                        onValueChange = { value ->
                            viewModelScope.launch {
                                GPApp.config.update { it.copy(maps_api_key = value) }
                                loadSettings()
                            }
                        },
                        validator = { null },
                        buttons = listOf(
                            ButtonAction("Test") {
                                val apiKey = globalSettings.value.maps_api_key
                                if (apiKey.isBlank()) {
                                    Toast.makeText(context, "Please enter an API key first", Toast.LENGTH_SHORT).show()
                                } else {
                                    testMapsApiKey(
                                        context,
                                        viewModelScope,
                                        apiKey,
                                        ::showApiKeyTestDialog
                                    )
                                }
                            }
                        )
                    ),
                    TextSetting(
                        id = "custom_manifest",
                        title = "Custom Manifest URL",
                        description = "Use a custom manifest URL when using Grindr Plus with LSPatch",
                        value = globalSettings.value.custom_manifest,
                        onValueChange = { value ->
                            viewModelScope.launch {
                                GPApp.config.update { it.copy(custom_manifest = value) }
                                loadSettings()
                            }
                        },
                        validator = { null }
                    ),
                    SwitchSetting(
                        id = "analytics",
                        title = "Opt-in analytics",
                        description = "Help improve the app by sending anonymous usage data",
                        isChecked = globalSettings.value.analytics,
                        onCheckedChange = { value ->
                            viewModelScope.launch {
                                GPApp.config.update { it.copy(analytics = value) }
                                loadSettings()
                            }
                        }
                    ),
                    SwitchSetting(
                        id = "discreet_icon",
                        title = "Camouflage app",
                        description = "Hide the app icon and use a different name",
                        isChecked = globalSettings.value.discreet_icon,
                        onCheckedChange = { value ->
                            viewModelScope.launch {
                                GPApp.config.update { it.copy(discreet_icon = value) }
                                loadSettings()

                                val appIconManager = AppIconManager(context)
                                appIconManager.changeAppIcon(if (value) AppIconManager.DISCREET_ICON else AppIconManager.DEFAULT_ICON)

                                Toast.makeText(
                                    context,
                                    "App icon changed. It may take a moment to update.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    ),
                    SwitchSetting(
                        id = "disable_permission_checks",
                        title = "Disable permission checks",
                        description = "Disable permission checks on startup (not recommended)",
                        isChecked = globalSettings.value.disable_permission_checks,
                        onCheckedChange = { value ->
                            viewModelScope.launch {
                                GPApp.config.update { it.copy(disable_permission_checks = value) }
                                loadSettings()
                            }
                        }
                    )
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    managerSettings += SwitchSetting(
                        id = "material_you",
                        title = "Enable dynamic colors",
                        description = "Use Material You colors for the app\nRestart the app to apply changes",
                        isChecked = globalSettings.value.material_you,
                        onCheckedChange = { value ->
                            viewModelScope.launch {
                                GPApp.config.update { it.copy(material_you = value) }
                                loadSettings()
                            }
                        }
                    )
                }

                _settingGroups.value = listOf(
                    SettingGroup(
                        id = "hooks",
                        title = "Manage Hooks",
                        settings = hookSettings
                    ),
                    SettingGroup(
                        id = "tasks",
                        title = "Manage Tasks",
                        settings = taskSettings
                    ),
                    SettingGroup(
                        id = "other",
                        title = "Other Settings",
                        settings = otherSettings
                    ),
                    SettingGroup(
                        id = "manager",
                        title = "Manager Settings",
                        settings = managerSettings
                    ),
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun changeSelectedPackage(packageName: String) {
        _selectedPackage.value = packageName
        loadSettings()
    }
}

class SettingsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
fun rememberViewModel(): SettingsViewModel {
    val context = LocalContext.current
    val factory = remember(context) { SettingsViewModelFactory(context) }
    return viewModel(factory = factory)
}