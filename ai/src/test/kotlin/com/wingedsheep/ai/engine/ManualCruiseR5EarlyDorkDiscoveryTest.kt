package com.wingedsheep.ai.engine

import com.wingedsheep.ai.insight.AiDecisionInsight
import com.wingedsheep.ai.insight.AiInsightSink
import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.GameSimulator
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.SimulationResult
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.engine.state.components.identity.CommanderRegistryComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * CRUISE R2 discovery — NOT policy qualification. Real catalog, real source
 * affordability, real R5 AI decision insight, one bounded counterfactual cast.
 * All three states were predeclared in CRUISE_R2_DISCOVERY_MANIFEST.json
 * BEFORE this class was created. No draws, no GameInitializer, no full games.
 */
class ManualCruiseR5EarlyDorkDiscoveryTest : ScenarioTestBase() {
    private data class Fixture(
        val id: String,
        val lands: List<String>,
        val commanderTaxCasts: Int,
        val animarAffordable: Boolean,
    )

    init {
        val fixtures = listOf(
            Fixture("CR-DISC-01", listOf("Forest", "Forest", "Island"), 0, false),
            Fixture("CR-DISC-02", listOf("Forest", "Forest", "Island", "Mountain"), 1, false),
            Fixture("CR-DISC-03", listOf("Forest", "Forest", "Island", "Mountain"), 0, true),
        )

        for (fixture in fixtures) {
            test("${fixture.id}: R5 raw scored decision versus real payable mana dork") {
                val builder = scenario().withPlayers("Manual-CRUISE", "Opponent")
                    .withCardInCommandZone(1, "Animar, Soul of Elements")
                    .withCardInHand(1, "Llanowar Elves")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                for (land in fixture.lands) builder.withCardOnBattlefield(1, land)
                val game = builder.build()
                val cmd = game.state.getZone(ZoneKey(game.player1Id, Zone.COMMAND)).single()
                game.state = game.state.updateEntity(cmd) {
                    it.with(CommanderComponent(
                        ownerId = game.player1Id,
                        castsFromCommandZone = fixture.commanderTaxCasts
                    ))
                }.updateEntity(game.player1Id) {
                    it.with(CommanderRegistryComponent(listOf(cmd)))
                }

                val state = game.state
                val playerId = game.player1Id
                val simulator = GameSimulator(cardRegistry)
                val legal = simulator.getLegalActions(state, playerId)
                val handId = state.getZone(ZoneKey(playerId, Zone.HAND)).single()
                state.getEntity(handId)?.get<CardComponent>()?.name shouldBe "Llanowar Elves"
                val dorkLegal = legal.filter { option ->
                    option.affordable && (option.action as? CastSpell)?.cardId == handId
                }
                // This is the substantive affordance the original eight R1
                // fixtures did not assert. Never call it a strategic defect just
                // because the AI later chooses pass instead.
                dorkLegal.size shouldBe 1
                val commanderAffordable = legal.any {
                    it.affordable && (it.action as? CastSpell)?.cardId == cmd
                }
                commanderAffordable shouldBe fixture.animarAffordable

                // Real executor and quiet-state evaluator, NOT a fabricated
                // mana quote. Quiet simulation auto-passes opponent priority:
                // it is evidence of a legal branch, NOT a game win.
                val attempted = ActionProcessor(cardRegistry)
                    .process(state, dorkLegal.single().action).result
                attempted.error shouldBe null
                val simulated = simulator.simulate(state, dorkLegal.single().action)
                check(simulated is SimulationResult.Terminal &&
                    handId in simulated.state.getZone(ZoneKey(playerId, Zone.BATTLEFIELD))
                ) { "CRUISE fixture dork did not reach an actual battlefield quiet state: $simulated" }

                val insights = mutableListOf<AiDecisionInsight>()
                val sink = AiInsightSink { _, record -> insights.add(record) }
                val profile = ManualTransmissionR5Candidate.profile
                profile.id shouldBe "manual-transmission-r5-survival-opt-in"
                val chosen = AIPlayer.create(
                    cardRegistry, playerId, profile, insightSink = sink
                ).chooseAction(state)
                val chosenName = (chosen as? CastSpell)?.cardId?.let {
                    state.getEntity(it)?.get<CardComponent>()?.name
                }
                val info = insights.lastOrNull()
                val dorkOptions = info?.options?.filter { it.cardName == "Llanowar Elves" }.orEmpty()
                val cmdOptions = info?.options?.filter { it.cardName == "Animar, Soul of Elements" }.orEmpty()
                val eval = AIPlayer.defaultEvaluator()
                val before = eval.evaluate(state, state.projectedState, playerId)
                val after = eval.evaluate(
                    simulated.state, simulated.state.projectedState, playerId
                )
                println("CRUISE_R5_DORK_DISCOVERY=" + buildJsonObject {
                    put("id", fixture.id)
                    put("source", "REAL_R5_UNCHANGED")
                    put("lands", fixture.lands.joinToString(","))
                    put("commanderTaxCasts", fixture.commanderTaxCasts)
                    put("animarLegallyAffordable", commanderAffordable)
                    put("llanowarLegallyAffordable", dorkLegal.size == 1)
                    put("llanowarActionProcessorAccepted", attempted.error == null)
                    put("llanowarQuietSimulationTerminal", simulated is SimulationResult.Terminal)
                    put("llanowarRawEvaluatorDelta", after - before)
                    put("chosenType", chosen::class.simpleName ?: "unknown")
                    put("chosenCard", chosenName ?: "none")
                    put("chosePass", chosen is PassPriority)
                    put("insightRecords", insights.size)
                    put("insightBaseline", info?.baselineScore?.toString() ?: "unavailable")
                    put("insightChosenLabel", info?.chosenLabel ?: "unavailable")
                    put("llanowarOptions", dorkOptions.size)
                    put("llanowarScore", dorkOptions.firstOrNull()?.score?.toString() ?: "unavailable")
                    put("llanowarOptionNote", dorkOptions.firstOrNull()?.note ?: "none")
                    put("animarOptions", cmdOptions.size)
                    put("animarScore", cmdOptions.firstOrNull()?.score?.toString() ?: "unavailable")
                    put("futureTurnBenefitProven", false)
                    put("policyChanged", false)
                    put("fullGame", false)
                })

                // The actual R5 chosen move is submitted separately, not
                // asserted equal to an enumerator template with missing targets.
                ActionProcessor(cardRegistry).process(state, chosen).result.error shouldBe null
                AiProfile.CURRENT.advisorModules.isEmpty() shouldBe true
                game.state shouldBe state
            }
        }
    }
}
