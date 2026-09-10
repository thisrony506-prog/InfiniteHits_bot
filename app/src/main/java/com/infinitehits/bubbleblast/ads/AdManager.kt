package com.infinitehits.bubbleblast.ads

import android.app.Activity
import android.view.View
import android.view.ViewGroup

/**
 * Everything the game needs from an ad provider, and nothing more.
 *
 * The game ships with [NoOpAdManager], which reports "no ads available" for every
 * placement - so the game is 100% playable and store compliant without any SDK.
 * Wiring AdMob later means adding a dependency and one class
 * (`AdMobAdManager`); no game code has to change. See docs/ADS_INTEGRATION.md.
 */
interface AdManager {

    /** Placement identifiers, so the frequency policy lives in one place. */
    enum class Placement {
        /** Small banner at the bottom of the home screen. */
        HOME_BANNER,

        /** Small banner under the level select grid. */
        LEVEL_SELECT_BANNER,

        /** Full screen ad after a level is completed. */
        LEVEL_COMPLETE_INTERSTITIAL,

        /** Full screen ad after a few losses in a row. */
        GAME_OVER_INTERSTITIAL,

        /** Opt-in video that doubles the coins earned in a level. */
        DOUBLE_COINS_REWARDED,

        /** Opt-in video that grants five extra moves after a loss. */
        EXTRA_MOVES_REWARDED
    }

    /** Rewards an opt-in video can pay out. */
    enum class Reward { DOUBLE_LEVEL_COINS, EXTRA_MOVES }

    /** Called once per activity so providers can attach to their lifecycle. */
    fun initialize(activity: Activity)

    /** Creates the banner view for [container], or returns null when no banner is available. */
    fun attachBanner(container: ViewGroup, placement: Placement): View?

    /** Hides any banner previously attached to [container]. */
    fun detachBanner(container: ViewGroup)

    /** Pre-loads an interstitial so it can be shown instantly later. */
    fun preloadInterstitial(placement: Placement)

    /** Shows an interstitial if one is ready. [onFinished] always runs. */
    fun showInterstitial(activity: Activity, placement: Placement, onFinished: () -> Unit)

    /** True when an opt-in video for [placement] is loaded and ready. */
    fun isRewardedReady(placement: Placement): Boolean

    /** Pre-loads an opt-in video. */
    fun preloadRewarded(placement: Placement)

    /**
     * Shows an opt-in video.
     * @param onReward invoked only when the player watched long enough
     * @param onFinished always invoked, reward or not
     */
    fun showRewarded(
        activity: Activity,
        placement: Placement,
        onReward: (Reward) -> Unit,
        onFinished: () -> Unit
    )

    /** The activity is going away; release provider resources. */
    fun release(activity: Activity)
}
