package com.aman.bastion.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aman.bastion.data.blocking.dao.AppCategoryDao
import com.aman.bastion.data.blocking.dao.AppRuleDao
import com.aman.bastion.data.db.AppDatabase
import com.aman.bastion.data.db.EncryptedDatabaseMigrator
import com.aman.bastion.data.hardcorelock.dao.HardcoreLockDao
import com.aman.bastion.data.inapp.dao.InAppSignatureDao
import com.aman.bastion.data.inapp.dao.InAppRuleDao
import com.aman.bastion.data.scheduling.dao.ScheduleDao
import com.aman.bastion.data.service.dao.ServiceStateDao
import com.aman.bastion.data.usage.dao.DailyUsageRecordDao
import com.aman.bastion.data.usage.dao.UsageHistoryDao
import com.aman.bastion.data.url.dao.UrlRuleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        securePrefs: SharedPreferences
    ): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "bastion.db"
        )
            .openHelperFactory(EncryptedDatabaseMigrator.prepareFactory(context, securePrefs))
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .enableMultiInstanceInvalidation()
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5
            )
            .build()

    @Provides
    fun provideAppRuleDao(db: AppDatabase): AppRuleDao = db.appRuleDao()

    @Provides
    fun provideAppCategoryDao(db: AppDatabase): AppCategoryDao = db.appCategoryDao()

    @Provides
    fun provideScheduleDao(db: AppDatabase): ScheduleDao = db.scheduleDao()

    @Provides
    fun provideDailyUsageRecordDao(db: AppDatabase): DailyUsageRecordDao = db.dailyUsageRecordDao()

    @Provides
    fun provideUsageHistoryDao(db: AppDatabase): UsageHistoryDao = db.usageHistoryDao()

    @Provides
    fun provideInAppRuleDao(db: AppDatabase): InAppRuleDao = db.inAppRuleDao()

    @Provides
    fun provideServiceStateDao(db: AppDatabase): ServiceStateDao = db.serviceStateDao()

    @Provides
    fun provideHardcoreLockDao(db: AppDatabase): HardcoreLockDao = db.hardcoreLockDao()

    @Provides
    fun provideInAppSignatureDao(db: AppDatabase): InAppSignatureDao = db.inAppSignatureDao()

    @Provides
    fun provideUrlRuleDao(db: AppDatabase): UrlRuleDao = db.urlRuleDao()
}
