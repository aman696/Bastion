package com.aman.bastion.data.db

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aman.bastion.data.blocking.entity.AppCategoryEntity
import com.aman.bastion.data.blocking.entity.AppRuleEntity
import com.aman.bastion.data.hardcorelock.entity.HardcoreLockEntity
import com.aman.bastion.data.inapp.entity.InAppRuleEntity
import com.aman.bastion.data.inapp.entity.InAppSignatureEntity
import com.aman.bastion.data.scheduling.entity.ScheduleEntity
import com.aman.bastion.data.service.entity.ServiceStateEntity
import com.aman.bastion.data.usage.entity.DailyUsageRecordEntity
import com.aman.bastion.data.usage.entity.UsageHistoryEntity
import com.aman.bastion.data.url.entity.UrlRuleEntity
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

object EncryptedDatabaseMigrator {

    private const val DATABASE_NAME = "bastion.db"
    private const val TEMP_DATABASE_NAME = "bastion-encrypted-migration.db"
    private const val PREF_DB_PASSPHRASE = "secure_db_passphrase_v1"
    private const val SQLITE_HEADER = "SQLite format 3"

    @Synchronized
    fun prepareFactory(
        context: Context,
        securePrefs: SharedPreferences
    ): SupportOpenHelperFactory {
        System.loadLibrary("sqlcipher")

        val passphrase = getOrCreatePassphrase(securePrefs)
        migrateIfNeeded(context, passphrase)
        return SupportOpenHelperFactory(passphrase)
    }

    private fun migrateIfNeeded(context: Context, passphrase: ByteArray) {
        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        if (!databaseFile.exists() || !isPlaintextDatabase(databaseFile)) return

        val legacyDb = buildPlaintextDatabase(context, DATABASE_NAME)
        val snapshot = runBlocking {
            DatabaseSnapshot(
                appRules = legacyDb.appRuleDao().getAllSync(),
                appCategories = legacyDb.appCategoryDao().getAllSync(),
                schedules = legacyDb.scheduleDao().getAllSync(),
                dailyUsage = legacyDb.dailyUsageRecordDao().getAllSync(),
                usageHistory = legacyDb.usageHistoryDao().getAllSync(),
                inAppRules = legacyDb.inAppRuleDao().getAllSync(),
                serviceStates = legacyDb.serviceStateDao().getAllSync(),
                hardcoreLocks = legacyDb.hardcoreLockDao().getAllSync(),
                inAppSignatures = legacyDb.inAppSignatureDao().getAllSync(),
                urlRules = legacyDb.urlRuleDao().getAllSync()
            )
        }
        legacyDb.close()

        val tempDbFile = context.getDatabasePath(TEMP_DATABASE_NAME)
        deleteDatabaseArtifacts(context, TEMP_DATABASE_NAME)

        val encryptedDb = buildEncryptedDatabase(context, TEMP_DATABASE_NAME, passphrase)
        runBlocking {
            encryptedDb.runInTransaction {
                runBlocking {
                    encryptedDb.appCategoryDao().upsertAll(snapshot.appCategories)
                    encryptedDb.appRuleDao().upsertAll(snapshot.appRules)
                    encryptedDb.scheduleDao().upsertAll(snapshot.schedules)
                    encryptedDb.dailyUsageRecordDao().upsertAll(snapshot.dailyUsage)
                    encryptedDb.usageHistoryDao().insertAll(snapshot.usageHistory)
                    encryptedDb.inAppRuleDao().upsertAll(snapshot.inAppRules)
                    encryptedDb.serviceStateDao().upsertAll(snapshot.serviceStates)
                    encryptedDb.hardcoreLockDao().upsertAll(snapshot.hardcoreLocks)
                    encryptedDb.inAppSignatureDao().upsertAll(snapshot.inAppSignatures)
                    encryptedDb.urlRuleDao().upsertAll(snapshot.urlRules)
                }
            }
        }
        encryptedDb.close()

        deleteDatabaseArtifacts(context, DATABASE_NAME)
        moveDatabaseArtifacts(context, TEMP_DATABASE_NAME, DATABASE_NAME)
    }

    private fun buildPlaintextDatabase(
        context: Context,
        databaseName: String
    ): AppDatabase = baseBuilder(context, databaseName).build()

    private fun buildEncryptedDatabase(
        context: Context,
        databaseName: String,
        passphrase: ByteArray
    ): AppDatabase = baseBuilder(context, databaseName)
        .openHelperFactory(SupportOpenHelperFactory(passphrase))
        .build()

    private fun baseBuilder(
        context: Context,
        databaseName: String
    ) = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        databaseName
    )
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .enableMultiInstanceInvalidation()
        .addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5
        )

    private fun getOrCreatePassphrase(securePrefs: SharedPreferences): ByteArray {
        val existing = securePrefs.getString(PREF_DB_PASSPHRASE, null)
        if (!existing.isNullOrBlank()) {
            return Base64.getDecoder().decode(existing)
        }

        val random = ByteArray(32)
        SecureRandom().nextBytes(random)
        val encoded = Base64.getEncoder().encodeToString(random)
        securePrefs.edit().putString(PREF_DB_PASSPHRASE, encoded).apply()
        return random
    }

    private fun isPlaintextDatabase(file: File): Boolean {
        val header = ByteArray(SQLITE_HEADER.length)
        return runCatching {
            file.inputStream().use { input ->
                val read = input.read(header)
                read == header.size && String(header, Charsets.US_ASCII) == SQLITE_HEADER
            }
        }.getOrDefault(false)
    }

    private fun deleteDatabaseArtifacts(context: Context, databaseName: String) {
        databaseCandidates(context, databaseName).forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private fun moveDatabaseArtifacts(context: Context, fromName: String, toName: String) {
        databaseCandidates(context, fromName)
            .zip(databaseCandidates(context, toName))
            .forEach { (from, to) ->
                if (from.exists()) {
                    if (to.exists()) {
                        to.delete()
                    }
                    from.renameTo(to)
                }
            }
    }

    private fun databaseCandidates(context: Context, databaseName: String): List<File> {
        val main = context.getDatabasePath(databaseName)
        return listOf(
            main,
            File("${main.path}-wal"),
            File("${main.path}-shm"),
            File("${main.path}-journal")
        )
    }

    private data class DatabaseSnapshot(
        val appRules: List<AppRuleEntity>,
        val appCategories: List<AppCategoryEntity>,
        val schedules: List<ScheduleEntity>,
        val dailyUsage: List<DailyUsageRecordEntity>,
        val usageHistory: List<UsageHistoryEntity>,
        val inAppRules: List<InAppRuleEntity>,
        val serviceStates: List<ServiceStateEntity>,
        val hardcoreLocks: List<HardcoreLockEntity>,
        val inAppSignatures: List<InAppSignatureEntity>,
        val urlRules: List<UrlRuleEntity>
    )
}
