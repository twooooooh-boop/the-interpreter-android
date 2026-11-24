package com.twooooooh.interpreter.domain

object KeymapRules {
    const val MAX_KEYS = 8        // keys per letter
    const val MAX_KEY_CHARS = 6   // characters per key

    /** Returns null when the key is fine, otherwise a short message for the user. */
    fun validateKey(key: String): String? {
        if (key.isEmpty()) return "Type a key first"
        if (key.any { it.code < 32 || it.code == 127 || it == ' ' }) return "No spaces allowed"
        if (hasLoneSurrogate(key)) return "Invalid text"
        if (key.codePointCount(0, key.length) > MAX_KEY_CHARS) return "Max $MAX_KEY_CHARS characters"
        return null
    }

    private fun hasLoneSurrogate(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length || !Character.isLowSurrogate(s[i + 1])) return true
                i += 2
            } else if (Character.isLowSurrogate(c)) {
                return true
            } else {
                i++
            }
        }
        return false
    }
}

data class EditResult(val state: KeymapState, val ok: Boolean, val message: String)

/**
 * Immutable set of the user's key edits.
 *
 *  - every plain letter (a-z, A-Z) can own several keys
 *  - the FIRST key of a letter is the primary one and is used when encoding
 *  - all keys of a letter are understood when decoding
 *  - a language that was never edited simply uses the built-in table
 */
data class KeymapState(
    val custom: Map<CipherLanguage, Map<Char, List<String>>> = emptyMap(),
) {
    fun isCustom(language: CipherLanguage): Boolean = custom.containsKey(language)

    private fun source(language: CipherLanguage): Map<Char, List<String>> =
        custom[language] ?: DefaultKeys.of(language)

    /** Keys of [letter] (primary first). */
    fun keysFor(language: CipherLanguage, letter: Char): List<String> =
        source(language)[letter].orEmpty()

    /** Which letter currently owns [key] (null if nobody). */
    fun owner(language: CipherLanguage, key: String): Char? {
        for ((letter, keys) in source(language)) if (key in keys) return letter
        return null
    }

    fun tables(language: CipherLanguage): CipherTables {
        val decode = HashMap<String, String>()
        val encode = HashMap<Char, String>()
        val lengths = HashSet<Int>()
        for ((letter, keys) in source(language)) {
            keys.forEachIndexed { index, key ->
                decode[key] = letter.toString()
                if (index == 0) encode[letter] = key
                lengths.add(key.length)
            }
        }
        return CipherTables(decode, encode, lengths.sortedDescending())
    }

    // ---- edits (each returns a new state) -----------------------------------------------

    fun addKey(language: CipherLanguage, letter: Char, key: String): EditResult {
        if (!Letters.isAsciiLetter(letter)) return EditResult(this, false, "Bad letter")
        KeymapRules.validateKey(key)?.let { return EditResult(this, false, it) }

        val owner = owner(language, key)
        if (owner == letter) return EditResult(this, false, "Already a key for '$letter'")
        if (keysFor(language, letter).size >= KeymapRules.MAX_KEYS) {
            return EditResult(this, false, "Max ${KeymapRules.MAX_KEYS} keys per letter")
        }

        val map = editable(language)
        var note = "Added"
        if (owner != null) {
            map[owner]?.remove(key)
            note = "Moved here from '$owner'"
        }
        map.getValue(letter).add(key)
        return EditResult(withMap(language, map), true, note)
    }

    fun removeKey(language: CipherLanguage, letter: Char, index: Int): KeymapState {
        if (keysFor(language, letter).getOrNull(index) == null) return this
        val map = editable(language)
        map.getValue(letter).removeAt(index)
        return withMap(language, map)
    }

    /** Moves key number [index] to the front, so it becomes the key used for encoding. */
    fun makePrimary(language: CipherLanguage, letter: Char, index: Int): KeymapState {
        if (index < 1 || keysFor(language, letter).getOrNull(index) == null) return this
        val map = editable(language)
        val list = map.getValue(letter)
        list.add(0, list.removeAt(index))
        return withMap(language, map)
    }

    fun clearLetter(language: CipherLanguage, letter: Char): KeymapState {
        if (keysFor(language, letter).isEmpty()) return this
        val map = editable(language)
        map.getValue(letter).clear()
        return withMap(language, map)
    }

    fun resetLanguage(language: CipherLanguage): KeymapState =
        if (isCustom(language)) KeymapState(custom - language) else this

    // ---- internals ----------------------------------------------------------------------

    private fun editable(language: CipherLanguage): MutableMap<Char, MutableList<String>> {
        val out = LinkedHashMap<Char, MutableList<String>>()
        val src = source(language)
        for (letter in Letters.ALL) out[letter] = (src[letter] ?: emptyList()).toMutableList()
        return out
    }

    private fun withMap(language: CipherLanguage, map: Map<Char, List<String>>): KeymapState =
        KeymapState(custom + (language to map.mapValues { it.value.toList() }))
}
