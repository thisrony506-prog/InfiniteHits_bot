package com.infinitehits.bubbleblast.core.model

/**
 * What a bubble *is*. [BubbleColor] decides how it matches, [BubbleKind] decides
 * how it behaves when it is popped or hit.
 */
enum class BubbleKind(
    /** Can take part in a colour match and be removed by one. */
    val matchesByColor: Boolean,
    /** Is destroyed when it pops (obstacles are not). */
    val breakable: Boolean,
    /** Counts towards "clear the board" level completion. */
    val blocksBoardClear: Boolean
) {
    NORMAL(matchesByColor = true, breakable = true, blocksBoardClear = true),

    /** Destroys every bubble in a small radius when popped. */
    BOMB(matchesByColor = true, breakable = true, blocksBoardClear = true),

    /** Adopts the colour of the group it lands next to, so it matches anything. */
    RAINBOW(matchesByColor = true, breakable = true, blocksBoardClear = true),

    /** Clears its own horizontal row when popped. */
    LIGHTNING(matchesByColor = true, breakable = true, blocksBoardClear = true),

    /** Burns a small cluster of bubbles around it when popped. */
    FIRE(matchesByColor = true, breakable = true, blocksBoardClear = true),

    /** Grey obstacle: immune to colour matches, only specials destroy it. */
    STONE(matchesByColor = false, breakable = true, blocksBoardClear = false),

    /** Coloured bubble wrapped in ice: matched bubbles crack it instead of popping. */
    ICE(matchesByColor = true, breakable = true, blocksBoardClear = true),

    /** Cannot be matched until a neighbouring bubble pops, which frees it. */
    LOCKED(matchesByColor = false, breakable = true, blocksBoardClear = true),

    /** Decorative wall: never destroyed, always anchors the cluster. */
    UNBREAKABLE(matchesByColor = false, breakable = false, blocksBoardClear = false),

    /** Sits on a sliding row: the whole row drifts left and right. */
    MOVING(matchesByColor = true, breakable = true, blocksBoardClear = true);

    val isSpecial: Boolean
        get() = this == BOMB || this == RAINBOW || this == LIGHTNING || this == FIRE

    val isObstacle: Boolean
        get() = this == STONE || this == ICE || this == LOCKED || this == UNBREAKABLE || this == MOVING

    /** Specials may be handed out by the launcher; obstacles never are. */
    val isShootable: Boolean
        get() = this == NORMAL || isSpecial

    companion object {
        /** Kinds that can be dropped by a launcher (used to build level pools). */
        val SHOOTABLE: List<BubbleKind> = entries.filter { it.isShootable }
    }
}
