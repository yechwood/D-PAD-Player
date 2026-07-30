package com.example.dpadplayer.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Migration tests to ensure DB upgrades from older versions succeed.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2() {
        // Create v1 database (no album_cache/track_cache) and then run migration to v2
        val dbName = "migration-test"
        helper.createDatabase(dbName, 1).apply {
            // DB created with version 1 schema
            close()
        }

        // Run the migration and open the resulting DB.
        val migratedDb = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            dbName
        ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()

        migratedDb.openHelper.writableDatabase.close()
        migratedDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3() {
        val dbName = "migration-test-v2-v3"
        helper.createDatabase(dbName, 2).apply {
            execSQL("INSERT INTO track_cache(trackId, title, sortTitle, artist, sortArtist, albumArtist, sortAlbumArtist, album, sortAlbum, albumId, trackNumber, discNumber, year, genre, duration, dateAdded, albumArtPath) VALUES(42, 'Title', 'Title', 'Artist', 'Artist', 'Artist', 'Artist', 'Album', 'Album', 1, 1, 1, 2024, 'Genre', 123000, 1700000000, '')")
            close()
        }

        val migratedDb = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            dbName
        ).addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()

        migratedDb.openHelper.writableDatabase.close()
        migratedDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To4() {
        val dbName = "migration-test-v3-v4"
        helper.createDatabase(dbName, 3).apply {
            close()
        }

        val migratedDb = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            dbName
        ).addMigrations(AppDatabase.MIGRATION_3_4)
            .build()

        migratedDb.openHelper.writableDatabase.close()
        migratedDb.close()
    }
}
