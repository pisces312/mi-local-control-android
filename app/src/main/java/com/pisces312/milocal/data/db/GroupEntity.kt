package com.pisces312.milocal.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val order: Int = 0,
    val color: String = "#1976D2"  // hex color
)

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY `order`, id")
    fun getAll(): Flow<List<GroupEntity>>

    @Insert
    suspend fun insert(group: GroupEntity): Long

    @Update
    suspend fun update(group: GroupEntity)

    @Delete
    suspend fun delete(group: GroupEntity)
}
