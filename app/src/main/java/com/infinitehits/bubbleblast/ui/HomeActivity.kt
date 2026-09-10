package com.infinitehits.bubbleblast.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import com.infinitehits.bubbleblast.R
import com.infinitehits.bubbleblast.audio.AudioManager
import com.infinitehits.bubbleblast.core.level.LevelCatalog
import com.infinitehits.bubbleblast.ui.dialog.DailyRewardDialog
import com.infinitehits.bubbleblast.ui.dialog.ShopDialog
import com.infinitehits.bubbleblast.util.Navigator
import com.infinitehits.bubbleblast.util.UiUtils

/**
 * Main menu: play, level select, coins, daily reward and the settings shortcuts.
 */
class HomeActivity : BaseActivity() {

    override val musicScene: AudioManager.Scene get() = AudioManager.Scene.HOME

    private lateinit var coinsText: TextView
    private lateinit var starsText: TextView
    private lateinit var playButton: TextView
    private lateinit var dailyBadge: View
    private lateinit var dailyLabel: TextView
    private lateinit var dailySubtitle: TextView
    private lateinit var soundButton: ImageButton
    private lateinit var musicButton: ImageButton

    private val handler = Handler(Looper.getMainLooper())

    /** Double-back-to-exit guard. */
    private var backPressedOnce = false
    private val backReset = Runnable { backPressedOnce = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        UiUtils.applySystemBarInsets(findViewById(R.id.home_root))

        coinsText = findViewById(R.id.home_coins_text)
        starsText = findViewById(R.id.home_stars_text)
        playButton = findViewById(R.id.home_play_button)
        dailyBadge = findViewById(R.id.home_daily_badge)
        dailyLabel = findViewById(R.id.home_daily_label)
        dailySubtitle = findViewById(R.id.home_daily_subtitle)
        soundButton = findViewById(R.id.home_sound_button)
        musicButton = findViewById(R.id.home_music_button)

        findViewById<TextView>(R.id.home_version_text)
            .text = getString(R.string.settings_version, services.versionName())

        UiUtils.attachPressEffect(
            playButton,
            findViewById(R.id.home_levels_button),
            findViewById(R.id.home_daily_button),
            findViewById(R.id.home_coins_button),
            soundButton,
            musicButton,
            findViewById(R.id.home_settings_button)
        )

        playButton.setOnClickListener {
            playClick()
            Navigator.toGame(this, services.save.nextLevelToPlay())
        }

        findViewById<View>(R.id.home_levels_button).setOnClickListener {
            playClick()
            Navigator.toLevelSelect(this)
        }

        findViewById<View>(R.id.home_coins_button).setOnClickListener {
            playClick()
            showShop()
        }

        findViewById<View>(R.id.home_daily_button).setOnClickListener {
            playClick()
            showDailyReward()
        }

        findViewById<View>(R.id.home_settings_button).setOnClickListener {
            playClick()
            Navigator.toSettings(this)
        }

        soundButton.setOnClickListener {
            playClick()
            services.settings.soundEnabled = !services.settings.soundEnabled
            refreshToggles()
        }

        musicButton.setOnClickListener {
            playClick()
            services.settings.musicEnabled = !services.settings.musicEnabled
            refreshToggles()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backPressedOnce) {
                    finish()
                    return
                }
                backPressedOnce = true
                UiUtils.toast(this@HomeActivity, R.string.home_exit_hint)
                handler.postDelayed(backReset, BACK_EXIT_WINDOW_MS)
            }
        })

        services.ads.preloadInterstitial(com.infinitehits.bubbleblast.ads.AdManager.Placement.HOME_BANNER)
        services.ads.preloadRewarded(com.infinitehits.bubbleblast.ads.AdManager.Placement.DOUBLE_COINS_REWARDED)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun refresh() {
        val save = services.save
        coinsText.text = String.format("%,d", save.coins)
        starsText.text = getString(R.string.levels_stars_total, save.totalStars, LevelCatalog.totalStars)

        val nextLevel = save.nextLevelToPlay()
        playButton.text = if (nextLevel <= 1 && save.totalStars == 0) {
            getString(R.string.home_play)
        } else {
            getString(R.string.home_continue, nextLevel)
        }

        val canClaim = services.dailyRewards.canClaimToday()
        UiUtils.setVisible(dailyBadge, canClaim)
        dailyLabel.setText(if (canClaim) R.string.home_daily else R.string.home_daily)
        dailySubtitle.setText(if (canClaim) R.string.home_daily_ready else R.string.daily_already)

        refreshToggles()
    }

    private fun refreshToggles() {
        val settings = services.settings
        soundButton.setImageResource(
            if (settings.soundEnabled) R.drawable.ic_sound_on else R.drawable.ic_sound_off
        )
        musicButton.setImageResource(
            if (settings.musicEnabled) R.drawable.ic_music_on else R.drawable.ic_music_off
        )
        soundButton.contentDescription = getString(R.string.settings_sound)
        musicButton.contentDescription = getString(R.string.settings_music)
    }

    private fun showDailyReward() {
        val manager = services.dailyRewards
        DailyRewardDialog(
            activity = this,
            rewards = com.infinitehits.bubbleblast.data.DailyRewardManager.REWARDS,
            nextIndex = manager.nextDayIndex(),
            canClaim = manager.canClaimToday(),
            streak = manager.currentStreak(),
            onClaim = {
                val reward = manager.claim()
                if (reward != null) {
                    services.audio.play(AudioManager.Sfx.STAR)
                    services.haptics.vibrate(2)
                    UiUtils.toast(
                        this,
                        if (reward.powerUp != null && reward.powerUpCount > 0) {
                            getString(
                                R.string.daily_reward_powerup,
                                reward.powerUpCount,
                                getString(PowerUpUi.nameRes(reward.powerUp))
                            )
                        } else {
                            getString(R.string.daily_reward_coins, reward.coins)
                        }
                    )
                }
                refresh()
            },
            onClose = { refresh() }
        ).show()
    }

    private fun showShop() {
        ShopDialog(
            activity = this,
            powerUps = PowerUpUi.order,
            initialOwned = services.save.powerUpInventory(),
            initialBalance = services.save.coins,
            onBuy = { powerUp ->
                if (services.save.spendCoins(powerUp.price)) {
                    services.save.addPowerUp(powerUp, 1)
                    services.audio.play(AudioManager.Sfx.POWER_UP)
                    services.haptics.vibrate(1)
                    services.save.coins
                } else {
                    -1
                }
            },
            onClose = { refresh() }
        ).show()
    }

    companion object {
        private const val BACK_EXIT_WINDOW_MS = 2200L
    }
}
