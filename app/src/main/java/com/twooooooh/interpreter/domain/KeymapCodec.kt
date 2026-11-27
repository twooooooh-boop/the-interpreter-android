package com.twooooooh.interpreter.domain

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Text format of keymap.dat (same as the original LÖVE app):
 *
 *     ELDAKEYS1
 *     demon<TAB>a<TAB>hex,hex
 *
 * Keys are stored as hex of their UTF-8 bytes, so any symbol is safe. Every edited language is
 * written completely (all 52 letters), languages never edited are not written at all.
 */
object KeymapCodec {
    private const val HEADER = "ELDAKEYS1"
    private val LINE = Regex("^([A-Za-z0-9]+)\t([A-Za-z])\t?(.*)$")

    fun encode(state: KeymapState): String {
        if (state.custom.isEmpty()) return ""
        val sb = StringBuilder(HEADER).append('\n')
        for (language in state.custom.keys.sortedBy { it.id }) {
            val map = state.custom.getValue(language)
            for (letter in Letters.LOWER + Letters.UPPER) {
                val hex = map[letter].orEmpty().joinToString(",") { toHex(it) }
                sb.append(language.id).append('\t').append(letter).append('\t').append(hex).append('\n')
            }
        }
        return sb.toString()
    }

    fun decode(text: String?): KeymapState {
        if (text.isNullOrEmpty()) return KeymapState()
        val lines = text.split('\n', '\r').filter { it.isNotEmpty() }
        if (lines.firstOrNull() != HEADER) return KeymapState()

        val loaded = LinkedHashMap<CipherLanguage, MutableMap<Char, List<String>>>()
        for (line in lines.drop(1)) {
            val m = LINE.find(line) ?: continue
            val language = CipherLanguage.entries.firstOrNull { it.id == m.groupValues[1] } ?: continue
            val letter = m.groupValues[2][0]
            val seen = HashSet<String>()
            val keys = ArrayList<String>()
            for (h in m.groupValues[3].split(',').filter { it.isNotEmpty() }) {
                val key = fromHex(h) ?: continue
                if (KeymapRules.validateKey(key) == null && seen.add(key)) keys.add(key)
            }
            loaded.getOrPut(language) { LinkedHashMap() }[letter] = keys
        }
        val custom = loaded.mapValues { (_, map) ->
            Letters.ALL.associateWith { map[it].orEmpty() }
        }
        return KeymapState(custom)
    }

    private fun toHex(s: String): String =
        s.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun fromHex(h: String): String? {
        if (h.length % 2 != 0 || !h.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        val bytes = ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }
}
