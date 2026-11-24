package com.twooooooh.interpreter.domain

/**
 * The built-in cipher tables: symbol -> plain letter.
 *
 * Ported 1:1 (same symbols, same order) from the original Lua `eldaMaps`.
 * Order matters: when a symbol appears twice in one table the LAST entry wins,
 * exactly like a Lua table constructor (Ridmik relies on this).
 * Non-ASCII symbols are written as \uXXXX escapes so they survive any editor.
 */
object BuiltinMaps {

    // Made from M4hdi
    val DEMON: Map<String, String> = linkedMapOf(
        "\u0394" to "a", "\u0431" to "b", "\u03C2" to "c", "\u0434" to "d", "\u044D" to "e",
        "\u0446" to "f", "\u03B4" to "g", "\u044E" to "h", "\u03B9" to "i", "\u043B" to "j",
        "\u03BA" to "k", "\u0433" to "l", "\u0449" to "m", "\u0439" to "n", "\u0398" to "o",
        "p" to "p", "\u03C6" to "q", "\u044F" to "r", "\u0282" to "s", "\u0442" to "t",
        "\u0447" to "u", "v" to "v", "\u03C9" to "w", "x" to "x", "\u00FD" to "y",
        "\u039E" to "x", "(z)" to "z", "A" to "A", "\u0411" to "B", "C" to "C",
        "\u0414" to "D", "\u04D8" to "E", "\u0426" to "F", "\u0263" to "G", "\u042E" to "H",
        "\u042B" to "I", "\u041B" to "J", "\u041A" to "K", "\u0413" to "L", "\u03A3" to "M",
        "\u0418" to "N", "\u03A9" to "O", "r" to "P", "(Q)" to "Q", "\u042F" to "R",
        "S" to "S", "\u03B3" to "T", "\u0427" to "U", "\u039B" to "V", "\u0292" to "W",
        "\u03F0" to "X", "\u03BB" to "Y", "(Z)" to "Z"
    )

    // Made via Ridmik (17.2.1) keyboard's non-alphanumeric positions where the letters would have existed.
    val RIDMIK: Map<String, String> = linkedMapOf(
        "\u00A3" to "a", "\\" to "b", "\u00A9" to "c", "\u2105" to "d", "|" to "e",
        "^" to "f", "\u00B0" to "g", "_" to "h", "\u00D7" to "i", "=" to "j",
        "[" to "k", "]" to "l", ">" to "m", "<" to "n", "{" to "o",
        "}" to "p", "~" to "q", "\u09BD" to "r", "\$" to "s", "\u221A" to "t",
        "\u00F7" to "u", "\u2122" to "v", "`" to "w", "\u00AE" to "x", "\u03C0" to "y",
        "\u200C!" to "z", "\u00A3" to "A", "\\" to "B", "\u00A9" to "C", "\u2105" to "D",
        "|" to "E", "^" to "F", "\u00B0" to "G", "_" to "H", "\u00D7" to "I",
        "=" to "J", "[" to "K", "]" to "L", ">" to "M", "<" to "N",
        "{" to "O", "}" to "P", "~" to "Q", "\u09BD" to "R", "\$" to "S",
        "\u221A" to "T", "\u00F7" to "U", "\u2122" to "V", "`" to "W", "\u00AE" to "X",
        "\u03C0" to "Y", "\u200C!" to "Z"
    )

    // Made by turning normal letters into blocky shapes, then putting them in each segment.
    val BLOCKY: Map<String, String> = linkedMapOf(
        "^0" to "a", "|o" to "b", "<" to "c", "o|" to "d", "<-" to "e",
        "|^-" to "f", "&" to "g", "h" to "h", "i" to "i", "j" to "j",
        "|/_" to "k", "l" to "l", "|^||^|" to "m", "|^|" to "n", "o" to "o",
        "|O" to "p", "O|/" to "q", "|^" to "r", "-<_" to "s", "t" to "t",
        "u" to "u", "v" to "v", "w" to "w", "*" to "x", "v/" to "y",
        "->_" to "z", "|=|" to "A", "|_|-|_" to "B", "-|_" to "C", "|-|_" to "D",
        "|_-_" to "E", "|-_" to "F", "-|_|-" to "G", "H" to "H", "I" to "I",
        "_|-|" to "J", "|/\\" to "K", "|_" to "L", "M" to "M", "N" to "N",
        "O" to "O", "|0" to "P", "O\\" to "Q", "|-|_\\" to "R", "-|_|_" to "S",
        "T" to "T", "U" to "U", "V" to "V", "W" to "W", "\\/" to "X",
        "\\/|" to "Y", "-/_" to "Z"
    )

    fun of(language: CipherLanguage): Map<String, String> = when (language) {
        CipherLanguage.DEMON -> DEMON
        CipherLanguage.RIDMIK -> RIDMIK
        CipherLanguage.BLOCKY -> BLOCKY
    }
}
