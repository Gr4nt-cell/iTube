package com.gr4nt.itube.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** What happens to a playing video when the user leaves the app. */
enum class LeaveAction(val label: String) {
    PIP("Picture-in-picture"),
    BACKGROUND("Keep playing audio"),
    POPUP("Floating pop-up"),
    PAUSE("Pause"),
}

object Settings {
    private lateinit var prefs: SharedPreferences

    private fun <T> state(initial: T) = MutableStateFlow(initial)

    private val _theme = state(ThemeMode.SYSTEM)
    private val _sbEnabled = state(true)
    private val _sbCategories = state(setOf("sponsor", "selfpromo", "interaction", "intro", "outro", "preview", "music_offtopic"))
    private val _quality = state(1080)
    private val _downloadQuality = state(1080)
    private val _autoplay = state(true)
    private val _leaveAction = state(LeaveAction.PIP)
    private val _restricted = state(false)
    private val _speed = state(1f)
    private val _showDislikes = state(true)

    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()
    val sbEnabled: StateFlow<Boolean> = _sbEnabled.asStateFlow()
    val sbCategories: StateFlow<Set<String>> = _sbCategories.asStateFlow()
    val quality: StateFlow<Int> = _quality.asStateFlow()
    val downloadQuality: StateFlow<Int> = _downloadQuality.asStateFlow()
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()
    val leaveAction: StateFlow<LeaveAction> = _leaveAction.asStateFlow()
    val restrictedMode: StateFlow<Boolean> = _restricted.asStateFlow()
    val defaultSpeed: StateFlow<Float> = _speed.asStateFlow()
    val showDislikes: StateFlow<Boolean> = _showDislikes.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _theme.value = runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)
        _sbEnabled.value = prefs.getBoolean("sb", true)
        _sbCategories.value = prefs.getStringSet("sb_cats", null) ?: _sbCategories.value
        _quality.value = prefs.getInt("quality", 1080)
        _downloadQuality.value = prefs.getInt("dl_quality", 1080)
        _autoplay.value = prefs.getBoolean("autoplay", true)
        _leaveAction.value = runCatching { LeaveAction.valueOf(prefs.getString("leave", "PIP")!!) }.getOrDefault(LeaveAction.PIP)
        _restricted.value = prefs.getBoolean("restricted", false)
        _speed.value = prefs.getFloat("speed", 1f)
        _showDislikes.value = prefs.getBoolean("dislikes", true)
    }

    fun setTheme(v: ThemeMode) { _theme.value = v; prefs.edit().putString("theme", v.name).apply() }
    fun setSbEnabled(v: Boolean) { _sbEnabled.value = v; prefs.edit().putBoolean("sb", v).apply() }
    fun toggleSbCategory(key: String) {
        val now = _sbCategories.value.let { if (key in it) it - key else it + key }
        _sbCategories.value = now
        prefs.edit().putStringSet("sb_cats", now).apply()
    }
    fun setQuality(v: Int) { _quality.value = v; prefs.edit().putInt("quality", v).apply() }
    fun setDownloadQuality(v: Int) { _downloadQuality.value = v; prefs.edit().putInt("dl_quality", v).apply() }
    fun setAutoplay(v: Boolean) { _autoplay.value = v; prefs.edit().putBoolean("autoplay", v).apply() }
    fun setLeaveAction(v: LeaveAction) { _leaveAction.value = v; prefs.edit().putString("leave", v.name).apply() }
    fun setRestricted(v: Boolean) { _restricted.value = v; prefs.edit().putBoolean("restricted", v).apply() }
    fun setDefaultSpeed(v: Float) { _speed.value = v; prefs.edit().putFloat("speed", v).apply() }
    fun setShowDislikes(v: Boolean) { _showDislikes.value = v; prefs.edit().putBoolean("dislikes", v).apply() }
}
