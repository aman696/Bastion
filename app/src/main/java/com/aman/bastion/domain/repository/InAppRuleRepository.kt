package com.aman.bastion.domain.repository

import com.aman.bastion.domain.model.InAppRule
import kotlinx.coroutines.flow.Flow

interface InAppRuleRepository {
    fun getByPackage(packageName: String): Flow<List<InAppRule>>
    fun getAll(): Flow<List<InAppRule>>
    suspend fun save(rule: InAppRule)
    suspend fun delete(id: String)
    suspend fun deleteByPackage(packageName: String)
}
