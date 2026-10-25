package com.dockeep.app.ui.add

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dockeep.app.data.storage.LocalFileStorage
import com.dockeep.app.domain.Category
import com.dockeep.app.domain.DefaultCategory
import com.dockeep.app.domain.DocumentGroup
import com.dockeep.app.ui.components.PdfFirstPage
import com.dockeep.app.ui.edit.ImageAdjustScreen
import com.dockeep.app.ui.LocalAppContainer
import com.dockeep.app.ui.components.launchExternal
import com.dockeep.app.ui.components.rememberCameraCapture
import com.dockeep.app.ui.edit.ImageEditSource
import com.dockeep.app.ui.edit.ImageEditSourceSaver
import com.dockeep.app.util.toDisplayDate
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDocumentScreen(
    viewModel: AddDocumentViewModel,
    storage: LocalFileStorage,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val lock = LocalAppContainer.current.appLockController
    var adjustSource by rememberSaveable(stateSaver = ImageEditSourceSaver) {
        mutableStateOf<ImageEditSource?>(null)
    }

    val takePicture = rememberCameraCapture(
        storage = storage,
        onCaptured = { adjustSource = ImageEditSource.FromFile(it) },
        onError = viewModel::showError
    )
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) adjustSource = ImageEditSource.FromUri(uri)
    }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.onFilePicked(uri, "application/pdf")
    }

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val pendingAdjust = adjustSource
    if (pendingAdjust != null) {
        ImageAdjustScreen(
            source = pendingAdjust,
            createOutputFile = { storage.createEditFile() },
            onCancel = { adjustSource = null },
            onConfirm = { file ->
                viewModel.onCaptureFile(file)
                adjustSource = null
            }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) "Edit document" else "Add document") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = state.isEdit || state.hasFile,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            label = "add-document"
        ) { showForm ->
            if (!showForm) {
                SourcePicker(
                    onScan = takePicture,
                    onPhoto = takePicture,
                    onImage = {
                        lock.launchExternal(viewModel::showError) {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    },
                    onPdf = {
                        lock.launchExternal(viewModel::showError, "No file manager was found on this phone.") {
                            pickPdf.launch("application/pdf")
                        }
                    }
                )
            } else {
                DocumentForm(
                    state = state,
                    storage = storage,
                    viewModel = viewModel,
                    onCrop = {
                        val pendingFile = state.pendingFile
                        val pendingUri = state.pendingUri
                        val existingPath = state.existingPath
                        when {
                            pendingFile != null -> adjustSource = ImageEditSource.FromFile(pendingFile)
                            pendingUri != null -> adjustSource = ImageEditSource.FromUri(pendingUri)
                            existingPath != null -> adjustSource = ImageEditSource.FromFile(storage.fileFor(existingPath))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SourcePicker(
    onScan: () -> Unit,
    onPhoto: () -> Unit,
    onImage: () -> Unit,
    onPdf: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Choose a source", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Files are copied into DocKeep’s private storage. Nothing is uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SourceCard("Scan with camera", "Capture a document", Icons.Rounded.DocumentScanner, Modifier.weight(1f), onScan)
            SourceCard("Take photo", "Use the camera", Icons.Rounded.CameraAlt, Modifier.weight(1f), onPhoto)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SourceCard("Upload image", "Choose from gallery", Icons.Rounded.Image, Modifier.weight(1f), onImage)
            SourceCard("Upload PDF", "Import a PDF file", Icons.Rounded.PictureAsPdf, Modifier.weight(1f), onPdf)
        }
    }
}

@Composable
private fun SourceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentForm(
    state: AddDocumentUiState,
    storage: LocalFileStorage,
    viewModel: AddDocumentViewModel,
    onCrop: () -> Unit
) {
    var issuePicker by remember { mutableStateOf(false) }
    var expiryPicker by remember { mutableStateOf(false) }
    var dobPicker by remember { mutableStateOf(false) }
    val category = DefaultCategory.fromId(state.categoryId)
    val types = state.categories.filter { it.groupId == category.group.id }.ifEmpty {
        DefaultCategory.inGroup(category.group).map {
            Category(
                id = it.id,
                name = it.displayName,
                emoji = it.emoji,
                folderName = it.folderName,
                sortOrder = it.sortOrder,
                groupId = it.group.id,
                groupName = it.group.displayName
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        FilePreview(state, storage)
        val canCrop = !state.isPdf && state.hasFile
        if (canCrop) {
            Button(
                onClick = onCrop,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Crop, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Crop & adjust")
            }
            Button(
                onClick = viewModel::scanAndAutoFill,
                enabled = state.canScan,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.ocrScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text("Scanning…")
                } else {
                    Icon(Icons.Rounded.DocumentScanner, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Scan & Auto-Fill")
                }
            }
        }
        state.ocrStatus?.let { status ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = status,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        AutoFilledField(
            filled = "name" in state.autoFilled,
            value = {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(category.nameLabel) },
                    singleLine = true
                )
            }
        )
        Text("Category", style = MaterialTheme.typography.titleSmall)
        CategoryChips(
            groups = DocumentGroup.entries,
            selectedGroupId = category.group.id,
            onGroup = viewModel::onGroupChange,
            types = types,
            selectedTypeId = category.id,
            onType = viewModel::onCategoryChange
        )
        if (category.showsFatherName) {
            AutoFilledField(
                filled = "father" in state.autoFilled,
                value = {
                    OutlinedTextField(
                        value = state.fatherName,
                        onValueChange = viewModel::onFatherNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Father's name") },
                        singleLine = true
                    )
                }
            )
        }
        AutoFilledField(
            filled = "number" in state.autoFilled,
            value = {
                OutlinedTextField(
                    value = state.documentNumber,
                    onValueChange = viewModel::onNumberChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(category.numberLabel) },
                    singleLine = true
                )
            }
        )
        if (category.showsDateOfBirth) {
            DateField("Date of birth", state.dateOfBirth, { dobPicker = true }, { viewModel.onDateOfBirth(null) }, "dob" in state.autoFilled)
        }
        if (category.showsIssueDate) {
            DateField(
                label = category.issueLabel,
                value = state.issueDate,
                onPick = { issuePicker = true },
                onClear = { viewModel.onIssueDate(null) },
                autoFilled = "issue" in state.autoFilled
            )
        }
        if (category.showsExpiry) {
            DateField(category.expiryLabel, state.expiryDate, { expiryPicker = true }, { viewModel.onExpiryDate(null) }, "expiry" in state.autoFilled)
        }
        if (category.showsVehicleIds) {
            AutoFilledField(
                filled = "chassis" in state.autoFilled,
                value = {
                    OutlinedTextField(
                        value = state.chassisNumber,
                        onValueChange = viewModel::onChassisChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Chassis number") },
                        singleLine = true
                    )
                }
            )
            AutoFilledField(
                filled = "engine" in state.autoFilled,
                value = {
                    OutlinedTextField(
                        value = state.engineNumber,
                        onValueChange = viewModel::onEngineChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Engine number") },
                        singleLine = true
                    )
                }
            )
        }
        if (category.showsClinical) {
            AutoFilledField(
                filled = "doctor" in state.autoFilled,
                value = {
                    OutlinedTextField(
                        value = state.doctorName,
                        onValueChange = viewModel::onDoctorChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Doctor name") },
                        singleLine = true
                    )
                }
            )
            AutoFilledField(
                filled = "hospital" in state.autoFilled,
                value = {
                    OutlinedTextField(
                        value = state.hospital,
                        onValueChange = viewModel::onHospitalChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Hospital / clinic") },
                        singleLine = true
                    )
                }
            )
        }
        if (category.showsPhone || state.phoneNumber.isNotBlank()) {
            AutoFilledField(
                filled = "phone" in state.autoFilled,
                value = {
                    OutlinedTextField(
                        value = state.phoneNumber,
                        onValueChange = viewModel::onPhoneChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Phone number") },
                        singleLine = true
                    )
                }
            )
        }
        OutlinedTextField(
            value = state.notes,
            onValueChange = viewModel::onNotesChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Notes (optional)") },
            minLines = 3
        )
        Button(
            onClick = viewModel::save,
            enabled = !state.saving && !state.ocrScanning,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (state.saving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(if (state.isEdit) "Save changes" else "Save document")
            }
        }
        Text(
            "Saved only in private app storage on this phone. Scans stay on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (issuePicker) {
        DateDialog(
            initial = state.issueDate,
            onDismiss = { issuePicker = false },
            onConfirm = {
                viewModel.onIssueDate(it)
                issuePicker = false
            }
        )
    }
    if (expiryPicker) {
        DateDialog(
            initial = state.expiryDate,
            onDismiss = { expiryPicker = false },
            onConfirm = {
                viewModel.onExpiryDate(it)
                expiryPicker = false
            }
        )
    }
    if (dobPicker) {
        DateDialog(
            initial = state.dateOfBirth,
            onDismiss = { dobPicker = false },
            onConfirm = {
                viewModel.onDateOfBirth(it)
                dobPicker = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(
    groups: List<DocumentGroup>,
    selectedGroupId: String,
    onGroup: (String) -> Unit,
    types: List<Category>,
    selectedTypeId: String,
    onType: (String) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.forEach { group ->
            FilterChip(
                selected = selectedGroupId == group.id,
                onClick = { onGroup(group.id) },
                label = { Text("${group.emoji} ${group.displayName}") }
            )
        }
    }
    Text("Type", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        types.forEach { type ->
            FilterChip(
                selected = selectedTypeId == type.id,
                onClick = { onType(type.id) },
                label = { Text(type.name) }
            )
        }
    }
}

@Composable
private fun AutoFilledField(
    filled: Boolean,
    value: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        value()
        if (filled) {
            Text(
                "Auto-filled from document",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun FilePreview(state: AddDocumentUiState, storage: LocalFileStorage) {
    val model: Any? = when {
        state.pendingFile != null -> state.pendingFile
        state.pendingUri != null -> state.pendingUri
        state.existingPath != null -> storage.fileFor(state.existingPath)
        else -> null
    }
    val isPdf = (state.pendingMime ?: state.existingMime)?.contains("pdf") == true
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                model is File && isPdf -> PdfFirstPage(model, Modifier.fillMaxSize())
                model is Uri && isPdf -> Text("PDF selected")
                model != null && !isPdf -> AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                )
                else -> Text("No file selected")
            }
        }
    }
}

@Composable
private fun DateField(
    label: String,
    value: Long?,
    onPick: () -> Unit,
    onClear: () -> Unit,
    autoFilled: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value?.toDisplayDate() ?: "Not set", style = MaterialTheme.typography.bodyLarge)
                if (autoFilled && value != null) {
                    Text(
                        "Auto-filled from document",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (value != null) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear date")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(
    initial: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { pickerState.selectedDateMillis?.let(onConfirm) ?: onDismiss() }) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = pickerState)
    }
}
