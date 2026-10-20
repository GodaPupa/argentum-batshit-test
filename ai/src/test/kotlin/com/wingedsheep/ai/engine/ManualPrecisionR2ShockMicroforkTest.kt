package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Exact archived R1 action-14 microfork ONLY. No new full games and no policy mutation. */
class ManualPrecisionR2ShockMicroforkTest : FunSpec({
    test("replay exact first 13 actions and compare real Breeding Pool Yes/No paths") {
        val input = Path.of(requireNotNull(System.getenv("MANUAL_R2_ORIGINAL_DIR")))
        val out = Path.of(requireNotNull(System.getenv("MANUAL_R2_OUTPUT")))
        Files.createDirectories(out)
        val gameDir = input.resolve("precision-r1-stage1-output/a-seed-202610202112")
        fun read(name: String) = Files.readString(gameDir.resolve(name))
        fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        check(Files.readString(input.resolve("precision-r1-stage1-output/source-sha.txt")).trim() ==
            "84e6347bbcdfa2990eb304051771007daf849ce9")
        val initialText = read("initial-state.json")
        check(sha(initialText.toByteArray(Charsets.UTF_8)) ==
            "cf00fb2f7c8f8cc7745639a081e403c9fc988d0cbb09d017a4130a92696a8adf")
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val raw = read("actions.jsonl").lineSequence().filter(String::isNotBlank)
            .map { json.parseToJsonElement(it).jsonObject }.toList()
        fun JsonObject.text(field: String) = get(field)?.jsonPrimitive?.contentOrNull
        fun JsonObject.number(field: String) = get(field)?.jsonPrimitive?.intOrNull
        val submissions = raw.filter { it.text("record") == "submit" && (it.number("index") ?: 0) <= 14 }
        val results = raw.filter { it.text("record") == "result" && (it.number("index") ?: 0) <= 14 }
            .associateBy { it.number("index") }
        check(submissions.mapNotNull { it.number("index") } == (1..14).toList())
        val original14 = submissions.last()
        check(original14.number("index") == 14 && original14.text("turn") == "1")
        check(original14.text("decisionSource") == "Breeding Pool")
        check(original14.text("actor") == "manual-mirror-0")
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        val processor = ActionProcessor(registry)
        val simulator = GameSimulator(registry)
        val actor = EntityId("manual-mirror-0")
        val elf = EntityId("e1")
        val pool = EntityId("e83")
        var state = json.decodeFromString<GameState>(initialText)
        var replayed = 0
        for (row in submissions.take(13)) {
            val index = requireNotNull(row.number("index"))
            val action = json.decodeFromString<GameAction>(requireNotNull(row.text("action")))
            val response = processor.process(state, action).result
            check(response.error == null) { "ORIGINAL_REPLAY_REJECTION action=" + index + " error=" + response.error }
            val expected = requireNotNull(results[index])
            val observedLife = state.turnOrder.map { response.state.lifeTotal(it) }
            val expectedLife = expected["life"]?.jsonArray?.map { it.jsonPrimitive.int }
            check(observedLife == expectedLife) { "Original replay life divergence index=" + index }
            val observedEvents = response.events.map { it.toString() }
            val expectedEvents = expected["events"]?.jsonArray?.map { it.jsonPrimitive.content }
            check(observedEvents == expectedEvents) {
                "Original replay event divergence index=" + index +
                    ", actual=" + observedEvents + ", expected=" + expectedEvents
            }
            state = response.state
            replayed++
        }
        check(replayed == 13 && state.turnNumber == 1 && state.activePlayerId == actor)
        check(state.getZone(ZoneKey(actor, Zone.HAND)).contains(elf))
        check(state.getEntity(elf)?.get<CardComponent>()?.name == "Arbor Elf")
        check(state.getEntity(pool)?.get<CardComponent>()?.name == "Breeding Pool")
        val choice = state.pendingDecision as? YesNoDecision
            ?: error("Exact R1 decision was not reconstructed as YesNoDecision")
        check(choice.id == "r0" && choice.playerId == actor)
        check(choice.context.sourceId == pool && choice.context.sourceName == "Breeding Pool")
        check(choice.prompt == "Pay 2 life to have Breeding Pool enter untapped?")
        check(state.lifeTotal(actor) == 40)
        val historic = json.decodeFromString<GameAction>(requireNotNull(original14.text("action")))
        check(historic is SubmitDecision &&
            (historic.response as? YesNoResponse)?.choice == false)

        val r5 = ManualTransmissionR5Candidate.profile
        check(r5.id == "manual-transmission-r5-survival-opt-in")
        check(AiProfile.CURRENT.advisorModules.isEmpty())
        val ai = AIPlayer.create(registry, actor, r5)
        val actualPolicyResponse = ai.respondToDecision(state, choice)
        check(actualPolicyResponse is YesNoResponse && !actualPolicyResponse.choice) {
            "R5 policy no longer reproduces original shock payment choice"
        }

        fun fork(pay: Boolean) = processor.process(
            state, SubmitDecision(actor, YesNoResponse(choice.id, pay))
        ).result.also { check(it.error == null) { "Real shock fork rejected pay=" + pay + ": " + it.error } }
        Files.writeString(out.resolve("state-before-r0.json"), json.encodeToString(state))
        val no = fork(false)
        val yes = fork(true)
        val expectedNoEvents = results.getValue(14)["events"]?.jsonArray
            ?.map { it.jsonPrimitive.content }
        check(no.events.map { it.toString() } == expectedNoEvents) {
            "Exact NO-payment events no longer match original"
        }
        check(no.state.lifeTotal(actor) == 40 && yes.state.lifeTotal(actor) == 38)
        check(no.state.getZone(ZoneKey(actor, Zone.BATTLEFIELD)).contains(pool))
        check(yes.state.getZone(ZoneKey(actor, Zone.BATTLEFIELD)).contains(pool))
        check(no.state.getEntity(pool)?.has<TappedComponent>() == true)
        check(yes.state.getEntity(pool)?.has<TappedComponent>() != true)
        check(no.state.getZone(ZoneKey(actor, Zone.HAND)).contains(elf))
        check(yes.state.getZone(ZoneKey(actor, Zone.HAND)).contains(elf))

        fun legalElfCast(next: GameState): CastSpell? = simulator
            .getLegalActions(next, actor)
            .firstOrNull { it.affordable && (it.action as? CastSpell)?.cardId == elf }
            ?.action as? CastSpell
        val cannotCast = legalElfCast(no.state)
        val canCast = legalElfCast(yes.state)
        check(cannotCast == null) { "Unexpected NO-branch affordable Arbor Elf cast" }
        check(canCast != null) { "Expected YES-branch turn-one Arbor Elf cast was not affordable" }
        val cast = processor.process(yes.state, canCast).result
        check(cast.error == null) { "YES-branch legal Arbor Elf cast was rejected: " + cast.error }
        Files.writeString(out.resolve("state-after-no.json"), json.encodeToString(no.state))
        Files.writeString(out.resolve("state-after-yes.json"), json.encodeToString(yes.state))
        val summary = buildJsonObject {
            put("status", "R2_EXACT_REAL_ENGINE_MICROFORK")
            put("originalRun", 39477314282L); put("originalArtifact", 12290286293L)
            put("sourceCommit", "84e6347bbcdfa2990eb304051771007daf849ce9")
            put("seed", 202610202112L); put("arm", "a"); put("actor", actor.value)
            put("originalDecisionAction", 14); put("originalActionsReplayedExactly", replayed)
            put("originalStateSha256", sha(initialText.toByteArray(Charsets.UTF_8)))
            put("r5RespondedNo", true)
            put("noPaymentLife", no.state.lifeTotal(actor))
            put("yesPaymentLife", yes.state.lifeTotal(actor))
            put("noPaymentTapped", no.state.getEntity(pool)?.has<TappedComponent>() == true)
            put("yesPaymentTapped", yes.state.getEntity(pool)?.has<TappedComponent>() == true)
            put("noPaymentAffordableArborElf", cannotCast != null)
            put("yesPaymentAffordableArborElf", canCast != null)
            put("yesArborElfEngineCastAccepted", cast.error == null)
            put("yesArborElfCastEvents", JsonArray(cast.events.map { JsonPrimitive(it.toString()) }))
            put("noEventsMatchOriginal", true)
            put("productionPolicyChanges", 0); put("newFullGames", 0)
            put("conclusion", "A legal turn-one Elf acceleration line was available after two-life payment; policy value needs strategic review.")
        }
        Files.writeString(out.resolve("r2-primary-microfork.json"), summary.toString())
        println("MANUAL_PRECISION_R2_PRIMARY_RESULT=" + summary)
    }
})
