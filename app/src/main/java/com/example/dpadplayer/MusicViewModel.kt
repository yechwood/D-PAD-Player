package com.example.dpadplayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.dpadplayer.db.AppDatabase
import com.example.dpadplayer.db.PlaylistEntity
import com.example.dpadplayer.db.PlaylistSongEntity
import com.example.dpadplayer.db.PlayStatEntity
import com.example.dpadplayer.playback.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


class MusicViewModel(app: Application) : AndroidViewModel(app) {
    var activeLibraryTab = -1
    var homeMenuFocusPos = 0
    private val libraryTabFocusPositions = IntArray(8) { -1 }

    fun getLibraryTabFocusPosition(tab: Int): Int =
        libraryTabFocusPositions.getOrElse(tab) { -1 }

    fun setLibraryTabFocusPosition(tab: Int, position: Int) {
        if (tab in libraryTabFocusPositions.indices) {
            libraryTabFocusPositions[tab] = position.coerceAtLeast(0)
        }
    }

    // ── Playback state ────────────────────────────────────────────────────────

    private val _tracks = MutableLiveData<List<Track>>(emptyList())
    val tracks: LiveData<List<Track>> = _tracks

    private val _currentIndex = MutableLiveData<Int>(-1)
    val currentIndex: LiveData<Int> = _currentIndex

    private val _isPlaying = MutableLiveData<Boolean>(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _position = MutableLiveData<Long>(0L)
    val position: LiveData<Long> = _position

    private val _repeatMode = MutableLiveData<Int>(REPEAT_OFF)
    val repeatMode: LiveData<Int> = _repeatMode

    private val _shuffleOn = MutableLiveData<Boolean>(false)
    val shuffleOn: LiveData<Boolean> = _shuffleOn

    private val _queue = MutableLiveData<List<Track>>(emptyList())
    val queue: LiveData<List<Track>> = _queue

    private val _sleepTimerRemainingMs = MutableLiveData<Long>(-1L)
    val sleepTimerRemainingMs: LiveData<Long> = _sleepTimerRemainingMs

    private val _searchQuery = MutableLiveData<String>("")
    val searchQuery: LiveData<String> = _searchQuery

    // ── Library (albums / artists / genres) ───────────────────────────────────

    private val _library = MutableLiveData<MusicLibrary.Library>(
        MusicLibrary.Library(emptyList(), emptyList(), emptyList(), emptyList()))
    val library: LiveData<MusicLibrary.Library> = _library

    private val _albums  = MutableLiveData<List<Album>>(emptyList())
    val albums:  LiveData<List<Album>>  = _albums

    private val _artists = MutableLiveData<List<Artist>>(emptyList())
    val artists: LiveData<List<Artist>> = _artists

    private val _genres  = MutableLiveData<List<Genre>>(emptyList())
    val genres:  LiveData<List<Genre>>  = _genres

    private val _recentlyPlayed = MutableLiveData<List<Track>>(emptyList())
    val recentlyPlayed: LiveData<List<Track>> = _recentlyPlayed

    private val _mostPlayed = MutableLiveData<List<Track>>(emptyList())
    val mostPlayed: LiveData<List<Track>> = _mostPlayed

    // ── Playlists (Room) ──────────────────────────────────────────────────────

    private val db get() = AppDatabase.getInstance(getApplication())

    val playlists: LiveData<List<PlaylistEntity>> =
        db.playlistDao().getAllPlaylists().asLiveData()

    // ── Loading ───────────────────────────────────────────────────────────────

    private var loadTracksJob: Job? = null

    fun loadTracks(sortOrder: String = "title") {
        loadTracksJob?.cancel()
        loadTracksJob = viewModelScope.launch(Dispatchers.IO) {
            val result = MediaStoreScanner.loadTracks(getApplication(), sortOrder)
            val lib    = MusicLibrary.build(result)
            // Merge album cache entries to preserve persisted metadata across restarts
            val merged = try {
                val db = com.example.dpadplayer.db.AppDatabase.getInstance(getApplication())
                val caches = db.albumCacheDao().getAll()
                if (caches.isNotEmpty()) {
                    val cacheByAlbumId = caches.associateBy { it.albumId }
                    val albums = lib.albums.map { album ->
                        val anyId = album.songs.firstOrNull()?.albumId ?: 0L
                        val c = cacheByAlbumId[anyId]
                        if (c != null && c.artPath.isNotBlank()) {
                            album.copy(albumArtUri = android.net.Uri.fromFile(java.io.File(c.artPath)))
                        } else album
                    }
                    lib.copy(albums = albums)
                } else lib
            } catch (_: Exception) { lib }
            _tracks.postValue(result)
            _library.postValue(merged)
            _albums.postValue(merged.albums)
            _artists.postValue(merged.artists)
            _genres.postValue(merged.genres)
            publishSmartPlaylists(result)

            // Background enrichment: lazily extract real ID3 tags + artwork for all tracks.
            // Already-enriched tracks (read from cache) are skipped automatically.
            delay(2000) // let initial display settle on main thread
            for (track in result) {
                if (track.albumArtUri != track.mediaStoreAlbumArtUri) {
                    continue
                }
                MediaStoreScanner.enrichTrack(getApplication(), track)
                delay(150)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        loadTracksJob?.cancel()
        // Clear any scanner scope if it was set to this viewModelScope
        if (MediaStoreScanner.scope === viewModelScope) MediaStoreScanner.scope = null
    }

    init {
        // Do not set MediaStoreScanner.scope here; application-level scope will be
        // provided by the Application subclass so cache writes survive ViewModel
        // lifecycles.
    }

    init {
        // Observe artwork events and update the library UI when new album art appears
        viewModelScope.launch(Dispatchers.Main) {
            ArtRepository.artEvents.collect { ev ->
                val currentLib = _library.value ?: return@collect
                val albumId = ev.albumId
                if (albumId <= 0L) return@collect
                val albums = currentLib.albums
                var changed = false
                val newAlbums = albums.map { album ->
                    val hasTrack = album.songs.any { it.albumId == albumId }
                    if (!hasTrack) return@map album
                    val cached = ArtRepository.getCachedAlbumArt(getApplication(), albumId)
                    if (cached != null && cached != album.albumArtUri) {
                        changed = true
                        album.copy(albumArtUri = cached)
                    } else album
                }
                if (changed) {
                    val newLib = currentLib.copy(albums = newAlbums)
                    _library.postValue(newLib)
                    _albums.postValue(newAlbums)
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            db.playStatsDao().observeAll().collectLatest { stats ->
                val currentTracks = _tracks.value ?: emptyList()
                publishSmartPlaylistsFromStats(stats, currentTracks)
            }
        }

        // Separate collector for metadata events so both flows are observed concurrently
        viewModelScope.launch(Dispatchers.Main) {
            val pending = LinkedHashMap<Long, Track>()
            var flushJob: Job? = null

            fun flushPending() {
                val currentTracks = _tracks.value ?: return
                if (pending.isEmpty()) return
                val updates = pending.values.toList()
                pending.clear()
                val updateMap = updates.associateBy { it.id }
                var changed = false
                val updated = currentTracks.map { old ->
                    val replacement = updateMap[old.id] ?: return@map old
                    if (replacement != old) {
                        changed = true
                        replacement
                    } else old
                }
                if (changed) {
                    val lib = MusicLibrary.build(updated)
                    _tracks.postValue(updated)
                    _library.postValue(lib)
                    _albums.postValue(lib.albums)
                    _artists.postValue(lib.artists)
                    _genres.postValue(lib.genres)
                    publishSmartPlaylists(updated)
                }
            }

            ArtRepository.metaEvents.collect { ev ->
                pending[ev.trackId] = ev.enriched
                if (flushJob == null || flushJob?.isCompleted == true) {
                    flushJob = launch(Dispatchers.Main) {
                        delay(300)
                        flushPending()
                    }
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query.trim()
    }

    private fun publishSmartPlaylists(tracks: List<Track>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stats = db.playStatsDao().getAll()
                publishSmartPlaylistsFromStats(stats, tracks)
            } catch (_: Exception) {
                _recentlyPlayed.postValue(emptyList())
                _mostPlayed.postValue(emptyList())
            }
        }
    }

    private fun publishSmartPlaylistsFromStats(stats: List<PlayStatEntity>, tracks: List<Track>) {
        val trackMap = tracks.associateBy { it.id }
        val recent = stats
            .sortedByDescending { it.lastPlayedAt }
            .mapNotNull { trackMap[it.trackId] }
            .take(100)
        val most = stats
            .sortedByDescending { it.playCount }
            .mapNotNull { trackMap[it.trackId] }
            .take(100)
        _recentlyPlayed.postValue(recent)
        _mostPlayed.postValue(most)
    }

    // ── Playlist operations ───────────────────────────────────────────────────

    fun createPlaylist(name: String, tracks: List<Track> = emptyList()) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = db.playlistDao().insertPlaylist(PlaylistEntity(name = name))
            if (tracks.isNotEmpty()) {
                db.playlistDao().insertSongs(tracks.mapIndexed { i, t ->
                    PlaylistSongEntity(playlistId = id, trackId = t.id, position = i)
                })
            }
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.playlistDao().renamePlaylist(playlistId, newName)
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.playlistDao().deletePlaylist(playlist)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val playlist = db.playlistDao().getPlaylist(playlistId) ?: return@launch
            db.playlistDao().deletePlaylist(playlist)
        }
    }

    fun addTracksToPlaylist(playlistId: Long, newTracks: List<Track>) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = db.playlistDao().getSongsForPlaylistOnce(playlistId)
            val startPos = existing.size
            db.playlistDao().insertSongs(newTracks.mapIndexed { i, t ->
                PlaylistSongEntity(playlistId = playlistId, trackId = t.id, position = startPos + i)
            })
        }
    }

    fun rewritePlaylist(playlistId: Long, newTracks: List<Track>) {
        viewModelScope.launch(Dispatchers.IO) {
            db.playlistDao().clearPlaylist(playlistId)
            db.playlistDao().insertSongs(newTracks.mapIndexed { i, t ->
                PlaylistSongEntity(playlistId = playlistId, trackId = t.id, position = i)
            })
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.playlistDao().removeSongFromPlaylist(playlistId, trackId)
            // Re-compact positions
            val remaining = db.playlistDao().getSongsForPlaylistOnce(playlistId)
            db.playlistDao().clearPlaylist(playlistId)
            db.playlistDao().insertSongs(remaining.mapIndexed { i, s ->
                s.copy(rowId = 0, position = i)
            })
        }
    }

    /** Resolve a playlist's track IDs into Track objects (in order). */
    suspend fun resolvePlaylistTracks(playlistId: Long): List<Track> {
        val rows = db.playlistDao().getSongsForPlaylistOnce(playlistId)
        val trackMap = (_tracks.value ?: emptyList()).associateBy { it.id }
        return rows.mapNotNull { trackMap[it.trackId] }
    }

    fun observePlaylistTracks(playlistId: Long): LiveData<List<Track>> {
        return db.playlistDao().getSongsForPlaylist(playlistId)
            .map { rows ->
                val trackMap = (_tracks.value ?: emptyList()).associateBy { it.id }
                rows.mapNotNull { row -> trackMap[row.trackId] }
            }
            .asLiveData()
    }

    // ── Setters (called by MainActivity from service callbacks) ───────────────

    fun setCurrentIndex(index: Int) { _currentIndex.value = index }
    fun setPlaying(playing: Boolean) { _isPlaying.value = playing }
    fun setPosition(pos: Long)       { _position.value = pos }
    fun setRepeatMode(mode: Int)     { _repeatMode.value = mode }
    fun setShuffleOn(on: Boolean)    { _shuffleOn.value = on }
    fun setQueue(q: List<Track>)     { _queue.value = q }
    fun setSleepTimerRemainingMs(remainingMs: Long) { _sleepTimerRemainingMs.value = remainingMs }

    companion object {
        const val REPEAT_OFF = 0
        const val REPEAT_ALL = 1
        const val REPEAT_ONE = 2

        fun filterTracks(tracks: List<Track>, query: String): List<Track> {
            val q = query.normalizedQuery()
            if (q.isEmpty()) return tracks
            return tracks.filter { t ->
                t.title.normalizedContains(q) ||
                    t.artist.normalizedContains(q) ||
                    t.album.normalizedContains(q) ||
                    t.genre.normalizedContains(q) ||
                    t.filePath.normalizedContains(q)
            }
        }

        fun filterAlbums(albums: List<Album>, query: String): List<Album> {
            val q = query.normalizedQuery()
            if (q.isEmpty()) return albums
            return albums.filter { a ->
                a.name.normalizedContains(q) ||
                    a.artist.normalizedContains(q) ||
                    a.year.toString().normalizedContains(q) ||
                    a.songs.any { s ->
                        s.title.normalizedContains(q) || s.artist.normalizedContains(q)
                    }
            }
        }

        fun filterArtists(artists: List<Artist>, query: String): List<Artist> {
            val q = query.normalizedQuery()
            if (q.isEmpty()) return artists
            return artists.filter { a ->
                a.name.normalizedContains(q) ||
                    a.albums.any { it.name.normalizedContains(q) } ||
                    a.songs.any { it.title.normalizedContains(q) }
            }
        }

        fun filterGenres(genres: List<Genre>, query: String): List<Genre> {
            val q = query.normalizedQuery()
            if (q.isEmpty()) return genres
            return genres.filter { g ->
                g.name.normalizedContains(q) ||
                    g.songs.any { it.title.normalizedContains(q) || it.artist.normalizedContains(q) }
            }
        }

        fun filterPlaylists(playlists: List<PlaylistEntity>, query: String): List<PlaylistEntity> {
            val q = query.normalizedQuery()
            if (q.isEmpty()) return playlists
            return playlists.filter { it.name.normalizedContains(q) }
        }

        fun filterFolders(folders: List<String>, query: String): List<String> {
            val q = query.normalizedQuery()
            if (q.isEmpty()) return folders
            return folders.filter { it.normalizedContains(q) }
        }

        private fun String.normalizedQuery(): String = trim().lowercase()
        private fun String.normalizedContains(query: String): Boolean = lowercase().contains(query)
    }
}
