package com.infinitehits.bubbleblast.data

import com.infinitehits.bubbleblast.core.powerup.PowerUp

/**
 * Seven day login streak. Rewards grow through the week and the seventh day
 * pays out a bundle, which is the retention loop the game is built around.
 */
class DailyRewardManager(private val save: SaveManager) {

    data class Reward(
        val dayIndex: Int,
        val coins: Int,
        val powerUp: PowerUp? = null,
        val powerUpCount: Int = 0
    ) {
        val isBigReward: Boolean get() = dayIndex == REWARDS.lastIndex
    }

    /** Today, as days since the Unix epoch (UTC - good enough for a login streak). */
    private fun today(): Long = System.currentTimeMillis() / MILLIS_PER_DAY

    /** True when the player has not claimed today's reward yet. */
    fun canClaimToday(): Boolean = save.lastDailyClaimDay != today()

    /** Index into [REWARDS] that the next claim will pay out. */
    fun nextDayIndex(): Int {
        if (!canClaimToday()) return (save.dailyStreak - 1).coerceAtLeast(0) % REWARDS.size
        val streak = effectiveStreak()
        return streak % REWARDS.size
    }

    /** Number of consecutive days including today, for the "Day N" label. */
    fun currentStreak(): Int = effectiveStreak().coerceAtLeast(0)

    private fun effectiveStreak(): Int {
        val last = save.lastDailyClaimDay
        if (last < 0) return 0
        val gap = today() - last
        return when {
            gap <= 0L -> save.dailyStreak.coerceAtLeast(1)
            gap == 1L -> save.dailyStreak + 1
            else -> 1
        }
    }

    /**
     * Grants today's reward.
     * @return the reward that was granted, or null when it was already claimed today
     */
    fun claim(): Reward? {
        if (!canClaimToday()) return null

        val streak = effectiveStreak()
        val reward = REWARDS[streak % REWARDS.size]

        save.addCoins(reward.coins)
        reward.powerUp?.let { save.addPowerUp(it, reward.powerUpCount) }
        save.dailyStreak = streak
        save.lastDailyClaimDay = today()
        return reward
    }

    companion object {
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

        /** Coins/power-ups for days one to seven. */
        val REWARDS: List<Reward> = listOf(
            Reward(0, coins = 25),
            Reward(1, coins = 40),
            Reward(2, coins = 60),
            Reward(3, coins = 50, powerUp = PowerUp.AIM_EXTENSION, powerUpCount = 1),
            Reward(4, coins = 80),
            Reward(5, coins = 60, powerUp = PowerUp.BOMB, powerUpCount = 1),
            Reward(6, coins = 150, powerUp = PowerUp.COLOR_CHANGER, powerUpCount = 2)
        )
    }
}
