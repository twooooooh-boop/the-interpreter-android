package com.twooooooh.interpreter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeymapJsonTest {
    private val demon = CipherLanguage.DEMON

    private fun success(text: String) = KeymapJson.parse(text) as KeymapImport.Success

    @Test
    fun exportThenImportRoundTrips() {
        var s = KeymapState()
        s = s.addKey(demon, 'a', "@@").state
        s = s.addKey(demon, 'b', "q\"x").state          // quote inside a key
        s = s.addKey(demon, 'c', "a\\b").state          // backslash inside a key
        s = s.addKey(CipherLanguage.RIDMIK, 'A', "\uD83D\uDE00").state  // emoji (surrogate pair)
        val json = KeymapJson.export(s, listOf(demon, CipherLanguage.RIDMIK))
        val back = success(json)
        assertEquals(setOf(demon, CipherLanguage.RIDMIK), back.maps.keys)
        assertTrue(back.warnings.isEmpty())
        val restored = KeymapState().importMaps(back.maps)
        for (language in listOf(demon, CipherLanguage.RIDMIK)) {
            for (letter in Letters.ALL) {
                assertEquals("$language $letter", s.keysFor(language, letter), restored.keysFor(language, letter))
            }
        }
    }

    @Test
    fun onlyTickedMapsAreExported() {
        val json = KeymapJson.export(KeymapState(), listOf(CipherLanguage.BLOCKY))
        assertEquals(setOf(CipherLanguage.BLOCKY), success(json).maps.keys)
        assertFalse(json.contains("\"demon\""))
    }

    @Test
    fun importingTheBuiltInTablesMeansNotEdited() {
        val json = KeymapJson.export(KeymapState(), CipherLanguage.entries)
        val state = KeymapState().addKey(demon, 'a', "@@").state.importMaps(success(json).maps)
        assertFalse(state.isCustom(demon))
        assertEquals(KeymapState(), state)
    }

    @Test
    fun importReplacesWholeTable() {
        val edited = KeymapState().addKey(demon, 'a', "@@").state
        val json = KeymapJson.export(edited, listOf(demon))
        val state = KeymapState().importMaps(success(json).maps)
        assertTrue(state.isCustom(demon))
        assertEquals(listOf("\u0394", "@@"), state.keysFor(demon, 'a'))
    }

    @Test
    fun badFilesAreRejectedWithAMessage() {
        assertTrue(KeymapJson.parse("") is KeymapImport.Failure)
        assertTrue(KeymapJson.parse("not json") is KeymapImport.Failure)
        assertTrue(KeymapJson.parse("[1,2,3]") is KeymapImport.Failure)
        assertTrue(KeymapJson.parse("{\"format\":\"something-else\",\"maps\":{}}") is KeymapImport.Failure)
        assertTrue(KeymapJson.parse("{\"format\":\"the-interpreter-keymaps\",\"version\":99,\"maps\":{}}") is KeymapImport.Failure)
        assertTrue(KeymapJson.parse("{\"maps\":{}}") is KeymapImport.Failure)
        assertTrue(KeymapJson.parse("{\"maps\":{\"nope\":{}}}") is KeymapImport.Failure)
        assertTrue(KeymapJson.parse("{\"maps\":{\"demon\":{\"a\":[\"x\"]") is KeymapImport.Failure)   // truncated
    }

    @Test
    fun invalidDuplicateAndExtraKeysAreSkippedWithWarnings() {
        val keys = (1..10).joinToString(",") { "\"k$it\"" }
        val text = """{"maps":{"demon":{"a":["x","a b","1234567","x"],"b":["x","y"],"c":[$keys],"1":["z"]},"mystery":{}}}"""
        val r = success(text)
        val table = r.maps.getValue(demon)
        assertEquals(listOf("x"), table['a'])
        assertEquals(listOf("y"), table['b'])                 // "x" already belongs to a
        assertEquals(KeymapRules.MAX_KEYS, table.getValue('c').size)
        assertTrue(r.warnings.size >= 5)
        assertTrue(r.warnings.any { it.contains("mystery") })
    }

    @Test
    fun unicodeEscapesAndBomAreUnderstood() {
        val text = "\uFEFF" + """{"maps":{"demon":{"a":["\u0394\u0394","\ud83d\ude00"]}}}"""
        val a = success(text).maps.getValue(demon).getValue('a')
        assertEquals(listOf("\u0394\u0394", "\uD83D\uDE00"), a)
    }

    @Test
    fun sessionPlanMatchesTheOriginalNaming() {
        val t = KeymapState().tables(CipherLanguage.RIDMIK)
        val now = java.util.Date(0L)
        val interpret = SessionPlanner.plan(CipherLanguage.RIDMIK, Mode.INTERPRET, "abc", "x", t, now)
        assertTrue(interpret.folderName.startsWith("NORMAL19"))
        assertEquals(listOf("input(RIDMIK).txt", "output(NORMAL).txt", "MISSING.txt"), interpret.files.map { it.name })
        val reverse = SessionPlanner.plan(CipherLanguage.DEMON, Mode.REVERSE, "abc", "x", KeymapState().tables(demon), now)
        assertTrue(reverse.folderName.startsWith("DEMON19"))
        assertEquals(listOf("input(NORMAL).txt", "output(DEMON).txt", "MISSING.txt"), reverse.files.map { it.name })
        assertTrue(reverse.files[2].content.startsWith("--- UNKNOWN SYMBOLS IN INPUT ---"))
    }
}
