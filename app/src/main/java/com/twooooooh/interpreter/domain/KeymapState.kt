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
 *  - every plain letter (a-z, A-Z) can own several keys; its FIRST key is the one used when encoding
 *  - one key can stand for SEVERAL letters (e.g. "ɣ" for G and for t). When decoding, the letter that
 *    has the key as its first key wins, then capital letters before small ones; the other letters are
 *    the key's further meanings and are shown faded
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

    /** All letters [key] stands for, most important first (the first one is what decoding shows). */
    fun lettersOf(language: CipherLanguage, key: String): List<Char> =
        rankMeanings(positionsOf(source(language), key))

    fun tables(language: CipherLanguage): CipherTables {
        val src = source(language)
        val byKey = HashMap<String, MutableList<Pair<Char, Int>>>()
        val encode = HashMap<Char, String>()
        val lengths = HashSet<Int>()
        val byLetter = HashMap<Char, List<String>>()
        for ((letter, keys) in src) {
            if (keys.isNotEmpty()) byLetter[letter] = keys
            keys.forEachIndexed { index, key ->
                byKey.getOrPut(key) { mutableListOf() }.add(letter to index)
                if (index == 0) encode[letter] = key
                lengths.add(key.length)
            }
        }
        val decode = HashMap<String, String>()
        val meanings = HashMap<String, List<Char>>()
        for ((key, positions) in byKey) {
            val ranked = rankMeanings(positions)
            decode[key] = ranked.first().toString()
            if (ranked.size > 1) meanings[key] = ranked
        }
        return CipherTables(decode, encode, lengths.sortedDescending(), byLetter, meanings)
    }

    // ---- edits (each returns a new state) -----------------------------------------------

    fun addKey(language: CipherLanguage, letter: Char, key: String): EditResult {
        if (!Letters.isAsciiLetter(letter)) return EditResult(this, false, "Bad letter")
        KeymapRules.validateKey(key)?.let { return EditResult(this, false, it) }

        val others = lettersOf(language, key)
        if (letter in others) return EditResult(this, false, "Already a key for '$letter'")
        if (keysFor(language, letter).size >= KeymapRules.MAX_KEYS) {
            return EditResult(this, false, "Max ${KeymapRules.MAX_KEYS} keys per letter")
        }

        val map = editable(language)
        map.getValue(letter).add(key)
        val note = if (others.isEmpty()) "Added" else "Added. It also stands for " + others.joinToString(", ") { "'$it'" }
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

    /**
     * Replaces whole tables (used by restore). A table that equals the built-in one is stored as
     * "not edited", so the language shows DEFAULT again.
     */
    fun importMaps(maps: Map<CipherLanguage, Map<Char, List<String>>>): KeymapState {
        var next = custom
        for ((language, table) in maps) {
            val full = Letters.ALL.associateWith { table[it].orEmpty() }
            val defaults = DefaultKeys.of(language)
            val same = Letters.ALL.all { full[it] == defaults[it].orEmpty() }
            next = if (same) next - language else next + (language to full)
        }
        return KeymapState(next)
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

    private fun positionsOf(table: Map<Char, List<String>>, key: String): List<Pair<Char, Int>> {
        val out = ArrayList<Pair<Char, Int>>()
        for ((letter, keys) in table) {
            val index = keys.indexOf(key)
            if (index >= 0) out.add(letter to index)
        }
        return out
    }

    private companion object {
        /**
         * Order of the letters a key stands for: the letter that has it as its first key comes first
         * (then second key, ...); on a tie capital letters come before small ones, then a-z.
         */
        fun rankMeanings(positions: List<Pair<Char, Int>>): List<Char> =
            positions.sortedWith(
                compareBy<Pair<Char, Int>>(
                    { it.second },
                    { if (it.first in 'A'..'Z') 0 else 1 },
                    { it.first.lowercaseChar() },
                    { it.first },
                ),
            ).map { it.first }
    }
}
