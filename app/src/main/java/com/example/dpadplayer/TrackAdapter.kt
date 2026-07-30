package com.example.dpadplayer

import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import com.bumptech.glide.Glide
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.dpadplayer.playback.Track

class TrackAdapter(
    private var items: List<Track>,
    private val isQueue: Boolean = false,
    private val onTrackClick: (Int) -> Unit,
    private val onTrackLongClick: ((Int) -> Boolean)? = null,
    /** Optional: override the popup menu items. If null, default (Add to playlist) is used. */
    private val onMenuClick: ((anchor: View, track: Track, index: Int) -> Unit)? = null
) : RecyclerView.Adapter<TrackAdapter.VH>() {

    private var selectedIndex = -1

    /** Called from outside to bind a popup-menu handler. */
    var menuClickListener: ((anchor: View, track: Track, index: Int) -> Unit)? = onMenuClick

    fun updateTracks(newItems: List<Track>) {
        if (isQueue) {
            items = newItems.toList()
            notifyDataSetChanged()
            return
        }
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(o: Int, n: Int) = items[o].id == newItems[n].id
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == newItems[n]
        })
        items = newItems
        diff.dispatchUpdatesTo(this)
    }

    fun setSelectedIndex(index: Int) {
        val old = selectedIndex
        selectedIndex = index
        if (old in items.indices) notifyItemChanged(old)
        if (index in items.indices) notifyItemChanged(index)
    }

    fun moveItem(from: Int, to: Int): Boolean {
        if (!isQueue) return false
        if (from !in items.indices || to !in items.indices || from == to) return false
        val mutable = items.toMutableList()
        val moved = mutable.removeAt(from)
        mutable.add(to, moved)
        items = mutable
        notifyItemMoved(from, to)
        return true
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val art: ImageView     = view.findViewById(R.id.tv_item_art)
        val title: TextView    = view.findViewById(R.id.tv_item_title)
        val artist: TextView   = view.findViewById(R.id.tv_item_artist)
        val menuBtn: ImageView = view.findViewById(R.id.btn_track_menu)
        val indicator: View    = view.findViewById(R.id.playing_indicator)

        private val clickable: View = view.findViewById(R.id.clickable_item)

        init {
            // Use anrimian-style clickable overlay: background/ripple + focus are applied to clickable_item
            applyItemFocusBackground(clickable)
            clickable.setOnClickListener {
                try {
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                    onTrackClick(pos)
                } catch (e: Exception) {
                    // Log the exception so we can capture a stack trace in logcat when reproducing the crash.
                    Log.e("TrackAdapter", "onTrackClick failed for position=$bindingAdapterPosition", e)
                }
            }
            clickable.setOnLongClickListener {
                try {
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setOnLongClickListener false
                    onTrackLongClick?.invoke(pos) ?: false
                } catch (e: Exception) {
                    Log.e("TrackAdapter", "onTrackLongClick failed for position=$bindingAdapterPosition", e)
                    false
                }
            }
            clickable.setupDpadItem {
                try {
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setupDpadItem
                    onTrackClick(pos)
                } catch (e: Exception) {
                    Log.e("TrackAdapter", "setupDpadItem onTrackClick failed for position=$bindingAdapterPosition", e)
                }
            }
            menuBtn.setOnClickListener { v ->
                try {
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                    val track = items[pos]
                    val listener = menuClickListener
                    if (listener != null) listener(v, track, pos)
                    else showDefaultMenu(v, track)
                } catch (e: Exception) {
                    Log.e("TrackAdapter", "menuBtn click failed", e)
                }
            }
            applyItemFocusBackground(menuBtn)
            menuBtn.setupDpadItem { menuBtn.performClick() }
        }

        private fun showDefaultMenu(anchor: View, track: Track) {
            val ctx = anchor.context
            if (ctx is MainActivity) {
                ctx.showTrackMenu(anchor, track)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_track, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val track = items[position]
        holder.title.text  = track.title
        // Show "Artist · 3:45" in secondary line
        holder.artist.text = "${track.artist} · ${formatMs(track.duration)}"

        // Load album art thumbnail; fall back to music note placeholder
        Glide.with(holder.itemView)
            .load(track.albumArtUri)
            .placeholder(R.drawable.ic_music_note)
            .error(R.drawable.ic_music_note)
            .fallback(R.drawable.ic_music_note)
            .into(holder.art)

        val isActive = position == selectedIndex
        // Activate only the clickable_item overlay (not the whole row or menuBtn)
        // so the "now playing" highlight covers exactly what the focus highlight covers.
        val clickableItem = holder.itemView.findViewById<View?>(R.id.clickable_item)
        if (clickableItem != null) {
            clickableItem.isActivated = isActive
        } else {
            holder.itemView.isActivated = isActive
        }
        holder.indicator.visibility = if (isActive) View.VISIBLE else View.INVISIBLE
    }

    override fun getItemCount() = items.size

    private fun formatMs(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }
}
