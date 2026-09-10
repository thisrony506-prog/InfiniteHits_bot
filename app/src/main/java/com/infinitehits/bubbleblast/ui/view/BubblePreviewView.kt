package com.infinitehits.bubbleblast.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.infinitehits.bubbleblast.core.engine.BubbleSpec
import com.infinitehits.bubbleblast.core.model.BubbleColor
import com.infinitehits.bubbleblast.core.model.BubbleKind
import com.infinitehits.bubbleblast.game.render.BubblePainter
import kotlin.math.min

/**
 * The "next bubble" preview in the HUD.
 *
 * Uses the same [BubblePainter] as the board, so the preview is pixel-identical
 * to the bubble that will actually be fired. Pops with a little bounce whenever
 * the bubble changes.
 */
class BubblePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val painter = BubblePainter()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var color: BubbleColor = BubbleColor.RED
    private var kind: BubbleKind = BubbleKind.NORMAL

    /** 1 = settled, > 1 = mid bounce. */
    private var bounce = 1f

    fun setBubble(spec: BubbleSpec) {
        setBubble(spec.color, spec.kind)
    }

    fun setBubble(newColor: BubbleColor, newKind: BubbleKind) {
        if (newColor == color && newKind == kind) return
        color = newColor
        kind = newKind
        bounce = 1.25f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val radius = min(width, height) / 2f * 0.86f
        painter.configure(radius, resources.displayMetrics.density)

        // Ease the bounce back to 1.
        if (bounce > 1f) {
            bounce = (bounce - 0.045f).coerceAtLeast(1f)
            postInvalidateOnAnimation()
        }

        val shell = if (kind == BubbleKind.RAINBOW) painter.rainbow() else painter.shell(color)
        val save = canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(bounce, bounce)
        canvas.drawBitmap(shell, -shell.width / 2f, -shell.height / 2f, paint)
        painter.glyph(kind)?.let { glyph ->
            canvas.drawBitmap(glyph, -glyph.width / 2f, -glyph.height / 2f, paint)
        }
        canvas.restoreToCount(save)
    }

    override fun onDetachedFromWindow() {
        painter.release()
        super.onDetachedFromWindow()
    }
}
