package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Room SQLite entity storing end-to-end encrypted resident records locally
 * so data remains secure and accessible offline.
 */
@Entity(tableName = "warga_kos")
data class WargaKosEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val encryptedPayload: String,
    val iv: String,
    val encryptionVersion: Int = 1,
    val syncStatus: String = SyncStatus.SYNCED.name,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

@Dao
interface WargaKosDao {
    @Query(
        "SELECT * FROM warga_kos WHERE userId = :userId AND syncStatus != 'PENDING_DELETE' " +
            "ORDER BY createdAtMillis ASC, id ASC"
    )
    fun observeActiveRecords(userId: String): Flow<List<WargaKosEntity>>

    @Query("SELECT * FROM warga_kos WHERE userId = :userId")
    suspend fun getAllForUser(userId: String): List<WargaKosEntity>

    @Query("SELECT * FROM warga_kos WHERE userId = :userId AND syncStatus = 'PENDING_UPLOAD'")
    suspend fun getPendingUploads(userId: String): List<WargaKosEntity>

    @Query("SELECT * FROM warga_kos WHERE userId = :userId AND syncStatus = 'PENDING_DELETE'")
    suspend fun getPendingDeletes(userId: String): List<WargaKosEntity>

    @Query("SELECT * FROM warga_kos WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WargaKosEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WargaKosEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<WargaKosEntity>)

    @Query("DELETE FROM warga_kos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM warga_kos WHERE userId = :userId AND syncStatus = 'SYNCED' AND id NOT IN (:activeIds)")
    suspend fun deleteSyncedNotIn(userId: String, activeIds: List<String>)

    @Query("DELETE FROM warga_kos WHERE userId = :userId AND syncStatus = 'SYNCED'")
    suspend fun deleteAllSyncedForUser(userId: String)
}

@Database(entities = [WargaKosEntity::class], version = 1, exportSchema = false)
abstract class WargaKosDatabase : RoomDatabase() {
    abstract fun wargaKosDao(): WargaKosDao

    companion object {
        @Volatile
        private var INSTANCE: WargaKosDatabase? = null

        fun getInstance(context: Context): WargaKosDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WargaKosDatabase::class.java,
                    "data_warga_kos.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
