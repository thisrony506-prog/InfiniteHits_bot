package com.infinitehits.bubbleblast.core.powerup

/**
 * The four in-game power-ups. Names and descriptions live in
 * `strings.xml`; this enum only carries the gameplay data.
 */
enum class PowerUp(val price: Int) {

    /** Longer aim guide that also shows one extra wall bounce. */
    AIM_EXTENSION(120),

    /** Swaps the loaded bubble for the one in the preview slot. */
    COLOR_CHANGER(100),

    /** Tap a spot on the board to blow up a small cluster (costs no move). */
    BOMB(180),

    /** Adds three moves to the current level. */
    EXTRA_MOVES(150);

    companion object {
        val ALL: List<PowerUp> = PowerUp.entries.toList()

        /** What a brand new player starts with. */
        val STARTING_INVENTORY: Map<PowerUp, Int> = mapOf(
            AIM_EXTENSION to 3,
            COLOR_CHANGER to 3,
            BOMB to 2,
            EXTRA_MOVES to 3
        )

        /** Moves granted by [EXTRA_MOVES]. */
        const val EXTRA_MOVES_AMOUNT = 3

        /** Shots that keep the extended aim guide after [AIM_EXTENSION]. */
        const val AIM_EXTENSION_SHOTS = 3
    }
}
