package com.twooooooh.interpreter.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twooooooh.interpreter.domain.CipherLanguage
import com.twooooooh.interpreter.domain.KeymapRules
import com.twooooooh.interpreter.domain.Letters
import com.twooooooh.interpreter.ui.KeyChip
import com.twooooooh.interpreter.ui.LanguageChips
import com.twooooooh.interpreter.ui.ScreenTitle
import com.twooooooh.interpreter.ui.theme.KeyTextStyle

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val keymap by viewModel.keymap.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }

    val letters = if (viewModel.showUpper) Letters.UPPER else Letters.LOWER

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        ScreenTitle("Key settings") {
            Text(
                text = if (keymap.isCustom(language)) "CUSTOM" else "DEFAULT",
                style = MaterialTheme.typography.labelMedium,
                color = if (keymap.isCustom(language)) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LanguageChips(selected = language, onSelect = viewModel::selectLanguage)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !viewModel.showUpper,
                onClick = { viewModel.showUpperCase(false) },
                label = { Text("a – z") },
            )
            FilterChip(
                selected = viewModel.showUpper,
                onClick = { viewModel.showUpperCase(true) },
                label = { Text("A – Z") },
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { confirmReset = true }, enabled = keymap.isCustom(language)) {
                Text("Reset")
            }
        }

        Text(
            text = "Tap a letter to choose its keys. A letter can have several keys; all of them decode. " +
                "The first (green) key is used when encoding.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(letters, key = { it }) { letter ->
                LetterRow(
                    letter = letter,
                    keys = keymap.keysFor(language, letter),
                    onClick = { viewModel.openEditor(letter) },
                )
            }
        }
    }

    val editing = viewModel.editingLetter
    if (editing != null) {
        KeyEditorSheet(
            language = language,
            letter = editing,
            keys = keymap.keysFor(language, editing),
            viewModel = viewModel,
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset ${language.displayName}?") },
            text = { Text("All your key changes for ${language.displayName} will be removed and the built-in keys come back.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetLanguage()
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LetterRow(letter: Char, keys: List<String>, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter.toString(),
                    style = KeyTextStyle.copy(fontSize = 22.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (keys.isEmpty()) {
                    Text(
                        text = "No key",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                keys.forEachIndexed { index, key -> KeyChip(text = key, primary = index == 0) }
            }
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit keys of $letter",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyEditorSheet(
    language: CipherLanguage,
    letter: Char,
    keys: List<String>,
    viewModel: SettingsViewModel,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newKey by remember(letter) { mutableStateOf("") }
    val message = viewModel.message

    fun submit() {
        if (viewModel.addKey(newKey)) newKey = ""
    }

    ModalBottomSheet(
        onDismissRequest = viewModel::closeEditor,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Keys for", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(language.displayName, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = letter.toString(),
                    style = KeyTextStyle.copy(fontSize = 40.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            OutlinedTextField(
                value = newKey,
                onValueChange = { newKey = viewModel.sanitize(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("New key (max ${KeymapRules.MAX_KEY_CHARS} characters)") },
                textStyle = KeyTextStyle,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailingIcon = {
                    TextButton(onClick = { submit() }, enabled = newKey.isNotEmpty()) { Text("Add") }
                },
            )

            Text(
                text = message?.text
                    ?: "A key that another letter already uses moves here. Up to ${KeymapRules.MAX_KEYS} keys per letter.",
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    message == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    message.isError -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.tertiary
                },
            )

            if (keys.isEmpty()) {
                Text(
                    text = "No keys — this letter will not decode.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            keys.forEachIndexed { index, key ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = key,
                            style = KeyTextStyle.copy(fontSize = 20.sp),
                            color = if (index == 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (index == 0) {
                            Text(
                                text = "PRIMARY",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        } else {
                            TextButton(onClick = { viewModel.makePrimary(index) }) { Text("Make primary") }
                        }
                        IconButton(onClick = { viewModel.removeKey(index) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete key",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::clearLetter,
                    enabled = keys.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Clear all") }
                Button(onClick = viewModel::closeEditor, modifier = Modifier.weight(1f)) { Text("Done") }
            }
        }
    }
}
