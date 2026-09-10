package com.infinitehits.bubbleblast.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.infinitehits.bubbleblast.core.model.BubbleColor
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pooled particle system for the pop sparks and celebration bursts.
 *
 * Storage is three flat arrays with a fixed capacity - no allocation happens
 * while the game runs, which keeps the garbage collector quiet on low-end
 * devices.
 */
class ParticleSystem(private val capacity: Int = 320) {

    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val maxLife = FloatArray(capacity)
    private val size = FloatArray(capacity)
    private val color = IntArray(capacity)
    private val drag = FloatArray(capacity)

    /** Number of live particles. */
    private var count = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var randomSeed = 12345L

    private fun nextRandom(): Float {
        // xorshift32 - cheaper than java.util.Random and good enough for sparks.
        randomSeed = randomSeed xor (randomSeed shl 13)
        randomSeed = randomSeed xor (randomSeed shr 7)
        randomSeed = randomSeed xor (randomSeed shl 17)
        return ((randomSeed ushr 8) and 0xFFFFFF).toFloat() / 0xFFFFFF.toFloat()
    }

    val isEmpty: Boolean get() = count == 0

    fun clear() {
        count = 0
    }

    /**
     * Spawns a ring of sparks.
     * @param strength multiplier for the burst speed (1 = normal pop)
     */
    fun burst(
        originX: Float,
        originY: Float,
        bubbleColor: BubbleColor,
        particleCount: Int = 10,
        strength: Float = 1f,
        baseSize: Float = 8f
    ) {
        for (i in 0 until particleCount) {
            if (count >= capacity) return
            val angle = nextRandom() * TWO_PI
            val speed = (0.4f + nextRandom() * 0.9f) * strength
            val index = count++
            x[index] = originX
            y[index] = originY
            vx[index] = cos(angle) * speed
            vy[index] = sin(angle) * speed - 0.25f * strength
            maxLife[index] = 0.42f + nextRandom() * 0.4f
            life[index] = maxLife[index]
            size[index] = baseSize * (0.55f + nextRandom() * 0.85f)
            drag[index] = 1.6f + nextRandom()
            color[index] = if (i % 3 == 0) Color.WHITE else bubbleColor.lightArgb
        }
    }

    /** A soft upward sparkle - used for special bubbles and star reveals. */
    fun sparkle(originX: Float, originY: Float, sparkleColor: Int, particleCount: Int = 12, baseSize: Float = 7f) {
        for (i in 0 until particleCount) {
            if (count >= capacity) return
            val angle = -nextRandom() * Math.PI.toFloat()
            val speed = 0.5f + nextRandom() * 1.2f
            val index = count++
            x[index] = originX
            y[index] = originY
            vx[index] = cos(angle) * speed * 0.6f
            vy[index] = sin(angle) * speed
            maxLife[index] = 0.5f + nextRandom() * 0.6f
            life[index] = maxLife[index]
            size[index] = baseSize * (0.5f + nextRandom())
            drag[index] = 0.9f + nextRandom() * 0.6f
            color[index] = sparkleColor
        }
    }

    /** Advances the simulation. [dt] is seconds, [scale] converts speed to pixels. */
    fun update(dt: Float, scale: Float) {
        var i = 0
        while (i < count) {
            life[i] -= dt
            if (life[i] <= 0f) {
                // Swap-remove: order does not matter for particles.
                val last = count - 1
                if (i != last) {
                    x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last]
                    life[i] = life[last]; maxLife[i] = maxLife[last]; size[i] = size[last]
                    color[i] = color[last]; drag[i] = drag[last]
                }
                count--
                continue
            }
            val damping = 1f - (drag[i] * dt).coerceAtMost(0.9f)
            vx[i] *= damping
            vy[i] = vy[i] * damping + GRAVITY * dt * scale
            x[i] += vx[i] * dt * scale
            y[i] += vy[i] * dt * scale
            i++
        }
    }

    /** Draws every particle with the shared soft dot bitmap. */
    fun draw(canvas: Canvas, dot: Bitmap?) {
        if (count == 0 || dot == null) return
        val halfWidth = dot.width / 2f
        val halfHeight = dot.height / 2f
        for (i in 0 until count) {
            val fraction = (life[i] / maxLife[i]).coerceIn(0f, 1f)
            val scale = size[i] * (0.35f + fraction * 0.9f) / halfWidth
            paint.alpha = (fraction * 255f).toInt().coerceIn(0, 255)
            paint.color = color[i]
            paint.colorFilter = null
            val save = canvas.save()
            canvas.translate(x[i], y[i])
            canvas.scale(scale, scale)
            canvas.drawBitmap(dot, -halfWidth, -halfHeight, paint)
            canvas.restore()
            if (save < 0) break
        }
        paint.alpha = 255
    }

    companion object {
        private const val TWO_PI = 6.2831855f
        private const val GRAVITY = 0.55f
    }
}
