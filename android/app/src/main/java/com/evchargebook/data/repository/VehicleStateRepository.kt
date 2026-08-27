package com.evchargebook.data.repository

import com.evchargebook.data.dao.VehicleStateDao
import com.evchargebook.data.entity.VehicleStateEntity
import kotlinx.coroutines.flow.Flow

class VehicleStateRepository(
    private val dao: VehicleStateDao
) {
    fun observe(vehicleId: Long): Flow<VehicleStateEntity?> = dao.observe(vehicleId)

    suspend fun get(vehicleId: Long): VehicleStateEntity? = dao.get(vehicleId)

    suspend fun getAll(): List<VehicleStateEntity> = dao.getAll()

    suspend fun save(state: VehicleStateEntity) = dao.upsert(state)

    suspend fun deleteAll() = dao.deleteAll()
}
