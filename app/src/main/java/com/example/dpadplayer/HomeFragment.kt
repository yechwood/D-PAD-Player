package com.example.dpadplayer

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * iPod-style home screen: a simple list of music categories.
 * Each row navigates to the corresponding list screen.
 */
class HomeFragment : Fragment() {

    private val viewModel: MusicViewModel by activityViewModels()

    private lateinit var miniPlayer: View
    private lateinit var miniOpenPlayer: View
    private lateinit var miniArt: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniBtnPlay: MaterialButton
    private lateinit var miniBtnNext: MaterialButton
    private lateinit var miniProgress: LinearProgressIndicator

    data class MenuItem(val labelRes: Int, val iconRes: Int, val tag: String)

    private var lastFocusedPos = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lastFocusedPos = viewModel.homeMenuFocusPos

        miniPlayer   = view.findViewById(R.id.mini_player)
        miniOpenPlayer = view.findViewById(R.id.mini_open_player)
        miniArt      = view.findViewById(R.id.mini_art)
        miniTitle    = view.findViewById(R.id.mini_title)
        miniArtist   = view.findViewById(R.id.mini_artist)
        miniBtnPlay  = view.findViewById(R.id.mini_btn_play)
        miniBtnNext  = view.findViewById(R.id.mini_btn_next)
        miniProgress = view.findViewById(R.id.mini_progress)

        miniTitle.isSelected = true

        val menuItems = listOf(
            MenuItem(R.string.menu_songs,     R.drawable.ic_music_note,    "songs"),
            MenuItem(R.string.menu_albums,    R.drawable.ic_album,         "albums"),
            MenuItem(R.string.menu_artists,   R.drawable.ic_account_circle,"artists"),
            MenuItem(R.string.menu_genres,    R.drawable.ic_genre,         "genres"),
            MenuItem(R.string.menu_playlists, R.drawable.ic_queue_music,   "playlists"),
            MenuItem(R.string.menu_settings,  R.drawable.ic_more_vert,     "settings"),
        )

        val recycler = view.findViewById<RecyclerView>(R.id.recycler_menu)
        val lm = FocusLinearLayoutManager(requireContext())
        lm.onFocusPosition = {
            lastFocusedPos = it
            viewModel.homeMenuFocusPos = it
        }
        recycler.layoutManager = lm
        recycler.adapter = HomeMenuAdapter(menuItems) { item ->
            when (item.tag) {
                "songs"     -> (activity as? MainActivity)?.openLibraryTab(0)
                "albums"    -> (activity as? MainActivity)?.openLibraryTab(1)
                "artists"   -> (activity as? MainActivity)?.openLibraryTab(2)
                "genres"    -> (activity as? MainActivity)?.openLibraryTab(3)
                "playlists" -> (activity as? MainActivity)?.openLibraryTab(4)
                "settings"  -> (activity as? MainActivity)?.openSettings()
            }
        }

        // Request focus on the last-focused item once the recycler is laid out
        focusMenuItem(recycler)

        miniPlayer.setOnClickListener(null)
        applyMiniPlayerFocusBackground(miniOpenPlayer)
        miniOpenPlayer.setOnClickListener { (activity as? MainActivity)?.openPlayer() }
        miniOpenPlayer.setupDpadItem { (activity as? MainActivity)?.openPlayer() }

        applyMiniPlayerFocusBackground(miniBtnPlay)
        miniBtnPlay.setupDpadItem(onFocusChanged = materialButtonFocusChangeHandler(miniBtnPlay)) {
            (activity as? MainActivity)?.togglePlayPause()
        }
        miniBtnPlay.setOnClickListener { (activity as? MainActivity)?.togglePlayPause() }
        applyMiniPlayerFocusBackground(miniBtnNext)
        miniBtnNext.setupDpadItem(onFocusChanged = materialButtonFocusChangeHandler(miniBtnNext)) {
            (activity as? MainActivity)?.sendCmd("NEXT")
        }
        miniBtnNext.setOnClickListener { (activity as? MainActivity)?.sendCmd("NEXT") }
        listOf(miniOpenPlayer, miniBtnPlay, miniBtnNext).forEach { miniControl ->
            miniControl.setOnKeyListener { v, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        focusMenuItem(recycler)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> {
                        v.performClick()
                        true
                    }
                    else -> false
                }
            }
        }

        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        val recycler = view?.findViewById<RecyclerView>(R.id.recycler_menu) ?: return
        focusMenuItem(recycler)
    }

    private fun focusMenuItem(recycler: RecyclerView) {
        fun requestFocusNow() {
            if (!isAdded || view == null || !isVisible) return
            val child = recycler.findViewHolderForAdapterPosition(lastFocusedPos)?.itemView
                ?: recycler.findViewHolderForAdapterPosition(0)?.itemView
            val target = child?.findViewById<View>(R.id.clickable_item) ?: child
            target?.requestFocus()
        }
        if (recycler.isLaidOut) {
            requestFocusNow()
        } else {
            recycler.post {
                if (!isAdded || view == null || !isVisible) return@post
                requestFocusNow()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.currentIndex.observe(viewLifecycleOwner) { refreshMiniPlayer() }
        viewModel.isPlaying.observe(viewLifecycleOwner)   { updateMiniPlayIcon(it) }
        viewModel.position.observe(viewLifecycleOwner)    { updateProgressBar(it) }
    }

    private fun refreshMiniPlayer() {
        val activity = activity as? MainActivity ?: return
        val track = activity.currentTrack()
        if (track == null) {
            miniTitle.text = "No track playing"
            miniArtist.text = ""
            miniProgress.max = 1
            miniProgress.progress = 0
            miniArt.setImageResource(R.drawable.ic_music_note)
            return
        }
        miniTitle.text  = track.title
        miniArtist.text = track.artist
        miniProgress.max = track.duration.coerceAtLeast(1L).toInt()
        Glide.with(this)
            .load(track.albumArtUri)
            .placeholder(R.drawable.ic_music_note)
            .error(R.drawable.ic_music_note)
            .fallback(R.drawable.ic_music_note)
            .into(miniArt)
        updateProgressBar(viewModel.position.value ?: 0L)
    }

    private fun updateMiniPlayIcon(isPlaying: Boolean) {
        val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        miniBtnPlay.icon = ContextCompat.getDrawable(requireContext(), icon)
    }

    private fun updateProgressBar(pos: Long) {
        val max = miniProgress.max.coerceAtLeast(1)
        val clamped = pos.coerceIn(0L, max.toLong())
        miniProgress.progress = clamped.toInt()
    }
}

// ── Home menu adapter ─────────────────────────────────────────────────────────

class HomeMenuAdapter(
    private val items: List<HomeFragment.MenuItem>,
    private val onClick: (HomeFragment.MenuItem) -> Unit
) : RecyclerView.Adapter<HomeMenuAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView  = view.findViewById(R.id.iv_menu_icon)
        val label: TextView  = view.findViewById(R.id.tv_menu_label)

        init {
            val clickable = view.findViewById<View>(R.id.clickable_item) ?: view
            applyItemFocusBackground(clickable)
            clickable.setOnClickListener { onClick(items[bindingAdapterPosition]) }
            clickable.setupDpadItem { onClick(items[bindingAdapterPosition]) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_home_menu, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.label.setText(item.labelRes)
        holder.icon.setImageResource(item.iconRes)
    }

    override fun getItemCount() = items.size
}
