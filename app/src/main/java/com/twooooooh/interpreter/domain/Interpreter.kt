package com.twooooooh.interpreter.domain

/**
 * The interpreting logic (ported from the original Lua app).
 *
 * decode: at every position try the known keys, longest first; if none matches, one character is
 *         handled as before - plain ASCII letters / digits / punctuation / spaces pass through,
 *         anything else becomes a numbered placeholder like [1], [2] (same symbol = same number).
 * encode: every letter becomes its primary key; the other letter case is tried when a letter has
 *         no key of its own; everything else is kept.
 */
object Interpreter {

    enum class TokenKind { KEY, PASS, UNKNOWN }

    /** One piece of the input: a key (decodes to [letter]), a plain ASCII char, or an unknown symbol. */
    class Token(
        val start: Int,
        val end: Int,
        val kind: TokenKind,
        val letter: String? = null,
        /** Further meanings of a key that stands for several letters (the first meaning is [letter]). */
        val alternates: List<String> = emptyList(),
    )

    /** Splits [input] exactly the way [decode] reads it (longest key first). */
    fun tokenize(input: String, tables: CipherTables): List<Token> {
        val tokens = ArrayList<Token>()
        var i = 0
        val n = input.length
        while (i < n) {
            var matched = false
            for (len in tables.keyLengths) {
                if (i + len <= n) {
                    val letter = tables.decodeMap[input.substring(i, i + len)]
                    if (letter != null) {
                        val key = input.substring(i, i + len)
                        val others = tables.meaningsByKey[key]?.drop(1)?.map { it.toString() }.orEmpty()
                        tokens.add(Token(i, i + len, TokenKind.KEY, letter, others))
                        i += len
                        matched = true
                        break
                    }
                }
            }
            if (!matched) {
                val cp = input.codePointAt(i)
                val width = Character.charCount(cp)
                tokens.add(Token(i, i + width, if (isPassThrough(cp)) TokenKind.PASS else TokenKind.UNKNOWN))
                i += width
            }
        }
        return tokens
    }

    fun decode(input: String, tables: CipherTables): String {
        val out = StringBuilder()
        val unknown = LinkedHashMap<String, Int>()
        for (t in tokenize(input, tables)) {
            when (t.kind) {
                TokenKind.KEY -> out.append(t.letter)
                TokenKind.PASS -> out.append(input, t.start, t.end)
                TokenKind.UNKNOWN -> {
                    val number = unknown.getOrPut(input.substring(t.start, t.end)) { unknown.size + 1 }
                    out.append('[').append(number).append(']')
                }
            }
        }
        return out.toString()
    }

    fun encode(text: String, tables: CipherTables): String {
        val out = StringBuilder()
        for (c in text) {
            val key = tables.encodeMap[c]
                ?: tables.encodeMap[asciiLower(c)]
                ?: tables.encodeMap[asciiUpper(c)]
            if (key != null) out.append(key) else out.append(c)
        }
        return out.toString()
    }

    // ---- several outputs: one per key a letter owns --------------------------------------------

    /** Keys that can stand for [c] (same lookup order as [encode]: exact case, lower, upper). */
    private fun keysOf(c: Char, tables: CipherTables): List<String>? =
        tables.keysByLetter[c] ?: tables.keysByLetter[asciiLower(c)] ?: tables.keysByLetter[asciiUpper(c)]

    /** How many different encodings [text] has: the most keys any of its letters owns (at least 1). */
    fun variantCount(text: String, tables: CipherTables): Int {
        var most = 1
        for (c in text) {
            val n = keysOf(c, tables)?.size ?: 0
            if (n > most) most = n
        }
        return most
    }

    /**
     * Encoding number [index] (0 = every letter's first key, 1 = second key, ...).
     * A letter that has fewer keys uses its primary key, so variant 0 equals [encode].
     */
    fun encodeVariant(text: String, tables: CipherTables, index: Int): String {
        val out = StringBuilder()
        for (c in text) {
            val keys = keysOf(c, tables)
            if (keys == null) out.append(c) else out.append(keys.getOrElse(index) { keys[0] })
        }
        return out.toString()
    }

    fun allVariants(text: String, tables: CipherTables): List<String> =
        List(variantCount(text, tables)) { encodeVariant(text, tables, it) }

    /** Every letter gets a random one of its keys. Same [seed] = same result. */
    fun encodeMixed(text: String, tables: CipherTables, seed: Long): String {
        val random = java.util.Random(seed)
        val out = StringBuilder()
        for (c in text) {
            val keys = keysOf(c, tables)
            if (keys == null) out.append(c) else out.append(keys[random.nextInt(keys.size)])
        }
        return out.toString()
    }

    // ---- Interpret mode: the same message written in each key style --------------------------

    /** How many key styles the letters of [input] have (the most keys any decoded letter owns, at least 1). */
    fun transcodeCount(input: String, tables: CipherTables): Int {
        var most = 1
        for (t in tokenize(input, tables)) {
            if (t.kind != TokenKind.KEY) continue
            val n = tables.keysByLetter[t.letter!![0]]?.size ?: 0
            if (n > most) most = n
        }
        return most
    }

    /**
     * Re-writes [input] with the [index]-th key of every letter it contains (0 = first key ...).
     * Letters with fewer keys use their primary key; unknown symbols and plain characters stay as typed.
     */
    fun transcodeVariant(input: String, tables: CipherTables, index: Int): String =
        transcode(input, tables) { keys -> keys.getOrElse(index) { keys[0] } }

    fun allTranscodes(input: String, tables: CipherTables): List<String> =
        List(transcodeCount(input, tables)) { transcodeVariant(input, tables, it) }

    /** Every letter gets a random one of its keys. Same [seed] = same result. */
    fun transcodeMixed(input: String, tables: CipherTables, seed: Long): String {
        val random = java.util.Random(seed)
        return transcode(input, tables) { keys -> keys[random.nextInt(keys.size)] }
    }

    private fun transcode(input: String, tables: CipherTables, pick: (List<String>) -> String): String {
        val out = StringBuilder()
        for (t in tokenize(input, tables)) {
            val keys = if (t.kind == TokenKind.KEY) tables.keysByLetter[t.letter!![0]] else null
            if (keys != null) out.append(pick(keys)) else out.append(input, t.start, t.end)
        }
        return out.toString()
    }

    // ---- Interpret mode: word by word ("interlinear") reading ------------------------------------

    /** One decoded piece: a letter (with the other letters its key can mean), a plain char or "[n]". */
    class Cell(val text: String, val alternates: List<String> = emptyList(), val unknown: Boolean = false)

    /** One word of the input: how it was typed, what it decodes to, and its pieces. */
    class WordReading(val encoded: String, val decoded: String, val cells: List<Cell>) {
        val hasAlternates: Boolean get() = cells.any { it.alternates.isNotEmpty() }
    }

    /** The words of the input, split into the lines of the input. */
    class WordLayout(val lines: List<List<WordReading>>, val truncated: Boolean)

    /** Does any key of [input] stand for more than one letter? */
    fun hasAmbiguity(input: String, tables: CipherTables): Boolean =
        tables.meaningsByKey.isNotEmpty() && tokenize(input, tables).any { it.alternates.isNotEmpty() }

    /**
     * Reads [input] word by word. Words are split at spaces / tabs / new lines; the decoded words,
     * put back together, give the same text as [decode] (unknown symbols are numbered over the whole text).
     * At most [maxWords] words are analysed, so a huge text cannot freeze the screen.
     */
    fun readWords(input: String, tables: CipherTables, maxWords: Int = 400): WordLayout {
        val unknown = LinkedHashMap<String, Int>()
        val lines = ArrayList<List<WordReading>>()
        var current = ArrayList<WordReading>()
        val decoded = StringBuilder()
        var cells = ArrayList<Cell>()
        var wordStart = -1
        var wordEnd = -1
        var count = 0
        var truncated = false

        fun flush(): Boolean {                       // false when the word limit is reached
            if (wordStart < 0) return true
            if (count >= maxWords) { truncated = true; return false }
            current.add(WordReading(input.substring(wordStart, wordEnd), decoded.toString(), cells))
            count++
            wordStart = -1
            decoded.setLength(0)
            cells = ArrayList()
            return true
        }

        for (t in tokenize(input, tables)) {
            val ch = input[t.start]
            val separator = t.kind == TokenKind.PASS && (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n')
            if (separator) {
                if (!flush()) break
                if (ch == '\n') { lines.add(current); current = ArrayList() }
            } else {
                if (wordStart < 0) wordStart = t.start
                wordEnd = t.end
                val cell = when (t.kind) {
                    TokenKind.KEY -> Cell(t.letter!!, t.alternates)
                    TokenKind.PASS -> Cell(input.substring(t.start, t.end))
                    TokenKind.UNKNOWN -> {
                        val number = unknown.getOrPut(input.substring(t.start, t.end)) { unknown.size + 1 }
                        Cell("[$number]", unknown = true)
                    }
                }
                cells.add(cell)
                decoded.append(cell.text)
            }
        }
        if (!truncated) flush()
        lines.add(current)
        return WordLayout(lines, truncated)
    }

    fun convert(input: String, mode: Mode, tables: CipherTables): String =
        if (mode == Mode.INTERPRET) decode(input, tables) else encode(input, tables)

    /** Symbols of [raw] that have no key and are not plain ASCII (first appearance order). */
    fun missingSymbols(raw: String, tables: CipherTables): List<String> {
        val seen = LinkedHashSet<String>()
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            val width = Character.charCount(cp)
            val ch = raw.substring(i, i + width)
            if (!tables.decodeMap.containsKey(ch) && !isPassThrough(cp)) seen.add(ch)
            i += width
        }
        return seen.toList()
    }

    /** Letters (a-z, A-Z) that no key points to, as "a b c " (trailing space like the original). */
    fun unassignedLetters(tables: CipherTables): String {
        return Letters.ALL.filter { it !in tables.keysByLetter }.joinToString("") { "$it " }
    }

    /** Text of the MISSING.txt file written next to a saved session. */
    fun missingReport(raw: String, tables: CipherTables): String {
        val missing = missingSymbols(raw, tables)
        val unused = unassignedLetters(tables)
        val sb = StringBuilder("--- UNKNOWN SYMBOLS IN INPUT ---\n")
        if (missing.isEmpty()) sb.append("None found.\n") else {
            for (m in missing) sb.append(m).append("  ")
            sb.append("\n")
        }
        sb.append("\n--- ALPHABETS NOT YET ASSIGNED ---\n")
        sb.append(if (unused.isEmpty()) "All assigned!" else unused)
        return sb.toString()
    }

    /** ASCII letters, digits, punctuation and white space (what Lua's %w %p %s matched). */
    internal fun isPassThrough(cp: Int): Boolean =
        cp in 48..57 || cp in 65..90 || cp in 97..122 ||     // alnum
            cp in 33..47 || cp in 58..64 || cp in 91..96 || cp in 123..126 ||  // punctuation
            cp in 9..13 || cp == 32                          // space

    private fun asciiLower(c: Char) = if (c in 'A'..'Z') (c + 32) else c
    private fun asciiUpper(c: Char) = if (c in 'a'..'z') (c - 32) else c
}
