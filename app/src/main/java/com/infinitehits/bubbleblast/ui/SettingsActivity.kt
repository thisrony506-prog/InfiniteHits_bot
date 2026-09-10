package com.infinitehits.bubbleblast.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch
import com.infinitehits.bubbleblast.R
import com.infinitehits.bubbleblast.audio.AudioManager
import com.infinitehits.bubbleblast.ui.dialog.AboutDialog
import com.infinitehits.bubbleblast.ui.dialog.ConfirmDialog
import com.infinitehits.bubbleblast.util.Navigator
import com.infinitehits.bubbleblast.util.UiUtils

/** Sound, music, vibration, gameplay options, the reset button and the legal pages. */
class SettingsActivity : BaseActivity() {

    override val musicScene: AudioManager.Scene get() = AudioManager.Scene.HOME

    private lateinit var soundSwitch: MaterialSwitch
    private lateinit var musicSwitch: MaterialSwitch
    private lateinit var vibrationSwitch: MaterialSwitch
    private lateinit var trajectorySwitch: MaterialSwitch
    private lateinit var leftHandedSwitch: MaterialSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        UiUtils.applySystemBarInsets(findViewById(R.id.settings_root))

        soundSwitch = findViewById(R.id.settings_sound_switch)
        musicSwitch = findViewById(R.id.settings_music_switch)
        vibrationSwitch = findViewById(R.id.settings_vibration_switch)
        trajectorySwitch = findViewById(R.id.settings_trajectory_switch)
        leftHandedSwitch = findViewById(R.id.settings_left_handed_switch)

        findViewById<TextView>(R.id.settings_version_text)
            .text = getString(R.string.settings_version, services.versionName())

        val back = findViewById<ImageButton>(R.id.settings_back_button)
        UiUtils.attachPressEffect(back)
        back.setOnClickListener {
            playClick()
            finish()
        }

        val settings = services.settings

        soundSwitch.isChecked = settings.soundEnabled
        musicSwitch.isChecked = settings.musicEnabled
        vibrationSwitch.isChecked = settings.vibrationEnabled
        trajectorySwitch.isChecked = settings.showTrajectory
        leftHandedSwitch.isChecked = settings.leftHanded

        soundSwitch.setOnCheckedChangeListener { _, checked ->
            playClick()
            settings.soundEnabled = checked
            if (checked) services.audio.play(AudioManager.Sfx.POP)
        }
        musicSwitch.setOnCheckedChangeListener { _, checked ->
            playClick()
            settings.musicEnabled = checked
        }
        vibrationSwitch.setOnCheckedChangeListener { _, checked ->
            settings.vibrationEnabled = checked
            playClick()
        }
        trajectorySwitch.setOnCheckedChangeListener { _, checked ->
            playClick()
            settings.showTrajectory = checked
        }
        leftHandedSwitch.setOnCheckedChangeListener { _, checked ->
            playClick()
            settings.leftHanded = checked
        }

        attachRow(R.id.settings_privacy_button) { Navigator.toLegal(this, Navigator.LEGAL_PRIVACY) }
        attachRow(R.id.settings_terms_button) { Navigator.toLegal(this, Navigator.LEGAL_TERMS) }
        attachRow(R.id.settings_about_button) {
            AboutDialog(this, services.versionName()).show()
        }
        attachRow(R.id.settings_reset_button) { confirmReset() }
    }

    private fun attachRow(viewId: Int, action: () -> Unit) {
        val view: View = findViewById(viewId)
        UiUtils.attachPressEffect(view)
        view.setOnClickListener {
            playClick()
            action()
        }
    }

    private fun confirmReset() {
        ConfirmDialog(
            activity = this,
            title = getString(R.string.settings_reset_title),
            message = getString(R.string.settings_reset_message),
            positiveLabel = getString(R.string.settings_reset_confirm),
            negativeLabel = getString(R.string.settings_cancel),
            onConfirm = {
                services.save.resetProgress()
                services.resetInterstitialCounters()
                UiUtils.toast(this, R.string.settings_reset_done)
            }
        ).show()
    }

    override fun onResume() {
        super.onResume()
        // A different screen (or the same one after a reset) may have changed things.
        soundSwitch.isChecked = services.settings.soundEnabled
        musicSwitch.isChecked = services.settings.musicEnabled
        vibrationSwitch.isChecked = services.settings.vibrationEnabled
        trajectorySwitch.isChecked = services.settings.showTrajectory
        leftHandedSwitch.isChecked = services.settings.leftHanded
    }
}
