package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.CastContext
import com.wingedsheep.ai.engine.advisor.modules.ManualAnimarDevelopmentAdvisor
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Diagnostic only. Re-enumerates legal choices and independently measured same-turn followups
 * from exact archived R5 states. Never changes policy or asserts that Animar should be cast.
 * No future draws, opponent secrets, opponent cooperation, or original AI memory are assumed.
 */
class ManualR6ProspectiveWitnessDiagnosticTest : FunSpec({
    test("probe real decision-time Animar followups at positive lead and negative control") {
        val archive = Path.of(requireNotNull(System.getenv("MANUAL_R6_AUDIT_ARCHIVE")))
        val out = Path.of(requireNotNull(System.getenv("MANUAL_R6_WITNESS_OUTPUT")))
        Files.createDirectories(out)
        val hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))
            .joinToString("") { "%02x".format(it) }
        hash shouldBe "985f86cc30893b6243b429b033794d790653fd5bae6a42d2c4a5a1c00a49d7eb"
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val sim = GameSimulator(registry)
        val evaluator = AIPlayer.defaultEvaluator()
        val seat = EntityId("manual-mirror-0")
        val results = mutableListOf<JsonElement>()
        fun name(state: GameState, id: EntityId): String =
            state.getEntity(id)?.get<CardComponent>()?.name ?: id.value
        fun eligible(state: GameState, hand: Set<EntityId>, action: LegalAction): Boolean {
            val cast = action.action as? CastSpell ?: return false
            return action.affordable && !action.requiresTargets && !action.hasXCost &&
                cast.cardId in hand &&
                state.getEntity(cast.cardId)?.get<CardComponent>()?.typeLine?.isCreature == true
        }
        fun finite(n: Double): JsonElement =
            if (n.isFinite()) JsonPrimitive(n) else JsonPrimitive(n.toString())

        ZipFile(archive.toFile()).use { zip ->
            val recorded = zip.getInputStream(
                requireNotNull(zip.getEntry("r6-output/opportunities.json"))
            ).bufferedReader().readText()
            val opportunities = json.parseToJsonElement(recorded).jsonArray
            opportunities.size shouldBe 13
            for ((index, turn) in listOf(1813 to 40, 2368 to 52)) {
                val state = json.decodeFromString<GameState>(zip.getInputStream(
                    requireNotNull(zip.getEntry("r6-output/state-before-" + index + ".json"))
                ).bufferedReader().readText())
                state.turnNumber shouldBe turn
                val commander = state.getZone(ZoneKey(seat, Zone.COMMAND)).single()
                val available = sim.getLegalActions(state, seat)
                val commanderOffer = available.single {
                    it.affordable && (it.action as? CastSpell)?.cardId == commander
                }
                val cast = commanderOffer.action as CastSpell
                val response = sim.simulate(state, cast) as? SimulationResult.Terminal
                    ?: error("Commander cast simulation was not terminal for action " + index)
                val after = response.state
                val beforeValue = evaluator.evaluate(state, state.projectedState, seat)
                val afterValue = evaluator.evaluate(after, after.projectedState, seat)
                val hand = state.getZone(ZoneKey(seat, Zone.HAND)).toSet()
                val without = available.filter { eligible(state, hand, it) }
                    .associateBy { (it.action as CastSpell).cardId }
                val withOffers = sim.getLegalActions(after, seat)
                    .filter { eligible(state, hand, it) }
                val initialCounters = after.getEntity(commander)?.get<CountersComponent>()
                    ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
                val observations = mutableListOf<JsonElement>()
                var maximumProspectiveCredit = 0.0
                for (offer in withOffers.take(20)) {
                    val handCreature = (offer.action as CastSpell).cardId
                    val plain = without[handCreature] ?: continue
                    val withResult = sim.simulate(after, offer.action) as? SimulationResult.Terminal
                    val plainResult = sim.simulate(state, plain.action) as? SimulationResult.Terminal
                    val finalCounters = withResult?.state?.getEntity(commander)?.get<CountersComponent>()
                        ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: initialCounters
                    val withGain = withResult?.let {
                        evaluator.evaluate(it.state, it.state.projectedState, seat) - afterValue
                    }
                    val plainGain = plainResult?.let {
                        evaluator.evaluate(it.state, it.state.projectedState, seat) - beforeValue
                    }
                    val delta = if (withGain != null && plainGain != null &&
                        withGain.isFinite() && plainGain.isFinite()) withGain - plainGain else null
                    if (delta != null && withGain != null && withGain > 0 &&
                        finalCounters > initialCounters && delta > maximumProspectiveCredit) {
                        maximumProspectiveCredit = delta
                    }
                    observations += buildJsonObject {
                        put("card", name(state, handCreature))
                        put("initiallyAffordable", plain.affordable)
                        put("affordableAfterAnimar", offer.affordable)
                        put("terminalSimulationAfterAnimar", withResult != null)
                        put("terminalSimulationWithoutAnimar", plainResult != null)
                        put("commanderCountersAdded", finalCounters - initialCounters)
                        put("withGain", withGain?.let(::finite) ?: JsonNull)
                        put("withoutGain", plainGain?.let(::finite) ?: JsonNull)
                        put("incrementalBoardGain", delta?.let(::finite) ?: JsonNull)
                    }
                }
                val priorCredit = ManualAnimarDevelopmentAdvisor.evaluateCast(
                    CastContext(state, state.projectedState, seat, commanderOffer,
                        beforeValue, afterValue, evaluator, sim)
                )
                val original = opportunities.map { it.jsonObject }.single {
                    it.getValue("index").jsonPrimitive.int == index
                }
                val summary = buildJsonObject {
                    put("archivedAction", index)
                    put("originalChoice", original.getValue("actualChoice"))
                    put("commanderLegalAndAffordable", commanderOffer.affordable)
                    put("manaSourcesBefore", original.getValue("availableManaSources").jsonArray.size)
                    put("myLifeAtDecision", state.lifeTotal(seat))
                    put("boardValueBefore", finite(beforeValue))
                    put("boardValueAfterAnimar", finite(afterValue))
                    put("afterCommanderOnBattlefield",
                        commander in after.getZone(ZoneKey(seat, Zone.BATTLEFIELD)))
                    put("afterCommanderStackSize", after.stack.size)
                    put("afterCommanderActivePlayer", after.activePlayerId.value)
                    put("afterCommanderPriorityPlayer", after.priorityPlayerId?.value)
                    put("originalAdvisorCredit", finite((priorCredit ?: afterValue) - afterValue))
                    put("eligibleHandFollowupsBefore", without.size)
                    put("eligibleHandFollowupsAfter", withOffers.size)
                    put("prospectiveSameTurnCredit", finite(maximumProspectiveCredit))
                    put("enumeratedPairedFollowups", JsonArray(observations))
                    put("note", "Diagnostic only: no chosen commander action or policy correction")
                }
                results += summary
                Files.writeString(out.resolve("witness-observations.json"), JsonArray(results).toString())
                println("R6_WITNESS_DIAGNOSTIC=" + summary)
            }
        }
        results.size shouldBe 2
        AiProfile.CURRENT.advisorModules.isEmpty() shouldBe true
    }
})
