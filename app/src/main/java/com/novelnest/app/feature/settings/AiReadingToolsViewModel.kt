package com.novelnest.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelnest.app.data.ai.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiReadingToolsSettingsState(
    val loaded: Boolean = false,
    val busy: Boolean = false,
    val settings: AiSettings = AiSettings(),
    val providers: List<AiProviderStatus> = emptyList(),
    val availableModels: List<AiModelAvailability> = emptyList(),
    val savedKeyRevisions: Map<AiProviderId, Long> = emptyMap(),
    val message: String = "",
    val failure: AiFailure? = null
)

/** No credential is exposed through observable state, including save acknowledgements. */
class AiReadingToolsViewModel(
    private val configuration: AiSettingsConfiguration,
    private val directory: AiModelDirectory,
    private val cache: AiResultCache
) : ViewModel() {
    private val mutable = MutableStateFlow(AiReadingToolsSettingsState())
    val state = mutable.asStateFlow()
    private var work: Job? = null
    val supportedModels: List<AiModelSpec> = AiProviderCatalog.models

    init { reload() }

    fun reload() = perform {
        val settings = configuration.settings()
        val providers = configuration.status()
        mutable.update { it.copy(loaded = true, settings = settings, providers = providers) }
    }
    fun saveSettings(settings: AiSettings) = perform {
        configuration.saveSettings(settings)
        val saved = configuration.settings()
        mutable.update { it.copy(loaded = true, settings = saved, message = "AI reading preferences saved.") }
    }
    fun replaceKey(provider: AiProviderId, replacement: String) = perform {
        configuration.replaceCredential(provider, replacement)
        directory.invalidate(provider)
        val statuses = configuration.status()
        mutable.update { it.copy(providers = statuses,
            savedKeyRevisions = if (replacement.isBlank()) it.savedKeyRevisions else
                it.savedKeyRevisions + (provider to ((it.savedKeyRevisions[provider] ?: 0L) + 1L)),
            availableModels = it.availableModels.filterNot { model -> model.route.provider == provider },
            message = if (replacement.isBlank()) "Saved key kept." else "API key saved securely. Availability has not been checked.") }
    }
    fun removeKey(provider: AiProviderId) = perform {
        configuration.removeCredential(provider)
        directory.invalidate(provider)
        val statuses = configuration.status()
        mutable.update { it.copy(providers = statuses,
            availableModels = it.availableModels.filterNot { model -> model.route.provider == provider }, message = "API key removed.") }
    }
    fun checkProvider(provider: AiProviderId) = perform {
        val key = configuration.credential(provider)
        if (key.isBlank()) throw AiException(AiErrorCode.CREDENTIALS, "Save an API key before checking availability.")
        val available = directory.available(provider, key, force = true)
        mutable.update { current -> current.copy(availableModels = current.availableModels.filterNot { it.route.provider == provider } +
            supportedModels.filter { it.route.provider == provider }.map { AiModelAvailability(it.route, it.route.model in available) },
            message = if (provider == AiProviderId.NVIDIA)
                "NVIDIA catalog checked. Listed models can still be unavailable to your account or at capacity. Generation access and remaining allowance have not been verified."
            else "Model availability checked. This does not generate text, verify remaining allowance, or verify your account billing tier.") }
    }
    fun clearResults() = perform {
        val cleared = cache.clear()
        mutable.update { it.copy(message = if (cleared) "AI result cache cleared. Credentials and reader data kept."
            else "Some AI cache files could not be removed.") }
    }
    fun dismissMessage() { mutable.update { it.copy(message = "", failure = null) } }

    private fun perform(block: suspend () -> Unit) {
        if (work?.isActive == true) return
        mutable.update { it.copy(busy = true, failure = null, message = "") }
        work = viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val failure = error as? AiException ?: AiException(AiErrorCode.STORAGE,
                    if (error is IllegalArgumentException) "Check the key and model selections. Save up to three supported routes per action."
                    else "AI settings could not be updated. Retry when ready.")
                mutable.update { it.copy(failure = AiFailure(failure.code, failure.message.orEmpty(), failure.retryAfterSeconds)) }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
