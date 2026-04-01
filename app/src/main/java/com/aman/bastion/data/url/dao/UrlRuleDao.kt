package com.aman.bastion.data.url.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.aman.bastion.data.url.entity.UrlRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UrlRuleDao {

    @Upsert
    suspend fun upsert(rule: UrlRuleEntity)

    @Upsert
    suspend fun upsertAll(rules: List<UrlRuleEntity>)

    @Query("DELETE FROM url_rules WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM url_rules ORDER BY pattern ASC")
    fun getAll(): Flow<List<UrlRuleEntity>>

    @Query("SELECT * FROM url_rules ORDER BY pattern ASC")
    suspend fun getAllSync(): List<UrlRuleEntity>

    @Query("SELECT * FROM url_rules WHERE id = :id LIMIT 1")
    suspend fun getByIdSync(id: String): UrlRuleEntity?

    @Query("SELECT * FROM url_rules WHERE is_enabled = 1 ORDER BY pattern ASC")
    suspend fun getEnabledSync(): List<UrlRuleEntity>
}
