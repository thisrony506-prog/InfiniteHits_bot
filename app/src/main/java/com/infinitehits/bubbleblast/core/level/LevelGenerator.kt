package com.infinitehits.bubbleblast.core.level

import com.infinitehits.bubbleblast.core.model.Bubble
import com.infinitehits.bubbleblast.core.model.BubbleColor
import com.infinitehits.bubbleblast.core.model.BubbleKind
import com.infinitehits.bubbleblast.core.model.BubbleState
import com.infinitehits.bubbleblast.core.model.Hex
import com.infinitehits.bubbleblast.core.util.Rng

/**
 * Turns a [LevelDefinition] into the starting bubbles of a board.
 *
 * The generator guarantees three things that matter for playability:
 *
 *  1. every colour that is present appears at least three times, so it can
 *     actually be matched (colours that end up with one or two bubbles are
 *     folded into the most common colour);
 *  2. every colour gets one seeded cluster of three connected bubbles, so the
 *     board always offers a first move;
 *  3. the layout, colours and specials are pure functions of the definition's
 *     seed, so a level is identical on every device and every replay.
 */
object LevelGenerator {

    /** Result of building a board. */
    data class Generated(
        val bubbles: List<Bubble>,
        val slidingRows: Set<Int>,
        val destructibleCount: Int,
        val totalCount: Int,
        val colorCounts: Map<BubbleColor, Int>
    )

    fun build(definition: LevelDefinition): Generated {
        val rows = LayoutPatterns.rows(definition.pattern)
        val rng = Rng(definition.seed)
        val palette = definition.colors.ifEmpty { BubbleColor.PLAYABLE.take(3) }

        // ---- 1. Normalise the ASCII art into a grid of symbols -------------
        val symbols = Array(rows.size) { row ->
            val columns = Hex.columnCount(row)
            val line = rows[row]
            CharArray(columns) { col -> if (col < line.length) line[col] else EMPTY }
        }

        // ---- 2. Colour every plain bubble ---------------------------------
        val colors = Array(rows.size) { row -> arrayOfNulls<BubbleColor>(symbols[row].size) }
        seedClusters(symbols, colors, palette, rng)
        fillRemainingColors(symbols, colors, palette, rng)
        removeLonelyColors(symbols, colors, palette, rng)

        // ---- 3. Build bubbles & apply obstacle / special symbols -----------
        val specialWeights = specialWeights(definition)
        val maxSpecials = 1 + symbols.sumOf { line -> line.count { it == PLAIN } } / 12
        var specialsPlaced = 0
        val bubbles = ArrayList<Bubble>(96)
        val slidingRows = LinkedHashSet<Int>()
        val colorCounts = LinkedHashMap<BubbleColor, Int>()

        for (row in symbols.indices) {
            for (col in symbols[row].indices) {
                val symbol = symbols[row][col]
                if (symbol == EMPTY) continue

                // A row that contains at least one moving bubble slides as a whole:
                // the physics of the row stays coherent when it drifts.
                if (symbol == MOVING) slidingRows.add(row)

                var color = colors[row][col] ?: palette[rng.nextInt(palette.size)]
                var kind = BubbleKind.NORMAL
                var iceLayers = 0

                when (symbol) {
                    STONE -> { kind = BubbleKind.STONE; color = BubbleColor.GRAY }
                    WALL -> { kind = BubbleKind.UNBREAKABLE; color = BubbleColor.GRAY }
                    LOCKED -> kind = BubbleKind.LOCKED
                    ICE -> {
                        kind = BubbleKind.ICE
                        iceLayers = 1 + definition.extraIceLayers
                    }
                    MOVING -> { kind = BubbleKind.MOVING; slidingRows.add(row) }
                    BOMB -> kind = BubbleKind.BOMB
                    RAINBOW -> kind = BubbleKind.RAINBOW
                    LIGHTNING -> kind = BubbleKind.LIGHTNING
                    FIRE -> kind = BubbleKind.FIRE
                    else -> {
                        // Plain bubble: maybe upgrade it to a special.
                        if (specialsPlaced < maxSpecials && rng.nextFloat() < definition.specialChance) {
                            val picked = rng.weighted(specialWeights)
                            if (picked != null) {
                                kind = picked
                                specialsPlaced++
                            }
                        }
                    }
                }

                if (kind.matchesByColor || kind == BubbleKind.LOCKED || kind == BubbleKind.ICE ||
                    kind == BubbleKind.MOVING || kind.isSpecial
                ) {
                    colorCounts[color] = (colorCounts[color] ?: 0) + 1
                }

                bubbles.add(
                    Bubble(
                        row = row,
                        col = col,
                        color = color,
                        kind = kind,
                        state = BubbleState.IDLE,
                        iceLayers = iceLayers
                    )
                )
            }
        }

        val destructible = bubbles.count { it.kind.blocksBoardClear }
        return Generated(
            bubbles = bubbles,
            slidingRows = slidingRows,
            destructibleCount = destructible,
            totalCount = bubbles.size,
            colorCounts = colorCounts
        )
    }

    // ------------------------------------------------------------------
    // Colour assignment
    // ------------------------------------------------------------------

    /**
     * Places one connected trio per colour so the player always has a match
     * available at the start of the level.
     */
    private fun seedClusters(
        symbols: Array<CharArray>,
        colors: Array<Array<BubbleColor?>>,
        palette: List<BubbleColor>,
        rng: Rng
    ) {
        val plainCells = ArrayList<Int>(128)
        for (row in symbols.indices) {
            for (col in symbols[row].indices) {
                if (symbols[row][col] == PLAIN) plainCells.add(row shl 6 or col)
            }
        }
        if (plainCells.isEmpty()) return
        rng.shuffle(plainCells)

        var cursor = 0
        for (color in palette) {
            // Find the next free cell that has at least two free neighbours.
            while (cursor < plainCells.size) {
                val packed = plainCells[cursor++]
                val row = packed shr 6
                val col = packed and 0x3F
                if (colors[row][col] != null) continue
                val freeNeighbours = Hex.neighborsOf(row, col).filter { cell ->
                    cell.row in symbols.indices &&
                        cell.col < symbols[cell.row].size &&
                        symbols[cell.row][cell.col] == PLAIN &&
                        colors[cell.row][cell.col] == null
                }
                if (freeNeighbours.size < 2) continue
                colors[row][col] = color
                var assigned = 1
                for (cell in freeNeighbours) {
                    if (assigned >= 3) break
                    colors[cell.row][cell.col] = color
                    assigned++
                }
                break
            }
        }
    }

    /** Grows the seeded clusters outwards: bubbles prefer a neighbour's colour. */
    private fun fillRemainingColors(
        symbols: Array<CharArray>,
        colors: Array<Array<BubbleColor?>>,
        palette: List<BubbleColor>,
        rng: Rng
    ) {
        for (row in symbols.indices) {
            for (col in symbols[row].indices) {
                if (symbols[row][col] != PLAIN || colors[row][col] != null) continue

                val neighbourColors = ArrayList<BubbleColor>(6)
                for (cell in Hex.neighborsOf(row, col)) {
                    if (cell.row !in symbols.indices) continue
                    if (cell.col >= symbols[cell.row].size) continue
                    colors[cell.row][cell.col]?.let { neighbourColors.add(it) }
                }

                colors[row][col] = when {
                    neighbourColors.isNotEmpty() && rng.nextFloat() < CLUSTER_BIAS ->
                        neighbourColors[rng.nextInt(neighbourColors.size)]
                    else -> palette[rng.nextInt(palette.size)]
                }
            }
        }
    }

    /**
     * A colour with one or two bubbles can never be popped, so those bubbles are
     * recoloured into the dominant colour instead of leaving dead weight on the
     * board.
     */
    private fun removeLonelyColors(
        symbols: Array<CharArray>,
        colors: Array<Array<BubbleColor?>>,
        palette: List<BubbleColor>,
        rng: Rng
    ) {
        val counts = HashMap<BubbleColor, Int>()
        for (row in symbols.indices) {
            for (col in symbols[row].indices) {
                colors[row][col]?.let { counts[it] = (counts[it] ?: 0) + 1 }
            }
        }
        val lonely = counts.filterValues { it in 1 until MIN_MATCH }.keys
        if (lonely.isEmpty()) return

        val fallback = counts.entries
            .filter { it.key !in lonely }
            .maxByOrNull { it.value }?.key
            ?: palette[rng.nextInt(palette.size)]

        for (row in symbols.indices) {
            for (col in symbols[row].indices) {
                val color = colors[row][col] ?: continue
                if (color in lonely) colors[row][col] = fallback
            }
        }
    }

    /** Weights for the specials a level may sprinkle onto its board. */
    private fun specialWeights(definition: LevelDefinition): Map<BubbleKind, Int> {
        val weights = LinkedHashMap<BubbleKind, Int>()
        for (kind in definition.allowedSpecials) {
            when (kind) {
                BubbleKind.BOMB -> weights[kind] = 3
                BubbleKind.RAINBOW -> weights[kind] = 3
                BubbleKind.LIGHTNING -> weights[kind] = 2
                BubbleKind.FIRE -> weights[kind] = 2
                else -> Unit
            }
        }
        return weights
    }

    // ------------------------------------------------------------------
    // ASCII legend
    // ------------------------------------------------------------------
    const val EMPTY = '.'
    const val PLAIN = '#'
    const val STONE = 'S'
    const val WALL = 'X'
    const val LOCKED = 'L'
    const val ICE = 'I'
    const val MOVING = 'M'
    const val BOMB = 'B'
    const val RAINBOW = 'R'
    const val LIGHTNING = 'T'
    const val FIRE = 'F'

    private const val CLUSTER_BIAS = 0.62f
    private const val MIN_MATCH = 3
}
