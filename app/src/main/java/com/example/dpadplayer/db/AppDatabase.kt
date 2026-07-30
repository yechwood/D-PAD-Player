package com.example.dpadplayer.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import android.content.pm.ApplicationInfo

// ── Entities ──────────────────────────────────────────────────────────────────

    @Entity(tableName = "playlists")
    data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "album_cache")
data class AlbumCacheEntity(
    @PrimaryKey val albumId: Long,
    val name: String,
    val artist: String,
    val artPath: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Each row represents one song slot in a playlist.
 * trackId is the MediaStore audio _ID.
 * position is the 0-based order within the playlist.
 * Using a separate row per slot allows duplicates and easy reordering.
 */
@Entity(
    tableName = "playlist_songs",
    foreignKeys = [ForeignKey(
        entity = PlaylistEntity::class,
        parentColumns = ["id"],
        childColumns = ["playlistId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("playlistId")],
)
data class PlaylistSongEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val playlistId: Long,
    val trackId: Long,   // MediaStore Audio._ID
    val position: Int,
)

// ── DAO ───────────────────────────────────────────────────────────────────────

@Dao
interface PlaylistDao {

    // ── Playlists ─────────────────────────────────────────────────────────────

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllPlaylistsOnce(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylist(id: Long): PlaylistEntity?

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    // ── Songs ─────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getSongsForPlaylist(playlistId: Long): Flow<List<PlaylistSongEntity>>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getSongsForPlaylistOnce(playlistId: Long): List<PlaylistSongEntity>

    @Insert
    suspend fun insertSongs(songs: List<PlaylistSongEntity>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND trackId = :trackId AND rowId IN (SELECT rowId FROM playlist_songs WHERE playlistId = :playlistId AND trackId = :trackId LIMIT 1)")
    suspend fun removeSongFromPlaylist(playlistId: Long, trackId: Long)
}

@Dao
interface AlbumCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cache: AlbumCacheEntity)

    @Query("SELECT * FROM album_cache WHERE albumId = :albumId LIMIT 1")
    suspend fun get(albumId: Long): AlbumCacheEntity?

    @Query("SELECT * FROM album_cache")
    suspend fun getAll(): List<AlbumCacheEntity>
}

@Entity(tableName = "track_cache")
data class TrackCacheEntity(
    @PrimaryKey val sourceUri: String,
    val trackId: Long,
    val title: String,
    val sortTitle: String,
    val artist: String,
    val sortArtist: String,
    val albumArtist: String,
    val sortAlbumArtist: String,
    val album: String,
    val sortAlbum: String,
    val albumId: Long,
    val trackNumber: Int,
    val discNumber: Int,
    val year: Int,
    val genre: String,
    val duration: Long,
    val dateAdded: Long,
    val albumArtPath: String,
)

@Entity(tableName = "play_stats")
data class PlayStatEntity(
    @PrimaryKey val trackId: Long,
    val playCount: Int,
    val lastPlayedAt: Long,
)

@Dao
interface TrackCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cache: TrackCacheEntity)

    @Query("SELECT * FROM track_cache WHERE sourceUri = :sourceUri LIMIT 1")
    suspend fun get(sourceUri: String): TrackCacheEntity?

    @Query("SELECT * FROM track_cache")
    suspend fun getAll(): List<TrackCacheEntity>

    @Query("SELECT * FROM track_cache WHERE sourceUri IN (:sourceUris)")
    suspend fun getBySourceUris(sourceUris: List<String>): List<TrackCacheEntity>
}

@Dao
interface PlayStatsDao {
    @Query("SELECT * FROM play_stats")
    fun observeAll(): Flow<List<PlayStatEntity>>

    @Query("SELECT * FROM play_stats")
    suspend fun getAll(): List<PlayStatEntity>

    @Query("UPDATE play_stats SET playCount = playCount + 1, lastPlayedAt = :playedAt WHERE trackId = :trackId")
    suspend fun incrementPlay(trackId: Long, playedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stat: PlayStatEntity)

    @Transaction
    suspend fun recordPlay(trackId: Long, playedAt: Long) {
        if (incrementPlay(trackId, playedAt) == 0) {
            insert(PlayStatEntity(trackId = trackId, playCount = 1, lastPlayedAt = playedAt))
        }
    }
}

// ── Database ──────────────────────────────────────────────────────────────────

@Database(entities = [PlaylistEntity::class, PlaylistSongEntity::class, AlbumCacheEntity::class, TrackCacheEntity::class, PlayStatEntity::class], version = 4, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun albumCacheDao(): AlbumCacheDao
    abstract fun trackCacheDao(): TrackCacheDao
    abstract fun playStatsDao(): PlayStatsDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // Explicit migration from v1 -> v2: create album_cache and track_cache tables
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create album_cache table
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `album_cache` (`albumId` INTEGER NOT NULL, `name` TEXT NOT NULL, `artist` TEXT NOT NULL, `artPath` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`albumId`))"
                )

                // Create track_cache table
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `track_cache` (`trackId` INTEGER NOT NULL, `title` TEXT NOT NULL, `sortTitle` TEXT NOT NULL, `artist` TEXT NOT NULL, `sortArtist` TEXT NOT NULL, `albumArtist` TEXT NOT NULL, `sortAlbumArtist` TEXT NOT NULL, `album` TEXT NOT NULL, `sortAlbum` TEXT NOT NULL, `albumId` INTEGER NOT NULL, `trackNumber` INTEGER NOT NULL, `discNumber` INTEGER NOT NULL, `year` INTEGER NOT NULL, `genre` TEXT NOT NULL, `duration` INTEGER NOT NULL, `dateAdded` INTEGER NOT NULL, `albumArtPath` TEXT NOT NULL, PRIMARY KEY(`trackId`))"
                )
            }
        }

        // v2 -> v3: switch track_cache primary key from trackId to sourceUri.
        // This avoids collisions between tracks from different MediaStore volumes
        // that can share the same numeric _ID.
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `track_cache_new` (`sourceUri` TEXT NOT NULL, `trackId` INTEGER NOT NULL, `title` TEXT NOT NULL, `sortTitle` TEXT NOT NULL, `artist` TEXT NOT NULL, `sortArtist` TEXT NOT NULL, `albumArtist` TEXT NOT NULL, `sortAlbumArtist` TEXT NOT NULL, `album` TEXT NOT NULL, `sortAlbum` TEXT NOT NULL, `albumId` INTEGER NOT NULL, `trackNumber` INTEGER NOT NULL, `discNumber` INTEGER NOT NULL, `year` INTEGER NOT NULL, `genre` TEXT NOT NULL, `duration` INTEGER NOT NULL, `dateAdded` INTEGER NOT NULL, `albumArtPath` TEXT NOT NULL, PRIMARY KEY(`sourceUri`))"
                )
                // Best-effort migration: old rows didn't store sourceUri, so synthesize
                // from EXTERNAL_CONTENT_URI. Fresh scans will rewrite with exact URIs.
                database.execSQL(
                    "INSERT OR REPLACE INTO track_cache_new(sourceUri, trackId, title, sortTitle, artist, sortArtist, albumArtist, sortAlbumArtist, album, sortAlbum, albumId, trackNumber, discNumber, year, genre, duration, dateAdded, albumArtPath) SELECT ('content://media/external/audio/media/' || trackId), trackId, title, sortTitle, artist, sortArtist, albumArtist, sortAlbumArtist, album, sortAlbum, albumId, trackNumber, discNumber, year, genre, duration, dateAdded, albumArtPath FROM track_cache"
                )
                database.execSQL("DROP TABLE track_cache")
                database.execSQL("ALTER TABLE track_cache_new RENAME TO track_cache")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `play_stats` (`trackId` INTEGER NOT NULL, `playCount` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL, PRIMARY KEY(`trackId`))"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val builder = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "dpad_player.db"
                    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

                    // Do NOT allow fallbackToDestructiveMigration here to avoid silent data loss in
                    // production. Migrations must be explicit (MIGRATION_1_2 is registered above).
                    builder.build().also { INSTANCE = it }
                }
            }
        }
}
