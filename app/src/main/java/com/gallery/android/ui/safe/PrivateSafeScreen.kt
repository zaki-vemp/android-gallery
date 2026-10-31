package com.gallery.android.ui.safe

import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            // Corner bracketed industrial shield icon
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primary, RectangleShape),
                contentAlignment = Alignment.Center,
            ) {
                com.gallery.android.ui.components.FrameCorners(
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "PRIVATE SAFE",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = androidx.compose.ui.unit.TextUnit(0.06f, androidx.compose.ui.unit.TextUnitType.Em),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                "AES-256 files sealed with the Android Keystore. Hidden from MediaStore. Screenshots blocked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            if (errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    errorMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(20.dp))

            // 4 PIN Dot Indicators
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for (i in 0 until 4) {
                    val filled = i < pin.length
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary,
                                RectangleShape,
                            )
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary else Color.Transparent,
                                RectangleShape,
                            )
                    )
                }
            }
        }

        // 3x4 Numeric Keypad
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            val keyRows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("C", "0", "⌫"),
            )

            keyRows.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 6.dp),
                ) {
                    row.forEach { label ->
                        Surface(
                            onClick = {
                                when (label) {
                                    "C" -> pin = ""
                                    "⌫" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                    else -> {
                                        if (pin.length < 4) {
                                            pin += label
                                            if (pin.length == 4) {
                                                onPinEntered(pin)
                                            }
                                        }
                                    }
                                }
                            },
                        shape = RectangleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.size(64.dp, 52.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            if (hasBiometric) {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onBiometricClick) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "USE FINGERPRINT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
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
    Column(modifier = Modifier.fillMaxSize()) {
        com.gallery.android.ui.components.ApertureHeader(
            title = "Private Safe",
            subtitle = "Unlocked · ${media.size} items",
            actions = {
                com.gallery.android.ui.components.HairlineChip(onClick = onLock) {
                    Text("LOCK", style = MaterialTheme.typography.labelSmall)
                }
            },
        )
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RectangleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Text(
                "Session unlocked · FLAG_SECURE active · files decrypt to memory only",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

            if (media.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Private Safe is empty", color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Move photos here to keep them encrypted and hidden",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(media, key = { it.id }) { item ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { onMediaClick(item.id) },
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(item.uri).crossfade(true).build(),
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "MOVE IN",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RectangleShape)
                        .padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text(
                    "RESTORE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RectangleShape)
                        .clickable { media.firstOrNull()?.let { onRestoreFromSafe(it.id) } }
                        .padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
    }
}
