package com.twooooooh.interpreter.ui.saves

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.twooooooh.interpreter.InterpreterApp
import java.io.File

class SavesViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as InterpreterApp).saves

    var currentDir by mutableStateOf(repo.root)
        private set
    var entries by mutableStateOf(repo.list(repo.root))
        private set
    var openedFile by mutableStateOf<File?>(null)
        private set
    var openedText by mutableStateOf("")
        private set

    val atRoot: Boolean get() = currentDir == repo.root

    fun refresh() {
        if (!currentDir.exists()) currentDir = repo.root
        entries = repo.list(currentDir)
    }

    fun open(file: File) {
        if (file.isDirectory) {
            currentDir = file
            refresh()
        } else {
            openedText = repo.readText(file)
            openedFile = file
        }
    }

    fun closeFile() {
        openedFile = null
        openedText = ""
    }

    /** Goes one folder up. @return false when already at the top. */
    fun up(): Boolean {
        if (atRoot) return false
        currentDir = currentDir.parentFile ?: repo.root
        refresh()
        return true
    }

    fun delete(file: File) {
        repo.delete(file)
        refresh()
    }
}
