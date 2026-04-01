package com.aman.bastion.data.service.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.aman.bastion.data.service.entity.ServiceStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceStateDao {

    @Upsert
    suspend fun upsert(state: ServiceStateEntity)

    @Upsert
    suspend fun upsertAll(states: List<ServiceStateEntity>)

    @Query("SELECT * FROM service_state WHERE id = 1 LIMIT 1")
    fun observe(): Flow<ServiceStateEntity?>

    @Query("SELECT * FROM service_state WHERE id = 1 LIMIT 1")
    suspend fun get(): ServiceStateEntity?

    @Query("SELECT * FROM service_state")
    suspend fun getAllSync(): List<ServiceStateEntity>
}
