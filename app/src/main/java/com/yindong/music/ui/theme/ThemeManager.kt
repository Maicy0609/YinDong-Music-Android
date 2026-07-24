package com.yindong.music.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "app_theme_prefs"
private const val PREF_KEY_DARK = "is_dark_mode"
private const val PREF_KEY_THEME_MODE = "theme_mode"
private const val PREF_KEY_SCHEME = "color_scheme_id"
private const val PREF_KEY_DYNAMIC_COLOR = "dynamic_color_enabled"
private const val DEFAULT_COLOR_SCHEME_ID = "pure_white"
private const val KEY_MIGRATED = "theme_migrated_from_legacy"
private const val KEY_SCHEME_MIGRATED_V2 = "scheme_migrated_to_pure_white_v2"

/** 主题模式：LIGHT(浅色) / DARK(深色) / SYSTEM(跟随系统) */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

object ThemeManager {

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** 用户在设置中选择的静态主题（不受动态色影响） */
    private val _staticColorScheme = MutableStateFlow(getColorSchemeById(DEFAULT_COLOR_SCHEME_ID))
    /** 封面提取的动态主题；为空表示尚未提取或已关闭 */
    private val _dynamicColorScheme = MutableStateFlow<ColorSchemeConfig?>(null)
    /** 动态色开关（持久化） */
    private val _dynamicColorEnabled = MutableStateFlow(false)

    /** 对外暴露的当前主题：开启动态色且有提取结果时使用动态主题，否则使用静态主题 */
    private val _colorSchemeState = MutableStateFlow(getColorSchemeById(DEFAULT_COLOR_SCHEME_ID))
    val colorSchemeState: StateFlow<ColorSchemeConfig> = _colorSchemeState.asStateFlow()

    /** 动态色开关状态（可观察） */
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    val isDark: Boolean get() = _isDarkMode.value
    val currentColorScheme: ColorSchemeConfig get() = _colorSchemeState.value
    val isDynamicColorActive: Boolean
        get() = _dynamicColorEnabled.value && _dynamicColorScheme.value != null

    fun initTheme(context: Context) {
        migrateFromLegacy(context)
        migrateToPureWhite(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // 读取主题模式
        val modeStr = prefs.getString(PREF_KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"
        _themeMode.value = when (modeStr) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
        // 根据 mode 计算 isDark
        val isDark = when (_themeMode.value) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> {
                val nightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
        _isDarkMode.value = isDark
        val schemeId = prefs.getString(PREF_KEY_SCHEME, DEFAULT_COLOR_SCHEME_ID) ?: DEFAULT_COLOR_SCHEME_ID
        _staticColorScheme.value = getColorSchemeById(schemeId)

        // 读取动态色开关
        _dynamicColorEnabled.value = prefs.getBoolean(PREF_KEY_DYNAMIC_COLOR, false)
        // 启动时若关闭动态色，确保不残留旧动态主题
        if (!_dynamicColorEnabled.value) {
            _dynamicColorScheme.value = null
            DynamicThemeBuilder.clearCache()
        }
        refreshExposedScheme()
    }

    /** 根据当前开关与动态主题状态，刷新对外暴露的主题 */
    private fun refreshExposedScheme() {
        val dynamic = _dynamicColorScheme.value
        _colorSchemeState.value = if (_dynamicColorEnabled.value && dynamic != null) dynamic
        else _staticColorScheme.value
    }

    /** 跟随系统模式下，根据系统当前深色状态更新 isDarkMode */
    fun applySystemDarkMode(context: Context) {
        if (_themeMode.value != ThemeMode.SYSTEM) return
        val nightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isDark = nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        _isDarkMode.value = isDark
    }

    /** 设置主题模式（浅色/深色/跟随系统） */
    fun setThemeMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY_THEME_MODE, mode.name)
            .apply()
        _themeMode.value = mode
        val isDark = when (mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> {
                val nightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
        _isDarkMode.value = isDark
    }

    /**
     * 一次性迁移：将所有用户切换到新的纯白极简主题，
     * 完成界面统一重写后的默认视觉风格。
     */
    private fun migrateToPureWhite(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SCHEME_MIGRATED_V2, false)) return
        prefs.edit()
            .putString(PREF_KEY_SCHEME, DEFAULT_COLOR_SCHEME_ID)
            .putBoolean(KEY_SCHEME_MIGRATED_V2, true)
            .apply()
    }

    fun setDarkMode(context: Context, isDark: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_KEY_DARK, isDark)
            .apply()
        _isDarkMode.value = isDark
    }

    fun toggleDarkMode(context: Context) {
        setDarkMode(context, !_isDarkMode.value)
    }

    fun setColorScheme(context: Context, schemeId: String) {
        val validId = if (AllColorSchemes.any { it.id == schemeId }) schemeId else DEFAULT_COLOR_SCHEME_ID
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY_SCHEME, validId)
            .apply()
        _staticColorScheme.value = getColorSchemeById(validId)
        // 切换静态主题时若动态色已开启，应自动关闭动态色，避免视觉冲突
        if (_dynamicColorEnabled.value) {
            setDynamicColorEnabled(context, false)
        } else {
            refreshExposedScheme()
        }
    }

    /**
     * 开关动态主题色。
     * - 开启：等待下一次封面更新时由 [updateDynamicTheme] 注入；立即清空旧缓存避免错位
     * - 关闭：恢复静态主题，清空动态缓存
     */
    fun setDynamicColorEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_KEY_DYNAMIC_COLOR, enabled)
            .apply()
        _dynamicColorEnabled.value = enabled
        if (!enabled) {
            _dynamicColorScheme.value = null
            DynamicThemeBuilder.clearCache()
        }
        refreshExposedScheme()
    }

    /**
     * 根据封面 URL 更新动态主题。
     * - 仅当动态色开关开启时执行提取
     * - 提取失败时保留上一次结果，避免界面闪烁
     * - 在 IO 线程执行，调用方无需切换调度器
     */
    suspend fun updateDynamicTheme(context: Context, coverUrl: String) {
        if (!_dynamicColorEnabled.value) return
        if (coverUrl.isBlank()) return
        val scheme = DynamicThemeBuilder.extract(context, coverUrl) ?: return
        _dynamicColorScheme.value = scheme
        refreshExposedScheme()
    }

    /** 读取已保存的动态色开关（用于设置界面初始状态） */
    fun getSavedDynamicColorEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_KEY_DYNAMIC_COLOR, false)
    }

    fun getSavedDarkMode(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_KEY_DARK, false)
    }

    fun getSavedColorSchemeId(context: Context): String {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_KEY_SCHEME, DEFAULT_COLOR_SCHEME_ID) ?: DEFAULT_COLOR_SCHEME_ID
        return if (AllColorSchemes.any { it.id == saved }) saved else DEFAULT_COLOR_SCHEME_ID
    }

    fun resetToDefault(context: Context) {
        setDarkMode(context, false)
        setDynamicColorEnabled(context, false)
        setColorScheme(context, DEFAULT_COLOR_SCHEME_ID)
    }

    private fun migrateFromLegacy(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED, false)) return

        val legacyPrefs = context.getSharedPreferences("cloud_music_data", Context.MODE_PRIVATE)
        val isDark = if (legacyPrefs.contains("dark_mode")) {
            legacyPrefs.getBoolean("dark_mode", false)
        } else {
            val oldThemeId = prefs.getString("current_theme_id", "light") ?: "light"
            oldThemeId == "dark"
        }

        val oldSchemeId = prefs.getString("color_scheme_id", DEFAULT_COLOR_SCHEME_ID) ?: DEFAULT_COLOR_SCHEME_ID

        prefs.edit()
            .putBoolean(PREF_KEY_DARK, isDark)
            .putString(PREF_KEY_SCHEME, oldSchemeId)
            .putBoolean(KEY_MIGRATED, true)
            .apply()
    }
}
