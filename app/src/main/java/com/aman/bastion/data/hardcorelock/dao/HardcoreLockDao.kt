package com.aman.bastion.data.hardcorelock.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.aman.bastion.data.hardcorelock.entity.HardcoreLockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HardcoreLockDao {

    @Upsert
    suspend fun upsert(lock: HardcoreLockEntity)

    @Upsert
    suspend fun upsertAll(locks: List<HardcoreLockEntity>)

    @Query("DELETE FROM hardcore_locks WHERE package_name = :packageName")
    suspend fun delete(packageName: String)

    @Query("SELECT * FROM hardcore_locks WHERE is_active = 1")
    fun observeActive(): Flow<List<HardcoreLockEntity>>

    @Query("SELECT * FROM hardcore_locks")
    suspend fun getAllSync(): List<HardcoreLockEntity>

    @Query("SELECT * FROM hardcore_locks WHERE package_name = :packageName LIMIT 1")
    suspend fun getByPackage(packageName: String): HardcoreLockEntity?

    @Query(
        """
        UPDATE hardcore_locks
        SET is_active = 0
        WHERE locked_until_ms <= :nowMs AND is_active = 1
        """
    )
    suspend fun deactivateExpired(nowMs: Long)
}
