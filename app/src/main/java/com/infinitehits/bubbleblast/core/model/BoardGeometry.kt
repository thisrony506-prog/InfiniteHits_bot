package com.infinitehits.bubbleblast.core.model

/**
 * Pixel geometry of the bubble board. Kept free of Android types so the whole
 * engine can be unit tested on the JVM.
 *
 * The bubble radius is derived from the view width so that exactly
 * [Hex.COLS_EVEN] bubbles fit across the board on every screen size, then
 * clamped by the available height so tall thin phones do not get oversized
 * bubbles.
 */
class BoardGeometry {

    /** Width of the play area in pixels. */
    var viewWidth: Float = 0f
        private set

    /** Height of the play area in pixels. */
    var viewHeight: Float = 0f
        private set

    /** Left edge of the board (the play area is horizontally centred). */
    var boardLeft: Float = 0f
        private set

    /** Total board width = 2r * COLS_EVEN. */
    var boardWidth: Float = 0f
        private set

    var bubbleRadius: Float = 16f
        private set

    /** Vertical distance between two rows. */
    var rowHeight: Float = 0f
        private set

    /** The y coordinate of the ceiling the top row hangs from. */
    var ceilingY: Float = 0f
        private set

    /** The y coordinate of the danger line. */
    var dangerY: Float = 0f
        private set

    /** Height reserved underneath the board for the launcher. */
    var launcherZoneHeight: Float = 0f
        private set

    val diameter: Float get() = bubbleRadius * 2f

    /** How many complete rows fit between the ceiling and the danger line. */
    var visibleRows: Int = 0
        private set

    /**
     * Recomputes the layout.
     *
     * @param width            play area width in pixels
     * @param height           play area height in pixels
     * @param horizontalPadding breathing room on both sides
     * @param maxRows          how many rows must be visible above the danger line
     * @param dangerExtraRows  extra clearance rows removed from the danger line
     */
    fun resize(
        width: Float,
        height: Float,
        horizontalPadding: Float,
        maxRows: Int,
        dangerExtraRows: Int
    ) {
        viewWidth = width
        viewHeight = height
        boardWidth = (width - horizontalPadding * 2f).coerceAtLeast(1f)

        val radiusByWidth = boardWidth / (Hex.COLS_EVEN * 2f)
        // Reserve room for the launcher, then fit `maxRows` rows into what is left.
        val launcherFraction = 0.22f
        val boardHeight = height * (1f - launcherFraction)
        val radiusByHeight = boardHeight / (2f * (1f + (maxRows - 1) * Hex.ROW_HEIGHT_FACTOR))
        bubbleRadius = minOf(radiusByWidth, radiusByHeight).coerceAtLeast(6f)

        boardWidth = bubbleRadius * 2f * Hex.COLS_EVEN
        boardLeft = (width - boardWidth) / 2f
        rowHeight = diameter * Hex.ROW_HEIGHT_FACTOR
        ceilingY = 0f

        launcherZoneHeight = height * launcherFraction
        dangerY = height - launcherZoneHeight - dangerExtraRows * rowHeight

        visibleRows = (((dangerY - ceilingY) / rowHeight).toInt()).coerceAtLeast(3)
    }

    /** Centre x of a cell, without any sliding-row offset. */
    fun cellCenterX(row: Int, col: Int): Float =
        boardLeft + bubbleRadius + col * diameter + Hex.rowOffsetUnits(row) * diameter

    /**
     * Centre y of a cell. [topRow] is the row index currently glued to the ceiling;
     * rows above it are negative so inserting a row does not move any bubble data.
     */
    fun cellCenterY(row: Int, topRow: Int): Float =
        ceilingY + bubbleRadius + (row - topRow) * rowHeight

    /** Column whose cell centre is closest to [x] inside [row]. */
    fun nearestColumn(row: Int, x: Float, slideOffset: Float = 0f): Int {
        val columns = Hex.columnCount(row)
        val relative = x - boardLeft - bubbleRadius - Hex.rowOffsetUnits(row) * diameter - slideOffset
        val col = Math.round(relative / diameter)
        return col.coerceIn(0, columns - 1)
    }

    /** Row whose cell centre is closest to [y]. May be outside the board. */
    fun nearestRow(y: Float, topRow: Int): Int =
        Math.round((y - ceilingY - bubbleRadius) / rowHeight) + topRow
}
