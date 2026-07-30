package com.example.dpadplayer

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import com.example.dpadplayer.db.PlaylistEntity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

/**
 * Shows songs in a single playlist with rename/delete options.
 * Pass [ARG_PLAYLIST_ID] as the playlist's Room ID.
 */
class PlaylistDetailFragment : Fragment() {

    companion object {
        const val ARG_PLAYLIST_ID = "playlist_id"
        fun newInstance(playlistId: Long) = PlaylistDetailFragment().apply {
            arguments = Bundle().also { it.putLong(ARG_PLAYLIST_ID, playlistId) }
        }
    }

    private val viewModel: MusicViewModel by activityViewModels()
    private var playlistId = -1L
    private val importM3uLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importM3u(uri)
        }
    private val exportM3uLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
            if (uri != null) exportM3u(uri)
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_playlist_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        playlistId = arguments?.getLong(ARG_PLAYLIST_ID, -1L) ?: -1L
        if (playlistId < 0) return

        val btnBack    = view.findViewById<MaterialButton>(R.id.btn_back)
        val tvTitle    = view.findViewById<TextView>(R.id.tv_playlist_title)
        val btnMenu    = view.findViewById<MaterialButton>(R.id.btn_playlist_menu)
        val recycler   = view.findViewById<RecyclerView>(R.id.recycler_playlist)

        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        applyPlayerControlFocusBackground(btnBack)
        btnBack.setupDpadItem(onFocusChanged = materialButtonFocusChangeHandler(btnBack)) {
            parentFragmentManager.popBackStack()
        }

        val adapter = TrackAdapter(
            items = emptyList(),
            onTrackClick = { index ->
                (activity as? MainActivity)?.playPlaylist(playlistId, index)
            },
            onMenuClick = { anchor, track, _ ->
                (activity as? MainActivity)?.showTrackMenu(anchor, track)
            }
        )
        adapter.menuClickListener = { anchor, track, _ ->
            val popup = android.widget.PopupMenu(requireContext(), anchor)
            popup.menu.add(0, 1, 0, "Remove from playlist")
            popup.setOnMenuItemClickListener { item ->
                if (item.itemId == 1) viewModel.removeSongFromPlaylist(playlistId, track.id)
                true
            }
            popup.show()
        }
        recycler.adapter = adapter
        recycler.layoutManager = FocusLinearLayoutManager(requireContext())

        var focusRequested = false

        // Observe playlists for name updates
        viewModel.playlists.observe(viewLifecycleOwner) { list ->
            val pl = list.find { it.id == playlistId }
            tvTitle.text = pl?.name ?: "Playlist"
        }

        // Observe playlist songs
        viewModel.observePlaylistTracks(playlistId).observe(viewLifecycleOwner) { tracks ->
            adapter.updateTracks(tracks)
            if (!focusRequested && tracks.isNotEmpty()) {
                focusRequested = true
                recycler.post {
                    val first = recycler.layoutManager?.findViewByPosition(0) ?: recycler.getChildAt(0)
                    (first?.findViewById<View?>(R.id.clickable_item) ?: first)?.requestFocus()
                }
            }
        }

        // Overflow menu: rename / delete
        applyPlayerControlFocusBackground(btnMenu)
        btnMenu.setupDpadItem(onFocusChanged = materialButtonFocusChangeHandler(btnMenu)) {
            val popup = PopupMenu(requireContext(), btnMenu)
            popup.menu.add(0, 1, 0, "Rename")
            popup.menu.add(0, 2, 1, "Delete playlist")
            popup.menu.add(0, 3, 2, "Import M3U")
            popup.menu.add(0, 4, 3, "Export M3U")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> showRenameDialog()
                    2 -> confirmDelete()
                    3 -> importM3uLauncher.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "*/*"))
                    4 -> {
                        val suggested = (viewModel.playlists.value?.find { it.id == playlistId }?.name ?: "playlist") + ".m3u"
                        exportM3uLauncher.launch(suggested)
                    }
                }
                true
            }
            popup.show()
        }
        btnMenu.setOnClickListener { anchor ->
            val popup = PopupMenu(requireContext(), anchor)
            popup.menu.add(0, 1, 0, "Rename")
            popup.menu.add(0, 2, 1, "Delete playlist")
            popup.menu.add(0, 3, 2, "Import M3U")
            popup.menu.add(0, 4, 3, "Export M3U")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> showRenameDialog()
                    2 -> confirmDelete()
                    3 -> importM3uLauncher.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "*/*"))
                    4 -> {
                        val suggested = (viewModel.playlists.value?.find { it.id == playlistId }?.name ?: "playlist") + ".m3u"
                        exportM3uLauncher.launch(suggested)
                    }
                }
                true
            }
            popup.show()
        }
    }

    private fun importM3u(uri: android.net.Uri) {
        val ctx = context ?: return
        lifecycleScope.launch {
            val content = PlaylistIo.readM3uFromUri(ctx, uri)
            if (content.isNullOrBlank()) {
                Toast.makeText(ctx, "Could not read M3U file", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val parsed = PlaylistIo.parseM3u(content)
            val allTracks = viewModel.tracks.value ?: emptyList()
            val resolved = PlaylistIo.resolveEntriesToTracks(parsed.entries, allTracks)
            if (resolved.isEmpty()) {
                Toast.makeText(ctx, "No matching local tracks found in M3U", Toast.LENGTH_SHORT).show()
                return@launch
            }
            viewModel.rewritePlaylist(playlistId, resolved)
            Toast.makeText(ctx, "Imported ${resolved.size} tracks", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportM3u(uri: android.net.Uri) {
        val ctx = context ?: return
        lifecycleScope.launch {
            val tracks = viewModel.resolvePlaylistTracks(playlistId)
            val name = viewModel.playlists.value?.find { it.id == playlistId }?.name ?: "Playlist"
            val content = PlaylistIo.buildM3uContent(name, tracks)
            val ok = PlaylistIo.writeM3uToUri(ctx, uri, content)
            Toast.makeText(ctx, if (ok) "Exported playlist" else "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRenameDialog() {
        val editText = EditText(requireContext())
        val current = viewModel.playlists.value?.find { it.id == playlistId }?.name ?: ""
        editText.setText(current)
        editText.selectAll()
        editText.requestFocus()
        AlertDialog.Builder(requireContext())
            .setTitle("Rename playlist")
            .setView(editText)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) viewModel.renamePlaylist(playlistId, newName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete() {
        val name = viewModel.playlists.value?.find { it.id == playlistId }?.name ?: "this playlist"
        AlertDialog.Builder(requireContext())
            .setTitle("Delete \"$name\"?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deletePlaylist(playlistId)
                parentFragmentManager.popBackStack()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
