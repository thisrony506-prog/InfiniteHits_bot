package com.infinitehits.bubbleblast.core.level

import com.infinitehits.bubbleblast.core.model.BubbleColor
import com.infinitehits.bubbleblast.core.model.BubbleKind

/**
 * Everything the engine needs to build and play one level. Definitions are
 * created by [LevelCatalog] and are immutable and deterministic: the same level
 * number always produces the same definition and the same starting board.
 */
data class LevelDefinition(
    /** 1-based level number. */
    val level: Int,

    /** Display name of the chapter this level belongs to. */
    val chapterName: String,

    /** Index of the chapter, 0-based. */
    val chapter: Int,

    /** Key into [LayoutPatterns]. */
    val pattern: String,

    /** Colours that may appear on the board. */
    val colors: List<BubbleColor>,

    /** Seed for the level generator - derived from [level]. */
    val seed: Long,

    /** Score needed to clear the level. */
    val targetScore: Int,

    /** Shots the player gets. */
    val moves: Int,

    /** A new row is pushed in every N shots; 0 disables drops. */
    val shotsBeforeDrop: Int,

    /** How many rows are pushed each time. */
    val dropRowCount: Int,

    /** Extra clearance rows taken off the danger line (higher = stricter). */
    val dangerExtraRows: Int,

    /** Probability that a coloured board bubble is upgraded to a special. */
    val specialChance: Float,

    /** Specials the launcher may randomly load. */
    val allowedSpecials: List<BubbleKind>,

    /** Extra ice layers on every [BubbleKind.ICE] bubble in the pattern. */
    val extraIceLayers: Int,

    /** Rows that must fit above the danger line - drives the bubble radius. */
    val maxRowsVisible: Int = 10,

    /** Chapter finale levels get a small visual flourish and tighter limits. */
    val isBossLevel: Boolean = false
) {
    /** Star score thresholds: 1 star clears, 2 and 3 need more. */
    val twoStarScore: Int get() = Math.round(targetScore * TWO_STAR_FACTOR)
    val threeStarScore: Int get() = Math.round(targetScore * THREE_STAR_FACTOR)

    val hasDrops: Boolean get() = shotsBeforeDrop > 0

    companion object {
        const val TWO_STAR_FACTOR = 1.22f
        const val THREE_STAR_FACTOR = 1.50f
    }
}
