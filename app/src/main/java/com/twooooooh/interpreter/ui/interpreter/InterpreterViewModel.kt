package com.twooooooh.interpreter.ui.interpreter

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.twooooooh.interpreter.InterpreterApp
import com.twooooooh.interpreter.data.SavesRepository
import com.twooooooh.interpreter.domain.CipherLanguage
import com.twooooooh.interpreter.domain.Interpreter
import com.twooooooh.interpreter.domain.Mode
import com.twooooooh.interpreter.domain.SessionPlanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

sealed interface SaveOutcome {
    data class Saved(val where: String) : SaveOutcome
    /** No folder picked yet (or access to it was lost): ask the user. */
    data object NeedsFolder : SaveOutcome
    data object Failed : SaveOutcome
}

class InterpreterViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as InterpreterApp

    val language: StateFlow<CipherLanguage> = app.preferences.language
    val mode: StateFlow<Mode> = app.preferences.mode
    val keymap = app.keymaps.state
    val splitLayout: StateFlow<Boolean> = app.preferences.splitLayout

    fun setLanguage(value: CipherLanguage) = app.preferences.setLanguage(value)
    fun setMode(value: Mode) = app.preferences.setMode(value)
    fun setSplitLayout(value: Boolean) = app.preferences.setSplitLayout(value)

    fun setSaveFolder(uri: Uri) = app.saves.setFolder(uri)

    /** Writes the current session into the folder the user picked. */
    suspend fun save(input: String): SaveOutcome = withContext(Dispatchers.IO) {
        val root = app.saves.root() ?: return@withContext SaveOutcome.NeedsFolder
        runCatching {
            val lang = language.value
            val m = mode.value
            val tables = keymap.value.tables(lang)
            val plan = if (m == Mode.REVERSE) {
                val variants = Interpreter.allVariants(input, tables)
                SessionPlanner.plan(lang, m, input, variants[0], tables, extraOutputs = variants.drop(1))
            } else {
                val readings = Interpreter.allReadings(input, tables)
                SessionPlanner.plan(
                    lang, m, input, readings[0], tables,
                    extraOutputs = readings.drop(1),
                    firstExtraKey = 2,
                    extraBaseName = "output(NORMAL)",
                )
            }
            val name = app.saves.write(root, plan)
            if (name != null) SaveOutcome.Saved("${app.saves.pickedFolderName() ?: "folder"}/${SavesRepository.ROOT_NAME}/$name")
            else SaveOutcome.Failed
        }.getOrDefault(SaveOutcome.Failed)
    }
}
