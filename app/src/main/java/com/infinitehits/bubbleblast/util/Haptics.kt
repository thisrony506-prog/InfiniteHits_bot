package com.infinitehits.bubbleblast.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.infinitehits.bubbleblast.data.SettingsManager

/**
 * Short haptic ticks that make pops feel physical. Respects the vibration
 * setting and quietly does nothing on devices without a vibrator.
 */
class Haptics(context: Context, private val settings: SettingsManager) {

    private val vibrator: Vibrator? = resolveVibrator(context)

    private fun resolveVibrator(context: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (error: Exception) {
        null
    }

    /** True when the device can actually buzz. */
    val isAvailable: Boolean
        get() = vibrator?.hasVibrator() == true

    /**
     * @param strength 0 = none, 1 = light tap, 2 = medium, 3 = heavy thud
     */
    fun vibrate(strength: Int) {
        if (strength <= 0 || !settings.vibrationEnabled) return
        val device = vibrator ?: return
        if (!device.hasVibrator()) return

        val duration = when (strength) {
            1 -> 12L
            2 -> 22L
            else -> 38L
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                device.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                device.vibrate(duration)
            }
        } catch (ignored: Exception) {
            // Some OEM builds reject vibration effects - haptics are optional.
        }
    }
}
