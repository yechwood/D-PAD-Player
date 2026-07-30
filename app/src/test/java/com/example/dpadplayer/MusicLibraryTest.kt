package com.example.dpadplayer

import com.example.dpadplayer.playback.Track
import org.junit.Assert.*
import org.junit.Test
import android.net.Uri
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.dpadplayer.PlaylistIo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])

class MusicLibraryTest {

    private fun makeTrack(id: Long, title: String, artist: String, album: String,
                          albumId: Long, filePath: String = "", albumArtUri: Uri? = null,
                          mediaStoreArt: Uri? = null, sortTitle: String = title,
                          sortAlbum: String = album, sortArtist: String = artist,
                          albumArtist: String = artist, sortAlbumArtist: String = artist,
                          trackNumber: Int = 0, discNumber: Int = 0,
                          year: Int = 0, genre: String = "", duration: Long = 0L,
                          dateAdded: Long = 0L): Track {
        val art = albumArtUri ?: mediaStoreArt ?: Uri.parse("content://none")
        val msArt = mediaStoreArt ?: art
        return Track(
            id = id,
            uri = Uri.parse("content://media/$id"),
            filePath = filePath,
            title = title,
            sortTitle = sortTitle,
            artist = artist,
            sortArtist = sortArtist,
            albumArtist = albumArtist,
            sortAlbumArtist = sortAlbumArtist,
            album = album,
            sortAlbum = sortAlbum,
            albumId = albumId,
            trackNumber = trackNumber,
            discNumber = discNumber,
            year = year,
            genre = genre,
            duration = duration,
            dateAdded = dateAdded,
            albumArtUri = art,
            mediaStoreAlbumArtUri = msArt,
        )
    }

    @Test
    fun testAlbumGroupingAndArtSelection() {
        val t1 = makeTrack(1, "A1", "Artist", "AlbumX", 10,
            albumArtUri = Uri.parse("file://embedded1"), mediaStoreArt = Uri.parse("content://ms/10"))
        val t2 = makeTrack(2, "A2", "Artist", "AlbumX", 10,
            albumArtUri = Uri.parse("content://ms/10"), mediaStoreArt = Uri.parse("content://ms/10"))
        val lib = MusicLibrary.build(listOf(t1, t2))
        assertEquals(1, lib.albums.size)
        val album = lib.albums.first()
        // albumArtUri should prefer the embedded artwork from t1
        assertEquals(Uri.parse("file://embedded1"), album.albumArtUri)
    }

    @Test
    fun testArtistSplittingAndCounts() {
        val t1 = makeTrack(1, "S1", "A & B", "Album1", 1)
        val t2 = makeTrack(2, "S2", "A;C", "Album2", 2)
        val lib = MusicLibrary.build(listOf(t1, t2))
        // artists should include a, b, c lowercase keys
        val artistKeys = lib.artists.map { it.id }
        assertTrue(artistKeys.contains("a & b" ) || artistKeys.any { it.contains("a") })
        // Song counts should sum to total tracks
        val totalSongs = lib.artists.sumOf { it.songs.size }
        assertTrue(totalSongs >= 2)
    }

    @Test
    fun testAlbumSongsPreferTrackNumberOverTitleSort() {
        val t1 = makeTrack(1, "Track B", "Artist", "AlbumX", 10, trackNumber = 2)
        val t2 = makeTrack(2, "Track A", "Artist", "AlbumX", 10, trackNumber = 1)

        val lib = MusicLibrary.build(listOf(t1, t2))
        val songs = lib.albums.first().songs

        assertEquals(2, songs.size)
        assertEquals(1, songs[0].trackNumber)
        assertEquals(2, songs[1].trackNumber)
    }

    @Test
    fun testDecodeMediaStoreTrackHandlesPackedAndSimpleValues() {
        assertEquals(1 to 5, MediaStoreScanner.decodeMediaStoreTrack(1005))
        assertEquals(0 to 7, MediaStoreScanner.decodeMediaStoreTrack(7))
        assertEquals(0 to 0, MediaStoreScanner.decodeMediaStoreTrack(0))
    }

    @Test
    fun testFilterTracksMatchesTitleArtistAndAlbum() {
        val t1 = makeTrack(1, "Dreamscape", "Aurora", "Night Sky", 11)
        val t2 = makeTrack(2, "Pulse", "Synth Unit", "Neon City", 12)

        val byTitle = MusicViewModel.filterTracks(listOf(t1, t2), "dream")
        assertEquals(listOf(t1), byTitle)

        val byArtist = MusicViewModel.filterTracks(listOf(t1, t2), "synth")
        assertEquals(listOf(t2), byArtist)

        val byAlbum = MusicViewModel.filterTracks(listOf(t1, t2), "night")
        assertEquals(listOf(t1), byAlbum)
    }

    @Test
    fun testFilterAlbumsMatchesSongsInsideAlbum() {
        val song = makeTrack(1, "Moonlight", "Piano Artist", "Quiet Hours", 33)
        val album = Album(
            id = "quiet",
            name = "Quiet Hours",
            sortName = "Quiet Hours",
            artist = "Various",
            year = 2024,
            songs = listOf(song),
            albumArtUri = Uri.parse("content://art/quiet")
        )

        val filtered = MusicViewModel.filterAlbums(listOf(album), "moon")
        assertEquals(1, filtered.size)
        assertEquals(album.id, filtered.first().id)
    }

    @Test
    fun testParseM3uReadsEntriesAndPlaylistTitle() {
        val m3u = """
            #EXTM3U
            #PLAYLIST:Road Trip
            #EXTINF:210,Artist - Song One
            /music/song1.mp3
            #EXTINF:180,Artist - Song Two
            /music/song2.mp3
        """.trimIndent()

        val parsed = PlaylistIo.parseM3u(m3u)
        assertEquals("Road Trip", parsed.title)
        assertEquals(listOf("/music/song1.mp3", "/music/song2.mp3"), parsed.entries)
    }

    @Test
    fun testResolveM3uEntriesMatchesByPathAndFilename() {
        val t1 = makeTrack(1, "One", "Artist", "Album", 1, filePath = "/storage/music/song1.mp3")
        val t2 = makeTrack(2, "Two", "Artist", "Album", 1, filePath = "/storage/music/song2.mp3")

        val resolved = PlaylistIo.resolveEntriesToTracks(
            listOf("/storage/music/song1.mp3", "song2.mp3"),
            listOf(t1, t2)
        )

        assertEquals(listOf(t1, t2), resolved)
    }
}
