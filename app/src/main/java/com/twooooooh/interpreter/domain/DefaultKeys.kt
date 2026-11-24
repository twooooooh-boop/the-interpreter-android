package com.twooooooh.interpreter.domain

/**
 * The built-in tables turned around: plain letter -> list of keys.
 * The first key of a list is the primary one (used when encoding). Keys that contain a lowercase
 * ASCII letter come first (same preference the original encoder had), then plain code-point order.
 */
object DefaultKeys {
    private val cache = HashMap<CipherLanguage, Map<Char, List<String>>>()

    @Synchronized
    fun of(language: CipherLanguage): Map<Char, List<String>> =
        cache.getOrPut(language) { invert(BuiltinMaps.of(language)) }

    internal fun invert(map: Map<String, String>): Map<Char, List<String>> {
        val grouped = LinkedHashMap<Char, MutableList<String>>()
        for ((symbol, letter) in map) {
            grouped.getOrPut(letter.single()) { mutableListOf() }.add(symbol)
        }
        val order = Comparator<String> { a, b ->
            val al = hasAsciiLower(a)
            val bl = hasAsciiLower(b)
            when {
                al != bl -> if (al) -1 else 1
                else -> a.compareTo(b)
            }
        }
        return grouped.mapValues { (_, keys) -> keys.sortedWith(order) }
    }

    private fun hasAsciiLower(s: String) = s.any { it in 'a'..'z' }
}
