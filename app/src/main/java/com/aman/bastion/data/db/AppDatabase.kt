package com.aman.bastion.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aman.bastion.data.blocking.dao.AppCategoryDao
import com.aman.bastion.data.blocking.dao.AppRuleDao
import com.aman.bastion.data.blocking.entity.AppCategoryEntity
import com.aman.bastion.data.blocking.entity.AppRuleEntity
import com.aman.bastion.data.inapp.dao.InAppRuleDao
import com.aman.bastion.data.inapp.entity.InAppRuleEntity
import com.aman.bastion.data.scheduling.dao.ScheduleDao
import com.aman.bastion.data.scheduling.entity.ScheduleEntity
import com.aman.bastion.data.service.dao.ServiceStateDao
import com.aman.bastion.data.service.entity.ServiceStateEntity
import com.aman.bastion.data.usage.dao.DailyUsageRecordDao
import com.aman.bastion.data.usage.dao.UsageHistoryDao
import com.aman.bastion.data.usage.entity.DailyUsageRecordEntity
import com.aman.bastion.data.usage.entity.UsageHistoryEntity

@Database(
    entities = [
        AppRuleEntity::class,
        AppCategoryEntity::class,
        ScheduleEntity::class,
        DailyUsageRecordEntity::class,
        UsageHistoryEntity::class,
        InAppRuleEntity::class,
        ServiceStateEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appRuleDao(): AppRuleDao
    abstract fun appCategoryDao(): AppCategoryDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun dailyUsageRecordDao(): DailyUsageRecordDao
    abstract fun usageHistoryDao(): UsageHistoryDao
    abstract fun inAppRuleDao(): InAppRuleDao
    abstract fun serviceStateDao(): ServiceStateDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE app_rules ADD COLUMN hardcore_until_ms INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
