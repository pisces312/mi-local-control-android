package com.pisces312.milocal.data.repository

import com.pisces312.milocal.data.db.GroupDao
import com.pisces312.milocal.data.db.GroupEntity
import kotlinx.coroutines.flow.Flow

class GroupRepository(private val dao: GroupDao) {
    fun allGroups(): Flow<List<GroupEntity>> = dao.getAll()
    suspend fun insert(group: GroupEntity): Long = dao.insert(group)
    suspend fun update(group: GroupEntity) = dao.update(group)
    suspend fun delete(group: GroupEntity) = dao.delete(group)
}
