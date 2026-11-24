package com.twooooooh.interpreter.ui.settings

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.twooooooh.interpreter.InterpreterApp
import com.twooooooh.interpreter.domain.CipherLanguage
import com.twooooooh.interpreter.domain.KeymapImport
import com.twooooooh.interpreter.domain.KeymapJson
import com.twooooooh.interpreter.domain.KeymapRules
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

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

    /** A parsed import file waiting for the user to tick which maps to apply. */
    var pendingImport by mutableStateOf<KeymapImport.Success?>(null)
        private set

    /** Writes the ticked maps as JSON to [uri]. */
    suspend fun writeExport(uri: Uri, languages: Set<CipherLanguage>): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = app.keymaps.exportJson(languages).toByteArray(Charsets.UTF_8)
            app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
        }.isSuccess
    }

    /** Reads and checks a JSON file. A good result is kept in [pendingImport]. */
    suspend fun readImport(uri: Uri): KeymapImport = withContext(Dispatchers.IO) {
        val result: KeymapImport = runCatching<KeymapImport> {
            val out = ByteArrayOutputStream()
            app.contentResolver.openInputStream(uri)!!.use { input ->
                val chunk = ByteArray(8192)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    out.write(chunk, 0, n)
                    if (out.size() > MAX_IMPORT_BYTES) return@runCatching KeymapImport.Failure("This file is too large")
                }
            }
            KeymapJson.parse(out.toString(Charsets.UTF_8.name()))
        }.getOrElse { KeymapImport.Failure("Could not read the file") }
        result
    }

    fun offerImport(result: KeymapImport.Success) {
        pendingImport = result
    }

    fun applyImport(selected: Set<CipherLanguage>) {
        val pending = pendingImport ?: return
        app.keymaps.importMaps(pending.maps.filterKeys { it in selected })
        pendingImport = null
        closeEditor()
    }

    fun dismissImport() {
        pendingImport = null
    }

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

    private companion object {
        const val MAX_IMPORT_BYTES = 2_000_000
    }
}
