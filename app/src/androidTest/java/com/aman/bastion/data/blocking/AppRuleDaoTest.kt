package com.aman.bastion.data.blocking

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aman.bastion.data.blocking.entity.AppRuleEntity
import com.aman.bastion.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppRuleDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun upsert_andGetAll_returnsRule() = runTest {
        val entity = AppRuleEntity(
            packageName = "com.test.app",
            dailyLimitMs = 0L,
            isHardBlocked = false,
            categoryId = null,
            createdAt = 1_000L,
            hardcoreUntilMs = 0L
        )
        db.appRuleDao().upsert(entity)
        val result = db.appRuleDao().getAll().first()
        assertEquals(1, result.size)
        assertEquals(entity, result[0])
    }

    @Test
    fun upsert_withHardcoreUntilMs_persists() = runTest {
        val entity = AppRuleEntity(
            packageName = "com.test.hardcore",
            dailyLimitMs = 0L,
            isHardBlocked = true,
            categoryId = null,
            createdAt = 1_000L,
            hardcoreUntilMs = 99_999_000L
        )
        db.appRuleDao().upsert(entity)
        val result = db.appRuleDao().getByPackage("com.test.hardcore").first()
        assertEquals(99_999_000L, result?.hardcoreUntilMs)
    }

    @Test
    fun delete_removesRule() = runTest {
        db.appRuleDao().upsert(
            AppRuleEntity("com.test.app", 0L, false, null, 1_000L, 0L)
        )
        db.appRuleDao().delete("com.test.app")
        val result = db.appRuleDao().getAll().first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun getByPackage_whenAbsent_returnsNull() = runTest {
        val result = db.appRuleDao().getByPackage("com.does.not.exist").first()
        assertNull(result)
    }
}
