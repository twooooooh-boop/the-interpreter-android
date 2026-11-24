package com.twooooooh.interpreter.domain

/** Result of reading a keymap JSON file. */
sealed interface KeymapImport {
    /** [maps] are complete tables (letter -> keys); [warnings] list things that were skipped. */
    data class Success(
        val maps: Map<CipherLanguage, Map<Char, List<String>>>,
        val warnings: List<String>,
    ) : KeymapImport

    data class Failure(val message: String) : KeymapImport
}

/**
 * Export / import of the letter -> keys tables as JSON:
 *
 *     {
 *       "format": "the-interpreter-keymaps",
 *       "version": 1,
 *       "maps": { "demon": { "a": ["Δ", "@@"], "b": [...], ... "Z": [...] } }
 *     }
 *
 * The first key of a letter is its primary key (used for encoding).
 */
object KeymapJson {
    const val FORMAT = "the-interpreter-keymaps"
    const val VERSION = 1

    fun export(state: KeymapState, languages: Collection<CipherLanguage>): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"format\": ").append(MiniJson.quote(FORMAT)).append(",\n")
        sb.append("  \"version\": ").append(VERSION).append(",\n")
        sb.append("  \"maps\": {\n")
        val chosen = CipherLanguage.entries.filter { it in languages }
        chosen.forEachIndexed { li, language ->
            sb.append("    ").append(MiniJson.quote(language.id)).append(": {\n")
            Letters.ALL.forEachIndexed { i, letter ->
                val keys = state.keysFor(language, letter).joinToString(", ") { MiniJson.quote(it) }
                sb.append("      ").append(MiniJson.quote(letter.toString())).append(": [").append(keys).append("]")
                sb.append(if (i < Letters.ALL.size - 1) ",\n" else "\n")
            }
            sb.append("    }").append(if (li < chosen.size - 1) ",\n" else "\n")
        }
        sb.append("  }\n}\n")
        return sb.toString()
    }

    fun parse(text: String): KeymapImport {
        val root = try {
            MiniJson.parse(text.removePrefix("\uFEFF"))
        } catch (e: JsonException) {
            return KeymapImport.Failure("This is not a valid JSON file (${e.message})")
        }
        if (root !is Map<*, *>) return KeymapImport.Failure("This file is not a keymap export")
        val format = root["format"]
        if (format != null && format != FORMAT) return KeymapImport.Failure("This file is not a keymap export")
        val version = (root["version"] as? Double)?.toInt()
        if (version != null && version > VERSION) {
            return KeymapImport.Failure("This file was made by a newer version of the app")
        }
        val mapsNode = root["maps"] as? Map<*, *>
            ?: return KeymapImport.Failure("No maps found in this file")

        val warnings = ArrayList<String>()
        val result = LinkedHashMap<CipherLanguage, Map<Char, List<String>>>()
        for ((rawId, rawTable) in mapsNode) {
            val language = CipherLanguage.entries.firstOrNull { it.id == rawId }
            if (language == null) {
                warnings.add("Skipped unknown map \"$rawId\"")
                continue
            }
            val table = rawTable as? Map<*, *>
            if (table == null) {
                warnings.add("Skipped ${language.displayName}: not a table")
                continue
            }
            val parsed = LinkedHashMap<Char, List<String>>()
            val seen = HashSet<String>()
            var invalid = 0
            var duplicates = 0
            var tooMany = 0
            var badLetters = 0
            for ((rawLetter, rawKeys) in table) {
                val letter = (rawLetter as? String)?.singleOrNull()
                if (letter == null || !Letters.isAsciiLetter(letter)) { badLetters++; continue }
                val list = rawKeys as? List<*>
                if (list == null) { invalid++; continue }
                val keys = ArrayList<String>()
                for (k in list) {
                    val key = k as? String
                    if (key == null || KeymapRules.validateKey(key) != null) { invalid++; continue }
                    if (!seen.add(key)) { duplicates++; continue }
                    if (keys.size >= KeymapRules.MAX_KEYS) { tooMany++; continue }
                    keys.add(key)
                }
                parsed[letter] = keys
            }
            val name = language.displayName
            if (badLetters > 0) warnings.add("$name: ignored $badLetters entries that are not a letter")
            if (invalid > 0) warnings.add("$name: skipped $invalid invalid keys")
            if (duplicates > 0) warnings.add("$name: skipped $duplicates keys used by more than one letter")
            if (tooMany > 0) warnings.add("$name: skipped $tooMany keys over the limit of ${KeymapRules.MAX_KEYS} per letter")
            result[language] = Letters.ALL.associateWith { parsed[it].orEmpty() }
        }
        if (result.isEmpty()) return KeymapImport.Failure("No usable maps found in this file")
        return KeymapImport.Success(result, warnings)
    }
}
