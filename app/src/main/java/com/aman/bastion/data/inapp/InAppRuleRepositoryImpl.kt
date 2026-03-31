package com.aman.bastion.data.inapp

import com.aman.bastion.data.inapp.dao.InAppRuleDao
import com.aman.bastion.data.inapp.entity.InAppRuleEntity
import com.aman.bastion.domain.model.InAppRule
import com.aman.bastion.domain.model.RuleType
import com.aman.bastion.domain.repository.InAppRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InAppRuleRepositoryImpl @Inject constructor(
    private val dao: InAppRuleDao
) : InAppRuleRepository {

    override fun getByPackage(packageName: String): Flow<List<InAppRule>> =
        dao.getByPackage(packageName).map { it.map(InAppRuleEntity::toDomain) }

    override fun getAll(): Flow<List<InAppRule>> =
        dao.getAll().map { it.map(InAppRuleEntity::toDomain) }

    override suspend fun save(rule: InAppRule) = dao.upsert(rule.toEntity())

    override suspend fun delete(id: String) = dao.delete(id)
}

private fun InAppRuleEntity.toDomain() = InAppRule(
    id          = id,
    packageName = packageName,
    featureId   = featureId,
    ruleName    = ruleName,
    isEnabled   = isEnabled,
    ruleType    = RuleType.valueOf(ruleType)
)

private fun InAppRule.toEntity() = InAppRuleEntity(
    id          = id,
    packageName = packageName,
    featureId   = featureId,
    ruleName    = ruleName,
    isEnabled   = isEnabled,
    ruleType    = ruleType.name
)
