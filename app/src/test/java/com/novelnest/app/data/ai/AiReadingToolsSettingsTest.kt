package com.novelnest.app.data.ai

import com.novelnest.app.feature.settings.AiReadingToolsViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AiReadingToolsSettingsTest {
    @get:Rule val temp = TemporaryFolder()
    private val main = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(main) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class Configuration : AiSettingsConfiguration {
        var value = AiSettings()
        var rejectKey = false
        var saveGate: CompletableDeferred<Unit>? = null
        val keys = mutableSetOf<AiProviderId>()
        override suspend fun settings() = value
        override suspend fun credential(provider: AiProviderId) = "offline-test-key"
        override suspend fun saveSettings(settings: AiSettings) { value = settings }
        override suspend fun replaceCredential(provider: AiProviderId, replacement: String) {
            saveGate?.await()
            if (rejectKey) throw AiException(AiErrorCode.STORAGE, "Could not save securely.")
            if (replacement.isNotBlank()) keys.add(provider)
        }
        override suspend fun removeCredential(provider: AiProviderId) { keys.remove(provider) }
        override suspend fun status() = AiProviderId.entries.map { AiProviderStatus(it, it in keys) }
    }
    private fun viewModel(config: Configuration) = AiReadingToolsViewModel(config,
        AiModelDirectory(listOf(FakeProvider(AiProviderId.OPEN_ROUTER))), AiResultCache(temp.newFolder()))

    @Test fun keySaveAcknowledgementWaitsForSuccessfulPersistence() = runTest {
        val config = Configuration()
        val vm = viewModel(config)
        try {
            runCurrent()
            config.saveGate = CompletableDeferred()
            vm.replaceKey(AiProviderId.OPEN_ROUTER, "offline-test-replacement")
            runCurrent()
            assertTrue(vm.state.value.busy)
            assertTrue(vm.state.value.savedKeyRevisions.isEmpty())
            config.saveGate!!.complete(Unit)
            runCurrent()
            assertFalse(vm.state.value.busy)
            assertEquals(1L, vm.state.value.savedKeyRevisions[AiProviderId.OPEN_ROUTER])
            assertTrue(vm.state.value.providers.single { it.provider == AiProviderId.OPEN_ROUTER }.configured)
        } finally { vm.viewModelScope.cancel() }
    }
    @Test fun failedSaveDoesNotAcknowledgeOrReplaceConfiguredKey() = runTest {
        val config = Configuration().apply { keys.add(AiProviderId.OPEN_ROUTER); rejectKey = true }
        val vm = viewModel(config)
        try {
            runCurrent()
            vm.replaceKey(AiProviderId.OPEN_ROUTER, "offline-test-replacement")
            runCurrent()
            assertEquals(AiErrorCode.STORAGE, vm.state.value.failure?.code)
            assertTrue(vm.state.value.savedKeyRevisions.isEmpty())
            assertTrue(vm.state.value.providers.single { it.provider == AiProviderId.OPEN_ROUTER }.configured)
            assertFalse(vm.state.value.busy)
        } finally { vm.viewModelScope.cancel() }
    }
    @Test fun savesAcknowledgeOnlyTheirOwnProviderAndBlankKeepsKey() = runTest {
        val config = Configuration()
        val vm = viewModel(config)
        try {
            runCurrent()
            vm.replaceKey(AiProviderId.OPEN_ROUTER, "offline-test-key"); runCurrent()
            vm.replaceKey(AiProviderId.GROQ, "offline-test-key"); runCurrent()
            vm.replaceKey(AiProviderId.GROQ, ""); runCurrent()
            assertEquals(mapOf(AiProviderId.OPEN_ROUTER to 1L, AiProviderId.GROQ to 1L), vm.state.value.savedKeyRevisions)
            assertEquals(2, config.keys.size)
        } finally { vm.viewModelScope.cancel() }
    }
    @Test fun repeatedSaveWhileBusyDoesNotProduceAFalseSuccessAcknowledgement() = runTest {
        val config = Configuration()
        val vm = viewModel(config)
        try {
            runCurrent()
            config.saveGate = CompletableDeferred()
            vm.replaceKey(AiProviderId.OPEN_ROUTER, "offline-test-key"); runCurrent()
            vm.replaceKey(AiProviderId.GROQ, "offline-test-key"); runCurrent()
            assertTrue(vm.state.value.savedKeyRevisions.isEmpty())
            config.saveGate!!.complete(Unit); runCurrent()
            assertEquals(mapOf(AiProviderId.OPEN_ROUTER to 1L), vm.state.value.savedKeyRevisions)
            assertFalse(AiProviderId.GROQ in config.keys)
        } finally { vm.viewModelScope.cancel() }
    }
    @Test fun policyAndConsentPersistAfterAnOperationFinishes() = runTest {
        val config = Configuration()
        val vm = viewModel(config)
        try {
            runCurrent()
            val selected = configured(freeRoute).copy(costPolicy = AiCostPolicy.FREE_ACCOUNT_PROVIDERS)
            vm.saveSettings(selected); runCurrent()
            assertEquals(selected, vm.state.value.settings)
            assertFalse(vm.state.value.busy)
            vm.saveSettings(selected.copy(consentedProviders = emptySet())); runCurrent()
            assertTrue(config.value.consentedProviders.isEmpty())
        } finally { vm.viewModelScope.cancel() }
    }
}
