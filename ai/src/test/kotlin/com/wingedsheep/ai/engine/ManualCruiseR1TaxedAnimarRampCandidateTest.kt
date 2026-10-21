package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.modules.ManualCruiseTaxedAnimarRampAdvisorModule
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.engine.state.components.identity.CommanderRegistryComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.matchers.shouldBe

/**
 * Fixed CRUISE candidate witnesses, predeclared in
 * CRUISE_R1_RAMP_CANDIDATE_PREDECLARED.json BEFORE candidate implementation.
 *
 * All boards are synthetic real-card scenarios with actual legal action
 * enumeration, real AI chooser and ActionProcessor / quiet-state evaluation.
 * No GameInitializer, random seeds, action-loop or four-player full games.
 */
class ManualCruiseR1TaxedAnimarRampCandidateTest : ScenarioTestBase() {
    private fun board(
        lands: List<String>,
        hand: List<String>,
        castsFromCommandZone: Int,
        opponentLife: Int = 20,
        readyElf: Boolean = false,
    ): TestGame {
        val builder = scenario()
            .withPlayers("CRUISE-Ramp", "Opponent")
            .withCardInCommandZone(1, "Animar, Soul of Elements")
            .withLifeTotal(2, opponentLife)
            .withActivePlayer(1)
            .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        for (land in lands) builder.withCardOnBattlefield(1, land)
        if (readyElf) {
            builder.withCardOnBattlefield(1, "Llanowar Elves", summoningSickness = false)
        }
        for (card in hand) builder.withCardInHand(1, card)
        val game = builder.build()
        val cmd = game.state.getZone(ZoneKey(game.player1Id, Zone.COMMAND)).single()
        game.state = game.state.updateEntity(cmd) {
            it.with(CommanderComponent(
                ownerId = game.player1Id,
                castsFromCommandZone = castsFromCommandZone,
            ))
        }.updateEntity(game.player1Id) {
            it.with(CommanderRegistryComponent(listOf(cmd)))
        }
        return game
    }

    private fun legalCommander(game: TestGame): Boolean {
        val id = game.state.getZone(ZoneKey(game.player1Id, Zone.COMMAND)).single()
        return GameSimulator(cardRegistry).getLegalActions(game.state, game.player1Id)
            .any { it.affordable && (it.action as? CastSpell)?.cardId == id }
    }

    private fun choose(game: TestGame, candidate: Boolean): GameAction =
        AIPlayer.create(
            cardRegistry,
            game.player1Id,
            if (candidate) ManualTransmissionCruiseR1Candidate.profile
            else ManualTransmissionR5Candidate.profile,
        ).chooseAction(game.state)

    private fun sourceName(game: TestGame, action: GameAction): String? {
        val id = when (action) {
            is CastSpell -> action.cardId
            is ActivateAbility -> action.sourceId
            else -> null
        }
        return id?.let { game.state.getEntity(it)?.get<CardComponent>()?.name }
    }

    private fun assertCandidateComposition() {
        val r5 = ManualTransmissionR5Candidate.profile
        val candidate = ManualTransmissionCruiseR1Candidate.profile
        candidate.id shouldBe "manual-cruise-r1-taxed-animar-ramp-opt-in"
        candidate.copy(id = r5.id, advisorModules = r5.advisorModules) shouldBe r5
        candidate.advisorModules.size shouldBe r5.advisorModules.size + 1
        candidate.advisorModules.take(r5.advisorModules.size) shouldBe r5.advisorModules
        candidate.advisorModules.last() shouldBe ManualCruiseTaxedAnimarRampAdvisorModule
        AiProfile.CURRENT.advisorModules.isEmpty() shouldBe true
    }

    init {
        test("C-RAMP-01: real observed R5 pass becomes executable Elf investment only on proven taxed commander access") {
            assertCandidateComposition()
            val game = board(
                listOf("Forest", "Forest", "Island", "Mountain"),
                listOf("Llanowar Elves"),
                castsFromCommandZone = 1,
            )
            legalCommander(game) shouldBe false
            val comparator = choose(game, candidate = false)
            comparator shouldBe PassPriority(game.player1Id)
            val candidate = choose(game, candidate = true)
            sourceName(game, candidate) shouldBe "Llanowar Elves"
            check(candidate is CastSpell) { "Expected actual Elf cast, saw: $candidate" }
            val result = actionProcessor.process(game.state, candidate).result
            result.error shouldBe null
            println("CRUISE_RAMP_POSITIVE id=C-RAMP-01 R5=$comparator " +
                "candidate=$candidate actualProcessorAccepted=true " +
                "projectionCaveat=NO_OPPONENT_RESPONSE_OR_FUTURE_DRAW")
        }

        test("C-RAMP-02: missing red is not repaired by green Elf") {
            val game = board(
                listOf("Forest", "Forest", "Island"),
                listOf("Llanowar Elves"),
                castsFromCommandZone = 0,
            )
            val baseline = choose(game, candidate = false)
            val candidate = choose(game, candidate = true)
            legalCommander(game) shouldBe false
            candidate shouldBe baseline
            println("CRUISE_RAMP_NEGATIVE id=C-RAMP-02 unchanged=$candidate")
        }

        test("C-RAMP-03: already-affordable Animar retains immediate R5 deployment") {
            val game = board(
                listOf("Forest", "Forest", "Island", "Mountain"),
                listOf("Llanowar Elves"),
                castsFromCommandZone = 0,
            )
            legalCommander(game) shouldBe true
            val baseline = choose(game, candidate = false)
            val candidate = choose(game, candidate = true)
            sourceName(game, baseline) shouldBe "Animar, Soul of Elements"
            candidate shouldBe baseline
            actionProcessor.process(game.state, candidate).result.error shouldBe null
            println("CRUISE_RAMP_NEGATIVE id=C-RAMP-03 unchanged=$candidate")
        }

        test("C-RAMP-04: actual immediate Bolt lethal remains preferred") {
            val game = board(
                listOf("Forest", "Forest", "Island", "Mountain", "Mountain"),
                listOf("Llanowar Elves", "Lightning Bolt"),
                castsFromCommandZone = 0,
                opponentLife = 3,
            )
            val baseline = choose(game, candidate = false)
            val candidate = choose(game, candidate = true)
            sourceName(game, baseline) shouldBe "Lightning Bolt"
            sourceName(game, candidate) shouldBe "Lightning Bolt"
            actionProcessor.process(game.state, candidate).result.error shouldBe null
            val simulation = GameSimulator(cardRegistry).simulate(game.state, candidate)
                as? SimulationResult.Terminal
            check(simulation != null && simulation.state.gameOver &&
                simulation.state.winnerId == game.player1Id) {
                "Selected Bolt does not terminate the bounded quiet-state simulation: $simulation"
            }
            println("CRUISE_RAMP_NEGATIVE id=C-RAMP-04 unchangedBoltLethal=true " +
                "quietStateAutopassesOpponents=true")
        }

        test("C-RAMP-05: no Elf in hand cannot manufacture a ramp action") {
            val game = board(
                listOf("Forest", "Forest", "Island", "Mountain"),
                emptyList(),
                castsFromCommandZone = 1,
            )
            legalCommander(game) shouldBe false
            val baseline = choose(game, candidate = false)
            choose(game, candidate = true) shouldBe baseline
            println("CRUISE_RAMP_NEGATIVE id=C-RAMP-05 unchanged=$baseline")
        }

        test("C-RAMP-06: the independent real mana-source quote needs a READY Elf for taxed commander") {
            val lands = listOf("Forest", "Forest", "Island", "Mountain")
            val before = board(lands, emptyList(), castsFromCommandZone = 1)
            val withElf = board(lands, emptyList(), castsFromCommandZone = 1, readyElf = true)
            legalCommander(before) shouldBe false
            legalCommander(withElf) shouldBe true
            // This is a static before/after resource-access comparison, NOT an
            // actual turn progression or an opponent-survival probability.
            println("CRUISE_RAMP_MECHANISM id=C-RAMP-06 " +
                "unassistedPayable=false withReadyElfPayable=true " +
                "noDrawsNoOpponentsNoGames=true")
        }
    }
}
