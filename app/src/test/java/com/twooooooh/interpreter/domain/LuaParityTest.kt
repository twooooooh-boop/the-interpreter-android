package com.twooooooh.interpreter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays vectors recorded from the original Lua implementation (resources/lua_vectors.txt):
 *   V  default tables, decode (I) / encode (R)
 *   OP key edits, applied in order, with the result the Lua code gave
 *   C  conversions after those edits
 *   K  key lists after those edits       D  default key order
 */
class LuaParityTest {

    private fun unhex(h: String): String {
        if (h.isEmpty()) return ""
        val bytes = ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        return String(bytes, Charsets.UTF_8)
    }

    private fun lang(id: String) = CipherLanguage.fromId(id)

    private val lines: List<List<String>> by lazy {
        val text = javaClass.classLoader!!.getResourceAsStream("lua_vectors.txt")!!
            .readBytes().toString(Charsets.UTF_8)
        text.split('\n').filter { it.isNotEmpty() }.map { it.split('\t') }
    }

    private fun convert(state: KeymapState, f: List<String>): Pair<String, String> {
        val language = lang(f[1])
        val input = unhex(f[3])
        val tables = state.tables(language)
        val actual = if (f[2] == "I") Interpreter.decode(input, tables) else Interpreter.encode(input, tables)
        return actual to unhex(f[4])
    }

    @Test
    fun defaultTablesMatchLua() {
        var count = 0
        for (f in lines.filter { it[0] == "V" }) {
            val (actual, expected) = convert(KeymapState(), f)
            assertEquals("V ${f[1]} ${f[2]} '${unhex(f[3])}'", expected, actual)
            count++
        }
        assertTrue(count > 400)
    }

    @Test
    fun defaultKeyOrderMatchesLua() {
        val state = KeymapState()
        for (f in lines.filter { it[0] == "D" }) {
            val expected = if (f.size > 3 && f[3].isNotEmpty()) f[3].split(',').map(::unhex) else emptyList()
            assertEquals("D ${f[1]} ${f[2]}", expected, state.keysFor(lang(f[1]), f[2][0]))
        }
    }

    @Test
    fun editsAndConversionsMatchLua() {
        var state = KeymapState()
        for (f in lines.filter { it[0] == "OP" }) {
            val language = lang(f[2])
            when (f[1]) {
                "add" -> {
                    val r = state.addKey(language, f[3][0], unhex(f[4]))
                    assertEquals("ok for ${f.joinToString(" ")}", f[5] == "true", r.ok)
                    assertEquals("msg for ${f.joinToString(" ")}", unhex(f[6]), r.message)
                    state = r.state
                }
                "primary" -> state = state.makePrimary(language, f[3][0], f[4].toInt() - 1)
                "clear" -> state = state.clearLetter(language, f[3][0])
                "remove" -> state = state.removeKey(language, f[3][0], f[4].toInt() - 1)
                else -> error("unknown op ${f[1]}")
            }
        }
        var count = 0
        for (f in lines.filter { it[0] == "C" }) {
            val (actual, expected) = convert(state, f)
            assertEquals("C ${f[1]} ${f[2]} '${unhex(f[3])}'", expected, actual)
            count++
        }
        assertTrue(count > 400)
        for (f in lines.filter { it[0] == "K" }) {
            val expected = if (f.size > 3 && f[3].isNotEmpty()) f[3].split(',').map(::unhex) else emptyList()
            assertEquals("K ${f[1]} ${f[2]}", expected, state.keysFor(lang(f[1]), f[2][0]))
        }
    }
}
