package com.twooooooh.interpreter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twooooooh.interpreter.domain.CipherLanguage
import com.twooooooh.interpreter.ui.theme.KeyTextStyle

@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        trailing()
    }
}

@Composable
fun LanguageChips(
    selected: CipherLanguage,
    onSelect: (CipherLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (language in CipherLanguage.entries) {
            FilterChip(
                selected = language == selected,
                onClick = { onSelect(language) },
                label = { Text(language.displayName) },
            )
        }
    }
}

/** A key shown as a small rounded chip. The primary key (used for encoding) is highlighted. */
@Composable
fun KeyChip(text: String, primary: Boolean, modifier: Modifier = Modifier) {
    val container = if (primary) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val content = if (primary) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .background(container, MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = KeyTextStyle, color = content)
    }
}
