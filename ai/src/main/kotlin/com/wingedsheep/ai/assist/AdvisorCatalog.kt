package com.wingedsheep.ai.assist

/**
 * Registry of the available AI-assist engines. The server's `AiAssistController` reads this to
 * populate the engine dropdowns and to dispatch a suggest-pick / auto-build request to the engine
 * the player chose. Plain Kotlin (no Spring) — the `ai` module has no framework dependency.
 *
 * To add an engine (LLM, MCTS, remote NN): implement [DraftAdvisor] / [DeckBuildAdvisor] and add it
 * to the relevant list here. Nothing else in the server or client needs to change.
 */
object AdvisorCatalog {

    val draftAdvisors: List<DraftAdvisor> = listOf(
        HeuristicDraftAdvisor,
        DraftsimDraftAdvisor,
    )

    val deckBuildAdvisors: List<DeckBuildAdvisor> = listOf(
        HeuristicDeckBuildAdvisor,
        DraftsimDeckBuildAdvisor,
    )

    /** The default engine id (first registered) when the client doesn't specify one. */
    val defaultDraftAdvisorId: String get() = draftAdvisors.first().id
    val defaultDeckBuildAdvisorId: String get() = deckBuildAdvisors.first().id

    fun draftAdvisor(id: String?): DraftAdvisor =
        draftAdvisors.firstOrNull { it.id == id } ?: draftAdvisors.first()

    fun deckBuildAdvisor(id: String?): DeckBuildAdvisor =
        deckBuildAdvisors.firstOrNull { it.id == id } ?: deckBuildAdvisors.first()
}
