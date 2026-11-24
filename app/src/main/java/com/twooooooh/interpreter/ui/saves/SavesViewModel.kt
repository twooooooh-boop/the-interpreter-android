package com.twooooooh.interpreter.ui.saves

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.twooooooh.interpreter.InterpreterApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Browses the folder the user picked for saved sessions. */
class SavesViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as InterpreterApp).saves

    val folderUri: StateFlow<String?> = repo.folderUri

    /** Folders we walked into, from the saves root down to the current folder. */
    private val stack = mutableStateListOf<DocumentFile>()

    var entries by mutableStateOf<List<DocumentFile>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var pickedFolderName by mutableStateOf<String?>(null)
        private set
    var openedName by mutableStateOf<String?>(null)
        private set
    var openedText by mutableStateOf("")
        private set

    val atRoot: Boolean get() = stack.isEmpty()
    val currentName: String get() = stack.lastOrNull()?.name ?: "Saves"

    fun setFolder(uri: Uri) {
        repo.setFolder(uri)
        stack.clear()
        refresh()
    }

    private class Listing(
        val hasRoot: Boolean,
        val entries: List<DocumentFile>,
        val folderName: String?,
    )

    fun refresh() {
        viewModelScope.launch {
            loading = true
            val current = stack.lastOrNull()
            val listing = withContext(Dispatchers.IO) {
                val root = repo.root()
                if (root == null) {
                    Listing(false, emptyList(), repo.pickedFolderName())
                } else {
                    val dir = if (current != null && current.exists()) current else root
                    Listing(true, repo.list(dir), repo.pickedFolderName())
                }
            }
            if (!listing.hasRoot) stack.clear()
            entries = listing.entries
            pickedFolderName = listing.folderName
            loading = false
        }
    }

    fun open(file: DocumentFile) {
        if (file.isDirectory) {
            stack.add(file)
            refresh()
        } else {
            viewModelScope.launch {
                val text = withContext(Dispatchers.IO) { repo.readText(file) }
                openedText = text
                openedName = file.name ?: "file"
            }
        }
    }

    fun closeFile() {
        openedName = null
        openedText = ""
    }

    /** Goes one folder up. @return false when already at the top. */
    fun up(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        refresh()
        return true
    }

    fun delete(file: DocumentFile) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(file) }
            refresh()
        }
    }
}
