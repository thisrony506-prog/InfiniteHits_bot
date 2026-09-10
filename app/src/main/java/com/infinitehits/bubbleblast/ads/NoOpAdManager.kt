package com.infinitehits.bubbleblast.ads

import android.app.Activity
import android.view.View
import android.view.ViewGroup

/**
 * The ad provider used when the game is built without an ad SDK.
 *
 * Every method is a no-op that reports "nothing available", which means:
 *  * no banner space is reserved, so the UI fills the screen;
 *  * [AdManager.showInterstitial] finishes immediately, so gameplay never waits;
 *  * rewarded buttons stay hidden because [isRewardedReady] returns false.
 *
 * This is what guarantees the game is fully playable offline and ad-free today,
 * while every call site is already written the way an ad provider would need it.
 */
class NoOpAdManager : AdManager {

    override fun initialize(activity: Activity) = Unit

    override fun attachBanner(container: ViewGroup, placement: AdManager.Placement): View? = null

    override fun detachBanner(container: ViewGroup) = Unit

    override fun preloadInterstitial(placement: AdManager.Placement) = Unit

    override fun showInterstitial(
        activity: Activity,
        placement: AdManager.Placement,
        onFinished: () -> Unit
    ) {
        onFinished()
    }

    override fun isRewardedReady(placement: AdManager.Placement): Boolean = false

    override fun preloadRewarded(placement: AdManager.Placement) = Unit

    override fun showRewarded(
        activity: Activity,
        placement: AdManager.Placement,
        onReward: (AdManager.Reward) -> Unit,
        onFinished: () -> Unit
    ) {
        // No ad to watch, no reward to hand out - the caller decides what to do.
        onFinished()
    }

    override fun release(activity: Activity) = Unit
}
