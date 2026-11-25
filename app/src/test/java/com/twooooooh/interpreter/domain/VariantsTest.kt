package com.twooooooh.interpreter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VariantsTest {
    private val demon = CipherLanguage.DEMON

    private fun edited(): CipherTables {
        var s = KeymapState()
        s = s.addKey(demon, 'a', "@@").state
        s = s.addKey(demon, 'a', "Q7").state
        s = s.addKey(demon, 'b', "zz").state
        return s.tables(demon)
    }

    @Test
    fun oneVariantPerKeyAndMissingKeysFallBackToPrimary() {
        val t = edited()
        assertEquals(3, Interpreter.variantCount("ab", t))
        assertEquals(listOf("\u0394\u0431", "@@zz", "Q7\u0431"), Interpreter.allVariants("ab", t))
    }

    @Test
    fun textWithoutMultiKeyLettersHasOneVariant() {
        val t = edited()
        assertEquals(1, Interpreter.variantCount("cde 123", t))
        assertEquals(1, Interpreter.allVariants("", t).size)
    }

    @Test
    fun firstVariantAlwaysEqualsPlainEncode() {
        val samples = listOf("Hello, World!", "The quick brown fox", "abcABC xyz 123", "\u00F1 \u65E5 ?")
        for (language in CipherLanguage.entries) {
            val t = KeymapState().tables(language)
            val e = edited()
            for (text in samples) {
                assertEquals("$language $text", Interpreter.encode(text, t), Interpreter.encodeVariant(text, t, 0))
                assertEquals(Interpreter.encode(text, e), Interpreter.encodeVariant(text, e, 0))
            }
        }
    }

    @Test
    fun everyVariantDecodesBackToTheSameText() {
        val t = edited()
        val text = "abc fed ABC"
        for (v in Interpreter.allVariants(text, t)) assertEquals(text, Interpreter.decode(v, t))
        for (seed in 1L..20L) assertEquals(text, Interpreter.decode(Interpreter.encodeMixed(text, t, seed), t))
    }

    @Test
    fun mixedIsRepeatableAndUsesDifferentKeys() {
        val t = edited()
        val text = "aaaaaaaaaaaaaaaaaaaa"
        assertEquals(Interpreter.encodeMixed(text, t, 7L), Interpreter.encodeMixed(text, t, 7L))
        val seen = HashSet<String>()
        for (seed in 1L..10L) seen.add(Interpreter.encodeMixed(text, t, seed))
        assertTrue(seen.size > 1)
    }

    @Test
    fun savedSessionContainsAllVariants() {
        val t = edited()
        val variants = Interpreter.allVariants("ab", t)
        val plan = SessionPlanner.plan(demon, Mode.REVERSE, "ab", variants[0], t, java.util.Date(0L), variants.drop(1))
        assertEquals(
            listOf("input(NORMAL).txt", "output(DEMON).txt", "output(DEMON)-key2.txt", "output(DEMON)-key3.txt", "MISSING.txt"),
            plan.files.map { it.name },
        )
        assertEquals("@@zz", plan.files[2].content)
    }
}
