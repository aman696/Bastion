package com.aman.bastion.ui.home

import android.content.Context
import android.content.pm.PackageManager
import com.aman.bastion.domain.model.AppRule
import com.aman.bastion.domain.repository.AppRuleRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAppRuleRepository
    private lateinit var context: Context
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAppRuleRepository()

        val pm = mockk<PackageManager>(relaxed = true) {
            every { getInstalledApplications(any<Int>()) } returns emptyList()
        }
        context = mockk(relaxed = true) {
            every { packageName } returns "com.aman.bastion"
            every { packageManager } returns pm
        }
        viewModel = HomeViewModel(fakeRepository, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onBlock_savesUnblockedRuleToRepository() = runTest {
        viewModel.onBlock("com.test.app")
        advanceUntilIdle()
        val rule = fakeRepository.getRuleFor("com.test.app")
        assertNotNull(rule)
        assertFalse(rule!!.isHardBlocked)
        assertTrue(rule.hardcoreUntilMs == 0L)
    }

    @Test
    fun onHardcoreBlock_savesHardBlockedRuleWithFutureTimestamp() = runTest {
        val before = System.currentTimeMillis()
        viewModel.onHardcoreBlock("com.test.app", 3_600_000L)
        advanceUntilIdle()
        val rule = fakeRepository.getRuleFor("com.test.app")
        assertNotNull(rule)
        assertTrue(rule!!.isHardBlocked)
        assertTrue(rule.hardcoreUntilMs >= before + 3_600_000L)
    }

    @Test
    fun onUnblock_softBlocked_deletesRule() = runTest {
        fakeRepository.save(AppRule("com.test.app", 0L, false, null, 0L, 0L))
        viewModel.onUnblock("com.test.app")
        advanceUntilIdle()
        assertNull(fakeRepository.getRuleFor("com.test.app"))
    }

    @Test
    fun onUnblock_whenHardcoreActive_doesNotDeleteRule() = runTest {
        val future = System.currentTimeMillis() + 3_600_000L
        fakeRepository.save(AppRule("com.test.app", 0L, true, null, 0L, future))
        viewModel.onUnblock("com.test.app")
        advanceUntilIdle()
        assertNotNull(fakeRepository.getRuleFor("com.test.app"))
    }

    @Test
    fun onAppSelected_setsSelectedAppInUiState() = runTest {
        val item = AppListItem("com.test.app", "Test App", mockk(relaxed = true), false, 0L)
        viewModel.onAppSelected(item)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.selectedApp?.packageName == "com.test.app")
    }

    @Test
    fun onDismissSheet_clearsSelectedApp() = runTest {
        val item = AppListItem("com.test.app", "Test App", mockk(relaxed = true), false, 0L)
        viewModel.onAppSelected(item)
        viewModel.onDismissSheet()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedApp)
    }
}

// --- Fake repo used only in this test file ---

class FakeAppRuleRepository : AppRuleRepository {
    private val store = mutableMapOf<String, AppRule>()
    private val flow = MutableStateFlow<List<AppRule>>(emptyList())

    fun getRuleFor(packageName: String): AppRule? = store[packageName]

    override fun getAll(): Flow<List<AppRule>> = flow

    override fun getByPackage(packageName: String): Flow<AppRule?> =
        flow.map { list -> list.find { it.packageName == packageName } }

    override suspend fun save(rule: AppRule) {
        store[rule.packageName] = rule
        flow.value = store.values.toList()
    }

    override suspend fun delete(packageName: String) {
        store.remove(packageName)
        flow.value = store.values.toList()
    }
}
