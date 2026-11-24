package com.twooooooh.interpreter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.twooooooh.interpreter.ui.interpreter.InterpreterScreen
import com.twooooooh.interpreter.ui.saves.SavesScreen
import com.twooooooh.interpreter.ui.settings.SettingsScreen

private enum class Destination(val label: String, val icon: ImageVector) {
    INTERPRETER("Interpreter", Icons.Default.Edit),
    SAVES("Saves", Icons.AutoMirrored.Filled.List),
    SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
fun MainScreen() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { inner ->
        Box(
            Modifier
                .padding(inner)
                .consumeWindowInsets(inner)
                .imePadding(),
        ) {
            when (Destination.entries[selected]) {
                Destination.INTERPRETER -> InterpreterScreen(snackbar)
                Destination.SAVES -> SavesScreen()
                Destination.SETTINGS -> SettingsScreen()
            }
        }
    }
}
