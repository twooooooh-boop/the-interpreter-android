package com.twooooooh.interpreter.ui.interpreter

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twooooooh.interpreter.domain.Interpreter
import com.twooooooh.interpreter.domain.Mode
import com.twooooooh.interpreter.ui.LanguageChips
import com.twooooooh.interpreter.ui.ScreenTitle
import com.twooooooh.interpreter.ui.theme.CipherTextStyle
import kotlinx.coroutines.launch

@Composable
fun InterpreterScreen(
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: InterpreterViewModel = viewModel(),
) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val keymap by viewModel.keymap.collectAsStateWithLifecycle()

    var input by rememberSaveable { mutableStateOf("") }
    val tables = remember(keymap, language) { keymap.tables(language) }

    // Reverse mode: one output per key a letter owns (Key 1, Key 2, ...), plus Mixed and All.
    var selection by rememberSaveable { mutableStateOf(0) }          // >= 0 key index, MIXED, ALL
    var mixSeed by rememberSaveable { mutableStateOf(1L) }
    val variants = remember(input, mode, tables) {
        if (mode == Mode.REVERSE) Interpreter.allVariants(input, tables) else emptyList()
    }
    val multi = variants.size > 1
    val view = when {
        !multi -> 0
        selection >= variants.size -> 0
        else -> selection
    }
    val mixed = remember(input, tables, mixSeed, multi) {
        if (multi) Interpreter.encodeMixed(input, tables, mixSeed) else ""
    }
    val output = when {
        mode == Mode.INTERPRET -> Interpreter.decode(input, tables)
        view == VIEW_MIXED -> mixed
        view == VIEW_ALL -> variants.joinToString("\n\n")
        else -> variants.getOrElse(view) { "" }
    }

    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    suspend fun saveNow(onNeedFolder: () -> Unit) {
        when (val outcome = viewModel.save(input)) {
            is SaveOutcome.Saved -> snackbar.showSnackbar("Saved to ${outcome.where}")
            SaveOutcome.NeedsFolder -> onNeedFolder()
            SaveOutcome.Failed -> snackbar.showSnackbar("Could not save. Pick the folder again in the Saves tab.")
        }
    }

    // First save (or lost access): ask where sessions should be stored, then save.
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
            scope.launch { snackbar.showSnackbar("No folder chosen - nothing was saved") }
        } else {
            viewModel.setSaveFolder(uri)
            scope.launch { saveNow(onNeedFolder = {}) }
        }
    }

    val inputLabel = if (mode == Mode.INTERPRET) "INPUT  ·  ENCODED" else "INPUT  ·  NORMAL TEXT"
    val outputLabel = when {
        mode == Mode.INTERPRET -> "OUTPUT  ·  NORMAL TEXT"
        multi -> "OUTPUT  ·  ENCODED  ·  ${variants.size} KEYS"
        else -> "OUTPUT  ·  ENCODED"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenTitle("Interpreter")

        LanguageChips(selected = language, onSelect = viewModel::setLanguage)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == Mode.INTERPRET,
                onClick = { viewModel.setMode(Mode.INTERPRET) },
                label = { Text("Interpret") },
            )
            FilterChip(
                selected = mode == Mode.REVERSE,
                onClick = { viewModel.setMode(Mode.REVERSE) },
                label = { Text("Reverse") },
            )
        }

        TextPanel(
            label = inputLabel,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            actions = {
                TextButton(onClick = { clipboard.getText()?.text?.let { input = it } }) { Text("Paste") }
                TextButton(onClick = { input = "" }, enabled = input.isNotEmpty()) { Text("Clear") }
            },
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                textStyle = CipherTextStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                modifier = Modifier.fillMaxSize(),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize()) {
                        if (input.isEmpty()) {
                            Text(
                                text = if (mode == Mode.INTERPRET) "Paste or type encoded text…" else "Type normal text…",
                                style = CipherTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
        }

        TextPanel(
            label = outputLabel,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            actions = {
                if (view == VIEW_MIXED) {
                    TextButton(onClick = { mixSeed = mixSeed + 1 }) { Text("Reroll") }
                }
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(output))
                        scope.launch { snackbar.showSnackbar(if (view == VIEW_ALL) "Copied all ${variants.size} outputs" else "Copied") }
                    },
                    enabled = output.isNotEmpty(),
                ) { Text(if (view == VIEW_ALL) "Copy all" else "Copy") }
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                if (multi) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        variants.indices.forEach { i ->
                            FilterChip(
                                selected = view == i,
                                onClick = { selection = i },
                                label = { Text("Key ${i + 1}") },
                            )
                        }
                        FilterChip(selected = view == VIEW_MIXED, onClick = { selection = VIEW_MIXED }, label = { Text("Mixed") })
                        FilterChip(selected = view == VIEW_ALL, onClick = { selection = VIEW_ALL }, label = { Text("All") })
                    }
                }
                if (view == VIEW_ALL) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        variants.forEachIndexed { i, text ->
                            VariantCard(
                                title = "KEY ${i + 1}",
                                text = text,
                                onCopy = {
                                    clipboard.setText(AnnotatedString(text))
                                    scope.launch { snackbar.showSnackbar("Copied key ${i + 1}") }
                                },
                            )
                        }
                    }
                } else {
                    SelectionContainer(Modifier.weight(1f)) {
                        Text(
                            text = output,
                            style = CipherTextStyle,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        }

        Button(
            onClick = { scope.launch { saveNow { folderLauncher.launch(null) } } },
            enabled = input.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) { Text("Save session") }
    }
}

/** A rounded card with a small label + actions on top and [content] below. */
@Composable
private fun TextPanel(
    label: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row { actions() }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(end = 8.dp),
            ) { content() }
        }
    }
}

private const val VIEW_MIXED = -1
private const val VIEW_ALL = -2

/** One encoded output with its own copy button (used by the "All" view). */
@Composable
private fun VariantCard(title: String, text: String, onCopy: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onCopy) { Text("Copy") }
            }
            SelectionContainer {
                Text(
                    text = text,
                    style = CipherTextStyle,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}
