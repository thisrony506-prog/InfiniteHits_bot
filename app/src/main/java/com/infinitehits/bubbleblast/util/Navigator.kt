package com.infinitehits.bubbleblast.util

import android.app.Activity
import android.content.Intent
import com.infinitehits.bubbleblast.ui.GameActivity
import com.infinitehits.bubbleblast.ui.HomeActivity
import com.infinitehits.bubbleblast.ui.LegalActivity
import com.infinitehits.bubbleblast.ui.LevelSelectActivity
import com.infinitehits.bubbleblast.ui.SettingsActivity

/** Central place for screen transitions and their extras. */
object Navigator {

    const val EXTRA_LEVEL = "extra_level"
    const val EXTRA_LEGAL_DOCUMENT = "extra_legal_document"

    const val LEGAL_PRIVACY = "privacy"
    const val LEGAL_TERMS = "terms"

    /** Screens never stack up: entering a new one finishes the previous. */
    private const val FLAG_REPLACE = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT

    fun toHome(activity: Activity) {
        activity.startActivity(Intent(activity, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
        fade(activity)
    }

    fun toLevelSelect(activity: Activity) {
        activity.startActivity(Intent(activity, LevelSelectActivity::class.java))
        fade(activity)
    }

    fun toGame(activity: Activity, level: Int) {
        activity.startActivity(Intent(activity, GameActivity::class.java).apply {
            putExtra(EXTRA_LEVEL, level)
        })
        fade(activity)
    }

    /** Restarts the level currently on screen. */
    fun restartGame(activity: Activity, level: Int) {
        activity.startActivity(Intent(activity, GameActivity::class.java).apply {
            putExtra(EXTRA_LEVEL, level)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        fade(activity)
    }

    fun toSettings(activity: Activity) {
        activity.startActivity(Intent(activity, SettingsActivity::class.java).apply {
            addFlags(FLAG_REPLACE)
        })
        fade(activity)
    }

    fun toLegal(activity: Activity, document: String) {
        activity.startActivity(Intent(activity, LegalActivity::class.java).apply {
            putExtra(EXTRA_LEGAL_DOCUMENT, document)
        })
        fade(activity)
    }

    fun toHomeClearingStack(activity: Activity) {
        activity.startActivity(Intent(activity, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        fade(activity)
    }

    @Suppress("DEPRECATION")
    private fun fade(activity: Activity) {
        try {
            activity.overridePendingTransition(
                com.infinitehits.bubbleblast.R.anim.screen_fade_in,
                com.infinitehits.bubbleblast.R.anim.screen_fade_out
            )
        } catch (ignored: Exception) {
            // Some OEM launchers dislike custom transitions; the default is fine.
        }
    }
}
