package com.novelnest.app.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelnest.app.NovelNestApplication
import com.novelnest.app.core.designsystem.*
import com.novelnest.app.core.ui.NovelNestButton
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.footer.rememberFooterBottomPadding
import com.novelnest.app.core.ui.header.NovelNestHeader
import com.novelnest.app.data.security.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AppLockPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val manager = (context.applicationContext as NovelNestApplication).appLock
    val state by manager.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var full by remember { mutableStateOf("") }
    var fullConfirm by remember { mutableStateOf("") }
    var epub by remember { mutableStateOf("") }
    var epubConfirm by remember { mutableStateOf("") }
    var biometric by remember(state.biometricEnabled) { mutableStateOf(state.biometricEnabled) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isErrorMessage by remember { mutableStateOf(false) }

    BackHandler { if (!busy) onBack() }

    fun clearPins() {
        current = ""; full = ""; fullConfirm = ""; epub = ""; epubConfirm = ""
    }

    fun perform(disable: Boolean) {
        if (busy) return
        val old = current; val normal = full; val restricted = epub
        if (!disable && (normal != fullConfirm || restricted != epubConfirm)) {
            isErrorMessage = true
            message = "Confirmations must match their PINs."
            return
        }
        if (!disable && biometric && !AppBiometrics.available(context)) {
            isErrorMessage = true
            message = "Enroll a supported fingerprint or face in Android Settings first."
            return
        }
        focus.clearFocus()
        busy = true
        message = null
        isErrorMessage = false
        scope.launch {
            try {
                if (disable) manager.disable(old) else manager.configure(old, normal, restricted, biometric)
                clearPins()
                isErrorMessage = false
                message = if (disable) "App lock disabled." else "App lock saved."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                isErrorMessage = true
                message = error.message ?: "Could not save app lock."
            } finally {
                busy = false
            }
        }
    }

    val bottomPadding = rememberFooterBottomPadding() + 16.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NovelBackground)
    ) {
        NovelNestHeader(
            title = "Lock & Biometrics",
            subtitle = "PIN protection & private EPUB mode",
            navigationIcon = {
                NovelNestIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to Settings",
                    enabled = !busy,
                    onClick = onBack
                )
            }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = bottomPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Status & Mode Overview Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = NovelSurface,
                    border = BorderStroke(1.dp, NovelSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(
                                        if (state.enabled) NovelPrimary.copy(alpha = 0.14f)
                                        else NovelSurfaceVariant
                                    )
                                    .border(
                                        1.dp,
                                        if (state.enabled) NovelPrimary.copy(alpha = 0.4f)
                                        else NovelSurfaceBorder,
                                        RoundedCornerShape(11.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (state.enabled) Icons.Rounded.Shield else Icons.Rounded.LockOpen,
                                    contentDescription = null,
                                    tint = if (state.enabled) NovelPrimary else NovelTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (state.enabled) "App Lock Active" else "App Lock Off",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = NovelTextPrimary,
                                    modifier = Modifier.semantics { heading() }
                                )
                                Text(
                                    text = if (state.enabled) {
                                        "App previews and screenshots are hidden."
                                    } else {
                                        "Protect Novel Nest with dual-mode 4-digit PINs."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NovelTextSecondary
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (state.enabled) NovelPrimary.copy(alpha = 0.16f) else NovelSurfaceVariant,
                                border = BorderStroke(
                                    0.75.dp,
                                    if (state.enabled) NovelPrimary.copy(alpha = 0.45f) else NovelSurfaceBorder
                                )
                            ) {
                                Text(
                                    text = if (state.enabled) "ENABLED" else "OFF",
                                    color = if (state.enabled) NovelPrimary else NovelTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = NovelSurfaceBorder.copy(alpha = 0.6f))

                        Text(
                            text = "Normal PIN opens Home with full access. EPUB PIN opens a private EPUB-only library.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NovelTextSecondary
                        )
                        Text(
                            text = "Use different four-digit PINs. Keep your normal PIN safe: there is no PIN recovery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NovelTertiary
                        )
                    }
                }

                // 2. PIN Configuration Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = NovelSurface,
                    border = BorderStroke(1.dp, NovelSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = NovelPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (state.enabled) "Change both PINs (leave blank to keep them)" else "Set your PINs",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NovelTextPrimary,
                                modifier = Modifier.semantics { heading() }
                            )
                        }

                        if (state.enabled) {
                            LockPinField(
                                value = current,
                                onChange = { current = it; message = null },
                                title = "Current normal PIN",
                                enabled = !busy,
                                maxLength = state.pinLength,
                                imeAction = ImeAction.Next,
                                onImeAction = { focus.moveFocus(FocusDirection.Down) }
                            )
                            HorizontalDivider(color = NovelSurfaceBorder.copy(alpha = 0.5f))
                        }

                        LockPinField(
                            value = full,
                            onChange = { full = it; message = null },
                            title = "Normal PIN",
                            enabled = !busy,
                            imeAction = ImeAction.Next,
                            onImeAction = { focus.moveFocus(FocusDirection.Down) }
                        )
                        LockPinField(
                            value = fullConfirm,
                            onChange = { fullConfirm = it; message = null },
                            title = "Confirm normal PIN",
                            enabled = !busy,
                            imeAction = ImeAction.Next,
                            onImeAction = { focus.moveFocus(FocusDirection.Down) }
                        )

                        Spacer(Modifier.height(2.dp))

                        LockPinField(
                            value = epub,
                            onChange = { epub = it; message = null },
                            title = "EPUB-only PIN",
                            enabled = !busy,
                            imeAction = ImeAction.Next,
                            onImeAction = { focus.moveFocus(FocusDirection.Down) }
                        )
                        LockPinField(
                            value = epubConfirm,
                            onChange = { epubConfirm = it; message = null },
                            title = "Confirm EPUB-only PIN",
                            enabled = !busy,
                            imeAction = ImeAction.Done,
                            onImeAction = { focus.clearFocus() }
                        )
                    }
                }

                // 3. Biometric & Lock Behavior Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = NovelSurface,
                    border = BorderStroke(1.dp, NovelSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(NovelPrimary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Fingerprint,
                                        contentDescription = null,
                                        tint = NovelPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Biometric unlock (normal access)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NovelTextPrimary
                                    )
                                    Text(
                                        text = "Fingerprint or face unlock for full access",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NovelTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Switch(
                                checked = biometric,
                                onCheckedChange = { biometric = it },
                                enabled = !busy,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NovelOnPrimary,
                                    checkedTrackColor = NovelPrimary,
                                    uncheckedThumbColor = NovelTextSecondary,
                                    uncheckedTrackColor = NovelSurfaceVariant
                                ),
                                modifier = Modifier.semantics {
                                    contentDescription = "Biometric unlock for normal access"
                                }
                            )
                        }

                        HorizontalDivider(color = NovelSurfaceBorder.copy(alpha = 0.5f))

                        Text(
                            text = "Switching apps keeps your session unlocked. Screen off, device lock, closing the app task, or a process restart require unlocking again.",
                            color = NovelTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // 4. Status Feedback Banner (Error or Success)
                AnimatedVisibility(
                    visible = message != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val accent = if (isErrorMessage) NovelError else NovelSecondary
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accent.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isErrorMessage) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircleOutline,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = message.orEmpty(),
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

                // 5. Primary & Secondary Actions (High-contrast NovelOnPrimary on NovelPrimary)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NovelNestButton(
                        onClick = { perform(false) },
                        enabled = !busy && state.access == AppAccess.FULL,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = NovelOnPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Saving...")
                        } else {
                            Text(if (state.enabled) "Save changes" else "Enable app lock")
                        }
                    }

                    if (state.enabled) {
                        OutlinedButton(
                            onClick = { clearPins(); manager.lock() },
                            enabled = !busy,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, NovelSurfaceBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = NovelSurface,
                                contentColor = NovelTextPrimary,
                                disabledContentColor = NovelTextSecondary.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = NovelPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Lock now", fontWeight = FontWeight.SemiBold)
                        }

                        TextButton(
                            onClick = { perform(true) },
                            enabled = !busy,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = NovelError,
                                disabledContentColor = NovelError.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text("Disable app lock", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LockPinField(
    value: String,
    onChange: (String) -> Unit,
    title: String,
    enabled: Boolean,
    maxLength: Int = AppPin.LENGTH,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    compact: Boolean = false
) {
    var visible by remember { mutableStateOf(false) }
    val visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()
    val fieldShape = RoundedCornerShape(12.dp)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = NovelPrimary,
        unfocusedBorderColor = NovelSurfaceBorder,
        focusedContainerColor = NovelSurfaceVariant.copy(alpha = 0.45f),
        unfocusedContainerColor = NovelSurfaceVariant.copy(alpha = 0.28f),
        focusedLabelColor = NovelPrimary,
        unfocusedLabelColor = NovelTextSecondary,
        focusedTextColor = NovelTextPrimary,
        unfocusedTextColor = NovelTextPrimary,
        cursorColor = NovelPrimary
    )

    if (!compact) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(AppPin.sanitizeInput(it, maxLength)) },
            label = { Text(title) },
            enabled = enabled,
            singleLine = true,
            visualTransformation = visualTransformation,
            trailingIcon = {
                IconButton(
                    onClick = { visible = !visible },
                    enabled = enabled
                ) {
                    Icon(
                        imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (visible) "Hide $title" else "Show $title",
                        tint = NovelTextSecondary
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onNext = { onImeAction() },
                onDone = { onImeAction() }
            ),
            shape = fieldShape,
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth()
        )
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = { onChange(AppPin.sanitizeInput(it, maxLength)) },
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(
            color = NovelTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        ),
        cursorBrush = SolidColor(NovelPrimary),
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() }
        ),
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
    ) { innerTextField ->
        OutlinedTextFieldDefaults.DecorationBox(
            value = value,
            innerTextField = innerTextField,
            enabled = enabled,
            singleLine = true,
            visualTransformation = visualTransformation,
            interactionSource = interactionSource,
            label = { Text(title, fontSize = 12.5.sp) },
            trailingIcon = {
                IconButton(
                    onClick = { visible = !visible },
                    enabled = enabled,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (visible) "Hide $title" else "Show $title",
                        tint = NovelTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            colors = fieldColors,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            container = {
                OutlinedTextFieldDefaults.Container(
                    enabled = enabled,
                    isError = false,
                    interactionSource = interactionSource,
                    colors = fieldColors,
                    shape = fieldShape
                )
            }
        )
    }
}
