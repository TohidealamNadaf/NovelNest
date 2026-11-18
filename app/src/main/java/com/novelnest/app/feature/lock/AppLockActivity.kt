package com.novelnest.app.feature.lock

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.novelnest.app.MainActivity
import com.novelnest.app.R
import com.novelnest.app.NovelNestApplication
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.core.ui.NovelNestButton
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.NovelNestHapticType
import com.novelnest.app.core.ui.animation.bounceClick
import com.novelnest.app.core.ui.animation.rememberNovelNestHaptic
import com.novelnest.app.data.security.*
import com.novelnest.app.feature.settings.LockPinField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppLockActivity : FragmentActivity() {
    private val manager get() = (application as NovelNestApplication).appLock
    private lateinit var prompt: BiometricPrompt
    private var operation: Job? = null
    private var message by mutableStateOf<String?>(null)
    private var busy by mutableStateOf(false)
    // Forgot PIN drafts live on the Activity so onStop can clear them synchronously.
    private var showForgot by mutableStateOf(false)
    private var recoveryOld by mutableStateOf("")
    private var recoveryFull by mutableStateOf("")
    private var recoveryFullConfirm by mutableStateOf("")
    private var recoveryEpub by mutableStateOf("")
    private var recoveryEpubConfirm by mutableStateOf("")
    internal var replacingTask = false
        private set
    private var completing = false
    private var biometricRevision = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The lock screen is a light amber gradient: transparent bars with dark icons,
        // matching its NovelOnPrimary text, instead of the app's default light-on-dark bars.
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(transparent, transparent),
            navigationBarStyle = SystemBarStyle.light(transparent, transparent)
        )
        applyDarkSystemBarIcons()
        biometricRevision = savedInstanceState?.getLong("biometric_revision", -1L) ?: -1L
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onBackPressedDispatcher.addCallback(this) { moveTaskToBack(true) }
        prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
                    val cipher = result.cryptoObject?.cipher ?: run { message = "Use your PIN to unlock."; return }
                    runOperation {
                        withContext(Dispatchers.IO) { cipher.doFinal(ByteArray(16)) }
                        manager.unlockBiometric(biometricRevision)
                    }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    busy = false
                    if (errorCode != BiometricPrompt.ERROR_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) message = errString.toString()
                }
                override fun onAuthenticationFailed() { message = "Biometric not recognized. Try again or use your PIN." }
            })
        setContent {
            NovelNestTheme(manageSystemBars = false) {
                val state by manager.state.collectAsStateWithLifecycle()
                val playHaptic = rememberNovelNestHaptic()
                var pin by remember { mutableStateOf("") }
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                val scrollState = rememberScrollState()

                LaunchedEffect(state.revision) {
                    pin = ""
                    closeForgot()
                }
                LaunchedEffect(state.retryAtMillis) {
                    now = System.currentTimeMillis()
                    while (now < state.retryAtMillis) {
                        kotlinx.coroutines.delay(1000)
                        now = System.currentTimeMillis()
                    }
                }
                LaunchedEffect(state.ready, state.enabled, state.access, state.revision, state.error) {
                    if (state.ready && !state.enabled && state.error == null) finish()
                    else if (!state.blocked && state.access != null) lifecycle.withResumed {
                        val latest = manager.state.value
                        if (!latest.blocked && latest.revision == state.revision && latest.access == state.access) {
                            complete(AppUnlock(state.access!!, state.resetRootAfterUnlock))
                        }
                    }
                }

                val pinLength = state.pinLength.coerceAtLeast(1)
                val waiting = state.retryAtMillis > now
                val inputEnabled = !busy && !waiting

                fun submitPin(entered: String) {
                    if (busy || waiting || !AppPin.valid(entered, pinLength)) return
                    // Keep every dot filled while the PIN is verified so the last digit is
                    // visibly acknowledged; input is blocked while busy. Clear on failure or
                    // cancellation (onStop) so a draft never survives a rejected attempt.
                    runOperation {
                        try {
                            manager.unlock(entered)
                        } catch (failure: Throwable) {
                            pin = ""
                            throw failure
                        }
                    }
                }

                fun appendDigit(digit: Char) {
                    if (!inputEnabled || pin.length >= pinLength) return
                    val updated = pin + digit
                    pin = updated
                    message = null
                    if (updated.length == pinLength) {
                        submitPin(updated)
                    }
                }

                fun removeDigit() {
                    if (!inputEnabled || pin.isEmpty()) return
                    pin = pin.dropLast(1)
                    message = null
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    colorResource(R.color.app_lock_background_top),
                                    colorResource(R.color.app_lock_background_center),
                                    colorResource(R.color.app_lock_background_bottom)
                                )
                            )
                        )
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown || !inputEnabled) return@onPreviewKeyEvent false
                            val digitChar = when (event.key) {
                                Key.Zero, Key.NumPad0 -> '0'
                                Key.One, Key.NumPad1 -> '1'
                                Key.Two, Key.NumPad2 -> '2'
                                Key.Three, Key.NumPad3 -> '3'
                                Key.Four, Key.NumPad4 -> '4'
                                Key.Five, Key.NumPad5 -> '5'
                                Key.Six, Key.NumPad6 -> '6'
                                Key.Seven, Key.NumPad7 -> '7'
                                Key.Eight, Key.NumPad8 -> '8'
                                Key.Nine, Key.NumPad9 -> '9'
                                else -> null
                            }
                            when {
                                digitChar != null -> { appendDigit(digitChar); true }
                                event.key == Key.Backspace -> { removeDigit(); true }
                                else -> false
                            }
                        }
                        .safeDrawingPadding()
                        // Only accept drag-to-scroll when the content overflows. Otherwise a key
                        // tap whose finger drifts past touch slop vertically was claimed by the
                        // (empty) scroll/overscroll gesture and the digit was silently dropped.
                        .verticalScroll(
                            state = scrollState,
                            enabled = scrollState.canScrollForward || scrollState.canScrollBackward
                        )
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 340.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Security Emblem
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(NovelOnPrimary.copy(alpha = 0.12f))
                                .border(1.dp, NovelOnPrimary.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = NovelOnPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        Text(
                            text = "Unlock Novel Nest",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = NovelOnPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() }
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = "Enter your $pinLength-digit PIN",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = NovelOnPrimary.copy(alpha = 0.78f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(22.dp))

                        when {
                            !state.ready -> {
                                Column(
                                    modifier = Modifier.padding(vertical = 28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = NovelOnPrimary,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(30.dp)
                                    )
                                    Text(
                                        text = "Verifying lock state...",
                                        color = NovelOnPrimary.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            state.error != null -> {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = NovelSurface,
                                    border = BorderStroke(1.dp, NovelError.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ErrorOutline,
                                            contentDescription = null,
                                            tint = NovelError,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = state.error!!,
                                            color = NovelTextPrimary,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier
                                                .weight(1f)
                                                .semantics { liveRegion = LiveRegionMode.Polite }
                                        )
                                    }
                                }
                            }

                            else -> {
                                val remainingSeconds = ((state.retryAtMillis - now + 999) / 1000).coerceAtLeast(1)
                                val caption = if (waiting) {
                                    "Try again in $remainingSeconds seconds."
                                } else {
                                    message
                                }
                                val hasError = caption != null

                                // 6-dot / N-dot PIN Indicator Row
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.semantics {
                                        contentDescription = "${pin.length} of $pinLength digits entered"
                                    }
                                ) {
                                    repeat(pinLength) { index ->
                                        val filled = index < pin.length
                                        val dotColor by animateColorAsState(
                                            targetValue = when {
                                                filled -> NovelOnPrimary
                                                hasError -> Color(0xFF7F1D1D).copy(alpha = 0.45f)
                                                else -> Color.Transparent
                                            },
                                            animationSpec = tween(durationMillis = 120),
                                            label = "dial_dot_color"
                                        )
                                        val dotScale by animateFloatAsState(
                                            targetValue = if (filled) 1.15f else 1f,
                                            animationSpec = tween(durationMillis = 120),
                                            label = "dial_dot_scale"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .graphicsLayer {
                                                    scaleX = dotScale
                                                    scaleY = dotScale
                                                }
                                                .clip(CircleShape)
                                                .background(dotColor)
                                                .border(
                                                    width = 1.5.dp,
                                                    color = if (hasError && !filled) Color(0xFF7F1D1D) else NovelOnPrimary,
                                                    shape = CircleShape
                                                )
                                        )
                                    }
                                }

                                // Status / Busy / Error Feedback Slot (fixed min-height prevents layout jump)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 14.dp, bottom = 14.dp)
                                        .defaultMinSize(minHeight = 28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        busy -> {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    color = NovelOnPrimary,
                                                    strokeWidth = 2.dp
                                                )
                                                Text(
                                                    text = "Unlocking...",
                                                    color = NovelOnPrimary,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }

                                        caption != null -> {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = NovelSurface,
                                                border = BorderStroke(1.dp, NovelError.copy(alpha = 0.55f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (waiting) {
                                                            Icons.Rounded.Timer
                                                        } else {
                                                            Icons.Rounded.ErrorOutline
                                                        },
                                                        contentDescription = null,
                                                        tint = NovelError,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Text(
                                                        text = caption,
                                                        color = NovelTextPrimary,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier.semantics {
                                                            liveRegion = LiveRegionMode.Polite
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Numeric Dial Pad with Round Cards
                                Column(
                                    modifier = Modifier.widthIn(max = 276.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val rows = listOf(
                                        listOf('1', '2', '3'),
                                        listOf('4', '5', '6'),
                                        listOf('7', '8', '9')
                                    )
                                    rows.forEach { rowDigits ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            rowDigits.forEach { digit ->
                                                DialPadDigitButton(
                                                    digit = digit,
                                                    enabled = inputEnabled,
                                                    onClick = {
                                                        playHaptic(NovelNestHapticType.LIGHT_TICK)
                                                        appendDigit(digit)
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Bottom Row: Clear (when entered) | 0 | Backspace
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (pin.isNotEmpty()) {
                                            DialPadActionButton(
                                                contentDescription = "Clear PIN",
                                                enabled = inputEnabled,
                                                onClick = {
                                                    playHaptic(NovelNestHapticType.LIGHT_TICK)
                                                    pin = ""
                                                    message = null
                                                }
                                            ) {
                                                Text(
                                                    text = "C",
                                                    color = NovelOnPrimary,
                                                    fontSize = 21.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else {
                                            Spacer(Modifier.size(68.dp))
                                        }

                                        DialPadDigitButton(
                                            digit = '0',
                                            enabled = inputEnabled,
                                            onClick = {
                                                playHaptic(NovelNestHapticType.LIGHT_TICK)
                                                appendDigit('0')
                                            }
                                        )

                                        DialPadActionButton(
                                            contentDescription = "Delete digit",
                                            enabled = inputEnabled && pin.isNotEmpty(),
                                            onClick = {
                                                playHaptic(NovelNestHapticType.LIGHT_TICK)
                                                removeDigit()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                                contentDescription = null,
                                                tint = if (pin.isNotEmpty()) {
                                                    NovelOnPrimary
                                                } else {
                                                    NovelOnPrimary.copy(alpha = 0.4f)
                                                },
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(22.dp))

                                // Text-Only Link Actions Below Dial Pad (No Card / No Border)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (state.biometricEnabled) {
                                        Arrangement.SpaceEvenly
                                    } else {
                                        Arrangement.Center
                                    },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Forgot PIN?",
                                        color = NovelOnPrimary,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textDecoration = TextDecoration.Underline,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(
                                                enabled = !busy,
                                                role = Role.Button,
                                                onClick = { message = null; showForgot = true }
                                            )
                                            .padding(horizontal = 10.dp, vertical = 12.dp)
                                    )

                                    if (state.biometricEnabled) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable(
                                                    enabled = inputEnabled,
                                                    role = Role.Button,
                                                    onClick = ::authenticateBiometric
                                                )
                                                .padding(horizontal = 10.dp, vertical = 12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Fingerprint,
                                                contentDescription = null,
                                                tint = NovelOnPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Use biometric",
                                                color = NovelOnPrimary,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                textDecoration = TextDecoration.Underline
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showForgot && state.ready && state.error == null) {
                        if (state.recoveryAvailable) {
                            ForgotPinDialog(
                                previousLength = state.recoveryPinLength,
                                waitingSeconds = if (waiting) ((state.retryAtMillis - now + 999) / 1000).coerceAtLeast(1) else 0,
                                busy = busy,
                                error = message,
                                onReset = ::submitRecovery,
                                onDismiss = ::closeForgot
                            )
                        } else {
                            ForgotPinUnavailableDialog(onDismiss = ::closeForgot)
                        }
                    }
                }
            }
        }
    }

    private fun runOperation(block: suspend () -> Unit) {
        if (operation?.isActive == true) return
        busy = true; message = null
        operation = lifecycleScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message = error.message ?: "Could not unlock. Please try your PIN." }
            finally { busy = false }
        }
    }

    /**
     * Android draws system-bar icons only as light or dark; dark matches the dial cards' dark
     * NovelSurface. Applied from the Activity lifecycle on the main thread (never from
     * composition), and again on resume so returning from BiometricPrompt keeps them dark.
     */
    private fun applyDarkSystemBarIcons() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    override fun onResume() {
        super.onResume()
        applyDarkSystemBarIcons()
    }

    private fun closeForgot() {
        showForgot = false
        recoveryOld = ""; recoveryFull = ""; recoveryFullConfirm = ""; recoveryEpub = ""; recoveryEpubConfirm = ""
    }

    private fun submitRecovery() {
        if (busy) return
        if (recoveryFull != recoveryFullConfirm || recoveryEpub != recoveryEpubConfirm) {
            message = "Confirmations must match their PINs."
            return
        }
        val old = recoveryOld; val full = recoveryFull; val epub = recoveryEpub
        runOperation {
            manager.recover(old, full, epub)
            closeForgot()
        }
    }

    @Composable
    private fun ForgotPinUnavailableDialog(onDismiss: () -> Unit) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = NovelSurface,
                border = BorderStroke(1.dp, NovelSurfaceBorder),
                shadowElevation = 20.dp,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .widthIn(max = 380.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NovelPrimary.copy(alpha = 0.14f))
                                .border(1.dp, NovelPrimary.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = NovelPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Forgot PIN?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NovelTextPrimary,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = "Local PIN encryption",
                                style = MaterialTheme.typography.bodySmall,
                                color = NovelTextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(NovelSurfaceVariant)
                                .border(1.dp, NovelSurfaceBorder, RoundedCornerShape(9.dp))
                                .clickable(role = Role.Button, onClickLabel = "Close", onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = NovelTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = NovelSurfaceBorder.copy(alpha = 0.7f))

                    Text(
                        text = "PIN reset needs your previous normal PIN, and none is saved yet. A previous PIN is kept only after you change your normal PIN in Lock & Biometrics. Otherwise Novel Nest cannot recover your PIN: clear the app's storage in Android Settings or reinstall the app.",
                        color = NovelTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 19.sp
                    )

                    NovelNestButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                    ) {
                        Text("Got it")
                    }
                }
            }
        }
    }

    @Composable
    private fun ForgotPinDialog(
        previousLength: Int,
        waitingSeconds: Long,
        busy: Boolean,
        error: String?,
        onReset: () -> Unit,
        onDismiss: () -> Unit
    ) {
        val waiting = waitingSeconds > 0
        val fieldsEnabled = !busy && !waiting
        val focus = LocalFocusManager.current
        val next = { focus.moveFocus(FocusDirection.Down); Unit }
        fun edit(update: () -> Unit) { update(); message = null }
        val complete = AppPin.valid(recoveryOld, previousLength) && AppPin.valid(recoveryFull) &&
            AppPin.valid(recoveryFullConfirm) && AppPin.valid(recoveryEpub) && AppPin.valid(recoveryEpubConfirm)

        Dialog(
            onDismissRequest = { if (!busy) onDismiss() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = NovelSurface,
                border = BorderStroke(1.dp, NovelSurfaceBorder),
                shadowElevation = 20.dp,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 20.dp)
                    .widthIn(max = 400.dp)
                    .heightIn(max = 640.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Modal Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NovelPrimary.copy(alpha = 0.14f))
                                .border(1.dp, NovelPrimary.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LockReset,
                                contentDescription = null,
                                tint = NovelPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reset PIN",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NovelTextPrimary,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = "Recover access using your previous PIN",
                                style = MaterialTheme.typography.bodySmall,
                                color = NovelTextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(NovelSurfaceVariant)
                                .border(1.dp, NovelSurfaceBorder, RoundedCornerShape(9.dp))
                                .clickable(
                                    enabled = !busy,
                                    role = Role.Button,
                                    onClickLabel = "Close",
                                    onClick = onDismiss
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = NovelTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = NovelSurfaceBorder.copy(alpha = 0.7f))

                    // Scrollable Form Body
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Enter the normal PIN you used before your last PIN change, then choose new four-digit PINs. Your current PIN is not accepted here.",
                            color = NovelTextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp
                        )
                        LockPinField(
                            value = recoveryOld, onChange = { edit { recoveryOld = it } },
                            title = "Previous normal PIN", enabled = fieldsEnabled,
                            maxLength = previousLength, onImeAction = next, compact = true
                        )
                        HorizontalDivider(color = NovelSurfaceBorder.copy(alpha = 0.45f))
                        LockPinField(
                            value = recoveryFull, onChange = { edit { recoveryFull = it } },
                            title = "New normal PIN", enabled = fieldsEnabled, onImeAction = next, compact = true
                        )
                        LockPinField(
                            value = recoveryFullConfirm, onChange = { edit { recoveryFullConfirm = it } },
                            title = "Confirm new normal PIN", enabled = fieldsEnabled, onImeAction = next, compact = true
                        )
                        LockPinField(
                            value = recoveryEpub, onChange = { edit { recoveryEpub = it } },
                            title = "New EPUB-only PIN", enabled = fieldsEnabled, onImeAction = next, compact = true
                        )
                        LockPinField(
                            value = recoveryEpubConfirm, onChange = { edit { recoveryEpubConfirm = it } },
                            title = "Confirm new EPUB-only PIN", enabled = fieldsEnabled,
                            imeAction = ImeAction.Done,
                            onImeAction = { focus.clearFocus() },
                            compact = true
                        )

                        val caption = if (waiting) "Too many attempts. Try again in $waitingSeconds seconds." else error
                        if (caption != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = NovelError.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, NovelError.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (waiting) Icons.Rounded.Timer else Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = NovelError,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = caption,
                                        color = NovelTextPrimary,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .weight(1f)
                                            .semantics { liveRegion = LiveRegionMode.Polite }
                                    )
                                }
                            }
                        }
                    }

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !busy,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, NovelSurfaceBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = NovelSurfaceVariant.copy(alpha = 0.45f),
                                contentColor = NovelTextPrimary,
                                disabledContentColor = NovelTextSecondary.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 46.dp)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.SemiBold)
                        }

                        NovelNestButton(
                            onClick = { focus.clearFocus(); onReset() },
                            enabled = fieldsEnabled && complete,
                            modifier = Modifier
                                .weight(1.3f)
                                .heightIn(min = 46.dp)
                        ) {
                            if (busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = NovelOnPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Resetting...")
                            } else {
                                Text("Reset & unlock")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun authenticateBiometric() {
        if (!AppBiometrics.available(this)) { message = "Biometrics are unavailable. Use your PIN."; return }
        biometricRevision = manager.state.value.revision
        runOperation {
            val cipher = withContext(Dispatchers.IO) { AppBiometrics.cipher() }
            if (manager.state.value.revision != biometricRevision) return@runOperation
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Novel Nest").setSubtitle("Open with normal access")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Use PIN").build(), BiometricPrompt.CryptoObject(cipher))
        }
    }

    private fun complete(result: AppUnlock) {
        if (completing) return
        completing = true
        val app = application as NovelNestApplication
        val notificationId = app.pendingNotificationId?.takeIf {
            result.access == AppAccess.FULL && it.isNotBlank() && it.length <= 1024
        }
        if (result.access == AppAccess.EPUB_ONLY) app.pendingNotificationId = null
        if (result.resetTask) {
            replacingTask = true
            (application as NovelNestApplication).appLockLifecycle.beginAuthenticatedTaskReplacement(taskId)
            try {
                startActivity(Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .apply {
                        if (notificationId != null) putExtra(
                            com.novelnest.app.data.notification.EXTRA_NOTIFICATION_ID, notificationId)
                    })
            } catch (error: RuntimeException) {
                replacingTask = false
                (application as NovelNestApplication).appLockLifecycle.cancelAuthenticatedTaskReplacement()
                completing = false
                manager.lock()
                throw error
            }
        }
        manager.acknowledgeRootReset(manager.state.value.revision)
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("biometric_revision", biometricRevision)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        operation?.cancel()
        closeForgot()
        if (::prompt.isInitialized && !isChangingConfigurations) prompt.cancelAuthentication()
        super.onStop()
    }
}

@Composable
private fun DialPadDigitButton(
    digit: Char,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (enabled) NovelSurface else NovelSurface.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, NovelSurfaceBorder),
        shadowElevation = if (enabled) 4.dp else 0.dp,
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .bounceClick(
                scaleDown = BounceScale.Small,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "Digit $digit",
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit.toString(),
                color = if (enabled) NovelTextPrimary else NovelTextSecondary,
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DialPadActionButton(
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(NovelOnPrimary.copy(alpha = if (enabled) 0.14f else 0.06f))
            .semantics { this.contentDescription = contentDescription }
            .bounceClick(
                scaleDown = BounceScale.Small,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick
            )
    ) {
        icon()
    }
}
