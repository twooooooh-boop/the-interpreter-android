package com.twooooooh.interpreter.domain

/**
 * Lookup tables the interpreter works with (built from the built-in maps plus the user's edits).
 *
 * @property decodeMap   key (cipher symbol / symbols) -> plain letter
 * @property encodeMap   plain letter -> its PRIMARY key
 * @property keyLengths  distinct key lengths, longest first (decoding tries long keys first)
 * @property keysByLetter every key of a letter, primary first (only letters that have keys)
 * @property meaningsByKey keys that stand for SEVERAL letters -> all those letters, most important first
 *                        ([decodeMap] holds the first one)
 */
class CipherTables(
    val decodeMap: Map<String, String>,
    val encodeMap: Map<Char, String>,
    val keyLengths: List<Int>,
    val keysByLetter: Map<Char, List<String>> = emptyMap(),
    val meaningsByKey: Map<String, List<Char>> = emptyMap(),
)
