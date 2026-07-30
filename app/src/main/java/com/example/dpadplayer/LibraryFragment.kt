package com.example.dpadplayer

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.EditText
import android.widget.TextView
import com.bumptech.glide.Glide
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import android.content.SharedPreferences
import android.util.Log
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.tabs.TabLayoutMediator

class LibraryFragment : Fragment() {

    private val viewModel: MusicViewModel by activityViewModels()

    private lateinit var viewPager: androidx.viewpager2.widget.ViewPager2
    private lateinit var tabLayout: com.google.android.material.tabs.TabLayout
    private lateinit var btnBack: MaterialButton
    private lateinit var btnSearch: MaterialButton
    private lateinit var miniArt: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniBtnPlay: MaterialButton
    private lateinit var miniBtnNext: MaterialButton
    private lateinit var miniProgress: LinearProgressIndicator
    private lateinit var miniOpenPlayer: View
    private var restoringInitialTab = false
    private var requestedInitialTab = 0

    private val tabTitles = listOf("Songs", "Albums", "Artists", "Genres", "Playlists", "Folders", "Recent", "Top")

    companion object {
        private const val ARG_TAB = "tab"
        fun newInstance(tabIndex: Int = 0) = LibraryFragment().apply {
            arguments = Bundle().also { it.putInt(ARG_TAB, tabIndex) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_library, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewPager       = view.findViewById(R.id.view_pager)
        tabLayout       = view.findViewById(R.id.tab_layout)
        btnBack         = view.findViewById(R.id.btn_back)
        btnSearch       = view.findViewById(R.id.btn_search)
        miniArt         = view.findViewById(R.id.mini_art)
        miniTitle       = view.findViewById(R.id.mini_title)
        miniArtist      = view.findViewById(R.id.mini_artist)
        miniBtnPlay     = view.findViewById(R.id.mini_btn_play)
        miniBtnNext     = view.findViewById(R.id.mini_btn_next)
        miniProgress    = view.findViewById(R.id.mini_progress)
        miniOpenPlayer  = view.findViewById(R.id.mini_open_player)

        // Setup ViewPager2 with tabs
        viewPager.adapter = LibraryPagerAdapter(this)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
        tabLayout.isFocusable = false
        tabLayout.isFocusableInTouchMode = false
        tabLayout.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        styleTabViews()

        // Apply "show_top_bar" preference immediately
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val showTopBar = prefs.getBoolean("show_top_bar", true)
        tabLayout.visibility = if (showTopBar) View.VISIBLE else View.GONE
        viewPager.visibility = View.VISIBLE

        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)

        // ViewPager must not steal focus directly, but it acts as a group
        viewPager.isFocusable = true
        viewPager.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        viewPager.registerOnPageChangeCallback(object: androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (restoringInitialTab && position != requestedInitialTab) {
                    Log.d("DPAD_FOCUS", "Ignoring unexpected onPageSelected position=$position while restoring tab=$requestedInitialTab")
                    return
                }
                Log.d("DPAD_FOCUS", "ViewPager onPageSelected: position=$position (old activeTabPosition=${viewModel.activeLibraryTab})")
                viewModel.activeLibraryTab = position
                requestFocusForTab(position)
            }
        })

        // Restore tab position from memory or arguments
        requestedInitialTab = if (viewModel.activeLibraryTab >= 0) viewModel.activeLibraryTab else (arguments?.getInt(ARG_TAB, 0) ?: 0)
        restoringInitialTab = true
        viewPager.setCurrentItem(requestedInitialTab, false)

        // Fire initial focus for the starting tab once the pager settles.
        viewPager.post {
            if (viewPager.currentItem != requestedInitialTab) {
                viewPager.setCurrentItem(requestedInitialTab, false)
            }
            restoringInitialTab = false
            requestFocusForTab(viewPager.currentItem)
        }

        miniTitle.isSelected = true

        // Back button
        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        applyPlayerControlFocusBackground(btnBack)
        btnBack.setupDpadItem(onFocusChanged = materialButtonFocusChangeHandler(btnBack)) {
            parentFragmentManager.popBackStack()
        }

        btnSearch.setOnClickListener { openSearchDialog() }
        applyPlayerControlFocusBackground(btnSearch)
        btnSearch.setupDpadItem(onFocusChanged = materialButtonFocusChangeHandler(btnSearch)) {
            openSearchDialog()
        }

        // Mini-player: left area opens full player
        applyMiniPlayerFocusBackground(miniOpenPlayer)
        miniOpenPlayer.setOnClickListener { (activity as? MainActivity)?.openPlayer() }
        miniOpenPlayer.setupDpadItem { (activity as? MainActivity)?.openPlayer() }

        // Play / Next buttons
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
                        requestFocusForCurrentTab()
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
        Log.d("DPAD_FOCUS", "LibraryFragment.onResume — re-requesting focus into active tab")
        viewPager.post {
            requestFocusForCurrentTab()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        } catch (_: Exception) { }
    }

    private val prefChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "show_top_bar") {
                val show = prefs.getBoolean(key, true)
                tabLayout.visibility = if (show) View.VISIBLE else View.GONE
                viewPager.visibility = View.VISIBLE
            }
        }

    fun selectTab(index: Int) {
        if (::viewPager.isInitialized) {
            viewModel.activeLibraryTab = index
            viewPager.setCurrentItem(index, true)
        }
    }

    private fun styleTabViews() {
        tabLayout.post {
            val tabStrip = tabLayout.getChildAt(0) as? ViewGroup ?: return@post
            val horizontalMargin = (4 * resources.displayMetrics.density).toInt()
            val verticalMargin = (4 * resources.displayMetrics.density).toInt()
            for (i in 0 until tabStrip.childCount) {
                val tabView = tabStrip.getChildAt(i)
                applyTopBarTabFocusBackground(tabView)
                tabView.isFocusable = true
                tabView.isFocusableInTouchMode = false

                val params = tabView.layoutParams as? ViewGroup.MarginLayoutParams ?: continue
                if (params.marginStart != horizontalMargin ||
                    params.marginEnd != horizontalMargin ||
                    params.topMargin != verticalMargin ||
                    params.bottomMargin != verticalMargin) {
                    params.marginStart = horizontalMargin
                    params.marginEnd = horizontalMargin
                    params.topMargin = verticalMargin
                    params.bottomMargin = verticalMargin
                    tabView.layoutParams = params
                }
            }
        }
    }

    private fun requestFocusForTab(position: Int) {
        val tag = "f$position"
        val f = childFragmentManager.findFragmentByTag(tag)
        Log.d("DPAD_FOCUS", "requestFocusForTab position=$position tag=$tag fragment=$f")
        if (f is TabWithRecycler) {
            f.requestInitialFocus()
            return
        }
        for (frag in childFragmentManager.fragments) {
            if (frag is TabWithRecycler && frag.isResumed) {
                frag.requestInitialFocus()
                break
            }
        }
    }

    private fun requestFocusForCurrentTab() {
        val currentItem = viewModel.activeLibraryTab.takeIf { it >= 0 } ?: viewPager.currentItem
        if (viewPager.currentItem != currentItem) {
            viewPager.setCurrentItem(currentItem, false)
        }
        Log.d("DPAD_FOCUS", "LibraryFragment.onResume — active tab is position=$currentItem")
        requestFocusForTab(currentItem)
    }

    private fun observeViewModel() {
        viewModel.currentIndex.observe(viewLifecycleOwner) { _ -> refreshMiniPlayer() }
        viewModel.isPlaying.observe(viewLifecycleOwner)   { playing -> updateMiniPlayIcon(playing) }
        viewModel.position.observe(viewLifecycleOwner)    { pos -> updateProgressBar(pos) }
        viewModel.searchQuery.observe(viewLifecycleOwner) { query ->
            val suffix = if (query.isBlank()) "" else " (${query.trim()})"
            view?.findViewById<TextView>(R.id.tv_app_title)?.text = "DPad Player$suffix"
        }
    }

    private fun openSearchDialog() {
        val current = viewModel.searchQuery.value.orEmpty()
        val input = EditText(requireContext()).apply {
            setText(current)
            setSelection(text.length)
            hint = "Search songs, albums, artists..."
        }
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Search library")
            .setView(input)
            .setPositiveButton("Apply") { _, _ ->
                viewModel.setSearchQuery(input.text.toString())
            }
            .setNeutralButton("Clear") { _, _ ->
                viewModel.setSearchQuery("")
            }
            .setNegativeButton("Cancel", null)
            .show()
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
