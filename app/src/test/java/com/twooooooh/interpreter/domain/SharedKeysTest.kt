package com.twooooooh.interpreter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One key standing for several letters (the "\u0263 for G and t" case). */
class SharedKeysTest {
    private val demon = CipherLanguage.DEMON
    private val gamma = "\u0263"   // the built-in key of G

    private fun tAlsoGamma(): KeymapState = KeymapState().addKey(demon, 't', gamma).state

    @Test
    fun theFirstLetterWinsAndTheOthersAreFurtherMeanings() {
        val s = tAlsoGamma()
        // gamma is G's FIRST key but only t's second key -> G first
        assertEquals(listOf('G', 't'), s.lettersOf(demon, gamma))
        val t = s.tables(demon)
        assertEquals("G", Interpreter.decode(gamma, t))
        val token = Interpreter.tokenize(gamma, t).single()
        assertEquals("G", token.letter)
        assertEquals(listOf("t"), token.alternates)
        assertTrue(Interpreter.hasAmbiguity("hello $gamma", t))
        assertFalse(Interpreter.hasAmbiguity("hello", t))
    }

    @Test
    fun whenBothHaveItAsFirstKeyTheCapitalLetterComesFirst() {
        var s = KeymapState().clearLetter(demon, 't')
        s = s.addKey(demon, 't', gamma).state            // now the FIRST key of t as well
        assertEquals(listOf('G', 't'), s.lettersOf(demon, gamma))
        val cell = Interpreter.readWords("${gamma}ab", s.tables(demon)).lines[0][0].cells[0]
        assertEquals("G", cell.text)
        assertEquals(listOf("t"), cell.alternates)
    }

    @Test
    fun theFirstKeyBeatsACapitalLetter() {
        // t has it as first key, G only as second key -> t first although G is a capital
        var s = KeymapState().clearLetter(demon, 't')
        s = s.addKey(demon, 't', "@@").state
        s = s.makePrimary(demon, 't', 0)
        s = s.addKey(demon, 'G', "@@").state             // second key of G, first key of t
        assertEquals(listOf('t', 'G'), s.lettersOf(demon, "@@"))
    }

    @Test
    fun severalMeaningsKeepTheirOrderAndEncodingStaysUnique() {
        var s = tAlsoGamma()
        s = s.addKey(demon, 'x', gamma).state
        assertEquals(listOf('G', 't', 'x'), s.lettersOf(demon, gamma))
        val t = s.tables(demon)
        assertEquals(listOf("t", "x"), Interpreter.tokenize(gamma, t).single().alternates)
        assertEquals("\u0263", Interpreter.encode("G", t))             // G still encodes to its first key
        assertEquals("\u0434", Interpreter.encode("d", t))             // untouched letters unchanged
    }

    @Test
    fun wordsShowEveryLetterWithItsFurtherMeanings() {
        val t = tAlsoGamma().tables(demon)
        val word = Interpreter.readWords("$gamma\u0449\u0449", t).lines[0][0]    // gamma + m + m
        assertEquals("Gmm", word.decoded)
        assertTrue(word.hasAlternates)
        assertEquals(listOf(listOf("t"), emptyList(), emptyList()), word.cells.map { it.alternates })
    }

    @Test
    fun wordsPutBackTogetherEqualTheNormalDecode() {
        val t = tAlsoGamma().tables(demon)
        val input = "$gamma\u00F1 \u0434\n\n\u0394\u00F1 q  \u65E5"
        val layout = Interpreter.readWords(input, t)
        assertEquals(3, layout.lines.size)
        assertTrue(layout.lines[1].isEmpty())
        val joined = layout.lines.joinToString("\n") { line -> line.joinToString(" ") { it.decoded } }
        assertEquals(Interpreter.decode(input.replace("  ", " "), t), joined)
        assertEquals("[1]", layout.lines[0][0].cells[1].text)          // unknown symbols numbered over the whole text
        assertEquals("[2]", layout.lines[2][2].cells[0].text)
    }

    @Test
    fun hugeTextsAreCutOff() {
        val t = tAlsoGamma().tables(demon)
        val layout = Interpreter.readWords("$gamma ".repeat(1000), t, maxWords = 50)
        assertTrue(layout.truncated)
        assertEquals(50, layout.lines.sumOf { it.size })
        assertFalse(Interpreter.readWords("$gamma $gamma", t).truncated)
    }

    @Test
    fun savedAndRestoredMapsKeepSharedKeys() {
        val s = tAlsoGamma()
        val back = KeymapJson.parse(KeymapJson.export(s, listOf(demon))) as KeymapImport.Success
        assertTrue(back.warnings.isEmpty())
        assertEquals(s.lettersOf(demon, gamma), KeymapState().importMaps(back.maps).lettersOf(demon, gamma))
        assertEquals(s, KeymapCodec.decode(KeymapCodec.encode(s)))
    }
}
