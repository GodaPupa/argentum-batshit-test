package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.PEST_MONO_BLUE_TERROR_TELEMETRY_SCHEMA_SHA256
import com.wingedsheep.gym.matchup.PEST_V2_QUALIFIED_RUNNER
import com.wingedsheep.gym.matchup.PestControlTierOneMonoBlueTerrorTurnZeroPreflight
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/** Vectorless composition check only; the fixed fixture remains at zero submitted actions. */
class PestControlTierOneMonoBlueTerrorTurnZeroPreflightTest : FunSpec({
    test("complete Mono-Blue Terror turn-zero harness composition is green and remains fail closed") {
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }
        val result = PestControlTierOneMonoBlueTerrorTurnZeroPreflight.inspect(registry)

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
        result.telemetrySchemaSha256 shouldBe PEST_MONO_BLUE_TERROR_TELEMETRY_SCHEMA_SHA256
        result.artifactContractValidated shouldBe true
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesAuthorized shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("missing registry support fails closed without creating an official path") {
        val result = PestControlTierOneMonoBlueTerrorTurnZeroPreflight.inspect(CardRegistry())

        result.green shouldBe false
        result.errors.isNotEmpty() shouldBe true
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesAuthorized shouldBe 0
        result.outcomeExposure shouldBe 0
    }
})
