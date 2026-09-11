package com.wingedsheep.ai.engine.advisor

/**
 * Registry that maps card names to their [CardAdvisor] instances.
 *
 * Advisors are registered via [CardAdvisorModule]s, typically one per set.
 * Lookup is O(1) by card name.
 *
 * **One advisor per card.** Lookup is a single map read, so a card claimed by two
 * advisors silently loses whichever registered first — including its `evaluateCast`
 * even when the winner only overrides `respondToDecision`. [register] therefore
 * throws on collision; combine the two advisors into one instead (see
 * `GiftCombatTrickAdvisor`, which delegates `evaluateCast` to `CombatTrickAdvisor`).
 */
class CardAdvisorRegistry {
    private val advisors = mutableMapOf<String, CardAdvisor>()

    fun register(advisor: CardAdvisor) {
        for (name in advisor.cardNames) {
            val existing = advisors[name]
            require(existing == null) {
                "Duplicate CardAdvisor registration for \"$name\": " +
                    "${existing!!::class.simpleName} and ${advisor::class.simpleName}. " +
                    "A card may have at most one advisor — merge them, or delegate one to the other."
            }
            advisors[name] = advisor
        }
    }

    /**
     * Named advisors keep precedence. Cards without one still pass through the small structural
     * resource-discipline layer, which returns null for shapes it does not recognize.
     */
    fun getAdvisor(cardName: String): CardAdvisor? = advisors[cardName] ?: ResourceDisciplineAdvisor
}
