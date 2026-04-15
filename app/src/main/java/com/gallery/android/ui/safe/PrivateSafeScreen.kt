package com.gallery.android.ui.safe

import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateSafeScreen(
    onMediaClick: (Long) -> Unit,
    viewModel: PrivateSafeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // FLAG_SECURE to prevent screenshots
    DisposableEffect(Unit) {
        val activity = context as? FragmentActivity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    when (uiState.authState) {
        SafeAuthState.SETTING_PIN -> {
            SetPinScreen(
                onPinSet = viewModel::setPin,
                errorMessage = uiState.errorMessage,
            )
        }
        SafeAuthState.LOCKED -> {
            LockScreen(
                hasBiometric = BiometricManager.from(context)
                    .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS,
                errorMessage = uiState.errorMessage,
                onPinEntered = viewModel::unlockWithPin,
                onBiometricClick = {
                    val activity = context as? FragmentActivity ?: return@LockScreen
                    val executor = ContextCompat.getMainExecutor(context)
                    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            viewModel.onBiometricSuccess()
                        }
                    })
                    prompt.authenticate(
                        BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Unlock Private Safe")
                            .setSubtitle("Use your biometric to access your private safe")
                            .setNegativeButtonText("Use PIN")
                            .build()
                    )
                },
            )
        }
        SafeAuthState.UNLOCKED -> {
            SafeContentScreen(
                media = uiState.safeMedia,
                onMediaClick = onMediaClick,
                onLock = viewModel::lock,
                onRestoreFromSafe = viewModel::restoreFromSafe,
            )
        }
    }
}

@Composable
private fun SetPinScreen(onPinSet: (String) -> Unit, errorMessage: String?) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text("Set Up Private Safe", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Create a PIN to protect your private photos", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6) pin = it },
            label = { Text("PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            isError = showError,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = confirmPin,
            onValueChange = { if (it.length <= 6) confirmPin = it },
            label = { Text("Confirm PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            isError = showError,
            supportingText = { if (showError) Text("PINs do not match") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (pin == confirmPin && pin.length >= 4) onPinSet(pin)
                else showError = true
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = pin.length >= 4,
        ) { Text("Set PIN") }
    }
}

@Composable
private fun LockScreen(
    hasBiometric: Boolean,
    errorMessage: String?,
    onPinEntered: (String) -> Unit,
    onBiometricClick: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text("Private Safe", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6) pin = it },
            label = { Text("Enter PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            isError = errorMessage != null,
            supportingText = { errorMessage?.let { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onPinEntered(pin) },
            modifier = Modifier.fillMaxWidth(),
            enabled = pin.isNotEmpty(),
        ) { Text("Unlock") }
        if (hasBiometric) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onBiometricClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Use Biometric")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SafeContentScreen(
    media: List<com.gallery.android.domain.model.MediaItem>,
    onMediaClick: (Long) -> Unit,
    onLock: () -> Unit,
    onRestoreFromSafe: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Private Safe", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onLock) {
                        Icon(Icons.Default.LockOpen, contentDescription = "Lock")
                    }
                },
            )
        }
    ) { padding ->
        if (media.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(16.dp))
                    Text("Private Safe is empty", color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(8.dp))
                    Text("Move photos here to keep them private", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(media, key = { it.id }) { item ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onMediaClick(item.id) }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(item.uri).crossfade(true).build(),
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        IconButton(
                            onClick = { onRestoreFromSafe(item.id) },
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = "Restore", tint = androidx.compose.ui.graphics.Color.White)
                        }
                    }
                }
            }
        }
    }
}
