package com.novelnest.app.feature.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnest.app.core.designsystem.NovelOnPrimary
import com.novelnest.app.core.designsystem.NovelPrimary
import com.novelnest.app.core.designsystem.NovelSurface
import com.novelnest.app.core.designsystem.NovelSurfaceBorder
import com.novelnest.app.core.designsystem.NovelSurfaceVariant
import com.novelnest.app.core.designsystem.NovelTextPrimary
import com.novelnest.app.core.designsystem.NovelTextSecondary
import com.novelnest.app.core.ui.NovelNestIconButton
import com.novelnest.app.core.ui.animation.BounceScale
import com.novelnest.app.core.ui.animation.bounceClick
import com.novelnest.app.core.ui.footer.rememberFooterBottomPadding
import com.novelnest.app.core.ui.header.NovelNestHeader
import com.novelnest.app.data.narration.NarrationStore
import com.novelnest.app.data.narration.NarrationCache
import com.novelnest.app.data.narration.NarrationEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated settings page for Audio Narration (Cloudflare Workers Free + MeloTTS).
 * Allows configuring the HTTPS Worker URL, private secret token, and inspecting service boundaries.
 */
@Composable
fun AudioNarrationPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember { NarrationStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var endpointUrl by remember { mutableStateOf("") }
    var savedEndpoint by remember { mutableStateOf("") }
    var accessToken by remember { mutableStateOf("") }
    var isConfigured by remember { mutableStateOf(false) }
    var settingsLoaded by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var isVerified by remember { mutableStateOf(false) }
    var verificationMessage by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    var cacheFileCount by remember { mutableIntStateOf(0) }
    var cacheBusy by remember { mutableStateOf(false) }
    var cacheMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val credentials = withContext(Dispatchers.IO) { store.credentials() }
        endpointUrl = credentials.first
        savedEndpoint = credentials.first
        isConfigured = credentials.first.isNotBlank() && credentials.second.isNotBlank()
        settingsLoaded = true
        try { cacheFileCount = NarrationCache.inspect(context.applicationContext).files }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { cacheMessage = "Could not inspect the audio cache. Tap Refresh to retry." }
    }

    Column(modifier = modifier.fillMaxSize()) {
        NovelNestHeader(
            title = "Audio Narration",
            subtitle = "Cloudflare MeloTTS & credentials",
            navigationIcon = {
                NovelNestIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack
                )
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = rememberFooterBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Live Connection Status Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NovelSurface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (isConfigured) NovelPrimary.copy(alpha = 0.4f) else NovelSurfaceBorder
                        )
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(NovelPrimary.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Headphones,
                                        contentDescription = null,
                                        tint = NovelPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Narration Service",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = NovelTextPrimary
                                    )
                                    Text(
                                        text = when {
                                            isTesting -> "Checking endpoint…"
                                            isVerified -> "Endpoint verified; audio not tested"
                                            isConfigured -> "Configured; connection not verified"
                                            else -> "Setup required"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NovelTextSecondary
                                    )
                                }
                            }

                            // Status Pill
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isVerified) Color(0x334CAF50) else NovelPrimary.copy(alpha = 0.18f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isVerified) Color(0xFF4CAF50) else NovelPrimary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isVerified) "Verified" else if (isConfigured) "Saved" else "Not Set",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isVerified) Color(0xFF81C784) else NovelPrimary
                                    )
                                }
                            }
                        }

                        if (savedEndpoint.isNotBlank()) {
                            HorizontalDivider(
                                color = NovelSurfaceBorder.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                            Text(
                                text = "Saved Endpoint",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NovelTextSecondary
                            )
                            Text(
                                text = savedEndpoint,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NovelPrimary,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            // 2. Worker Configuration Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NovelSurface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(NovelSurfaceBorder)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column {
                            Text(
                                text = "Cloudflare Worker Setup",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NovelTextPrimary
                            )
                            Text(
                                text = "Enter the deployed Worker URL and the private secret token you provisioned via wrangler secret put.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NovelTextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        // Endpoint URL Input
                        OutlinedTextField(
                            value = endpointUrl,
                            enabled = settingsLoaded && !isSaving && !isTesting,
                            onValueChange = {
                                endpointUrl = it
                                errorMessage = ""
                                isVerified = false
                                verificationMessage = ""
                            },
                            label = { Text("HTTPS Worker URL") },
                            placeholder = { Text("https://novelnest-narration.<subdomain>.workers.dev/v1/narration") },
                            leadingIcon = {
                                Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = NovelPrimary)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Access Token Input
                        OutlinedTextField(
                            value = accessToken,
                            enabled = settingsLoaded && !isSaving && !isTesting,
                            onValueChange = {
                                accessToken = it
                                errorMessage = ""
                                isVerified = false
                                verificationMessage = ""
                            },
                            label = { Text(if (isConfigured && accessToken.isEmpty()) "Access Token (Stored securely)" else "Worker Access Token") },
                            placeholder = { Text(if (isConfigured) "Enter new token to replace" else "32+ char secret token") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Key, contentDescription = null, tint = NovelPrimary)
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (passwordVisible) "Hide token" else "Show token",
                                        tint = NovelTextSecondary
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Error Banner
                        if (errorMessage.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = errorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                enabled = settingsLoaded && !isSaving && !isTesting && endpointUrl.isNotBlank() && (accessToken.isNotBlank() || isConfigured),
                                onClick = {
                                    isSaving = true
                                    errorMessage = ""
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            runCatching {
                                                val tokenToSave = if (accessToken.isNotBlank()) accessToken else store.token()
                                                store.configure(endpointUrl.trim(), tokenToSave.trim())
                                            }
                                        }
                                        isSaving = false
                                        result.onSuccess {
                                            isConfigured = true
                                            savedEndpoint = endpointUrl.trim()
                                            isVerified = false
                                            verificationMessage = ""
                                            accessToken = ""
                                            Toast.makeText(context, "Cloud narration configuration saved!", Toast.LENGTH_SHORT).show()
                                        }.onFailure {
                                            errorMessage = it.message ?: "Could not save configuration."
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NovelPrimary,
                                    contentColor = NovelOnPrimary,
                                    disabledContainerColor = NovelPrimary.copy(alpha = 0.35f),
                                    disabledContentColor = NovelOnPrimary.copy(alpha = 0.6f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = NovelOnPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = "Save Configuration",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            color = NovelOnPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                        TextButton(
                            enabled = settingsLoaded && !isTesting && !isSaving && endpointUrl.isNotBlank() && (accessToken.isNotBlank() || isConfigured),
                            onClick = {
                                isTesting = true
                                isVerified = false
                                verificationMessage = ""
                                val endpointToCheck = endpointUrl.trim()
                                val enteredToken = accessToken.trim()
                                scope.launch {
                                    try {
                                        val token = withContext(Dispatchers.IO) { enteredToken.ifBlank { store.token() } }
                                        NarrationEndpoint.check(endpointToCheck, token)
                                        isVerified = true
                                        verificationMessage = "Endpoint and credentials verified. This check does not generate speech or confirm remaining AI allowance."
                                    } catch (cancelled: CancellationException) { throw cancelled }
                                    catch (e: Exception) { verificationMessage = e.message ?: "Endpoint check failed." }
                                    finally { isTesting = false }
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = NovelPrimary)
                        ) {
                            Text(
                                text = if (isTesting) "Checking…" else "Check Endpoint",
                                color = NovelPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (verificationMessage.isNotBlank()) Text(
                            verificationMessage, style = MaterialTheme.typography.bodySmall,
                            color = if (isVerified) NovelTextSecondary else MaterialTheme.colorScheme.error)
                    }
                }
            }

            // 3. Audio Cache Management Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NovelSurface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(NovelSurfaceBorder)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Disposable Audio Cache",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NovelTextPrimary
                        )
                        Text(
                            text = "Synthesized MP3 passages are cached locally on this device (up to 64 MiB) to avoid re-requesting Cloudflare allowance when re-listening.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NovelTextSecondary
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Text(
                                text = "Cached passages: $cacheFileCount files",
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NovelTextPrimary
                            )

                            if (cacheFileCount > 0) {
                                TextButton(
                                    enabled = !cacheBusy,
                                    onClick = {
                                        cacheBusy = true
                                        scope.launch {
                                            try {
                                                val result = NarrationCache.clear(context.applicationContext)
                                                cacheFileCount = result.remaining.files
                                                cacheMessage = when {
                                                    result.playbackProtected -> "Narration session protected. Stop narration before clearing its cache."
                                                    result.failed > 0 -> "Deleted ${result.deleted} passages; ${result.failed} could not be removed. ${result.remaining.files} remain."
                                                    result.remaining.files > 0 -> "Deleted ${result.deleted} passages; ${result.remaining.files} remain."
                                                    else -> "Audio cache cleared."
                                                }
                                            } catch (cancelled: CancellationException) { throw cancelled }
                                            catch (_: Exception) { cacheMessage = "Could not clear the audio cache. Tap Refresh to inspect it." }
                                            finally { cacheBusy = false }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = null,
                                        tint = NovelPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear Audio Cache", color = NovelPrimary, fontSize = 12.sp)
                                }
                            }
                        }
                        TextButton(enabled = !cacheBusy, onClick = {
                            cacheBusy = true
                            scope.launch {
                                try {
                                    cacheFileCount = NarrationCache.inspect(context.applicationContext).files
                                    cacheMessage = "Cache information refreshed."
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { cacheMessage = "Could not inspect the audio cache." }
                                finally { cacheBusy = false }
                            }
                        }) { Text(if (cacheBusy) "Working…" else "Refresh Cache") }
                        if (cacheMessage.isNotBlank()) Text(cacheMessage, style = MaterialTheme.typography.bodySmall, color = NovelTextSecondary)
                    }
                }
            }

            // 4. Boundaries & Security Information Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NovelSurface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(NovelSurfaceBorder)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = NovelPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Service Boundaries & Security",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NovelTextPrimary
                            )
                        }

                        listOf(
                            "Workers Free Tier: Free-plan limits stop generation. Upgrading the account to Paid changes billing behavior.",
                            "MeloTTS Model: Calls the fixed @cf/myshell-ai/melotts model; no expensive LLMs or arbitrary compute.",
                            "Device Security: Tokens use Android Keystore AES-GCM encryption and are excluded from backups.",
                            "Rate Limiting: Approximate regional limit of 12 requests/minute; not a global allowance counter.",
                            "No Text Logging: Cloudflare Worker observability is disabled; chapter passages are processed in-flight over HTTPS."
                        ).forEach { rule ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text("• ", color = NovelPrimary, fontWeight = FontWeight.Bold)
                                Text(
                                    text = rule,
                                    fontSize = 12.sp,
                                    color = NovelTextSecondary,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
