package com.novelnest.app.feature.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelnest.app.data.dictionary.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class DictionaryProblem(val kind: DictionaryError, val message: String)
internal data class DictionaryUiState(
    val query: String = "",
    val result: DictionaryResult? = null,
    val loading: Boolean = false,
    val cached: Boolean = false,
    val stale: Boolean = false,
    val paused: Boolean = false,
    val retryBlocked: Boolean = false,
    val problem: DictionaryProblem? = null
) {
    val canRetry: Boolean get() = !loading && !retryBlocked && problem?.kind !in
        setOf(DictionaryError.INVALID_WORD, DictionaryError.NOT_FOUND)
}

/** Reader/Activity objects never enter this state. Rotation retains this one lookup, not a new fetch. */
internal class DictionaryViewModel(private val repository: DictionaryRepository, initialWord: String?) : ViewModel() {
    private val mutableState = MutableStateFlow(DictionaryUiState())
    val state = mutableState.asStateFlow()
    private var word: String? = null
    private var visible = false
    private var attempted = false
    private var requestId = 0L
    private var lookup: Job? = null
    private var cooldown: Job? = null
    init { select(initialWord) }

    fun select(raw: String?) {
        ++requestId
        lookup?.cancel()
        cooldown?.cancel()
        attempted = false
        word = try { DictionaryWord.normalize(raw) }
        catch (error: DictionaryException) {
            mutableState.value = DictionaryUiState(problem = DictionaryProblem(error.kind, error.message.orEmpty()))
            null
        }
        word?.let { mutableState.value = DictionaryUiState(query = it) }
        if (visible && word != null) load(false)
    }

    fun activate() {
        visible = true
        if (!attempted && word != null) load(false) else armCooldown()
    }

    fun deactivate() {
        visible = false
        ++requestId
        lookup?.cancel()
        lookup = null
        cooldown?.cancel()
        if (mutableState.value.loading) mutableState.value = mutableState.value.copy(loading = false, paused = true)
    }

    fun retry() {
        if (mutableState.value.canRetry && repository.cooldownRemainingMs() == 0L) load(true)
        else armCooldown()
    }

    private fun load(force: Boolean) {
        val query = word ?: return
        if (!visible || lookup?.isActive == true) return
        val id = ++requestId
        attempted = true
        cooldown?.cancel()
        mutableState.value = mutableState.value.copy(loading = true, paused = false, problem = null, retryBlocked = false)
        lookup = viewModelScope.launch {
            try {
                val snapshot = repository.cached(query)
                if (id != requestId) return@launch
                val fresh = snapshot.result?.isFresh(System.currentTimeMillis()) == true
                // Clearing/evicting disk cache must not erase a definition already on screen.
                val displayed = snapshot.result ?: mutableState.value.result
                mutableState.value = mutableState.value.copy(result = displayed,
                    cached = snapshot.result != null || mutableState.value.cached,
                    stale = displayed != null && !displayed.isFresh(System.currentTimeMillis()))
                if (fresh && !force) return@launch
                val result = repository.fetch(query, snapshot.generation, force)
                if (id == requestId) mutableState.value = mutableState.value.copy(result = result, cached = false, stale = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: DictionaryException) {
                if (id == requestId) mutableState.value = mutableState.value.copy(problem = DictionaryProblem(error.kind, error.message.orEmpty()))
            } catch (_: Exception) {
                if (id == requestId) mutableState.value = mutableState.value.copy(problem =
                    DictionaryProblem(DictionaryError.UNAVAILABLE, "Could not complete the dictionary lookup. Please try again."))
            } finally {
                if (id == requestId) {
                    mutableState.value = mutableState.value.copy(loading = false)
                    armCooldown()
                }
            }
        }
    }

    // One delayed UI update at expiry, not a per-second poll or an automatic HTTP retry.
    private fun armCooldown() {
        cooldown?.cancel()
        val remaining = repository.cooldownRemainingMs()
        mutableState.value = mutableState.value.copy(retryBlocked = remaining > 0)
        if (visible && remaining > 0) cooldown = viewModelScope.launch {
            delay(remaining)
            if (visible) mutableState.value = mutableState.value.copy(retryBlocked = false)
        }
    }
}
