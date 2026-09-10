# Bubble Blast — architecture

The project is one Gradle module (`:app`) split into layers that only ever depend
downwards. Nothing in `core/` knows that Android, Canvas or Activities exist, which
is what makes the engine testable on the JVM and easy to reason about.

```
ui/            Activities, dialogs and small custom views      (Android)
 │  game/      GameView, renderers, particles                    (Android/Canvas)
 │   │  data/  SaveManager, SettingsManager, DailyRewardManager  (Android prefs)
 │   │   │  core/   model · level · engine · score · powerup     (pure Kotlin)
 └───┴───┴────┴──────────────────────────────────────────────────────────────
```

## 1. Layers

| Package | Responsibility | Depends on |
| --- | --- | --- |
| `core.model` | `BubbleColor`, `BubbleKind`, `Bubble`, `Hex`, `Board`, `BoardGeometry` | nothing |
| `core.util` | `Rng` (deterministic xorshift64\*) | nothing |
| `core.level` | `LayoutPatterns` (20 hand-authored ASCII layouts), `LevelGenerator`, `LevelDefinition`, `LevelCatalog` (120 levels) | `core.model`, `core.util` |
| `core.engine` | `Shot`, `ShotSimulator`, `Shooter`, `EngineListener`, `GameEngine` (state machine + rules) | `core.model`, `core.level`, `core.score`, `core.powerup` |
| `core.score` | `ScoreSystem`, `StarRating` | `core.level` |
| `core.powerup` | `PowerUp` enum (price, starting inventory, amounts) | nothing |
| `data` | `SaveManager`, `SettingsManager`, `DailyRewardManager` — everything persisted | `SharedPreferences` |
| `audio` | `AudioManager` — SoundPool SFX, MediaPlayer music, settings-aware | `data`, `res/raw` |
| `ads` | `AdManager` interface + `NoOpAdManager` | nothing |
| `game` | `GameView` (SurfaceView + render thread), `ParticleSystem`, `render/BoardRenderer`, `render/BubblePainter` | `core`, `audio`, `data` |
| `ui` | 6 activities, 7 dialogs, `LevelAdapter`, custom views | everything above |
| `util` | `Haptics`, `UiUtils`, `Navigator` | `data` |

`AppServices` is the only place that owns long-lived objects. It is created once by
`BubbleBlastApp` and handed to every Activity — all of its members hold the
application context, so no Activity can be leaked by a service.

## 2. GameEngine — the rules

`GameEngine` is a plain Kotlin class driven by `update(dt)`. It never touches
Android and never allocates per frame on the hot path.

States: `INTRO → AIMING → FLYING → RESOLVING → AIMING`, plus `PAUSED`,
`LEVEL_COMPLETE` and `GAME_OVER`.

| Step | What happens |
| --- | --- |
| Aiming | `beginAim`, `aimAt(x, y)` clamp the angle to ±78° (`MAX_AIM_ANGLE = 1.3613 rad`), so shots can never travel into the launcher zone. `releaseAim` fires. |
| Flight | `ShotSimulator` advances the shot in sub-steps of `0.4 × radius` (max 96 per frame), bouncing off the walls and the ceiling and recording a poly-line trace for the aim guide. |
| Landing | The landing cell is resolved with `Board.nearestFreeCell`; the bubble is placed on the hex grid (odd rows are offset by half a bubble), never overlapping another bubble. |
| Resolution | Match detection walks the hex neighbourhood (`Hex.neighborsOf`) to find same-colour groups of ≥3. Floating clusters (not connected to the ceiling) are found by a BFS from row 0 and dropped. |
| Cascades | Specials chain into further pops; drops award combo multipliers; the loop repeats until the board is stable, then it returns to `AIMING`. |
| End conditions | Target score reached → `LEVEL_COMPLETE`; out of moves or a bubble crossing `BoardGeometry.dangerY` → `GAME_OVER`. |

### Scoring

| Event | Points |
| --- | --- |
| Bubble popped | 10, plus 8 for every bubble past the third in the group |
| Special detonated | +50 |
| Perfect shot (6+ with one shot) | +100 |
| Cluster dropped | 20 per bubble |
| Board cleared | +1000, and always 3 stars |
| Spare move left | +25 |

Combo multiplier: `1 + 0.25 × (combo − 1)`, capped at 3×. Stars: 1★ at the target,
2★ at 1.22×, 3★ at 1.50× (or a full board clear).

### Specials and obstacles

| Bubble | Effect |
| --- | --- |
| Bomb | Detonates everything within 1.75 cells |
| Rainbow | Wild card: matches any colour |
| Lightning | Clears its whole horizontal row |
| Fire | Burns a 1.25-cell cluster |
| Stone / Ice / Locked / Unbreakable / Moving | Obstacles: stone ignores colour matches, ice needs a match to crack, locked frees when a neighbour pops, unbreakable only anchors the cluster, moving rows drift left and right |

Specials are introduced gradually: Bomb at level 4, Rainbow at 9, Lightning at 24,
Fire at 44 (see `LevelCatalog.specialUnlockLevel`).

### Power-ups

| Power-up | Cost | Effect |
| --- | --- | --- |
| Aim Extension | 120 | Three shots with a longer guide and three wall bounces |
| Colour Changer | 100 | Swaps the loaded bubble for the previewed one |
| Bomb | 180 | Tap the board to clear a small cluster, free of charge |
| Extra Moves | 150 | +3 moves |

New players start with 3 / 3 / 2 / 3 of them, and more can be bought with coins
earned in game.

## 3. Rendering

* `GameView` is a `SurfaceView` with a dedicated `BubbleBlast-Render` thread
  paced at 60 FPS. Input and lifecycle events travel through a lock-free
  `ConcurrentLinkedQueue` of `Command`s, so the UI thread never blocks on the
  simulation and vice versa.
* `BubblePainter` pre-bakes one shaded sphere bitmap per colour (highlight,
  rim, glyph, ice shell, rainbow sheen, dots) and re-draws those bitmaps every
  frame — no per-frame gradients or allocations.
* `ParticleSystem` is a fixed pool of 320 particles; a burst simply recycles
  slots, so popping never triggers GC on low-end devices.
* `BoardRenderer` bakes the sky, stars and ceiling plate once per size change and
  then draws the board, danger-line pulse, pop/crack/fall animations, launcher,
  aim guide, score popups and the level-complete celebration.
* Effects are deliberately cheap: screen shake (sin-based offset), squash-and-
  stretch on spawn, scale-out on pop, and a pulsing danger line.

## 4. Persistence

| Key group | Stored |
| --- | --- |
| `stars` | one digit per level (`0..3`) in level order |
| `scores` | `level:score;` pairs for levels already played |
| `unlocked`, `coins`, `best`, `plays`, `pops` | scalars |
| `power_*` | owned count for each power-up |
| `daily_*` | last claim day and current streak |

Settings (sound, music, vibration, aim guide, left-handed layout) live in the same
`bubble_blast_prefs` file, so a device backup restores progress and preferences
together (`res/xml/backup_rules.xml`).

## 5. Extending the game

* **New level layouts** — add ASCII art to `LayoutPatterns`; the width rule
  (11 characters on even rows, 10 on odd rows) is enforced by
  `tools/check_patterns.py`.
* **More levels** — raise `LevelCatalog.LEVEL_COUNT` and add a `Chapter`; stars
  and score storage scale automatically because both are keyed by level number.
* **A different ad network** — implement `AdManager` and return it from
  `AppServices.createAdManager()`; no call site changes (see
  [ADS_INTEGRATION.md](ADS_INTEGRATION.md)).
* **New power-ups** — add an entry to `PowerUp`, a case in
  `GameEngine.usePowerUp`, and an icon + strings entry.
* **New screens** — extend `BaseActivity`, register the Activity in the manifest
  and add a `Navigator` helper.
