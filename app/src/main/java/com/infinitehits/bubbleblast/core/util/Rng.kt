package com.infinitehits.bubbleblast.core.util

/**
 * Tiny deterministic xorshift64* generator.
 *
 * Every level is generated from the level number alone, so a level always looks
 * exactly the same on every device and after every restart - and the whole engine
 * stays free of `java.util.Random` so it can be unit tested on the JVM.
 */
class Rng(seed: Long) {

    private var state: Long = if (seed == 0L) GOLDEN_GAMMA else seed

    fun nextLong(): Long {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        state = x
        return x
    }

    /** Uniform value in `0 until bound`. */
    fun nextInt(bound: Int): Int {
        if (bound <= 0) return 0
        return ((nextLong() ushr 1) % bound).toInt()
    }

    /** Uniform value in `min until max`. */
    fun nextInt(min: Int, max: Int): Int = min + nextInt(max - min)

    /** Uniform value in `0f until 1f`. */
    fun nextFloat(): Float = ((nextLong() ushr 11).toDouble() / (1L shl 53).toDouble()).toFloat()

    fun nextBoolean(): Boolean = (nextLong() and 1L) == 0L

    /** Picks a random element, or null for an empty list. */
    fun <T> pick(list: List<T>): T? = if (list.isEmpty()) null else list[nextInt(list.size)]

    /** Fisher-Yates shuffle, in place. */
    fun <T> shuffle(list: MutableList<T>) {
        for (i in list.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val tmp = list[i]
            list[i] = list[j]
            list[j] = tmp
        }
    }

    /** Weighted pick over a map of item -> positive weight. */
    fun <T> weighted(map: Map<T, Int>): T? {
        var total = 0
        for (weight in map.values) {
            if (weight > 0) total += weight
        }
        if (total <= 0) return null
        var roll = nextInt(total)
        for ((item, weight) in map) {
            if (weight <= 0) continue
            roll -= weight
            if (roll < 0) return item
        }
        return null
    }

    /** A new generator derived from this one - handy for per-system streams. */
    fun fork(salt: Long): Rng = Rng(state xor (salt * GOLDEN_GAMMA))

    companion object {
        private const val GOLDEN_GAMMA = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
    }
}
