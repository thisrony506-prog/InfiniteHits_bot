package com.infinitehits.bubbleblast.core.score

/**
 * All score maths of a single level, in one place so it can be unit tested and
 * tuned without touching the engine.
 *
 * Scoring model
 * -------------
 * | event                              | points                                   |
 * |------------------------------------|------------------------------------------|
 * | bubble popped                      | 10 (+ 8 for every bubble past the third) |
 * | dropped (disconnected) cluster     | 20 per bubble, combo multiplier applies  |
 * | special bubble destroyed           | +50 per special that fired               |
 * | combo multiplier                   | 1.0x, +0.25x per consecutive pop, max 3x |
 * | perfect shot (6+ bubbles at once)  | +100                                     |
 * | cleared the whole board            | +1000                                    |
 * | unused move at the end of a level  | +25 each                                 |
 */
class ScoreSystem {

    var score: Int = 0
        private set

    /** Consecutive shots that popped at least one bubble. */
    var combo: Int = 0
        private set

    var bestCombo: Int = 0
        private set

    var poppedCount: Int = 0
        private set

    var droppedCount: Int = 0
        private set

    var specialsTriggered: Int = 0
        private set

    /** Multiplier applied to every score event of the current shot. */
    val multiplier: Float
        get() = (1f + (combo - 1) * COMBO_STEP).coerceIn(1f, MAX_MULTIPLIER)

    /** Called once per shot that pops something. */
    fun registerCombo() {
        combo++
        if (combo > bestCombo) bestCombo = combo
    }

    fun resetCombo() {
        combo = 0
    }

    /**
     * Awards the points for a shot that destroyed [count] bubbles.
     * @return the points awarded (already multiplied)
     */
    fun registerPop(count: Int, specialsDestroyed: Int): Int {
        if (count <= 0) return 0
        poppedCount += count
        specialsTriggered += specialsDestroyed

        var points = count * POINTS_PER_BUBBLE
        if (count > MIN_MATCH) points += (count - MIN_MATCH) * EXTRA_BUBBLE_BONUS
        points += specialsDestroyed * SPECIAL_BONUS
        points = Math.round(points * multiplier)

        score += points
        return points
    }

    /** Flat bonus points that ignore the combo multiplier (ice cracks, events). */
    fun registerBonus(points: Int): Int {
        if (points <= 0) return 0
        score += points
        return points
    }

    /** Bonus for a shot that pops a lot of bubbles at once. */
    fun registerPerfectShot(count: Int): Int {
        if (count < PERFECT_SHOT_SIZE) return 0
        val points = Math.round(PERFECT_SHOT_BONUS * multiplier)
        score += points
        return points
    }

    /** Awards the points for bubbles that lost their anchor. */
    fun registerDrop(count: Int): Int {
        if (count <= 0) return 0
        droppedCount += count
        val points = Math.round(count * POINTS_PER_DROPPED * multiplier)
        score += points
        return points
    }

    /** Bonus when the player wipes every destructible bubble off the board. */
    fun registerBoardClear(): Int {
        score += BOARD_CLEAR_BONUS
        return BOARD_CLEAR_BONUS
    }

    /** Converts unused moves into points when a level is completed. */
    fun registerRemainingMoves(movesLeft: Int): Int {
        if (movesLeft <= 0) return 0
        val points = movesLeft * POINTS_PER_SPARE_MOVE
        score += points
        return points
    }

    fun reset() {
        score = 0
        combo = 0
        bestCombo = 0
        poppedCount = 0
        droppedCount = 0
        specialsTriggered = 0
    }

    companion object {
        const val POINTS_PER_BUBBLE = 10
        const val EXTRA_BUBBLE_BONUS = 8
        const val POINTS_PER_DROPPED = 20
        const val SPECIAL_BONUS = 50
        const val PERFECT_SHOT_SIZE = 6
        const val PERFECT_SHOT_BONUS = 100
        const val BOARD_CLEAR_BONUS = 1000
        const val POINTS_PER_SPARE_MOVE = 25
        const val MIN_MATCH = 3
        const val COMBO_STEP = 0.25f
        const val MAX_MULTIPLIER = 3f
    }
}
