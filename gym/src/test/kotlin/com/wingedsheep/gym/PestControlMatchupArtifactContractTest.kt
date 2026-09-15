package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.MatchupEnvironmentIdentity
import com.wingedsheep.gym.matchup.MatchupProvenance
import com.wingedsheep.gym.matchup.MatchupRawGame
import com.wingedsheep.gym.matchup.PEST_MATCHUP_SCHEMA
import com.wingedsheep.gym.matchup.PREBOARD_MATCH_RESULT
import com.wingedsheep.gym.matchup.PestControlMatchupArtifactCodec
import com.wingedsheep.gym.matchup.PestSeat
import com.wingedsheep.gym.matchup.StartingDeck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val ARTIFACT_GATE3_HEAD = "b6fc0fb6fc31efa2148e3e3174782d267656e44c"

class PestControlMatchupArtifactContractTest : FunSpec({
    val sparseJson = Json { encodeDefaults = false }
    val tolerantJson = Json { ignoreUnknownKeys = true }
    val explicitJson = Json { encodeDefaults = true }
    val raw = MatchupRawGame(
        provenance = MatchupProvenance(
            sourceCommit = ARTIFACT_GATE3_HEAD,
            pestSeat = PestSeat.SEAT_ZERO,
            startingDeck = StartingDeck.PEST_CONTROL,
            environment = MatchupEnvironmentIdentity.current(),
        ),
        fixtureId = "NONEXPERIMENTAL_GATE4_ARTIFACT",
        openingZones = emptyList(),
        mulligans = emptyList(),
        priorityActions = emptyList(),
    )

    test("canonical JSON report manifest and deterministic compression reconcile") {
        val first = PestControlMatchupArtifactCodec.build(raw)
        val second = PestControlMatchupArtifactCodec.build(raw)
        first.rawJson.contentEquals(second.rawJson).shouldBeTrue()
        first.compressed.contentEquals(second.compressed).shouldBeTrue()
        first.report.contentEquals(second.report).shouldBeTrue()
        first.manifest.contentEquals(second.manifest).shouldBeTrue()
        PestControlMatchupArtifactCodec.verify(first).shouldBeEmpty()
        first.report.decodeToString().contains(PREBOARD_MATCH_RESULT).shouldBeTrue()
    }

    test("tampering with any derived artifact is rejected") {
        val bundle = PestControlMatchupArtifactCodec.build(raw)
        val tampered = bundle.copy(report = bundle.report + '!'.code.toByte())
        PestControlMatchupArtifactCodec.verify(tampered) shouldBe listOf(
            "report hash mismatch", "report is not derived from raw JSON",
        )
    }

    test("historical default fields decode and explicit values round trip") {
        val encoded = sparseJson.encodeToString(raw)
        val decoded = tolerantJson.decodeFromString<MatchupRawGame>(encoded)
        decoded.fixtureIsNonexperimental.shouldBeTrue()
        decoded.excludedFromFutureSeedOverlapRegistry.shouldBeTrue()
        decoded.provenance.matchResult shouldBe PREBOARD_MATCH_RESULT
        decoded.provenance.schema shouldBe PEST_MATCHUP_SCHEMA
        val explicit = explicitJson.encodeToString(decoded)
        tolerantJson.decodeFromString<MatchupRawGame>(explicit) shouldBe decoded
    }

    test("human report is derived exclusively by decoding canonical raw JSON") {
        val bundle = PestControlMatchupArtifactCodec.build(raw)
        PestControlMatchupArtifactCodec.renderReport(bundle.rawJson).toByteArray()
            .contentEquals(bundle.report).shouldBeTrue()
    }
})
