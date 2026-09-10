package com.infinitehits.bubbleblast.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.infinitehits.bubbleblast.core.model.BubbleColor
import com.infinitehits.bubbleblast.core.util.Rng
import com.infinitehits.bubbleblast.game.render.BubblePainter
import kotlin.math.min

/**
 * Decorative background bubbles that drift upwards behind the menus.
 *
 * Deliberately cheap: one small bitmap per bubble, no gradients per frame, and
 * the animation stops completely when the view is off screen or detached.
 */
class BubbleFieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private class Drifter(
        var x: Float,
        var y: Float,
        var speed: Float,
        var swayPhase: Float,
        var swaySpeed: Float,
        var radius: Float,
        val color: BubbleColor
    )

    private val painter = BubblePainter()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rng = Rng(0x5EEDBEEFL)
    private val drifters = ArrayList<Drifter>(BUBBLE_COUNT)

    private var lastFrameNanos = 0L
    private var running = false

    private val frameCallback = object : Runnable {
        override fun run() {
            if (!running) return
            invalidate()
            postOnAnimation(this)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        seed(w.toFloat(), h.toFloat())
    }

    private fun seed(width: Float, height: Float) {
        drifters.clear()
        if (width <= 0f || height <= 0f) return
        val base = min(width, height) * 0.07f
        val colors = BubbleColor.PLAYABLE
        for (i in 0 until BUBBLE_COUNT) {
            drifters.add(
                Drifter(
                    x = rng.nextFloat() * width,
                    y = rng.nextFloat() * height,
                    speed = (0.02f + rng.nextFloat() * 0.05f) * height,
                    swayPhase = rng.nextFloat() * 6.28f,
                    swaySpeed = 0.4f + rng.nextFloat() * 0.9f,
                    radius = base * (0.5f + rng.nextFloat() * 0.9f),
                    color = colors[rng.nextInt(colors.size)]
                )
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (drifters.isEmpty()) return

        val now = System.nanoTime()
        var dt = if (lastFrameNanos == 0L) 0f else (now - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = now
        if (dt > 0.05f) dt = 0.05f

        val width = width.toFloat()
        val height = height.toFloat()

        for (drifter in drifters) {
            drifter.y -= drifter.speed * dt
            drifter.swayPhase += drifter.swaySpeed * dt
            if (drifter.y + drifter.radius < 0f) {
                drifter.y = height + drifter.radius
                drifter.x = rng.nextFloat() * width
            }
            val x = drifter.x + kotlin.math.sin(drifter.swayPhase) * drifter.radius * 0.8f
            painter.configure(drifter.radius, resources.displayMetrics.density)
            val shell = painter.shell(drifter.color)
            paint.alpha = DECORATION_ALPHA
            canvas.drawBitmap(shell, x - shell.width / 2f, drifter.y - shell.height / 2f, paint)
            paint.alpha = 255
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        painter.release()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && isAttachedToWindow) start() else stop()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && isAttachedToWindow) start() else stop()
    }

    private fun start() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        postOnAnimation(frameCallback)
    }

    private fun stop() {
        running = false
        removeCallbacks(frameCallback)
    }

    companion object {
        private const val BUBBLE_COUNT = 14
        private const val DECORATION_ALPHA = 90
    }
}
