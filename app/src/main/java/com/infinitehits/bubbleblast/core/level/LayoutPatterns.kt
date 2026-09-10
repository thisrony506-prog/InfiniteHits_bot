package com.infinitehits.bubbleblast.core.level

/**
 * Hand authored level layouts, written as ASCII art.
 *
 * Row 0 is the top row (the one glued to the ceiling) and must be 11 characters
 * wide, row 1 must be 10, row 2 is 11 again and so on - matching
 * [com.infinitehits.bubbleblast.core.model.Hex], where odd rows hold one bubble
 * fewer so that both row shapes span the same board width.
 *
 * Legend
 * ------
 * ```
 *  .  empty space
 *  #  coloured bubble (colour chosen by the generator)
 *  S  stone            - only destroyed by specials
 *  X  unbreakable wall - indestructible anchor
 *  L  locked           - freed when a neighbouring bubble pops
 *  I  ice              - takes an extra match to break the shell
 *  M  moving           - the whole row slides left and right
 *  B  bomb bubble
 *  R  rainbow bubble
 *  T  lightning bubble
 *  F  fire bubble
 * ```
 */
object LayoutPatterns {

    val FULL_THREE = "full_three"
    val THIN_WALL = "thin_wall"
    val WALL = "wall"
    val PYRAMID = "pyramid"
    val DIAMOND = "diamond"
    val CHECKER = "checker"
    val STRIPES = "stripes"
    val ARCH = "arch"
    val ISLANDS = "islands"
    val COLUMNS = "columns"
    val SCATTER = "scatter"
    val ZIPPER = "zipper"
    val TWIN_TOWERS = "twin_towers"
    val FORTRESS = "fortress"
    val ICEBOX = "icebox"
    val VAULT = "vault"
    val RAILS = "rails"
    val SPECIAL_LAB = "special_lab"
    val DEEP_CAVE = "deep_cave"
    val GRAND_WALL = "grand_wall"

    private val patterns: Map<String, List<String>> = linkedMapOf(
        FULL_THREE to listOf(
            "###########",
            "##########",
            ".#########."
        ),
        THIN_WALL to listOf(
            "###########",
            "#........#",
            "###########"
        ),
        WALL to listOf(
            "###########",
            "##########",
            "###########",
            "##########"
        ),
        PYRAMID to listOf(
            "###########",
            ".########.",
            "..#######..",
            "...#####..",
            "....###...."
        ),
        DIAMOND to listOf(
            "....###....",
            "...#####..",
            "..#######..",
            ".#########",
            "###########",
            ".########."
        ),
        CHECKER to listOf(
            "#.#.#.#.#.#",
            ".#.#.#.#.#",
            "#.#.#.#.#.#",
            ".#.#.#.#.#"
        ),
        STRIPES to listOf(
            "##.##.##.##",
            "##..##..##",
            "##.##.##.##",
            "##..##..##",
            "##.##.##.##"
        ),
        ARCH to listOf(
            "#.........#",
            "#........#",
            "#.........#",
            "##########",
            "###########"
        ),
        ISLANDS to listOf(
            "###...###..",
            "##....##..",
            "###...###..",
            "..##...##."
        ),
        COLUMNS to listOf(
            "##.##.##.##",
            ".#.##.##.#",
            "##.##.##.##",
            "#.##.##.##",
            "##.##.##.##",
            ".#.##.##.#"
        ),
        SCATTER to listOf(
            ".##..#..##.",
            "#..#.###..",
            "..##...#...",
            ".#..##..#.",
            "#..##...##.",
            "..###..#.."
        ),
        ZIPPER to listOf(
            "###........",
            "..###.....",
            ".....###...",
            ".......###",
            "....###....",
            "..###....."
        ),
        TWIN_TOWERS to listOf(
            "###.....###",
            "###....###",
            "###.....###",
            "###....###",
            "###########"
        ),
        FORTRESS to listOf(
            "##XXXXXX###",
            "#X######X.",
            "##S####S##.",
            "#..####..#",
            "###SS##S##.",
            "..######.."
        ),
        ICEBOX to listOf(
            "###########",
            ".##II###..",
            "###II#####.",
            "..##II##..",
            "##II#######"
        ),
        VAULT to listOf(
            ".LLL...LLL.",
            "##########",
            "LL.......LL",
            "##########",
            "...........",
            "..#####..."
        ),
        RAILS to listOf(
            "###########",
            "##########",
            "MMMMMMMMMMM",
            "##########",
            "MMMMMMMMMMM",
            "..........",
            "###########"
        ),
        SPECIAL_LAB to listOf(
            "###########",
            "#B#######R",
            "###T###F###",
            "##########",
            "#R#####B###"
        ),
        DEEP_CAVE to listOf(
            "####S######",
            "##S###S#..",
            "#S#####S##.",
            "##########",
            "..######...",
            "####...###",
            "##.#####.##"
        ),
        GRAND_WALL to listOf(
            "###########",
            "##########",
            "S#########S",
            "##########",
            "####XX#####",
            "##########",
            "IIIIIIIIIII",
            "##########"
        )
    )

    /** All pattern keys, in a stable order. */
    val ALL_KEYS: List<String> = patterns.keys.toList()

    fun rows(key: String): List<String> = patterns[key] ?: patterns.getValue(FULL_THREE)

    /** Number of rows in a pattern. */
    fun rowCount(key: String): Int = rows(key).size

    /** True when the pattern contains at least one of [symbol]. */
    fun contains(key: String, symbol: Char): Boolean = rows(key).any { it.indexOf(symbol) >= 0 }
}
