package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Composition checks use preserved engine checkpoints; original regression files stay unchanged. */
class ManualR4CompositionArchiveTest : FunSpec({
    val registry = EncoreArchive.registry()
    val processor = ActionProcessor(registry)
    val output = (System.getenv("MANUAL_R4_COMPOSITION_OUTPUT")?.let(Path::of)
        ?: EncoreArchive.root.resolve("ai/build/manual-r4-composition")).resolve("archives")
    Files.createDirectories(output)
    test("composed pilot submits accepted archived Encore required defender split") {
        val state = EncoreArchive.savedState()
        val actor = EncoreArchive.rejected().playerId
        val action = AIPlayer.create(registry, actor, ManualTransmissionR4Candidate.profile).chooseAction(state) as DeclareAttackers
        action.attackers[EntityId("e756")] shouldBe EntityId("manual-mirror-1")
        action.attackers[EntityId("e757")] shouldBe EntityId("manual-mirror-3")
        val result = processor.process(state, action).result
        result.error shouldBe null
        Files.writeString(output.resolve("encore-action.json"), EncoreArchive.json.encodeToString<GameAction>(action))
        Files.writeString(output.resolve("encore-after.json"), EncoreArchive.json.encodeToString(result.state))
    }
    test("composed pilot exits archived counter-only Statue return through a real legal selection") {
        val path = EncoreArchive.root.resolve("gameplay/manual-r3/evidence/statue-second-regressions.zip")
        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) } shouldBe
            "9746bae6c9a6b86f1b5c5da95053028b3ec6b8a6ad75f792b60c93dd08b68d02"
        val state = ZipFile(path.toFile()).use { zip -> EncoreArchive.json.decodeFromString<GameState>(
            zip.getInputStream(zip.getEntry("r3-output/regressions/first-return-state.json")).bufferedReader().readText()) }
        val decision = requireNotNull(state.pendingDecision)
        val actor = decision.playerId
        val response = AIPlayer.create(registry, actor, ManualTransmissionR4Candidate.profile).respondToDecision(state, decision)
        val selected = response as CardsSelectedResponse
        check(selected.selectedCards.isNotEmpty())
        check(selected.selectedCards.none { state.getEntity(it)?.get<CardComponent>()?.name == "Ancestral Statue" })
        val action = SubmitDecision(actor, response)
        val result = processor.process(state, action).result
        result.error shouldBe null
        check(EntityId("e341") in result.state.getZone(ZoneKey(actor, Zone.BATTLEFIELD)))
        Files.writeString(output.resolve("statue-exit-action.json"), EncoreArchive.json.encodeToString<GameAction>(action))
        Files.writeString(output.resolve("statue-exit-after.json"), EncoreArchive.json.encodeToString(result.state))
    }
    test("composition has explicit profile identity with distinct advisor ownership and opt in defenders") {
        val profile = ManualTransmissionR4Candidate.profile
        profile.id shouldBe "manual-transmission-r4-composed"
        profile.respectRequiredAttackDefenders shouldBe true
        profile.advisorModules.size shouldBe 2
        check(!AiProfile.CURRENT.respectRequiredAttackDefenders)
        check(AiProfile.CURRENT.advisorModules.isEmpty())
    }
})
