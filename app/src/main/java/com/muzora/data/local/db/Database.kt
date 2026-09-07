package com.muzora.data.local.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "blacklist")
data class BlacklistEntity(
    @PrimaryKey val songId: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "cache_index")
data class CacheEntity(
    @PrimaryKey val songId: String,
    val filePath: String,
    val bytes: Long,
    val contentLength: Long,
    val complete: Boolean,
    val likedPinned: Boolean,
    val lastAccess: Long,
    val title: String,
    val artist: String,
    val album: String,
    val coverArt: String?,
    val duration: Int,
)

@Entity(tableName = "local_radio")
data class LocalRadioEntity(
    @PrimaryKey val id: String,
    val name: String,
    val streamUrl: String,
)

/** Offline-capable favorites mirror (synced with server when online). */
@Entity(tableName = "local_stars")
data class LocalStarEntity(
    @PrimaryKey val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverArt: String?,
    val duration: Int,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Pending star/unstar to flush when the network returns. */
@Entity(tableName = "pending_stars")
data class PendingStarEntity(
    @PrimaryKey val songId: String,
    /** true = star on server, false = unstar */
    val wantStarred: Boolean,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface BlacklistDao {
    @Query("SELECT songId FROM blacklist")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT songId FROM blacklist")
    suspend fun getIds(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM blacklist WHERE songId = :id)")
    suspend fun contains(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BlacklistEntity)

    @Query("DELETE FROM blacklist WHERE songId = :id")
    suspend fun delete(id: String)
}

@Dao
interface CacheDao {
    @Query("SELECT * FROM cache_index WHERE complete = 1")
    fun observeComplete(): Flow<List<CacheEntity>>

    @Query("SELECT * FROM cache_index WHERE songId = :id")
    suspend fun get(id: String): CacheEntity?

    @Query("SELECT * FROM cache_index WHERE complete = 1")
    suspend fun getComplete(): List<CacheEntity>

    @Query("SELECT * FROM cache_index WHERE complete = 1 AND (title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%' OR album LIKE '%' || :q || '%')")
    suspend fun search(q: String): List<CacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CacheEntity)

    @Query("DELETE FROM cache_index WHERE songId = :id")
    suspend fun delete(id: String)

    @Query("SELECT COALESCE(SUM(bytes), 0) FROM cache_index WHERE complete = 1")
    suspend fun totalBytes(): Long

    @Query("SELECT * FROM cache_index WHERE complete = 1 AND likedPinned = 0 ORDER BY lastAccess ASC")
    suspend fun oldestNonLiked(): List<CacheEntity>

    @Query("UPDATE cache_index SET likedPinned = :pinned WHERE songId = :id")
    suspend fun setLikedPinned(id: String, pinned: Boolean)

    @Query("UPDATE cache_index SET lastAccess = :ts WHERE songId = :id")
    suspend fun touch(id: String, ts: Long = System.currentTimeMillis())
}

@Dao
interface LocalRadioDao {
    @Query("SELECT * FROM local_radio ORDER BY name ASC")
    fun observeAll(): Flow<List<LocalRadioEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalRadioEntity)

    @Query("DELETE FROM local_radio WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface LocalStarDao {
    @Query("SELECT * FROM local_stars ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<LocalStarEntity>>

    @Query("SELECT * FROM local_stars ORDER BY updatedAt DESC")
    suspend fun getAll(): List<LocalStarEntity>

    @Query("SELECT songId FROM local_stars")
    suspend fun getIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalStarEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<LocalStarEntity>)

    @Query("DELETE FROM local_stars WHERE songId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM local_stars")
    suspend fun clear()
}

@Dao
interface PendingStarDao {
    @Query("SELECT * FROM pending_stars ORDER BY updatedAt ASC")
    suspend fun getAll(): List<PendingStarEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingStarEntity)

    @Query("DELETE FROM pending_stars WHERE songId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM pending_stars")
    suspend fun clear()
}

@Database(
    entities = [
        BlacklistEntity::class,
        CacheEntity::class,
        LocalRadioEntity::class,
        LocalStarEntity::class,
        PendingStarEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class MuzoraDatabase : RoomDatabase() {
    abstract fun blacklistDao(): BlacklistDao
    abstract fun cacheDao(): CacheDao
    abstract fun localRadioDao(): LocalRadioDao
    abstract fun localStarDao(): LocalStarDao
    abstract fun pendingStarDao(): PendingStarDao
}
