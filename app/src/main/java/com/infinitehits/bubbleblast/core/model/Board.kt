package com.infinitehits.bubbleblast.core.model

import kotlin.math.abs
import kotlin.math.sin

/**
 * The bubble grid.
 *
 * Bubbles live in a sparse map of `row -> array of columns`, which lets the
 * board grow upwards for free: pushing a row onto the ceiling only decrements
 * [topRow]. Everything else - screen positions, neighbours, flood fills - is
 * derived from that index.
 *
 * Hot paths (matching, floating detection) deliberately avoid allocating
 * objects per cell: neighbouring cells are walked through a small int buffer and
 * visited cells are tracked with an `Int` key instead of a data class.
 */
class Board(val geometry: BoardGeometry) {

    private val rows = HashMap<Int, Array<Bubble?>>()

    /** Row index currently attached to the ceiling. Inserted rows decrement it. */
    var topRow: Int = 0
        private set

    /** Rows whose bubbles slide left and right (see [BubbleKind.MOVING]). */
    private val slidingRows = HashSet<Int>()

    /** Phase of the sliding animation in radians. */
    private var slidePhase = 0f

    /** Horizontal travel of a sliding row, in bubble diameters. */
    var slideAmplitudeUnits: Float = 0.24f

    /** Animated vertical offset applied to every bubble while a row drops in. */
    private var descentOffset: Float = 0f
    private var descentTarget: Float = 0f

    // Scratch buffers reused by the flood fills so a pop does not allocate.
    private val neighborBuffer = IntArray(12)
    private val visited = HashSet<Int>(256)
    private val queue = ArrayDeque<Bubble>()
    private val collectBuffer = ArrayList<Bubble>(64)

    // ------------------------------------------------------------------
    // Layout & animation
    // ------------------------------------------------------------------

    /** Advances board animations. [dt] is in seconds. */
    fun update(dt: Float) {
        if (slidingRows.isNotEmpty()) {
            slidePhase += dt * SLIDE_SPEED
            if (slidePhase > TWO_PI) slidePhase -= TWO_PI
        }
        if (descentOffset != descentTarget) {
            val step = geometry.rowHeight / DESCENT_DURATION * dt
            descentOffset = if (abs(descentTarget - descentOffset) <= step) {
                descentTarget
            } else {
                descentOffset + step * if (descentTarget > descentOffset) 1f else -1f
            }
        }
    }

    /** Horizontal slide offset of [row] in pixels. */
    fun slideOffset(row: Int): Float =
        if (row in slidingRows) sin(slidePhase) * slideAmplitudeUnits * geometry.diameter else 0f

    /** True while a freshly inserted row is still sliding into place. */
    val descending: Boolean get() = descentOffset != descentTarget

    /** Centre of a cell in view pixels, including slide and descent animation. */
    fun centerX(row: Int, col: Int): Float = geometry.cellCenterX(row, col) + slideOffset(row)

    fun centerY(row: Int): Float = geometry.cellCenterY(row, topRow) + descentOffset

    fun markRowSliding(row: Int) {
        slidingRows.add(row)
    }

    fun isRowSliding(row: Int): Boolean = row in slidingRows

    // ------------------------------------------------------------------
    // Access
    // ------------------------------------------------------------------

    private fun rowArray(row: Int, create: Boolean): Array<Bubble?>? {
        var array = rows[row]
        if (array == null && create) {
            array = arrayOfNulls(Hex.columnCount(row))
            rows[row] = array
        }
        return array
    }

    fun bubbleAt(row: Int, col: Int): Bubble? {
        if (!Hex.isValid(row, col)) return null
        return rows[row]?.get(col)
    }

    fun bubbleAt(cell: Cell): Bubble? = bubbleAt(cell.row, cell.col)

    fun isEmptyCell(row: Int, col: Int): Boolean =
        Hex.isValid(row, col) && bubbleAt(row, col) == null

    fun isEmptyCell(cell: Cell): Boolean = isEmptyCell(cell.row, cell.col)

    fun place(bubble: Bubble) {
        val array = rowArray(bubble.row, create = true) ?: return
        if (bubble.col < 0 || bubble.col >= array.size) return
        array[bubble.col] = bubble
    }

    fun removeAt(row: Int, col: Int): Bubble? {
        if (!Hex.isValid(row, col)) return null
        val array = rows[row] ?: return null
        val removed = array[col]
        array[col] = null
        return removed
    }

    fun remove(bubble: Bubble) {
        if (bubbleAt(bubble.row, bubble.col) === bubble) removeAt(bubble.row, bubble.col)
    }

    fun clear() {
        rows.clear()
        slidingRows.clear()
        topRow = 0
        slidePhase = 0f
        descentOffset = 0f
        descentTarget = 0f
    }

    /**
     * Runs [action] for every bubble currently on the board.
     *
     * `inline` on purpose: this is called every frame from the engine and the
     * renderer, and a capturing lambda would allocate once per frame.
     */
    inline fun forEachBubble(action: (Bubble) -> Unit) {
        for (array in rows.values) {
            for (i in array.indices) {
                val bubble = array[i] ?: continue
                action(bubble)
            }
        }
    }

    fun allBubbles(): List<Bubble> {
        val result = ArrayList<Bubble>(64)
        forEachBubble { result.add(it) }
        return result
    }

    fun countBubbles(): Int {
        var count = 0
        forEachBubble { count++ }
        return count
    }

    /** Bubbles the player can still destroy - stone and unbreakable walls excluded. */
    fun countDestructible(): Int {
        var count = 0
        forEachBubble { if (it.blocksBoardClear) count++ }
        return count
    }

    fun hasBubbles(): Boolean {
        var found = false
        forEachBubble { found = true }
        return found
    }

    /** Colours still on the board, so the launcher never hands out a dead colour. */
    fun activeColors(): List<BubbleColor> {
        val found = LinkedHashSet<BubbleColor>()
        forEachBubble { bubble ->
            if (bubble.color.playable && bubble.blocksBoardClear) found.add(bubble.color)
        }
        return found.toList()
    }

    /** Lowest visible edge of any bubble, or null when the board is empty. */
    fun lowestEdgeY(): Float? {
        var lowest = Float.NEGATIVE_INFINITY
        forEachBubble { bubble ->
            val y = centerY(bubble.row) + geometry.bubbleRadius
            if (y > lowest) lowest = y
        }
        return if (lowest == Float.NEGATIVE_INFINITY) null else lowest
    }

    /** True when at least one bubble has reached the danger line. */
    fun crossedDangerLine(): Boolean {
        val lowest = lowestEdgeY() ?: return false
        return lowest >= geometry.dangerY
    }

    /** Deepest row index that still holds a bubble. */
    fun bottomRow(): Int {
        var bottom = topRow
        forEachBubble { if (it.row > bottom) bottom = it.row }
        return bottom
    }

    // ------------------------------------------------------------------
    // Neighbours & matching
    // ------------------------------------------------------------------

    fun neighborsOf(row: Int, col: Int): List<Cell> = Hex.neighborsOf(row, col)

    /** Bubbles directly touching [bubble]. */
    fun adjacentBubbles(bubble: Bubble): List<Bubble> {
        val result = ArrayList<Bubble>(6)
        val count = Hex.neighborsOf(bubble.row, bubble.col, neighborBuffer)
        for (i in 0 until count) {
            bubbleAt(neighborBuffer[i * 2], neighborBuffer[i * 2 + 1])?.let { result.add(it) }
        }
        return result
    }

    private fun matches(groupColor: BubbleColor, candidate: Bubble): Boolean =
        !candidate.finished &&
            candidate.color == groupColor &&
            candidate.kind.matchesByColor

    /**
     * Flood fill of every bubble connected to [start] with the same colour.
     * Returns an empty list when [start] cannot match by colour.
     */
    fun findMatchGroup(start: Bubble): List<Bubble> {
        val result = ArrayList<Bubble>(12)
        if (!start.kind.matchesByColor || !start.color.playable) return result

        visited.clear()
        queue.clear()
        queue.add(start)
        visited.add(key(start.row, start.col))
        result.add(start)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val count = Hex.neighborsOf(current.row, current.col, neighborBuffer)
            for (i in 0 until count) {
                val row = neighborBuffer[i * 2]
                val col = neighborBuffer[i * 2 + 1]
                if (!visited.add(key(row, col))) continue
                val other = bubbleAt(row, col) ?: continue
                if (matches(start.color, other)) {
                    result.add(other)
                    queue.add(other)
                }
            }
        }
        return result
    }

    /**
     * Every bubble that is no longer connected to the ceiling. Unbreakable walls
     * act as anchors; everything else has to reach the top row.
     */
    fun findFloating(): List<Bubble> {
        visited.clear()
        queue.clear()
        collectBuffer.clear()

        forEachBubble { bubble ->
            val attachedToCeiling = bubble.row == topRow
            val isWall = bubble.kind == BubbleKind.UNBREAKABLE
            if ((attachedToCeiling || isWall) && visited.add(key(bubble.row, bubble.col))) {
                queue.add(bubble)
            }
        }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val count = Hex.neighborsOf(current.row, current.col, neighborBuffer)
            for (i in 0 until count) {
                val row = neighborBuffer[i * 2]
                val col = neighborBuffer[i * 2 + 1]
                if (!visited.add(key(row, col))) continue
                val other = bubbleAt(row, col) ?: continue
                queue.add(other)
            }
        }

        forEachBubble { bubble ->
            if (key(bubble.row, bubble.col) !in visited) collectBuffer.add(bubble)
        }
        return ArrayList(collectBuffer)
    }

    /**
     * Pushes a brand new row of bubbles onto the ceiling. Existing bubbles keep
     * their coordinates - only [topRow] moves - so this is O(1).
     *
     * @param animated plays the "row slides down" animation
     */
    fun insertTopRow(bubbles: List<Bubble>, animated: Boolean) {
        topRow -= 1
        for (bubble in bubbles) {
            bubble.row = topRow
            bubble.col = bubble.col.coerceIn(0, Hex.columnCount(topRow) - 1)
            bubble.kind = bubble.kind
            place(bubble)
        }
        if (animated) {
            descentOffset -= geometry.rowHeight
        }
    }

    /** Removes [bubbles] from the grid and returns them. */
    fun detach(bubbles: Collection<Bubble>): List<Bubble> {
        for (bubble in bubbles) remove(bubble)
        return bubbles.toList()
    }

    /** Clears a whole row (unbreakable walls survive) and returns what was there. */
    fun clearRow(row: Int): List<Bubble> {
        val array = rows[row] ?: return emptyList()
        val removed = ArrayList<Bubble>(array.size)
        for (i in array.indices) {
            val bubble = array[i] ?: continue
            if (bubble.kind == BubbleKind.UNBREAKABLE) continue
            removed.add(bubble)
            array[i] = null
        }
        return removed
    }

    /**
     * Bubbles within [radiusInCells] grid steps of the origin cell, measured in
     * pixel space so the blast stays circular on screen.
     */
    fun bubblesInRadius(originRow: Int, originCol: Int, radiusInCells: Float): List<Bubble> {
        val originX = geometry.cellCenterX(originRow, originCol)
        val originY = geometry.cellCenterY(originRow, topRow)
        val maxDistance = radiusInCells * geometry.diameter
        val maxDistanceSquared = maxDistance * maxDistance
        val result = ArrayList<Bubble>(24)
        forEachBubble { bubble ->
            if (bubble.kind != BubbleKind.UNBREAKABLE) {
                val dx = geometry.cellCenterX(bubble.row, bubble.col) - originX
                val dy = geometry.cellCenterY(bubble.row, topRow) - originY
                if (dx * dx + dy * dy <= maxDistanceSquared) result.add(bubble)
            }
        }
        return result
    }

    /** Grid cell closest to a pixel position, clamped to the board bounds. */
    fun cellAt(x: Float, y: Float): Cell {
        val row = geometry.nearestRow(y, topRow)
        val col = geometry.nearestColumn(row, x, slideOffset(row))
        return Cell(row, col)
    }

    /** Free cell closest to a pixel position inside the given row window. */
    fun nearestFreeCell(x: Float, y: Float, minRow: Int, maxRow: Int): Cell? {
        var best: Cell? = null
        var bestDistance = Float.MAX_VALUE
        for (row in minRow..maxRow) {
            for (col in 0 until Hex.columnCount(row)) {
                if (!isEmptyCell(row, col)) continue
                val dx = centerX(row, col) - x
                val dy = centerY(row) - y
                val distance = dx * dx + dy * dy
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = Cell(row, col)
                }
            }
        }
        return best
    }

    companion object {
        private const val TWO_PI = 6.2831855f
        private const val SLIDE_SPEED = 1.35f
        private const val DESCENT_DURATION = 0.32f

        /** Packs a grid coordinate into a single int; columns are always < 64. */
        fun key(row: Int, col: Int): Int = (row shl 6) or col
    }
}
