package com.dockeep.app.ui.details

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dockeep.app.data.storage.LocalFileStorage
import com.dockeep.app.domain.DocumentItem
import com.dockeep.app.domain.FileType
import com.dockeep.app.ui.components.ConfirmDeleteDialog
import com.dockeep.app.ui.components.ExpiryBadge
import com.dockeep.app.ui.components.PdfFirstPage
import com.dockeep.app.ui.components.SoftCard
import com.dockeep.app.ui.edit.ImageAdjustScreen
import com.dockeep.app.ui.edit.ImageEditSource
import com.dockeep.app.ui.viewer.FullScreenDocumentViewer
import com.dockeep.app.util.toDisplayDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailsScreen(
    viewModel: DocumentDetailsViewModel,
    storage: LocalFileStorage,
    onBack: () -> Unit,
    onEdit: (String) -> Unit
) {
    val document by viewModel.document.collectAsStateWithLifecycle()
    val details by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var replaceSheet by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var adjustSource by remember { mutableStateOf<ImageEditSource?>(null) }
    val captureFile = remember { storage.createCaptureFile() }
    val captureUri = remember(captureFile) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", captureFile)
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && captureFile.exists()) {
            replaceSheet = false
            adjustSource = ImageEditSource.FromFile(captureFile)
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            replaceSheet = false
            adjustSource = ImageEditSource.FromUri(uri)
        }
    }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.replaceFile(uri, "application/pdf")
        replaceSheet = false
    }

    LaunchedEffect(details.deleted) { if (details.deleted) onBack() }

    val pendingAdjust = adjustSource
    if (pendingAdjust != null) {
        ImageAdjustScreen(
            source = pendingAdjust,
            createOutputFile = { storage.createEditFile() },
            onCancel = { adjustSource = null },
            onConfirm = { file ->
                viewModel.replaceCapture(file)
                adjustSource = null
            }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(document?.name ?: "Document") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (document != null) {
                        IconButton(onClick = { fullscreen = true }) {
                            Icon(Icons.Rounded.Fullscreen, contentDescription = "View full screen")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete document",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        val current = document
        if (current == null) {
            Box(Modifier.padding(padding)) { Text("Document not found", modifier = Modifier.padding(20.dp)) }
            return@Scaffold
        }
        val file = viewModel.fileFor(current)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Preview(current, file, onOpenFullscreen = { fullscreen = true })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(current.categoryLabel, style = MaterialTheme.typography.titleMedium)
                ExpiryBadge(current.expiryStatus)
            }
            SoftCard(Modifier.fillMaxWidth()) {
                MetaRow("Name", current.name)
                MetaRow("Category", current.categoryLabel)
                MetaRow("Document number", current.documentNumber ?: "—")
                MetaRow("Issue date", current.issueDate?.toDisplayDate() ?: "—")
                MetaRow("Expiry date", current.expiryDate?.toDisplayDate() ?: "—")
                MetaRow("Notes", current.notes ?: "—")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = { onEdit(current.id) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Edit, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text("Edit")
                }
                FilledTonalButton(onClick = { replaceSheet = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.SwapHoriz, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text("Replace")
                }
            }
            if (current.fileType == FileType.IMAGE && file.exists()) {
                FilledTonalButton(
                    onClick = { adjustSource = ImageEditSource.FromFile(file) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Crop, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text("Crop & adjust")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { share(context, file, current) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.IosShare, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text("Share")
                }
            }
            Button(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("Delete document")
            }
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            documentName = document?.name ?: "this document",
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false }
        )
    }

    if (fullscreen && document != null) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            FullScreenDocumentViewer(
                document = document!!,
                file = viewModel.fileFor(document!!),
                onClose = { fullscreen = false }
            )
        }
    }

    if (replaceSheet) {
        ModalBottomSheet(
            onDismissRequest = { replaceSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Replace file", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { takePicture.launch(captureUri) }) { Text("Scan or take photo") }
                TextButton(onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Upload image") }
                TextButton(onClick = { pickPdf.launch("application/pdf") }) { Text("Upload PDF") }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun Preview(
    document: DocumentItem,
    file: java.io.File,
    onOpenFullscreen: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onOpenFullscreen)
    ) {
        if (!file.exists()) {
            Text("File missing", modifier = Modifier.padding(20.dp))
        } else if (document.fileType == FileType.PDF) {
            PdfFirstPage(file, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            AsyncImage(
                model = file,
                contentDescription = document.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Fullscreen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.padding(4.dp))
                Text("Full screen", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun share(context: android.content.Context, file: java.io.File, document: DocumentItem) {
    if (!file.exists()) return
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = document.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, document.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share ${document.name}"))
}
