package com.example.dpadplayer

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import java.io.File


// ── Songs tab ────────────────────────────────────────────────────────────────

class SongsTabFragment : Fragment(), TabWithRecycler {
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: TrackAdapter
    private var lastFocusedPos = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_tab_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recycler = view.findViewById(R.id.recycler)
        lastFocusedPos = viewModel.getLibraryTabFocusPosition(0)
        adapter = TrackAdapter(
            items = emptyList(),
            onTrackClick = { index -> 
                val tracks = MusicViewModel.filterTracks(
                    viewModel.tracks.value ?: emptyList(),
                    viewModel.searchQuery.value.orEmpty()
                )
                if (tracks.isNotEmpty()) {
                    (activity as? MainActivity)?.playTracks(tracks, index)
                }
            }
        )
        adapter.menuClickListener = { anchor, track, _ ->
            showTrackMenu(anchor, track)
        }
        recycler.adapter = adapter
        val lm = FocusLinearLayoutManager(requireContext())
        lm.onFocusPosition = {
            lastFocusedPos = it
            viewModel.setLibraryTabFocusPosition(0, it)
        }
        recycler.layoutManager = lm

        fun refreshTracks() {
            val filtered = MusicViewModel.filterTracks(
                viewModel.tracks.value ?: emptyList(),
                viewModel.searchQuery.value.orEmpty()
            )
            adapter.updateTracks(filtered)
        }
        viewModel.tracks.observe(viewLifecycleOwner) { refreshTracks() }
        viewModel.searchQuery.observe(viewLifecycleOwner) { refreshTracks() }
        viewModel.currentIndex.observe(viewLifecycleOwner) { adapter.setSelectedIndex(it) }
    }

    private fun showTrackMenu(anchor: View, track: com.example.dpadplayer.playback.Track) {
        (activity as? MainActivity)?.showTrackMenu(anchor, track)
    }

    override fun recyclerView(): RecyclerView = recycler

    override fun requestInitialFocus() {
        // Try to focus the currently selected track or the first visible child.
        recycler.post {
            val preferred = lastFocusedPos.takeIf { it >= 0 } ?: (viewModel.currentIndex.value ?: 0)
            val lm = recycler.layoutManager
            var target: View? = null
            try { target = lm?.findViewByPosition(preferred) } catch (_: Exception) { }
            if (target == null && recycler.childCount > 0) target = recycler.getChildAt(0)
            val clickable = target?.findViewById<View?>(R.id.clickable_item) ?: target
            Log.d("DPAD_FOCUS", "SongsTab.requestInitialFocus preferred=$preferred target=${clickable?.id} success=${clickable?.requestFocus()}")
        }
    }
}

// ── Albums tab ───────────────────────────────────────────────────────────────

class AlbumsTabFragment : Fragment(), TabWithRecycler {
    private val viewModel: MusicViewModel by activityViewModels()
    private var recyclerRef: RecyclerView? = null
    private var lastFocusedPos = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_tab_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recyclerRef = recycler
        lastFocusedPos = viewModel.getLibraryTabFocusPosition(1)
        val adapter = AlbumAdapter(emptyList()) { album ->
            (activity as? MainActivity)?.openAlbumDetail(album)
        }
        recycler.adapter = adapter
        val lm = FocusLinearLayoutManager(requireContext())
        lm.onFocusPosition = {
            lastFocusedPos = it
            viewModel.setLibraryTabFocusPosition(1, it)
        }
        recycler.layoutManager = lm
        fun refreshAlbums() {
            val filtered = MusicViewModel.filterAlbums(
                viewModel.albums.value ?: emptyList(),
                viewModel.searchQuery.value.orEmpty()
            )
            adapter.update(filtered)
        }
        viewModel.albums.observe(viewLifecycleOwner) { refreshAlbums() }
        viewModel.searchQuery.observe(viewLifecycleOwner) { refreshAlbums() }
    }

    override fun recyclerView(): RecyclerView? = recyclerRef

    override fun requestInitialFocus() {
        recyclerRef?.post {
            val lm = recyclerRef?.layoutManager
            val target = try { lm?.findViewByPosition(lastFocusedPos) } catch (_: Exception) { null }
            val child = (target ?: recyclerRef?.getChildAt(0))
            val clickable = child?.findViewById<View?>(R.id.clickable_item) ?: child
            Log.d("DPAD_FOCUS", "AlbumsTab.requestInitialFocus target=${clickable?.id} success=${clickable?.requestFocus()}")
        }
    }
}

// ── Artists tab ──────────────────────────────────────────────────────────────

class ArtistsTabFragment : Fragment(), TabWithRecycler {
    private val viewModel: MusicViewModel by activityViewModels()
    private var recyclerRef: RecyclerView? = null
    private var lastFocusedPos = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_tab_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recyclerRef = recycler
        lastFocusedPos = viewModel.getLibraryTabFocusPosition(2)
        val adapter = ArtistAdapter(emptyList()) { artist ->
            (activity as? MainActivity)?.openArtistDetail(artist)
        }
        recycler.adapter = adapter
        val lm = FocusLinearLayoutManager(requireContext())
        lm.onFocusPosition = {
            lastFocusedPos = it
            viewModel.setLibraryTabFocusPosition(2, it)
        }
        recycler.layoutManager = lm
        fun refreshArtists() {
            val filtered = MusicViewModel.filterArtists(
                viewModel.artists.value ?: emptyList(),
                viewModel.searchQuery.value.orEmpty()
            )
            adapter.update(filtered)
        }
        viewModel.artists.observe(viewLifecycleOwner) { refreshArtists() }
        viewModel.searchQuery.observe(viewLifecycleOwner) { refreshArtists() }
    }

    override fun recyclerView(): RecyclerView? = recyclerRef

    override fun requestInitialFocus() {
        recyclerRef?.post {
            val lm = recyclerRef?.layoutManager
            val target = try { lm?.findViewByPosition(lastFocusedPos) } catch (_: Exception) { null }
            val child = (target ?: recyclerRef?.getChildAt(0))
            val clickable = child?.findViewById<View?>(R.id.clickable_item) ?: child
            Log.d("DPAD_FOCUS", "ArtistsTab.requestInitialFocus target=${clickable?.id} success=${clickable?.requestFocus()}")
        }
    }
}

// ── Genres tab ───────────────────────────────────────────────────────────────

class GenresTabFragment : Fragment(), TabWithRecycler {
    private val viewModel: MusicViewModel by activityViewModels()
    private var recyclerRef: RecyclerView? = null
    private var lastFocusedPos = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_tab_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recyclerRef = recycler
        lastFocusedPos = viewModel.getLibraryTabFocusPosition(3)
        val adapter = GenreAdapter(emptyList()) { genre ->
            (activity as? MainActivity)?.openGenreDetail(genre)
        }
        recycler.adapter = adapter
        val lm = FocusLinearLayoutManager(requireContext())
        lm.onFocusPosition = {
            lastFocusedPos = it
            viewModel.setLibraryTabFocusPosition(3, it)
        }
        recycler.layoutManager = lm
        fun refreshGenres() {
            val filtered = MusicViewModel.filterGenres(
                viewModel.genres.value ?: emptyList(),
                viewModel.searchQuery.value.orEmpty()
            )
            adapter.update(filtered)
        }
        viewModel.genres.observe(viewLifecycleOwner) { refreshGenres() }
        viewModel.searchQuery.observe(viewLifecycleOwner) { refreshGenres() }
    }

    override fun recyclerView(): RecyclerView? = recyclerRef

    override fun requestInitialFocus() {
        val rv = recyclerRef ?: return
        // If adapter has items, focus immediately; otherwise wait for first data load
        if ((rv.adapter?.itemCount ?: 0) > 0) {
            focusFirstItem(rv)
        } else {
            // Data not loaded yet — fire once when it arrives
            val observer = object : androidx.lifecycle.Observer<List<Genre>> {
                override fun onChanged(value: List<Genre>) {
                    if (value.isNotEmpty()) {
                        focusFirstItem(rv)
                        viewModel.genres.removeObserver(this)
                    }
                }
            }
            viewModel.genres.observe(viewLifecycleOwner, observer)
        }
    }

    private fun focusFirstItem(rv: RecyclerView) {
        rv.post {
            val lm = rv.layoutManager
            val target = try { lm?.findViewByPosition(lastFocusedPos) } catch (_: Exception) { null }
            val child = target ?: rv.getChildAt(0)
            val clickable = child?.findViewById<View?>(R.id.clickable_item) ?: child
            Log.d("DPAD_FOCUS", "GenresTab.focusFirstItem target=${clickable?.id} success=${clickable?.requestFocus()}")
        }
    }
}

// ── Playlists tab ─────────────────────────────────────────────────────────────

class PlaylistsTabFragment : Fragment(), TabWithRecycler {
    private val viewModel: MusicViewModel by activityViewModels()
    private var recyclerRef: RecyclerView? = null
    private var lastFocusedPos = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_tab_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recyclerRef = recycler
        lastFocusedPos = viewModel.getLibraryTabFocusPosition(4)

        val adapter = PlaylistAdapter(
            items = emptyList(),
            onPlaylistClick = { playlist -> (activity as? MainActivity)?.openPlaylistDetail(playlist) }
        )
        adapter.onCreateClick = { showCreatePlaylistDialog() }
        recycler.adapter = adapter
        val lm = FocusLinearLayoutManager(requireContext())
        lm.onFocusPosition = {
            lastFocusedPos = it
            viewModel.setLibraryTabFocusPosition(4, it)
        }
        recycler.layoutManager = lm
        fun refreshPlaylists() {
            val filtered = MusicViewModel.filterPlaylists(
                viewModel.playlists.value ?: emptyList(),
                viewModel.searchQuery.value.orEmpty()
            )
            adapter.update(filtered)
        }
        viewModel.playlists.observe(viewLifecycleOwner) { refreshPlaylists() }
        viewModel.searchQuery.observe(viewLifecycleOwner) { refreshPlaylists() }
    }

    private fun showCreatePlaylistDialog() {
        val editText = EditText(requireContext())
        editText.hint = "Playlist name"
        editText.requestFocus()
        AlertDialog.Builder(requireContext())
            .setTitle("New playlist")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) viewModel.createPlaylist(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun recyclerView(): RecyclerView? = recyclerRef

    override fun requestInitialFocus() {
        recyclerRef?.post {
            val lm = recyclerRef?.layoutManager
            val target = try { lm?.findViewByPosition(lastFocusedPos) } catch (_: Exception) { null }
            val child = (target ?: recyclerRef?.getChildAt(0))
            val clickable = child?.findViewById<View?>(R.id.clickable_item) ?: child
            Log.d("DPAD_FOCUS", "PlaylistsTab.requestInitialFocus target=${clickable?.id} success=${clickable?.requestFocus()}")
        }
    }
}

class RecentlyPlayedTabFragment : Fragment(), TabWithRecycler {
    private val viewModel: MusicViewModel by activityViewModels()
    private var recyclerRef: RecyclerView? = null
    private var lastFocusedPos = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_tab_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recyclerRef = recycler
        lastFocusedPos = viewModel.getLibraryTabFocusPosition(6)

        val adapter = TrackAdapter(
            items = emptyList(),
            onTrackClick = { index ->
                val tracks = viewModel.recentlyPlayed.value ?: emptyList()
                if (tracks.isNotEmpty()) {
                    (activity as? MainActivity)?.playTracks(tracks, index)
                }
            }
        )
        adapter.menuClickListener = { anchor, track, _ ->
            (activity as? MainActivity)?.showTrackMenu(anchor, track)
        }
        recycler.adapter = adapter
        val lm = FocusLinearLayoutManager(requireContext())
        lm.onFocusPosition = {
            lastFocusedPos = it
            viewModel.setLibraryTabFocusPosition(6, it)
        }
        recycler.layoutManager = lm

        viewModel.recentlyPlayed.observe(viewLifecycleOwner) { adapter.updateTracks(it) }
    }

    override fun recyclerView(): RecyclerView? = recyclerRef

    override fun requestInitialFocus() {
        recyclerRef?.post {
            val lm = recyclerRef?.layoutManager
            val target = try { lm?.findViewByPosition(lastFocusedPos) } catch (_: Exception) { null }
            val child = (target ?: recyclerRef?.getChildAt(0))
            val clickable = child?.findViewById<View?>(R.id.clickable_item) ?: child
            clickable?.requestFocus()
        }
    }
}

class MostPlayedTabFragment : Fragment(), TabWithRecycler {
    private val viewModel: MusicViewModel by activityViewModels()
    private var recyclerRef: RecyclerView? = null
    private var lastFocusedPos = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_tab_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recyclerRef = recycler
        lastFocusedPos = viewModel.getLibraryTabFocusPosition(7)

        val adapter = TrackAdapter(
            items = emptyList(),
            onTrackClick = { index ->
                val tracks = viewModel.mostPlayed.value ?: emptyList()
                if (tracks.isNotEmpty()) {
                    (activity as? MainActivity)?.playTracks(tracks, index)
                }
            }
        )
        adapter.menuClickListener = { anchor, track, _ ->
            (activity as? MainActivity)?.showTrackMenu(anchor, track)
        }
        recycler.adapter = adapter
        val lm = FocusLinearLayoutManager(requireContext())
        lm.onFocusPosition = {
            lastFocusedPos = it
            viewModel.setLibraryTabFocusPosition(7, it)
        }
        recycler.layoutManager = lm

        viewModel.mostPlayed.observe(viewLifecycleOwner) { adapter.updateTracks(it) }
    }

    override fun recyclerView(): RecyclerView? = recyclerRef

    override fun requestInitialFocus() {
        recyclerRef?.post {
            val lm = recyclerRef?.layoutManager
            val target = try { lm?.findViewByPosition(lastFocusedPos) } catch (_: Exception) { null }
            val child = (target ?: recyclerRef?.getChildAt(0))
            val clickable = child?.findViewById<View?>(R.id.clickable_item) ?: child
            clickable?.requestFocus()
        }
    }
}

// ── ViewPager2 adapter ────────────────────────────────────────────────────────

class LibraryPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount() = 8
    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> SongsTabFragment()
        1 -> AlbumsTabFragment()
        2 -> ArtistsTabFragment()
        3 -> GenresTabFragment()
        4 -> PlaylistsTabFragment()
        5 -> FoldersTabFragment()
        6 -> RecentlyPlayedTabFragment()
        7 -> MostPlayedTabFragment()
        else -> SongsTabFragment()
    }
}

// New: Folders tab — simple folder listing
class FoldersTabFragment : Fragment(), TabWithRecycler {
    private val viewModel: MusicViewModel by activityViewModels()
    private var recyclerRef: RecyclerView? = null
    private var lastFocusedPos = 0
    private val folders = mutableListOf<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_tab_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recyclerRef = recycler
        lastFocusedPos = viewModel.getLibraryTabFocusPosition(5)
        val adapter = FolderAdapter(folders) { folder ->
            (activity as? MainActivity)?.openFolderDetail(folder)
        }
        recycler.adapter = adapter
        val lm = FocusLinearLayoutManager(requireContext())
        lm.onFocusPosition = {
            lastFocusedPos = it
            viewModel.setLibraryTabFocusPosition(5, it)
        }
        recycler.layoutManager = lm

        fun refreshFolders() {
            val tracks = viewModel.tracks.value ?: emptyList()
            val dirs = tracks.mapNotNull { it.filePath.takeIf { p -> p.isNotBlank() } }
                .map { File(it).parent ?: it }
                .distinct()
                .sorted()
            val filtered = MusicViewModel.filterFolders(dirs, viewModel.searchQuery.value.orEmpty())
            folders.clear()
            folders.addAll(filtered)
            (recycler.adapter as? FolderAdapter)?.update(folders)
        }
        viewModel.tracks.observe(viewLifecycleOwner) { refreshFolders() }
        viewModel.searchQuery.observe(viewLifecycleOwner) { refreshFolders() }
    }

    override fun recyclerView(): RecyclerView? = recyclerRef

    override fun requestInitialFocus() {
        recyclerRef?.post {
            val lm = recyclerRef?.layoutManager
            val target = try { lm?.findViewByPosition(lastFocusedPos) } catch (_: Exception) { null }
            val child = (target ?: recyclerRef?.getChildAt(0))
            val clickable = child?.findViewById<View?>(R.id.clickable_item) ?: child
            clickable?.requestFocus()
        }
    }
}
