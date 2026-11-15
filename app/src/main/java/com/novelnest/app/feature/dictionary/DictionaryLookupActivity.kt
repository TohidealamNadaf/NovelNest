package com.novelnest.app.feature.dictionary

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.novelnest.app.NovelNestApplication
import com.novelnest.app.core.designsystem.NovelNestTheme
import kotlin.math.min

/** Native text-processing entry: never modifies selected text or changes the reader's back stack. */
class DictionaryLookupActivity : ComponentActivity() {
    private lateinit var model: DictionaryViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as NovelNestApplication
        val initial = savedInstanceState?.getString(WORD) ?: selectedWord(intent)
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DictionaryViewModel(app.dictionaryRepository, initial) as T
        })[DictionaryViewModel::class.java]
        // A real floating Activity preserves the native reader's menu and handles underneath.
        setFinishOnTouchOutside(true)
        window.setLayout((min(resources.configuration.screenWidthDp - 40, 380).coerceAtLeast(1) *
            resources.displayMetrics.density).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        setContent {
            val settings by app.preferencesManager.readerSettings.collectAsStateWithLifecycle()
            val state by model.state.collectAsStateWithLifecycle()
            NovelNestTheme(manageSystemBars = false) {
                DictionaryPanel(state, settings.theme, model::retry, ::finish)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::model.isInitialized) model.activate()
    }
    override fun onStop() {
        // Configuration recreation retains the running ViewModel request and avoids a second HTTP call.
        if (::model.isInitialized && !isChangingConfigurations) model.deactivate()
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (::model.isInitialized) outState.putString(WORD, model.state.value.query)
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::model.isInitialized) model.select(selectedWord(intent))
    }
    private fun selectedWord(intent: Intent?): String? = try {
        if (intent?.action != Intent.ACTION_PROCESS_TEXT || intent.type != "text/plain") null
        else intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.takeIf { it.length <= 512 }?.toString()
    } catch (_: RuntimeException) { null } // Exported text-processing inputs can contain malformed parcels.

    companion object { private const val WORD = "dictionary_query" }
}
