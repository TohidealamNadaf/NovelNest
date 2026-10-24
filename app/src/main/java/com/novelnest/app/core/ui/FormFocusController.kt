package com.novelnest.app.core.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController

@Composable
internal fun rememberFormFocusController(): FormFocusController {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return remember(focusManager, keyboard) { FormFocusController(focusManager, keyboard) }
}

/** Observes outside taps without consuming child clicks, field selection or list drags. */
internal class FormFocusController(
    private val focusManager: FocusManager,
    private val keyboard: SoftwareKeyboardController?
) {
    // Coordinates are queried only on taps, not published as composition state on scroll.
    private var container: LayoutCoordinates? = null
    private val fields = mutableMapOf<String, LayoutCoordinates>()
    private val focusedFields = mutableSetOf<String>()

    fun clear() {
        focusManager.clearFocus()
        keyboard?.hide()
    }

    fun field(key: String, onFocused: (Boolean) -> Unit): Modifier = Modifier
        .onGloballyPositioned { fields[key] = it }
        .onFocusChanged {
            if (it.isFocused) focusedFields.add(key) else focusedFields.remove(key)
            onFocused(it.isFocused)
        }

    private fun insideField(position: Offset): Boolean {
        val root = container?.takeIf { it.isAttached } ?: return false
        val point = root.localToWindow(position)
        return fields.values.any { it.isAttached && it.boundsInWindow().contains(point) }
    }

    fun container(): Modifier = Modifier
        .onGloballyPositioned { container = it }
        .pointerInput(this) {
            awaitEachGesture {
                // Initial pass observes buttons too; never consume their pointer events.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (focusedFields.isEmpty() || insideField(down.position)) return@awaitEachGesture
                var tap = true
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.changes.size > 1 ||
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop) tap = false
                    if (!change.pressed) {
                        if (tap && !insideField(change.position)) clear()
                        break
                    }
                }
            }
        }
}
