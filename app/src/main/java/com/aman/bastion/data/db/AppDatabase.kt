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
import com.aman.bastion.data.hardcorelock.dao.HardcoreLockDao
import com.aman.bastion.data.hardcorelock.entity.HardcoreLockEntity
import com.aman.bastion.data.inapp.dao.InAppSignatureDao
import com.aman.bastion.data.inapp.dao.InAppRuleDao
import com.aman.bastion.data.inapp.entity.InAppSignatureEntity
import com.aman.bastion.data.inapp.entity.InAppRuleEntity
import com.aman.bastion.data.scheduling.dao.ScheduleDao
import com.aman.bastion.data.scheduling.entity.ScheduleEntity
import com.aman.bastion.data.service.dao.ServiceStateDao
import com.aman.bastion.data.service.entity.ServiceStateEntity
import com.aman.bastion.data.usage.dao.DailyUsageRecordDao
import com.aman.bastion.data.usage.dao.UsageHistoryDao
import com.aman.bastion.data.usage.entity.DailyUsageRecordEntity
import com.aman.bastion.data.usage.entity.UsageHistoryEntity
import com.aman.bastion.data.url.dao.UrlRuleDao
import com.aman.bastion.data.url.entity.UrlRuleEntity

@Database(
    entities = [
        AppRuleEntity::class,
        AppCategoryEntity::class,
        ScheduleEntity::class,
        DailyUsageRecordEntity::class,
        UsageHistoryEntity::class,
        InAppRuleEntity::class,
        ServiceStateEntity::class,
        HardcoreLockEntity::class,
        InAppSignatureEntity::class,
        UrlRuleEntity::class
    ],
    version = 5,
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
    abstract fun hardcoreLockDao(): HardcoreLockDao
    abstract fun inAppSignatureDao(): InAppSignatureDao
    abstract fun urlRuleDao(): UrlRuleDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE app_rules ADD COLUMN hardcore_until_ms INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE app_rules ADD COLUMN unlock_condition TEXT")
                database.execSQL("ALTER TABLE app_rules ADD COLUMN block_note TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `hardcore_locks` (
                        `package_name` TEXT NOT NULL,
                        `locked_at_ms` INTEGER NOT NULL,
                        `locked_until_ms` INTEGER NOT NULL,
                        `is_active` INTEGER NOT NULL,
                        PRIMARY KEY(`package_name`)
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `inapp_signatures` (
                        `id` TEXT NOT NULL,
                        `rule_id` TEXT NOT NULL,
                        `screen_state` TEXT NOT NULL,
                        `tier` INTEGER NOT NULL,
                        `match_type` TEXT NOT NULL,
                        `match_value` TEXT NOT NULL,
                        `is_regex` INTEGER NOT NULL,
                        `min_version_code` INTEGER,
                        `max_version_code` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `url_rules` (
                        `id` TEXT NOT NULL,
                        `pattern` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `is_enabled` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
