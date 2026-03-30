package com.aman.bastion.domain.repository

import com.aman.bastion.domain.model.AppRule
import kotlinx.coroutines.flow.Flow

interface AppRuleRepository {
    fun getAll(): Flow<List<AppRule>>
    fun getByPackage(packageName: String): Flow<AppRule?>
    suspend fun save(rule: AppRule)
    suspend fun delete(packageName: String)
}
