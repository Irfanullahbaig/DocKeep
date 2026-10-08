package com.dockeep.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.dockeep.app.ui.components.CategoryCard
import com.dockeep.app.ui.components.ConfirmDeleteDialog
import com.dockeep.app.ui.components.DocKeepBrand
import com.dockeep.app.ui.components.DocumentRow
import com.dockeep.app.ui.components.EmptyState
import com.dockeep.app.ui.components.SectionHeader
import com.dockeep.app.ui.components.StatCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    storage: LocalFileStorage,
    onAdd: () -> Unit,
    onCategory: (String) -> Unit,
    onDocuments: () -> Unit,
    onExpiring: () -> Unit,
    onDocument: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<DocumentItem?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add Document") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { DocKeepBrand() }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        label = "Total documents",
                        value = state.totalDocuments.toString(),
                        icon = Icons.Rounded.Description,
                        modifier = Modifier.weight(1f),
                        onClick = onDocuments
                    )
                    StatCard(
                        label = "Expiring soon",
                        value = (state.expiringSoonCount + state.expiredCount).toString(),
                        icon = Icons.Rounded.Schedule,
                        modifier = Modifier.weight(1f),
                        onClick = onExpiring
                    )
                }
            }
            item {
                SectionHeader(
                    title = "Categories",
                    actionLabel = "See all",
                    onAction = onDocuments
                )
            }
            items(state.categories.chunked(2)) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { category ->
                        CategoryCard(
                            category = category,
                            modifier = Modifier.weight(1f),
                            onClick = { onCategory(category.id) }
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item {
                AnimatedVisibility(visible = state.expiringSoon.isNotEmpty()) {
                    SectionHeader(
                        title = "Expiring soon",
                        actionLabel = "View",
                        onAction = onExpiring
                    )
                }
            }
            items(state.expiringSoon, key = { "expiring-${it.id}" }) { document ->
                DocumentRow(
                    document = document,
                    file = storage.fileFor(document.relativePath),
                    onClick = { onDocument(document.id) },
                    onDelete = { pendingDelete = document }
                )
            }
            item {
                SectionHeader(
                    title = "Recent documents",
                    actionLabel = "All",
                    onAction = onDocuments
                )
            }
            if (state.recent.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.FolderOff,
                        title = "No documents yet",
                        message = "Add a scan, photo, or PDF. Everything stays on this phone."
                    )
                }
            } else {
                items(state.recent, key = { it.id }) { document ->
                    DocumentRow(
                        document = document,
                        file = storage.fileFor(document.relativePath),
                        onClick = { onDocument(document.id) },
                        onDelete = { pendingDelete = document }
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
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
