package com.example.dpadplayer.playback

import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.MediaBrowserServiceCompat
import com.example.dpadplayer.MediaStoreScanner
import com.example.dpadplayer.MusicLibrary
import com.example.dpadplayer.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DPadMediaBrowserService : MediaBrowserServiceCompat() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaSession: MediaSessionCompat? = null
    private var cachedTracks: List<Track> = emptyList()
    private var cachedLibrary: MusicLibrary.Library? = null

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "dpad_browser_session").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    startPlaybackServiceCommand(PlaybackService.COMMAND_PLAY)
                }

                override fun onPause() {
                    startPlaybackServiceCommand(PlaybackService.COMMAND_PAUSE)
                }

                override fun onSkipToNext() {
                    startPlaybackServiceCommand(PlaybackService.COMMAND_NEXT)
                }

                override fun onSkipToPrevious() {
                    startPlaybackServiceCommand(PlaybackService.COMMAND_PREV)
                }

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    val parts = mediaId?.split(':') ?: return
                    if (parts.isEmpty()) return
                    when (parts[0]) {
                        ID_TRACK -> {
                            val trackId = parts.getOrNull(1)?.toLongOrNull() ?: return
                            startPlaybackServiceCommand(
                                PlaybackService.COMMAND_PLAY_TRACK_ID,
                                Bundle().apply { putLong(PlaybackService.EXTRA_TRACK_ID, trackId) }
                            )
                        }
                    }
                }
            })
            isActive = true
        }
        sessionToken = mediaSession?.sessionToken
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot(ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        result.detach()
        serviceScope.launch {
            val items = buildChildren(parentId)
            result.sendResult(items)
        }
    }

    private suspend fun buildChildren(parentId: String): MutableList<MediaBrowserCompat.MediaItem> {
        val library = loadLibrary()
        val tracks = library.tracks
        return when {
            parentId == ROOT_ID -> mutableListOf(
                browsableItem(SONGS_ID, getString(R.string.menu_songs)),
                browsableItem(ALBUMS_ID, getString(R.string.menu_albums)),
                browsableItem(ARTISTS_ID, getString(R.string.menu_artists)),
                browsableItem(PLAYLISTS_ID, getString(R.string.menu_playlists)),
                browsableItem(RECENT_ID, "Recently played"),
                browsableItem(TOP_ID, "Most played"),
            )

            parentId == SONGS_ID -> tracks.map { playableTrackItem(it) }.toMutableList()

            parentId == ALBUMS_ID -> library.albums.map { album ->
                browsableItem("$ALBUM_ID:${album.id}", album.name, album.artist, album.albumArtUri)
            }.toMutableList()

            parentId.startsWith("$ALBUM_ID:") -> {
                val key = parentId.substringAfter(':')
                val album = library.albums.firstOrNull { it.id == key }
                album?.songs?.map { playableTrackItem(it) }?.toMutableList() ?: mutableListOf()
            }

            parentId == ARTISTS_ID -> library.artists.map { artist ->
                browsableItem("$ARTIST_ID:${artist.id}", artist.name, "${artist.albumCount} albums")
            }.toMutableList()

            parentId.startsWith("$ARTIST_ID:") -> {
                val key = parentId.substringAfter(':')
                val artist = library.artists.firstOrNull { it.id == key }
                artist?.songs?.map { playableTrackItem(it) }?.toMutableList() ?: mutableListOf()
            }

            parentId == PLAYLISTS_ID -> {
                val playlists = withContext(Dispatchers.IO) {
                    com.example.dpadplayer.db.AppDatabase.getInstance(applicationContext)
                        .playlistDao()
                        .getAllPlaylistsOnce()
                }
                playlists.map { playlist ->
                    browsableItem("$PLAYLIST_ID:${playlist.id}", playlist.name)
                }.toMutableList()
            }

            parentId.startsWith("$PLAYLIST_ID:") -> {
                val playlistId = parentId.substringAfter(':').toLongOrNull() ?: return mutableListOf()
                val rows = withContext(Dispatchers.IO) {
                    com.example.dpadplayer.db.AppDatabase.getInstance(applicationContext)
                        .playlistDao()
                        .getSongsForPlaylistOnce(playlistId)
                }
                val map = tracks.associateBy { it.id }
                rows.mapNotNull { map[it.trackId] }.map { playableTrackItem(it) }.toMutableList()
            }

            parentId == RECENT_ID -> {
                val stats = withContext(Dispatchers.IO) {
                    com.example.dpadplayer.db.AppDatabase.getInstance(applicationContext)
                        .playStatsDao()
                        .getAll()
                }
                val map = tracks.associateBy { it.id }
                stats.sortedByDescending { it.lastPlayedAt }
                    .mapNotNull { map[it.trackId] }
                    .take(200)
                    .map { playableTrackItem(it) }
                    .toMutableList()
            }

            parentId == TOP_ID -> {
                val stats = withContext(Dispatchers.IO) {
                    com.example.dpadplayer.db.AppDatabase.getInstance(applicationContext)
                        .playStatsDao()
                        .getAll()
                }
                val map = tracks.associateBy { it.id }
                stats.sortedByDescending { it.playCount }
                    .mapNotNull { map[it.trackId] }
                    .take(200)
                    .map { playableTrackItem(it) }
                    .toMutableList()
            }

            else -> mutableListOf()
        }
    }

    private suspend fun loadLibrary(): MusicLibrary.Library {
        cachedLibrary?.let { return it }
        val tracks = loadTracksForBrowse()
        val library = MusicLibrary.build(tracks)
        cachedLibrary = library
        return library
    }

    private suspend fun loadTracksForBrowse(): List<Track> {
        if (cachedTracks.isNotEmpty()) return cachedTracks
        val loaded = withContext(Dispatchers.IO) {
            MediaStoreScanner.loadTracks(applicationContext, "title")
        }
        cachedTracks = loaded
        return loaded
    }

    private fun startPlaybackServiceCommand(action: String, extras: Bundle? = null) {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        if (extras != null) intent.putExtras(extras)
        startService(intent)
    }

    private fun browsableItem(
        id: String,
        title: String,
        subtitle: String? = null,
        icon: android.net.Uri? = null,
    ): MediaBrowserCompat.MediaItem {
        return MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(id)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setIconUri(icon)
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }

    private fun playableTrackItem(track: Track): MediaBrowserCompat.MediaItem {
        return MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId("$ID_TRACK:${track.id}")
                .setTitle(track.title)
                .setSubtitle(track.artist)
                .setDescription(track.album)
                .setIconUri(track.albumArtUri)
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    companion object {
        private const val ROOT_ID = "root"
        private const val SONGS_ID = "songs"
        private const val ALBUMS_ID = "albums"
        private const val ARTISTS_ID = "artists"
        private const val PLAYLISTS_ID = "playlists"
        private const val RECENT_ID = "recent"
        private const val TOP_ID = "top"

        private const val ALBUM_ID = "album"
        private const val ARTIST_ID = "artist"
        private const val PLAYLIST_ID = "playlist"
        private const val ID_TRACK = "track"
    }
}
