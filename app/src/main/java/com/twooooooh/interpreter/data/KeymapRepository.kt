package com.twooooooh.interpreter.data

import com.twooooooh.interpreter.domain.CipherLanguage
import com.twooooooh.interpreter.domain.EditResult
import com.twooooooh.interpreter.domain.KeymapCodec
import com.twooooooh.interpreter.domain.KeymapState
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the user's key edits, exposes them as a [StateFlow] and keeps keymap.dat up to date.
 * Disk writes happen on a single background thread, so they are ordered and never block the UI.
 */
class KeymapRepository(private val file: File) {

    private val _state = MutableStateFlow(load())
    val state: StateFlow<KeymapState> = _state.asStateFlow()

    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "keymap-writer").apply { isDaemon = true }
    }

    fun addKey(language: CipherLanguage, letter: Char, key: String): EditResult = synchronized(this) {
        val result = _state.value.addKey(language, letter, key)
        if (result.ok) commit(result.state)
        result
    }

    fun removeKey(language: CipherLanguage, letter: Char, index: Int) =
        mutate { it.removeKey(language, letter, index) }

    fun makePrimary(language: CipherLanguage, letter: Char, index: Int) =
        mutate { it.makePrimary(language, letter, index) }

    fun clearLetter(language: CipherLanguage, letter: Char) =
        mutate { it.clearLetter(language, letter) }

    fun exportJson(languages: Collection<CipherLanguage>): String =
        com.twooooooh.interpreter.domain.KeymapJson.export(_state.value, languages)

    fun importMaps(maps: Map<CipherLanguage, Map<Char, List<String>>>) =
        mutate { it.importMaps(maps) }

    /** @return true if the language had edits that were thrown away */
    fun resetLanguage(language: CipherLanguage): Boolean = synchronized(this) {
        val before = _state.value
        val after = before.resetLanguage(language)
        if (after != before) commit(after)
        after != before
    }

    private fun mutate(change: (KeymapState) -> KeymapState) = synchronized(this) {
        val after = change(_state.value)
        if (after != _state.value) commit(after)
    }

    private fun commit(newState: KeymapState) {
        _state.value = newState
        val text = KeymapCodec.encode(newState)
        writer.execute {
            runCatching {
                if (text.isEmpty()) {
                    file.delete()
                } else {
                    val tmp = File(file.parentFile, file.name + ".tmp")
                    tmp.writeText(text, Charsets.UTF_8)
                    if (!tmp.renameTo(file)) {
                        file.writeText(text, Charsets.UTF_8)
                        tmp.delete()
                    }
                }
            }
        }
    }

    private fun load(): KeymapState = runCatching {
        if (file.exists()) KeymapCodec.decode(file.readText(Charsets.UTF_8)) else KeymapState()
    }.getOrDefault(KeymapState())
}
