package com.twooooooh.interpreter.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.twooooooh.interpreter.InterpreterApp
import com.twooooooh.interpreter.domain.CipherLanguage
import com.twooooooh.interpreter.domain.KeymapRules
import kotlinx.coroutines.flow.StateFlow

data class FeedbackMessage(val text: String, val isError: Boolean)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as InterpreterApp

    val language: StateFlow<CipherLanguage> = app.preferences.language
    val keymap = app.keymaps.state

    var showUpper by mutableStateOf(false)
        private set
    var editingLetter by mutableStateOf<Char?>(null)
        private set
    var message by mutableStateOf<FeedbackMessage?>(null)
        private set

    fun selectLanguage(value: CipherLanguage) {
        app.preferences.setLanguage(value)
        closeEditor()
    }

    fun showUpperCase(value: Boolean) {
        showUpper = value
    }

    fun openEditor(letter: Char) {
        message = null
        editingLetter = letter
    }

    fun closeEditor() {
        editingLetter = null
        message = null
    }

    /** @return true if the key was added (the caller clears its text field). */
    fun addKey(key: String): Boolean {
        val letter = editingLetter ?: return false
        val result = app.keymaps.addKey(language.value, letter, key)
        message = FeedbackMessage(result.message, isError = !result.ok)
        return result.ok
    }

    fun removeKey(index: Int) {
        val letter = editingLetter ?: return
        app.keymaps.removeKey(language.value, letter, index)
        message = FeedbackMessage("Removed", isError = false)
    }

    fun makePrimary(index: Int) {
        val letter = editingLetter ?: return
        app.keymaps.makePrimary(language.value, letter, index)
        message = FeedbackMessage("Now primary — used when encoding", isError = false)
    }

    fun clearLetter() {
        val letter = editingLetter ?: return
        app.keymaps.clearLetter(language.value, letter)
        message = FeedbackMessage("All keys removed", isError = false)
    }

    fun resetLanguage(): Boolean {
        closeEditor()
        return app.keymaps.resetLanguage(language.value)
    }

    /** Removes spaces / control characters and cuts the text to the maximum key length. */
    fun sanitize(raw: String): String {
        val cleaned = raw.filter { it.code >= 32 && it.code != 127 && it != ' ' }
        var count = 0
        var end = 0
        while (end < cleaned.length && count < KeymapRules.MAX_KEY_CHARS) {
            end += Character.charCount(cleaned.codePointAt(end))
            count++
        }
        return cleaned.substring(0, end)
    }
}
