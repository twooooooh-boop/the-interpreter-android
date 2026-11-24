package com.twooooooh.interpreter

import android.app.Application
import com.twooooooh.interpreter.data.KeymapRepository
import com.twooooooh.interpreter.data.PreferencesRepository
import com.twooooooh.interpreter.data.SavesRepository
import java.io.File

/** Application class: owns the repositories (a tiny manual dependency container). */
class InterpreterApp : Application() {

    lateinit var preferences: PreferencesRepository
        private set
    lateinit var keymaps: KeymapRepository
        private set
    lateinit var saves: SavesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = PreferencesRepository(this)
        keymaps = KeymapRepository(File(filesDir, "keymap.dat"))
        saves = SavesRepository(filesDir)
    }
}
