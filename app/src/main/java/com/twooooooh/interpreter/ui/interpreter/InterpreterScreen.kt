package com.twooooooh.interpreter.ui.interpreter

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
private const val VIEW_WORDS = -4    // Interpret: word by word, other meanings of a key faded under the letter
private const val VIEW_NORMAL = -3   // Interpret: plain decoded text
private const val VIEW_ALL = -2
private const val VIEW_MIXED = -1

private data class ViewChip(val view: Int, val label: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterpreterScreen(
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: InterpreterViewModel = viewModel(),
) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val keymap by viewModel.keymap.collectAsStateWithLifecycle()
    val split by viewModel.splitLayout.collectAsStateWithLifecycle()
    val interpret = mode == Mode.INTERPRET
    val imeVisible = WindowInsets.isImeVisible

    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val input = field.text
    val tables = remember(keymap, language) { keymap.tables(language) }
    val textStyle = if (split) CipherTextStyle.copy(fontSize = 16.sp, lineHeight = 24.sp) else CipherTextStyle

    // Interpret: normal text + the message written in each key style. Reverse: one output per key.
    val keyOutputs = remember(input, mode, tables) {
        if (interpret) Interpreter.allTranscodes(input, tables) else Interpreter.allVariants(input, tables)
    }
    val normalText = remember(input, mode, tables) { if (interpret) Interpreter.decode(input, tables) else "" }
    val multi = keyOutputs.size > 1
    // Does any key of the input stand for more than one letter?
    val ambiguous = remember(input, mode, tables) { interpret && Interpreter.hasAmbiguity(input, tables) }

    var selection by rememberSaveable(mode) { mutableStateOf(if (interpret) VIEW_WORDS else 0) }
    var mixSeed by rememberSaveable { mutableStateOf(1L) }

    val chips = remember(multi, ambiguous, interpret, keyOutputs.size) {
        buildList<ViewChip> {
            if (interpret && ambiguous) add(ViewChip(VIEW_WORDS, "Words"))
            if (interpret && (ambiguous || multi)) add(ViewChip(VIEW_NORMAL, "Normal"))
            if (multi) {
                for (i in keyOutputs.indices) add(ViewChip(i, "Key ${i + 1}"))
                add(ViewChip(VIEW_MIXED, "Mixed"))
                add(ViewChip(VIEW_ALL, "All"))
            }
        }
    }
    val view = when {
        chips.isEmpty() -> if (interpret) VIEW_NORMAL else 0
        chips.any { it.view == selection } -> selection
        else -> chips.first().view
    }

    val wordLayout = remember(input, tables, view, interpret) {
        if (interpret && view == VIEW_WORDS) Interpreter.readWords(input, tables) else null
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
        VIEW_WORDS, VIEW_NORMAL -> normalText
        VIEW_MIXED -> mixed
        VIEW_ALL -> cards.joinToString("\n\n") { it.second }
        else -> keyOutputs.getOrElse(view) { "" }
    }

    val outputLabel = when {
        view == VIEW_WORDS -> "OUTPUT  ·  WORDS"
        view == VIEW_NORMAL -> "OUTPUT  ·  NORMAL TEXT"
        view == VIEW_ALL -> "OUTPUT  ·  ALL ${cards.size}"
        view == VIEW_MIXED -> "OUTPUT  ·  MIXED KEYS"
        interpret -> "OUTPUT  ·  KEY ${view + 1} STYLE"
        else -> "OUTPUT  ·  ENCODED"
    }
    val outputDetail = buildString {
        append(output.length).append(" chars")
        if (!interpret && multi && view >= 0) append("  ·  key ${view + 1} of ${keyOutputs.size}")
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

    // ---- the two panes (used by both layouts) ------------------------------------------------
    val inputPane: @Composable (Modifier, Boolean) -> Unit = { paneModifier, fill ->
        InputPanel(
            interpret = interpret,
            value = field,
            onValueChange = { field = it },
            tables = tables,
            language = language,
            keymap = keymap,
            textStyle = textStyle,
            fill = fill,
            showPalette = !split && interpret,
            onPaste = { clipboard.getText()?.text?.let { insertText(it) } },
            onClear = { clearWithUndo() },
            onInsert = { insertText(it) },
            onBackspace = { backspace() },
            modifier = paneModifier,
        )
    }
    val outputPane: @Composable (Modifier, Boolean) -> Unit = { paneModifier, fill ->
        OutputPanel(
            label = outputLabel,
            detail = outputDetail,
            chips = chips,
            view = view,
            onSelectView = { selection = it },
            output = output,
            wordLayout = wordLayout,
            cards = cards,
            textStyle = textStyle,
            fill = fill,
            canUseAsInput = output.isNotEmpty() && view != VIEW_ALL,
            onCopy = { text, message ->
                clipboard.setText(AnnotatedString(text))
                scope.launch { snackbar.showSnackbar(message) }
            },
            onReroll = { mixSeed += 1 },
            onUseAsInput = {
                val text = output
                // decoded normal text goes back in as Reverse input; encoded output goes in as Interpret input
                val decodedView = interpret && (view == VIEW_NORMAL || view == VIEW_WORDS)
                viewModel.setMode(if (decodedView) Mode.REVERSE else Mode.INTERPRET)
                field = TextFieldValue(text, TextRange(text.length))
            },
            modifier = paneModifier,
        )
    }
    val layoutToggle: @Composable () -> Unit = {
        TextButton(onClick = { viewModel.setSplitLayout(!split) }) { Text(if (split) "Stacked" else "Side by side") }
    }
    val saveButton: @Composable () -> Unit = {
        Button(
            onClick = { scope.launch { saveNow { folderLauncher.launch(null) } } },
            enabled = input.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save session") }
    }

    if (split) {
        // Input on the left, output on the right: the panes fill the screen, each scrolls inside.
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScreenTitle("Interpreter", trailing = layoutToggle)

            if (!imeVisible) {
                LanguageChips(selected = language, onSelect = viewModel::setLanguage)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = interpret,
                        onClick = { viewModel.setMode(Mode.INTERPRET) },
                        label = { Text("Interpret  ·  encoded → normal") },
                    )
                    FilterChip(
                        selected = !interpret,
                        onClick = { viewModel.setMode(Mode.REVERSE) },
                        label = { Text("Reverse") },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                inputPane(Modifier.weight(1f).fillMaxHeight(), true)
                outputPane(Modifier.weight(1f).fillMaxHeight(), true)
            }

            if (interpret) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    KeyPalette(
                        language = language,
                        keymap = keymap,
                        onInsert = { insertText(it) },
                        onBackspace = { backspace() },
                    )
                }
            }

            if (!imeVisible) {
                saveButton()
                Spacer(Modifier.height(4.dp))
            }
        }
    } else {
        // Stacked: the whole screen scrolls, so the keyboard never squeezes the panels.
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenTitle("Interpreter", trailing = layoutToggle)

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

            inputPane(Modifier.fillMaxWidth(), false)
            outputPane(Modifier.fillMaxWidth(), false)
            saveButton()
            Spacer(Modifier.height(8.dp))
        }
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
    textStyle: TextStyle,
    fill: Boolean,
    showPalette: Boolean,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onInsert: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
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
        modifier = modifier,
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
                modifier = (if (fill) Modifier.weight(1f) else Modifier.heightIn(min = 120.dp, max = 260.dp))
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        focusRequester.requestFocus()
                        keyboard?.show()
                    }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
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
                                    text = if (interpret) "Type with the keys below, or paste encoded text…" else "Type normal text…",
                                    style = textStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        }
                    },
                )
            }

            if (showPalette) {
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
    detail: String,
    chips: List<ViewChip>,
    view: Int,
    onSelectView: (Int) -> Unit,
    output: String,
    wordLayout: Interpreter.WordLayout?,
    cards: List<Pair<String, String>>,
    textStyle: TextStyle,
    fill: Boolean,
    canUseAsInput: Boolean,
    onCopy: (text: String, message: String) -> Unit,
    onReroll: () -> Unit,
    onUseAsInput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Column(Modifier.padding(bottom = 8.dp)) {
            PanelHeader(label = label, detail = detail) {
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
                        .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
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

            val areaModifier = (if (fill) Modifier.weight(1f) else Modifier.heightIn(min = 120.dp, max = 360.dp))
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)

            when {
                view == VIEW_WORDS && wordLayout != null -> {
                    InterlinearView(layout = wordLayout, textStyle = textStyle, modifier = areaModifier)
                }
                view == VIEW_ALL -> {
                    Column(modifier = areaModifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for ((title, text) in cards) {
                            OutputCard(
                                title = title,
                                text = text,
                                textStyle = textStyle,
                                onCopy = { onCopy(text, "Copied ${title.lowercase()}") },
                            )
                        }
                    }
                }
                else -> {
                    Box(modifier = areaModifier) {
                        SelectionContainer {
                            Text(
                                text = output,
                                style = textStyle,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Word by word. Every letter shows what its key means first; when a key can also mean other
 * letters, those are listed under it, faded (three rows are visible, more scroll).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterlinearView(
    layout: Interpreter.WordLayout,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Faded letters: other things the same key can mean",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        )
        for (line in layout.lines) {
            if (line.isEmpty()) {
                Spacer(Modifier.height(6.dp))
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (word in line) WordView(word = word, textStyle = textStyle)
                }
            }
        }
        if (layout.truncated) {
            Text(
                text = "Only the first words are shown here. Use Normal for the whole text.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WordView(word: Interpreter.WordReading, textStyle: TextStyle) {
    if (!word.hasAlternates) {
        Text(text = word.decoded, style = textStyle, color = MaterialTheme.colorScheme.tertiary)
    } else {
        Row {
            for (cell in word.cells) LetterCell(cell = cell, textStyle = textStyle)
        }
    }
}

@Composable
private fun LetterCell(cell: Interpreter.Cell, textStyle: TextStyle) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = cell.text,
            style = textStyle,
            color = if (cell.unknown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
        )
        if (cell.alternates.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .heightIn(max = 66.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                for (alternate in cell.alternates) {
                    Text(
                        text = alternate,
                        style = KeyTextStyle.copy(fontSize = 13.sp, lineHeight = 20.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                }
            }
        }
    }
}

/** One output with its own copy button (used by the "All" view). */
@Composable
private fun OutputCard(title: String, text: String, textStyle: TextStyle, onCopy: () -> Unit) {
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
                    style = textStyle,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}

/** Label and counter on top, the actions underneath (they scroll sideways in a narrow pane). */
@Composable
private fun PanelHeader(
    label: String,
    detail: String,
    detailIsWarning: Boolean = false,
    actions: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = if (detailIsWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}
