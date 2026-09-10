# Bubble Blast — the 120 levels

Levels are **generated deterministically**, not hand-listed. Level *n* always
produces the same board, move budget and target score on every device, because
everything is derived from `seed = n * 7919 + 1_000_003` and a small xorshift RNG
(`core/util/Rng.kt`). Positions, colours, ice layers, moving rows and specials are
all decided by that seed, so a level can be replayed and tuned without touching a
single asset.

## Chapters

| # | Chapter | Levels | Colours | New in this chapter | Drop every | Danger clearance |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Sunny Start | 1–20 | 3 | Plain bubbles, first layouts | — | 0 rows |
| 2 | Colour Rush | 21–40 | 4 | Moving rows, stone | 15 shots | 0 rows |
| 3 | Cold Front | 41–60 | 4 | Ice shells, locked bubbles | 13 shots | 0 rows |
| 4 | The Fortress | 61–80 | 5 | Unbreakable walls, denser layouts | 11 shots | 1 row |
| 5 | Night Shift | 81–100 | 5 | Double ice layers, cave layouts | 10 shots | 1 row |
| 6 | Blast Master | 101–120 | 6 | Two-row drops, tight boards | 8 shots | 2 rows |

Special bubbles unlock one at a time so each one gets a level to shine:

| Special | First level | Effect |
| --- | --- | --- |
| Bomb | 4 | Destroys everything within 1.75 cells |
| Rainbow | 9 | Wild card, matches any colour |
| Lightning | 24 | Clears its whole row |
| Fire | 44 | Burns a 1.25-cell cluster |

Every 20th level (20, 40, 60, …) and level 120 are **boss levels**: they use the
chapter's signature layout with the tightest move budget and target of that
chapter.

## How the numbers are derived

| Quantity | Formula |
| --- | --- |
| Moves | `ceil(destructible × (movesPerBubble + chapterProgress × movesRamp))`, clamped to 12…90 |
| Target | `round(destructible × 9 × ratio / 10) × 10`, `ratio` from 0.70 up to 0.88, clamped to 0.60…1.00 |
| Two stars | target × 1.22 |
| Three stars | target × 1.50 (or clearing the board) |
| Coins | `10 + 12 × stars + min(score / 400, 20) + 25` for a first clear, capped at 140 |

`destructible` counts only bubbles that block a board clear, so walls and stone
never inflate the target. The target stays deliberately below the ~15 points per
bubble a competent player averages (pop + combo + drop bonuses), which is why no
level is mathematically impossible.

## Layouts

Twenty hand-authored ASCII layouts live in `core/level/LayoutPatterns.kt`:

```
FULL_THREE  THIN_WALL  WALL       PYRAMID   DIAMOND
CHECKER     STRIPES    ARCH       ISLANDS   COLUMNS
SCATTER     ZIPPER     TWIN_TOWERS FORTRESS ICEBOX
VAULT       RAILS      SPECIAL_LAB DEEP_CAVE GRAND_WALL
```

Legend (one character per cell):

| Char | Meaning |
| --- | --- |
| `.` | empty space |
| `#` | coloured bubble |
| `S` | stone |
| `X` | unbreakable wall |
| `L` | locked bubble |
| `I` | ice bubble |
| `M` | moving bubble (the whole row slides) |
| `B` `R` `T` `F` | bomb, rainbow, lightning, fire |

Rows must alternate 11 and 10 characters, matching the offset hex grid, and a
level must be 3…12 rows tall. `tools/check_patterns.py` verifies both rules for
every pattern.

## Adding or rebalancing levels

* **Retune difficulty** — edit the `Chapter` table in `LevelCatalog.kt` (moves per
  bubble, target ratio, drop cadence). Nothing else needs to change.
* **Add a chapter** — append a `Chapter` and raise `LEVEL_COUNT`; the save file
  stores stars and scores per level number, so existing saves stay valid.
* **Add a layout** — drop it into `LayoutPatterns` and reference it from a
  chapter's `patterns` list; run `python3 tools/check_patterns.py` afterwards.

Because generation is seeded, a level that *feels* wrong can be inspected without
playing it: `LevelGenerator.build(LevelCatalog.get(n))` returns the exact bubble
list, and `LevelCatalog.board(n)` caches it.
