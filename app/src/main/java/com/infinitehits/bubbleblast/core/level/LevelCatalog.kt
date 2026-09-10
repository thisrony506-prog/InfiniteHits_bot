package com.infinitehits.bubbleblast.core.level

import com.infinitehits.bubbleblast.core.model.BubbleColor
import com.infinitehits.bubbleblast.core.model.BubbleKind

/**
 * The 120 bundled levels.
 *
 * Levels are generated instead of hand listed so difficulty can ramp smoothly,
 * but generation is fully deterministic: level *n* always produces the same
 * board, the same move budget and the same target score on every device.
 *
 * Difficulty knobs
 * ----------------
 *  * **moves** - `destructible bubbles x movesPerBubble`, ramped up inside a
 *    chapter. More bubbles per move means the player needs bigger pops.
 *  * **target** - a fraction of the board's theoretical value. The ratio is
 *    clamped to `0.60 .. 1.00` of `9 points per destructible bubble`, which is
 *    deliberately below the ~15 points per bubble a competent player averages
 *    (pop + combo + drop bonuses), so no level can be mathematically impossible.
 *  * **drops** - a new row is pushed in every N shots, squeezing the player
 *    towards the danger line.
 *  * **colours** - 3 colours at the start, 6 in the final chapter.
 *  * **specials & obstacles** - introduced one at a time (see [specialUnlockLevel]).
 */
object LevelCatalog {

    const val LEVEL_COUNT = 120

    /** Every level of a chapter is played on one of these layouts, in order. */
    data class Chapter(
        val index: Int,
        val name: String,
        val from: Int,
        val to: Int,
        val colorCount: Int,
        val patterns: List<String>,
        val bossPattern: String,
        val movesPerBubble: Float,
        val movesRamp: Float,
        val targetRatio: Float,
        val targetRamp: Float,
        val dropEvery: Int,
        val dropRowCount: Int,
        val dangerExtraRows: Int,
        val specialChanceBase: Float
    ) {
        fun contains(level: Int): Boolean = level in from..to
    }

    val chapters: List<Chapter> = listOf(
        Chapter(
            index = 0,
            name = "Sunny Start",
            from = 1, to = 20,
            colorCount = 3,
            patterns = listOf(
                LayoutPatterns.FULL_THREE,
                LayoutPatterns.THIN_WALL,
                LayoutPatterns.PYRAMID,
                LayoutPatterns.WALL
            ),
            bossPattern = LayoutPatterns.PYRAMID,
            movesPerBubble = 0.34f,
            movesRamp = 0.05f,
            targetRatio = 0.70f,
            targetRamp = 0.10f,
            dropEvery = 0,
            dropRowCount = 1,
            dangerExtraRows = 0,
            specialChanceBase = 0.012f
        ),
        Chapter(
            index = 1,
            name = "Colour Rush",
            from = 21, to = 40,
            colorCount = 4,
            patterns = listOf(
                LayoutPatterns.STRIPES,
                LayoutPatterns.CHECKER,
                LayoutPatterns.DIAMOND,
                LayoutPatterns.ISLANDS,
                LayoutPatterns.SCATTER
            ),
            bossPattern = LayoutPatterns.DIAMOND,
            movesPerBubble = 0.33f,
            movesRamp = 0.06f,
            targetRatio = 0.74f,
            targetRamp = 0.10f,
            dropEvery = 15,
            dropRowCount = 1,
            dangerExtraRows = 0,
            specialChanceBase = 0.02f
        ),
        Chapter(
            index = 2,
            name = "Cold Front",
            from = 41, to = 60,
            colorCount = 4,
            patterns = listOf(
                LayoutPatterns.ARCH,
                LayoutPatterns.COLUMNS,
                LayoutPatterns.ICEBOX,
                LayoutPatterns.TWIN_TOWERS
            ),
            bossPattern = LayoutPatterns.ICEBOX,
            movesPerBubble = 0.32f,
            movesRamp = 0.07f,
            targetRatio = 0.78f,
            targetRamp = 0.09f,
            dropEvery = 13,
            dropRowCount = 1,
            dangerExtraRows = 0,
            specialChanceBase = 0.025f
        ),
        Chapter(
            index = 3,
            name = "The Fortress",
            from = 61, to = 80,
            colorCount = 5,
            patterns = listOf(
                LayoutPatterns.FORTRESS,
                LayoutPatterns.VAULT,
                LayoutPatterns.ZIPPER,
                LayoutPatterns.DEEP_CAVE
            ),
            bossPattern = LayoutPatterns.FORTRESS,
            movesPerBubble = 0.31f,
            movesRamp = 0.07f,
            targetRatio = 0.82f,
            targetRamp = 0.08f,
            dropEvery = 11,
            dropRowCount = 1,
            dangerExtraRows = 1,
            specialChanceBase = 0.03f
        ),
        Chapter(
            index = 4,
            name = "Night Shift",
            from = 81, to = 100,
            colorCount = 5,
            patterns = listOf(
                LayoutPatterns.RAILS,
                LayoutPatterns.DEEP_CAVE,
                LayoutPatterns.VAULT,
                LayoutPatterns.ZIPPER
            ),
            bossPattern = LayoutPatterns.RAILS,
            movesPerBubble = 0.30f,
            movesRamp = 0.07f,
            targetRatio = 0.86f,
            targetRamp = 0.07f,
            dropEvery = 10,
            dropRowCount = 1,
            dangerExtraRows = 1,
            specialChanceBase = 0.035f
        ),
        Chapter(
            index = 5,
            name = "Blast Master",
            from = 101, to = LEVEL_COUNT,
            colorCount = 6,
            patterns = listOf(
                LayoutPatterns.GRAND_WALL,
                LayoutPatterns.SPECIAL_LAB,
                LayoutPatterns.COLUMNS,
                LayoutPatterns.FORTRESS
            ),
            bossPattern = LayoutPatterns.GRAND_WALL,
            movesPerBubble = 0.30f,
            movesRamp = 0.06f,
            targetRatio = 0.88f,
            targetRamp = 0.08f,
            dropEvery = 8,
            dropRowCount = 2,
            dangerExtraRows = 2,
            specialChanceBase = 0.04f
        )
    )

    private val cache = HashMap<Int, LevelDefinition>(LEVEL_COUNT)
    private val generatedCache = HashMap<Int, LevelGenerator.Generated>(LEVEL_COUNT)

    /** Level number clamped into the playable range. */
    fun clampLevel(level: Int): Int = level.coerceIn(1, LEVEL_COUNT)

    fun chapterFor(level: Int): Chapter {
        val clamped = clampLevel(level)
        return chapters.firstOrNull { it.contains(clamped) } ?: chapters.last()
    }

    /** Display name shown on the level select screen and in the HUD. */
    fun chapterName(level: Int): String = chapterFor(level).name

    /** The generated starting board for [level]. Cached - the result is immutable in use. */
    fun board(level: Int): LevelGenerator.Generated {
        val clamped = clampLevel(level)
        return generatedCache.getOrPut(clamped) { LevelGenerator.build(get(clamped)) }
    }

    /** Full definition of a level. Cached. */
    fun get(level: Int): LevelDefinition {
        val clamped = clampLevel(level)
        return cache.getOrPut(clamped) { create(clamped) }
    }

    /** Level suggested by the Play button: the first level the player has not cleared. */
    fun nextPlayableLevel(lastUnlocked: Int, starsByLevel: Map<Int, Int>): Int {
        val start = clampLevel(lastUnlocked)
        for (level in start..LEVEL_COUNT) {
            if ((starsByLevel[level] ?: 0) == 0) return level
        }
        return LEVEL_COUNT
    }

    val totalStars: Int get() = LEVEL_COUNT * 3

    // ------------------------------------------------------------------
    // Generation
    // ------------------------------------------------------------------

    /** Level at which each special starts appearing on the board. */
    private fun specialUnlockLevel(kind: BubbleKind): Int = when (kind) {
        BubbleKind.BOMB -> 4
        BubbleKind.RAINBOW -> 9
        BubbleKind.LIGHTNING -> 24
        BubbleKind.FIRE -> 44
        else -> 1
    }

    private fun create(level: Int): LevelDefinition {
        val chapter = chapterFor(level)
        val position = if (chapter.to > chapter.from) {
            (level - chapter.from).toFloat() / (chapter.to - chapter.from).toFloat()
        } else {
            0f
        }
        val isBoss = level % 20 == 0 || level == LEVEL_COUNT
        val patternIndex = (level - chapter.from) % chapter.patterns.size
        val pattern = if (isBoss) chapter.bossPattern else chapter.patterns[patternIndex]

        val colors = pickColors(chapter.colorCount, level)
        val allowedSpecials = BubbleKind.SHOOTABLE
            .filter { it.isSpecial && level >= specialUnlockLevel(it) }

        // First pass: how many bubbles actually end up on this board?
        val preview = LevelGenerator.build(
            LevelDefinition(
                level = level,
                chapterName = chapter.name,
                chapter = chapter.index,
                pattern = pattern,
                colors = colors,
                seed = seedFor(level),
                targetScore = 0,
                moves = 0,
                shotsBeforeDrop = 0,
                dropRowCount = chapter.dropRowCount,
                dangerExtraRows = chapter.dangerExtraRows,
                specialChance = specialChanceFor(chapter, level),
                allowedSpecials = allowedSpecials,
                extraIceLayers = if (chapter.index >= 4) 1 else 0,
                isBossLevel = isBoss
            )
        )

        val destructible = preview.destructibleCount.coerceAtLeast(6)
        val moves = Math.ceil((destructible * (chapter.movesPerBubble + position * chapter.movesRamp)).toDouble())
            .toInt()
            .coerceIn(12, 90)
        val ratio = (chapter.targetRatio + position * chapter.targetRamp).coerceIn(0.60f, 1.00f)
        val target = ((destructible * POINTS_PER_BUBBLE_ESTIMATE * ratio) / 10f).toInt() * 10

        return LevelDefinition(
            level = level,
            chapterName = chapter.name,
            chapter = chapter.index,
            pattern = pattern,
            colors = colors,
            seed = seedFor(level),
            targetScore = target.coerceAtLeast(120),
            moves = moves,
            shotsBeforeDrop = chapter.dropEvery,
            dropRowCount = chapter.dropRowCount,
            dangerExtraRows = chapter.dangerExtraRows,
            specialChance = specialChanceFor(chapter, level),
            allowedSpecials = allowedSpecials,
            extraIceLayers = if (chapter.index >= 4) 1 else 0,
            maxRowsVisible = 10,
            isBossLevel = isBoss
        )
    }

    private fun specialChanceFor(chapter: Chapter, level: Int): Float {
        val ramp = (level - chapter.from).toFloat() * 0.0015f
        return (chapter.specialChanceBase + ramp).coerceAtMost(0.09f)
    }

    /** Rotating colour subsets keep the early chapters from always looking identical. */
    private fun pickColors(count: Int, level: Int): List<BubbleColor> {
        val palette = BubbleColor.PLAYABLE
        val start = (level * 3) % palette.size
        return (0 until count.coerceIn(2, palette.size)).map { palette[(start + it) % palette.size] }
            .distinct()
    }

    private fun seedFor(level: Int): Long = level * 7919L + 1_000_003L

    /**
     * Conservative per-bubble value used to size a level's target score.
     * Popping a bubble is worth at least 10 points before combo and drop bonuses.
     */
    private const val POINTS_PER_BUBBLE_ESTIMATE = 9f
}
