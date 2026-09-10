package com.infinitehits.bubbleblast.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.infinitehits.bubbleblast.R
import com.infinitehits.bubbleblast.core.powerup.PowerUp

/** Presentation data for the four power-ups (kept out of the gameplay enum). */
object PowerUpUi {

    @StringRes
    fun nameRes(powerUp: PowerUp): Int = when (powerUp) {
        PowerUp.AIM_EXTENSION -> R.string.powerup_aim
        PowerUp.COLOR_CHANGER -> R.string.powerup_color
        PowerUp.BOMB -> R.string.powerup_bomb
        PowerUp.EXTRA_MOVES -> R.string.powerup_moves
    }

    @StringRes
    fun descriptionRes(powerUp: PowerUp): Int = when (powerUp) {
        PowerUp.AIM_EXTENSION -> R.string.powerup_aim_desc
        PowerUp.COLOR_CHANGER -> R.string.powerup_color_desc
        PowerUp.BOMB -> R.string.powerup_bomb_desc
        PowerUp.EXTRA_MOVES -> R.string.powerup_moves_desc
    }

    @DrawableRes
    fun iconRes(powerUp: PowerUp): Int = when (powerUp) {
        PowerUp.AIM_EXTENSION -> R.drawable.ic_aim
        PowerUp.COLOR_CHANGER -> R.drawable.ic_color_drop
        PowerUp.BOMB -> R.drawable.ic_bomb
        PowerUp.EXTRA_MOVES -> R.drawable.ic_plus_circle
    }

    /** Canonical order used by the game HUD and the shop. */
    val order: List<PowerUp> = listOf(
        PowerUp.AIM_EXTENSION,
        PowerUp.COLOR_CHANGER,
        PowerUp.BOMB,
        PowerUp.EXTRA_MOVES
    )
}
