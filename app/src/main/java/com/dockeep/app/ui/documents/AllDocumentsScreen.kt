package com.dockeep.app.ui.documents

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dockeep.app.data.storage.LocalFileStorage
import com.dockeep.app.domain.DocumentItem
import com.dockeep.app.ui.components.ConfirmDeleteDialog
import com.dockeep.app.ui.components.DocumentRow
import com.dockeep.app.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllDocumentsScreen(
    viewModel: AllDocumentsViewModel,
    storage: LocalFileStorage,
    onDocument: (String) -> Unit,
    onAdd: (String?) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<DocumentItem?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAdd(state.selectedTypeId ?: state.selectedGroupId) }) {
                Icon(Icons.Rounded.Add, contentDescription = "Add document")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                placeholder = { Text("Search name, number, category, notes") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.selectedGroupId == null && state.expiryChip == ExpiryChip.ALL,
                    onClick = {
                        viewModel.onGroupSelected(null)
                        viewModel.onExpiryChip(ExpiryChip.ALL)
                    },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = state.expiryChip == ExpiryChip.EXPIRING,
                    onClick = { viewModel.onExpiryChip(ExpiryChip.EXPIRING) },
                    label = { Text("Expiring soon") }
                )
                FilterChip(
                    selected = state.expiryChip == ExpiryChip.EXPIRED,
                    onClick = { viewModel.onExpiryChip(ExpiryChip.EXPIRED) },
                    label = { Text("Expired") }
                )
                state.groups.forEach { group ->
                    FilterChip(
                        selected = state.selectedGroupId == group.id,
                        onClick = {
                            viewModel.onGroupSelected(
                                if (state.selectedGroupId == group.id) null else group.id
                            )
                        },
                        label = { Text("${group.emoji} ${group.name}") }
                    )
                }
            }
            if (state.types.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.types.forEach { type ->
                        FilterChip(
                            selected = state.selectedTypeId == type.id,
                            onClick = {
                                viewModel.onTypeSelected(
                                    if (state.selectedTypeId == type.id) null else type.id
                                )
                            },
                            label = { Text(type.name) }
                        )
                    }
                }
            }
            if (state.visible.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "No matching documents",
                    message = "Try another search or clear the filters."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.visible, key = { it.id }) { document ->
                        DocumentRow(
                            document = document,
                            file = storage.fileFor(document.relativePath),
                            onClick = { onDocument(document.id) },
                            onDelete = { pendingDelete = document }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { document ->
        ConfirmDeleteDialog(
            documentName = document.name,
            onConfirm = {
                viewModel.delete(document.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}
