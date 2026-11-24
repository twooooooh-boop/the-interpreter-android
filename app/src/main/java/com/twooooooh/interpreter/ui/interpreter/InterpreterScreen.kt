package com.twooooooh.interpreter.ui.interpreter

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
    val output = remember(input, mode, tables) { Interpreter.convert(input, mode, tables) }

    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val inputLabel = if (mode == Mode.INTERPRET) "INPUT  ·  ENCODED" else "INPUT  ·  NORMAL TEXT"
    val outputLabel = if (mode == Mode.INTERPRET) "OUTPUT  ·  NORMAL TEXT" else "OUTPUT  ·  ENCODED"

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
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(output))
                        scope.launch { snackbar.showSnackbar("Copied") }
                    },
                    enabled = output.isNotEmpty(),
                ) { Text("Copy") }
            },
        ) {
            SelectionContainer {
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

        Button(
            onClick = {
                scope.launch {
                    val ok = viewModel.save(input)
                    snackbar.showSnackbar(if (ok) "Saved — see the Saves tab" else "Could not save")
                }
            },
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
