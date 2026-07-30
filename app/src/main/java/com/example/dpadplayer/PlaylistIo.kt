package com.example.dpadplayer

import android.content.Context
import android.net.Uri
import com.example.dpadplayer.playback.Track
import java.util.Locale

object PlaylistIo {

    data class ParsedPlaylist(
        val title: String?,
        val entries: List<String>,
    )

    fun parseM3u(content: String): ParsedPlaylist {
        val lines = content.lines()
        var title: String? = null
        val entries = mutableListOf<String>()

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTM3U", ignoreCase = true)) continue
            if (line.startsWith("#PLAYLIST:", ignoreCase = true)) {
                title = line.substringAfter(':').trim().ifBlank { null }
                continue
            }
            if (line.startsWith("#")) continue
            entries.add(line)
        }
        return ParsedPlaylist(title = title, entries = entries)
    }

    fun resolveEntriesToTracks(entries: List<String>, tracks: List<Track>): List<Track> {
        if (entries.isEmpty() || tracks.isEmpty()) return emptyList()

        val byPath = tracks
            .filter { it.filePath.isNotBlank() }
            .associateBy { normalizePath(it.filePath) }
        val byName = tracks
            .groupBy { fileNameFromPath(it.filePath).lowercase(Locale.US) }

        val resolved = mutableListOf<Track>()
        for (entry in entries) {
            val path = normalizePath(entry)
            val direct = byPath[path]
            if (direct != null) {
                resolved.add(direct)
                continue
            }

            val fileName = fileNameFromPath(entry).lowercase(Locale.US)
            val match = byName[fileName]?.firstOrNull()
            if (match != null) resolved.add(match)
        }
        return resolved
    }

    fun buildM3uContent(playlistName: String, tracks: List<Track>): String {
        val builder = StringBuilder()
        builder.append("#EXTM3U\n")
        builder.append("#PLAYLIST:").append(playlistName).append('\n')
        for (track in tracks) {
            val seconds = (track.duration / 1000L).coerceAtLeast(0L)
            builder.append("#EXTINF:")
                .append(seconds)
                .append(',')
                .append(track.artist)
                .append(" - ")
                .append(track.title)
                .append('\n')
            builder.append(track.filePath.ifBlank { track.uri.toString() }).append('\n')
        }
        return builder.toString()
    }

    fun writeM3uToUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray())
                out.flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun readM3uFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizePath(path: String): String {
        return path
            .replace('\\', '/')
            .trim()
            .lowercase(Locale.US)
    }

    private fun fileNameFromPath(path: String): String {
        if (path.isBlank()) return ""
        val cleaned = path.replace('\\', '/')
        return cleaned.substringAfterLast('/', "")
    }
}
