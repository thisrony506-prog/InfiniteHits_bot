package com.infinitehits.bubbleblast.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import com.infinitehits.bubbleblast.R
import com.infinitehits.bubbleblast.audio.AudioManager
import com.infinitehits.bubbleblast.util.UiUtils

/**
 * Launch screen: logo, loading bar and a short pre-roll while the audio engine
 * warms up. The player never sees the home screen pop in half-built.
 */
class SplashActivity : BaseActivity() {

    override val musicScene: AudioManager.Scene get() = AudioManager.Scene.HOME

    private val handler = Handler(Looper.getMainLooper())
    private var finishRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        UiUtils.applySystemBarInsets(findViewById(R.id.splash_root))

        findViewById<TextView>(R.id.splash_version)
            .text = getString(R.string.settings_version, services.versionName())

        val progress = findViewById<ProgressBar>(R.id.splash_progress)
        progress.max = 100
        ObjectAnimator.ofInt(progress, "progress", 0, 100).apply {
            duration = SPLASH_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        val status = findViewById<TextView>(R.id.splash_status)
        handler.postDelayed(
            { status.text = getString(R.string.splash_preparing) },
            SPLASH_DURATION_MS / 2
        )

        // Everything the first screen needs is already loaded: the audio manager
        // is created with AppServices and the first level is generated lazily.
        val runnable = Runnable {
            if (isFinishing || isDestroyed) return@Runnable
            startActivity(Intent(this, HomeActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.screen_fade_in, R.anim.screen_fade_out)
            finish()
        }
        finishRunnable = runnable
        handler.postDelayed(runnable, SPLASH_DURATION_MS + 150L)
    }

    override fun onDestroy() {
        finishRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** The player can skip the splash by tapping it. */
    override fun onBackPressed() {
        // Swallow back on the splash: the first screen is about to appear.
    }

    companion object {
        private const val SPLASH_DURATION_MS = 1600L
    }
}
