package com.wingedsheep.search

/**
 * Mana-cost-symbol multiset parsing & comparison for `mana:` / `m:` queries.
 *
 * Accepts both `{2}{u}{u}` and `2uu`. Comparators:
 *   `:` / `=`  card mana == requested
 *   `>=`       card mana ⊇ requested
 *   `<=`       card mana ⊆ requested
 *   `>`        ⊇ and not equal
 *   `<`        ⊆ and not equal
 *
 * Hybrid (`{u/r}`) and Phyrexian (`{w/p}`) symbols match literally — we don't
 * expand them. Mirrors the frontend `manaCost.ts`.
 */
object ManaCostMatch {

    fun parseQueryManaSymbols(value: String): List<String> {
        val symbols = ArrayList<String>()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            when {
                ch == '{' -> {
                    val close = value.indexOf('}', i + 1)
                    if (close == -1) break
                    symbols += value.substring(i + 1, close).uppercase()
                    i = close + 1
                }
                ch.isWhitespace() -> i++
                ch.isDigit() -> {
                    var j = i
                    while (j < value.length && value[j].isDigit()) j++
                    symbols += value.substring(i, j)
                    i = j
                }
                ch.isLetter() -> {
                    symbols += ch.uppercase()
                    i++
                }
                else -> i++
            }
        }
        return symbols
    }

    fun parseCardManaSymbols(manaCost: String): List<String> {
        val symbols = ArrayList<String>()
        val regex = Regex("\\{([^}]+)}")
        for (m in regex.findAll(manaCost)) symbols += m.groupValues[1].uppercase()
        return symbols
    }

    private fun bag(symbols: List<String>): Map<String, Int> =
        symbols.groupingBy { it }.eachCount()

    fun matches(op: Op, queryValue: String, cardManaCost: String): Boolean {
        val wanted = bag(parseQueryManaSymbols(queryValue))
        val have = bag(parseCardManaSymbols(cardManaCost))
        return when (op) {
            Op.EQ, Op.EXACT -> have == wanted
            Op.GE -> wanted.subsetOf(have)
            Op.LE -> have.subsetOf(wanted)
            Op.GT -> wanted.subsetOf(have) && have != wanted
            Op.LT -> have.subsetOf(wanted) && have != wanted
            Op.NEQ -> have != wanted
        }
    }

    private fun Map<String, Int>.subsetOf(other: Map<String, Int>): Boolean =
        this.all { (k, v) -> (other[k] ?: 0) >= v }
}
