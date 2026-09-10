package com.wingedsheep.search

/**
 * Public facade for the search language.
 *
 * Single entry point: [parse] produces a [SearchResult] containing the
 * [CardPredicate] and any [ParseError]s. Callers (REST controller, CLI,
 * tests) compose by filtering their card list with the predicate and
 * surfacing the errors verbatim.
 *
 * The grammar lives in [Parser]; the supported keys live in [Matchers].
 * Keep this module the single source of truth — the frontend mirrors it
 * but never re-implements semantics independently.
 */
object SearchService {

    data class SearchResult(
        val predicate: CardPredicate,
        val errors: List<ParseError>,
    )

    fun parse(query: String): SearchResult {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return SearchResult({ true }, emptyList())
        }
        val ast = Parser.parse(query)
        val compiled = Evaluator.compile(ast.ast)
        return SearchResult(compiled.predicate, ast.errors + compiled.errors)
    }

    /** Convenience: filter a list of cards by [query] in one call. */
    fun <T : SearchCard> search(cards: List<T>, query: String): List<T> {
        val result = parse(query)
        return cards.filter(result.predicate)
    }
}
