package com.wingedsheep.ai.engine

/**
 * Immutable policy version selected when a responder is constructed.
 *
 * The legacy option preserves the previously qualified two-simulation yes/no policy. It has no
 * completed-branch selection, affordability preflight or committed follow-up. All other generic
 * responders and advisor dispatch retain their existing behavior. This selector changes no engine
 * legality, decision validation, deck or card definition.
 */
enum class YesNoDecisionStrategy {
    COMPLETED_BRANCH_V1,
    LEGACY_SIMULATE_BOTH_V1,
}
