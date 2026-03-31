package com.aman.bastion.data.blocking

import com.aman.bastion.data.blocking.dao.AppRuleDao
import com.aman.bastion.data.blocking.entity.AppRuleEntity
import com.aman.bastion.domain.model.AppRule
import com.aman.bastion.domain.repository.AppRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRuleRepositoryImpl @Inject constructor(
    private val dao: AppRuleDao
) : AppRuleRepository {

    override fun getAll(): Flow<List<AppRule>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override fun getByPackage(packageName: String): Flow<AppRule?> =
        dao.getByPackage(packageName).map { it?.toDomain() }

    override suspend fun save(rule: AppRule) =
        dao.upsert(rule.toEntity())

    override suspend fun delete(packageName: String) =
        dao.delete(packageName)

    private fun AppRuleEntity.toDomain() = AppRule(
        packageName = packageName,
        dailyLimitMs = dailyLimitMs,
        isHardBlocked = isHardBlocked,
        categoryId = categoryId,
        createdAt = createdAt,
        hardcoreUntilMs = hardcoreUntilMs
    )

    private fun AppRule.toEntity() = AppRuleEntity(
        packageName = packageName,
        dailyLimitMs = dailyLimitMs,
        isHardBlocked = isHardBlocked,
        categoryId = categoryId,
        createdAt = createdAt,
        hardcoreUntilMs = hardcoreUntilMs
    )
}
