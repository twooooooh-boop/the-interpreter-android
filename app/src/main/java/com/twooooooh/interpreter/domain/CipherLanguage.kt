package com.twooooooh.interpreter.domain

/** The cipher alphabets the app understands. [id] is used for storage, [displayName] for the UI. */
enum class CipherLanguage(val id: String, val displayName: String) {
    DEMON("demon", "Demon"),
    RIDMIK("ridmik", "Ridmik"),
    BLOCKY("blocky", "Blocky");

    companion object {
        fun fromId(id: String?): CipherLanguage = entries.firstOrNull { it.id == id } ?: DEMON
    }
}

/** INTERPRET: input is encoded, output is normal text. REVERSE: input is normal text, output is encoded. */
enum class Mode {
    INTERPRET,
    REVERSE;

    companion object {
        fun fromName(name: String?): Mode = entries.firstOrNull { it.name == name } ?: INTERPRET
    }
}

object Letters {
    val LOWER: List<Char> = ('a'..'z').toList()
    val UPPER: List<Char> = ('A'..'Z').toList()
    val ALL: List<Char> = LOWER + UPPER

    fun isAsciiLetter(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'
}
