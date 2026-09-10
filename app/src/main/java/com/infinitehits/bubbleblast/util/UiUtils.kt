package com.infinitehits.bubbleblast.util

import android.content.Context
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.infinitehits.bubbleblast.R

/** Small UI helpers shared by every screen. */
object UiUtils {

    /**
     * Applies status/navigation bar padding to a root view.
     *
     * Android 15 forces edge-to-edge for apps targeting SDK 35; below that the
     * framework already insets the window and this listener simply receives
     * zeroes, so the same code is correct on every version.
     */
    fun applySystemBarInsets(
        root: View,
        horizontal: Boolean = true,
        top: Boolean = true,
        bottom: Boolean = true
    ) {
        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                baseLeft + if (horizontal) bars.left else 0,
                baseTop + if (top) bars.top else 0,
                baseRight + if (horizontal) bars.right else 0,
                baseBottom + if (bottom) bars.bottom else 0
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    /** Adds a subtle "press" scale animation to buttons and tiles. */
    fun attachPressEffect(vararg views: View) {
        for (view in views) {
            view.setOnTouchListener { target, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> target.animate()
                        .scaleX(PRESS_SCALE).scaleY(PRESS_SCALE).setDuration(70L).start()

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        target.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(140L)
                            .setInterpolator(OvershootInterpolator(2f))
                            .start()
                    }
                }
                false
            }
        }
    }

    /** Plays the "pop in" animation of a freshly scored star. */
    fun revealStar(star: ImageView, earned: Boolean, delayMillis: Long) {
        star.setImageResource(if (earned) R.drawable.ic_star else R.drawable.ic_star_empty)
        if (!earned) return
        star.postDelayed({
            val animation = AnimationUtils.loadAnimation(star.context, R.anim.star_pop)
            star.startAnimation(animation)
        }, delayMillis)
    }

    /** Runs a small bounce used on the level-complete banner. */
    fun bounce(view: View, delayMillis: Long = 0L) {
        view.postDelayed({
            view.animate()
                .scaleX(1.12f).scaleY(1.12f).setDuration(160L)
                .withEndAction {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(160L).start()
                }
                .start()
        }, delayMillis)
    }

    fun setVisible(view: View, visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun toast(context: Context, message: CharSequence) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun toast(context: Context, messageRes: Int) {
        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
    }

    fun bold(textView: TextView) {
        textView.setTypeface(Typeface.create(textView.typeface, Typeface.BOLD))
    }

    /** Applies a gentle vibration-free pop when a value changes in the HUD. */
    fun pulse(view: View) {
        view.animate().scaleX(1.18f).scaleY(1.18f).setDuration(90L)
            .withEndAction { view.animate().scaleX(1f).scaleY(1f).setDuration(120L).start() }
            .start()
    }

    fun fadeIn(view: View, durationMillis: Long = 220L) {
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(durationMillis).start()
    }

    /** Simple cross-fade used when swapping screens inside the same activity. */
    fun crossFade(outgoing: View, incoming: View) {
        incoming.alpha = 0f
        incoming.visibility = View.VISIBLE
        incoming.animate().alpha(1f).setDuration(180L).start()
        outgoing.animate().alpha(0f).setDuration(180L)
            .withEndAction { outgoing.visibility = View.GONE }
            .start()
    }

    /** Unused-animation guard: keeps the import list honest for future use. */
    @Suppress("unused")
    fun loadAnimation(context: Context, animationRes: Int): Animation =
        AnimationUtils.loadAnimation(context, animationRes)

    private const val PRESS_SCALE = 0.95f
}
