package com.twooooooh.interpreter.data

import com.twooooooh.interpreter.domain.CipherLanguage
import com.twooooooh.interpreter.domain.CipherTables
import com.twooooooh.interpreter.domain.Interpreter
import com.twooooooh.interpreter.domain.Mode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saved sessions live in  <app files>/The Interpreter/<NAME><timestamp>/  (same layout as the
 * LÖVE version): the input, the output and a MISSING.txt report.
 */
class SavesRepository(filesDir: File) {

    val root: File = File(filesDir, ROOT_NAME).also { it.mkdirs() }

    /** @return the folder that was written */
    fun saveSession(
        language: CipherLanguage,
        mode: Mode,
        input: String,
        output: String,
        tables: CipherTables,
        now: Date = Date(),
    ): File {
        val lang = language.displayName.uppercase(Locale.ROOT)
        val (entryName, inputName, outputName) = if (mode == Mode.INTERPRET) {
            Triple("NORMAL", "input($lang).txt", "output(NORMAL).txt")
        } else {
            Triple(lang, "input(NORMAL).txt", "output($lang).txt")
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(now)
        var dir = File(root, entryName + stamp)
        var n = 2
        while (dir.exists()) dir = File(root, "$entryName${stamp}_${n++}")
        dir.mkdirs()
        File(dir, inputName).writeText(input, Charsets.UTF_8)
        File(dir, outputName).writeText(output, Charsets.UTF_8)
        File(dir, "MISSING.txt").writeText(Interpreter.missingReport(input, tables), Charsets.UTF_8)
        return dir
    }

    /** Folders first (newest first), then files by name. */
    fun list(dir: File): List<File> {
        val children = dir.listFiles().orEmpty().toList()
        val folders = children.filter { it.isDirectory }.sortedByDescending { it.lastModified() }
        val files = children.filter { it.isFile }.sortedBy { it.name.lowercase(Locale.ROOT) }
        return folders + files
    }

    fun readText(file: File): String =
        if (isInsideRoot(file)) runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("") else ""

    fun delete(file: File): Boolean =
        isInsideRoot(file) && file != root && file.deleteRecursively()

    fun isInsideRoot(file: File): Boolean = runCatching {
        file.canonicalPath.startsWith(root.canonicalPath)
    }.getOrDefault(false)

    companion object {
        const val ROOT_NAME = "The Interpreter"
    }
}
