package com.wingedsheep.search

/**
 * Lexer for the Scryfall-flavoured search language.
 *
 * Mirrors `web-client/src/components/deckbuilder/query/lexer.ts` exactly — the
 * two implementations must agree on grammar (the frontend filters in-memory,
 * this module powers the `/api/cards/search` endpoint, and they share the
 * same docs).
 *
 * Decisions:
 *   - `(` / `)` always split a token, even without surrounding whitespace.
 *     Inside double quotes parens are literal.
 *   - `and` / `or` / `not` are keywords only when they appear as a standalone
 *     bareword (no leading `-` / `!`, no key/op).
 *   - Regex literals (`/.../[a-z]*`) are only recognised as the *value* of a
 *     `key OP` term. A bare `/foo/` outside a `key:` context is treated as a
 *     regular bareword (slashes appear in real card names).
 */
object Lexer {

    private val KEY_RE = Regex("^[a-zA-Z]+$")
    private val DIGIT = Regex("\\d")

    fun lex(query: String): List<Token> {
        val tokens = ArrayList<Token>()
        var i = 0
        val n = query.length

        while (i < n) {
            val ch = query[i]
            if (ch.isWhitespace()) { i++; continue }
            if (ch == '(') { tokens.add(Token(TokenKind.LParen, Span(i, i + 1))); i++; continue }
            if (ch == ')') { tokens.add(Token(TokenKind.RParen, Span(i, i + 1))); i++; continue }

            val start = i
            val buf = StringBuilder()
            var inQuote = false
            var quoteChar: Char? = null
            while (i < n) {
                val c = query[i]
                if (inQuote) {
                    buf.append(c)
                    if (c == quoteChar) { inQuote = false; quoteChar = null }
                    i++
                    continue
                }
                if (c == '"' || c == '\'') { inQuote = true; quoteChar = c; buf.append(c); i++; continue }
                if (c.isWhitespace()) break
                if (c == '(' || c == ')') break
                buf.append(c); i++
            }
            val span = Span(start, i)
            if (buf.isEmpty()) continue
            tokens.add(buildTerm(buf.toString(), span))
        }
        return tokens
    }

    private fun buildTerm(raw: String, span: Span): Token {
        val lc = raw.lowercase()
        if (raw.isNotEmpty() && raw[0] != '-' && raw[0] != '!') {
            when (lc) {
                "and" -> return Token(TokenKind.And, span)
                "or"  -> return Token(TokenKind.Or, span)
                "not" -> return Token(TokenKind.Not, span)
            }
        }

        var term = raw
        var negate = false
        var exact = false
        if (term.startsWith("-") && term.length > 1) { negate = true; term = term.substring(1) }
        if (term.startsWith("!") && term.length > 1) { exact = true; term = term.substring(1) }

        val match = findKeyOp(term)
        if (match != null) {
            val (key, op, valueStart) = match
            val parsed = parseValue(term.substring(valueStart), allowRegex = true)
            return Token(
                kind = TokenKind.Term,
                span = span,
                negate = negate,
                key = key.lowercase(),
                op = op,
                value = parsed.value,
                regex = parsed.regex,
                regexFlags = parsed.regexFlags,
                exact = exact,
            )
        }

        val parsed = parseValue(term, allowRegex = false)
        return Token(
            kind = TokenKind.Term,
            span = span,
            negate = negate,
            key = null,
            op = Op.EQ,
            value = parsed.value,
            regex = parsed.regex,
            regexFlags = parsed.regexFlags,
            exact = exact,
        )
    }

    private data class KeyOpMatch(val key: String, val op: Op, val valueStart: Int)

    private fun findKeyOp(term: String): KeyOpMatch? {
        for (i in term.indices) {
            for (op in Op.ORDERED) {
                if (i + op.text.length >= term.length) continue
                if (!term.regionMatches(i, op.text, 0, op.text.length)) continue
                val key = term.substring(0, i)
                if (KEY_RE.matches(key)) {
                    return KeyOpMatch(key, op, i + op.text.length)
                }
            }
        }
        return null
    }

    private data class ParsedValue(val value: String, val regex: Boolean, val regexFlags: String)

    private val REGEX_RE = Regex("^/(.+)/([a-zA-Z]*)$")

    private fun parseValue(raw: String, allowRegex: Boolean): ParsedValue {
        if (raw.length >= 2) {
            val first = raw[0]
            if ((first == '"' || first == '\'') && raw.last() == first) {
                return ParsedValue(raw.substring(1, raw.length - 1), regex = false, regexFlags = "")
            }
            if (allowRegex && first == '/') {
                val m = REGEX_RE.matchEntire(raw)
                if (m != null) {
                    return ParsedValue(m.groupValues[1], regex = true, regexFlags = m.groupValues[2])
                }
            }
        }
        // `DIGIT` is intentionally referenced so the unused-import lint doesn't
        // strip the helper used by sibling files in this module.
        @Suppress("UNUSED_VARIABLE")
        val touchKept = DIGIT
        return ParsedValue(raw, regex = false, regexFlags = "")
    }
}
