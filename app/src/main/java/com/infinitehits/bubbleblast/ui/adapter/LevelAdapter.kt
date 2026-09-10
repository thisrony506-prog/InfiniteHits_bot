package com.infinitehits.bubbleblast.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.infinitehits.bubbleblast.R
import com.infinitehits.bubbleblast.core.level.LevelCatalog
import com.infinitehits.bubbleblast.util.UiUtils

/**
 * Grid of levels for the level select screen.
 *
 * The adapter only ever binds 120 tiny tiles and re-uses them as the player
 * scrolls, so the screen stays smooth even on low-end devices.
 */
class LevelAdapter(private val onLevelClick: (Int) -> Unit) :
    RecyclerView.Adapter<LevelAdapter.LevelViewHolder>() {

    private var unlockedLevel: Int = 1
    private var starsByLevel: Map<Int, Int> = emptyMap()
    private var currentLevel: Int = 1

    fun submit(unlockedLevel: Int, starsByLevel: Map<Int, Int>, currentLevel: Int) {
        this.unlockedLevel = unlockedLevel
        this.starsByLevel = starsByLevel
        this.currentLevel = currentLevel
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = LevelCatalog.LEVEL_COUNT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LevelViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_level, parent, false)
        val margin = (parent.resources.displayMetrics.density * ITEM_MARGIN_DP).toInt()
        (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.setMargins(margin, margin, margin, margin)
            view.layoutParams = it
        }
        return LevelViewHolder(view)
    }

    override fun onBindViewHolder(holder: LevelViewHolder, position: Int) {
        val level = position + 1
        val unlocked = level <= unlockedLevel
        val stars = starsByLevel[level] ?: 0
        holder.bind(level, unlocked, stars, level == currentLevel, onLevelClick)
    }

    class LevelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val root: View = itemView
        private val number: TextView = itemView.findViewById(R.id.level_number)
        private val lock: ImageView = itemView.findViewById(R.id.level_lock)
        private val stars: List<ImageView> = listOf(
            itemView.findViewById(R.id.level_star_1),
            itemView.findViewById(R.id.level_star_2),
            itemView.findViewById(R.id.level_star_3)
        )

        fun bind(level: Int, unlocked: Boolean, starCount: Int, isCurrent: Boolean, onClick: (Int) -> Unit) {
            val context = itemView.context
            number.text = level.toString()

            val background = when {
                !unlocked -> R.drawable.bg_level_locked
                isCurrent -> R.drawable.bg_level_current
                else -> R.drawable.bg_level_unlocked
            }
            root.setBackgroundResource(background)

            number.alpha = if (unlocked) 1f else 0.55f
            UiUtils.setVisible(lock, !unlocked)
            number.visibility = if (unlocked) View.VISIBLE else View.INVISIBLE

            for ((index, star) in stars.entries.withIndex()) {
                val earned = starCount > index
                star.setImageResource(if (earned) R.drawable.ic_star else R.drawable.ic_star_empty)
                star.alpha = if (unlocked) 1f else 0.4f
                star.contentDescription = context.getString(
                    if (earned) R.string.cd_star_full else R.string.cd_star_empty
                )
            }

            root.isEnabled = unlocked
            root.alpha = if (unlocked) 1f else 0.9f
            UiUtils.attachPressEffect(root)
            root.setOnClickListener {
                UiUtils.pulse(root)
                onClick(level)
            }
        }
    }

    companion object {
        private const val ITEM_MARGIN_DP = 4f
    }
}
