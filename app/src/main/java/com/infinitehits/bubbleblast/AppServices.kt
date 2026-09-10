package com.infinitehits.bubbleblast

import android.content.Context
import com.infinitehits.bubbleblast.ads.AdManager
import com.infinitehits.bubbleblast.ads.NoOpAdManager
import com.infinitehits.bubbleblast.audio.AudioManager
import com.infinitehits.bubbleblast.data.DailyRewardManager
import com.infinitehits.bubbleblast.data.SaveManager
import com.infinitehits.bubbleblast.data.SettingsManager
import com.infinitehits.bubbleblast.util.Haptics

/**
 * Process-wide services, created once in [BubbleBlastApp].
 *
 * Activities reach these through `(application as BubbleBlastApp).services`.
 * Everything here is either stateless or holds only the application context, so
 * no Activity can be leaked by a service.
 *
 * This is also the single place where swapping the ad provider happens.
 */
class AppServices(context: Context) {

    private val appContext: Context = context.applicationContext

    val save: SaveManager = SaveManager.get(appContext)

    val settings: SettingsManager = SettingsManager(appContext)

    val audio: AudioManager = AudioManager(appContext, settings).also {
        it.initialize()
        settings.onChange = { audio.onSettingsChanged() }
    }

    val haptics: Haptics = Haptics(appContext, settings)

    val dailyRewards: DailyRewardManager = DailyRewardManager(save)

    /**
     * Ad provider. [NoOpAdManager] reports "no inventory" for every placement, so
     * the game is completely ad-free until a provider is wired up in
     * docs/ADS_INTEGRATION.md.
     */
    val ads: AdManager = createAdManager(appContext)

    /** Interstitials are capped so the player is never interrupted twice in a row. */
    private var levelsSinceInterstitial: Int = 0
    private var lossesSinceInterstitial: Int = 0

    /** @return true when an interstitial should be shown now */
    fun shouldShowLevelInterstitial(): Boolean {
        if (!BuildConfig.ADS_ENABLED) return false
        levelsSinceInterstitial++
        if (levelsSinceInterstitial >= LEVEL_INTERSTITIAL_EVERY) {
            levelsSinceInterstitial = 0
            return true
        }
        return false
    }

    /** @return true when a game-over interstitial should be shown now */
    fun shouldShowGameOverInterstitial(): Boolean {
        if (!BuildConfig.ADS_ENABLED) return false
        lossesSinceInterstitial++
        if (lossesSinceInterstitial >= GAME_OVER_INTERSTITIAL_EVERY) {
            lossesSinceInterstitial = 0
            return true
        }
        return false
    }

    fun resetInterstitialCounters() {
        levelsSinceInterstitial = 0
        lossesSinceInterstitial = 0
    }

    /** Music bed for the screen that just became visible. */
    fun setMusicScene(scene: AudioManager.Scene) {
        audio.setScene(scene)
    }

    fun onAppForegroundState(foreground: Boolean) {
        audio.setForeground(foreground)
    }

    fun versionName(): String = try {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        info.versionName ?: DEFAULT_VERSION
    } catch (error: Exception) {
        DEFAULT_VERSION
    }

    private fun createAdManager(context: Context): AdManager = when (BuildConfig.AD_PROVIDER) {
        // "admob" -> AdMobAdManager(context)  // uncomment together with the dependency
        else -> NoOpAdManager()
    }

    companion object {
        private const val LEVEL_INTERSTITIAL_EVERY = 3
        private const val GAME_OVER_INTERSTITIAL_EVERY = 3
        private const val DEFAULT_VERSION = "1.0.0"
    }
}
