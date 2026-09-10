package com.infinitehits.bubbleblast.ui

import android.os.Bundle
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.infinitehits.bubbleblast.R
import com.infinitehits.bubbleblast.audio.AudioManager
import com.infinitehits.bubbleblast.core.level.LevelCatalog
import com.infinitehits.bubbleblast.ui.adapter.LevelAdapter
import com.infinitehits.bubbleblast.util.Navigator
import com.infinitehits.bubbleblast.util.UiUtils

/** Scrollable grid of all 120 levels with lock state and stars. */
class LevelSelectActivity : BaseActivity() {

    override val musicScene: AudioManager.Scene get() = AudioManager.Scene.HOME

    private lateinit var adapter: LevelAdapter
    private lateinit var recycler: RecyclerView
    private var scrolledToCurrent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_level_select)
        UiUtils.applySystemBarInsets(findViewById(R.id.levels_root))

        adapter = LevelAdapter { level -> onLevelSelected(level) }
        recycler = findViewById(R.id.levels_recycler)
        recycler.layoutManager = GridLayoutManager(this, SPAN_COUNT)
        recycler.adapter = adapter
        recycler.setHasFixedSize(true)

        findViewById<android.widget.ImageButton>(R.id.levels_back_button).setOnClickListener {
            playClick()
            finish()
        }
        UiUtils.attachPressEffect(findViewById(R.id.levels_back_button))

        services.ads.preloadInterstitial(com.infinitehits.bubbleblast.ads.AdManager.Placement.LEVEL_SELECT_BANNER)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val save = services.save
        adapter.submit(save.unlockedLevel, save.starMap(), save.nextLevelToPlay())

        findViewById<TextView>(R.id.levels_stars_text)
            .text = getString(R.string.levels_stars_total, save.totalStars, LevelCatalog.totalStars)
        findViewById<TextView>(R.id.levels_coins_text).text = String.format("%,d", save.coins)

        val cleared = save.starMap().size
        findViewById<TextView>(R.id.levels_subtitle)
            .text = getString(R.string.levels_progress, cleared, LevelCatalog.LEVEL_COUNT)

        if (!scrolledToCurrent) {
            scrolledToCurrent = true
            val target = (save.nextLevelToPlay() - 1).coerceAtLeast(0)
            recycler.post { recycler.scrollToPosition(target.coerceAtMost(LevelCatalog.LEVEL_COUNT - 1)) }
        }
    }

    private fun onLevelSelected(level: Int) {
        if (!services.save.isLevelUnlocked(level)) {
            playClick()
            UiUtils.toast(this, getString(R.string.levels_locked, services.save.unlockedLevel))
            return
        }
        playClick()
        Navigator.toGame(this, level)
    }

    companion object {
        private const val SPAN_COUNT = 4
    }
}
