package com.twooooooh.interpreter.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.twooooooh.interpreter.domain.SessionPlan
import java.util.Locale

/**
 * Saved sessions go into a folder the USER picks (Storage Access Framework), not into app data.
 * Inside the picked folder a sub-folder "The Interpreter" is used, so nothing else in that folder
 * is ever listed or deleted by the app.
 */
class SavesRepository(
    private val context: Context,
    private val preferences: PreferencesRepository,
) {
    /** Tree URI of the picked folder (null until the user picks one). */
    val folderUri = preferences.saveFolder

    /** Remembers the picked folder and keeps read/write access to it across restarts. */
    fun setFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        val old = preferences.saveFolder.value
        if (old != null && old != uri.toString()) {
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(old), flags) }
        }
        preferences.setSaveFolder(uri.toString())
    }

    /** The "The Interpreter" folder inside the picked folder, or null if no usable folder is picked. */
    fun root(): DocumentFile? {
        val text = preferences.saveFolder.value ?: return null
        val picked = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(text)) }.getOrNull() ?: return null
        if (!picked.exists() || !picked.canWrite()) return null
        return picked.findFile(ROOT_NAME)?.takeIf { it.isDirectory } ?: picked.createDirectory(ROOT_NAME)
    }

    /** Display name of the picked folder, for the UI. */
    fun pickedFolderName(): String? {
        val text = preferences.saveFolder.value ?: return null
        return runCatching { DocumentFile.fromTreeUri(context, Uri.parse(text))?.name }.getOrNull()
    }

    /** Writes the plan into a new folder under [root]. @return the created folder name, or null on failure. */
    fun write(root: DocumentFile, plan: SessionPlan): String? {
        var name = plan.folderName
        var n = 2
        while (root.findFile(name) != null) name = "${plan.folderName}_${n++}"
        val dir = root.createDirectory(name) ?: return null
        for (file in plan.files) {
            val doc = dir.createFile("text/plain", file.name) ?: return null
            val ok = runCatching {
                context.contentResolver.openOutputStream(doc.uri, "wt")?.use { it.write(file.content.toByteArray(Charsets.UTF_8)) }
            }.isSuccess
            if (!ok) return null
        }
        return name
    }

    /** Folders first (newest first), then files by name. */
    fun list(dir: DocumentFile): List<DocumentFile> {
        val children = dir.listFiles().toList()
        val folders = children.filter { it.isDirectory }.sortedByDescending { it.lastModified() }
        val files = children.filter { it.isFile }.sortedBy { (it.name ?: "").lowercase(Locale.ROOT) }
        return folders + files
    }

    fun readText(file: DocumentFile): String = runCatching {
        context.contentResolver.openInputStream(file.uri)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
    }.getOrDefault("")

    fun delete(file: DocumentFile): Boolean = runCatching { file.delete() }.getOrDefault(false)

    companion object {
        const val ROOT_NAME = "The Interpreter"
    }
}
