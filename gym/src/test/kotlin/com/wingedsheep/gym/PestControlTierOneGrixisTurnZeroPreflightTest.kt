package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.PEST_GRIXIS_TELEMETRY_SCHEMA_SHA256
import com.wingedsheep.gym.matchup.PEST_V2_QUALIFIED_RUNNER
import com.wingedsheep.gym.matchup.PestControlTierOneGrixisTurnZeroPreflight
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/** Vectorless composition check only; the fixed fixture remains at zero submitted actions. */
class PestControlTierOneGrixisTurnZeroPreflightTest : FunSpec({
    test("complete Grixis turn-zero harness composition is green and remains fail closed") {
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }
        val result = PestControlTierOneGrixisTurnZeroPreflight.inspect(registry)

        result.errors shouldBe emptyList()
        result.green shouldBe true
        result.activationBlockers.shouldContainExactly(
            "smoke harness is not AUTHORIZED",
            "smoke vector is not frozen",
            "game adapter has no official initialization method",
            "no execution method is defined",
        )
        result.qualifiedRunner shouldBe PEST_V2_QUALIFIED_RUNNER
        result.constructionStepCount shouldBe 0
        result.telemetrySchemaSha256 shouldBe PEST_GRIXIS_TELEMETRY_SCHEMA_SHA256
        result.artifactContractValidated shouldBe true
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesAuthorized shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("missing registry support fails closed without creating an official path") {
        val result = PestControlTierOneGrixisTurnZeroPreflight.inspect(CardRegistry())

        result.green shouldBe false
        result.errors.isNotEmpty() shouldBe true
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesAuthorized shouldBe 0
        result.outcomeExposure shouldBe 0
    }
})
