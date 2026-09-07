package com.muzora.data.repo

import android.content.Context
import com.muzora.data.api.SubsonicClient
import com.muzora.data.local.db.LocalStarDao
import com.muzora.data.local.db.LocalStarEntity
import com.muzora.data.local.db.PendingStarDao
import com.muzora.data.local.db.PendingStarEntity
import com.muzora.domain.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class StarRepository(
    context: Context,
    private val localStarDao: LocalStarDao,
    private val pendingStarDao: PendingStarDao,
    private val client: SubsonicClient,
) {
    private val backupFile = File(context.noBackupFilesDir, "stars_backup.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun observeLocalStars(): Flow<List<Song>> =
        localStarDao.observeAll().map { list -> list.map { it.toSong() } }

    suspend fun getLocalStars(): List<Song> = localStarDao.getAll().map { it.toSong() }

    suspend fun getLocalStarIds(): Set<String> = localStarDao.getIds().toSet()

    /** Restore Room stars from JSON backup if DB table is empty (e.g. after bad migration). */
    suspend fun restoreFromBackupIfEmpty() {
        if (localStarDao.getAll().isNotEmpty()) return
        val restored = readBackup()
        if (restored.isEmpty()) return
        localStarDao.upsertAll(restored.map { it.toEntity() })
    }

    /**
     * Apply like/unlike locally immediately and queue server sync.
     * Tries the API right away when online; otherwise waits for [syncPending].
     */
    suspend fun setStarredLocally(song: Song, starred: Boolean) {
        if (starred) {
            localStarDao.upsert(song.toEntity())
        } else {
            localStarDao.delete(song.id)
        }
        writeBackup(localStarDao.getAll().map { it.toSong() })
        pendingStarDao.upsert(
            PendingStarEntity(songId = song.id, wantStarred = starred)
        )
        if (client.isConfigured) {
            val ok = runCatching {
                if (starred) client.star(song.id) else client.unstar(song.id)
                true
            }.getOrDefault(false)
            if (ok) {
                pendingStarDao.delete(song.id)
            }
        }
    }

    /** Push pending star/unstar ops, then refresh local mirror from server. */
    suspend fun syncPending(): Boolean {
        if (!client.isConfigured) return false
        val pending = pendingStarDao.getAll()
        for (op in pending) {
            val ok = runCatching {
                if (op.wantStarred) client.star(op.songId) else client.unstar(op.songId)
                true
            }.getOrDefault(false)
            if (ok) {
                pendingStarDao.delete(op.songId)
            }
        }
        return runCatching {
            val remote = client.getStarred()
            val stillPending = pendingStarDao.getAll().associateBy { it.songId }
            val merged = linkedMapOf<String, Song>()
            remote.forEach { merged[it.id] = it.copy(starred = true) }
            for (op in stillPending.values) {
                if (op.wantStarred) {
                    if (!merged.containsKey(op.songId)) {
                        localStarDao.getAll().find { it.songId == op.songId }?.toSong()?.let {
                            merged[it.id] = it
                        }
                    }
                } else {
                    merged.remove(op.songId)
                }
            }
            localStarDao.clear()
            localStarDao.upsertAll(merged.values.map { it.toEntity() })
            stillPending.values.forEach { pendingStarDao.upsert(it) }
            writeBackup(merged.values.toList())
            true
        }.getOrDefault(false)
    }

    suspend fun replaceFromServer(songs: List<Song>) {
        val pending = pendingStarDao.getAll().associateBy { it.songId }
        val merged = linkedMapOf<String, Song>()
        songs.forEach { merged[it.id] = it.copy(starred = true) }
        for (op in pending.values) {
            if (op.wantStarred) {
                if (!merged.containsKey(op.songId)) {
                    localStarDao.getAll().find { it.songId == op.songId }?.toSong()?.let {
                        merged[it.id] = it.copy(starred = true)
                    }
                }
            } else {
                merged.remove(op.songId)
            }
        }
        localStarDao.clear()
        localStarDao.upsertAll(merged.values.map { it.toEntity() })
        pending.values.forEach { pendingStarDao.upsert(it) }
        writeBackup(merged.values.toList())
    }

    private fun writeBackup(songs: List<Song>) {
        runCatching {
            val payload = StarsBackup(songs = songs.map {
                StarBackupDto(
                    id = it.id,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    coverArt = it.coverArt,
                    duration = it.duration,
                )
            })
            backupFile.writeText(json.encodeToString(payload))
        }
    }

    private fun readBackup(): List<Song> {
        if (!backupFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<StarsBackup>(backupFile.readText()).songs.map {
                Song(
                    id = it.id,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    coverArt = it.coverArt,
                    duration = it.duration,
                    starred = true,
                )
            }
        }.getOrDefault(emptyList())
    }
}

@Serializable
private data class StarsBackup(val songs: List<StarBackupDto>)

@Serializable
private data class StarBackupDto(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverArt: String? = null,
    val duration: Int = 0,
)

private fun Song.toEntity() = LocalStarEntity(
    songId = id,
    title = title,
    artist = artist,
    album = album,
    coverArt = coverArt,
    duration = duration,
)

private fun LocalStarEntity.toSong() = Song(
    id = songId,
    title = title,
    artist = artist,
    album = album,
    coverArt = coverArt,
    duration = duration,
    starred = true,
)
