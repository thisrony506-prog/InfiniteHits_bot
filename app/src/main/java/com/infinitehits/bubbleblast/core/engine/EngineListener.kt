package com.infinitehits.bubbleblast.core.engine

import com.infinitehits.bubbleblast.core.model.BubbleColor
import com.infinitehits.bubbleblast.core.model.BubbleKind
import com.infinitehits.bubbleblast.core.powerup.PowerUp

/** Snapshot of everything the HUD shows. Sent only when something changed. */
data class GameStats(
    val level: Int,
    val score: Int,
    val targetScore: Int,
    val moves: Int,
    val combo: Int,
    val bestCombo: Int,
    val state: GameEngine.State
)

/** Floating "+120" style callouts drawn by the renderer. */
enum class PopupKind { SCORE, DROP, PERFECT, SPECIAL, BONUS }

/** Why a level ended in a loss. */
enum class GameOverReason { OUT_OF_MOVES, DANGER_LINE }

data class LevelResult(
    val level: Int,
    val score: Int,
    val targetScore: Int,
    val stars: Int,
    val coinsEarned: Int,
    val movesLeft: Int,
    val bestCombo: Int,
    val boardCleared: Boolean,
    val isNewBest: Boolean,
    val firstClear: Boolean,
    /** Bubbles popped during the level - shown on the results screen. */
    val popped: Int = 0,
    /** Bubbles dropped by losing their anchor. */
    val dropped: Int = 0
)

data class GameOverResult(
    val level: Int,
    val score: Int,
    val bestScore: Int,
    val reason: GameOverReason,
    val isNewBest: Boolean
)

/**
 * The engine talks to the outside world only through this interface, which keeps
 * rendering, audio and persistence completely separate from game rules.
 */
interface EngineListener {

    /** HUD values changed. */
    fun onStatsChanged(stats: GameStats)

    /** The launcher loaded a new bubble (or the preview changed). */
    fun onAmmoChanged(current: BubbleSpec, next: BubbleSpec)

    /** A bubble left the launcher. */
    fun onShotFired(color: BubbleColor, kind: BubbleKind)

    /** A bubble popped - the renderer spawns particles, the audio manager a sound. */
    fun onBubblePopped(x: Float, y: Float, color: BubbleColor, kind: BubbleKind)

    /** A group lost its anchor and fell off the board. */
    fun onClusterDropped(count: Int, points: Int)

    /** A special bubble fired (bomb, lightning, fire). */
    fun onSpecialTriggered(kind: BubbleKind, x: Float, y: Float)

    /** Floating score text. */
    fun onPopup(kind: PopupKind, value: Int, x: Float, y: Float)

    /** The combo counter changed; 0 means the streak was broken. */
    fun onComboChanged(combo: Int, x: Float, y: Float)

    /** Bubbles were added, removed or recoloured: cached guides must be rebuilt. */
    fun onBoardChanged()

    /** A new row was pushed in from the ceiling. */
    fun onRowInserted()

    /** Screen shake request, 0..1. */
    fun onShake(intensity: Float)

    /** Haptic feedback request; 0 = off, 1 = light, 2 = medium, 3 = heavy. */
    fun onVibrate(strength: Int)

    /** Power-up counters changed. */
    fun onPowerUpCountsChanged(counts: Map<PowerUp, Int>)

    /** A power-up could not be used (nothing left / wrong moment). */
    fun onPowerUpRejected(powerUp: PowerUp)

    /** The player finished the level. */
    fun onLevelComplete(result: LevelResult)

    /** The player ran out of moves or the bubbles crossed the danger line. */
    fun onGameOver(result: GameOverResult)
}
