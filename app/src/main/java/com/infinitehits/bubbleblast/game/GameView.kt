package com.infinitehits.bubbleblast.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.infinitehits.bubbleblast.AppServices
import com.infinitehits.bubbleblast.audio.AudioManager
import com.infinitehits.bubbleblast.core.engine.BubbleSpec
import com.infinitehits.bubbleblast.core.engine.EngineListener
import com.infinitehits.bubbleblast.core.engine.GameOverResult
import com.infinitehits.bubbleblast.core.engine.GameStats
import com.infinitehits.bubbleblast.core.engine.LevelResult
import com.infinitehits.bubbleblast.core.engine.PopupKind
import com.infinitehits.bubbleblast.core.engine.GameEngine
import com.infinitehits.bubbleblast.core.level.LevelDefinition
import com.infinitehits.bubbleblast.core.model.BoardGeometry
import com.infinitehits.bubbleblast.core.model.BubbleColor
import com.infinitehits.bubbleblast.core.model.BubbleKind
import com.infinitehits.bubbleblast.core.powerup.PowerUp
import com.infinitehits.bubbleblast.game.render.BoardRenderer
import com.infinitehits.bubbleblast.game.render.BubblePainter
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max
import kotlin.math.sin

/**
 * The playable surface.
 *
 * Runs the whole game loop on a dedicated render thread (input and UI events are
 * pushed through a lock-free command queue) so the simulation is never blocked by
 * layout or animations running on the UI thread - and the UI thread is never
 * blocked by the simulation.
 */
class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable, EngineListener {

    /** HUD level callbacks, always delivered on the main thread. */
    interface Listener {
        fun onStatsChanged(stats: GameStats)
        fun onAmmoChanged(current: BubbleSpec, next: BubbleSpec)
        fun onLevelComplete(result: LevelResult)
        fun onGameOver(result: GameOverResult)
        fun onPowerUpCountsChanged(counts: Map<PowerUp, Int>)
        fun onPowerUpRejected(powerUp: PowerUp)
        fun onEngineReady()
    }

    var listener: Listener? = null

    /** Mirrors [com.infinitehits.bubbleblast.data.SettingsManager.showTrajectory]. */
    var showTrajectory: Boolean = true

    private var services: AppServices? = null

    // ---- render thread state -----------------------------------------
    private val painter = BubblePainter()
    private val renderer = BoardRenderer(painter)
    private val particles = ParticleSystem()

    @Volatile
    private var running = false
    private var renderThread: Thread? = null
    private var needsResize = true

    private val geometry = BoardGeometry()

    @Volatile
    private var engine: GameEngine? = null
    private var pendingDefinition: LevelDefinition? = null
    private var inventory: Map<PowerUp, Int> = emptyMap()
    private var firstClear = true
    private var previousBest = 0

    private var shake = 0f
    private var shakePhase = 0f
    private var frameCount = 0

    private val commands = ConcurrentLinkedQueue<Command>()

    /** True while the activity is in the background. */
    @Volatile
    private var foreground = false

    /** True while the pause dialog is open. */
    @Volatile
    private var pausedByUser = false

    private val paused: Boolean get() = !foreground || pausedByUser

    // ------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------

    init {
        holder.addCallback(this)
        isFocusable = true
        keepScreenOn = true
    }

    fun attachServices(services: AppServices) {
        this.services = services
    }

    /** Starts (or restarts) a level. Safe to call from the main thread. */
    fun startLevel(
        definition: LevelDefinition,
        inventory: Map<PowerUp, Int>,
        firstClearAttempt: Boolean,
        previousBestScore: Int
    ) {
        this.inventory = inventory
        this.firstClear = firstClearAttempt
        this.previousBest = previousBestScore
        commands.add(Command.Start(definition))
        foreground = true
    }

    fun setUserPaused(paused: Boolean) {
        pausedByUser = paused
        commands.add(if (paused) Command.Pause else Command.Resume)
    }

    /**
     * Called from the activity lifecycle. Rendering stops while the app is in the
     * background; bitmaps are only freed once the render thread has really
     * stopped (see [onDetachedFromWindow]) so a frame in flight can never touch a
     * freed bitmap.
     */
    fun onForeground(foreground: Boolean) {
        this.foreground = foreground
        needsResize = true
    }

    fun usePowerUp(powerUp: PowerUp) {
        commands.add(Command.UsePowerUp(powerUp))
    }

    fun restartLevel() {
        commands.add(Command.Restart)
    }

    fun continueAfterGameOver() {
        commands.add(Command.ContinueAfterGameOver)
    }

    fun updateInventory(counts: Map<PowerUp, Int>) {
        commands.add(Command.Inventory(counts))
    }

    // ------------------------------------------------------------------
    // Surface lifecycle
    // ------------------------------------------------------------------

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        needsResize = true
        if (renderThread == null) {
            renderThread = Thread(this, "BubbleBlast-Render").also { it.start() }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        needsResize = true
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        val thread = renderThread
        renderThread = null
        thread?.join(SURFACE_JOIN_TIMEOUT_MS)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        needsResize = true
    }

    override fun onDetachedFromWindow() {
        running = false
        renderThread?.join(SURFACE_JOIN_TIMEOUT_MS)
        renderThread = null
        // The thread has stopped, so the cached bitmaps are safe to drop now.
        painter.release()
        renderer.release()
        super.onDetachedFromWindow()
    }

    // ------------------------------------------------------------------
    // Render loop
    // ------------------------------------------------------------------

    override fun run() {
        var lastFrame = System.nanoTime()
        while (running) {
            val frameStart = System.nanoTime()
            var dt = (frameStart - lastFrame) / 1_000_000_000f
            lastFrame = frameStart
            if (dt > MAX_DELTA) dt = MAX_DELTA

            processCommands()

            val active = engine
            if (active != null && !paused) {
                active.update(dt)
            }
            if (!paused) {
                particles.update(dt, geometry.viewWidth.coerceAtLeast(1f) * 0.5f)
                renderer.update(dt)
                updateShake(dt)
            }

            if (needsResize) {
                needsResize = false
                performResize()
            }

            val canvas = try {
                holder.lockCanvas()
            } catch (error: IllegalStateException) {
                null
            }
            if (canvas != null) {
                try {
                    drawFrame(canvas, dt)
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
            frameCount++

            // Frame pacing: cap at 60 FPS so low-end devices are not run hot.
            val elapsed = System.nanoTime() - frameStart
            val remaining = FRAME_NANOS - elapsed
            if (remaining > 0L) {
                try {
                    Thread.sleep(remaining / 1_000_000L, (remaining % 1_000_000L).toInt())
                } catch (ignored: InterruptedException) {
                    Thread.currentThread().interrupt()
                    running = false
                }
            }
        }
    }

    private fun processCommands() {
        while (true) {
            val command = commands.poll() ?: return
            when (command) {
                is Command.Start -> {
                    // A Start always means "play this level from the beginning":
                    // it arrives for the first level, for Next Level, for a replay
                    // and for a restart from the pause dialog. Building a fresh
                    // engine is the cheapest correct way to reset every counter.
                    engine = null
                    pendingDefinition = command.definition
                    ensureEngine()
                }
                Command.Pause -> engine?.pause()
                Command.Resume -> engine?.resume()
                Command.Restart -> engine?.restart()
                Command.ContinueAfterGameOver -> engine?.continueWithExtraMoves()
                is Command.UsePowerUp -> engine?.usePowerUp(command.powerUp)
                is Command.Inventory -> {
                    inventory = command.counts
                    engine?.powerUpInventory = command.counts
                }
                is Command.Aim -> engine?.aimAt(command.x, command.y)
                Command.BeginAim -> engine?.beginAim()
                Command.ReleaseAim -> engine?.releaseAim()
                is Command.Tap -> engine?.tapAt(command.x, command.y)
            }
        }
    }

    /** Creates the engine once both the size and the level definition are known. */
    private fun ensureEngine() {
        val definition = pendingDefinition ?: return
        if (geometry.viewWidth <= 0f || geometry.viewHeight <= 0f) return
        if (engine == null) {
            val created = GameEngine(definition, geometry)
            created.listener = this
            created.powerUpInventory = inventory
            engine = created
            created.isFirstClearAttempt = firstClear
            created.previousBestScore = previousBest
            created.start()
            post { listener?.onEngineReady() }
        }
    }

    private fun performResize() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val definition = pendingDefinition ?: engine?.definition
        geometry.resize(
            width = w,
            height = h,
            horizontalPadding = max(2f, w * 0.012f),
            maxRows = definition?.maxRowsVisible ?: 10,
            dangerExtraRows = definition?.dangerExtraRows ?: 0
        )
        painter.configure(geometry.bubbleRadius, resources.displayMetrics.density)
        renderer.resize(width, height, geometry)
        ensureEngine()
    }

    private fun updateShake(dt: Float) {
        if (shake <= 0f) return
        shake = (shake - dt * SHAKE_DECAY).coerceAtLeast(0f)
        shakePhase += dt * SHAKE_FREQUENCY
    }

    private fun drawFrame(canvas: Canvas, dt: Float) {
        canvas.drawColor(BACKGROUND_COLOR)
        val active = engine
        if (active == null) {
            renderer.drawBackground(canvas, geometry)
            return
        }

        val save = canvas.save()
        if (shake > 0f) {
            val amplitude = shake * geometry.bubbleRadius * SHAKE_AMPLITUDE
            canvas.translate(sin(shakePhase) * amplitude, sin(shakePhase * 1.7f + 1f) * amplitude * 0.6f)
        }

        renderer.drawBackground(canvas, geometry)
        renderer.drawBoard(canvas, active, geometry)

        if (active.bombArmed) {
            // The bomb power-up is waiting for a tap: flash the board border.
            renderer.drawArmedOverlay(canvas, geometry)
        }

        renderer.drawEffects(canvas, active, geometry, particles)

        if (!active.isOver && active.state != GameEngine.State.FLYING) {
            renderer.drawAimGuide(canvas, active, geometry, showTrajectory)
        }

        renderer.drawLauncher(
            canvas,
            active,
            geometry,
            active.currentSpec.color,
            active.currentSpec.kind
        )
        renderer.drawCelebration(canvas)
        renderer.drawPopups(canvas)
        canvas.restoreToCount(save)
    }

    // ------------------------------------------------------------------
    // Touch input
    // ------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val active = engine ?: return false
        if (paused || active.isOver) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (active.bombArmed) {
                    commands.add(Command.Tap(event.x, event.y))
                } else {
                    commands.add(Command.BeginAim)
                    commands.add(Command.Aim(event.x, event.y))
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!active.bombArmed) commands.add(Command.Aim(event.x, event.y))
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!active.bombArmed) commands.add(Command.ReleaseAim)
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    // ------------------------------------------------------------------
    // EngineListener - runs on the render thread
    // ------------------------------------------------------------------

    override fun onStatsChanged(stats: GameStats) {
        post { listener?.onStatsChanged(stats) }
    }

    override fun onAmmoChanged(current: BubbleSpec, next: BubbleSpec) {
        post { listener?.onAmmoChanged(current, next) }
    }

    override fun onShotFired(color: BubbleColor, kind: BubbleKind) {
        services?.audio?.play(AudioManager.Sfx.SHOOT)
    }

    override fun onBubblePopped(x: Float, y: Float, color: BubbleColor, kind: BubbleKind) {
        val strength = if (kind.isSpecial) 1.6f else 1f
        particles.burst(
            originX = x,
            originY = y,
            bubbleColor = color,
            particleCount = if (kind.isSpecial) 16 else 9,
            strength = strength,
            baseSize = geometry.bubbleRadius * 0.5f
        )
    }

    override fun onClusterDropped(count: Int, points: Int) {
        services?.audio?.play(AudioManager.Sfx.DROP)
        if (count >= 4) {
            services?.audio?.play(AudioManager.Sfx.COMBO, 0.85f, 0.6f)
        }
    }

    override fun onSpecialTriggered(kind: BubbleKind, x: Float, y: Float) {
        shake = max(shake, 0.85f)
        particles.sparkle(x, y, 0xFFFFE082.toInt(), particleCount = 20, baseSize = geometry.bubbleRadius * 0.6f)
    }

    override fun onPopup(kind: PopupKind, value: Int, x: Float, y: Float) {
        if (value <= 0) return
        val text = when (kind) {
            PopupKind.SCORE -> "+$value"
            PopupKind.DROP -> "DROP +$value"
            PopupKind.PERFECT -> "PERFECT +$value"
            PopupKind.SPECIAL -> "BLAST +$value"
            PopupKind.BONUS -> "+$value"
        }
        val color = when (kind) {
            PopupKind.DROP -> 0xFF4DE1FF.toInt()
            PopupKind.PERFECT -> 0xFFFFC629.toInt()
            PopupKind.SPECIAL -> 0xFFFF7A2F.toInt()
            PopupKind.BONUS -> 0xFF38D96B.toInt()
            PopupKind.SCORE -> Color.WHITE
        }
        renderer.addPopup(text, x, y, color, geometry.bubbleRadius * 0.78f)
    }

    override fun onComboChanged(combo: Int, x: Float, y: Float) {
        if (combo <= 1) return
        services?.audio?.play(AudioManager.Sfx.COMBO, (1f + combo * 0.06f).coerceAtMost(1.8f))
        renderer.addPopup(
            "COMBO x$combo",
            if (x > 0f) x else geometry.viewWidth / 2f,
            if (y > 0f) y else geometry.dangerY * 0.6f,
            0xFF4DE1FF.toInt(),
            geometry.bubbleRadius * 0.9f
        )
    }

    override fun onBoardChanged() {
        // Nothing visual to do: the renderer reads the board directly. Kept for
        // symmetry with the engine's contract and future cached guides.
    }

    override fun onRowInserted() {
        services?.audio?.play(AudioManager.Sfx.DROP, 0.8f)
    }

    override fun onShake(intensity: Float) {
        shake = max(shake, intensity.coerceIn(0f, 1f))
        if (shake > 0.05f) shakePhase = 0f
    }

    override fun onVibrate(strength: Int) {
        services?.haptics?.vibrate(strength)
    }

    override fun onPowerUpCountsChanged(counts: Map<PowerUp, Int>) {
        inventory = counts
        post { listener?.onPowerUpCountsChanged(counts) }
    }

    override fun onPowerUpRejected(powerUp: PowerUp) {
        post { listener?.onPowerUpRejected(powerUp) }
    }

    override fun onLevelComplete(result: LevelResult) {
        services?.audio?.play(AudioManager.Sfx.LEVEL_COMPLETE)
        renderer.celebrate(geometry.viewWidth / 2f, geometry.dangerY * 0.45f)
        particles.sparkle(
            geometry.viewWidth / 2f,
            geometry.dangerY * 0.5f,
            0xFFFFC629.toInt(),
            particleCount = 40,
            baseSize = geometry.bubbleRadius * 0.7f
        )
        post { listener?.onLevelComplete(result) }
    }

    override fun onGameOver(result: GameOverResult) {
        services?.audio?.play(AudioManager.Sfx.GAME_OVER)
        shake = 1f
        post { listener?.onGameOver(result) }
    }

    /** Frame counter used by the debug overlay / tests. */
    val renderedFrames: Int get() = frameCount

    val isEngineReady: Boolean get() = engine != null

    val engineState: GameEngine.State? get() = engine?.state

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    private sealed interface Command {
        data class Start(val definition: LevelDefinition) : Command
        data class Aim(val x: Float, val y: Float) : Command
        data class Tap(val x: Float, val y: Float) : Command
        data class UsePowerUp(val powerUp: PowerUp) : Command
        data class Inventory(val counts: Map<PowerUp, Int>) : Command
        data object BeginAim : Command
        data object ReleaseAim : Command
        data object Pause : Command
        data object Resume : Command
        data object Restart : Command
        data object ContinueAfterGameOver : Command
    }

    companion object {
        private const val BACKGROUND_COLOR = 0xFF160C33.toInt()
        private const val FRAME_NANOS = 1_000_000_000L / 60L
        private const val MAX_DELTA = 1f / 20f
        private const val SURFACE_JOIN_TIMEOUT_MS = 1500L
        private const val SHAKE_DECAY = 2.6f
        private const val SHAKE_FREQUENCY = 42f
        private const val SHAKE_AMPLITUDE = 0.35f
    }
}
