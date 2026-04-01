package com.aman.bastion.data.inapp.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.aman.bastion.data.inapp.entity.InAppRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InAppRuleDao {

    @Upsert
    suspend fun upsert(rule: InAppRuleEntity)

    @Query("DELETE FROM inapp_rules WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM inapp_rules WHERE package_name = :packageName")
    suspend fun deleteByPackage(packageName: String)

    @Query("SELECT * FROM inapp_rules WHERE package_name = :packageName")
    fun getByPackage(packageName: String): Flow<List<InAppRuleEntity>>

    @Query("SELECT * FROM inapp_rules WHERE package_name = :packageName")
    suspend fun getByPackageSync(packageName: String): List<InAppRuleEntity>

    @Query("SELECT * FROM inapp_rules")
    fun getAll(): Flow<List<InAppRuleEntity>>
}
