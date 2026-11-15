package dev.octoexpress.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.GitBranch
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ShieldCheck
import dev.octoexpress.auth.SessionManager
import dev.octoexpress.auth.openUrlInBrowser
import dev.octoexpress.model.AppBranding
import dev.octoexpress.model.DeviceCodeState
import dev.octoexpress.ui.components.OctoLoadingState
import dev.octoexpress.ui.components.OctoWavyProgress
import dev.octoexpress.ui.components.expressiveAnimateContentSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SignInTab(val label: String) {
    OAUTH_DEVICE("OAuth Device Flow"),
    PAT("Personal Token")
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SignInScreen(
    sessionManager: SessionManager,
    onSignedIn: () -> Unit
) {
    val savedClientId by sessionManager.clientId.collectAsState()
    var clientIdInput by remember(savedClientId) { mutableStateOf(savedClientId) }
    var patInput by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(SignInTab.OAUTH_DEVICE) }
    var deviceState by remember { mutableStateOf<DeviceCodeState>(DeviceCodeState.Idle) }
    var patLoading by remember { mutableStateOf(false) }
    var patError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    var pollingJob by remember { mutableStateOf<Job?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            ElevatedCard(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .expressiveAnimateContentSize()
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(
                            imageVector = Lucide.GitBranch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(16.dp).size(32.dp)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = AppBranding.APP_NAME,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Fast, Material 3 Expressive GitHub client for Android & Linux.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(22.dp))

                    PrimaryTabRow(
                        selectedTabIndex = SignInTab.entries.indexOf(selectedTab).coerceAtLeast(0),
                        containerColor = androidx.compose.ui.graphics.Color.Transparent
                    ) {
                        SignInTab.entries.forEach { tab ->
                            Tab(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                text = { Text(tab.label, maxLines = 1, softWrap = false) }
                            )
                        }
                    }

                    Spacer(Modifier.height(22.dp))

                    AnimatedContent(targetState = selectedTab) { tab ->
                        when (tab) {
                            SignInTab.OAUTH_DEVICE -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    when (val state = deviceState) {
                                        is DeviceCodeState.Idle,
                                        is DeviceCodeState.Error -> {
                                            OutlinedTextField(
                                                value = clientIdInput,
                                                onValueChange = { clientIdInput = it },
                                                label = { Text("GitHub OAuth App Client ID") },
                                                placeholder = { Text("Ov23li...") },
                                                singleLine = true,
                                                leadingIcon = {
                                                    Icon(Lucide.ShieldCheck, contentDescription = null)
                                                },
                                                supportingText = {
                                                    Text("Enable Device Flow in your GitHub OAuth/GitHub App settings. No secret required.")
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            if (state is DeviceCodeState.Error) {
                                                Spacer(Modifier.height(12.dp))
                                                Surface(
                                                    color = MaterialTheme.colorScheme.errorContainer,
                                                    shape = MaterialTheme.shapes.medium,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = state.message,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        modifier = Modifier.padding(12.dp)
                                                    )
                                                }
                                            }

                                            Spacer(Modifier.height(18.dp))

                                            Button(
                                                onClick = {
                                                    pollingJob?.cancel()
                                                    pollingJob = scope.launch {
                                                        sessionManager.startDeviceFlow(clientIdInput)
                                                            .collect { next ->
                                                                deviceState = next
                                                                if (next is DeviceCodeState.Authenticated) {
                                                                    onSignedIn()
                                                                }
                                                            }
                                                    }
                                                },
                                                enabled = clientIdInput.isNotBlank(),
                                                modifier = Modifier.fillMaxWidth().height(52.dp)
                                            ) {
                                                Text("Sign in with GitHub OAuth")
                                            }
                                        }

                                        is DeviceCodeState.RequestingCode -> {
                                            OctoLoadingState(message = "Requesting device code from GitHub...")
                                        }

                                        is DeviceCodeState.AwaitingUser -> {
                                            Text(
                                                text = "Enter this code on GitHub",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Spacer(Modifier.height(12.dp))

                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = MaterialTheme.shapes.large,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                SelectionContainer {
                                                    Text(
                                                        text = state.userCode,
                                                        style = MaterialTheme.typography.headlineMedium.copy(
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            letterSpacing = 3.sp
                                                        ),
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.padding(vertical = 18.dp, horizontal = 12.dp)
                                                    )
                                                }
                                            }

                                            Spacer(Modifier.height(10.dp))

                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                shape = MaterialTheme.shapes.medium,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    SelectionContainer(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = state.verificationUri,
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                fontFamily = FontFamily.Monospace
                                                            ),
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            runCatching {
                                                                clipboard.setText(AnnotatedString(state.verificationUri))
                                                            }
                                                            scope.launch {
                                                                snackbarHostState.showSnackbar("Copied ${state.verificationUri}")
                                                            }
                                                        },
                                                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                                    ) {
                                                        Icon(
                                                            Lucide.Copy,
                                                            contentDescription = "Copy URL",
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(Modifier.height(14.dp))
                                            OctoWavyProgress()
                                            Spacer(Modifier.height(10.dp))

                                            Text(
                                                text = state.statusMessage ?: "Waiting for authorization...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )

                                            Spacer(Modifier.height(16.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                FilledTonalButton(
                                                    onClick = {
                                                        runCatching {
                                                            clipboard.setText(AnnotatedString(state.userCode))
                                                        }
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar("Copied ${state.userCode}")
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .pointerHoverIcon(PointerIcon.Hand)
                                                ) {
                                                    Icon(Lucide.Copy, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Copy Code", maxLines = 1, softWrap = false)
                                                }

                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            runCatching {
                                                                clipboard.setText(AnnotatedString(state.userCode))
                                                            }
                                                            val openedByPlatform = withContext(Dispatchers.IO) {
                                                                openUrlInBrowser(state.verificationUri)
                                                            }
                                                            val opened = if (openedByPlatform) {
                                                                true
                                                            } else {
                                                                runCatching {
                                                                    uriHandler.openUri(state.verificationUri)
                                                                    true
                                                                }.getOrDefault(false)
                                                            }
                                                            snackbarHostState.showSnackbar(
                                                                if (opened) {
                                                                    "Code ${state.userCode} copied. Opening GitHub..."
                                                                } else {
                                                                    "Code ${state.userCode} copied. Open ${state.verificationUri} in your browser."
                                                                }
                                                            )
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .pointerHoverIcon(PointerIcon.Hand)
                                                ) {
                                                    Icon(Lucide.ExternalLink, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Open GitHub", maxLines = 1, softWrap = false)
                                                }
                                            }

                                            Spacer(Modifier.height(10.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    pollingJob?.cancel()
                                                    deviceState = DeviceCodeState.Idle
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .pointerHoverIcon(PointerIcon.Hand)
                                            ) {
                                                Text("Cancel")
                                            }
                                        }

                                        is DeviceCodeState.Verifying -> {
                                            OctoLoadingState(message = state.message)
                                        }

                                        is DeviceCodeState.Authenticated -> {
                                            OctoLoadingState(message = "Welcome, @${state.session.userLogin ?: "developer"}!")
                                        }
                                    }
                                }
                            }

                            SignInTab.PAT -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = patInput,
                                        onValueChange = {
                                            patInput = it
                                            patError = null
                                        },
                                        label = { Text("GitHub Personal Access Token") },
                                        placeholder = { Text("ghp_... or github_pat_...") },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        leadingIcon = { Icon(Lucide.KeyRound, contentDescription = null) },
                                        supportingText = {
                                            Text("Requires repo, gist, notifications, read:user scopes.")
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (patError != null) {
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            text = patError!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }

                                    Spacer(Modifier.height(14.dp))
                                    if (patLoading) {
                                        OctoWavyProgress()
                                        Spacer(Modifier.height(12.dp))
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                patLoading = true
                                                patError = null
                                                val result = sessionManager.signInWithPat(patInput)
                                                patLoading = false
                                                result.onSuccess { onSignedIn() }
                                                    .onFailure { patError = it.message ?: "Invalid token" }
                                            }
                                        },
                                        enabled = patInput.isNotBlank() && !patLoading,
                                        modifier = Modifier.fillMaxWidth().height(52.dp)
                                    ) {
                                        Text("Sign in with Token")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
