package com.infinitehits.bubbleblast.core.model

/**
 * Hex-grid maths for the bubble board.
 *
 * The board uses "offset coordinates" with a horizontal offset layout:
 *
 *  * even rows hold [COLS_EVEN] bubbles and start at x = 0
 *  * odd rows hold [COLS_ODD] bubbles and are shifted right by half a bubble
 *
 * Because an odd row is shifted by half a bubble *and* holds one bubble fewer,
 * both row shapes cover exactly the same board width - the playing field has
 * perfectly straight walls even though it is a hex grid.
 *
 * Rows are indexed downward and may be negative: pushing a new row onto the top
 * of the board simply decrements the top row index instead of moving bubbles.
 */
object Hex {

    const val COLS_EVEN = 11
    const val COLS_ODD = 10

    /** Vertical distance between rows, in bubble diameters: sqrt(3)/2. */
    const val ROW_HEIGHT_FACTOR = 0.8660254f

    fun isEvenRow(row: Int): Boolean = row % 2 == 0

    /** Number of playable columns in [row]. */
    fun columnCount(row: Int): Int = if (isEvenRow(row)) COLS_EVEN else COLS_ODD

    fun isValid(row: Int, col: Int): Boolean = col >= 0 && col < columnCount(row)

    /** Horizontal shift of a row, in bubble diameters. */
    fun rowOffsetUnits(row: Int): Float = if (isEvenRow(row)) 0f else 0.5f

    /**
     * Column pairs of the two upper neighbours of (row, col). The offsets depend
     * on row parity because odd rows are shifted right.
     *
     * Even row (no shift):  up = (col - 1), (col)
     * Odd row (shifted):    up = (col),     (col + 1)
     */
    fun upperNeighborColumns(row: Int, col: Int): Pair<Int, Int> =
        if (isEvenRow(row)) Pair(col - 1, col) else Pair(col, col + 1)

    /** Column pairs of the two lower neighbours - mirrored version of [upperNeighborColumns]. */
    fun lowerNeighborColumns(row: Int, col: Int): Pair<Int, Int> = upperNeighborColumns(row, col)

    /**
     * Fills [out] with the (row, col) pairs of every valid neighbour of the cell.
     * Returns the number of neighbours written (2 to 6).
     */
    fun neighborsOf(row: Int, col: Int, out: IntArray): Int {
        var n = 0
        // Same row
        if (isValid(row, col - 1)) { out[n++] = row; out[n++] = col - 1 }
        if (isValid(row, col + 1)) { out[n++] = row; out[n++] = col + 1 }

        val upper = upperNeighborColumns(row, col)
        if (isValid(row - 1, upper.first)) { out[n++] = row - 1; out[n++] = upper.first }
        if (isValid(row - 1, upper.second)) { out[n++] = row - 1; out[n++] = upper.second }

        val lower = lowerNeighborColumns(row, col)
        if (isValid(row + 1, lower.first)) { out[n++] = row + 1; out[n++] = lower.first }
        if (isValid(row + 1, lower.second)) { out[n++] = row + 1; out[n++] = lower.second }

        return n / 2
    }

    /** Convenience wrapper returning a freshly allocated list. */
    fun neighborsOf(row: Int, col: Int): List<Cell> {
        val buffer = IntArray(12)
        val count = neighborsOf(row, col, buffer)
        val result = ArrayList<Cell>(count)
        for (i in 0 until count) {
            result.add(Cell(buffer[i * 2], buffer[i * 2 + 1]))
        }
        return result
    }
}

/** Immutable grid coordinate. */
data class Cell(val row: Int, val col: Int) {
    override fun toString(): String = "($row,$col)"
}
