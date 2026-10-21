package com.wingedsheep.ai.engine

import com.wingedsheep.ai.llm.CardSummary
import com.wingedsheep.ai.llm.MulliganInfo
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
 * CRUISE R1: FIXED synthetic source-bound baseline decisions, not gameplay and
 * not candidate policy qualification. IDs and fixtures are predeclared in
 * CRUISE_R1_FIXED_FIXTURES.json, before any scores or policy edits.
 *
 * Historical Precision game harnesses set skipMulligans=true and use
 * AIPlayer.create(..., ManualTransmissionR5Candidate.profile), NOT
 * EngineAiPlayerController.decideMulligan. The separate opening-hand cases
 * below must never be reported as actions taken by the matched R5 harness.
 */
class ManualCruiseR5DecisionBaselinePreflightTest : ScenarioTestBase() {

    private data class OpeningFixture(
        val id: String,
        val hand: List<String>,
        val mulliganCount: Int = 0
    )

    private data class ActionFixture(
        val id: String,
        val battlefieldLands: List<String>,
        val hand: List<String>,
        val commanderTaxCasts: Int = 0,
        val opponentLife: Int = 20,
        val commanderAffordable: Boolean,
    )

    private fun openingInfo(game: TestGame, mulliganCount: Int): MulliganInfo {
        val ids = game.state.getHand(game.player1Id)
        val cards = ids.associateWith { id ->
            val card = requireNotNull(game.state.getEntity(id)?.get<CardComponent>())
            CardSummary(
                name = card.name,
                manaCost = card.manaCost.toString(),
                typeLine = card.typeLine.toString(),
                oracleText = card.oracleText,
            )
        }
        return MulliganInfo(
            hand = ids,
            mulliganCount = mulliganCount,
            cardsToPutOnBottom = mulliganCount,
            cards = cards,
            isOnThePlay = true,
        )
    }

    private fun actionFixtureGame(fixture: ActionFixture): TestGame {
        val builder = scenario()
            .withPlayers("Manual-CRUISE", "Opponent")
            .withCardInCommandZone(1, "Animar, Soul of Elements")
            .withLifeTotal(2, fixture.opponentLife)
            .withActivePlayer(1)
            .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        for (land in fixture.battlefieldLands) builder.withCardOnBattlefield(1, land)
        for (card in fixture.hand) builder.withCardInHand(1, card)
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
        return game
    }

    init {
        val openingCases = listOf(
            OpeningFixture("CR-OPEN-01", listOf(
                "Forest", "Island", "Mountain", "Llanowar Elves",
                "Ancestral Statue", "Eternal Witness", "Imperial Recruiter"
            )),
            OpeningFixture("CR-OPEN-02", listOf(
                "Forest", "Forest", "Forest", "Mountain",
                "Ancestral Statue", "Imperial Recruiter", "Eternal Witness"
            )),
            OpeningFixture("CR-OPEN-03", listOf(
                "Forest", "Island", "Mountain", "Llanowar Elves",
                "Ancestral Statue", "Eternal Witness", "Imperial Recruiter"
            ), mulliganCount = 1),
        )

        for (fixture in openingCases) {
            test("${fixture.id}: real controller keeps or mulligans fixed Temur hand") {
                fixture.hand.size shouldBe 7
                val builder = scenario().withPlayers("Manual-CRUISE", "Opponent")
                fixture.hand.forEach { builder.withCardInHand(1, it) }
                val game = builder.build()
                val before = game.state
                val decision = EngineAiPlayerController(
                    cardRegistry,
                    game.player1Id,
                    gameStateProvider = { game.state },
                ).decideMulligan(openingInfo(game, fixture.mulliganCount))
                // Diagnostic only: record the actual baseline before proposing an
                // opening-hand policy. This is NOT the R5 matched-game controller.
                println("CRUISE_OPENING_BASELINE id=${fixture.id} " +
                    "controller=EngineAiPlayerController " +
                    "mulligans=${fixture.mulliganCount} keep=${decision} " +
                    "hand=${fixture.hand}")
                game.state shouldBe before
            }
        }

        val actionCases = listOf(
            ActionFixture("CR-ACT-01",
                listOf("Forest", "Forest", "Island", "Mountain"),
                listOf("Llanowar Elves"), commanderAffordable = true),
            ActionFixture("CR-ACT-02",
                listOf("Forest", "Forest", "Island"),
                listOf("Llanowar Elves"), commanderAffordable = false),
            ActionFixture("CR-ACT-03",
                listOf("Forest", "Forest", "Island", "Mountain"),
                listOf("Llanowar Elves"), commanderTaxCasts = 1,
                commanderAffordable = false),
            ActionFixture("CR-ACT-04",
                listOf("Forest", "Forest", "Island", "Mountain"),
                emptyList(), commanderAffordable = true),
            ActionFixture("CR-ACT-05",
                listOf("Forest", "Forest", "Island", "Mountain", "Mountain"),
                listOf("Llanowar Elves", "Lightning Bolt"),
                opponentLife = 3, commanderAffordable = true),
        )

        for (fixture in actionCases) {
            test("${fixture.id}: legal R5 commander opportunity and chosen actual action") {
                val game = actionFixtureGame(fixture)
                val profile = ManualTransmissionR5Candidate.profile
                profile.id shouldBe "manual-transmission-r5-survival-opt-in"
                AiProfile.CURRENT.advisorModules.isEmpty() shouldBe true
                val player = game.player1Id
                val commanderId = game.state.getZone(ZoneKey(player, Zone.COMMAND)).single()
                val simulator = GameSimulator(cardRegistry)
                val legal = simulator.getLegalActions(game.state, player)
                val commandOffers = legal.filter {
                    (it.action as? CastSpell)?.cardId == commanderId
                }
                val affordable = commandOffers.any { it.affordable }
                affordable shouldBe fixture.commanderAffordable
                val chosen = AIPlayer.create(cardRegistry, player, profile).chooseAction(game.state)
                val chosenName = when (chosen) {
                    is CastSpell -> game.state.getEntity(chosen.cardId)?.get<CardComponent>()?.name
                    is ActivateAbility -> game.state.getEntity(chosen.sourceId)?.get<CardComponent>()?.name
                    else -> null
                }
                // The existing R5 chooser must submit a real legal action. We do
                // not preset its commander/development choice as a success result.
                if (chosen !is PassPriority) {
                    check(legal.any { it.action == chosen }) {
                        "CRUISE ${fixture.id}: chosen action absent from legal list: $chosen"
                    }
                }
                if (!affordable) {
                    check(chosen !is CastSpell || chosen.cardId != commanderId) {
                        "CRUISE ${fixture.id}: invented unaffordable Animar cast"
                    }
                }
                println("CRUISE_ACTION_BASELINE id=${fixture.id} " +
                    "profile=${profile.id} legalAnimar=${affordable} " +
                    "chosenType=${chosen::class.simpleName} chosenCard=${chosenName} " +
                    "hand=${fixture.hand} lands=${fixture.battlefieldLands.size}")
                val result = actionProcessor.process(game.state, chosen).result
                result.error shouldBe null // one real action only; NOT gameplay
            }
        }
    }
}
