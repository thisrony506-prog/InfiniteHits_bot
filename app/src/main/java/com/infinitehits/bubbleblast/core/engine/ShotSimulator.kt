package com.infinitehits.bubbleblast.core.engine

import com.infinitehits.bubbleblast.core.model.Board
import com.infinitehits.bubbleblast.core.model.BoardGeometry
import com.infinitehits.bubbleblast.core.model.Bubble
import com.infinitehits.bubbleblast.core.model.Hex
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Moving-bubble physics: wall bounces, ceiling contact and bubble collisions.
 *
 * The integration is sub-stepped so a bubble can never tunnel through the board
 * no matter how high the frame rate - or the bubble speed - gets. The same code
 * drives the real shot and the aim guide, so what the player sees is exactly
 * what happens.
 */
class ShotSimulator(
    private val geometry: BoardGeometry,
    private val board: Board
) {

    /** What a simulation step ran into. */
    sealed class Impact {
        /** Still flying. */
        object None : Impact()

        /** Bounced off the left or right wall this step. */
        object Wall : Impact()

        /** Reached the ceiling and must be attached to the top row. */
        object Ceiling : Impact()

        /** Touched an existing bubble. */
        data class BubbleHit(val bubble: Bubble) : Impact()
    }

    /** Result of a prediction step. */
    data class Trace(
        val points: MutableList<Float> = ArrayList(64),
        var impact: Impact = Impact.None,
        var bounces: Int = 0
    ) {
        val pointCount: Int get() = points.size / 2
        fun add(x: Float, y: Float) {
            points.add(x)
            points.add(y)
        }
        fun lastX(): Float = if (points.isEmpty()) 0f else points[points.size - 2]
        fun lastY(): Float = if (points.isEmpty()) 0f else points[points.size - 1]
    }

    fun leftWall(): Float = geometry.boardLeft + geometry.bubbleRadius

    fun rightWall(): Float = geometry.boardLeft + geometry.boardWidth - geometry.bubbleRadius

    /**
     * Advances [shot] by [dt] seconds, including the wall bounce.
     * Returns the first significant impact of this frame (or [Impact.None]).
     */
    fun advance(shot: Shot, dt: Float): Impact {
        val distance = shot.speed() * dt
        val stepLength = (geometry.bubbleRadius * 0.4f).coerceAtLeast(1f)
        val steps = ceil(distance / stepLength).toInt().coerceIn(1, 96)
        val stepDt = dt / steps

        var impact: Impact = Impact.None
        for (step in 0 until steps) {
            shot.x += shot.vx * stepDt
            shot.y += shot.vy * stepDt

            val minX = leftWall()
            val maxX = rightWall()
            if (shot.x < minX) {
                shot.x = minX + (minX - shot.x)
                shot.vx = -shot.vx
                shot.bounces++
                impact = Impact.Wall
            } else if (shot.x > maxX) {
                shot.x = maxX - (shot.x - maxX)
                shot.vx = -shot.vx
                shot.bounces++
                impact = Impact.Wall
            }

            if (shot.y - geometry.bubbleRadius <= geometry.ceilingY) {
                shot.y = geometry.ceilingY + geometry.bubbleRadius
                shot.vy = abs(shot.vy)
                return Impact.Ceiling
            }

            val hit = findHit(shot.x, shot.y)
            if (hit != null) return Impact.BubbleHit(hit)
        }
        return impact
    }

    /**
     * Runs a throw-away copy of the shot and records the flight path.
     * Used by the aim guide; never mutates the board.
     */
    fun trace(
        startX: Float,
        startY: Float,
        angle: Float,
        speed: Float,
        maxBounces: Int,
        maxPoints: Int = 96
    ): Trace {
        val trace = Trace()
        var x = startX
        var y = startY
        var vx = sin(angle) * speed
        var vy = -cos(angle) * speed
        val stepLength = (geometry.bubbleRadius * 0.45f).coerceAtLeast(1f)
        val stepDt = stepLength / speed
        var bounces = 0

        trace.add(x, y)
        for (i in 0 until MAX_TRACE_STEPS) {
            x += vx * stepDt
            y += vy * stepDt

            val minX = leftWall()
            val maxX = rightWall()
            var bounced = false
            if (x < minX) {
                x = minX + (minX - x)
                vx = -vx
                bounced = true
            } else if (x > maxX) {
                x = maxX - (x - maxX)
                vx = -vx
                bounced = true
            }
            if (bounced) {
                bounces++
                trace.bounces = bounces
                if (bounces > maxBounces) {
                    if (trace.pointCount < maxPoints) trace.add(x, y)
                    trace.impact = Impact.Wall
                    return trace
                }
            }

            if (y - geometry.bubbleRadius <= geometry.ceilingY) {
                y = geometry.ceilingY + geometry.bubbleRadius
                if (trace.pointCount < maxPoints) trace.add(x, y)
                trace.impact = Impact.Ceiling
                return trace
            }

            val hit = findHit(x, y)
            if (hit != null) {
                if (trace.pointCount < maxPoints) trace.add(x, y)
                trace.impact = Impact.BubbleHit(hit)
                return trace
            }

            // Sample the polyline at a fixed spacing so the drawn dots stay even.
            if (trace.pointCount < maxPoints) {
                val dx = x - trace.lastX()
                val dy = y - trace.lastY()
                if (dx * dx + dy * dy >= stepLength * stepLength * 1.6f) trace.add(x, y)
            }
        }
        return trace
    }

    /**
     * Finds a bubble the shot overlaps.
     *
     * Only the cell under the shot and its neighbours are tested - checking the
     * whole board every sub-step would be needlessly expensive on low-end
     * devices.
     */
    private fun findHit(x: Float, y: Float): Bubble? {
        val row = geometry.nearestRow(y, board.topRow)
        val col = geometry.nearestColumn(row, x, board.slideOffset(row))
        val hitDistance = geometry.bubbleRadius * 2f * COLLISION_TOLERANCE
        val hitDistanceSquared = hitDistance * hitDistance

        for (r in row - 1..row + 1) {
            val columns = Hex.columnCount(r)
            for (c in col - 1..col + 1) {
                if (c < 0 || c >= columns) continue
                val bubble = board.bubbleAt(r, c) ?: continue
                val dx = board.centerX(r, c) - x
                val dy = board.centerY(r) - y
                if (dx * dx + dy * dy <= hitDistanceSquared) return bubble
            }
        }
        return null
    }

    /** Straight-line distance helper used by the launcher's aim clamp. */
    fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        /** Slight overlap tolerance so bubbles visually nest instead of hovering. */
        private const val COLLISION_TOLERANCE = 0.94f
        private const val MAX_TRACE_STEPS = 9000
    }
}
