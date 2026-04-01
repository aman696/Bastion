package com.aman.bastion.data.blocking.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.aman.bastion.data.blocking.entity.AppRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {

    @Upsert
    suspend fun upsert(rule: AppRuleEntity)

    @Query("DELETE FROM app_rules WHERE package_name = :packageName")
    suspend fun delete(packageName: String)

    @Query("SELECT * FROM app_rules")
    fun getAll(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rules WHERE package_name = :packageName")
    fun getByPackage(packageName: String): Flow<AppRuleEntity?>

    @Query("SELECT * FROM app_rules WHERE package_name = :packageName LIMIT 1")
    suspend fun getByPackageSync(packageName: String): AppRuleEntity?

    @Query(
        """
        SELECT * FROM app_rules
        WHERE is_hard_blocked = 1
        AND hardcore_until_ms > 0
        AND hardcore_until_ms <= :nowMs
        """
    )
    suspend fun getExpiredHardcoreRules(nowMs: Long): List<AppRuleEntity>

    @Query("SELECT * FROM app_rules WHERE category_id = :categoryId")
    fun getByCategory(categoryId: String): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rules WHERE category_id = :categoryId")
    suspend fun getByCategorySync(categoryId: String): List<AppRuleEntity>
}
