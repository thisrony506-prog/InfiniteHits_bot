package com.infinitehits.bubbleblast.game.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.infinitehits.bubbleblast.core.model.BubbleColor
import com.infinitehits.bubbleblast.core.model.BubbleKind
import kotlin.math.max

/**
 * Pre-renders every bubble into a small bitmap once per size change.
 *
 * Drawing a bubble then costs a single `drawBitmap` call: no gradients, no
 * paths, no allocations per frame. That is what keeps the game at 60 FPS on
 * low-end hardware - the shading, gloss and special glyphs are all baked.
 */
class BubblePainter {

    private var radius = 0f
    private var density = 1f

    private val shells = HashMap<BubbleColor, Bitmap>()
    private val glyphs = HashMap<BubbleKind, Bitmap>()
    private var rainbowShell: Bitmap? = null
    private var iceOverlay: Bitmap? = null

    /** Soft round particle used for pop sparks and the aim guide dots. */
    var particle: Bitmap? = null
        private set

    /** Prepared per size change. Cheap to call every frame - it early-outs. */
    fun configure(radius: Float, density: Float) {
        if (this.radius == radius && this.density == density && shells.isNotEmpty()) return
        this.radius = radius
        this.density = density
        build()
    }

    fun shell(color: BubbleColor): Bitmap = shells.getOrPut(color) { buildShell(color, radius) }

    fun rainbow(): Bitmap = rainbowShell ?: buildRainbow(radius).also { rainbowShell = it }

    fun ice(): Bitmap = iceOverlay ?: buildIce(radius).also { iceOverlay = it }

    fun glyph(kind: BubbleKind): Bitmap? = when (kind) {
        BubbleKind.BOMB, BubbleKind.LIGHTNING, BubbleKind.FIRE,
        BubbleKind.LOCKED, BubbleKind.STONE, BubbleKind.UNBREAKABLE,
        BubbleKind.MOVING -> glyphs.getOrPut(kind) { buildGlyph(kind, radius) }

        else -> null
    }

    private fun build() {
        shells.clear()
        glyphs.clear()
        rainbowShell = null
        iceOverlay = null
        particle = buildParticle(radius)
    }

    /**
     * Drops every cached bitmap. They are not recycled explicitly: the renderer
     * may still be holding a reference in a local variable, and letting the GC
     * collect them avoids "trying to use a recycled bitmap" crashes.
     */
    fun release() {
        shells.clear()
        glyphs.clear()
        rainbowShell = null
        iceOverlay = null
        particle = null
        radius = 0f
    }

    // ------------------------------------------------------------------
    // Bitmap builders
    // ------------------------------------------------------------------

    private fun buildShell(color: BubbleColor, radius: Float): Bitmap {
        val size = max(2, (radius * 2f).toInt())
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centre = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Sphere shading: highlight top-left, dark rim bottom-right.
        paint.shader = RadialGradient(
            centre - radius * 0.32f,
            centre - radius * 0.38f,
            radius * 1.45f,
            intArrayOf(color.lightArgb, color.argb, color.darkArgb),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centre, centre, radius * 0.985f, paint)

        // Rim for definition against busy backgrounds.
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, radius * 0.1f)
        paint.color = withAlpha(color.darkArgb, 150)
        canvas.drawCircle(centre, centre, radius * 0.94f, paint)

        // Glossy highlight.
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            centre - radius * 0.34f,
            centre - radius * 0.4f,
            radius * 0.55f,
            0xCCFFFFFF.toInt(),
            0x00FFFFFF,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centre - radius * 0.33f, centre - radius * 0.37f, radius * 0.42f, paint)
        paint.shader = null

        return bitmap
    }

    /** The rainbow bubble: six wedges of the playable palette. */
    private fun buildRainbow(radius: Float): Bitmap {
        val size = max(2, (radius * 2f).toInt())
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centre = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(centre - radius, centre - radius, centre + radius, centre + radius)
        val colors = BubbleColor.PLAYABLE

        for (i in colors.indices) {
            paint.shader = null
            paint.color = colors[i].argb
            canvas.drawArc(rect, i * (360f / colors.size), 360f / colors.size + 1f, true, paint)
        }

        // Soft inner shade so the wedges read as a sphere.
        paint.shader = RadialGradient(
            centre - radius * 0.3f,
            centre - radius * 0.34f,
            radius * 1.25f,
            intArrayOf(0x66FFFFFF, 0x11000000, 0x99000000.toInt()),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centre, centre, radius * 0.985f, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, radius * 0.1f)
        paint.color = withAlpha(Color.WHITE, 170)
        canvas.drawCircle(centre, centre, radius * 0.94f, paint)
        paint.style = Paint.Style.FILL

        return bitmap
    }

    /** Translucent icy shell drawn over a frozen bubble. */
    private fun buildIce(radius: Float): Bitmap {
        val size = max(2, (radius * 2f).toInt())
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centre = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.shader = RadialGradient(
            centre - radius * 0.2f,
            centre - radius * 0.25f,
            radius * 1.3f,
            intArrayOf(0xE6E8FBFF.toInt(), 0xB3A9E4FF.toInt(), 0x9980C4E8.toInt()),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centre, centre, radius * 0.97f, paint)

        // Crack lines.
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, radius * 0.09f)
        paint.color = 0x99FFFFFF.toInt()
        val crack = Path()
        crack.moveTo(centre - radius * 0.55f, centre - radius * 0.2f)
        crack.lineTo(centre - radius * 0.1f, centre - radius * 0.45f)
        crack.lineTo(centre + radius * 0.15f, centre - radius * 0.05f)
        crack.lineTo(centre + radius * 0.55f, centre - radius * 0.35f)
        canvas.drawPath(crack, paint)
        val crack2 = Path()
        crack2.moveTo(centre - radius * 0.35f, centre + radius * 0.55f)
        crack2.lineTo(centre - radius * 0.05f, centre + radius * 0.1f)
        crack2.lineTo(centre + radius * 0.4f, centre + radius * 0.45f)
        canvas.drawPath(crack2, paint)

        paint.style = Paint.Style.FILL
        return bitmap
    }

    private fun buildGlyph(kind: BubbleKind, radius: Float): Bitmap {
        val size = max(2, (radius * 2f).toInt())
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centre = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (kind) {
            BubbleKind.BOMB -> drawBombGlyph(canvas, centre, radius, paint)
            BubbleKind.LIGHTNING -> drawBoltGlyph(canvas, centre, radius, paint)
            BubbleKind.FIRE -> drawFlameGlyph(canvas, centre, radius, paint)
            BubbleKind.LOCKED -> drawLockGlyph(canvas, centre, radius, paint)
            BubbleKind.STONE -> drawStoneGlyph(canvas, centre, radius, paint)
            BubbleKind.UNBREAKABLE -> drawWallGlyph(canvas, centre, radius, paint)
            BubbleKind.MOVING -> drawMovingGlyph(canvas, centre, radius, paint)
            else -> Unit
        }
        return bitmap
    }

    private fun drawBombGlyph(canvas: Canvas, centre: Float, radius: Float, paint: Paint) {
        paint.style = Paint.Style.FILL
        paint.color = 0xE6212233.toInt()
        canvas.drawCircle(centre, centre + radius * 0.12f, radius * 0.5f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1.5f, radius * 0.14f)
        paint.color = 0xFFEDEDF5.toInt()
        canvas.drawCircle(centre, centre + radius * 0.12f, radius * 0.5f, paint)
        // Fuse
        val fuse = Path()
        fuse.moveTo(centre + radius * 0.28f, centre - radius * 0.28f)
        fuse.quadTo(centre + radius * 0.62f, centre - radius * 0.5f, centre + radius * 0.5f, centre - radius * 0.72f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1.5f, radius * 0.12f)
        canvas.drawPath(fuse, paint)
        // Spark
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFE066.toInt()
        canvas.drawCircle(centre + radius * 0.5f, centre - radius * 0.75f, radius * 0.17f, paint)
    }

    private fun drawBoltGlyph(canvas: Canvas, centre: Float, radius: Float, paint: Paint) {
        val bolt = Path()
        bolt.moveTo(centre + radius * 0.22f, centre - radius * 0.72f)
        bolt.lineTo(centre - radius * 0.34f, centre + radius * 0.06f)
        bolt.lineTo(centre + radius * 0.0f, centre + radius * 0.06f)
        bolt.lineTo(centre - radius * 0.2f, centre + radius * 0.72f)
        bolt.lineTo(centre + radius * 0.36f, centre - radius * 0.08f)
        bolt.lineTo(centre + radius * 0.02f, centre - radius * 0.08f)
        bolt.close()

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(2f, radius * 0.16f)
        paint.color = 0xCC2B2B3C.toInt()
        canvas.drawPath(bolt, paint)

        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFF59D.toInt()
        canvas.drawPath(bolt, paint)
    }

    private fun drawFlameGlyph(canvas: Canvas, centre: Float, radius: Float, paint: Paint) {
        val flame = Path()
        flame.moveTo(centre, centre - radius * 0.78f)
        flame.cubicTo(
            centre + radius * 0.6f, centre - radius * 0.2f,
            centre + radius * 0.45f, centre + radius * 0.5f,
            centre, centre + radius * 0.7f
        )
        flame.cubicTo(
            centre - radius * 0.45f, centre + radius * 0.5f,
            centre - radius * 0.6f, centre - radius * 0.2f,
            centre, centre - radius * 0.78f
        )
        flame.close()

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(2f, radius * 0.16f)
        paint.color = 0xCC3A1900.toInt()
        canvas.drawPath(flame, paint)

        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFE082.toInt()
        canvas.drawPath(flame, paint)

        // Inner flame
        val inner = Path()
        inner.moveTo(centre, centre - radius * 0.34f)
        inner.cubicTo(
            centre + radius * 0.28f, centre + radius * 0.02f,
            centre + radius * 0.2f, centre + radius * 0.34f,
            centre, centre + radius * 0.46f
        )
        inner.cubicTo(
            centre - radius * 0.2f, centre + radius * 0.34f,
            centre - radius * 0.28f, centre + radius * 0.02f,
            centre, centre - radius * 0.34f
        )
        inner.close()
        paint.color = 0xFFFF7043.toInt()
        canvas.drawPath(inner, paint)
    }

    private fun drawLockGlyph(canvas: Canvas, centre: Float, radius: Float, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(2f, radius * 0.16f)
        paint.color = 0xFFEDEDF5.toInt()
        val shackle = RectF(
            centre - radius * 0.3f,
            centre - radius * 0.58f,
            centre + radius * 0.3f,
            centre + radius * 0.02f
        )
        canvas.drawArc(shackle, 180f, 180f, false, paint)

        paint.style = Paint.Style.FILL
        paint.color = 0xFF3B3B52.toInt()
        val body = RectF(
            centre - radius * 0.44f,
            centre - radius * 0.1f,
            centre + radius * 0.44f,
            centre + radius * 0.5f
        )
        canvas.drawRoundRect(body, radius * 0.14f, radius * 0.14f, paint)
        paint.color = 0xFFEDEDF5.toInt()
        canvas.drawCircle(centre, centre + radius * 0.18f, radius * 0.09f, paint)
    }

    private fun drawStoneGlyph(canvas: Canvas, centre: Float, radius: Float, paint: Paint) {
        paint.style = Paint.Style.FILL
        paint.color = 0x33000000
        canvas.drawCircle(centre - radius * 0.3f, centre + radius * 0.1f, radius * 0.16f, paint)
        canvas.drawCircle(centre + radius * 0.22f, centre - radius * 0.28f, radius * 0.12f, paint)
        canvas.drawCircle(centre + radius * 0.3f, centre + radius * 0.32f, radius * 0.1f, paint)
        paint.color = 0x33FFFFFF
        canvas.drawCircle(centre - radius * 0.35f, centre - radius * 0.3f, radius * 0.1f, paint)
    }

    private fun drawWallGlyph(canvas: Canvas, centre: Float, radius: Float, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, radius * 0.1f)
        paint.color = 0x66FFFFFF
        val hatch = Path()
        var offset = -radius
        while (offset <= radius) {
            hatch.moveTo(centre + offset, centre - radius * 0.8f)
            hatch.lineTo(centre + offset + radius * 1.6f, centre + radius * 0.8f)
            offset += radius * 0.5f
        }
        canvas.save()
        canvas.clipPath(
            Path().apply {
                addCircle(centre, centre, radius * 0.92f, Path.Direction.CW)
            }
        )
        canvas.drawPath(hatch, paint)
        canvas.restore()

        paint.style = Paint.Style.FILL
        paint.color = 0x88FFFFFF.toInt()
        canvas.drawCircle(centre - radius * 0.5f, centre - radius * 0.5f, radius * 0.1f, paint)
        canvas.drawCircle(centre + radius * 0.5f, centre + radius * 0.5f, radius * 0.1f, paint)
    }

    private fun drawMovingGlyph(canvas: Canvas, centre: Float, radius: Float, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1.5f, radius * 0.12f)
        paint.color = 0xCCFFFFFF.toInt()
        paint.strokeCap = Paint.Cap.ROUND

        val left = Path()
        left.moveTo(centre - radius * 0.12f, centre - radius * 0.42f)
        left.lineTo(centre - radius * 0.5f, centre)
        left.lineTo(centre - radius * 0.12f, centre + radius * 0.42f)
        canvas.drawPath(left, paint)

        val right = Path()
        right.moveTo(centre + radius * 0.12f, centre - radius * 0.42f)
        right.lineTo(centre + radius * 0.5f, centre)
        right.lineTo(centre + radius * 0.12f, centre + radius * 0.42f)
        canvas.drawPath(right, paint)
        paint.strokeCap = Paint.Cap.BUTT
    }

    /** Soft radial dot reused for particles and aim guide markers. */
    private fun buildParticle(radius: Float): Bitmap {
        val size = max(4, (radius * 1.6f).toInt())
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centre = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            centre, centre, centre,
            intArrayOf(0xFFFFFFFF.toInt(), 0x99FFFFFF.toInt(), 0x00FFFFFF),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centre, centre, centre * 0.98f, paint)
        return bitmap
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
}
