package com.infinitehits.bubbleblast.core.score

import com.infinitehits.bubbleblast.core.level.LevelDefinition

/** Turns a finished level into stars and coins. */
object StarRating {

    const val MAX_STARS = 3

    /**
     * 1 star for reaching the target, 2 and 3 stars for beating it by a wider
     * margin. Wiping the board is always worth the full three stars.
     */
    fun starsFor(definition: LevelDefinition, score: Int, boardCleared: Boolean): Int = when {
        boardCleared -> MAX_STARS
        score >= definition.threeStarScore -> 3
        score >= definition.twoStarScore -> 2
        score >= definition.targetScore -> 1
        else -> 0
    }

    /**
     * Coin payout of a finished level. First-time clears pay a bonus so
     * replaying an old level is still worth something, but never as much as
     * pushing forward.
     */
    fun coinsFor(stars: Int, score: Int, firstClear: Boolean): Int {
        if (stars <= 0) return score / 900
        val base = 10
        val perStar = stars * 12
        val scoreBonus = (score / 400).coerceAtMost(20)
        val firstBonus = if (firstClear) 25 else 0
        return (base + perStar + scoreBonus + firstBonus).coerceAtMost(140)
    }
}
