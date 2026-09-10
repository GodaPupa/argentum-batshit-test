package com.wingedsheep.tooling.coverage.dashboard

/**
 * A small, line-based Kotlin syntax highlighter for the card-detail code view. It tokenizes the
 * emitted `cardDef` source into coloured spans rather than returning pre-coloured strings, so the
 * pane renderer can truncate a long line to its width token-by-token without an ANSI escape ever
 * being split. Block comments and triple-quoted strings carry their open state across lines.
 *
 * Not a full Kotlin grammar — just enough lexing (keywords, strings, comments, numbers, types,
 * call sites, annotations) to make a generated card pleasant to read at a glance.
 */
object KotlinHighlight {
    data class Tok(val text: String, val color: Int?)

    // colour roles (256-colour indices), chosen to read well over a dark terminal
    private const val KEYWORD = 176   // purple
    private const val TYPE = 80       // teal — PascalCase identifiers (SDK types)
    private const val CALL = 222      // light yellow — function call sites
    private const val STRING = 114    // green
    private const val NUMBER = 215    // orange
    private const val COMMENT = 244   // grey
    private const val ANNOTATION = 215

    private val KEYWORDS = setOf(
        "package", "import", "val", "var", "fun", "object", "class", "interface", "enum", "sealed",
        "data", "companion", "return", "if", "else", "when", "is", "in", "as", "by", "for", "while",
        "do", "try", "catch", "finally", "throw", "true", "false", "null", "this", "super", "it",
        "private", "internal", "public", "protected", "override", "open", "abstract", "const",
        "lateinit", "vararg", "out", "typealias",
    )

    /** Tokenize [text] into per-line spans (one inner list per source line). */
    fun highlight(text: String): List<List<Tok>> {
        val out = ArrayList<List<Tok>>()
        var inBlock = false
        var inTriple = false
        for (line in text.split("\n")) {
            val toks = ArrayList<Tok>()
            var i = 0
            val n = line.length
            while (i < n) {
                if (inBlock) {
                    val end = line.indexOf("*/", i)
                    if (end < 0) { toks.add(Tok(line.substring(i), COMMENT)); i = n }
                    else { toks.add(Tok(line.substring(i, end + 2), COMMENT)); i = end + 2; inBlock = false }
                    continue
                }
                if (inTriple) {
                    val end = line.indexOf("\"\"\"", i)
                    if (end < 0) { toks.add(Tok(line.substring(i), STRING)); i = n }
                    else { toks.add(Tok(line.substring(i, end + 3), STRING)); i = end + 3; inTriple = false }
                    continue
                }
                val c = line[i]
                when {
                    c == '/' && i + 1 < n && line[i + 1] == '/' -> { toks.add(Tok(line.substring(i), COMMENT)); i = n }
                    c == '/' && i + 1 < n && line[i + 1] == '*' -> {
                        val end = line.indexOf("*/", i + 2)
                        if (end < 0) { toks.add(Tok(line.substring(i), COMMENT)); inBlock = true; i = n }
                        else { toks.add(Tok(line.substring(i, end + 2), COMMENT)); i = end + 2 }
                    }
                    c == '"' && i + 2 < n && line[i + 1] == '"' && line[i + 2] == '"' -> {
                        val end = line.indexOf("\"\"\"", i + 3)
                        if (end < 0) { toks.add(Tok(line.substring(i), STRING)); inTriple = true; i = n }
                        else { toks.add(Tok(line.substring(i, end + 3), STRING)); i = end + 3 }
                    }
                    c == '"' -> {
                        var j = i + 1
                        while (j < n) {
                            if (line[j] == '\\') { j += 2; continue }
                            if (line[j] == '"') { j++; break }
                            j++
                        }
                        toks.add(Tok(line.substring(i, minOf(j, n)), STRING)); i = minOf(j, n)
                    }
                    c == '@' && i + 1 < n && line[i + 1].isLetter() -> {
                        var j = i + 1
                        while (j < n && (line[j].isLetterOrDigit() || line[j] == '_')) j++
                        toks.add(Tok(line.substring(i, j), ANNOTATION)); i = j
                    }
                    c.isLetter() || c == '_' -> {
                        var j = i
                        while (j < n && (line[j].isLetterOrDigit() || line[j] == '_')) j++
                        val word = line.substring(i, j)
                        val color = when {
                            word in KEYWORDS -> KEYWORD
                            word[0].isUpperCase() -> TYPE
                            j < n && line[j] == '(' -> CALL
                            else -> null
                        }
                        toks.add(Tok(word, color)); i = j
                    }
                    c.isDigit() -> {
                        var j = i
                        while (j < n && (line[j].isDigit() || line[j] == '.' || line[j] == '_')) j++
                        toks.add(Tok(line.substring(i, j), NUMBER)); i = j
                    }
                    else -> { toks.add(Tok(c.toString(), null)); i++ }
                }
            }
            out.add(toks)
        }
        return out
    }

    /** Render one tokenized line to ANSI, truncating with an ellipsis to exactly [width] columns. */
    fun renderLine(toks: List<Tok>, width: Int): String {
        if (width <= 0) return ""
        val sb = StringBuilder()
        var vis = 0
        for (t in toks) {
            if (vis >= width) break
            val room = width - vis
            val truncated = t.text.length > room
            val text = if (!truncated) t.text else if (room == 1) "…" else t.text.take(room - 1) + "…"
            sb.append(if (t.color != null) Ansi.fg(t.color) + text + Ansi.RESET else text)
            vis += text.length
            if (truncated) break
        }
        if (vis < width) sb.append(" ".repeat(width - vis))
        return sb.toString()
    }
}
