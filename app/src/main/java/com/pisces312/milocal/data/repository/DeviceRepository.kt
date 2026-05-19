package com.pisces312.milocal.data.repository

import com.pisces312.milocal.data.db.DeviceDao
import com.pisces312.milocal.data.db.DeviceEntity
import kotlinx.coroutines.flow.Flow

class DeviceRepository(private val dao: DeviceDao) {
    fun allDevices(): Flow<List<DeviceEntity>> = dao.getAll()
    suspend fun getById(id: Long): DeviceEntity? = dao.getById(id)
    fun getByGroup(groupId: Long): Flow<List<DeviceEntity>> = dao.getByGroup(groupId)
    fun getUngrouped(): Flow<List<DeviceEntity>> = dao.getUngrouped()
    fun getByRoom(room: String): Flow<List<DeviceEntity>> = dao.getByRoom(room)
    fun getUnassigned(): Flow<List<DeviceEntity>> = dao.getUnassigned()
    fun getRooms(): Flow<List<String>> = dao.getRooms()
    suspend fun insert(device: DeviceEntity): Long = dao.insert(device)
    suspend fun insertAll(devices: List<DeviceEntity>): List<Long> = dao.insertAll(devices)
    suspend fun update(device: DeviceEntity) = dao.update(device)
    suspend fun delete(device: DeviceEntity) = dao.delete(device)
}
