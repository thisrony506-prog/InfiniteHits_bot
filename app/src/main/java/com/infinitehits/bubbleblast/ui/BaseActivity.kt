package com.infinitehits.bubbleblast.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.infinitehits.bubbleblast.AppServices
import com.infinitehits.bubbleblast.BubbleBlastApp
import com.infinitehits.bubbleblast.audio.AudioManager

/**
 * Shared plumbing for every screen: service access, background music scene and
 * the standard "button click" feedback.
 */
abstract class BaseActivity : AppCompatActivity() {

    protected val services: AppServices
        get() = (application as BubbleBlastApp).services

    /** Music bed this screen should play. */
    protected open val musicScene: AudioManager.Scene
        get() = AudioManager.Scene.NONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        services.settings.onChange = { services.audio.onSettingsChanged() }
    }

    override fun onStart() {
        super.onStart()
        services.setMusicScene(musicScene)
    }

    /** Click sound plus a light tick, used by every button in the game. */
    protected fun playClick() {
        services.audio.play(AudioManager.Sfx.CLICK)
        services.haptics.vibrate(1)
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(
            com.infinitehits.bubbleblast.R.anim.screen_fade_in,
            com.infinitehits.bubbleblast.R.anim.screen_fade_out
        )
    }
}
