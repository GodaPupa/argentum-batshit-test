package com.wingedsheep.sdk.serialization

import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.scripting.targets.TargetOpponent
import com.wingedsheep.sdk.scripting.targets.TargetPlayer
import com.wingedsheep.sdk.scripting.targets.TargetRequirement
import com.wingedsheep.sdk.scripting.targets.withId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * Round-trip + description tests for the player-target `restriction` slot (TargetUnion PR 1).
 * The restriction and its [com.wingedsheep.sdk.scripting.references.Player.Candidate] reference
 * ride inside `CardDefinition`, so they must serialize and survive a JSON round-trip.
 */
class PlayerTargetRestrictionSerializationTest : DescribeSpec({

    val json = CardSerialization.json

    describe("TargetPlayer / TargetOpponent restriction") {

        it("round-trips a TargetPlayer carrying a Player.Candidate restriction") {
            val original: TargetRequirement = TargetPlayer(
                restriction = Conditions.candidateLifeAtMost(10),
                descriptionOverride = "target player with 10 or less life"
            )
            val restored = json.decodeFromString(
                TargetRequirement.serializer(),
                json.encodeToString(TargetRequirement.serializer(), original)
            )
            restored shouldBe original
            (restored as TargetPlayer).restriction shouldBe Conditions.candidateLifeAtMost(10)
        }

        it("round-trips a TargetOpponent carrying a LIFE_LOST tracker restriction") {
            val original: TargetRequirement = TargetOpponent(
                restriction = Conditions.candidateLostLifeThisTurn(),
                descriptionOverride = "target opponent who lost life this turn"
            )
            val restored = json.decodeFromString(
                TargetRequirement.serializer(),
                json.encodeToString(TargetRequirement.serializer(), original)
            )
            restored shouldBe original
        }

        it("renders the descriptionOverride for a restricted target") {
            TargetPlayer(
                restriction = Conditions.candidateLostLifeThisTurn(),
                descriptionOverride = "target player who lost life this turn"
            ).description shouldBe "target player who lost life this turn"
        }

        it("preserves the restriction when an id is stamped via withId") {
            val req = TargetPlayer(restriction = Conditions.candidateLifeAtMost(5))
            val withId = req.withId("victim")
            (withId as TargetPlayer).restriction shouldBe Conditions.candidateLifeAtMost(5)
            withId.id shouldBe "victim"
        }
    }
})
