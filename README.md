# Bubble Blast

A complete, offline, native Android bubble shooter — 120 levels, six colours,
four special bubbles, four power-ups, coins, daily rewards and full progress
saving. Written in Kotlin with the Android Canvas API (no game engine, no web
view, no third-party runtime), portrait-locked, and designed for 60 FPS on
low-end phones.

```
./gradlew assembleDebug      # debug APK
./gradlew bundleRelease      # Play-ready App Bundle
./gradlew test               # engine tests (no device needed)
```

Full instructions: [docs/BUILD_AND_RELEASE.md](docs/BUILD_AND_RELEASE.md).

## Features

**Gameplay** — bottom-centre launcher, drag to aim with a trajectory guide that
shows wall bounces, release to fire, hex-grid snapping to the ceiling and to other
bubbles, matches of three or more, chain reactions, disconnected clusters fall
away, combo multipliers, perfect-shot and board-clear bonuses, limited moves per
level, danger line, and automatic reloading with a next-bubble preview.

**Special bubbles** — Bomb (blows up a small area), Rainbow (wild card), Lightning
(clears a row), Fire (burns a cluster), introduced one at a time from level 4
onwards.

**Obstacles** — stone, ice shells, locked bubbles, unbreakable walls and sliding
rows, each introduced in its own chapter so difficulty ramps smoothly.

**Power-ups** — Aim Extension, Colour Changer, Bomb and Extra Moves; the first
three of each are free, and the shop sells more for coins earned in game.

**Progression** — 120 levels across six chapters, 1–3 stars per level, best score
per level, coins, a seven-day daily reward streak, and a level select grid that
remembers stars and locks. Everything is stored locally and restored after a
relaunch; Settings ▸ Reset progress clears it after a confirmation.

**Screens** — Splash (animated logo + loading bar), Home (Play/Continue, Level,
coins, Daily Reward, Settings, sound + music toggles), Level Select, Game (level /
score / coins / pause on top, board in the middle, launcher, next bubble, power-ups
and moves below), Level Complete, Game Over, Settings and the legal pages.

**Polish** — baked sphere art with highlights and shadows, particle pops, screen
shake, star reveals, rounded gradient UI, screen transitions, haptics (switchable)
and generated SFX + music for shooting, popping, combos, wins, losses and clicks.

## Project layout

```
.
├── app/
│   ├── build.gradle.kts                module config (AGP 8.7.3, Kotlin 2.0.21)
│   ├── proguard-rules.pro              R8 keep rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml     6 activities, portrait-locked, VIBRATE only
│       │   ├── assets/legal/           offline privacy policy + terms
│       │   ├── java/com/infinitehits/bubbleblast/
│       │   │   ├── BubbleBlastApp.kt   application, foreground tracking
│       │   │   ├── AppServices.kt      process-wide services + ad factory
│       │   │   ├── core/               model · util · level · engine · score · powerup
│       │   │   ├── data/               save, settings, daily rewards
│       │   │   ├── audio/              AudioManager (SoundPool + MediaPlayer)
│       │   │   ├── ads/                AdManager interface + no-op provider
│       │   │   ├── game/               GameView, renderers, particle system
│       │   │   ├── ui/                 6 activities, 7 dialogs, adapter, custom views
│       │   │   └── util/               Haptics, UiUtils, Navigator
│       │   └── res/                    values · drawable (38) · layout (16) · anim (6) · raw (11) · mipmap
│       └── test/                       JVM engine tests
├── docs/                               ARCHITECTURE · BUILD_AND_RELEASE · LEVELS ·
│                                       ASSETS · ADS_INTEGRATION · VERIFICATION
├── store/                              Play Store icon + feature graphic
├── tools/                              asset generators + static analysers
├── gradle/ , gradlew, gradlew.bat      Gradle 8.9 wrapper
├── build.gradle.kts, settings.gradle.kts, gradle.properties
```

## Documentation

| Document | Contents |
| --- | --- |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | layer map, engine state machine, scoring table, rendering strategy, persistence keys |
| [docs/BUILD_AND_RELEASE.md](docs/BUILD_AND_RELEASE.md) | SDK setup, signing, release APK/AAB, Play Console checklist, troubleshooting |
| [docs/LEVELS.md](docs/LEVELS.md) | chapter table, difficulty formulas, layout legend, how to rebalance |
| [docs/ASSETS.md](docs/ASSETS.md) | how the icons and audio are generated and how to replace them |
| [docs/ADS_INTEGRATION.md](docs/ADS_INTEGRATION.md) | dropping AdMob into the existing placeholder seam |
| [docs/VERIFICATION.md](docs/VERIFICATION.md) | what the static analysers prove, and what still needs a device |

## Engineering notes

* **No third-party runtime dependencies** beyond AndroidX and Material. The engine,
  level generation, particle system and painter are all original code.
* **The engine is Android-free.** `core/` is pure Kotlin, which is why 120 levels
  and the scoring rules can be unit tested on the JVM (`./gradlew test`).
* **Deterministic levels.** Every board is derived from the level number, so a
  level is identical on every device and after every restart — and a save file
  only needs the level number, not the board.
* **Ad-free by construction.** The game ships with a no-op ad provider behind a
  real interface; the rewarded buttons hide themselves when no reward is
  available, so there are no dead buttons anywhere.
* **Nothing is a placeholder.** Every screen, dialog, sound and level is wired to
  working logic; the only intentional stubs are the ad provider (documented) and
  the legal text, which you should replace with your own before publishing.

## Static checks

```bash
python3 tools/check_patterns.py && python3 tools/check_resources.py && python3 tools/check_symbols.py
```

These verify the ASCII level layouts, every resource reference, and every
cross-layer symbol call — see [docs/VERIFICATION.md](docs/VERIFICATION.md).
