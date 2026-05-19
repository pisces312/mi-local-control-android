package com.pisces312.milocal.data.repository

import com.pisces312.milocal.data.db.DeviceDao
import com.pisces312.milocal.data.db.DeviceEntity
import kotlinx.coroutines.flow.Flow

class DeviceRepository(private val dao: DeviceDao) {
    fun allDevices(): Flow<List<DeviceEntity>> = dao.getAll()
    suspend fun getById(id: Long): DeviceEntity? = dao.getById(id)
    suspend fun insert(device: DeviceEntity): Long = dao.insert(device)
    suspend fun insertAll(devices: List<DeviceEntity>): List<Long> = dao.insertAll(devices)
    suspend fun update(device: DeviceEntity) = dao.update(device)
    suspend fun delete(device: DeviceEntity) = dao.delete(device)
}
