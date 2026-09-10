package com.infinitehits.bubbleblast.game.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.infinitehits.bubbleblast.core.engine.FallingBubble
import com.infinitehits.bubbleblast.core.engine.GameEngine
import com.infinitehits.bubbleblast.core.model.BoardGeometry
import com.infinitehits.bubbleblast.core.model.Bubble
import com.infinitehits.bubbleblast.core.model.BubbleColor
import com.infinitehits.bubbleblast.core.model.BubbleKind
import com.infinitehits.bubbleblast.core.model.BubbleState
import com.infinitehits.bubbleblast.core.util.Rng
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * Draws the playing field: background, bubbles, danger line, launcher, aim guide
 * and the floating score callouts.
 *
 * The static sky is baked into a single bitmap whenever the view size changes,
 * so a frame costs one large `drawBitmap` plus one small blit per bubble.
 */
class BoardRenderer(private val painter: BubblePainter) {

    private var background: Bitmap? = null
    private var width = 0
    private var height = 0
    private var radius = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val popups = ArrayList<Popup>(12)
    private val dangerDash = DashPathEffect(floatArrayOf(18f, 14f), 0f)

    private var time = 0f
    private var celebrationTimer = 0f
    private var celebrationX = 0f
    private var celebrationY = 0f

    private class Popup(
        val text: String,
        val x: Float,
        val y: Float,
        val color: Int,
        val size: Float
    ) {
        var life = POPUP_LIFE
        val maxLife = POPUP_LIFE
    }

    // ------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------

    /** Bakes the static background. Cheap when nothing changed. */
    fun resize(viewWidth: Int, viewHeight: Int, geometry: BoardGeometry) {
        if (viewWidth == width && viewHeight == height && abs(geometry.bubbleRadius - radius) < 0.5f) return
        width = viewWidth
        height = viewHeight
        radius = geometry.bubbleRadius
        background = bakeBackground(viewWidth, viewHeight, geometry)
    }

    fun release() {
        background = null
        width = 0
        height = 0
    }

    fun update(dt: Float) {
        time += dt
        var i = 0
        while (i < popups.size) {
            val popup = popups[i]
            popup.life -= dt
            if (popup.life <= 0f) popups.removeAt(i) else i++
        }
        if (celebrationTimer > 0f) celebrationTimer -= dt
    }

    fun addPopup(text: String, x: Float, y: Float, color: Int, size: Float) {
        if (popups.size >= MAX_POPUPS) popups.removeAt(0)
        popups.add(Popup(text, x, y, color, size))
    }

    fun celebrate(x: Float, y: Float) {
        celebrationTimer = CELEBRATION_TIME
        celebrationX = x
        celebrationY = y
    }

    // ------------------------------------------------------------------
    // Background
    // ------------------------------------------------------------------

    private fun bakeBackground(viewWidth: Int, viewHeight: Int, geometry: BoardGeometry): Bitmap? {
        if (viewWidth <= 0 || viewHeight <= 0) return null
        val bitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Deep sky gradient with a warm glow behind the danger line.
        paint.shader = LinearGradient(
            0f, 0f, 0f, viewHeight.toFloat(),
            0xFF1B1040.toInt(), 0xFF3B1A6E.toInt(), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat(), paint)

        paint.shader = RadialGradient(
            viewWidth / 2f, viewHeight * 0.92f, viewHeight * 0.75f,
            0x5548E0FF.toInt(), 0x00100833, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat(), paint)
        paint.shader = null

        // Scattered starfield, deterministic so it does not shimmer between sizes.
        val rng = Rng(20240910L)
        paint.style = Paint.Style.FILL
        for (i in 0 until STAR_COUNT) {
            val sx = rng.nextFloat() * viewWidth
            val sy = rng.nextFloat() * viewHeight * 0.85f
            val starRadius = (0.6f + rng.nextFloat() * 1.6f) * (viewWidth / 400f).coerceAtLeast(0.6f)
            paint.color = Color.argb(
                (40 + rng.nextInt(90)),
                255, 255, 255
            )
            canvas.drawCircle(sx, sy, starRadius, paint)
        }

        // Ceiling bar with hanging hooks, drawn where the top row attaches.
        val ceilingHeight = max(6f, geometry.bubbleRadius * 0.62f)
        paint.shader = LinearGradient(
            0f, 0f, 0f, ceilingHeight,
            0xFF6A4BC7.toInt(), 0xFF33206B.toInt(), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, viewWidth.toFloat(), ceilingHeight, paint)
        paint.shader = null
        paint.color = 0x55FFFFFF
        canvas.drawRect(0f, ceilingHeight - max(1f, geometry.bubbleRadius * 0.07f), viewWidth.toFloat(), ceilingHeight, paint)

        val hookSpacing = geometry.diameter
        paint.color = 0x88C9A9FF.toInt()
        var hookX = geometry.boardLeft + geometry.bubbleRadius
        while (hookX < viewWidth) {
            canvas.drawCircle(hookX, ceilingHeight * 0.45f, max(1.2f, geometry.bubbleRadius * 0.1f), paint)
            hookX += hookSpacing
        }

        return bitmap
    }

    fun drawBackground(canvas: Canvas, geometry: BoardGeometry) {
        val bitmap = background
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, 0f, 0f, null)
        } else {
            paint.shader = null
            paint.color = 0xFF2A1655.toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }

    // ------------------------------------------------------------------
    // Board content
    // ------------------------------------------------------------------

    /** Draws every resting bubble plus the danger line. */
    fun drawBoard(canvas: Canvas, engine: GameEngine, geometry: BoardGeometry) {
        // Wall rails make the bounce believable.
        paint.color = 0x22FFFFFF
        canvas.drawRect(0f, 0f, geometry.boardLeft, height.toFloat(), paint)
        canvas.drawRect(geometry.boardLeft + geometry.boardWidth, 0f, width.toFloat(), height.toFloat(), paint)

        val bubbles = ArrayList<Bubble>(128)
        engine.board.forEachBubble { bubbles.add(it) }

        val radius = geometry.bubbleRadius
        for (bubble in bubbles) {
            val x = engine.board.centerX(bubble.row, bubble.col)
            val y = engine.board.centerY(bubble.row)
            val scale = if (bubble.state == BubbleState.SPAWNING) {
                1f + 0.22f * (1f - (bubble.animTime / SPAWN_TIME).coerceIn(0f, 1f))
            } else {
                1f
            }
            drawBubble(canvas, bubble.color, bubble.kind, x, y, radius, scale, 1f, bubble.iceLayers)
        }

        drawDangerLine(canvas, engine, geometry)
    }

    private fun drawDangerLine(canvas: Canvas, engine: GameEngine, geometry: BoardGeometry) {
        val lowest = engine.board.lowestEdgeY()
        val distance = if (lowest == null) Float.MAX_VALUE else geometry.dangerY - lowest
        val close = distance < geometry.rowHeight * 2.2f

        val pulse = 0.55f + 0.45f * sin(time * (if (close) 6f else 2.4f))
        paint.style = Paint.Style.STROKE
        paint.pathEffect = dangerDash
        paint.strokeWidth = max(2f, geometry.bubbleRadius * 0.16f)
        paint.color = if (close) {
            Color.argb((90 + 140 * pulse).toInt(), 255, 77, 94)
        } else {
            Color.argb((45 + 55 * pulse).toInt(), 255, 214, 102)
        }
        val lineY = geometry.dangerY
        canvas.drawLine(0f, lineY, width.toFloat(), lineY, paint)
        paint.pathEffect = null
        paint.style = Paint.Style.FILL
    }

    /** Popping, cracking and falling bubbles plus the travelling shot. */
    fun drawEffects(canvas: Canvas, engine: GameEngine, geometry: BoardGeometry, particles: ParticleSystem) {
        val radius = geometry.bubbleRadius

        for (bubble in engine.poppingBubbles) {
            val animTime = bubble.animTime
            if (animTime < 0f) {
                // Staggered start: still fully visible.
                continue
            }
            val progress = (animTime / GameEngine.POP_DURATION).coerceIn(0f, 1f)
            val scale = 1f + progress * 0.55f
            val alpha = (1f - progress * progress)
            val x = engine.board.centerX(bubble.row, bubble.col)
            val y = engine.board.centerY(bubble.row)
            drawBubble(canvas, bubble.color, bubble.kind, x, y, radius, scale, alpha, 0)

            // Expanding ring
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1.5f, radius * 0.12f * (1f - progress))
            paint.color = Color.argb(((1f - progress) * 200).toInt(), 255, 255, 255)
            canvas.drawCircle(x, y, radius * (1f + progress * 1.1f), paint)
            paint.style = Paint.Style.FILL
        }

        for (bubble in engine.crackingBubbles) {
            val progress = (bubble.animTime / CRACK_TIME).coerceIn(0f, 1f)
            val shakeX = sin(bubble.animTime * 60f) * radius * 0.12f * (1f - progress)
            val x = engine.board.centerX(bubble.row, bubble.col) + shakeX
            val y = engine.board.centerY(bubble.row)
            paint.color = Color.argb(((1f - progress) * 160).toInt(), 255, 255, 255)
            canvas.drawCircle(x, y, radius * 1.05f, paint)
        }

        for (falling in engine.fallingBubbles) {
            drawFalling(canvas, falling, radius)
        }

        for (shot in engine.snapshotShots()) {
            drawBubble(canvas, shot.color, shot.kind, shot.x, shot.y, radius, 1f, 1f, 0)
        }

        particles.draw(canvas, painter.particle)
    }

    private fun drawFalling(canvas: Canvas, falling: FallingBubble, radius: Float) {
        val save = canvas.save()
        canvas.translate(falling.x, falling.y)
        canvas.rotate(falling.rotation * 57.29578f)
        drawBubble(canvas, falling.color, falling.kind, 0f, 0f, radius, 1f, 0.92f, 0)
        canvas.restoreToCount(save)
    }

    // ------------------------------------------------------------------
    // Bubble primitives
    // ------------------------------------------------------------------

    fun drawBubble(
        canvas: Canvas,
        color: BubbleColor,
        kind: BubbleKind,
        x: Float,
        y: Float,
        radius: Float,
        scale: Float,
        alpha: Float,
        iceLayers: Int
    ) {
        val shell = if (kind == BubbleKind.RAINBOW) painter.rainbow() else painter.shell(color)
        val save = canvas.save()
        canvas.translate(x, y)
        if (scale != 1f) canvas.scale(scale, scale)
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        canvas.drawBitmap(shell, -shell.width / 2f, -shell.height / 2f, paint)
        paint.alpha = 255

        if (iceLayers > 0 || kind == BubbleKind.ICE) {
            val ice = painter.ice()
            paint.alpha = ((if (iceLayers > 1) 235 else 190) * alpha).toInt().coerceIn(0, 255)
            canvas.drawBitmap(ice, -ice.width / 2f, -ice.height / 2f, paint)
            paint.alpha = 255
        }

        painter.glyph(kind)?.let { glyph ->
            paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
            canvas.drawBitmap(glyph, -glyph.width / 2f, -glyph.height / 2f, paint)
            paint.alpha = 255
        }
        canvas.restoreToCount(save)
    }

    // ------------------------------------------------------------------
    // Launcher & aim guide
    // ------------------------------------------------------------------

    /** Draws the launcher base, barrel and the loaded bubble. */
    fun drawLauncher(
        canvas: Canvas,
        engine: GameEngine,
        geometry: BoardGeometry,
        currentColor: BubbleColor,
        currentKind: BubbleKind
    ) {
        val x = engine.launcherX()
        val y = engine.launcherY()
        val radius = geometry.bubbleRadius
        val angle = engine.aimAngle

        // Pedestal
        paint.shader = LinearGradient(
            x - radius * 1.9f, y, x + radius * 1.9f, y + radius * 2.4f,
            0xFF6A4BC7.toInt(), 0xFF2C1A5E.toInt(), Shader.TileMode.CLAMP
        )
        val pedestal = RectF(
            x - radius * 1.9f,
            y + radius * 0.7f,
            x + radius * 1.9f,
            y + radius * 2.6f
        )
        canvas.drawRoundRect(pedestal, radius * 0.8f, radius * 0.8f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, radius * 0.08f)
        paint.color = 0x66FFFFFF
        canvas.drawRoundRect(pedestal, radius * 0.8f, radius * 0.8f, paint)
        paint.style = Paint.Style.FILL

        // Barrel
        val save = canvas.save()
        canvas.translate(x, y)
        canvas.rotate(angle * 57.29578f)
        val barrel = RectF(-radius * 0.42f, -radius * 2.3f, radius * 0.42f, -radius * 0.2f)
        paint.shader = LinearGradient(
            barrel.left, 0f, barrel.right, 0f,
            0xFFB9A5F0.toInt(), 0xFF5B3FA8.toInt(), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(barrel, radius * 0.36f, radius * 0.36f, paint)
        paint.shader = null
        canvas.restoreToCount(save)

        // Loaded bubble with a gentle bob
        val bob = sin(time * 3.2f) * radius * 0.05f
        drawBubble(canvas, currentColor, currentKind, x, y + bob, radius, 1f, 1f, 0)
    }

    /** Dotted trajectory. [showTrajectory] off collapses it to a short hint. */
    fun drawAimGuide(canvas: Canvas, engine: GameEngine, geometry: BoardGeometry, showTrajectory: Boolean) {
        val pointCount = engine.traceCount
        if (pointCount <= 0) return
        val dot = painter.particle ?: return
        val radius = geometry.bubbleRadius
        val step = if (showTrajectory) 1 else 8

        var index = 0
        while (index < pointCount) {
            val t = index.toFloat() / pointCount
            val alpha = ((1f - t) * 0.85f + 0.15f) * 255f
            val dotRadius = radius * (if (showTrajectory) 0.16f else 0.2f)
            val x = engine.tracePointX(index)
            val y = engine.tracePointY(index)
            paint.alpha = alpha.toInt().coerceIn(0, 255)
            paint.color = Color.WHITE
            canvas.drawBitmap(
                dot,
                null,
                RectF(x - dotRadius, y - dotRadius, x + dotRadius, y + dotRadius),
                paint
            )
            index += step
        }
        paint.alpha = 255

        // Landing reticle
        val lastX = engine.tracePointX(pointCount - 1)
        val lastY = engine.tracePointY(pointCount - 1)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1.5f, radius * 0.14f)
        paint.color = Color.argb(210, 77, 225, 255)
        canvas.drawCircle(lastX, lastY, radius * 0.85f, paint)
        paint.color = Color.argb(120, 255, 255, 255)
        canvas.drawCircle(lastX, lastY, radius * 0.45f, paint)
        paint.style = Paint.Style.FILL
    }

    /** Pulsing border shown while the bomb power-up waits for a tap. */
    fun drawArmedOverlay(canvas: Canvas, geometry: BoardGeometry) {
        val pulse = 0.5f + 0.5f * sin(time * 7f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(2f, geometry.bubbleRadius * 0.22f)
        paint.color = Color.argb((70 + 120 * pulse).toInt(), 255, 122, 47)
        canvas.drawRect(
            paint.strokeWidth / 2f,
            paint.strokeWidth / 2f,
            width - paint.strokeWidth / 2f,
            height - paint.strokeWidth / 2f,
            paint
        )
        paint.style = Paint.Style.FILL
    }

    /** Highlights the cell under the finger while aiming. */
    fun drawAimHint(canvas: Canvas, x: Float, y: Float, geometry: BoardGeometry, valid: Boolean) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1.5f, geometry.bubbleRadius * 0.14f)
        paint.color = if (valid) Color.argb(180, 56, 217, 107) else Color.argb(180, 255, 77, 94)
        canvas.drawCircle(x, y, geometry.bubbleRadius * 0.9f, paint)
        paint.style = Paint.Style.FILL
    }

    // ------------------------------------------------------------------
    // Callouts & celebration
    // ------------------------------------------------------------------

    fun drawPopups(canvas: Canvas) {
        if (popups.isEmpty()) return
        for (popup in popups) {
            val progress = 1f - popup.life / popup.maxLife
            val alpha = ((1f - progress) * 255f).toInt().coerceIn(0, 255)
            val y = popup.y - progress * popup.size * 2.4f
            textPaint.textSize = popup.size
            textPaint.alpha = alpha
            textPaint.color = popup.color
            textPaint.setShadowLayer(popup.size * 0.16f, 0f, popup.size * 0.1f, 0xB3000000.toInt())
            canvas.drawText(popup.text, popup.x, y, textPaint)
            textPaint.clearShadowLayer()
        }
        textPaint.alpha = 255
    }

    fun drawCelebration(canvas: Canvas) {
        if (celebrationTimer <= 0f) return
        val progress = 1f - celebrationTimer / CELEBRATION_TIME
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(2f, radius * 0.3f * (1f - progress))
        paint.color = Color.argb(((1f - progress) * 190).toInt(), 255, 214, 102)
        canvas.drawCircle(celebrationX, celebrationY, width * 0.15f + progress * width * 0.75f, paint)
        paint.style = Paint.Style.FILL
    }

    val popupCount: Int get() = popups.size

    companion object {
        private const val STAR_COUNT = 70
        private const val POPUP_LIFE = 1.1f
        private const val MAX_POPUPS = 16
        private const val SPAWN_TIME = 0.18f
        private const val CRACK_TIME = 0.25f
        private const val CELEBRATION_TIME = 0.9f
    }
}
