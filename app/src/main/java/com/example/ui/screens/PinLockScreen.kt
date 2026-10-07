package com.example.ui.screens

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MintAccent
import com.example.util.BiometricAuthHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PinLockScreen(
    onUnlock: () -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE) }
    val savedPin = remember { prefs.getString("pin", null) }
    
    var currentPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    var failedAttempts by remember { mutableIntStateOf(prefs.getInt("pin_failed_attempts", 0)) }
    var lockoutTime by remember { mutableLongStateOf(prefs.getLong("pin_lockout_until", 0L)) }
    
    val scope = rememberCoroutineScope()

    val isLockedOut = remember(lockoutTime) {
        System.currentTimeMillis() < lockoutTime
    }

    // Update lockout status periodically
    LaunchedEffect(lockoutTime) {
        if (isLockedOut) {
            while (System.currentTimeMillis() < lockoutTime) {
                delay(1000)
            }
            // Lockout ended
            errorMessage = ""
            isError = false
        }
    }

    val canUseBiometric = remember(context) {
        BiometricAuthHelper.isBiometricAvailable(context) && 
        BiometricAuthHelper.isBiometricEnabled(context)
    }

    // Function to trigger biometric authentication prompt
    val triggerBiometricAuth: () -> Unit = remember(context, onUnlock) {
        {
            val activity = BiometricAuthHelper.findFragmentActivity(context)
            if (activity != null && canUseBiometric) {
                BiometricAuthHelper.authenticate(
                    activity = activity,
                    title = "ยืนยันตัวตนด้วยลายนิ้วมือ",
                    subtitle = "แตะเซนเซอร์สแกนลายนิ้วมือเพื่อเข้าใช้งาน Smart OSM",
                    negativeButtonText = "ใช้รหัส PIN แทน",
                    onSuccess = {
                        onUnlock()
                    },
                    onError = { errorCode, errString ->
                        // Don't show error if user cancelled intentionally
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && 
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            errorMessage = errString.toString()
                            isError = true
                            scope.launch {
                                delay(3000)
                                isError = false
                            }
                        }
                    },
                    onFailed = {
                        errorMessage = "ไม่พบลายนิ้วมือที่ตรงกัน กรุณาลองใหม่อีกครั้ง"
                        isError = true
                        scope.launch {
                            delay(2000)
                            isError = false
                        }
                    }
                )
            }
        }
    }

    // Auto-prompt biometric if user has an existing PIN or biometric is configured
    LaunchedEffect(Unit) {
        if (canUseBiometric && savedPin != null) {
            // Give the screen a brief moment to render smoothly before showing system dialog
            delay(350)
            triggerBiometricAuth()
        }
    }

    val title = when {
        savedPin != null -> "กรุณาใส่รหัสผ่าน (PIN)"
        isConfirming -> "ยืนยันรหัสผ่านอีกครั้ง"
        else -> "ตั้งรหัสผ่าน 6 หลัก"
    }
    
    val icon = if (savedPin != null) Icons.Filled.Lock else Icons.Filled.LockOpen

    fun handlePinDigit(digit: String) {
        if (isLockedOut) {
            val remainingSeconds = ((lockoutTime - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            errorMessage = "ระบบระงับชั่วคราว: กรุณาลองใหม่ใน $remainingSeconds วินาที"
            isError = true
            return
        }

        if (currentPin.length < 6) {
            currentPin += digit
            isError = false
            
            if (currentPin.length == 6) {
                if (savedPin != null) {
                    // Verify hashed PIN
                    val isValid = if (savedPin.length == 6) {
                        // Migration: If old PIN was plaintext
                        currentPin == savedPin
                    } else {
                        com.example.util.SecurityUtils.verifyPin(currentPin, savedPin)
                    }

                    if (isValid) {
                        // Reset failures on success
                        failedAttempts = 0
                        prefs.edit()
                            .putInt("pin_failed_attempts", 0)
                            .putLong("pin_lockout_until", 0L)
                            .apply()
                        
                        // Migrate to hashed PIN if it was plaintext
                        if (savedPin.length == 6) {
                            prefs.edit().putString("pin", com.example.util.SecurityUtils.hashPin(currentPin)).apply()
                        }
                        
                        onUnlock()
                    } else {
                        failedAttempts++
                        isError = true
                        
                        if (failedAttempts >= 5) {
                            val newLockoutUntil = System.currentTimeMillis() + (30 * 1000) // 30s lockout
                            lockoutTime = newLockoutUntil
                            prefs.edit()
                                .putInt("pin_failed_attempts", failedAttempts)
                                .putLong("pin_lockout_until", newLockoutUntil)
                                .apply()
                            errorMessage = "ใส่รหัสผิดเกินกำหนด: ระงับการใช้งาน 30 วินาที"
                        } else {
                            prefs.edit().putInt("pin_failed_attempts", failedAttempts).apply()
                            errorMessage = "รหัสผ่านไม่ถูกต้อง (ลองได้อีก ${5 - failedAttempts} ครั้ง)"
                        }
                        
                        scope.launch {
                            delay(500)
                            currentPin = ""
                        }
                    }
                } else {
                    // Setup mode
                    if (!isConfirming) {
                        isConfirming = true
                        confirmPin = currentPin
                        currentPin = ""
                    } else {
                        if (currentPin == confirmPin) {
                            // Store hashed PIN
                            val hashedPin = com.example.util.SecurityUtils.hashPin(currentPin)
                            prefs.edit().putString("pin", hashedPin).apply()
                            onUnlock()
                        } else {
                            isError = true
                            errorMessage = "รหัสผ่านไม่ตรงกัน กรุณาลองใหม่"
                            scope.launch {
                                delay(1000)
                                currentPin = ""
                                confirmPin = ""
                                isConfirming = false
                            }
                        }
                    }
                }
            }
        }
    }

    fun handleDelete() {
        if (currentPin.isNotEmpty()) {
            currentPin = currentPin.dropLast(1)
            isError = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = EmeraldPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Lock",
                tint = MintAccent,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            
            AnimatedVisibility(
                visible = isError,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.errorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (!isError) {
                Spacer(modifier = Modifier.height(28.dp))
            }
            
            // Pin indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(vertical = 32.dp)
            ) {
                for (i in 0 until 6) {
                    val isFilled = i < currentPin.length
                    val color by animateColorAsState(
                        targetValue = if (isError) MaterialTheme.colorScheme.errorContainer else if (isFilled) MintAccent else Color.White.copy(alpha = 0.3f),
                        animationSpec = tween(300)
                    )
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Keypad
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val padData = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf(if (canUseBiometric && savedPin != null) "BIO" else "", "0", "DEL")
                )
                
                padData.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        row.forEach { key ->
                            if (key.isEmpty()) {
                                Spacer(modifier = Modifier.size(72.dp))
                            } else if (key == "BIO") {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(MintAccent.copy(alpha = 0.2f))
                                        .clickable { triggerBiometricAuth() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Fingerprint,
                                        contentDescription = "สแกนลายนิ้วมือ",
                                        tint = MintAccent,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            } else if (key == "DEL") {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .clickable { handleDelete() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Backspace,
                                        contentDescription = "Delete",
                                        tint = Color.White
                                    )
                                }
                            } else {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .clickable { handlePinDigit(key) }
                                ) {
                                    Text(
                                        text = key,
                                        color = Color.White,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
