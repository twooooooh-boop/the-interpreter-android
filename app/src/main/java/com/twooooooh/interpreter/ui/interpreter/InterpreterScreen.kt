package com.twooooooh.interpreter.ui.interpreter

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twooooooh.interpreter.domain.CipherLanguage
import com.twooooooh.interpreter.domain.CipherTables
import com.twooooooh.interpreter.domain.Interpreter
import com.twooooooh.interpreter.domain.KeymapState
import com.twooooooh.interpreter.domain.Letters
import com.twooooooh.interpreter.domain.Mode
import com.twooooooh.interpreter.ui.LanguageChips
import com.twooooooh.interpreter.ui.ScreenTitle
import com.twooooooh.interpreter.ui.theme.CipherTextStyle
import com.twooooooh.interpreter.ui.theme.KeyTextStyle
import kotlinx.coroutines.launch

// Which output is shown: a key index (0, 1, 2 …) or one of these special views.
private const val VIEW_NORMAL = -3
private const val VIEW_ALL = -2
private const val VIEW_MIXED = -1

private data class ViewChip(val view: Int, val label: String)

@Composable
fun InterpreterScreen(
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: InterpreterViewModel = viewModel(),
) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val keymap by viewModel.keymap.collectAsStateWithLifecycle()
    val interpret = mode == Mode.INTERPRET

    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val input = field.text
    val tables = remember(keymap, language) { keymap.tables(language) }

    // Interpret: normal text + the message written in each key style. Reverse: one output per key.
    val keyOutputs = remember(input, mode, tables) {
        if (interpret) Interpreter.allTranscodes(input, tables) else Interpreter.allVariants(input, tables)
    }
    val normalText = remember(input, mode, tables) { if (interpret) Interpreter.decode(input, tables) else "" }
    val multi = keyOutputs.size > 1

    var selection by rememberSaveable(mode) { mutableStateOf(if (interpret) VIEW_NORMAL else 0) }
    var mixSeed by rememberSaveable { mutableStateOf(1L) }
    val defaultView = if (interpret) VIEW_NORMAL else 0
    val view = when {
        !multi -> defaultView
        selection >= keyOutputs.size -> defaultView
        selection == VIEW_NORMAL && !interpret -> 0
        else -> selection
    }

    val mixed = remember(input, tables, mixSeed, multi, interpret) {
        when {
            !multi -> ""
            interpret -> Interpreter.transcodeMixed(input, tables, mixSeed)
            else -> Interpreter.encodeMixed(input, tables, mixSeed)
        }
    }
    val cards = remember(normalText, keyOutputs, interpret) {
        buildList<Pair<String, String>> {
            if (interpret) add("NORMAL TEXT" to normalText)
            keyOutputs.forEachIndexed { i, text -> add("KEY ${i + 1}" to text) }
        }
    }
    val output = when (view) {
        VIEW_NORMAL -> normalText
        VIEW_MIXED -> mixed
        VIEW_ALL -> cards.joinToString("\n\n") { it.second }
        else -> keyOutputs.getOrElse(view) { "" }
    }

    val chips = remember(multi, interpret, keyOutputs.size) {
        if (!multi) emptyList<ViewChip>() else buildList<ViewChip> {
            if (interpret) add(ViewChip(VIEW_NORMAL, "Normal"))
            for (i in keyOutputs.indices) add(ViewChip(i, "Key ${i + 1}"))
            add(ViewChip(VIEW_MIXED, "Mixed"))
            add(ViewChip(VIEW_ALL, "All"))
        }
    }
    val outputLabel = when {
        view == VIEW_NORMAL -> "OUTPUT  ·  NORMAL TEXT"
        view == VIEW_ALL -> "OUTPUT  ·  ALL ${cards.size}"
        view == VIEW_MIXED -> "OUTPUT  ·  MIXED KEYS"
        interpret -> "OUTPUT  ·  KEY ${view + 1} STYLE"
        multi -> "OUTPUT  ·  ENCODED  ·  KEY ${view + 1} OF ${keyOutputs.size}"
        else -> "OUTPUT  ·  ENCODED"
    }

    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // ---- editing helpers (cursor aware) ------------------------------------------------------
    fun insertText(text: String) {
        val start = minOf(field.selection.start, field.selection.end)
        val end = maxOf(field.selection.start, field.selection.end)
        field = TextFieldValue(field.text.replaceRange(start, end, text), TextRange(start + text.length))
    }

    fun backspace() {
        val start = minOf(field.selection.start, field.selection.end)
        val end = maxOf(field.selection.start, field.selection.end)
        val text = field.text
        if (start != end) {
            field = TextFieldValue(text.removeRange(start, end), TextRange(start))
        } else if (start > 0) {
            val cut = if (start >= 2 && Character.isLowSurrogate(text[start - 1]) && Character.isHighSurrogate(text[start - 2])) 2 else 1
            field = TextFieldValue(text.removeRange(start - cut, start), TextRange(start - cut))
        }
    }

    fun clearWithUndo() {
        val previous = field
        field = TextFieldValue("")
        scope.launch {
            val result = snackbar.showSnackbar("Input cleared", actionLabel = "Undo")
            if (result == SnackbarResult.ActionPerformed) field = previous
        }
    }

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

    // ---- layout: the whole screen scrolls, so the keyboard never squeezes the panels ---------
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Interpreter")

        LanguageChips(selected = language, onSelect = viewModel::setLanguage)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = interpret,
                    onClick = { viewModel.setMode(Mode.INTERPRET) },
                    label = { Text("Interpret") },
                )
                FilterChip(
                    selected = !interpret,
                    onClick = { viewModel.setMode(Mode.REVERSE) },
                    label = { Text("Reverse") },
                )
            }
            Text(
                text = if (interpret) "Encoded  →  normal text" else "Normal text  →  encoded",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        InputPanel(
            interpret = interpret,
            value = field,
            onValueChange = { field = it },
            tables = tables,
            language = language,
            keymap = keymap,
            onPaste = { clipboard.getText()?.text?.let { insertText(it) } },
            onClear = { clearWithUndo() },
            onInsert = { insertText(it) },
            onBackspace = { backspace() },
        )

        OutputPanel(
            label = outputLabel,
            chips = chips,
            view = view,
            onSelectView = { selection = it },
            output = output,
            cards = cards,
            canUseAsInput = output.isNotEmpty() && view != VIEW_ALL,
            onCopy = { text, message ->
                clipboard.setText(AnnotatedString(text))
                scope.launch { snackbar.showSnackbar(message) }
            },
            onReroll = { mixSeed += 1 },
            onUseAsInput = {
                val text = output
                // decoded normal text goes back in as Reverse input; encoded output goes in as Interpret input
                viewModel.setMode(if (interpret && view == VIEW_NORMAL) Mode.REVERSE else Mode.INTERPRET)
                field = TextFieldValue(text, TextRange(text.length))
            },
        )

        Button(
            onClick = { scope.launch { saveNow { folderLauncher.launch(null) } } },
            enabled = input.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save session") }

        Spacer(Modifier.height(8.dp))
    }
}

// ================================================================================================
// Input
// ================================================================================================

@Composable
private fun InputPanel(
    interpret: Boolean,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    tables: CipherTables,
    language: CipherLanguage,
    keymap: KeymapState,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onInsert: (String) -> Unit,
    onBackspace: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Interpret mode: recognised keys are tinted, symbols that cannot be read are underlined in red.
    val keyColor = MaterialTheme.colorScheme.primary
    val unknownColor = MaterialTheme.colorScheme.error
    val tokens = remember(value.text, tables, interpret) {
        if (interpret) Interpreter.tokenize(value.text, tables) else emptyList<Interpreter.Token>()
    }
    val transformation = remember(value.text, tokens, keyColor, unknownColor) {
        HighlightTransformation(value.text, tokens, keyColor, unknownColor)
    }
    val unknownCount = remember(tokens) { tokens.count { it.kind == Interpreter.TokenKind.UNKNOWN } }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            PanelHeader(
                label = if (interpret) "INPUT  ·  ENCODED" else "INPUT  ·  NORMAL TEXT",
                detail = buildString {
                    append(value.text.length).append(" chars")
                    if (unknownCount > 0) append("  ·  ").append(unknownCount).append(" unknown")
                },
                detailIsWarning = unknownCount > 0,
            ) {
                TextButton(onClick = onPaste, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Paste") }
                TextButton(
                    onClick = onClear,
                    enabled = value.text.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) { Text("Clear") }
            }

            // The whole area is tappable, not just the text.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 260.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        focusRequester.requestFocus()
                        keyboard?.show()
                    }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = CipherTextStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
                    visualTransformation = transformation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth()) {
                            if (value.text.isEmpty()) {
                                Text(
                                    text = if (interpret) "Type with the key bar below, or paste encoded text…" else "Type normal text…",
                                    style = CipherTextStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        }
                    },
                )
            }

            if (interpret) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                KeyPalette(
                    language = language,
                    keymap = keymap,
                    onInsert = onInsert,
                    onBackspace = onBackspace,
                )
            }
        }
    }
}

/** Colours recognised keys and underlines unknown symbols; does not change the text or the cursor. */
private class HighlightTransformation(
    private val source: String,
    private val tokens: List<Interpreter.Token>,
    private val keyColor: Color,
    private val unknownColor: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (tokens.isEmpty() || text.text != source) return TransformedText(text, OffsetMapping.Identity)
        val styled = buildAnnotatedString {
            append(text.text)
            for (t in tokens) {
                when (t.kind) {
                    Interpreter.TokenKind.KEY -> addStyle(SpanStyle(color = keyColor), t.start, t.end)
                    Interpreter.TokenKind.UNKNOWN -> addStyle(
                        SpanStyle(color = unknownColor, textDecoration = TextDecoration.Underline),
                        t.start,
                        t.end,
                    )
                    Interpreter.TokenKind.PASS -> Unit
                }
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

/** Tap-to-type bar with every key of the current map (so no symbol keyboard is needed). */
@Composable
private fun KeyPalette(
    language: CipherLanguage,
    keymap: KeymapState,
    onInsert: (String) -> Unit,
    onBackspace: () -> Unit,
) {
    var upper by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(true) }
    val haptic = LocalHapticFeedback.current

    Column(Modifier.padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "KEYS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilterChip(selected = !upper, onClick = { upper = false }, label = { Text("a – z") })
            FilterChip(selected = upper, onClick = { upper = true }, label = { Text("A – Z") })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "Show") }
        }
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (letter in if (upper) Letters.UPPER else Letters.LOWER) {
                        for (key in keymap.keysFor(language, letter)) {
                            KeyButton(key = key, letter = letter) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onInsert(key)
                            }
                        }
                    }
                }
                TextButton(onClick = { onInsert(" ") }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Space") }
                TextButton(onClick = onBackspace, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Del") }
            }
        }
    }
}

@Composable
private fun KeyButton(key: String, letter: Char, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.widthIn(min = 40.dp),
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = key, style = KeyTextStyle, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ================================================================================================
// Output
// ================================================================================================

@Composable
private fun OutputPanel(
    label: String,
    chips: List<ViewChip>,
    view: Int,
    onSelectView: (Int) -> Unit,
    output: String,
    cards: List<Pair<String, String>>,
    canUseAsInput: Boolean,
    onCopy: (text: String, message: String) -> Unit,
    onReroll: () -> Unit,
    onUseAsInput: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(bottom = 12.dp)) {
            PanelHeader(label = label, detail = "${output.length} chars") {
                if (view == VIEW_MIXED) {
                    TextButton(onClick = onReroll, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Reroll") }
                }
                TextButton(
                    onClick = onUseAsInput,
                    enabled = canUseAsInput,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) { Text("Use as input") }
                TextButton(
                    onClick = {
                        if (view == VIEW_ALL) onCopy(output, "Copied all ${cards.size} outputs") else onCopy(output, "Copied")
                    },
                    enabled = output.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) { Text(if (view == VIEW_ALL) "Copy all" else "Copy") }
            }

            if (chips.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (chip in chips) {
                        FilterChip(
                            selected = view == chip.view,
                            onClick = { onSelectView(chip.view) },
                            label = { Text(chip.label) },
                        )
                    }
                }
            }

            if (view == VIEW_ALL) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for ((title, text) in cards) {
                        OutputCard(title = title, text = text, onCopy = { onCopy(text, "Copied ${title.lowercase()}") })
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    SelectionContainer {
                        Text(
                            text = output,
                            style = CipherTextStyle,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** One output with its own copy button (used by the "All" view). */
@Composable
private fun OutputCard(title: String, text: String, onCopy: () -> Unit) {
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

/** Small label on the left, a counter in the middle and actions on the right. */
@Composable
private fun PanelHeader(
    label: String,
    detail: String,
    detailIsWarning: Boolean = false,
    actions: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = if (detailIsWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        actions()
    }
}
