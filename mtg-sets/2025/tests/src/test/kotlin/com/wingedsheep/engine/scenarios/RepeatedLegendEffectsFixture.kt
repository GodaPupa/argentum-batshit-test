package com.wingedsheep.engine.scenarios

import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.LegendRuleDoesNotApplyTo

/**
 * Explicit deterministic setup for testing two same-name legendary effects together.
 * The canonical Gogo and Water Crystal definitions remain unchanged. Without an exemption,
 * CR 704.5j requires a legend choice before either scenario can receive priority. This fixture
 * uses the existing exemption primitive; it is not a canonical card or a research pool entry.
 */
internal val RepeatedLegendEffectsFixture = card("Repeated Legendary Effects Test Exemption") {
    manaCost = "{0}"
    typeLine = "Artifact"
    staticAbility {
        ability = LegendRuleDoesNotApplyTo(GameObjectFilter.Permanent)
    }
}
