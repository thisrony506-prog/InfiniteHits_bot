package com.infinitehits.bubbleblast.core.engine

import com.infinitehits.bubbleblast.core.model.BubbleColor
import com.infinitehits.bubbleblast.core.model.BubbleKind

/** What the launcher currently holds. */
data class BubbleSpec(val color: BubbleColor, val kind: BubbleKind) {
    companion object {
        val EMPTY = BubbleSpec(BubbleColor.RED, BubbleKind.NORMAL)
    }
}

/** A bubble travelling from the launcher towards the board. */
class Shot(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: BubbleColor,
    val kind: BubbleKind
) {
    var bounces: Int = 0

    val speedSquared: Float get() = vx * vx + vy * vy

    fun speed(): Float = kotlin.math.sqrt(speedSquared)
}

/** A bubble that lost its anchor and is now falling off the bottom of the screen. */
class FallingBubble(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rotation: Float,
    val rotationSpeed: Float,
    val color: BubbleColor,
    val kind: BubbleKind
)
