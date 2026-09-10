# Verification

This project was written in an environment with **no JDK, no Android SDK and no
device**, so the usual "does it compile / does it run" loop was replaced with
static analysis that is checked into `tools/`. This page is an honest account of
what has been verified, how, and what still needs a real build.

## 1. What is verified automatically

| Tool | Command | Coverage |
| --- | --- | --- |
| Layout checker | `python3 tools/check_patterns.py` | all 20 ASCII layouts: 11/10 character rows, legal symbols, 3–12 rows, every `LayoutPatterns` constant resolves |
| Resource checker | `python3 tools/check_resources.py` | every `R.string/drawable/layout/id/anim/raw/style/mipmap` reference in Kotlin, layouts and the manifest resolves; no duplicate declarations; every XML file parses; resource file names are lowercase (aapt2 rule); custom views in XML exist as Kotlin classes |
| Symbol checker | `python3 tools/check_symbols.py` | every `import` resolves; every `Type.member` reference exists; every method/property call on a typed receiver exists (types inferred per function scope, including inherited members); every `override` matches the interface signature; every interface implementation covers its members; no unused imports |
| Asset generators | `python3 tools/generate_icons.py`, `python3 tools/generate_audio.py` | regenerate the 17 vectors, launcher icons and 11 audio files from source |

All three analysers currently report clean:

```
patterns checked: 20 -> ALL PATTERNS OK
strings: 120  colors: 58  dimens: 26  drawables: 38  layouts: 16  raw: 11  anim: 6
  -> ALL RESOURCE REFERENCES RESOLVE
files: 47  types: 92  -> ALL SYMBOLS RESOLVE
```

The analysers were themselves mutation-tested: deliberately introducing an unknown
method on `AppServices`/`Board`/`GameEngine`, a property typo on a data class, an
unknown `R.string`, and a listener override with the wrong parameter type are all
reported — so a green run is meaningful rather than vacuous.

## 2. What the tests cover

`app/src/test/java/.../core/EngineCoreTest.kt` (JUnit 4, runs with `./gradlew test`,
no device needed) asserts the invariants the gameplay depends on:

* the RNG is deterministic, bounded, and its shuffle is a true permutation;
* the hex grid reports 11/10 columns and 6 neighbours for interior cells;
* all 120 level definitions exist, use unique seeds, and stay inside sane ranges
  (moves 12…90, colours 3…6, monotone star thresholds);
* level numbers outside the range are clamped instead of crashing;
* every generated board is legal: bubbles stay inside the grid, every colour has
  at least three copies (so no colour is unmatchable), the board has destructible
  bubbles, and generation is byte-for-byte reproducible;
* level 1 contains no specials (specials unlock later);
* star and coin payouts follow the documented thresholds and caps.

## 3. What still needs a real build

These cannot be proven without a compiler and a device:

1. **Compilation.** The static checks catch unresolved symbols, wrong override
   signatures and missing resources, but not type inference subtleties (for
   example passing a `Float` where an `Int` is expected, or a missing `when`
   branch). Run `./gradlew assembleDebug` first — expect only trivial fixes, if
   any.
2. **Visual layout.** All 16 layouts were written against the design tokens in
   `res/values/`, but their exact look on a 360 dp phone and a tablet has to be
   seen. Nothing is hard-coded to a screen size: the board geometry is computed in
   `BoardGeometry` from the view size, and every layout uses `dp`/`sp` plus
   weights.
3. **Feel.** Shot speed, aim sensitivity, particle density and the shake
   amplitude are tuned by reasoning (documented in `ARCHITECTURE.md`); the
   constants are all in one place per class so they are easy to nudge after
   playing: `GameEngine.companion`, `GameView.companion`,
   `ParticleSystem.companion`.
4. **Audio timbre.** The generated SFX are synthesised, so they are crisp and
   consistent rather than recorded — listen on a device and replace
   `res/raw/*.ogg` if you want a different character (see `ASSETS.md`).
5. **Device matrix.** minSdk 24 means the app runs on Android 7.0+. The only
   version-conditional APIs used are `VibrationEffect` (guarded with a version
   check in `Haptics`) and `enableOnBackInvokedCallback`, which is ignored on old
   platforms.

## 4. Suggested first-run checklist

```bash
./gradlew assembleDebug          # 1. does it compile
./gradlew test                   # 2. do the engine invariants hold
./gradlew installDebug           # 3. install on a device
```

Then play level 1 and check: aim guide follows the finger, a shot sticks to the
board, three same-coloured bubbles pop, disconnected bubbles fall, the HUD counts
down, and finishing the level shows the stars. A second device with Android 7 is
the best stress test for the fallbacks.
