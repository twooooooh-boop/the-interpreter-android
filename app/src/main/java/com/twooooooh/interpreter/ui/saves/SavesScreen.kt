package com.twooooooh.interpreter.ui.saves

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twooooooh.interpreter.ui.ScreenTitle
import com.twooooooh.interpreter.ui.theme.CipherTextStyle
import com.twooooooh.interpreter.ui.theme.KeyTextStyle
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun SavesScreen(
    modifier: Modifier = Modifier,
    viewModel: SavesViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    BackHandler(enabled = !viewModel.atRoot) { viewModel.up() }

    var pendingDelete by remember { mutableStateOf<File?>(null) }
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!viewModel.atRoot) {
                IconButton(onClick = { viewModel.up() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            ScreenTitle(if (viewModel.atRoot) "Saves" else viewModel.currentDir.name)
        }

        if (viewModel.entries.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nothing saved yet.\nUse “Save session” on the Interpreter tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(viewModel.entries, key = { it.absolutePath }) { file ->
                    SaveRow(
                        file = file,
                        onOpen = { viewModel.open(file) },
                        onDelete = { pendingDelete = file },
                    )
                }
            }
        }
    }

    pendingDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete?") },
            text = { Text(file.name) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(file)
                    pendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    viewModel.openedFile?.let { file ->
        AlertDialog(
            onDismissRequest = viewModel::closeFile,
            title = { Text(file.name, style = MaterialTheme.typography.titleMedium) },
            text = {
                Box(
                    Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SelectionContainer {
                        Text(viewModel.openedText, style = CipherTextStyle.copy(fontSize = 16.sp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::closeFile) { Text("Close") } },
            dismissButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(viewModel.openedText)) }) { Text("Copy") }
            },
        )
    }
}

@Composable
private fun SaveRow(file: File, onOpen: () -> Unit, onDelete: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onOpen)
                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (file.isDirectory) "DIR" else "TXT",
                    style = KeyTextStyle.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(
                    text = describe(file),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun describe(file: File): String {
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(file.lastModified()))
    return if (file.isDirectory) "${file.listFiles()?.size ?: 0} files · $date" else "${file.length()} bytes · $date"
}
