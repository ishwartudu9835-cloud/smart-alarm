package com.ishwar.smartalarm.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** App settings + profile. Compose state ke saath, taaki badalte hi UI update ho. */
object Prefs {
    private lateinit var sp: SharedPreferences
    private var ready = false

    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    var name by mutableStateOf("")
        private set
    var photoVersion by mutableStateOf(0)
        private set
    var hindi by mutableStateOf(true)
        private set
    var theme by mutableStateOf(THEME_SYSTEM)
        private set
    var volume by mutableFloatStateOf(1f)
        private set
    var vibration by mutableStateOf(true)
        private set
    var autoRepeat by mutableStateOf(true)
        private set
    var defaultSound by mutableStateOf<String?>(null)
        private set

    fun init(ctx: Context) {
        if (ready) return
        sp = ctx.applicationContext.getSharedPreferences("smartalarm_prefs", Context.MODE_PRIVATE)
        name = sp.getString("name", "") ?: ""
        hindi = sp.getBoolean("hindi", true)
        theme = sp.getInt("theme", THEME_SYSTEM)
        volume = sp.getFloat("volume", 1f)
        vibration = sp.getBoolean("vibration", true)
        autoRepeat = sp.getBoolean("autoRepeat", true)
        defaultSound = sp.getString("defaultSound", null)
        ready = true
    }

    fun setName(v: String) { name = v; sp.edit().putString("name", v).apply() }
    fun setHindi(v: Boolean) { hindi = v; sp.edit().putBoolean("hindi", v).apply() }
    fun setTheme(v: Int) { theme = v; sp.edit().putInt("theme", v).apply() }
    fun setVolume(v: Float) { volume = v; sp.edit().putFloat("volume", v).apply() }
    fun setVibration(v: Boolean) { vibration = v; sp.edit().putBoolean("vibration", v).apply() }
    fun setAutoRepeat(v: Boolean) { autoRepeat = v; sp.edit().putBoolean("autoRepeat", v).apply() }
    fun setDefaultSound(v: String?) { defaultSound = v; sp.edit().putString("defaultSound", v).apply() }
    fun photoChanged() { photoVersion++ }

    var lastGeneratedDay: Long
        get() = sp.getLong("lastGeneratedDay", -1L)
        set(v) { sp.edit().putLong("lastGeneratedDay", v).apply() }

    var askedPermissions: Boolean
        get() = sp.getBoolean("askedPermissions", false)
        set(v) { sp.edit().putBoolean("askedPermissions", v).apply() }

    fun photoFile(ctx: Context) = File(ctx.filesDir, "profile.jpg")
    fun voicesDir(ctx: Context) = File(ctx.filesDir, "voices").apply { mkdirs() }
    fun soundsDir(ctx: Context) = File(ctx.filesDir, "sounds").apply { mkdirs() }

    /** Backup ke liye */
    fun export(): Map<String, Any?> = mapOf(
        "name" to name, "hindi" to hindi, "theme" to theme, "volume" to volume.toDouble(),
        "vibration" to vibration, "autoRepeat" to autoRepeat, "defaultSound" to defaultSound
    )
}

/** Hindi / English text */
fun tr(en: String, hi: String): String = if (Prefs.hindi) hi else en
