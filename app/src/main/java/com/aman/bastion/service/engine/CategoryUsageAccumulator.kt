package com.aman.bastion.service.engine

import android.content.pm.PackageManager
import com.aman.bastion.data.blocking.dao.AppCategoryDao
import com.aman.bastion.data.blocking.dao.AppRuleDao
import com.aman.bastion.data.usage.entity.DailyUsageRecordEntity
import com.aman.bastion.service.OverlayEngine
import com.aman.bastion.service.OverlayType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryUsageAccumulator @Inject constructor(
    private val appCategoryDao: AppCategoryDao,
    private val appRuleDao: AppRuleDao,
    private val overlayEngine: OverlayEngine,
    private val packageManager: PackageManager
) {

    private val categoryElapsed = mutableMapOf<String, Long>()

    suspend fun onSessionFlushed(packageName: String, elapsedMs: Long) {
        val rule = appRuleDao.getByPackageSync(packageName) ?: return
        val categoryId = rule.categoryId ?: return

        categoryElapsed[categoryId] = (categoryElapsed[categoryId] ?: 0L) + elapsedMs
        checkCategoryLimit(categoryId)
    }

    fun resetAllAccumulators() {
        categoryElapsed.clear()
    }

    suspend fun rebuildFromDatabase(todayRecords: List<DailyUsageRecordEntity>) {
        categoryElapsed.clear()
        todayRecords.forEach { record ->
            val rule = appRuleDao.getByPackageSync(record.packageName) ?: return@forEach
            val catId = rule.categoryId ?: return@forEach
            categoryElapsed[catId] = (categoryElapsed[catId] ?: 0L) +
                maxOf(0L, record.elapsedMs - record.exclusionMs)
        }
    }

    private suspend fun checkCategoryLimit(categoryId: String) {
        val category = appCategoryDao.getByIdSync(categoryId) ?: return
        if (category.dailyLimitMs == 0L) return

        val totalElapsed = categoryElapsed[categoryId] ?: return
        if (totalElapsed < category.dailyLimitMs) return

        val appsInCategory = appRuleDao.getByCategorySync(categoryId)
        appsInCategory.forEach { appRule ->
            val appLabel = runCatching {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(
                        appRule.packageName,
                        PackageManager.ApplicationInfoFlags.of(0)
                    )
                ).toString()
            }.getOrElse { appRule.packageName }

            overlayEngine.showOverlay(
                "block_${appRule.packageName}",
                OverlayType.AppBlock(
                    packageName = appRule.packageName,
                    appLabel = appLabel,
                    lockedUntilMs = 0L,
                    reason = "Category limit reached: ${category.name}"
                )
            )
        }
    }
}
