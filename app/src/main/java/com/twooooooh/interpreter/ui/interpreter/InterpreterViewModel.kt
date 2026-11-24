package com.twooooooh.interpreter.ui.interpreter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.twooooooh.interpreter.InterpreterApp
import com.twooooooh.interpreter.domain.CipherLanguage
import com.twooooooh.interpreter.domain.Interpreter
import com.twooooooh.interpreter.domain.Mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class InterpreterViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as InterpreterApp

    val language: StateFlow<CipherLanguage> = app.preferences.language
    val mode: StateFlow<Mode> = app.preferences.mode
    val keymap = app.keymaps.state

    fun setLanguage(value: CipherLanguage) = app.preferences.setLanguage(value)
    fun setMode(value: Mode) = app.preferences.setMode(value)

    /** Writes the current session to the Saves folder. @return true on success. */
    suspend fun save(input: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val lang = language.value
            val m = mode.value
            val tables = keymap.value.tables(lang)
            app.saves.saveSession(lang, m, input, Interpreter.convert(input, m, tables), tables)
        }.isSuccess
    }
}
