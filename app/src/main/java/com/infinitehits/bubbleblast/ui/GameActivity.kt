package com.infinitehits.bubbleblast.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import com.infinitehits.bubbleblast.R
import com.infinitehits.bubbleblast.ads.AdManager
import com.infinitehits.bubbleblast.audio.AudioManager
import com.infinitehits.bubbleblast.core.engine.BubbleSpec
import com.infinitehits.bubbleblast.core.engine.GameOverResult
import com.infinitehits.bubbleblast.core.engine.GameStats
import com.infinitehits.bubbleblast.core.engine.LevelResult
import com.infinitehits.bubbleblast.core.level.LevelCatalog
import com.infinitehits.bubbleblast.core.level.LevelDefinition
import com.infinitehits.bubbleblast.core.powerup.PowerUp
import com.infinitehits.bubbleblast.game.GameView
import com.infinitehits.bubbleblast.ui.dialog.ConfirmDialog
import com.infinitehits.bubbleblast.ui.dialog.GameOverDialog
import com.infinitehits.bubbleblast.ui.dialog.LevelCompleteDialog
import com.infinitehits.bubbleblast.ui.dialog.PauseDialog
import com.infinitehits.bubbleblast.ui.dialog.ShopDialog
import com.infinitehits.bubbleblast.ui.view.BubblePreviewView
import com.infinitehits.bubbleblast.util.Navigator
import com.infinitehits.bubbleblast.util.UiUtils

/**
 * The playable level screen: HUD on top, [GameView] in the middle, power-ups and
 * the next-bubble preview underneath.
 */
class GameActivity : BaseActivity(), GameView.Listener {

    override val musicScene: AudioManager.Scene get() = AudioManager.Scene.GAME

    private lateinit var gameView: GameView
    private lateinit var definition: LevelDefinition

    private lateinit var scoreText: TextView
    private lateinit var targetText: TextView
    private lateinit var coinsText: TextView
    private lateinit var movesText: TextView
    private lateinit var comboText: TextView
    private lateinit var levelText: TextView
    private lateinit var nextPreview: BubblePreviewView

    private val powerUpButtons = HashMap<PowerUp, View>()
    private val powerUpCounts = HashMap<PowerUp, TextView>()
    private val handler = Handler(Looper.getMainLooper())

    private var level: Int = 1
    private var currentScore: Int = 0
    private var resultDialogShown = false
    private var pauseDialog: PauseDialog? = null
    private var shopDialog: ShopDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)
        UiUtils.applySystemBarInsets(findViewById(R.id.game_root))

        level = intent.getIntExtra(Navigator.EXTRA_LEVEL, services.save.nextLevelToPlay())
        definition = LevelCatalog.get(level)

        bindViews()
        wireButtons()
        applyLeftHandedLayout()
        startLevel(level)
    }

    private fun bindViews() {
        gameView = findViewById(R.id.game_surface)
        scoreText = findViewById(R.id.hud_score_text)
        targetText = findViewById(R.id.hud_target_text)
        coinsText = findViewById(R.id.hud_coins_text)
        movesText = findViewById(R.id.game_moves_text)
        comboText = findViewById(R.id.game_combo_text)
        levelText = findViewById(R.id.hud_level_text)
        nextPreview = findViewById(R.id.game_next_preview)

        powerUpButtons[PowerUp.AIM_EXTENSION] = findViewById(R.id.powerup_aim_button)
        powerUpButtons[PowerUp.COLOR_CHANGER] = findViewById(R.id.powerup_color_button)
        powerUpButtons[PowerUp.BOMB] = findViewById(R.id.powerup_bomb_button)
        powerUpButtons[PowerUp.EXTRA_MOVES] = findViewById(R.id.powerup_moves_button)

        powerUpCounts[PowerUp.AIM_EXTENSION] = findViewById(R.id.powerup_aim_count)
        powerUpCounts[PowerUp.COLOR_CHANGER] = findViewById(R.id.powerup_color_count)
        powerUpCounts[PowerUp.BOMB] = findViewById(R.id.powerup_bomb_count)
        powerUpCounts[PowerUp.EXTRA_MOVES] = findViewById(R.id.powerup_moves_count)
    }

    private fun wireButtons() {
        val pauseButton = findViewById<ImageButton>(R.id.hud_pause_button)
        pauseButton.setOnClickListener {
            playClick()
            showPauseDialog()
        }
        UiUtils.attachPressEffect(pauseButton)

        for ((powerUp, view) in powerUpButtons) {
            UiUtils.attachPressEffect(view)
            view.setOnClickListener {
                playClick()
                onPowerUpClicked(powerUp)
            }
        }

        findViewById<View>(R.id.hud_coins_text).setOnClickListener {
            playClick()
            showShop()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (resultDialogShown) return
                showPauseDialog()
            }
        })
    }

    private fun startLevel(newLevel: Int) {
        level = newLevel.coerceIn(1, LevelCatalog.LEVEL_COUNT)
        definition = LevelCatalog.get(level)
        resultDialogShown = false
        currentScore = 0

        levelText.text = getString(R.string.hud_level, level)
        targetText.text = getString(R.string.hud_target, format(definition.targetScore))
        movesText.text = definition.moves.toString()
        scoreText.text = "0"
        coinsText.text = format(services.save.coins)
        comboText.alpha = 0f

        gameView.attachServices(services)
        gameView.listener = this
        gameView.showTrajectory = services.settings.showTrajectory
        gameView.startLevel(
            definition = definition,
            inventory = services.save.powerUpInventory(),
            firstClearAttempt = !services.save.isLevelCleared(level),
            previousBestScore = services.save.bestScoreFor(level)
        )

        updatePowerUpButtons(services.save.powerUpInventory())
        findViewById<View>(R.id.game_bottom_bar).startAnimation(
            android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_up_in)
        )
        services.save.registerPlay()
        services.ads.preloadRewarded(AdManager.Placement.DOUBLE_COINS_REWARDED)
        services.ads.preloadRewarded(AdManager.Placement.EXTRA_MOVES_REWARDED)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val requested = intent.getIntExtra(Navigator.EXTRA_LEVEL, level)
        if (requested != level) {
            startLevel(requested)
        } else {
            gameView.restartLevel()
        }
    }

    override fun onResume() {
        super.onResume()
        gameView.onForeground(true)
        gameView.showTrajectory = services.settings.showTrajectory
        coinsText.text = format(services.save.coins)
        applyLeftHandedLayout()
    }

    override fun onPause() {
        gameView.onForeground(false)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        pauseDialog?.dismiss()
        shopDialog?.dismiss()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private fun showPauseDialog() {
        if (pauseDialog?.isShowing == true) return
        gameView.setUserPaused(true)
        pauseDialog = PauseDialog(
            activity = this,
            level = level,
            chapterName = definition.chapterName,
            onResume = { gameView.setUserPaused(false) },
            onRestart = {
                gameView.setUserPaused(false)
                startLevel(level)
            },
            onSettings = {
                gameView.setUserPaused(false)
                Navigator.toSettings(this)
            },
            onHome = {
                // The level is thrown away, so confirm before leaving.
                ConfirmDialog(
                    activity = this,
                    title = getString(R.string.pause_quit_title),
                    message = getString(R.string.pause_quit_message),
                    positiveLabel = getString(R.string.pause_home),
                    negativeLabel = getString(R.string.pause_resume),
                    onConfirm = {
                        gameView.setUserPaused(false)
                        Navigator.toHomeClearingStack(this)
                    }
                ).show()
            }
        ).also { it.show() }
    }

    private fun onPowerUpClicked(powerUp: PowerUp) {
        val owned = services.save.powerUpCount(powerUp)
        if (owned <= 0) {
            UiUtils.toast(this, getString(R.string.powerup_none_left, getString(PowerUpUi.nameRes(powerUp))))
            showShop()
            return
        }
        services.audio.play(AudioManager.Sfx.POWER_UP)
        gameView.usePowerUp(powerUp)
        when (powerUp) {
            PowerUp.AIM_EXTENSION ->
                UiUtils.toast(this, getString(R.string.powerup_aim_active, PowerUp.AIM_EXTENSION_SHOTS))
            PowerUp.COLOR_CHANGER -> UiUtils.toast(this, R.string.powerup_color_changed)
            PowerUp.BOMB -> UiUtils.toast(this, R.string.powerup_bomb_armed)
            PowerUp.EXTRA_MOVES ->
                UiUtils.toast(this, getString(R.string.powerup_added_moves, PowerUp.EXTRA_MOVES_AMOUNT))
        }
    }

    private fun showShop() {
        if (shopDialog?.isShowing == true) return
        shopDialog = ShopDialog(
            activity = this,
            powerUps = PowerUpUi.order,
            initialOwned = services.save.powerUpInventory(),
            initialBalance = services.save.coins,
            onBuy = { powerUp ->
                if (services.save.spendCoins(powerUp.price)) {
                    services.save.addPowerUp(powerUp, 1)
                    services.audio.play(AudioManager.Sfx.POWER_UP)
                    updatePowerUpButtons(services.save.powerUpInventory())
                    gameView.updateInventory(services.save.powerUpInventory())
                    services.save.coins
                } else {
                    -1
                }
            },
            onClose = {
                coinsText.text = format(services.save.coins)
                updatePowerUpButtons(services.save.powerUpInventory())
            }
        ).also { it.show() }
    }

    private fun applyLeftHandedLayout() {
        val leftHanded = services.settings.leftHanded
        val container = findViewById<FrameLayout>(R.id.powerup_aim_button).parent as? LinearLayout ?: return
        val order = PowerUpUi.order.mapNotNull { powerUpButtons[it] }
        val desired = if (leftHanded) order.reversed() else order
        var needsRebuild = container.childCount != desired.size
        if (!needsRebuild) {
            for (index in desired.indices) {
                if (container.getChildAt(index) !== desired[index]) {
                    needsRebuild = true
                    break
                }
            }
        }
        if (!needsRebuild) return

        container.removeAllViews()
        for (view in desired) {
            (view.parent as? LinearLayout)?.removeView(view)
            container.addView(view)
        }
    }

    private fun updatePowerUpButtons(counts: Map<PowerUp, Int>) {
        for ((powerUp, view) in powerUpButtons) {
            val count = counts[powerUp] ?: 0
            powerUpCounts[powerUp]?.text = count.toString()
            view.alpha = if (count > 0) 1f else 0.45f
        }
    }

    // ------------------------------------------------------------------
    // GameView.Listener - always on the main thread
    // ------------------------------------------------------------------

    override fun onStatsChanged(stats: GameStats) {
        if (stats.score != currentScore) {
            currentScore = stats.score
            scoreText.text = format(stats.score)
            UiUtils.pulse(scoreText)
        }
        movesText.text = stats.moves.toString()
        if (stats.combo > 1) {
            comboText.text = getString(R.string.hud_combo, stats.combo)
            comboText.animate().alpha(1f).setDuration(120L).start()
        } else {
            comboText.animate().alpha(0f).setDuration(200L).start()
        }
    }

    override fun onAmmoChanged(current: BubbleSpec, next: BubbleSpec) {
        nextPreview.setBubble(next)
    }

    override fun onPowerUpCountsChanged(counts: Map<PowerUp, Int>) {
        // The engine consumed a power-up: mirror that in the save file.
        for (powerUp in PowerUp.ALL) {
            var stored = services.save.powerUpCount(powerUp)
            val engineCount = counts[powerUp] ?: 0
            while (stored > engineCount) {
                if (!services.save.consumePowerUp(powerUp)) break
                stored--
            }
        }
        updatePowerUpButtons(counts)
    }

    override fun onPowerUpRejected(powerUp: PowerUp) {
        UiUtils.toast(this, getString(R.string.powerup_none_left, getString(PowerUpUi.nameRes(powerUp))))
    }

    override fun onEngineReady() {
        coinsText.text = format(services.save.coins)
    }

    override fun onLevelComplete(result: LevelResult) {
        if (resultDialogShown) return
        resultDialogShown = true
        services.save.recordLevelResult(result)
        coinsText.text = format(services.save.coins)

        // Let the pop animation and the falling bubbles finish before the panel
        // covers the screen.
        handler.postDelayed({ showLevelCompleteDialog(result) }, RESULT_DIALOG_DELAY_MS)
    }

    private fun showLevelCompleteDialog(result: LevelResult) {
        if (isFinishing || isDestroyed) return
        val bestForLevel = services.save.bestScoreFor(result.level)
        val showAd = services.shouldShowLevelInterstitial()

        val present = {
            LevelCompleteDialog(
                activity = this,
                result = result,
                bestScoreForLevel = bestForLevel,
                rewardedAdAvailable = services.ads.isRewardedReady(AdManager.Placement.DOUBLE_COINS_REWARDED),
                onReplay = { replay() },
                onNext = { goToNextLevel(result.level) },
                onHome = { Navigator.toHomeClearingStack(this) },
                onDoubleCoins = { watchDoubleCoinsAd(result) }
            ).show()
        }

        if (showAd) {
            services.ads.showInterstitial(this, AdManager.Placement.LEVEL_COMPLETE_INTERSTITIAL, present)
        } else {
            present()
        }
    }

    override fun onGameOver(result: GameOverResult) {
        if (resultDialogShown) return
        resultDialogShown = true
        coinsText.text = format(services.save.coins)

        handler.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            val showAd = services.shouldShowGameOverInterstitial()
            val present = {
                GameOverDialog(
                    activity = this,
                    result = result,
                    rewardedAdAvailable = services.ads.isRewardedReady(AdManager.Placement.EXTRA_MOVES_REWARDED),
                    onRetry = { replay() },
                    onLevels = { Navigator.toLevelSelect(this) },
                    onHome = { Navigator.toHomeClearingStack(this) },
                    onContinueWithAd = { watchContinueAd() }
                ).show()
            }
            if (showAd) {
                services.ads.showInterstitial(this, AdManager.Placement.GAME_OVER_INTERSTITIAL, present)
            } else {
                present()
            }
        }, RESULT_DIALOG_DELAY_MS)
    }

    private fun replay() = startLevel(level)

    private fun goToNextLevel(completedLevel: Int) {
        if (completedLevel >= LevelCatalog.LEVEL_COUNT) {
            Navigator.toLevelSelect(this)
            return
        }
        Navigator.restartGame(this, completedLevel + 1)
    }

    private fun watchDoubleCoinsAd(result: LevelResult) {
        if (!services.ads.isRewardedReady(AdManager.Placement.DOUBLE_COINS_REWARDED)) {
            UiUtils.toast(this, R.string.reward_unavailable)
            return
        }
        services.ads.showRewarded(
            activity = this,
            placement = AdManager.Placement.DOUBLE_COINS_REWARDED,
            onReward = {
                services.save.addCoins(result.coinsEarned)
                coinsText.text = format(services.save.coins)
                UiUtils.toast(this, getString(R.string.reward_granted))
                services.audio.play(AudioManager.Sfx.STAR)
            },
            onFinished = { services.ads.preloadRewarded(AdManager.Placement.DOUBLE_COINS_REWARDED) }
        )
    }

    private fun watchContinueAd() {
        if (!services.ads.isRewardedReady(AdManager.Placement.EXTRA_MOVES_REWARDED)) {
            UiUtils.toast(this, R.string.reward_unavailable)
            return
        }
        services.ads.showRewarded(
            activity = this,
            placement = AdManager.Placement.EXTRA_MOVES_REWARDED,
            onReward = {
                resultDialogShown = false
                gameView.continueAfterGameOver()
            },
            onFinished = { services.ads.preloadRewarded(AdManager.Placement.EXTRA_MOVES_REWARDED) }
        )
    }

    private fun format(value: Int): String = String.format("%,d", value)

    companion object {
        /** Small pause so the player sees the final pop before the panel appears. */
        private const val RESULT_DIALOG_DELAY_MS = 520L
    }
}
