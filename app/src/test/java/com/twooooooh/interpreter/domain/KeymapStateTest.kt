package com.twooooooh.interpreter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeymapStateTest {
    private val demon = CipherLanguage.DEMON

    @Test
    fun builtInTablesRoundTrip() {
        for (language in CipherLanguage.entries) {
            val tables = KeymapState().tables(language)
            assertEquals(BuiltinMaps.of(language), tables.decodeMap)
        }
    }

    @Test
    fun ridmikDuplicateSymbolsKeepTheLastEntry() {
        // 26 distinct symbols, each listed for a and A: the upper-case letter wins (as in the Lua table)
        assertEquals(26, BuiltinMaps.RIDMIK.size)
        assertTrue(BuiltinMaps.RIDMIK.values.all { it[0] in 'A'..'Z' })
    }

    @Test
    fun letterCanHaveSeveralKeysAndPrimaryIsUsedForEncoding() {
        var s = KeymapState()
        s = s.addKey(demon, 'a', "@@").state
        s = s.addKey(demon, 'a', "Q7").state
        assertEquals(listOf("\u0394", "@@", "Q7"), s.keysFor(demon, 'a'))
        val t = s.tables(demon)
        assertEquals("a", t.decodeMap["@@"])
        assertEquals("\u0394", t.encodeMap['a'])
        s = s.makePrimary(demon, 'a', 2)
        assertEquals("Q7", s.tables(demon).encodeMap['a'])
        assertEquals("Q7", Interpreter.encode("a", s.tables(demon)))
    }

    @Test
    fun aKeyCanStandForSeveralLetters() {
        val r = KeymapState().addKey(demon, 'b', "\u0394")      // a's key
        assertTrue(r.ok)
        assertEquals("Added. It also stands for 'a'", r.message)
        assertEquals(listOf("\u0394"), r.state.keysFor(demon, 'a'))   // a keeps it
        assertEquals(listOf("\u0431", "\u0394"), r.state.keysFor(demon, 'b'))
        assertEquals(listOf('a', 'b'), r.state.lettersOf(demon, "\u0394"))
    }

    @Test
    fun validation() {
        val s = KeymapState()
        assertFalse(s.addKey(demon, 'a', "").ok)
        assertFalse(s.addKey(demon, 'a', "a b").ok)
        assertFalse(s.addKey(demon, 'a', "1234567").ok)
        assertFalse(s.addKey(demon, '1', "zz").ok)
        assertFalse(s.addKey(demon, 'a', "\u0394").ok)   // already its key
        assertNull(KeymapRules.validateKey("\u200C!"))   // built-in Ridmik key must stay valid
        var t = s
        repeat(KeymapRules.MAX_KEYS) { i -> t = t.addKey(demon, 'q', "k$i").state }
        assertFalse(t.addKey(demon, 'q', "zzz").ok)
    }

    @Test
    fun resetAndClear() {
        var s = KeymapState().addKey(demon, 'a', "@@").state
        assertTrue(s.isCustom(demon))
        s = s.clearLetter(demon, 'a')
        assertTrue(s.keysFor(demon, 'a').isEmpty())
        assertNull(s.tables(demon).decodeMap["\u0394"])
        s = s.resetLanguage(demon)
        assertFalse(s.isCustom(demon))
        assertEquals("a", s.tables(demon).decodeMap["\u0394"])
    }

    @Test
    fun multiCharacterKeysDecodeLongestFirst() {
        var s = KeymapState()
        s = s.addKey(demon, 'b', "ab").state
        s = s.addKey(demon, 'c', "abc").state
        val t = s.tables(demon)
        assertEquals("c", Interpreter.decode("abc", t))
        assertEquals("b", Interpreter.decode("ab", t))
    }

    @Test
    fun unknownSymbolsGetStableNumbers() {
        val t = KeymapState().tables(demon)
        assertEquals("[1][2][1]a", Interpreter.decode("\u00F1\u65E5\u00F1\u0394", t))
    }

    @Test
    fun codecRoundTripAndCorruption() {
        var s = KeymapState().addKey(demon, 'a', "@@").state
        s = s.addKey(CipherLanguage.RIDMIK, 'A', "zz").state
        val text = KeymapCodec.encode(s)
        assertTrue(text.startsWith("ELDAKEYS1\n"))
        assertEquals(s, KeymapCodec.decode(text))
        assertEquals(KeymapState(), KeymapCodec.decode("garbage\nnot a file"))
        assertEquals(KeymapState(), KeymapCodec.decode(null))
        assertEquals("", KeymapCodec.encode(KeymapState()))
    }

    @Test
    fun missingReportFormat() {
        val t = KeymapState().tables(demon)
        val report = Interpreter.missingReport("\u0394\u00F1", t)
        assertTrue(report.startsWith("--- UNKNOWN SYMBOLS IN INPUT ---\n\u00F1  \n"))
        assertTrue(report.contains("--- ALPHABETS NOT YET ASSIGNED ---\n"))
        assertTrue(Interpreter.missingReport("abc", t).contains("None found."))
    }
}
