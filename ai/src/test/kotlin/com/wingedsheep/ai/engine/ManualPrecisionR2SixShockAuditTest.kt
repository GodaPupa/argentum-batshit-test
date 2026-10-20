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

/**
 * Pure archival replay and exact-state YES/NO micro-forks of all six observed R1 shockland choices.
 * NO new complete games, no AI policy changes and no approval of Challenger A.
 */
class ManualPrecisionR2SixShockAuditTest : FunSpec({
    test("reconstruct six historical shock choices from two exact original A archives") {
        val out = Path.of(requireNotNull(System.getenv("MANUAL_R2_SIX_OUTPUT")))
        Files.createDirectories(out)
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        val processor = ActionProcessor(registry)
        val simulator = GameSimulator(registry)
        val r5 = ManualTransmissionR5Candidate.profile
        check(r5.id == "manual-transmission-r5-survival-opt-in")
        check(AiProfile.CURRENT.advisorModules.isEmpty())
        fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        fun JsonObject.string(k: String) = get(k)?.jsonPrimitive?.contentOrNull
        fun JsonObject.number(k: String) = get(k)?.jsonPrimitive?.intOrNull
        fun casts(state: GameState, actor: EntityId): List<String> {
            val hand = state.getZone(ZoneKey(actor, Zone.HAND)).toSet()
            return simulator.getLegalActions(state, actor)
                .filter { it.affordable && (it.action as? CastSpell)?.cardId in hand }
                .mapNotNull { candidate ->
                    (candidate.action as? CastSpell)?.cardId?.let { id ->
                        state.getEntity(id)?.get<CardComponent>()?.name
                    }
                }.distinct().sorted()
        }
        val investigations = mutableListOf<JsonElement>()
        val replayReceipts = mutableListOf<JsonElement>()
        val specs = listOf(
            Triple("202610202111", listOf(468, 555, 599),
                "ca7032465c59f44966fef3c1e780e1861c73587e2f93f094f5f962c8d108fb89"),
            Triple("202610202112", listOf(14, 273, 1002),
                "cf00fb2f7c8f8cc7745639a081e403c9fc988d0cbb09d017a4130a92696a8adf")
        )
        for ((seed, indices, initialHash) in specs) {
            val base = Path.of(requireNotNull(System.getenv("MANUAL_R2_ARCHIVE_" + seed)))
            val pref = base.resolve("precision-r1-stage1-output/a-seed-" + seed)
            check(Files.readString(base.resolve("precision-r1-stage1-output/source-sha.txt")).trim() ==
                "84e6347bbcdfa2990eb304051771007daf849ce9")
            val initialText = Files.readString(pref.resolve("initial-state.json"))
            check(sha(initialText.toByteArray()) == initialHash)
            val rows = Files.readAllLines(pref.resolve("actions.jsonl"))
                .filter(String::isNotBlank)
                .map { json.parseToJsonElement(it).jsonObject }
            val end = indices.max()
            val submissions = rows.filter { it.string("record") == "submit" &&
                (it.number("index") ?: Int.MAX_VALUE) <= end }
            val originals = rows.filter { it.string("record") == "result" &&
                (it.number("index") ?: Int.MAX_VALUE) <= end }.associateBy { it.number("index") }
            check(submissions.size == end && submissions.mapNotNull { it.number("index") } == (1..end).toList())
            var state = json.decodeFromString<GameState>(initialText)
            var replayed = 0
            var exactEventMatches = 0
            val abilityBindings = mutableListOf<JsonElement>()
            val mismatchedEvents = mutableListOf<Int>()
            for (row in submissions) {
                val index = requireNotNull(row.number("index"))
                val actor = EntityId(requireNotNull(row.string("actor")))
                val archived = json.decodeFromString<GameAction>(requireNotNull(row.string("action")))
                if (index in indices) {
                    check(state.turnNumber == row.number("turn"))
                    val decision = state.pendingDecision as? YesNoDecision
                        ?: error("Missing real shock YesNoDecision before historical action " + index)
                    val expectedName = requireNotNull(row.string("decisionSource"))
                    check(decision.context.sourceName == expectedName)
                    check(decision.playerId == actor && decision.id == (archived as SubmitDecision).response.decisionId)
                    check(decision.prompt.startsWith("Pay 2 life to have "))
                    check((archived.response as? YesNoResponse)?.choice == false)
                    val source = requireNotNull(decision.context.sourceId)
                    val historicLife = state.lifeTotal(actor)
                    val ai = AIPlayer.create(registry, actor, r5)
                    val policyReply = ai.respondToDecision(state, decision) as? YesNoResponse
                        ?: error("No R5 Yes/No response for historical decision " + index)
                    fun fork(yes: Boolean) = processor.process(
                        state, SubmitDecision(actor, YesNoResponse(decision.id, yes))
                    ).result.also { check(it.error == null) { "Real fork rejected at " + index + ": " + it.error } }
                    val no = fork(false)
                    val yes = fork(true)
                    check(no.state.lifeTotal(actor) == historicLife &&
                        yes.state.lifeTotal(actor) == historicLife - 2)
                    check(no.state.getEntity(source)?.has<TappedComponent>() == true)
                    check(yes.state.getEntity(source)?.has<TappedComponent>() != true)
                    val noCast = casts(no.state, actor)
                    val yesCast = casts(yes.state, actor)
                    val unlocked = yesCast.filter { it !in noCast }
                    val originalResultEvents = originals.getValue(index)?.get("events")?.jsonArray
                        ?.map { it.jsonPrimitive.content }
                    check(no.events.map { it.toString() } == originalResultEvents)
                    val details = buildJsonObject {
                        put("seed", seed); put("index", index)
                        put("globalTurn", state.turnNumber); put("actor", actor.value)
                        put("land", expectedName); put("sourceEntity", source.value)
                        put("lifeBefore", historicLife); put("noPaymentLife", no.state.lifeTotal(actor))
                        put("yesPaymentLife", yes.state.lifeTotal(actor))
                        put("r5ChosePay", policyReply.choice)
                        put("noTapped", no.state.getEntity(source)?.has<TappedComponent>() == true)
                        put("yesTapped", yes.state.getEntity(source)?.has<TappedComponent>() == true)
                        put("affordableHandSpellsNo", JsonArray(noCast.map(::JsonPrimitive)))
                        put("affordableHandSpellsYes", JsonArray(yesCast.map(::JsonPrimitive)))
                        put("newlyAffordableIfPay", JsonArray(unlocked.map(::JsonPrimitive)))
                        put("classification", if (unlocked.isEmpty())
                            "INSUFFICIENT_EVIDENCE_OF_SUBOPTIMALITY" else
                            "POTENTIAL_MISSED_TEMPO_REQUIRES_OPPORTUNITY_COST_REVIEW")
                        put("exactOriginalNoEventsMatch", true)
                    }
                    investigations += details
                    Files.writeString(out.resolve("state-before-" + seed + "-action-" + index + ".json"), json.encodeToString(state))
                    Files.writeString(out.resolve("state-yes-" + seed + "-action-" + index + ".json"), json.encodeToString(yes.state))
                    Files.writeString(out.resolve("state-no-" + seed + "-action-" + index + ".json"), json.encodeToString(no.state))
                    println("MANUAL_R2_SHOCK_DECISION=" + details)
                }
                // Exact archived replay; only rebind an orphaned activation ID if the original
                // test-only R15 method identifies a unique current affordable same-source ability.
                val submitted = if (archived is ActivateAbility) {
                    val offered = simulator.getLegalActions(state, archived.playerId)
                        .filter { it.affordable && (archived.targets.isEmpty() || it.requiresTargets) }
                        .mapNotNull { it.action as? ActivateAbility }
                        .filter { it.sourceId == archived.sourceId }
                        .map { it.abilityId }.distinct()
                    if (archived.abilityId !in offered && offered.size == 1) {
                        abilityBindings += buildJsonObject {
                            put("index", index); put("source", archived.sourceId.value)
                            put("from", archived.abilityId.value); put("to", offered.single().value)
                        }
                        archived.copy(abilityId = offered.single())
                    } else archived
                } else archived
                val applied = processor.process(state, submitted).result
                check(applied.error == null) { "Archived replay rejected seed=" + seed +
                    " action=" + index + " error=" + applied.error }
                val expected = originals.getValue(index)
                val expectedLife = expected["life"]?.jsonArray?.map { it.jsonPrimitive.int }
                check(state.turnOrder.map { applied.state.lifeTotal(it) } == expectedLife) {
                    "Archived life divergence seed=" + seed + " action=" + index
                }
                if (applied.events.map { it.toString() } ==
                    expected["events"]?.jsonArray?.map { it.jsonPrimitive.content })
                    exactEventMatches++ else mismatchedEvents += index
                state = applied.state
                replayed++
            }
            replayReceipts += buildJsonObject {
                put("seed", seed); put("originalSourceCommit", "84e6347bbcdfa2990eb304051771007daf849ce9")
                put("replayedOriginalActions", replayed)
                put("actualEngineEventMatches", exactEventMatches)
                put("mismatchedEventIndices", JsonArray(mismatchedEvents.map(::JsonPrimitive)))
                put("uniqueAbilityIdRebindings", abilityBindings.size)
                put("rebindings", JsonArray(abilityBindings))
                put("allSixChoicesReconstructed", true)
            }
        }
        check(investigations.size == 6)
        val summary = buildJsonObject {
            put("status", "R2_SIX_SHOCK_EXACT_REPLAY_COMPLETE")
            put("originalRun", 39477314282L)
            put("originalR1Source", "84e6347bbcdfa2990eb304051771007daf849ce9")
            put("observedShocks", investigations.size)
            put("decisions", JsonArray(investigations))
            put("replayReceipts", JsonArray(replayReceipts))
            put("fullGamesExecuted", 0)
            put("policyChanges", 0)
            put("promotions", 0)
            put("meaning", "Legal YES and NO branches and same-turn castability, not proof of eventual win.")
        }
        Files.writeString(out.resolve("r2-six-shock-audit.json"), summary.toString())
        println("MANUAL_R2_SIX_SHOCK_RESULT=" + summary)
    }
})
