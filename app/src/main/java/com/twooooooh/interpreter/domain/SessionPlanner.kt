package com.twooooooh.interpreter.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SessionFile(val name: String, val content: String)

/** What a "Save session" writes: one folder with three text files. */
data class SessionPlan(val folderName: String, val files: List<SessionFile>)

object SessionPlanner {
    fun plan(
        language: CipherLanguage,
        mode: Mode,
        input: String,
        output: String,
        tables: CipherTables,
        now: Date = Date(),
        extraOutputs: List<String> = emptyList(),
        firstExtraKey: Int = 2,
    ): SessionPlan {
        val lang = language.displayName.uppercase(Locale.ROOT)
        val entryName: String
        val inputName: String
        val outputName: String
        if (mode == Mode.INTERPRET) {
            entryName = "NORMAL"; inputName = "input($lang).txt"; outputName = "output(NORMAL).txt"
        } else {
            entryName = lang; inputName = "input(NORMAL).txt"; outputName = "output($lang).txt"
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(now)
        return SessionPlan(
            folderName = entryName + stamp,
            files = listOf(
                SessionFile(inputName, input),
                SessionFile(outputName, output),
            ) + extraOutputs.mapIndexed { i, text ->
                SessionFile("output($lang)-key${i + firstExtraKey}.txt", text)
            } + listOf(
                SessionFile("MISSING.txt", Interpreter.missingReport(input, tables)),
            ),
        )
    }
}
