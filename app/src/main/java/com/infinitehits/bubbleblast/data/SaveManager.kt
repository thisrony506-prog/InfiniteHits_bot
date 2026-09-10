package com.infinitehits.bubbleblast.data

import android.content.Context
import android.content.SharedPreferences
import com.infinitehits.bubbleblast.core.engine.LevelResult
import com.infinitehits.bubbleblast.core.level.LevelCatalog
import com.infinitehits.bubbleblast.core.powerup.PowerUp

/**
 * Local, offline save game.
 *
 * Everything lives in a single [SharedPreferences] file so a device backup
 * restores levels, stars, coins and best scores in one go (see
 * `res/xml/backup_rules.xml`).
 *
 * Storage layout
 * --------------
 *  * `stars`   - one digit per level, `0..3`, in level order
 *  * `scores`  - `level:score` pairs joined with `;`, only for levels played
 *  * `unlocked`- highest unlocked level
 *  * `coins`, `best`, `plays`, `pops`
 *  * `power_*` - owned count per power-up
 *  * `daily_*` - streak bookkeeping
 */
class SaveManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var starsCache: IntArray = loadStars()
    private var bestScoresCache: HashMap<Int, Int> = loadBestScores()

    // ------------------------------------------------------------------
    // Progress
    // ------------------------------------------------------------------

    /** Highest level the player may enter. Levels are unlocked one at a time. */
    var unlockedLevel: Int
        get() = prefs.getInt(KEY_UNLOCKED, 1).coerceIn(1, LevelCatalog.LEVEL_COUNT)
        private set(value) {
            prefs.edit().putInt(KEY_UNLOCKED, value.coerceIn(1, LevelCatalog.LEVEL_COUNT)).apply()
        }

    var coins: Int
        get() = prefs.getInt(KEY_COINS, STARTING_COINS)
        private set(value) {
            prefs.edit().putInt(KEY_COINS, value.coerceAtLeast(0)).apply()
        }

    /** Best single-level score, shown on the game over screen. */
    var bestScore: Int
        get() = prefs.getInt(KEY_BEST_SCORE, 0)
        private set(value) {
            if (value > bestScore) prefs.edit().putInt(KEY_BEST_SCORE, value).apply()
        }

    val totalStars: Int get() = starsCache.sum()

    val totalPlays: Int get() = prefs.getInt(KEY_PLAYS, 0)

    val totalPops: Int get() = prefs.getInt(KEY_POPS, 0)

    fun starsFor(level: Int): Int {
        val index = level - 1
        return if (index in starsCache.indices) starsCache[index] else 0
    }

    fun bestScoreFor(level: Int): Int = bestScoresCache[level] ?: 0

    fun isLevelUnlocked(level: Int): Boolean = level <= unlockedLevel

    fun isLevelCleared(level: Int): Boolean = starsFor(level) > 0

    /** Level suggested by the Play button. */
    fun nextLevelToPlay(): Int = LevelCatalog.nextPlayableLevel(unlockedLevel, starMap())

    fun starMap(): Map<Int, Int> {
        val map = HashMap<Int, Int>()
        for (level in 1..LevelCatalog.LEVEL_COUNT) {
            val stars = starsFor(level)
            if (stars > 0) map[level] = stars
        }
        return map
    }

    /**
     * Stores the outcome of a finished level.
     * @return true when this was the first clear of that level
     */
    fun recordLevelResult(result: LevelResult): Boolean {
        val firstClear = result.stars > 0 && starsFor(result.level) == 0

        if (result.stars > starsFor(result.level)) {
            val index = result.level - 1
            if (index in starsCache.indices) {
                starsCache[index] = result.stars
                prefs.edit().putString(KEY_STARS, encodeStars(starsCache)).apply()
            }
        }

        if (result.score > bestScoreFor(result.level)) {
            bestScoresCache[result.level] = result.score
            prefs.edit().putString(KEY_SCORES, encodeScores(bestScoresCache)).apply()
        }

        bestScore = result.score

        if (result.stars > 0) {
            addCoins(result.coinsEarned)
            if (result.level >= unlockedLevel && result.level < LevelCatalog.LEVEL_COUNT) {
                unlockedLevel = result.level + 1
            }
        }

        prefs.edit()
            .putInt(KEY_PLAYS, totalPlays + 1)
            .putInt(KEY_POPS, totalPops + result.popped)
            .apply()
        return firstClear
    }

    fun registerPlay() {
        prefs.edit().putInt(KEY_PLAYS, totalPlays + 1).apply()
    }

    // ------------------------------------------------------------------
    // Coins & power-ups
    // ------------------------------------------------------------------

    fun addCoins(amount: Int): Int {
        if (amount == 0) return coins
        coins += amount
        return coins
    }

    fun spendCoins(amount: Int): Boolean {
        if (amount <= 0) return true
        if (coins < amount) return false
        coins -= amount
        return true
    }

    fun powerUpCount(powerUp: PowerUp): Int = prefs.getInt(powerUpKey(powerUp), 0)

    fun powerUpInventory(): Map<PowerUp, Int> = PowerUp.ALL.associateWith { powerUpCount(it) }

    fun addPowerUp(powerUp: PowerUp, amount: Int = 1): Int {
        val current = powerUpCount(powerUp)
        val updated = (current + amount).coerceAtLeast(0)
        prefs.edit().putInt(powerUpKey(powerUp), updated).apply()
        return updated
    }

    fun consumePowerUp(powerUp: PowerUp): Boolean {
        val current = powerUpCount(powerUp)
        if (current <= 0) return false
        prefs.edit().putInt(powerUpKey(powerUp), current - 1).apply()
        return true
    }

    // ------------------------------------------------------------------
    // Daily reward bookkeeping
    // ------------------------------------------------------------------

    var lastDailyClaimDay: Long
        get() = prefs.getLong(KEY_DAILY_LAST_DAY, -1L)
        set(value) = prefs.edit().putLong(KEY_DAILY_LAST_DAY, value).apply()

    var dailyStreak: Int
        get() = prefs.getInt(KEY_DAILY_STREAK, 0)
        set(value) = prefs.edit().putInt(KEY_DAILY_STREAK, value.coerceAtLeast(0)).apply()

    // ------------------------------------------------------------------
    // Reset
    // ------------------------------------------------------------------

    /** Wipes every trace of the player's progress. Settings are kept. */
    fun resetProgress() {
        starsCache = IntArray(LevelCatalog.LEVEL_COUNT)
        bestScoresCache = HashMap()
        prefs.edit()
            .remove(KEY_STARS)
            .remove(KEY_SCORES)
            .remove(KEY_UNLOCKED)
            .remove(KEY_COINS)
            .remove(KEY_BEST_SCORE)
            .remove(KEY_PLAYS)
            .remove(KEY_POPS)
            .remove(KEY_DAILY_LAST_DAY)
            .remove(KEY_DAILY_STREAK)
            .apply()
        for (powerUp in PowerUp.ALL) {
            prefs.edit().putInt(powerUpKey(powerUp), PowerUp.STARTING_INVENTORY[powerUp] ?: 0).apply()
        }
    }

    /** Called once after install so a new player owns a few power-ups. */
    fun ensureStartingInventory() {
        if (prefs.getBoolean(KEY_INITIALISED, false)) return
        for (powerUp in PowerUp.ALL) {
            prefs.edit().putInt(powerUpKey(powerUp), PowerUp.STARTING_INVENTORY[powerUp] ?: 0).apply()
        }
        prefs.edit().putBoolean(KEY_INITIALISED, true).apply()
    }

    // ------------------------------------------------------------------
    // (De)serialisation
    // ------------------------------------------------------------------

    private fun loadStars(): IntArray {
        val stars = IntArray(LevelCatalog.LEVEL_COUNT)
        val encoded = prefs.getString(KEY_STARS, null) ?: return stars
        for (i in 0 until minOf(encoded.length, stars.size)) {
            val value = encoded[i] - '0'
            stars[i] = if (value in 0..3) value else 0
        }
        return stars
    }

    private fun encodeStars(stars: IntArray): String {
        val builder = StringBuilder(stars.size)
        for (value in stars) builder.append(value.coerceIn(0, 3))
        return builder.toString()
    }

    private fun loadBestScores(): HashMap<Int, Int> {
        val map = HashMap<Int, Int>()
        val encoded = prefs.getString(KEY_SCORES, null) ?: return map
        for (entry in encoded.split(';')) {
            if (entry.isEmpty()) continue
            val parts = entry.split(':')
            if (parts.size != 2) continue
            val level = parts[0].toIntOrNull() ?: continue
            val score = parts[1].toIntOrNull() ?: continue
            if (level >= 1 && level <= LevelCatalog.LEVEL_COUNT && score > 0) map[level] = score
        }
        return map
    }

    private fun encodeScores(scores: Map<Int, Int>): String =
        scores.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value}" }

    private fun powerUpKey(powerUp: PowerUp): String = "power_" + powerUp.name.lowercase()

    companion object {
        /** Preferences file name - shared with SettingsManager so one backup covers everything. */
        const val PREFS_NAME = "bubble_blast_prefs"

        private const val KEY_STARS = "stars"
        private const val KEY_SCORES = "scores"
        private const val KEY_UNLOCKED = "unlocked"
        private const val KEY_COINS = "coins"
        private const val KEY_BEST_SCORE = "best"
        private const val KEY_PLAYS = "plays"
        private const val KEY_POPS = "pops"
        private const val KEY_DAILY_LAST_DAY = "daily_last_day"
        private const val KEY_DAILY_STREAK = "daily_streak"
        private const val KEY_INITIALISED = "initialised"

        /** A fresh player starts with enough coins for one power-up. */
        const val STARTING_COINS = 150

        @Volatile
        private var instance: SaveManager? = null

        fun get(context: Context): SaveManager =
            instance ?: synchronized(this) {
                instance ?: SaveManager(context).also { it.ensureStartingInventory(); instance = it }
            }
    }
}
