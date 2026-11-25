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

class TranscodeTest {
    private val demon = CipherLanguage.DEMON

    private fun edited(): CipherTables {
        var s = KeymapState()
        s = s.addKey(demon, 'a', "@@").state
        s = s.addKey(demon, 'a', "Q7").state
        s = s.addKey(demon, 'b', "zz").state
        return s.tables(demon)
    }

    @Test
    fun interpretModeCanShowTheMessageInEachKeyStyle() {
        val t = edited()
        val input = "\u0394zz"                                   // a (key 1) + b (key 2)
        assertEquals("ab", Interpreter.decode(input, t))
        assertEquals(3, Interpreter.transcodeCount(input, t))
        assertEquals(listOf("\u0394\u0431", "@@zz", "Q7\u0431"), Interpreter.allTranscodes(input, t))
    }

    @Test
    fun transcodingMatchesEncodingTheDecodedText() {
        val t = edited()
        val text = "abc fed ABC"
        val encoded = Interpreter.encodeVariant(text, t, 1)
        for (k in 0 until Interpreter.variantCount(text, t)) {
            assertEquals(Interpreter.encodeVariant(text, t, k), Interpreter.transcodeVariant(encoded, t, k))
        }
        assertEquals(text, Interpreter.decode(Interpreter.transcodeMixed(encoded, t, 5L), t))
    }

    @Test
    fun unknownSymbolsAndPlainTextSurviveTranscoding() {
        val t = edited()
        assertEquals("@@\u00F1 hi!", Interpreter.transcodeVariant("\u0394\u00F1 hi!", t, 1).replace("hi", "hi"))
        assertEquals(1, Interpreter.transcodeCount("\u00F1\u00F1 123", t))
    }

    @Test
    fun tokensCoverTheWholeInputAndMatchDecode() {
        val t = edited()
        val input = "\u0394\u00F1 zz(z)\uD83D\uDE00\n"
        val tokens = Interpreter.tokenize(input, t)
        var pos = 0
        for (tok in tokens) { assertEquals(pos, tok.start); pos = tok.end }
        assertEquals(input.length, pos)
        val unknown = tokens.count { it.kind == Interpreter.TokenKind.UNKNOWN }
        assertEquals(2, unknown)                                 // the n-tilde and the emoji
    }

    @Test
    fun savedInterpretSessionListsEveryKeyStyle() {
        val t = edited()
        val styles = Interpreter.allTranscodes("\u0394zz", t)
        val plan = SessionPlanner.plan(demon, Mode.INTERPRET, "\u0394zz", "ab", t, java.util.Date(0L), styles, firstExtraKey = 1)
        assertEquals(
            listOf("input(DEMON).txt", "output(NORMAL).txt", "output(DEMON)-key1.txt", "output(DEMON)-key2.txt", "output(DEMON)-key3.txt", "MISSING.txt"),
            plan.files.map { it.name },
        )
    }
}

class WordReadingTest {
    private val demon = CipherLanguage.DEMON

    private fun edited(): CipherTables {
        var s = KeymapState()
        s = s.addKey(demon, 'a', "@@").state
        s = s.addKey(demon, 'a', "Q7").state
        s = s.addKey(demon, 'b', "zz").state
        return s.tables(demon)
    }

    @Test
    fun everyWordShowsItsOtherKeyStyles() {
        val t = edited()
        val layout = Interpreter.readWords("\u0394zz \u0394 \u0434", t)
        val words = layout.lines.single()
        assertEquals(listOf("ab", "a", "d"), words.map { it.decoded })
        assertEquals(listOf("@@zz", "Q7\u0431"), words[0].alternates)       // 2nd and 3rd style
        assertEquals(listOf("@@", "Q7"), words[1].alternates)
        assertTrue(words[2].alternates.isEmpty())                            // d has a single key
    }

    @Test
    fun wordsPutBackTogetherEqualTheNormalDecode() {
        val t = edited()
        val input = "\u0394zz \u00F1 \u0434\n\n\u0394\u00F1 q  \u65E5"
        val layout = Interpreter.readWords(input, t)
        assertEquals(3, layout.lines.size)                                   // the empty line stays an (empty) line
        assertTrue(layout.lines[1].isEmpty())
        val joined = layout.lines.joinToString("\n") { line -> line.joinToString(" ") { it.decoded } }
        assertEquals(Interpreter.decode(input.replace("  ", " "), t), joined)
        // unknown symbols are numbered over the whole text, not per word
        assertEquals("[1]", layout.lines[0][1].decoded)
        assertEquals("a[1]", layout.lines[2][0].decoded)
        assertEquals("[2]", layout.lines[2][2].decoded)
    }

    @Test
    fun hugeTextsAreCutOff() {
        val t = edited()
        val layout = Interpreter.readWords("\u0394 ".repeat(1000), t, maxWords = 50)
        assertTrue(layout.truncated)
        assertEquals(50, layout.lines.sumOf { it.size })
        assertTrue(!Interpreter.readWords("\u0394 \u0394", t).truncated)
    }
}
