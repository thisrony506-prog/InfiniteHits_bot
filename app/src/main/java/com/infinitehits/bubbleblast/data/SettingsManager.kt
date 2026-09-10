package com.infinitehits.bubbleblast.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Player preferences: audio, haptics and two gameplay comfort options.
 * Lives in the same preferences file as the save game so a backup restores it.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)

    /** Invoked whenever any setting changes, so open screens can refresh themselves. */
    var onChange: (() -> Unit)? = null

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) = put(KEY_SOUND, value)

    var musicEnabled: Boolean
        get() = prefs.getBoolean(KEY_MUSIC, true)
        set(value) = put(KEY_MUSIC, value)

    var vibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION, true)
        set(value) = put(KEY_VIBRATION, value)

    /** Draws the dotted aim guide. Turn off for a harder, cleaner board. */
    var showTrajectory: Boolean
        get() = prefs.getBoolean(KEY_TRAJECTORY, true)
        set(value) = put(KEY_TRAJECTORY, value)

    /** Mirrors the power-up row for left handed players. */
    var leftHanded: Boolean
        get() = prefs.getBoolean(KEY_LEFT_HANDED, false)
        set(value) = put(KEY_LEFT_HANDED, value)

    private fun put(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        onChange?.invoke()
    }

    companion object {
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_MUSIC = "music_enabled"
        private const val KEY_VIBRATION = "vibration_enabled"
        private const val KEY_TRAJECTORY = "show_trajectory"
        private const val KEY_LEFT_HANDED = "left_handed"
    }
}
