package com.example.dpadplayer

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.example.dpadplayer.playback.PlaybackService
import com.example.dpadplayer.playback.Track
import com.example.dpadplayer.db.PlaylistEntity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    val viewModel: MusicViewModel by viewModels()

    // Service binding
    private var service: PlaybackService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as PlaybackService.LocalBinder).getService()
            bound = true
            // Push current track list into service only if the service queue is empty
            viewModel.tracks.value?.let { tracks ->
                if (service!!.tracks.isEmpty() && tracks.isNotEmpty()) {
                    service!!.tracks.addAll(tracks)
                    // (Optional) service!!.notifyQueueChanged() if you exposed it publicly
                }
            }
            // Restore selected index in adapter
            val idx = service!!.currentIndex
            if (idx >= 0) viewModel.setCurrentIndex(idx)
            
            // Set initial queue state to ViewModel
            viewModel.setQueue(service!!.tracks.toList())
            
            setupServiceCallbacks()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            bound = false
            service = null
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (hasStoragePermission()) onPermissionGranted()
            else showStoragePermissionRequiredDialog()
        }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply saved accent + night mode before inflating
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        SettingsFragment.applyTheme(prefs.getString("theme", "system") ?: "system")
        setTheme(SettingsFragment.accentThemeRes(prefs.getString("accent", "deep_purple") ?: "deep_purple"))

        setContentView(R.layout.activity_main)

        // Show HomeFragment as the root screen
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment(), TAG_HOME)
                .commit()
        }

        checkPermissionsAndLoad()
    }

    override fun onStart() {
        super.onStart()
        Intent(this, PlaybackService::class.java).also { intent ->
            ContextCompat.startForegroundService(this, intent)
            bindService(intent, connection, BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        android.util.Log.d("MainAct", "onStop bound=$bound")
        if (bound) {
            service?.onTrackChanged         = null
            service?.onPlaybackStateChanged = null
            service?.onPositionChanged      = null
            service?.onShuffleChanged       = null
            service?.onRepeatChanged        = null
            service?.onQueueChanged         = null
            service?.onSleepTimerChanged    = null
            unbindService(connection)
            bound = false
        }
    }

    // ── Fragment navigation ────────────────────────────────────────────────────

    fun openLibraryTab(tabIndex: Int) {
        viewModel.activeLibraryTab = tabIndex
        if (supportFragmentManager.findFragmentByTag(TAG_LIBRARY)?.isVisible == true) return
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right,
                                 android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.fragment_container, LibraryFragment.newInstance(tabIndex), TAG_LIBRARY)
            .addToBackStack(null)
            .commit()
    }

    fun openSettings() {
        if (supportFragmentManager.findFragmentByTag(TAG_SETTINGS)?.isVisible == true) return
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right,
                                 android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.fragment_container, SettingsFragment(), TAG_SETTINGS)
            .addToBackStack(null)
            .commit()
    }

    fun openPlayer() {
        if (supportFragmentManager.findFragmentByTag(TAG_PLAYER)?.isVisible == true) return
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right, R.anim.slide_out_left,
                android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.fragment_container, PlayerFragment(), TAG_PLAYER)
            .addToBackStack(null)
            .commit()
    }

    fun openAlbumDetail(album: Album) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right, R.anim.slide_out_left,
                android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.fragment_container, AlbumDetailFragment.newInstance(album.id), TAG_ALBUM)
            .addToBackStack(null)
            .commit()
    }

    fun openArtistDetail(artist: Artist) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right,
                                 android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.fragment_container, ArtistDetailFragment.newInstance(artist.id), TAG_ARTIST)
            .addToBackStack(null)
            .commit()
    }

    fun openGenreDetail(genre: Genre) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right,
                                 android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.fragment_container, GenreDetailFragment.newInstance(genre.id), TAG_GENRE)
            .addToBackStack(null)
            .commit()
    }

    fun openPlaylistDetail(playlist: PlaylistEntity) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right,
                                 android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.fragment_container, PlaylistDetailFragment.newInstance(playlist.id), TAG_PLAYLIST)
            .addToBackStack(null)
            .commit()
    }

    fun openFolderDetail(folderPath: String) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right,
                                 android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.fragment_container, FolderDetailFragment.newInstance(folderPath), TAG_FOLDER)
            .addToBackStack(null)
            .commit()
    }

    fun openQueue() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left,
                                 android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.fragment_container, QueueFragment(), "queue")
            .addToBackStack(null)
            .commit()
    }

    fun getQueue(): List<Track> {
        return viewModel.queue.value ?: service?.tracks ?: emptyList()
    }

    fun playQueueItem(index: Int) {
        service?.prepareAndPlay(index)
    }

    fun playTracks(tracks: List<Track>, startIndex: Int) {
        service?.playTracks(tracks, startIndex)
    }

    fun playPlaylist(playlistId: Long, startIndex: Int) {
        lifecycleScope.launch {
            val tracks = viewModel.resolvePlaylistTracks(playlistId)
            if (tracks.isNotEmpty()) {
                playTracks(tracks, startIndex.coerceIn(0, tracks.size - 1))
            }
        }
    }

    private fun isPlayerVisible() =
        supportFragmentManager.findFragmentByTag(TAG_PLAYER)?.isVisible == true

    // ── D-pad key handling ─────────────────────────────────────────────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                // Back key always pops the stack
                KeyEvent.KEYCODE_BACK -> {
                    if (supportFragmentManager.backStackEntryCount > 0) {
                        supportFragmentManager.popBackStack()
                        return true
                    }
                }
                // Media keys work from any screen
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlayPause(); return true }
                KeyEvent.KEYCODE_MEDIA_NEXT       -> { sendCmd("NEXT"); return true }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS   -> { sendCmd("PREV"); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ── Public API for fragments ───────────────────────────────────────────────

    fun togglePlayPause() {
        val svc = service ?: return
        if (svc.isPlaying) sendCmd("PAUSE") else sendCmd("PLAY")
    }

    fun playTrack(index: Int) {
        service?.prepareAndPlay(index)
    }

    fun seekTo(posMs: Long) {
        service?.seekTo(posMs)
    }

    fun currentTrack(): Track? {
        val svc = service ?: return null
        if (svc.tracks.isEmpty()) return null
        val idx = svc.currentIndex
        return if (idx >= 0 && idx < svc.tracks.size) svc.tracks[idx] else null
    }

    fun toggleShuffle() {
        service?.toggleShuffle()
    }

    fun playNext(track: Track) {
        service?.playNextTrack(track)
        Toast.makeText(this, getString(R.string.msg_playing_next, track.title), Toast.LENGTH_SHORT).show()
    }

    fun addToQueue(track: Track) {
        service?.enqueueTrack(track)
        Toast.makeText(this, getString(R.string.msg_added_to_queue, track.title), Toast.LENGTH_SHORT).show()
    }

    fun setPlaybackSpeed(speed: Float) {
        service?.setPlaybackSpeed(speed)
    }

    fun playbackSpeed(): Float = service?.getPlaybackSpeed() ?: 1.0f

    fun startSleepTimer(durationMs: Long) {
        service?.startSleepTimer(durationMs)
    }

    fun cancelSleepTimer() {
        service?.cancelSleepTimer()
    }

    fun sleepTimerRemainingMs(): Long = service?.sleepTimerRemainingMs() ?: -1L

    fun moveQueueItem(from: Int, to: Int): Boolean {
        return service?.moveQueueItem(from, to) == true
    }

    fun openEqualizer() {
        val sessionId = service?.audioSessionId ?: return

        if (sessionId <= 0) {
            Toast.makeText(this, "Equalizer unavailable right now", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "No system equalizer found", Toast.LENGTH_SHORT).show()
        }
    }

    fun showTrackMenu(anchor: android.view.View, track: Track) {
        val popup = android.widget.PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, getString(R.string.menu_add_to_playlist))
        popup.menu.add(0, 2, 0, getString(R.string.menu_play_next))
        popup.menu.add(0, 3, 0, getString(R.string.menu_add_to_queue))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showAddToPlaylistDialog(track)
                2 -> playNext(track)
                3 -> addToQueue(track)
            }
            true
        }
        popup.show()
    }

    private fun showAddToPlaylistDialog(track: Track) {
        val playlists = viewModel.playlists.value ?: emptyList()
        if (playlists.isEmpty()) {
            showCreateAndAddDialog(track)
            return
        }
        val options = (playlists.map { it.name } + listOf(getString(R.string.menu_new_playlist_with_plus))).toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_add_to_playlist_title)
            .setItems(options) { _, which ->
                if (which < playlists.size) {
                    viewModel.addTracksToPlaylist(playlists[which].id, listOf(track))
                    Toast.makeText(this, getString(R.string.msg_added_to_playlist, playlists[which].name), Toast.LENGTH_SHORT).show()
                } else {
                    showCreateAndAddDialog(track)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showCreateAndAddDialog(track: Track) {
        val editText = android.widget.EditText(this)
        editText.hint = getString(R.string.hint_playlist_name)
        editText.requestFocus()
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_new_playlist_title)
            .setView(editText)
            .setPositiveButton(R.string.action_create) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.createPlaylist(name, listOf(track))
                    Toast.makeText(this, getString(R.string.msg_playlist_created, name), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    fun cycleRepeat() {
        service?.cycleRepeat()
    }

    fun sendCmd(cmd: String) {
        val action = when (cmd) {
            "PLAY"     -> PlaybackService.COMMAND_PLAY
            "PAUSE"    -> PlaybackService.COMMAND_PAUSE
            "NEXT"     -> PlaybackService.COMMAND_NEXT
            "PREV"     -> PlaybackService.COMMAND_PREV
            "SEEK_FWD" -> PlaybackService.COMMAND_SEEK_FWD
            "SEEK_BWD" -> PlaybackService.COMMAND_SEEK_BWD
            else       -> return
        }
        startService(Intent(this, PlaybackService::class.java).also { it.action = action })
    }

    // ── Service callbacks ──────────────────────────────────────────────────────

    private fun setupServiceCallbacks() {
        val svc = service ?: return
        svc.onTrackChanged = { index ->
            runOnUiThread {
                viewModel.setCurrentIndex(index)
            }
        }
        svc.onPlaybackStateChanged = { isPlaying ->
            runOnUiThread {
                viewModel.setPlaying(isPlaying)
            }
        }
        svc.onPositionChanged = { pos ->
            runOnUiThread {
                viewModel.setPosition(pos)
            }
        }
        svc.onShuffleChanged = { on ->
            runOnUiThread {
                viewModel.setShuffleOn(on)
            }
        }
        svc.onRepeatChanged = { mode ->
            runOnUiThread {
                viewModel.setRepeatMode(mode)
            }
        }
        svc.onQueueChanged = { q ->
            runOnUiThread {
                viewModel.setQueue(q)
            }
        }
        svc.onSleepTimerChanged = { remaining ->
            runOnUiThread {
                viewModel.setSleepTimerRemainingMs(remaining)
            }
        }
        viewModel.setSleepTimerRemainingMs(svc.sleepTimerRemainingMs())
    }

    // ── Permissions ────────────────────────────────────────────────────────────

    private fun checkPermissionsAndLoad() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()

        val storageGranted = hasStoragePermission()
        if (storageGranted) {
            onPermissionGranted()
            // Still request notifications if missing, without blocking the scan
            val notifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.POST_NOTIFICATIONS else null
            if (notifPermission != null &&
                ContextCompat.checkSelfPermission(this, notifPermission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(arrayOf(notifPermission))
            }
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun showStoragePermissionRequiredDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_storage_permission_title)
            .setMessage(R.string.dialog_storage_permission_message)
            .setPositiveButton(R.string.action_open_settings) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun hasStoragePermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun onPermissionGranted() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val sortOrder = prefs.getString("sort_order", "title") ?: "title"
        viewModel.loadTracks(sortOrder)
    }

    fun rescanLibrary() {
        if (hasStoragePermission()) {
            onPermissionGranted()
            Toast.makeText(this, getString(R.string.msg_rescanning_library), Toast.LENGTH_SHORT).show()
        } else {
            checkPermissionsAndLoad()
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun libraryFragment() =
        supportFragmentManager.findFragmentByTag(TAG_LIBRARY) as? LibraryFragment

    private fun isDescendantOf(view: android.view.View, parent: android.view.View): Boolean {
        var v: android.view.View? = view
        while (v != null) {
            if (v === parent) return true
            v = v.parent as? android.view.View
        }
        return false
    }

    companion object {
        const val TAG_HOME     = "home"
        const val TAG_LIBRARY  = "library"
        const val TAG_PLAYER   = "player"
        const val TAG_SETTINGS = "settings"
        const val TAG_ALBUM    = "album"
        const val TAG_PLAYLIST = "playlist"
        const val TAG_ARTIST   = "artist"
        const val TAG_GENRE    = "genre"
        const val TAG_FOLDER   = "folder"
    }
}
