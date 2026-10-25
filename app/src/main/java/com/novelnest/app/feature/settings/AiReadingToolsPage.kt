package com.novelnest.app.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelError
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSecondary
import com.novelnest.app.core.designsystem.NovelSurface
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.designsystem.NovelTextTertiary
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.NovelNestHapticType
import com.novelnest.app.core.ui.animation.bounceClick
import com.novelnest.app.core.ui.animation.rememberNovelNestHaptic
import com.novelnest.app.core.ui.footer.rememberFooterBottomPadding
import com.novelnest.app.core.ui.header.NovelNestHeader
import com.novelnest.app.core.ui.rememberFormFocusController
import com.novelnest.app.data.ai.AiAction
import com.novelnest.app.data.ai.AiCostPolicy
import com.novelnest.app.data.ai.AiModelSpec
import com.novelnest.app.data.ai.AiProviderId
import com.novelnest.app.data.ai.AiProviderStatus
import com.novelnest.app.data.ai.AiRoute
import com.novelnest.app.data.ai.AiSettings
import com.novelnest.app.feature.settings.components.SettingsSectionHeader

@Composable
fun AiReadingToolsPage(
    viewModel: AiReadingToolsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formFocus = rememberFormFocusController()
    val playHaptic = rememberNovelNestHaptic()
    val snackbarHostState = remember { SnackbarHostState() }

    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showBillingConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        if (state.message.isNotBlank()) {
            snackbarHostState.showSnackbar(state.message)
            viewModel.dismissMessage()
        }
    }

    LaunchedEffect(state.failure) {
        state.failure?.let {
            snackbarHostState.showSnackbar(it.message)
            viewModel.dismissMessage()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(formFocus.container())
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            NovelNestHeader(
                title = "AI Reading Tools",
                subtitle = "Summaries, rewrites & provider configuration",
                navigationIcon = {
                    NovelNestIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to Settings",
                        onClick = {
                            formFocus.clear()
                            onBack()
                        }
                    )
                }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = rememberFooterBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Privacy & Chapter Sharing Notice
                item {
                    ChapterSharingNoticeCard()
                }

                // Section 2: Cost Policy & Billing Disclaimer
                item {
                    SettingsSectionHeader(
                        title = "Cost & Billing Policy",
                        icon = Icons.Rounded.Security
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    CostPolicyCard(
                        currentPolicy = state.settings.costPolicy,
                        enabled = !state.busy,
                        onPolicyChanged = { newPolicy ->
                            if (!state.busy && newPolicy != state.settings.costPolicy) {
                                formFocus.clear()
                                playHaptic(NovelNestHapticType.LIGHT_TICK)
                                if (newPolicy == AiCostPolicy.FREE_ACCOUNT_PROVIDERS) showBillingConfirm = true
                                else viewModel.saveSettings(state.settings.copy(costPolicy = newPolicy))
                            }
                        }
                    )
                }

                // Section 3: Summary Routes
                item {
                    SettingsSectionHeader(
                        title = "Summary Route Ordering",
                        icon = Icons.Rounded.Route
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    RouteOrderingCard(
                        action = AiAction.SUMMARY,
                        routes = state.settings.summaryRoutes,
                        availableSpecs = viewModel.supportedModels,
                        enabled = !state.busy,
                        onRoutesChanged = { updated ->
                            viewModel.saveSettings(state.settings.copy(summaryRoutes = updated))
                        }
                    )
                }

                // Section 4: Rewrite Routes
                item {
                    SettingsSectionHeader(
                        title = "Rewrite Route Ordering",
                        icon = Icons.Rounded.Route
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    RouteOrderingCard(
                        action = AiAction.REWRITE,
                        routes = state.settings.rewriteRoutes,
                        availableSpecs = viewModel.supportedModels,
                        enabled = !state.busy,
                        onRoutesChanged = { updated ->
                            viewModel.saveSettings(state.settings.copy(rewriteRoutes = updated))
                        }
                    )
                }

                // Section 5: Extract Terms Routes
                item {
                    SettingsSectionHeader(
                        title = "Extract Terms Route Ordering",
                        icon = Icons.Rounded.Route
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    RouteOrderingCard(
                        action = AiAction.EXTRACT,
                        routes = state.settings.extractRoutes,
                        availableSpecs = viewModel.supportedModels,
                        enabled = !state.busy,
                        onRoutesChanged = { updated ->
                            viewModel.saveSettings(state.settings.copy(extractRoutes = updated))
                        }
                    )
                }

                // Section 6: Providers & Credentials
                item {
                    SettingsSectionHeader(
                        title = "Provider Credentials & Availability",
                        icon = Icons.Rounded.Key
                    )
                }

                items(AiProviderId.entries.toList(), key = { it.name }) { providerId ->
                    val status = state.providers.firstOrNull { it.provider == providerId }
                        ?: AiProviderStatus(providerId, configured = false)
                    val isConsented = providerId in state.settings.consentedProviders

                    ProviderCard(
                        providerId = providerId,
                        status = status,
                        isConsented = isConsented,
                        supportedModels = viewModel.supportedModels.filter { it.route.provider == providerId },
                        availableModels = state.availableModels.filter { it.route.provider == providerId },
                        isBusy = state.busy,
                        savedKeyRevision = state.savedKeyRevisions[providerId] ?: 0L,
                        fieldModifier = formFocus.field("ai-key:${providerId.name}") {},
                        onConsentChanged = { consented ->
                            playHaptic(NovelNestHapticType.LIGHT_TICK)
                            val updatedConsent = if (consented) {
                                state.settings.consentedProviders + providerId
                            } else {
                                state.settings.consentedProviders - providerId
                            }
                            viewModel.saveSettings(state.settings.copy(consentedProviders = updatedConsent))
                        },
                        onSaveKey = { newKey ->
                            formFocus.clear()
                            viewModel.replaceKey(providerId, newKey)
                        },
                        onRemoveKey = {
                            viewModel.removeKey(providerId)
                        },
                        onCheckAvailability = {
                            viewModel.checkProvider(providerId)
                        }
                    )
                }

                // Section 6: Cache Management
                item {
                    SettingsSectionHeader(
                        title = "AI Data & Storage",
                        icon = Icons.Rounded.DeleteSweep
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ClearAiResultsCard(
                        enabled = !state.busy,
                        onClearRequested = { showClearCacheConfirm = true }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = rememberFooterBottomPadding() + 12.dp)
                .padding(horizontal = 16.dp)
        ) { snackbarData ->
            val isError = state.failure != null
            val accentColor = if (isError) NovelError else NovelSecondary

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = NovelSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(accentColor.copy(alpha = 0.4f))
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = snackbarData.visuals.message,
                        color = NovelTextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    snackbarData.visuals.actionLabel?.let { action ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = action,
                            color = NovelPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { snackbarData.performAction() }
                        )
                    }
                }
            }
        }

        if (showClearCacheConfirm) {
            AlertDialog(
                onDismissRequest = { showClearCacheConfirm = false },
                containerColor = NovelSurface,
                shape = RoundedCornerShape(16.dp),
                title = {
                    Text(
                        text = "Clear AI Result Cache?",
                        color = NovelTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "This will remove all cached chapter summaries and rewrites from storage. Your API keys, reading bookmarks, and chapter history are safely kept.",
                        color = NovelTextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        enabled = !state.busy,
                        onClick = {
                            showClearCacheConfirm = false
                            viewModel.clearResults()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NovelError,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Clear Cache",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCacheConfirm = false }) {
                        Text("Cancel", color = NovelTextSecondary)
                    }
                }
            )
        }

        if (showBillingConfirm) {
            AlertDialog(
                onDismissRequest = { showBillingConfirm = false },
                containerColor = NovelSurface,
                title = { Text("Allow free-account providers?", color = NovelTextPrimary) },
                text = {
                    Text(
                        "Gemini and Groq may charge for requests made with a billed account's API key. " +
                            "NovelNest cannot verify your billing tier or enforce a spending cutoff. " +
                            "Use a provider-enforced free account/project. NVIDIA's free hosted access is rate-limited and for prototype/testing use only. Keep Explicit Free Only if you do not accept these conditions.",
                        color = NovelTextSecondary
                    )
                },
                confirmButton = {
                    TextButton(enabled = !state.busy, onClick = {
                        showBillingConfirm = false
                        viewModel.saveSettings(state.settings.copy(costPolicy = AiCostPolicy.FREE_ACCOUNT_PROVIDERS))
                    }) { Text("I understand — enable") }
                },
                dismissButton = {
                    TextButton(onClick = { showBillingConfirm = false }) { Text("Keep current policy") }
                }
            )
        }
    }
}

@Composable
private fun ChapterSharingNoticeCard() {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NovelSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(NovelPrimary.copy(alpha = 0.35f))
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NovelPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PrivacyTip,
                    contentDescription = null,
                    tint = NovelPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Chapter Sharing & Fallback Disclosure",
                    color = NovelTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "When requesting a summary or rewrite, chapter text is sent to your primary configured route. If that provider is rate-limited or unavailable, enabled fallback providers will also receive chapter text to complete your request.\n\nOnly configure providers you trust with chapter content. Sharing requires explicit provider consent below.",
                    color = NovelTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun CostPolicyCard(
    currentPolicy: AiCostPolicy,
    enabled: Boolean,
    onPolicyChanged: (AiCostPolicy) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NovelSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(NovelSurfaceBorder)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Billing & Allowance Acknowledgement",
                color = NovelTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = "NovelNest does not guarantee zero billing, unlimited quota, or uninterrupted model uptime. External provider usage limits, account tiers, and terms of service apply.",
                color = NovelTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            HorizontalDivider(color = NovelSurfaceBorder.copy(alpha = 0.6f))

            // Policy Option 1: EXPLICIT_FREE_ONLY
            CostPolicyOptionRow(
                title = "Explicit Free Only (Default)",
                description = "Only allows models explicitly verified as free (e.g. OpenRouter Gemma Free). Blocks routes that require paid or developer-account billing.",
                selected = currentPolicy == AiCostPolicy.EXPLICIT_FREE_ONLY,
                enabled = enabled,
                onClick = { onPolicyChanged(AiCostPolicy.EXPLICIT_FREE_ONLY) }
            )

            // Policy Option 2: FREE_ACCOUNT_PROVIDERS
            CostPolicyOptionRow(
                title = "Free Account Providers",
                description = "Permits free-account Gemini/Groq routes and NVIDIA prototype/testing routes. Provider limits and account conditions apply; an API key is required.",
                selected = currentPolicy == AiCostPolicy.FREE_ACCOUNT_PROVIDERS,
                enabled = enabled,
                onClick = { onPolicyChanged(AiCostPolicy.FREE_ACCOUNT_PROVIDERS) }
            )
        }
    }
}

@Composable
private fun CostPolicyOptionRow(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) NovelPrimary.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (selected) NovelPrimary else NovelTextSecondary.copy(alpha = 0.5f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(NovelPrimary)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (selected) NovelPrimary else NovelTextPrimary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = NovelTextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun RouteOrderingCard(
    action: AiAction,
    routes: List<AiRoute>,
    availableSpecs: List<AiModelSpec>,
    enabled: Boolean,
    onRoutesChanged: (List<AiRoute>) -> Unit
) {
    var showAddDropdown by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NovelSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(NovelSurfaceBorder)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = when (action) {
                            AiAction.SUMMARY -> "Summary Routes"
                            AiAction.REWRITE -> "Rewrite Routes"
                            AiAction.EXTRACT -> "Extract Terms Routes"
                        },
                        color = NovelTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${routes.size}/3 routes configured (Primary & Fallbacks)",
                        color = NovelTextSecondary,
                        fontSize = 12.sp
                    )
                }

                Box {
                    val canAdd = enabled && routes.size < 3
                    val buttonBg = if (canAdd) NovelPrimary else NovelSurfaceVariant
                    val buttonContentColor = if (canAdd) NovelOnPrimary else NovelTextSecondary.copy(alpha = 0.5f)

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(buttonBg)
                            .clickable(enabled = canAdd) { showAddDropdown = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add route",
                            tint = buttonContentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Add Route",
                            color = buttonContentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    DropdownMenu(
                        expanded = showAddDropdown,
                        onDismissRequest = { showAddDropdown = false },
                        modifier = Modifier.background(NovelSurfaceVariant)
                    ) {
                        val unselectedSpecs = availableSpecs.filter { spec ->
                            routes.none { it == spec.route }
                        }
                        if (unselectedSpecs.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No additional models available", color = NovelTextSecondary, fontSize = 12.sp) },
                                onClick = { showAddDropdown = false }
                            )
                        } else {
                            unselectedSpecs.forEach { spec ->
                                DropdownMenuItem(
                                    enabled = enabled,
                                    text = {
                                        Column {
                                            Text(
                                                text = spec.label,
                                                color = NovelTextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "${spec.route.provider.name} • ${if (spec.explicitlyFree) "Explicit Free" else "Developer Tier"}",
                                                color = NovelTextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        showAddDropdown = false
                                        onRoutesChanged(routes + spec.route)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (routes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(NovelSurfaceVariant.copy(alpha = 0.5f))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No routes selected for this action. Tap 'Add Route' above to select a primary provider.",
                        color = NovelTextSecondary,
                        fontSize = 12.sp
                    )
                }
            } else {
                routes.forEachIndexed { index, route ->
                    val spec = availableSpecs.firstOrNull { it.route == route }
                    val label = spec?.label ?: route.model
                    val roleLabel = when (index) {
                        0 -> "1. Primary"
                        1 -> "2. Fallback 1"
                        else -> "3. Fallback 2"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(NovelSurfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = roleLabel,
                                    color = if (index == 0) NovelPrimary else NovelSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = route.provider.name,
                                    color = NovelTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = label,
                                color = NovelTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Move Up Button
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val mutable = routes.toMutableList()
                                    val item = mutable.removeAt(index)
                                    mutable.add(index - 1, item)
                                    onRoutesChanged(mutable)
                                }
                            },
                            enabled = enabled && index > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = "Move route up",
                                tint = if (index > 0) NovelTextPrimary else NovelTextSecondary.copy(alpha = 0.3f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Move Down Button
                        IconButton(
                            onClick = {
                                if (index < routes.size - 1) {
                                    val mutable = routes.toMutableList()
                                    val item = mutable.removeAt(index)
                                    mutable.add(index + 1, item)
                                    onRoutesChanged(mutable)
                                }
                            },
                            enabled = enabled && index < routes.size - 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowDownward,
                                contentDescription = "Move route down",
                                tint = if (index < routes.size - 1) NovelTextPrimary else NovelTextSecondary.copy(alpha = 0.3f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Remove Button
                        IconButton(
                            enabled = enabled,
                            onClick = {
                                onRoutesChanged(routes.filterIndexed { i, _ -> i != index })
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Remove route",
                                tint = NovelError.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    providerId: AiProviderId,
    status: AiProviderStatus,
    isConsented: Boolean,
    supportedModels: List<AiModelSpec>,
    availableModels: List<com.novelnest.app.data.ai.AiModelAvailability>,
    isBusy: Boolean,
    savedKeyRevision: Long,
    fieldModifier: Modifier,
    onConsentChanged: (Boolean) -> Unit,
    onSaveKey: (String) -> Unit,
    onRemoveKey: () -> Unit,
    onCheckAvailability: () -> Unit
) {
    // Ephemeral key input; never saved in rememberSaveable or SavedStateHandle
    var keyInput by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }

    // Acknowledgement is provider-specific and emitted only after a successful save.
    LaunchedEffect(savedKeyRevision) {
        if (savedKeyRevision > 0) { keyInput = ""; keyVisible = false }
    }

    val providerDisplayName = when (providerId) {
        AiProviderId.OPEN_ROUTER -> "OpenRouter"
        AiProviderId.GEMINI -> "Google Gemini"
        AiProviderId.GROQ -> "Groq Cloud"
        AiProviderId.NVIDIA -> "NVIDIA (prototype/testing)"
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NovelSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (status.configured) NovelPrimary.copy(alpha = 0.3f) else NovelSurfaceBorder
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Provider Name + Configured / Unreadable Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(NovelPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = null,
                            tint = NovelPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = providerDisplayName,
                            color = NovelTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (status.unreadable) "Storage Decryption Error"
                            else if (status.configured) "Key Configured in Keystore"
                            else "No API Key Configured",
                            color = if (status.unreadable) NovelError
                            else if (status.configured) NovelSecondary
                            else NovelTextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }

                // Status Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (status.configured) NovelSecondary.copy(alpha = 0.15f)
                            else NovelSurfaceVariant
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (status.configured) "CONFIGURED" else "INACTIVE",
                        color = if (status.configured) NovelSecondary else NovelTextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Chapter Sharing Consent Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(NovelSurfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Allow Chapter Sharing",
                        color = NovelTextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (providerId == AiProviderId.NVIDIA)
                            "Allow chapter sharing for prototype/testing summaries, rewrites and terms. Free hosted access is rate-limited; this is not a production entitlement."
                        else "Allows sending chapter text to $providerDisplayName for summaries, rewrites and terms.",
                        color = NovelTextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    enabled = !isBusy,
                    checked = isConsented,
                    onCheckedChange = onConsentChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NovelOnPrimary,
                        checkedTrackColor = NovelPrimary,
                        uncheckedThumbColor = NovelTextSecondary,
                        uncheckedTrackColor = NovelSurfaceVariant
                    )
                )
            }

            // API Key Masked Replacement Field
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (status.configured) "Replace API Key" else "Enter API Key",
                        color = NovelTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 8.dp)
                    )

                    val apiKeyUrl = when (providerId) {
                        AiProviderId.OPEN_ROUTER -> "https://openrouter.ai/keys"
                        AiProviderId.GEMINI -> "https://aistudio.google.com/app/apikey"
                        AiProviderId.GROQ -> "https://console.groq.com/keys"
                        AiProviderId.NVIDIA -> "https://build.nvidia.com/"
                    }
                    val uriHandler = LocalUriHandler.current

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NovelPrimary.copy(alpha = 0.12f))
                            .clickable {
                                try {
                                    uriHandler.openUri(apiKeyUrl)
                                } catch (_: Exception) {}
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Get API Key",
                            color = NovelPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = "Get API Key for $providerDisplayName in browser",
                            tint = NovelPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                OutlinedTextField(
                    enabled = !isBusy,
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    textStyle = TextStyle(
                        color = NovelTextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    placeholder = {
                        Text(
                            text = if (status.configured) "•••••••••••• (Leave blank to keep current key)" else "Paste API key here",
                            fontSize = 12.sp,
                            color = NovelTextTertiary
                        )
                    },
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                imageVector = if (keyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (keyVisible) "Hide key" else "Show key",
                                tint = NovelTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (!isBusy && keyInput.isNotBlank()) {
                                onSaveKey(keyInput.trim())
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = NovelTextPrimary,
                        unfocusedTextColor = NovelTextPrimary,
                        focusedBorderColor = NovelPrimary,
                        unfocusedBorderColor = NovelSurfaceBorder,
                        focusedPlaceholderColor = NovelTextTertiary,
                        unfocusedPlaceholderColor = NovelTextTertiary,
                        cursorColor = NovelPrimary,
                        focusedContainerColor = NovelSurfaceVariant,
                        unfocusedContainerColor = NovelSurfaceVariant
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().then(fieldModifier)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (status.configured || status.unreadable) {
                        TextButton(
                            enabled = !isBusy,
                            onClick = { showRemoveDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = NovelError)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Remove Key", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    val canSave = keyInput.isNotBlank() && !isBusy
                    val saveBg = if (canSave) NovelPrimary else NovelSurfaceVariant
                    val saveContentColor = if (canSave) NovelOnPrimary else NovelTextSecondary.copy(alpha = 0.5f)

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(saveBg)
                            .clickable(enabled = canSave) {
                                if (canSave) {
                                    onSaveKey(keyInput.trim())
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Save key",
                            tint = saveContentColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Save Key",
                            color = saveContentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider(color = NovelSurfaceBorder.copy(alpha = 0.5f))

            // Model Availability & Validation Status
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp)
                    ) {
                        Text(
                            text = "Supported Models",
                            color = NovelTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Availability check only; generation has not been verified.",
                            color = NovelTextSecondary,
                            fontSize = 10.5.sp
                        )
                    }

                    OutlinedButton(
                        onClick = onCheckAvailability,
                        enabled = status.configured && !isBusy,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NovelPrimary,
                            disabledContentColor = NovelTextSecondary.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NetworkCheck,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Check Availability", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                supportedModels.forEach { spec ->
                    val availability = availableModels.firstOrNull { it.route == spec.route }
                    val isAvailable = availability?.available

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(NovelSurfaceVariant.copy(alpha = 0.4f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = spec.label,
                                color = NovelTextPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Max input: ${spec.inputBytes / 1000}KB • ${if (spec.explicitlyFree) "Explicit Free" else "Developer Quota"}",
                                color = NovelTextSecondary,
                                fontSize = 10.5.sp
                            )
                        }

                        // Availability Tag
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (isAvailable) {
                                true -> {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Available",
                                        tint = NovelSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Available",
                                        color = NovelSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                false -> {
                                    Icon(
                                        imageVector = Icons.Rounded.Warning,
                                        contentDescription = "Unavailable",
                                        tint = NovelError,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Unavailable",
                                        color = NovelError,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                null -> {
                                    Text(
                                        text = if (status.configured) "Not checked" else "Unconfigured",
                                        color = NovelTextSecondary.copy(alpha = 0.6f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            containerColor = NovelSurface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Remove $providerDisplayName Key?",
                    color = NovelTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will permanently remove the stored API key from Android Keystore. Routes targeting this provider will fail until a new key is added.",
                    color = NovelTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    enabled = !isBusy,
                    onClick = {
                        showRemoveDialog = false
                        onRemoveKey()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NovelError,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "Remove", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text("Cancel", color = NovelTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ClearAiResultsCard(
    enabled: Boolean,
    onClearRequested: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NovelSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(NovelSurfaceBorder)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Clear AI Results Cache",
                    color = NovelTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Clears all cached chapter summaries and rewrites from storage. Saved API keys, bookmarks, and novel files remain untouched.",
                    color = NovelTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedButton(
                enabled = enabled,
                onClick = onClearRequested,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NovelError),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = "Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
