package com.infinitehits.bubbleblast.ui.dialog

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.infinitehits.bubbleblast.R
import com.infinitehits.bubbleblast.core.engine.GameOverReason
import com.infinitehits.bubbleblast.core.engine.GameOverResult
import com.infinitehits.bubbleblast.core.engine.LevelResult
import com.infinitehits.bubbleblast.core.level.LevelCatalog
import com.infinitehits.bubbleblast.core.powerup.PowerUp
import com.infinitehits.bubbleblast.data.DailyRewardManager
import com.infinitehits.bubbleblast.ui.PowerUpUi
import com.infinitehits.bubbleblast.util.UiUtils

/**
 * Base class for the rounded, dimmed panels the game uses instead of the stock
 * platform dialogs. Every dialog is dismissible only through its own buttons so
 * a level can never be quit by an accidental back tap.
 */
abstract class StyledDialog(activity: Activity, layoutRes: Int) : Dialog(activity, R.style.Dialog_BubbleBlast) {

    protected val content: View = View.inflate(activity, layoutRes, null)

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(content)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.92f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.62f)
            attributes = attributes.apply { gravity = android.view.Gravity.CENTER }
        }
        setCanceledOnTouchOutside(false)
        setCancelable(false)
    }

    protected fun button(viewId: Int, action: () -> Unit) {
        content.findViewById<View>(viewId)?.let { view ->
            view.setOnClickListener {
                UiUtils.pulse(view)
                action()
            }
            UiUtils.attachPressEffect(view)
        }
    }

    protected fun text(viewId: Int, value: CharSequence?) {
        content.findViewById<TextView>(viewId)?.text = value
    }

    protected fun hide(viewId: Int) {
        UiUtils.setVisible(content.findViewById(viewId) ?: return, false)
    }

    protected fun show(viewId: Int) {
        UiUtils.setVisible(content.findViewById(viewId) ?: return, true)
    }

    protected fun setEnabledState(viewId: Int, enabled: Boolean) {
        content.findViewById<View>(viewId)?.apply {
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.45f
        }
    }
}

// ----------------------------------------------------------------------
// Pause
// ----------------------------------------------------------------------

class PauseDialog(
    activity: Activity,
    level: Int,
    chapterName: String,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onSettings: () -> Unit,
    onHome: () -> Unit
) : StyledDialog(activity, R.layout.dialog_pause) {

    init {
        text(R.id.pause_level_text, activity.getString(R.string.hud_level, level) + " · " + chapterName)
        button(R.id.pause_resume_button) { dismiss(); onResume() }
        button(R.id.pause_restart_button) { dismiss(); onRestart() }
        button(R.id.pause_settings_button) { dismiss(); onSettings() }
        button(R.id.pause_home_button) { dismiss(); onHome() }
    }
}

// ----------------------------------------------------------------------
// Level complete
// ----------------------------------------------------------------------

class LevelCompleteDialog(
    activity: Activity,
    private val result: LevelResult,
    bestScoreForLevel: Int,
    rewardedAdAvailable: Boolean,
    onReplay: () -> Unit,
    onNext: () -> Unit,
    onHome: () -> Unit,
    onDoubleCoins: () -> Unit
) : StyledDialog(activity, R.layout.dialog_level_complete) {

    init {
        val stars = listOf(
            R.id.complete_star_1 to 1,
            R.id.complete_star_2 to 2,
            R.id.complete_star_3 to 3
        )
        for ((viewId, index) in stars) {
            val star = content.findViewById<ImageView>(viewId)
            UiUtils.revealStar(star, result.stars >= index, delayMillis = 220L * index)
        }

        text(R.id.complete_score_text, format(result.score))
        text(R.id.complete_best_text, format(maxOf(bestScoreForLevel, result.score)))
        text(R.id.complete_coins_text, "+" + format(result.coinsEarned))
        UiUtils.setVisible(content.findViewById(R.id.complete_new_best_text), result.isNewBest)
        UiUtils.bounce(content.findViewById(R.id.complete_star_2), 150L)

        if (rewardedAdAvailable && result.coinsEarned > 0) {
            show(R.id.complete_double_button)
            button(R.id.complete_double_button) { onDoubleCoins() }
        } else {
            hide(R.id.complete_double_button)
        }

        val isLastLevel = result.level >= LevelCatalog.LEVEL_COUNT
        val nextButton = content.findViewById<TextView>(R.id.complete_next_button)
        nextButton.text = activity.getString(R.string.level_complete_next)
        if (isLastLevel) {
            UiUtils.setVisible(nextButton, false)
            text(
                R.id.complete_new_best_text,
                activity.getString(R.string.level_complete_all_done)
            )
            UiUtils.setVisible(content.findViewById(R.id.complete_new_best_text), true)
        } else {
            button(R.id.complete_next_button) { dismiss(); onNext() }
        }

        button(R.id.complete_replay_button) { dismiss(); onReplay() }
        button(R.id.complete_home_button) { dismiss(); onHome() }
    }

    private fun format(value: Int) = String.format("%,d", value)
}

// ----------------------------------------------------------------------
// Game over
// ----------------------------------------------------------------------

class GameOverDialog(
    activity: Activity,
    private val result: GameOverResult,
    rewardedAdAvailable: Boolean,
    onRetry: () -> Unit,
    onLevels: () -> Unit,
    onHome: () -> Unit,
    onContinueWithAd: () -> Unit
) : StyledDialog(activity, R.layout.dialog_game_over) {

    init {
        text(
            R.id.gameover_reason_text,
            activity.getString(
                when (result.reason) {
                    GameOverReason.OUT_OF_MOVES -> R.string.game_over_reason_moves
                    GameOverReason.DANGER_LINE -> R.string.game_over_reason_danger
                }
            )
        )
        text(R.id.gameover_score_text, String.format("%,d", result.score))
        text(R.id.gameover_best_text, String.format("%,d", result.bestScore))

        if (rewardedAdAvailable) {
            show(R.id.gameover_continue_button)
            button(R.id.gameover_continue_button) { dismiss(); onContinueWithAd() }
        } else {
            hide(R.id.gameover_continue_button)
        }

        button(R.id.gameover_retry_button) { dismiss(); onRetry() }
        button(R.id.gameover_levels_button) { dismiss(); onLevels() }
        button(R.id.gameover_home_button) { dismiss(); onHome() }
    }
}

// ----------------------------------------------------------------------
// Generic confirmation
// ----------------------------------------------------------------------

class ConfirmDialog(
    activity: Activity,
    title: CharSequence,
    message: CharSequence,
    positiveLabel: CharSequence,
    negativeLabel: CharSequence,
    onConfirm: () -> Unit
) : StyledDialog(activity, R.layout.dialog_confirm) {

    init {
        text(R.id.confirm_title, title)
        text(R.id.confirm_message, message)
        val positive = content.findViewById<TextView>(R.id.confirm_positive_button)
        positive.text = positiveLabel
        val negative = content.findViewById<TextView>(R.id.confirm_negative_button)
        negative.text = negativeLabel

        button(R.id.confirm_positive_button) { dismiss(); onConfirm() }
        button(R.id.confirm_negative_button) { dismiss() }
    }
}

// ----------------------------------------------------------------------
// About
// ----------------------------------------------------------------------

class AboutDialog(activity: Activity, versionName: String) : StyledDialog(activity, R.layout.dialog_about) {
    init {
        text(R.id.about_version_text, activity.getString(R.string.settings_version, versionName))
        button(R.id.about_close_button) { dismiss() }
    }
}

// ----------------------------------------------------------------------
// Daily reward
// ----------------------------------------------------------------------

class DailyRewardDialog(
    activity: Activity,
    private val rewards: List<DailyRewardManager.Reward>,
    private val nextIndex: Int,
    private val canClaim: Boolean,
    private val streak: Int,
    private val onClaim: () -> Unit,
    private val onClose: () -> Unit
) : StyledDialog(activity, R.layout.dialog_daily_reward) {

    init {
        text(R.id.daily_streak_text, activity.getString(R.string.daily_streak, streak.coerceAtLeast(1)))

        val container = content.findViewById<LinearLayout>(R.id.daily_days_container)
        container.removeAllViews()
        for ((index, reward) in rewards.withIndex()) {
            val cell = View.inflate(activity, R.layout.item_daily_day, null)
            cell.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

            val label = cell.findViewById<TextView>(R.id.daily_day_label)
            label.text = (index + 1).toString()

            val value = cell.findViewById<TextView>(R.id.daily_day_value)
            value.text = if (reward.powerUp != null && reward.powerUpCount > 0) {
                "+" + reward.powerUpCount
            } else {
                "+" + reward.coins
            }

            val icon = cell.findViewById<ImageView>(R.id.daily_day_icon)
            if (reward.powerUp != null) {
                icon.setImageResource(com.infinitehits.bubbleblast.ui.PowerUpUi.iconRes(reward.powerUp))
            }

            val isToday = index == nextIndex
            cell.alpha = if (isToday) 1f else 0.55f
            cell.setBackgroundResource(
                if (isToday) R.drawable.bg_level_current else R.drawable.bg_star_slot
            )
            container.addView(cell)
        }

        val claimButton = content.findViewById<TextView>(R.id.daily_claim_button)
        claimButton.text = activity.getString(if (canClaim) R.string.daily_claim else R.string.daily_claimed)
        setEnabledState(R.id.daily_claim_button, canClaim)

        button(R.id.daily_claim_button) {
            if (canClaim) {
                onClaim()
                dismiss()
            }
        }
        button(R.id.daily_close_button) {
            dismiss()
            onClose()
        }
    }
}

// ----------------------------------------------------------------------
// Power-up shop
// ----------------------------------------------------------------------

class ShopDialog(
    activity: Activity,
    private val powerUps: List<PowerUp>,
    initialOwned: Map<PowerUp, Int>,
    initialBalance: Int,
    /**
     * Performs the purchase.
     * @return the new coin balance, or a negative number when the player cannot afford it
     */
    private val onBuy: (PowerUp) -> Int,
    private val onClose: () -> Unit
) : StyledDialog(activity, R.layout.dialog_shop) {

    private val balanceView: TextView = content.findViewById(R.id.shop_balance_text)
    private val container: LinearLayout = content.findViewById(R.id.shop_rows_container)

    private var balance: Int = initialBalance
    private val owned = HashMap<PowerUp, Int>(initialOwned)

    init {
        render()
        button(R.id.shop_close_button) {
            dismiss()
            onClose()
        }
    }

    private fun render() {
        balanceView.text = activity.getString(R.string.shop_balance, String.format("%,d", balance))
        container.removeAllViews()

        for (powerUp in powerUps) {
            val row = View.inflate(activity, R.layout.view_powerup_row, null)
            row.findViewById<ImageView>(R.id.shop_row_icon).setImageResource(PowerUpUi.iconRes(powerUp))
            row.findViewById<TextView>(R.id.shop_row_name).setText(PowerUpUi.nameRes(powerUp))
            row.findViewById<TextView>(R.id.shop_row_desc).setText(PowerUpUi.descriptionRes(powerUp))
            row.findViewById<TextView>(R.id.shop_row_owned)
                .text = activity.getString(R.string.powerup_owned, owned[powerUp] ?: 0)
            row.findViewById<TextView>(R.id.shop_row_price).text = String.format("%,d", powerUp.price)

            val buy = row.findViewById<View>(R.id.shop_row_buy_button)
            UiUtils.attachPressEffect(buy)
            buy.setOnClickListener {
                UiUtils.pulse(buy)
                val newBalance = onBuy(powerUp)
                if (newBalance >= 0) {
                    balance = newBalance
                    owned[powerUp] = (owned[powerUp] ?: 0) + 1
                    UiUtils.toast(
                        activity,
                        activity.getString(R.string.shop_purchased, activity.getString(PowerUpUi.nameRes(powerUp)))
                    )
                    render()
                } else {
                    UiUtils.toast(activity, R.string.shop_not_enough)
                }
            }
            container.addView(row)
        }
    }
}
