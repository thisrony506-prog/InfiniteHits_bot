package com.infinitehits.bubbleblast.core.model

import androidx.annotation.ColorInt

/**
 * The six playable bubble colours plus one neutral tint used by obstacles.
 *
 * Every colour ships three ARGB values (base / dark rim / light highlight) so the
 * canvas renderer can bake a single pre-shaded sphere bitmap per colour and then
 * draw that bitmap every frame.
 */
enum class BubbleColor(
    @ColorInt val argb: Int,
    @ColorInt val darkArgb: Int,
    @ColorInt val lightArgb: Int,
    /** Playable colours can be loaded into the launcher and appear in matches. */
    val playable: Boolean
) {
    RED(0xFFFF4D5E.toInt(), 0xFFC81E36.toInt(), 0xFFFF97A2.toInt(), true),
    BLUE(0xFF2E9BFF.toInt(), 0xFF0B62C4.toInt(), 0xFF9AD1FF.toInt(), true),
    GREEN(0xFF31C95B.toInt(), 0xFF12813A.toInt(), 0xFF96EDA9.toInt(), true),
    YELLOW(0xFFFFC629.toInt(), 0xFFC98E00.toInt(), 0xFFFFE499.toInt(), true),
    PURPLE(0xFFA85CFF.toInt(), 0xFF6E23C8.toInt(), 0xFFD6B3FF.toInt(), true),
    ORANGE(0xFFFF7A2F.toInt(), 0xFFC94006.toInt(), 0xFFFFBC8A.toInt(), true),

    /** Used by stone / ice / locked / unbreakable obstacles. */
    GRAY(0xFF8C8FA6.toInt(), 0xFF5A5D74.toInt(), 0xFFC3C6D6.toInt(), false);

    companion object {
        /** The six colours a launcher may shoot, in canonical order. */
        val PLAYABLE: List<BubbleColor> = entries.filter { it.playable }

        val COUNT: Int = PLAYABLE.size

        /** Resolves a save-file index back to a colour, tolerating corrupt data. */
        fun fromIndex(index: Int): BubbleColor =
            PLAYABLE.getOrElse(index) { PLAYABLE[0] }

        fun indexOf(color: BubbleColor): Int = PLAYABLE.indexOf(color).coerceAtLeast(0)
    }
}
