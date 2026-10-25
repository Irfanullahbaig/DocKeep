package com.dockeep.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dockeep.app.security.AppPermissions
import com.dockeep.app.ui.components.DocKeepBrand

@Composable
fun PermissionsOnboardingScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onFinished() }

    fun requestPermissions() {
        val missing = AppPermissions.missing(context)
        if (missing.isEmpty()) onFinished() else launcher.launch(missing)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        DocKeepBrand()
        Spacer(Modifier.height(28.dp))
        Text("Allow access to get started", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "DocKeep stays offline. These permissions are only used on this phone to scan, import, and remind you about documents.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        PermissionRow(
            icon = Icons.Rounded.CameraAlt,
            title = "Camera",
            body = "Opens your camera app to scan IDs, cards, and papers. Photos go straight into DocKeep."
        )
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            icon = Icons.Rounded.Folder,
            title = "Photos & storage",
            body = "Pick images and PDFs with Android’s picker. DocKeep only sees the files you choose."
        )
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            icon = Icons.Rounded.Notifications,
            title = "Notifications",
            body = "Optional local reminders when a document is expiring soon."
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = ::requestPermissions,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Allow permissions")
        }
        TextButton(
            onClick = onFinished,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Not now")
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    body: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
