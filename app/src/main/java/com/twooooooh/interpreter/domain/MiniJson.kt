package com.twooooooh.interpreter.domain

class JsonException(message: String) : Exception(message)

/**
 * A small, strict JSON reader (no Android classes, so it can be unit tested on the JVM).
 * Objects become LinkedHashMap<String, Any?>, arrays List<Any?>, numbers Double,
 * strings String, booleans Boolean and null stays null.
 */
internal object MiniJson {
    private const val MAX_DEPTH = 32

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWhitespace()
        val value = p.readValue(0)
        p.skipWhitespace()
        if (!p.atEnd()) throw JsonException("Unexpected data after the JSON value")
        return value
    }

    fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c.code < 0x20 -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length

        fun skipWhitespace() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r')) i++
        }

        fun readValue(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw JsonException("JSON is nested too deeply")
            if (atEnd()) throw JsonException("Unexpected end of file")
            return when (val c = s[i]) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c in '0'..'9') readNumber() else throw JsonException("Unexpected character '$c' at $i")
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!s.startsWith(word, i)) throw JsonException("Unexpected text at $i")
            i += word.length
            return value
        }

        private fun readNumber(): Double {
            val start = i
            while (i < s.length && (s[i] in '0'..'9' || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDoubleOrNull() ?: throw JsonException("Bad number at $start")
        }

        private fun readString(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonException("Unterminated string")
                val c = s[i++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (atEnd()) throw JsonException("Unterminated string")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw JsonException("Bad \\u escape")
                                val code = s.substring(i, i + 4).toIntOrNull(16) ?: throw JsonException("Bad \\u escape")
                                sb.append(code.toChar())
                                i += 4
                            }
                            else -> throw JsonException("Bad escape \\$e")
                        }
                    }
                    c.code < 0x20 -> throw JsonException("Control character in string")
                    else -> sb.append(c)
                }
            }
        }

        private fun readArray(depth: Int): List<Any?> {
            i++ // [
            val out = ArrayList<Any?>()
            skipWhitespace()
            if (!atEnd() && s[i] == ']') { i++; return out }
            while (true) {
                skipWhitespace()
                out.add(readValue(depth + 1))
                skipWhitespace()
                if (atEnd()) throw JsonException("Unterminated array")
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return out
                    else -> throw JsonException("Expected , or ] at ${i - 1}")
                }
            }
        }

        private fun readObject(depth: Int): Map<String, Any?> {
            i++ // {
            val out = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (!atEnd() && s[i] == '}') { i++; return out }
            while (true) {
                skipWhitespace()
                if (atEnd() || s[i] != '"') throw JsonException("Expected a key at $i")
                val key = readString()
                skipWhitespace()
                if (atEnd() || s[i++] != ':') throw JsonException("Expected : after key")
                skipWhitespace()
                out[key] = readValue(depth + 1)
                skipWhitespace()
                if (atEnd()) throw JsonException("Unterminated object")
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return out
                    else -> throw JsonException("Expected , or } at ${i - 1}")
                }
            }
        }
    }
}
