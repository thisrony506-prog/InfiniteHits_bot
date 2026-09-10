package com.infinitehits.bubbleblast.core.model

/** Lifecycle state of a bubble that still exists on the board. */
enum class BubbleState {
    /** Resting in its grid cell. */
    IDLE,

    /** Playing its pop animation, already removed from the match logic. */
    POPPING,

    /** Cracking out of its ice shell. */
    CRACKING,

    /** Just placed by a shot - plays a small squash-and-stretch entrance. */
    SPAWNING
}

/**
 * A single bubble on the board. Mutable on purpose: the engine recycles bubbles
 * every frame and allocating one object per grid cell per frame would hammer the
 * garbage collector on low-end devices.
 */
class Bubble(
    var row: Int,
    var col: Int,
    var color: BubbleColor,
    var kind: BubbleKind = BubbleKind.NORMAL,
    var state: BubbleState = BubbleState.IDLE,
    /** Remaining ice layers; only meaningful for [BubbleKind.ICE]. */
    var iceLayers: Int = 0
) {
    /** Counts up while the bubble plays a one-shot animation. */
    var animTime: Float = 0f

    /** True once the bubble has left the grid (popping animation finished). */
    var finished: Boolean = false

    val special: Boolean get() = kind.isSpecial

    val breakable: Boolean get() = kind.breakable

    val blocksBoardClear: Boolean get() = kind.blocksBoardClear

    /** Ice keeps the bubble alive for one extra match per layer. */
    val frozen: Boolean get() = kind == BubbleKind.ICE && iceLayers > 0

    fun copyInto(target: Bubble) {
        target.row = row
        target.col = col
        target.color = color
        target.kind = kind
        target.state = state
        target.iceLayers = iceLayers
        target.animTime = animTime
        target.finished = finished
    }

    override fun toString(): String = "Bubble($row,$col,$color,$kind)"
}
