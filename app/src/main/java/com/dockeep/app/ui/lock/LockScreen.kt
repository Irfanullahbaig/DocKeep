package com.dockeep.app.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.dockeep.app.security.AuthAvailability
import com.dockeep.app.ui.components.DocKeepBrand

@Composable
fun LockScreen(
    availability: AuthAvailability,
    errorMessage: String?,
    onUnlock: () -> Unit,
    onDisableLock: () -> Unit
) {
    var launched by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(availability, lifecycleOwner) {
        if (availability != AuthAvailability.READY) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!launched) {
                launched = true
                onUnlock()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DocKeepBrand()
        Spacer(Modifier.height(36.dp))
        Icon(
            imageVector = Icons.Rounded.Fingerprint,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text("Unlock DocKeep", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (availability) {
                AuthAvailability.READY -> "Use fingerprint, face unlock, or your device PIN to open your documents."
                AuthAvailability.NONE_ENROLLED -> "Set a screen lock on this device to protect DocKeep. Documents stay hidden until then."
                AuthAvailability.UNAVAILABLE -> "This device cannot authenticate right now. Documents stay locked."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onUnlock,
            enabled = availability == AuthAvailability.READY,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Unlock")
        }
        if (availability != AuthAvailability.READY) {
            TextButton(onClick = onDisableLock) {
                Text("Continue without app lock")
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "DocKeep never shows document information before you authenticate.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
