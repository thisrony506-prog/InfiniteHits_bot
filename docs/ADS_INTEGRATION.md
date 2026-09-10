# Ads — placeholder now, AdMob-ready later

Bubble Blast ships **without any ad SDK**. What it does ship is the full set of
call sites an ad provider needs, behind one interface, so wiring AdMob in later is
a change in two files plus a Gradle line — no gameplay code is touched.

## 1. How the seam works

```
ui/*  ──▶ AppServices.ads ──▶ AdManager (interface)
                                   ▲
                    NoOpAdManager ─┘        AdMobAdManager (future)
```

* `AdManager` (`ads/AdManager.kt`) defines placements, the interstitial/rewarded
  flow and banner attachment.
* `NoOpAdManager` is the shipped implementation: no banners, `showInterstitial`
  finishes immediately, `isRewardedReady` always returns `false` — so no button
  that depends on a reward is ever shown.
* `AppServices.createAdManager()` is the single switch that decides which
  implementation is used.
* `BuildConfig.ADS_ENABLED` gates the frequency caps, so an ad-free build behaves
  identically to the current one.

### Placements already wired

| Placement | Used by | Type |
| --- | --- | --- |
| `HOME_BANNER` | `HomeActivity` | banner (container reserved on demand) |
| `LEVEL_SELECT_BANNER` | `LevelSelectActivity` | banner |
| `LEVEL_COMPLETE_INTERSTITIAL` | `GameActivity` after a win, every 3 levels | interstitial |
| `GAME_OVER_INTERSTITIAL` | `GameActivity` after a loss, every 3 losses | interstitial |
| `DOUBLE_COINS_REWARDED` | level-complete dialog ("Double coins") | rewarded |
| `EXTRA_MOVES_REWARDED` | game-over dialog ("Continue with +5 moves") | rewarded |

Both rewarded buttons are hidden automatically while `isRewardedReady()` is
`false`, which is why the shipped game shows no dead buttons.

## 2. Adding AdMob

1. **Dependency** — uncomment in `app/build.gradle.kts`:

   ```kotlin
   implementation("com.google.android.gms:play-services-ads:23.5.0")
   ```

2. **Build flags** — same file, in `defaultConfig`:

   ```kotlin
   buildConfigField("String", "AD_PROVIDER", "\"admob\"")
   buildConfigField("boolean", "ADS_ENABLED", "true")
   ```

3. **Application ID** — in `app/src/main/AndroidManifest.xml`, inside
   `<application>` (the commented placeholder is already there):

   ```xml
   <meta-data
       android:name="com.google.android.gms.ads.APPLICATION_ID"
       android:value="ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY" />
   ```

4. **Implement the provider** — add `ads/AdMobAdManager.kt`. Keep the same shape
   as `NoOpAdManager`; only the six overrides change:

   ```kotlin
   class AdMobAdManager(private val context: Context) : AdManager {

       override fun initialize(activity: Activity) {
           MobileAds.initialize(context)
       }

       override fun preloadInterstitial(placement: AdManager.Placement) {
           // load into a small cache keyed by placement
       }

       override fun showInterstitial(activity: Activity, placement: AdManager.Placement,
                                     onFinished: () -> Unit) {
           val ad = cached(placement)
           if (ad == null) { onFinished(); return }        // never block gameplay
           ad.fullScreenContentCallback = object : FullScreenContentCallback() {
               override fun onAdDismissedFullScreenContent() { onFinished() }
               override fun onAdFailedToShowFullScreenContent(e: AdError) { onFinished() }
           }
           ad.show(activity)
       }

       override fun isRewardedReady(placement: AdManager.Placement) = rewarded(placement) != null

       override fun showRewarded(activity: Activity, placement: AdManager.Placement,
                                 onReward: (AdManager.Reward) -> Unit, onFinished: () -> Unit) { … }

       // attachBanner / detachBanner / release follow the same pattern
   }
   ```

5. **Return it from the factory** — in `AppServices`, uncomment the matching
   branch (the switch and the commented line are already there):

   ```kotlin
   private fun createAdManager(context: Context): AdManager = when (BuildConfig.AD_PROVIDER) {
       "admob" -> AdMobAdManager(context)   // uncomment together with the dependency
       else -> NoOpAdManager()
   }
   ```

6. **Test IDs while developing** — use Google's sample unit IDs
   (`ca-app-pub-3940256099942544/…`); never click your own live ads.

## 3. Rules the call sites already follow

* An interstitial is only shown **after** the result dialog is ready, and the
  dialog is presented either way — an ad failure can never swallow a level
  result.
* Interstitials are capped (every third level / third loss) and never appear
  during play, on the splash screen or after a pause.
* Rewarded buttons appear only when a reward is actually available.
* No ad is ever loaded while the game is in the background
  (`BubbleBlastApp` tracks foreground state).
* All placement names are strings, not enum ordinals, so the ordering can change
  without invalidating cached state.
