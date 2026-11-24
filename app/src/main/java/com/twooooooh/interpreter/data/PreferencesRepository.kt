package com.twooooooh.interpreter.data

import android.content.Context
import com.twooooooh.interpreter.domain.CipherLanguage
import com.twooooooh.interpreter.domain.Mode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Remembers the selected language and mode between launches. */
class PreferencesRepository(context: Context) {
    private val prefs = context.getSharedPreferences("interpreter_prefs", Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(CipherLanguage.fromId(prefs.getString(KEY_LANGUAGE, null)))
    val language: StateFlow<CipherLanguage> = _language.asStateFlow()

    private val _mode = MutableStateFlow(Mode.fromName(prefs.getString(KEY_MODE, null)))
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    /** Tree URI (as text) of the folder the user picked for saved sessions, or null if none yet. */
    private val _saveFolder = MutableStateFlow(prefs.getString(KEY_SAVE_FOLDER, null))
    val saveFolder: StateFlow<String?> = _saveFolder.asStateFlow()

    fun setSaveFolder(value: String?) {
        _saveFolder.value = value
        prefs.edit().putString(KEY_SAVE_FOLDER, value).apply()
    }

    fun setLanguage(value: CipherLanguage) {
        _language.value = value
        prefs.edit().putString(KEY_LANGUAGE, value.id).apply()
    }

    fun setMode(value: Mode) {
        _mode.value = value
        prefs.edit().putString(KEY_MODE, value.name).apply()
    }

    private companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_MODE = "mode"
        const val KEY_SAVE_FOLDER = "save_folder"
    }
}
