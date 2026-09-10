package com.infinitehits.bubbleblast.core.engine

import com.infinitehits.bubbleblast.core.level.LevelCatalog
import com.infinitehits.bubbleblast.core.level.LevelDefinition
import com.infinitehits.bubbleblast.core.model.Board
import com.infinitehits.bubbleblast.core.model.BoardGeometry
import com.infinitehits.bubbleblast.core.model.Bubble
import com.infinitehits.bubbleblast.core.model.BubbleColor
import com.infinitehits.bubbleblast.core.model.BubbleKind
import com.infinitehits.bubbleblast.core.model.BubbleState
import com.infinitehits.bubbleblast.core.model.Cell
import com.infinitehits.bubbleblast.core.model.Hex
import com.infinitehits.bubbleblast.core.powerup.PowerUp
import com.infinitehits.bubbleblast.core.score.ScoreSystem
import com.infinitehits.bubbleblast.core.score.StarRating
import com.infinitehits.bubbleblast.core.util.Rng
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The heart of Bubble Blast: board state, shooting, matching, cascades, scoring
 * and the win/lose rules. It never touches Android APIs, so the entire ruleset
 * is unit testable on the JVM while the renderer simply draws whatever state the
 * engine exposes.
 *
 * Frame flow
 * ----------
 * ```
 *  INTRO -> AIMING -> FLYING -> RESOLVING -> AIMING
 *                                   |-> LEVEL_COMPLETE
 *                                   |-> GAME_OVER
 * ```
 */
class GameEngine(
    val definition: LevelDefinition,
    private val geometry: BoardGeometry
) {

    enum class State { INTRO, AIMING, FLYING, RESOLVING, PAUSED, LEVEL_COMPLETE, GAME_OVER }

    /** The live board, drawn by the renderer. */
    val board = Board(geometry)

    var listener: EngineListener? = null

    /** Filled in by the activity so power-up usage can be validated against the inventory. */
    var powerUpInventory: Map<PowerUp, Int> = emptyMap()

    /** True when this level has never been cleared before (drives the first-clear bonus). */
    var isFirstClearAttempt: Boolean = true

    /** Best score for this level before this attempt, used for the "new best" badge. */
    var previousBestScore: Int = 0

    private val rng = Rng(definition.seed * 31L + 17L)
    private val shooter = Shooter()
    private val simulator = ShotSimulator(geometry, board)
    private val scoreSystem = ScoreSystem()

    /** Bubbles playing their pop animation. Owned by the engine, drawn by the view. */
    val poppingBubbles = ArrayList<Bubble>(16)

    /** Bubbles falling off the bottom of the screen. */
    val fallingBubbles = ArrayList<FallingBubble>(16)

    /** Ice bubbles playing their "shell cracked" animation. */
    val crackingBubbles = ArrayList<Bubble>(8)

    private val shots = ArrayList<Shot>(2)

    var state: State = State.INTRO
        private set

    /** Aim direction in radians from straight up; positive turns right. */
    var aimAngle: Float = 0f
        private set

    var aimActive: Boolean = false
        private set

    var moves: Int = 0
        private set

    var isPaused: Boolean = false
        private set

    /** True while the bomb power-up waits for a tap on the board. */
    var bombArmed: Boolean = false
        private set

    /** Remaining shots that get the extended aim guide. */
    var extendedAimShots: Int = 0
        private set

    private val tracePoints = ArrayList<Float>(128)

    /** Number of valid points in the aim guide. */
    var traceCount: Int = 0
        private set

    private var pauseState: State = State.AIMING
    private var introTimer = 0f
    private var resolveTimer = 0f
    private var shotsSinceDrop = 0
    private var outcomeFired = false
    private var boardCleared = false
    private var levelResult: LevelResult? = null
    private var gameOverResult: GameOverResult? = null
    private var lastStats: GameStats? = null

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /** Builds the board and starts the level. */
    fun start() {
        board.clear()
        scoreSystem.reset()
        poppingBubbles.clear()
        fallingBubbles.clear()
        crackingBubbles.clear()
        shots.clear()
        levelResult = null
        gameOverResult = null
        boardCleared = false
        outcomeFired = false
        bombArmed = false
        extendedAimShots = 0
        aimAngle = 0f
        aimActive = false
        moves = definition.moves
        shotsSinceDrop = 0
        introTimer = INTRO_DURATION
        resolveTimer = 0f
        state = State.INTRO
        isPaused = false
        lastStats = null

        val generated = LevelCatalog.board(definition.level)
        for (bubble in generated.bubbles) board.place(bubble)
        for (row in generated.slidingRows) board.markRowSliding(row)

        shooter.reset(definition.colors, definition.specialChance, definition.allowedSpecials, rng)
        shooter.syncWithBoard(board.activeColors())

        updateTrace()
        emitAmmo()
        emitStats(force = true)
        listener?.onBoardChanged()
    }

    fun restart() = start()

    fun pause() {
        if (isPaused || isOver) return
        pauseState = state
        state = State.PAUSED
        isPaused = true
        emitStats(force = true)
    }

    fun resume() {
        if (!isPaused) return
        isPaused = false
        state = pauseState
        emitStats(force = true)
    }

    val isOver: Boolean get() = state == State.LEVEL_COMPLETE || state == State.GAME_OVER

    val currentSpec: BubbleSpec get() = shooter.current

    val nextSpec: BubbleSpec get() = shooter.next

    val totalScore: Int get() = scoreSystem.score

    val combo: Int get() = scoreSystem.combo

    val bestCombo: Int get() = scoreSystem.bestCombo

    fun launcherX(): Float = geometry.viewWidth / 2f

    fun launcherY(): Float = max(
        geometry.dangerY + geometry.bubbleRadius * 1.4f,
        geometry.viewHeight - geometry.bubbleRadius * 2.4f
    )

    private fun maxBounces(): Int = if (extendedAimShots > 0) EXTENDED_BOUNCES else NORMAL_BOUNCES

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    fun beginAim() {
        if (state != State.AIMING) return
        aimActive = true
    }

    /** Points the launcher at a touch position. */
    fun aimAt(x: Float, y: Float) {
        if (state != State.AIMING) return
        aimActive = true
        setAimAngle(angleTo(x, y))
    }

    fun releaseAim() {
        if (state != State.AIMING) return
        aimActive = false
        fire()
    }

    fun cancelAim() {
        aimActive = false
    }

    /** Sets the aim directly (tests, accessibility, keyboard). */
    fun setAimAngle(angle: Float) {
        if (state != State.AIMING && state != State.INTRO) {
            aimAngle = angle.coerceIn(-MAX_AIM_ANGLE, MAX_AIM_ANGLE)
            return
        }
        val clamped = angle.coerceIn(-MAX_AIM_ANGLE, MAX_AIM_ANGLE)
        if (abs(clamped - aimAngle) > AIM_EPSILON) {
            aimAngle = clamped
            updateTrace()
        }
    }

    private fun angleTo(x: Float, y: Float): Float {
        val dx = x - launcherX()
        val dy = launcherY() - y
        if (dy <= 1f && abs(dx) < 1f) return aimAngle
        return kotlin.math.atan2(dx, dy.coerceAtLeast(1f))
    }

    /** Fires the loaded bubble. */
    fun fire() {
        if (state != State.AIMING) return
        val spec = shooter.current
        val speed = shotSpeed()

        shots.clear()
        shots.add(
            Shot(
                x = launcherX(),
                y = launcherY() - geometry.bubbleRadius * 0.6f,
                vx = sin(aimAngle) * speed,
                vy = -cos(aimAngle) * speed,
                color = spec.color,
                kind = spec.kind
            )
        )

        state = State.FLYING
        moves = (moves - 1).coerceAtLeast(0)
        shotsSinceDrop++
        listener?.onShotFired(spec.color, spec.kind)
        listener?.onVibrate(1)

        // The preview bubble slides into the launcher straight away - that is
        // what makes the shooting rhythm feel snappy.
        shooter.rotate(board.activeColors())
        emitAmmo()
        emitStats()
    }

    private fun shotSpeed(): Float = (geometry.viewHeight / SHOT_TRAVEL_SECONDS).coerceIn(600f, 3200f)

    /**
     * Tap on the play area. Returns true when the tap was consumed - currently
     * only the bomb power-up listens for taps.
     */
    fun tapAt(x: Float, y: Float): Boolean {
        if (state != State.AIMING || !bombArmed) return false
        val cell = board.cellAt(x, y)
        val bubble = board.bubbleAt(cell) ?: return false

        bombArmed = false
        listener?.onVibrate(3)
        listener?.onShake(0.7f)
        listener?.onSpecialTriggered(
            BubbleKind.BOMB,
            board.centerX(bubble.row, bubble.col),
            board.centerY(bubble.row)
        )
        blastArea(cell.row, cell.col, BOMB_POWERUP_RADIUS)
        updateTrace()
        emitStats()
        // A bomb can clear the board, so the win/lose rules must be re-checked.
        concludeLevel()
        return true
    }

    // ------------------------------------------------------------------
    // Power-ups
    // ------------------------------------------------------------------

    fun usePowerUp(powerUp: PowerUp): Boolean {
        if (state != State.AIMING) {
            listener?.onPowerUpRejected(powerUp)
            return false
        }
        val owned = powerUpInventory[powerUp] ?: 0
        if (owned <= 0) {
            listener?.onPowerUpRejected(powerUp)
            return false
        }

        when (powerUp) {
            PowerUp.AIM_EXTENSION -> extendedAimShots += PowerUp.AIM_EXTENSION_SHOTS
            PowerUp.COLOR_CHANGER -> {
                shooter.swap()
                emitAmmo()
            }
            PowerUp.BOMB -> bombArmed = true
            PowerUp.EXTRA_MOVES -> {
                moves += PowerUp.EXTRA_MOVES_AMOUNT
                emitStats()
            }
        }

        powerUpInventory = powerUpInventory.toMutableMap().apply { put(powerUp, owned - 1) }
        listener?.onPowerUpCountsChanged(powerUpInventory)
        listener?.onVibrate(1)
        updateTrace()
        return true
    }

    // ------------------------------------------------------------------
    // Frame update
    // ------------------------------------------------------------------

    /** Advances the simulation. [dt] is seconds since the previous frame. */
    fun update(dt: Float) {
        if (isPaused || isOver) return
        val step = dt.coerceIn(0f, MAX_FRAME_STEP)

        board.update(step)
        updatePopping(step)
        updateFalling(step)

        updateSpawning(step)

        when (state) {
            State.INTRO -> {
                introTimer -= step
                if (introTimer <= 0f) {
                    state = State.AIMING
                    updateTrace()
                    emitStats(force = true)
                }
            }
            State.FLYING -> updateShots(step)
            State.RESOLVING -> {
                resolveTimer -= step
                if (resolveTimer <= 0f) {
                    state = State.AIMING
                    finishResolution()
                }
            }
            else -> Unit
        }
    }

    private fun updateShots(dt: Float) {
        if (shots.isEmpty()) {
            state = State.RESOLVING
            resolveTimer = RESOLVE_TIME
            return
        }
        val shot = shots[0]
        when (val impact = simulator.advance(shot, dt)) {
            is ShotSimulator.Impact.BubbleHit -> {
                shots.clear()
                attach(shot, impact.bubble)
            }
            ShotSimulator.Impact.Ceiling -> {
                shots.clear()
                attach(shot, null)
            }
            else -> {
                if (shot.y > geometry.viewHeight + geometry.bubbleRadius * 4f) {
                    // Safety net: never leave a bubble flying forever.
                    shots.clear()
                    state = State.RESOLVING
                    resolveTimer = RESOLVE_TIME
                }
            }
        }
    }

    private fun updatePopping(dt: Float) {
        if (poppingBubbles.isNotEmpty()) {
            var i = 0
            while (i < poppingBubbles.size) {
                val bubble = poppingBubbles[i]
                bubble.animTime += dt
                if (bubble.animTime >= POP_DURATION) {
                    bubble.finished = true
                    poppingBubbles.removeAt(i)
                } else {
                    i++
                }
            }
        }
        if (crackingBubbles.isNotEmpty()) {
            var i = 0
            while (i < crackingBubbles.size) {
                val bubble = crackingBubbles[i]
                bubble.animTime += dt
                if (bubble.animTime >= CRACK_DURATION) {
                    bubble.state = BubbleState.IDLE
                    bubble.animTime = 0f
                    crackingBubbles.removeAt(i)
                } else {
                    i++
                }
            }
        }
    }

    /** Advances the little squash-and-stretch animation of freshly placed bubbles. */
    private fun updateSpawning(dt: Float) {
        board.forEachBubble { bubble ->
            if (bubble.state == BubbleState.SPAWNING) {
                bubble.animTime += dt
                if (bubble.animTime >= SPAWN_DURATION) {
                    bubble.animTime = 0f
                    bubble.state = BubbleState.IDLE
                }
            }
        }
    }

    private fun updateFalling(dt: Float) {
        if (fallingBubbles.isEmpty()) return
        val gravity = geometry.viewHeight * 2.6f
        var i = 0
        while (i < fallingBubbles.size) {
            val bubble = fallingBubbles[i]
            bubble.vy += gravity * dt
            bubble.x += bubble.vx * dt
            bubble.y += bubble.vy * dt
            bubble.rotation += bubble.rotationSpeed * dt
            if (bubble.y - geometry.bubbleRadius > geometry.viewHeight + geometry.bubbleRadius) {
                fallingBubbles.removeAt(i)
            } else {
                i++
            }
        }
    }

    // ------------------------------------------------------------------
    // Shot resolution
    // ------------------------------------------------------------------

    private fun attach(shot: Shot, hit: Bubble?) {
        if (extendedAimShots > 0) extendedAimShots--

        val cell = if (hit != null) chooseAttachCell(hit, shot) else chooseCeilingCell(shot)
        if (cell == null) {
            state = State.RESOLVING
            resolveTimer = RESOLVE_TIME
            return
        }

        val placed = Bubble(
            row = cell.row,
            col = cell.col,
            color = shot.color,
            kind = shot.kind,
            state = BubbleState.SPAWNING
        )
        board.place(placed)

        // A rainbow bubble adopts the colour of the group it landed next to,
        // which is exactly what makes it match with anything.
        if (shot.kind == BubbleKind.RAINBOW) {
            placed.color = assimilateColor(cell)
        }

        val group = board.findMatchGroup(placed)
        if (group.size >= ScoreSystem.MIN_MATCH) {
            scoreSystem.registerCombo()
            popGroup(group, placed)
        } else {
            scoreSystem.resetCombo()
            listener?.onComboChanged(0, 0f, 0f)
        }

        listener?.onBoardChanged()
        state = State.RESOLVING
        resolveTimer = RESOLVE_TIME
        emitStats()
    }

    /** Free cell next to the bubble the shot touched, closest to the shot itself. */
    private fun chooseAttachCell(hit: Bubble, shot: Shot): Cell? {
        var best: Cell? = null
        var bestDistance = Float.MAX_VALUE
        for (cell in Hex.neighborsOf(hit.row, hit.col)) {
            if (!board.isEmptyCell(cell)) continue
            val dx = board.centerX(cell.row, cell.col) - shot.x
            val dy = board.centerY(cell.row) - shot.y
            val distance = dx * dx + dy * dy
            if (distance < bestDistance) {
                bestDistance = distance
                best = cell
            }
        }
        return best ?: board.nearestFreeCell(shot.x, shot.y, hit.row - 1, hit.row + 1)
    }

    /** The shot reached the ceiling: hang it from the top row. */
    private fun chooseCeilingCell(shot: Shot): Cell? {
        val row = board.topRow
        val columns = Hex.columnCount(row)
        val preferred = geometry.nearestColumn(row, shot.x, board.slideOffset(row))
        for (offset in 0 until columns) {
            val left = preferred - offset
            if (left >= 0 && board.isEmptyCell(row, left)) return Cell(row, left)
            val right = preferred + offset
            if (offset != 0 && right < columns && board.isEmptyCell(row, right)) return Cell(row, right)
        }
        return board.nearestFreeCell(shot.x, shot.y, row + 1, row + 2)
    }

    /** Majority colour around [cell] - used by the rainbow bubble. */
    private fun assimilateColor(cell: Cell): BubbleColor {
        val counts = HashMap<BubbleColor, Int>()
        for (neighbour in Hex.neighborsOf(cell.row, cell.col)) {
            val bubble = board.bubbleAt(neighbour) ?: continue
            if (!bubble.color.playable) continue
            counts[bubble.color] = (counts[bubble.color] ?: 0) + 1
        }
        counts.entries.maxByOrNull { it.value }?.let { return it.key }
        val active = board.activeColors()
        return if (active.isEmpty()) BubbleColor.PLAYABLE[rng.nextInt(BubbleColor.COUNT)]
        else active[rng.nextInt(active.size)]
    }

    /** Pops a match group and runs every cascade it sets off. */
    private fun popGroup(group: List<Bubble>, origin: Bubble) {
        val destroyed = ArrayList<Bubble>(group.size + 8)
        val pendingSpecials = ArrayDeque<Bubble>()
        var order = 0

        for (bubble in group) {
            if (bubble.kind == BubbleKind.ICE && bubble.iceLayers > 0) {
                crackIce(bubble)
                continue
            }
            destroy(bubble, destroyed, pendingSpecials, order++)
        }

        runCascades(pendingSpecials)
        unlockNeighbours(destroyed)

        val points = scoreSystem.registerPop(destroyed.size, destroyed.count { it.kind.isSpecial })
        if (points > 0) {
            listener?.onPopup(PopupKind.SCORE, points, board.centerX(origin.row, origin.col), board.centerY(origin.row))
        }
        val perfect = scoreSystem.registerPerfectShot(destroyed.size)
        if (perfect > 0) {
            listener?.onPopup(PopupKind.PERFECT, perfect, board.centerX(origin.row, origin.col), board.centerY(origin.row))
            listener?.onShake(0.5f)
        }
        if (scoreSystem.combo > 1) {
            listener?.onComboChanged(scoreSystem.combo, board.centerX(origin.row, origin.col), board.centerY(origin.row))
        }
        listener?.onVibrate(if (destroyed.size >= 5) 2 else 1)
        if (destroyed.size >= 4) listener?.onShake(0.35f)

        syncFalling()
    }

    private fun runCascades(pendingSpecials: ArrayDeque<Bubble>) {
        var guard = 0
        while (pendingSpecials.isNotEmpty() && guard++ < MAX_CHAIN) {
            triggerSpecial(pendingSpecials.removeFirst())
        }
    }

    /** Removes one bubble, plays its animation and queues its special effect. */
    private fun destroy(bubble: Bubble, destroyed: MutableList<Bubble>, pendingSpecials: ArrayDeque<Bubble>, order: Int) {
        if (!bubble.kind.breakable) return
        crackingBubbles.remove(bubble)
        val x = board.centerX(bubble.row, bubble.col)
        val y = board.centerY(bubble.row)
        board.remove(bubble)
        bubble.state = BubbleState.POPPING
        bubble.animTime = -order * POP_STAGGER
        poppingBubbles.add(bubble)
        destroyed.add(bubble)
        listener?.onBubblePopped(x, y, bubble.color, bubble.kind)
        if (bubble.kind.isSpecial && bubble.kind != BubbleKind.RAINBOW) {
            pendingSpecials.add(bubble)
        }
    }

    private fun triggerSpecial(special: Bubble) {
        listener?.onSpecialTriggered(
            special.kind,
            board.centerX(special.row, special.col),
            board.centerY(special.row)
        )
        listener?.onVibrate(2)
        listener?.onShake(0.6f)

        when (special.kind) {
            BubbleKind.BOMB -> blastArea(special.row, special.col, BOMB_RADIUS)
            BubbleKind.FIRE -> blastArea(special.row, special.col, FIRE_RADIUS)
            BubbleKind.LIGHTNING -> clearRow(special.row)
            else -> Unit
        }
    }

    /** Destroys everything inside a circular blast (bombs, fire, bomb power-up). */
    private fun blastArea(row: Int, col: Int, radiusInCells: Float) {
        val victims = board.bubblesInRadius(row, col, radiusInCells)
        if (victims.isEmpty()) {
            syncFalling()
            return
        }
        val destroyed = ArrayList<Bubble>(victims.size)
        val pendingSpecials = ArrayDeque<Bubble>()
        var order = 0
        for (bubble in victims) destroy(bubble, destroyed, pendingSpecials, order++)
        runCascades(pendingSpecials)
        unlockNeighbours(destroyed)

        val points = scoreSystem.registerPop(destroyed.size, destroyed.count { it.kind.isSpecial })
        if (points > 0) {
            listener?.onPopup(
                PopupKind.SPECIAL,
                points,
                geometry.cellCenterX(row, col),
                geometry.cellCenterY(row, board.topRow)
            )
        }
        syncFalling()
    }

    /** The lightning bubble wipes its own row. */
    private fun clearRow(row: Int) {
        val rowBubbles = board.clearRow(row)
        if (rowBubbles.isEmpty()) {
            syncFalling()
            return
        }
        val destroyed = ArrayList<Bubble>(rowBubbles.size)
        val pendingSpecials = ArrayDeque<Bubble>()
        var order = 0
        for (bubble in rowBubbles) {
            val x = board.centerX(bubble.row, bubble.col)
            val y = board.centerY(bubble.row)
            bubble.state = BubbleState.POPPING
            bubble.animTime = -order * POP_STAGGER
            order++
            poppingBubbles.add(bubble)
            destroyed.add(bubble)
            listener?.onBubblePopped(x, y, bubble.color, bubble.kind)
            if (bubble.kind.isSpecial && bubble.kind != BubbleKind.RAINBOW) pendingSpecials.add(bubble)
        }
        runCascades(pendingSpecials)
        unlockNeighbours(destroyed)

        val points = scoreSystem.registerPop(destroyed.size, destroyed.count { it.kind.isSpecial })
        if (points > 0) {
            listener?.onPopup(PopupKind.SPECIAL, points, geometry.viewWidth / 2f, geometry.cellCenterY(row, board.topRow))
        }
        syncFalling()
    }

    /** Ice absorbs a match instead of popping. */
    private fun crackIce(bubble: Bubble) {
        bubble.iceLayers--
        bubble.animTime = 0f
        bubble.state = BubbleState.CRACKING
        if (bubble !in crackingBubbles) crackingBubbles.add(bubble)
        if (bubble.iceLayers <= 0) bubble.kind = BubbleKind.NORMAL
        listener?.onVibrate(1)
        listener?.onPopup(
            PopupKind.BONUS,
            scoreSystem.registerBonus(ICE_CRACK_POINTS),
            board.centerX(bubble.row, bubble.col),
            board.centerY(bubble.row)
        )
    }

    /** Locked bubbles are freed as soon as a neighbour pops. */
    private fun unlockNeighbours(destroyed: List<Bubble>) {
        for (bubble in destroyed) {
            for (neighbour in Hex.neighborsOf(bubble.row, bubble.col)) {
                val other = board.bubbleAt(neighbour) ?: continue
                if (other.kind == BubbleKind.LOCKED) {
                    other.kind = BubbleKind.NORMAL
                    other.state = BubbleState.SPAWNING
                    other.animTime = 0f
                    listener?.onVibrate(1)
                }
            }
        }
    }

    /** Detaches bubbles that lost their anchor and starts their fall. */
    private fun syncFalling() {
        val floating = board.findFloating()
        if (floating.isEmpty()) return
        val points = scoreSystem.registerDrop(floating.size)
        for (bubble in floating) {
            val x = board.centerX(bubble.row, bubble.col)
            val y = board.centerY(bubble.row)
            board.remove(bubble)
            fallingBubbles.add(
                FallingBubble(
                    x = x,
                    y = y,
                    vx = (rng.nextFloat() - 0.5f) * geometry.viewWidth * 0.35f,
                    vy = -geometry.viewHeight * 0.06f * rng.nextFloat(),
                    rotation = 0f,
                    rotationSpeed = (rng.nextFloat() - 0.5f) * 6f,
                    color = bubble.color,
                    kind = bubble.kind
                )
            )
            listener?.onBubblePopped(x, y, bubble.color, bubble.kind)
        }
        listener?.onClusterDropped(floating.size, points)
        listener?.onPopup(PopupKind.DROP, points, geometry.viewWidth / 2f, geometry.dangerY * 0.7f)
        listener?.onVibrate(if (floating.size >= 4) 2 else 1)
        if (floating.size >= 4) listener?.onShake(0.5f)
    }

    /** Pushes fresh rows in from the ceiling. */
    private fun pushRows(count: Int) {
        repeat(count.coerceIn(1, MAX_DROP_ROWS)) {
            val row = board.topRow - 1
            val columns = Hex.columnCount(row)
            val colors = board.activeColors().ifEmpty { definition.colors }
            val bubbles = ArrayList<Bubble>(columns)
            for (col in 0 until columns) {
                if (rng.nextFloat() <= DROP_FILL_RATIO && colors.isNotEmpty()) {
                    bubbles.add(
                        Bubble(
                            row = row,
                            col = col,
                            color = colors[rng.nextInt(colors.size)],
                            kind = BubbleKind.NORMAL
                        )
                    )
                }
            }
            board.insertTopRow(bubbles, animated = true)
        }
        listener?.onRowInserted()
        listener?.onShake(0.4f)
        listener?.onVibrate(2)
        listener?.onBoardChanged()
    }

    // ------------------------------------------------------------------
    // Win / lose
    // ------------------------------------------------------------------

    private fun finishResolution() {
        updateTrace()
        emitStats()

        // Fresh rows arrive after the shot resolved but before the outcome is
        // decided, so a drop can push the player over the danger line.
        if (definition.hasDrops && shotsSinceDrop >= definition.shotsBeforeDrop) {
            shotsSinceDrop = 0
            pushRows(definition.dropRowCount)
        }
        concludeLevel()
    }

    private fun concludeLevel() {
        if (outcomeFired || isOver) return

        val cleared = board.countDestructible() == 0
        val reachedTarget = scoreSystem.score >= definition.targetScore

        if (cleared || reachedTarget) {
            outcomeFired = true
            boardCleared = cleared
            if (cleared) {
                scoreSystem.registerBoardClear()
                listener?.onPopup(
                    PopupKind.BONUS,
                    ScoreSystem.BOARD_CLEAR_BONUS,
                    geometry.viewWidth / 2f,
                    geometry.dangerY * 0.45f
                )
            }
            val spareMovePoints = scoreSystem.registerRemainingMoves(moves)
            if (spareMovePoints > 0) {
                listener?.onPopup(PopupKind.BONUS, spareMovePoints, geometry.viewWidth / 2f, geometry.dangerY * 0.6f)
            }
            emitStats(force = true)
            publishLevelComplete()
            return
        }

        if (moves <= 0) {
            outcomeFired = true
            finishWithLoss(GameOverReason.OUT_OF_MOVES)
            return
        }

        if (board.crossedDangerLine()) {
            outcomeFired = true
            finishWithLoss(GameOverReason.DANGER_LINE)
            return
        }

        state = State.AIMING
        updateTrace()
        emitStats(force = true)
    }

    private fun publishLevelComplete() {
        val stars = StarRating.starsFor(definition, scoreSystem.score, boardCleared)
        val coins = StarRating.coinsFor(stars, scoreSystem.score, isFirstClearAttempt)
        val result = LevelResult(
            level = definition.level,
            score = scoreSystem.score,
            targetScore = definition.targetScore,
            stars = stars,
            coinsEarned = coins,
            movesLeft = moves,
            bestCombo = scoreSystem.bestCombo,
            boardCleared = boardCleared,
            isNewBest = scoreSystem.score > previousBestScore,
            firstClear = isFirstClearAttempt,
            popped = scoreSystem.poppedCount,
            dropped = scoreSystem.droppedCount
        )
        levelResult = result
        state = State.LEVEL_COMPLETE
        listener?.onLevelComplete(result)
    }

    private fun finishWithLoss(reason: GameOverReason) {
        val result = GameOverResult(
            level = definition.level,
            score = scoreSystem.score,
            bestScore = max(scoreSystem.score, previousBestScore),
            reason = reason,
            isNewBest = scoreSystem.score > previousBestScore
        )
        gameOverResult = result
        state = State.GAME_OVER
        listener?.onGameOver(result)
    }

    fun levelResultOrNull(): LevelResult? = levelResult

    fun gameOverResultOrNull(): GameOverResult? = gameOverResult

    /** Rewarded-video continue: hands out extra moves and resumes play. */
    fun continueWithExtraMoves(extraMoves: Int = CONTINUE_MOVES): Boolean {
        if (state != State.GAME_OVER) return false
        outcomeFired = false
        gameOverResult = null
        moves += extraMoves
        state = State.AIMING
        updateTrace()
        emitStats(force = true)
        return true
    }

    // ------------------------------------------------------------------
    // Aim guide
    // ------------------------------------------------------------------

    private fun updateTrace() {
        tracePoints.clear()
        traceCount = 0
        if (isPaused || isOver || state == State.FLYING) return

        val trace = simulator.trace(
            startX = launcherX(),
            startY = launcherY() - geometry.bubbleRadius * 0.6f,
            angle = aimAngle,
            speed = shotSpeed(),
            maxBounces = maxBounces()
        )
        tracePoints.addAll(trace.points)
        traceCount = trace.pointCount
    }

    /** The bubble(s) currently in flight - normally zero or one. */
    fun snapshotShots(): List<Shot> = shots

    fun tracePointX(index: Int): Float = tracePoints[index * 2]

    fun tracePointY(index: Int): Float = tracePoints[index * 2 + 1]

    // ------------------------------------------------------------------
    // Listener plumbing
    // ------------------------------------------------------------------

    private fun emitStats(force: Boolean = false) {
        val stats = GameStats(
            level = definition.level,
            score = scoreSystem.score,
            targetScore = definition.targetScore,
            moves = moves,
            combo = scoreSystem.combo,
            bestCombo = scoreSystem.bestCombo,
            state = state
        )
        if (force || stats != lastStats) {
            lastStats = stats
            listener?.onStatsChanged(stats)
        }
    }

    private fun emitAmmo() {
        listener?.onAmmoChanged(shooter.current, shooter.next)
    }

    companion object {
        /** Aim clamp: 78 degrees either side of straight up. */
        const val MAX_AIM_ANGLE = 1.3613f

        private const val INTRO_DURATION = 0.45f
        private const val RESOLVE_TIME = 0.42f
        /** How long a popped bubble takes to fade out. Shared with the renderer. */
        const val POP_DURATION = 0.34f
        private const val CRACK_DURATION = 0.25f
        private const val SPAWN_DURATION = 0.18f
        private const val POP_STAGGER = 0.035f
        private const val MAX_FRAME_STEP = 1f / 30f
        private const val MAX_CHAIN = 64
        private const val MAX_DROP_ROWS = 3
        private const val BOMB_RADIUS = 1.75f
        private const val FIRE_RADIUS = 1.25f
        private const val BOMB_POWERUP_RADIUS = 1.6f
        private const val DROP_FILL_RATIO = 0.9f
        private const val CONTINUE_MOVES = 5
        private const val ICE_CRACK_POINTS = 5
        private const val AIM_EPSILON = 0.0015f
        private const val NORMAL_BOUNCES = 1
        private const val EXTENDED_BOUNCES = 3
        private const val SHOT_TRAVEL_SECONDS = 0.62f

        init {
            require(BOMB_RADIUS > FIRE_RADIUS) { "bomb blast must be bigger than fire" }
        }
    }
}
