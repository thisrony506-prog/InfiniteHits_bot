package com.infinitehits.bubbleblast.core.engine

import com.infinitehits.bubbleblast.core.model.BubbleColor
import com.infinitehits.bubbleblast.core.model.BubbleKind
import com.infinitehits.bubbleblast.core.util.Rng

/**
 * The launcher's magazine: what is loaded now and what comes next.
 *
 * The shooter never hands out a colour that is not on the board - if the last
 * red bubble disappears while a red bubble is loaded, the loaded bubble is
 * recoloured. That single rule removes the most common cause of unwinnable
 * boards in bubble shooters.
 */
class Shooter {

    var current: BubbleSpec = BubbleSpec.EMPTY
        private set

    var next: BubbleSpec = BubbleSpec.EMPTY
        private set

    private var pool: List<BubbleColor> = BubbleColor.PLAYABLE.take(3)
    private var allowedSpecials: List<BubbleKind> = emptyList()
    private var specialChance: Float = 0f
    private lateinit var rng: Rng

    fun reset(
        colors: List<BubbleColor>,
        specialChance: Float,
        allowedSpecials: List<BubbleKind>,
        rng: Rng
    ) {
        this.pool = colors.ifEmpty { BubbleColor.PLAYABLE.take(3) }
        this.specialChance = specialChance.coerceIn(0f, 0.5f)
        this.allowedSpecials = allowedSpecials.filter { it.isSpecial }
        this.rng = rng
        current = generate(emptyList())
        next = generate(emptyList())
    }

    /** Called after every shot: the preview bubble becomes the loaded one. */
    fun rotate(activeColors: List<BubbleColor>) {
        current = next
        next = generate(activeColors)
        syncWithBoard(activeColors)
    }

    /** Colour changer power-up. */
    fun swap() {
        val tmp = current
        current = next
        next = tmp
    }

    /** Recolours loaded bubbles whose colour is no longer on the board. */
    fun syncWithBoard(activeColors: List<BubbleColor>) {
        if (activeColors.isEmpty()) return
        if (current.color !in activeColors) current = current.copy(color = pickColor(activeColors))
        if (next.color !in activeColors) next = next.copy(color = pickColor(activeColors))
    }

    private fun generate(activeColors: List<BubbleColor>): BubbleSpec {
        val color = pickColor(activeColors)
        val kind = if (allowedSpecials.isNotEmpty() && rng.nextFloat() < specialChance) {
            allowedSpecials[rng.nextInt(allowedSpecials.size)]
        } else {
            BubbleKind.NORMAL
        }
        return BubbleSpec(color, kind)
    }

    private fun pickColor(activeColors: List<BubbleColor>): BubbleColor {
        val usable = if (activeColors.isEmpty()) {
            pool
        } else {
            activeColors.filter { it in pool }.ifEmpty { activeColors }
        }
        return usable[rng.nextInt(usable.size)]
    }
}
