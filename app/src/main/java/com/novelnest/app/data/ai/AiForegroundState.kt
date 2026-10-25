package com.novelnest.app.data.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Activity lifecycle signal only. Generation never starts itself on a foreground transition. */
class AiForegroundState {
    private val mutable = MutableStateFlow(true)
    val foreground = mutable.asStateFlow()
    fun update(foreground: Boolean) { mutable.value = foreground }
}
