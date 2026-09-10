package com.infinitehits.bubbleblast.core

import com.infinitehits.bubbleblast.core.level.LevelCatalog
import com.infinitehits.bubbleblast.core.level.LevelDefinition
import com.infinitehits.bubbleblast.core.level.LevelGenerator
import com.infinitehits.bubbleblast.core.model.BubbleColor
import com.infinitehits.bubbleblast.core.model.Hex
import com.infinitehits.bubbleblast.core.score.StarRating
import com.infinitehits.bubbleblast.core.util.Rng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the parts of the engine that are pure Kotlin: the deterministic
 * RNG, the hex grid maths, the 120 level definitions and the generator that turns
 * a definition into a board.
 *
 * These run with `./gradlew test` - no device or emulator needed.
 */
class EngineCoreTest {

    // ------------------------------------------------------------------
    // Rng
    // ------------------------------------------------------------------

    @Test
    fun `rng is deterministic for a given seed`() {
        val first = Rng(1234L)
        val second = Rng(1234L)
        repeat(64) {
            assertEquals(first.nextLong(), second.nextLong())
        }
    }

    @Test
    fun `rng stays inside its bounds`() {
        val rng = Rng(99L)
        repeat(500) {
            val value = rng.nextInt(6)
            assertTrue("nextInt(6) returned $value", value in 0..5)
            val ranged = rng.nextInt(3, 9)
            assertTrue("nextInt(3, 9) returned $ranged", ranged in 3..8)
            val fraction = rng.nextFloat()
            assertTrue("nextFloat returned $fraction", fraction >= 0f && fraction < 1f)
        }
    }

    @Test
    fun `shuffle keeps every element`() {
        val rng = Rng(7L)
        val items = MutableList(20) { it }
        rng.shuffle(items)
        assertEquals(20, items.size)
        assertEquals((0 until 20).toList(), items.sorted())
    }

    @Test
    fun `weighted pick ignores zero weights`() {
        val rng = Rng(3L)
        assertNull(rng.weighted(mapOf("a" to 0, "b" to 0)))
        val picked = rng.weighted(mapOf("a" to 0, "b" to 5))
        assertEquals("b", picked)
    }

    // ------------------------------------------------------------------
    // Hex grid
    // ------------------------------------------------------------------

    @Test
    fun `hex grid alternates column counts`() {
        assertEquals(11, Hex.columnCount(0))
        assertEquals(10, Hex.columnCount(1))
        assertEquals(11, Hex.columnCount(2))
        assertTrue(Hex.isValid(0, 10))
        assertFalse(Hex.isValid(0, 11))
        assertFalse(Hex.isValid(1, 10))
        assertTrue(Hex.isValid(1, 9))
    }

    @Test
    fun `interior cell has six neighbours`() {
        assertEquals(6, Hex.neighborsOf(2, 5).size)
        assertEquals(6, Hex.neighborsOf(3, 5).size)
        // Corners have fewer: col 0 of an even row touches 4 cells.
        assertTrue(Hex.neighborsOf(0, 0).size < 6)
    }

    // ------------------------------------------------------------------
    // Colours
    // ------------------------------------------------------------------

    @Test
    fun `the six playable colours are present`() {
        assertEquals(6, BubbleColor.PLAYABLE.size)
        assertEquals(6, BubbleColor.COUNT)
        val names = BubbleColor.PLAYABLE.map { it.name }.toSet()
        assertEquals(setOf("RED", "BLUE", "GREEN", "YELLOW", "PURPLE", "ORANGE"), names)
        assertEquals(BubbleColor.GRAY, BubbleColor.entries.first { !it.playable })
    }

    // ------------------------------------------------------------------
    // Level catalog
    // ------------------------------------------------------------------

    @Test
    fun `catalog has 120 playable levels`() {
        assertEquals(120, LevelCatalog.LEVEL_COUNT)
        assertEquals(360, LevelCatalog.totalStars)
    }

    @Test
    fun `every level definition is sane`() {
        val seeds = HashSet<Long>()
        for (level in 1..LevelCatalog.LEVEL_COUNT) {
            val definition = LevelCatalog.get(level)
            val label = "level $level"

            assertEquals(label, level, definition.level)
            assertTrue("$label chapter name", definition.chapterName.isNotBlank())
            assertTrue("$label moves ${definition.moves}", definition.moves in 12..90)
            assertTrue("$label target ${definition.targetScore}", definition.targetScore > 0)
            assertTrue("$label colours", definition.colors.size in 3..6)
            assertTrue("$label two stars", definition.twoStarScore >= definition.targetScore)
            assertTrue("$label three stars", definition.threeStarScore >= definition.twoStarScore)
            assertTrue("$label visibility", definition.maxRowsVisible in 3..14)
            assertTrue("$label drop rows", definition.dropRowCount in 0..3)
            assertTrue("$label special chance", definition.specialChance in 0f..0.5f)
            assertTrue("$label seed", seeds.add(definition.seed))
        }
    }

    @Test
    fun `difficulty grows across the chapters`() {
        val early = LevelCatalog.get(3)
        val late = LevelCatalog.get(LevelCatalog.LEVEL_COUNT)
        assertTrue(
            "the last level should use at least as many colours as an early one",
            late.colors.size >= early.colors.size
        )
        assertTrue(
            "the last level should ask for more points than the third one",
            late.targetScore > early.targetScore
        )
        assertTrue("early levels stay small", early.colors.size <= 4)
    }

    @Test
    fun `requesting a level outside the range is clamped`() {
        assertEquals(1, LevelCatalog.get(0).level)
        assertEquals(1, LevelCatalog.get(-25).level)
        assertEquals(LevelCatalog.LEVEL_COUNT, LevelCatalog.get(9999).level)
    }

    // ------------------------------------------------------------------
    // Level generation
    // ------------------------------------------------------------------

    @Test
    fun `generated boards keep every colour matchable`() {
        for (level in intArrayOf(1, 11, 24, 44, 60, 90, 120)) {
            val definition = LevelCatalog.get(level)
            val board = LevelGenerator.build(definition)
            val label = "level $level"

            assertTrue("$label has bubbles", board.bubbles.isNotEmpty())
            assertTrue("$label has destructible bubbles", board.destructibleCount > 0)
            assertTrue("$label total count", board.totalCount >= board.destructibleCount)

            for ((color, count) in board.colorCounts) {
                assertTrue("$label colour $color appears $count times (needs 3)", count >= 3)
            }
            for (bubble in board.bubbles) {
                assertTrue(
                    "$label placed a bubble outside the grid at (${bubble.row}, ${bubble.col})",
                    Hex.isValid(bubble.row, bubble.col)
                )
            }
            for (row in board.slidingRows) {
                assertTrue("$label slides a row it does not use: $row", row >= 0)
            }
        }
    }

    @Test
    fun `generation is deterministic`() {
        val definition = LevelCatalog.get(37)
        val first = LevelGenerator.build(definition)
        val second = LevelGenerator.build(definition)
        assertEquals(first.bubbles.size, second.bubbles.size)
        assertEquals(first.destructibleCount, second.destructibleCount)
        assertEquals(first.slidingRows, second.slidingRows)
        for (index in first.bubbles.indices) {
            val a = first.bubbles[index]
            val b = second.bubbles[index]
            assertEquals("row of bubble $index", a.row.toLong(), b.row.toLong())
            assertEquals("col of bubble $index", a.col.toLong(), b.col.toLong())
            assertEquals("colour of bubble $index", a.color, b.color)
            assertEquals("kind of bubble $index", a.kind, b.kind)
        }
        assertEquals(first.colorCounts, second.colorCounts)
    }

    @Test
    fun `specials only appear once they are unlocked`() {
        val early = LevelGenerator.build(LevelCatalog.get(1))
        assertTrue(
            "level 1 must not contain specials",
            early.bubbles.none { it.kind.isSpecial }
        )
        assertNotNull(LevelGenerator.build(LevelCatalog.get(LevelCatalog.LEVEL_COUNT)))
    }

    // ------------------------------------------------------------------
    // Stars and coins
    // ------------------------------------------------------------------

    @Test
    fun `stars follow the score thresholds`() {
        val definition: LevelDefinition = LevelCatalog.get(12)
        assertEquals(0, StarRating.starsFor(definition, 0, false))
        assertEquals(1, StarRating.starsFor(definition, definition.targetScore, false))
        assertEquals(2, StarRating.starsFor(definition, definition.twoStarScore, false))
        assertEquals(3, StarRating.starsFor(definition, definition.threeStarScore, false))
        assertEquals(3, StarRating.starsFor(definition, 0, true))
        assertTrue(StarRating.starsFor(definition, definition.targetScore - 1, false) <= 1)
    }

    @Test
    fun `coins are capped and never negative`() {
        for (stars in 0..3) {
            for (score in intArrayOf(0, 1_000, 50_000)) {
                val coins = StarRating.coinsFor(stars, score, firstClear = true)
                assertTrue("stars=$stars score=$score gave $coins", coins in 0..140)
            }
        }
        assertTrue(StarRating.coinsFor(1, 2_000, firstClear = true) > 0)
        assertEquals(0, StarRating.coinsFor(0, 0, firstClear = false))
    }
}
