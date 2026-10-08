package com.dockeep.app.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dockeep.app.domain.AutoLockTimeout
import com.dockeep.app.domain.ThemeMode
import com.dockeep.app.notifications.ExpiryNotifications
import com.dockeep.app.security.BiometricAuthenticator
import com.dockeep.app.ui.components.SoftCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    authenticator: BiometricAuthenticator
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = uri
    }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setNotifications(granted) { enabled ->
            if (enabled) ExpiryNotifications.schedule(context) else ExpiryNotifications.cancel(context)
        }
    }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp)
        ) {
            item {
                Text("Security", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                SoftCard(Modifier.fillMaxWidth()) {
                    SettingSwitch(
                        title = "App lock",
                        subtitle = "Require authentication before documents are shown",
                        checked = settings.appLockEnabled,
                        onChecked = { enabled ->
                            authenticator.authenticate(
                                title = if (enabled) "Enable app lock" else "Disable app lock",
                                subtitle = "Confirm it’s you",
                                allowBiometric = settings.biometricEnabled,
                                onSuccess = { viewModel.setAppLock(enabled) },
                                onError = {},
                                onFailed = {}
                            )
                        }
                    )
                    SettingSwitch(
                        title = "Biometric authentication",
                        subtitle = "Fingerprint or face unlock, with device PIN fallback",
                        checked = settings.biometricEnabled,
                        onChecked = { viewModel.setBiometric(it) }
                    )
                    Text("Auto lock", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                    AutoLockTimeout.entries.forEach { timeout ->
                        FilterChip(
                            selected = settings.autoLockTimeout == timeout,
                            onClick = { viewModel.setAutoLock(timeout) },
                            label = { Text(timeout.label) },
                            modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(20.dp))
                Text("Notifications", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                SoftCard(Modifier.fillMaxWidth()) {
                    SettingSwitch(
                        title = "Expiry notifications",
                        subtitle = "Local reminders when a document is within 30 days of expiry",
                        checked = settings.expiryNotificationsEnabled,
                        onChecked = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= 33) {
                                notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.setNotifications(enabled) { on ->
                                    if (on) ExpiryNotifications.schedule(context) else ExpiryNotifications.cancel(context)
                                }
                            }
                        }
                    )
                }
            }
            item {
                Spacer(Modifier.height(20.dp))
                Text("Appearance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                SoftCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setTheme(mode) },
                                label = {
                                    Text(
                                        when (mode) {
                                            ThemeMode.SYSTEM -> "System"
                                            ThemeMode.LIGHT -> "Light"
                                            ThemeMode.DARK -> "Dark"
                                        }
                                    )
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(20.dp))
                Text("Storage", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                SoftCard(Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("Storage usage") },
                        supportingContent = {
                            Text("${ui.storageLabel} · ${ui.documentCount} documents · ${ui.fileCount} files")
                        },
                        leadingContent = { androidx.compose.material3.Icon(Icons.Rounded.Folder, contentDescription = null) }
                    )
                    ListItem(
                        headlineContent = { Text("Export backup") },
                        supportingContent = { Text("Save a local zip of documents and metadata") },
                        leadingContent = { androidx.compose.material3.Icon(Icons.Rounded.CloudUpload, contentDescription = null) },
                        modifier = Modifier.clickable {
                            val name = "DocKeep-backup-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.zip"
                            export.launch(name)
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Restore backup") },
                        supportingContent = { Text("Replace local documents from a DocKeep zip") },
                        leadingContent = { androidx.compose.material3.Icon(Icons.Rounded.CloudDownload, contentDescription = null) },
                        modifier = Modifier.clickable { restore.launch(arrayOf("application/zip", "*/*")) }
                    )
                    ListItem(
                        headlineContent = { Text("Delete all documents") },
                        supportingContent = { Text("Permanently erase files and metadata on this phone") },
                        leadingContent = { androidx.compose.material3.Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable { confirmDelete = true }
                    )
                }
            }
            item {
                Spacer(Modifier.height(20.dp))
                Text(
                    "DocKeep is fully offline. Documents never leave this device unless you share or export them yourself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = {
                    val activity = context as? Activity
                    activity?.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                }) { Text("Open device screen lock settings") }
                Spacer(Modifier.height(48.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all documents?") },
            text = { Text("Every file and its details will be removed from this phone. A backup will not be created automatically.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteAll()
                }) { Text("Delete all") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Restore this backup?") },
            text = { Text("Existing documents on this phone will be replaced with the backup.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = null
                    viewModel.restoreBackup(uri)
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChecked) }
    )
}
