package com.pisces312.milocal.data.db

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val model: String = "",
    val ip: String,
    val token: String,
    val mac: String = "",
    val type: String = "generic",
    val groupId: Long? = null,
    val room: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY sortOrder, createdAt")
    fun getAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun getById(id: Long): DeviceEntity?

    @Query("SELECT * FROM devices WHERE groupId = :groupId ORDER BY sortOrder, createdAt")
    fun getByGroup(groupId: Long): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE groupId IS NULL ORDER BY sortOrder, createdAt")
    fun getUngrouped(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE room = :room ORDER BY sortOrder, createdAt")
    fun getByRoom(room: String): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE room IS NULL ORDER BY sortOrder, createdAt")
    fun getUnassigned(): Flow<List<DeviceEntity>>

    @Query("SELECT DISTINCT room FROM devices WHERE room IS NOT NULL ORDER BY room")
    fun getRooms(): Flow<List<String>>

    @Insert
    suspend fun insert(device: DeviceEntity): Long

    @Insert
    suspend fun insertAll(devices: List<DeviceEntity>): List<Long>

    @Update
    suspend fun update(device: DeviceEntity)

    @Delete
    suspend fun delete(device: DeviceEntity)
}

@Database(entities = [DeviceEntity::class, GroupEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun groupDao(): GroupDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "milocal.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `order` INTEGER NOT NULL, `color` TEXT NOT NULL)")
                db.execSQL("ALTER TABLE devices ADD COLUMN `groupId` INTEGER")
                db.execSQL("ALTER TABLE devices ADD COLUMN `room` TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE devices ADD COLUMN `mac` TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
