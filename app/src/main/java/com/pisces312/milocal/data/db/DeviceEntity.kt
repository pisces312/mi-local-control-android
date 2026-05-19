package com.pisces312.milocal.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val model: String = "",
    val ip: String,
    val token: String,
    val type: String = "generic",  // light, plug, climate, generic
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY sortOrder, createdAt")
    fun getAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun getById(id: Long): DeviceEntity?

    @Insert
    suspend fun insert(device: DeviceEntity): Long

    @Insert
    suspend fun insertAll(devices: List<DeviceEntity>): List<Long>

    @Update
    suspend fun update(device: DeviceEntity)

    @Delete
    suspend fun delete(device: DeviceEntity)
}

@Database(entities = [DeviceEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "milocal.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
