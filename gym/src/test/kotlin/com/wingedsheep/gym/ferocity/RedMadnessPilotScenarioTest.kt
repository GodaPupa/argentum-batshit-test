package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.legalactions.*
import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.state.components.player.CardsDrawnThisTurnComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json

/**
 * Exactly the predeclared 24 fixed Red policy cases. This is neither matchup sampling nor
 * confirmation. Setup uses named public boards; every policy call receives the qualified adapter's
 * real actor input/menu, and its single chosen action is submitted without repair or retry.
 * Opposing priority passes are explicit disruption-fixture behavior, not a benchmark pilot.
 */
class RedMadnessPilotScenarioTest : ScenarioTestBase() {
    private val pilot = RedMadnessPilot()
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator(cardRegistry, ManaSolver(cardRegistry), CostCalculator(cardRegistry),
        PredicateEvaluator(), ConditionEvaluator(), TurnManager(cardRegistry))
    private val calls = mutableMapOf<Int, Long>()
    private val json = Json { encodeDefaults = true }

    private fun fixture(number: Int): ScenarioBuilder {
        val builder = scenario().withPlayers("Red", "Fixed adversary").withRngSeed(202609267000L + number)
            .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        repeat(12) {
            builder.withCardInLibrary(1, "Mountain")
            builder.withCardInLibrary(2, "Mountain")
        }
        return builder
    }

    private fun TestGame.drawn(count: Int) {
        state = state.updateEntity(player1Id) { it.with(CardsDrawnThisTurnComponent(count)) }
    }
    private fun TestGame.mulligan(taken: Int = 0, kept: Boolean = false) {
        state = state.updateEntity(player1Id) { it.with(MulliganStateComponent(taken, kept)) }
        state = state.updateEntity(player2Id) { it.with(MulliganStateComponent(hasKept = true, leylinePhaseStarted = true)) }
        state = state.copy(phase = Phase.BEGINNING, step = Step.UNTAP)
    }

    private fun TestGame.proposed(number: Int): GameAction {
        val actor = state.pendingDecision?.playerId ?: requireNotNull(state.priorityPlayerId)
        actor shouldBe player1Id
        val step = calls.getOrDefault(number, 0L)
        calls[number] = step + 1
        val input = adapter.build(state, actor, fullMenu(state, actor, enumerator),
            ActorEpoch(RedMadnessPilot.VERSION, "fixed-red-policy-case-${number.toString().padStart(2, '0')}", step), 0xFE202609L)
        val proposal = pilot.choose(input)
        proposal.inputBindingHash shouldBe input.bindingHash
        proposal.nextPolicyRngState shouldBe input.policyRngState
        println("RED_POLICY_INPUT ${input.canonicalJson()}")
        println("RED_POLICY_PROPOSAL ${json.encodeToString(ActorProposal.serializer(), proposal)}")
        return proposal.action
    }

    private fun TestGame.submitPolicy(number: Int): GameAction = proposed(number).also {
        val result = execute(it)
        println("RED_POLICY_RESULT case=$number success=${result.isSuccess} paused=${result.isPaused} error=${result.error}")
        result.error shouldBe null
    }

    private fun TestGame.toDecision() {
        var n = 0
        while (state.pendingDecision == null && state.stack.isNotEmpty() && !state.gameOver) {
            require(n++ < 20) { "Fixed fixture did not reach its decision" }
            passPriority().error shouldBe null
        }
    }

    private fun TestGame.drain(number: Int) {
        var n = 0
        while ((state.stack.isNotEmpty() || state.pendingDecision != null) && !state.gameOver) {
            require(n++ < 60) { "Fixed fixture stack/decision cap reached" }
            val q = state.pendingDecision
            when {
                q == null -> passPriority().error shouldBe null
                q.playerId == player1Id -> submitPolicy(number)
                q is CombatResolutionDecision -> execute(SubmitDecision(q.playerId,
                    CombatResolutionResponse(q.id, q.edges.filter { it.editableBy == q.playerId }
                        .map { DamageEdgeAmount(it.id, it.amount) }))).error shouldBe null
                else -> error("Unspecified adversary decision in fixed case: $q")
            }
        }
    }

    private fun TestGame.castName(action: GameAction): String = state.getEntity((action as CastSpell).cardId)!!
        .get<com.wingedsheep.engine.state.components.identity.CardComponent>()!!.name
    private fun TestGame.attackReady(vararg names: String) {
        passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
        declareAttackers(names.associateWith { 2 }).error shouldBe null
        passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
        declareNoBlockers().error shouldBe null
    }

    init {
        // TestCards intentionally overrides some familiar names. The policy bank instead uses
        // the exact canonical corpus, including its real cost, targets and effect definitions.
        val selectedNames = RedMadnessPilot.PUBLIC_POOL + setOf("Counterspell")
        val required = RedMadnessPilot.MAIN_NAMES + setOf("Lightning Bolt", "Counterspell", "Krark-Clan Shaman",
            "Myr Enforcer", "Baleful Strix", "Great Furnace", "Drossforge Bridge", "Island")
        registerFirstCellCanonicalCards(cardRegistry, selectedNames, required)
        test("01 mulligan a zero-land seven") {
            val g = fixture(1).withCardsInHand(1, "Lightning Bolt", 4).withCardsInHand(1, "Fireblast", 3).build()
            g.mulligan()
            g.submitPolicy(1).shouldBeInstanceOf<TakeMulligan>()
            g.state.getEntity(g.player1Id)!!.get<MulliganStateComponent>()!!.mulligansTaken shouldBe 1
        }
        test("02 keep two Mountains with pressure and follow-up spells") {
            val g = fixture(2).withCardsInHand(1, "Mountain", 2).withCardInHand(1, "Kessig Flamebreather")
                .withCardInHand(1, "Guttersnipe").withCardInHand(1, "Lightning Bolt")
                .withCardInHand(1, "Grab the Prize").withCardInHand(1, "Fiery Temper").build()
            g.mulligan()
            g.submitPolicy(2).shouldBeInstanceOf<KeepHand>()
        }
        test("03 mulligan six lands without a productive draw plan") {
            val g = fixture(3).withCardsInHand(1, "Mountain", 6).withCardInHand(1, "Fireblast").build()
            g.mulligan()
            g.submitPolicy(3).shouldBeInstanceOf<TakeMulligan>()
        }
        test("04 London bottom preserves the only pressure spell and required lands") {
            val g = fixture(4).withCardsInHand(1, "Mountain", 4).withCardInHand(1, "Kessig Flamebreather")
                .withCardsInHand(1, "Fireblast", 2).build()
            g.mulligan(taken = 2, kept = true)
            val action = g.proposed(4).shouldBeInstanceOf<BottomCards>()
            action.cardIds.size shouldBe 2
            action.cardIds.all { id -> g.state.getEntity(id)!!
                .get<com.wingedsheep.engine.state.components.identity.CardComponent>()!!.name == "Mountain" } shouldBe true
            g.execute(action).error shouldBe null
            g.state.getHand(g.player1Id).size shouldBe 5
        }
        test("05 develop Flamebreather before a nonurgent spell") {
            val g = fixture(5).withLandsOnBattlefield(1, "Mountain", 3).withCardInHand(1, "Kessig Flamebreather")
                .withCardInHand(1, "Lightning Bolt").build()
            val action = g.proposed(5)
            g.castName(action) shouldBe "Kessig Flamebreather"
            g.execute(action).error shouldBe null
            g.drain(5)
            g.findPermanent("Kessig Flamebreather") shouldBe (action as CastSpell).cardId
        }
        test("06 Guttersnipe consumes its actual three mana") {
            val g = fixture(6).withLandsOnBattlefield(1, "Mountain", 3).withCardInHand(1, "Guttersnipe")
                .withCardInHand(1, "Lightning Bolt").build()
            val action = g.proposed(6)
            g.castName(action) shouldBe "Guttersnipe"
            g.execute(action).error shouldBe null
            val observed = adapter.build(g.state, g.player1Id, fullMenu(g.state, g.player1Id, enumerator), ActorEpoch("fixed", "case06-after", 0), 1)
            ActorPublicCards(observed).ownBoard.filter { it.name == "Mountain" }.all { it.tapped } shouldBe true
        }
        test("07 take actual lethal burn at the opponent") {
            val g = fixture(7).withLandsOnBattlefield(1, "Mountain", 1).withCardInHand(1, "Lightning Bolt")
                .withLifeTotal(2, 3).build()
            val action = g.proposed(7).shouldBeInstanceOf<CastSpell>()
            action.targets shouldBe listOf(ChosenTarget.Player(g.player2Id))
            g.execute(action).error shouldBe null
            g.drain(7)
            g.state.gameOver shouldBe true
            g.state.winnerId shouldBe g.player1Id
        }
        test("08 remove a Shaman before its destructive activation when removal matters") {
            val g = fixture(8).withLandsOnBattlefield(1, "Mountain", 1).withCardInHand(1, "Lightning Bolt")
                .withCardOnBattlefield(1, "Kessig Flamebreather").withCardOnBattlefield(1, "Guttersnipe")
                .withCardOnBattlefield(2, "Krark-Clan Shaman").withLandsOnBattlefield(2, "Great Furnace", 3).build()
            val shaman = g.findPermanent("Krark-Clan Shaman")!!
            val action = g.proposed(8).shouldBeInstanceOf<CastSpell>()
            action.targets shouldBe listOf(ChosenTarget.Permanent(shaman))
            g.execute(action).error shouldBe null
            g.drain(8)
            (shaman in g.state.getBattlefield()) shouldBe false
        }
        test("09 do not spend one damage on an unsupported four-toughness removal line") {
            val g = fixture(9).withLandsOnBattlefield(1, "Mountain", 1).withCardInHand(1, "Lava Dart")
                .withCardOnBattlefield(2, "Myr Enforcer").build()
            g.submitPolicy(9).shouldBeInstanceOf<PassPriority>()
        }
        test("10 Robbery discards Temper while retaining actual madness mana") {
            val g = fixture(10).withLandsOnBattlefield(1, "Mountain", 3).withCardInHand(1, "Highway Robbery")
                .withCardInHand(1, "Fiery Temper").withCardInHand(1, "Mountain").build()
            // This fixed midturn board already used its land drop to develop one of the three
            // Mountains. Keep the spare Mountain as a real discard alternative, not a new play.
            g.state = g.state.updateEntity(g.player1Id) {
                it.with(com.wingedsheep.engine.state.components.player.LandDropsComponent(remaining = 0, maxPerTurn = 1))
            }
            g.state.getEntity(g.player1Id)!!.get<com.wingedsheep.engine.state.components.player.LandDropsComponent>()!!.remaining shouldBe 0
            fullMenu(g.state, g.player1Id, enumerator).none { it.action is PlayLand } shouldBe true
            g.findCardsInHand(1, "Mountain").size shouldBe 1
            g.state.projectedState.getBattlefieldControlledBy(g.player1Id).count { id ->
                g.state.getEntity(id)!!.get<com.wingedsheep.engine.state.components.identity.CardComponent>()!!.name == "Mountain" &&
                    g.state.getEntity(id)!!.get<com.wingedsheep.engine.state.components.battlefield.TappedComponent>() == null
            } shouldBe 3
            val temper = g.findCardsInHand(1, "Fiery Temper").single()
            val action = g.proposed(10).shouldBeInstanceOf<CastSpell>()
            g.castName(action) shouldBe "Highway Robbery"
            action.chosenModes shouldBe emptyList()
            g.execute(action).error shouldBe null
            g.state.pendingDecision shouldBe null
            (temper in g.state.getHand(g.player1Id)) shouldBe true
            g.toDecision()
            val choice = g.state.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
            choice.context.phase shouldBe DecisionPhase.RESOLUTION
            g.submitPolicy(10) // Choose the available discard branch during resolution.
            g.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            g.submitPolicy(10) // Select the actual Temper; this is not an extra casting cost.
            (temper in g.state.getExile(g.player1Id)) shouldBe true
            val mana = g.state.projectedState.getBattlefieldControlledBy(g.player1Id).count { id ->
                g.state.getEntity(id)!!.get<com.wingedsheep.engine.state.components.identity.CardComponent>()!!.name == "Mountain" &&
                    g.state.getEntity(id)!!.get<com.wingedsheep.engine.state.components.battlefield.TappedComponent>() == null
            }
            mana shouldBe 1
        }
        test("11 accept the real madness offer and pay its actual red mana") {
            val g = fixture(11).withLandsOnBattlefield(1, "Mountain", 3).withCardInHand(1, "Grab the Prize")
                .withCardInHand(1, "Fiery Temper").build()
            g.submitPolicy(11)
            g.toDecision()
            val answer = g.proposed(11).shouldBeInstanceOf<SubmitDecision>().response.shouldBeInstanceOf<YesNoResponse>()
            answer.choice shouldBe true
            g.execute(SubmitDecision(g.player1Id, answer)).error shouldBe null
            g.drain(11)
            g.getLifeTotal(2) shouldBe 15
            g.state.getExile(g.player1Id).isEmpty() shouldBe true
        }
        test("12 decline the madness offer when no red mana remains") {
            val g = fixture(12).withLandsOnBattlefield(1, "Mountain", 2).withCardInHand(1, "Grab the Prize")
                .withCardInHand(1, "Fiery Temper").build()
            val temper = g.findCardsInHand(1, "Fiery Temper").single()
            g.submitPolicy(12)
            g.toDecision()
            val answer = g.proposed(12).shouldBeInstanceOf<SubmitDecision>().response.shouldBeInstanceOf<YesNoResponse>()
            answer.choice shouldBe false
            g.execute(SubmitDecision(g.player1Id, answer)).error shouldBe null
            g.drain(12)
            (temper in g.state.getGraveyard(g.player1Id)) shouldBe true
            g.getLifeTotal(2) shouldBe 18
        }
        test("13 discard Snacker before draws that include the actual third card") {
            val g = fixture(13).withLandsOnBattlefield(1, "Mountain", 2).withCardInHand(1, "Grab the Prize")
                .withCardInHand(1, "Sneaky Snacker").build()
            g.drawn(1)
            val snacker = g.findCardsInHand(1, "Sneaky Snacker").single()
            val action = g.proposed(13).shouldBeInstanceOf<CastSpell>()
            action.additionalCostPayment!!.discardedCards shouldBe listOf(snacker)
            g.execute(action).error shouldBe null
            g.drain(13)
            (snacker in g.state.getBattlefield()) shouldBe true
            g.state.getEntity(g.player1Id)!!.get<CardsDrawnThisTurnComponent>()!!.count shouldBe 3
        }
        test("14 two cards drawn do not return a discarded Snacker") {
            val g = fixture(14).withLandsOnBattlefield(1, "Mountain", 2).withCardInHand(1, "Grab the Prize")
                .withCardInHand(1, "Sneaky Snacker").build()
            g.drawn(0)
            val snacker = g.findCardsInHand(1, "Sneaky Snacker").single()
            g.submitPolicy(14)
            g.drain(14)
            (snacker in g.state.getGraveyard(g.player1Id)) shouldBe true
            (snacker in g.state.getBattlefield()) shouldBe false
            g.state.getEntity(g.player1Id)!!.get<CardsDrawnThisTurnComponent>()!!.count shouldBe 2
        }
        test("15 a returned tapped Snacker does not become an immediate attacker") {
            val g = fixture(15).withLandsOnBattlefield(1, "Mountain", 1).withCardInHand(1, "Faithless Looting")
                .withCardInGraveyard(1, "Sneaky Snacker")
                .withCardOnBattlefield(1, "Kessig Flamebreather", summoningSickness = false).build()
            val returnedTurn = g.state.turnNumber
            g.drawn(1)
            g.submitPolicy(15)
            g.drain(15)
            val snacker = g.findPermanent("Sneaky Snacker")!!
            g.state.getEntity(snacker)!!.has<com.wingedsheep.engine.state.components.battlefield.TappedComponent>() shouldBe true
            g.state.getEntity(snacker)!!.has<com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent>() shouldBe true
            g.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            g.state.turnNumber shouldBe returnedTurn
            g.state.activePlayerId shouldBe g.player1Id
            val action = g.proposed(15).shouldBeInstanceOf<DeclareAttackers>()
            (snacker in action.attackers) shouldBe false
            g.execute(action).error shouldBe null
        }
        test("16 use Plot only for the explicit future Robbery plan") {
            val g = fixture(16).withLandsOnBattlefield(1, "Mountain", 2).withCardInHand(1, "Highway Robbery")
                .withCardInHand(1, "Guttersnipe").build()
            val robbery = g.findCardsInHand(1, "Highway Robbery").single()
            val action = g.proposed(16).shouldBeInstanceOf<PlotCard>()
            action.cardId shouldBe robbery
            g.execute(action).error shouldBe null
            (robbery in g.state.getExile(g.player1Id)) shouldBe true
            g.state.stack shouldBe emptyList()
            g.state.getHand(g.player1Id).size shouldBe 1
        }
        test("17 Brew pays one discard and declares its own player target") {
            val g = fixture(17).withLandsOnBattlefield(1, "Mountain", 2).withCardInHand(1, "Sazacap's Brew")
                .withCardInHand(1, "Sneaky Snacker").build()
            val snack = g.findCardsInHand(1, "Sneaky Snacker").single()
            val action = g.proposed(17).shouldBeInstanceOf<CastSpell>()
            action.targets shouldBe listOf(ChosenTarget.Player(g.player1Id))
            action.giftRecipient shouldBe null
            action.additionalCostPayment!!.discardedCards shouldBe listOf(snack)
            val result = g.execute(action)
            result.error shouldBe null
            result.events.filterIsInstance<CardsDiscardedEvent>().sumOf { it.cardIds.size } shouldBe 1
            g.drain(17)
            g.state.getHand(g.player1Id).size shouldBe 2
        }
        test("18 promising Brew's real gift creates the opposing tapped Fish before its pump") {
            val g = fixture(18).withLandsOnBattlefield(1, "Mountain", 2).withCardInHand(1, "Sazacap's Brew")
                .withCardInHand(1, "Sneaky Snacker").withCardOnBattlefield(1, "Sneaky Snacker", summoningSickness = false)
                .withLifeTotal(2, 4).build()
            val attacker = g.findPermanent("Sneaky Snacker")!!
            g.attackReady("Sneaky Snacker")
            val action = g.proposed(18).shouldBeInstanceOf<CastSpell>()
            action.giftRecipient shouldBe g.player2Id
            action.targets shouldBe listOf(ChosenTarget.Player(g.player1Id), ChosenTarget.Permanent(attacker))
            g.execute(action).error shouldBe null
            g.drain(18)
            val input = adapter.build(g.state, g.player1Id, fullMenu(g.state, g.player1Id, enumerator), ActorEpoch("fixed", "case18-after", 0), 1)
            val cards = ActorPublicCards(input)
            val fish = cards.opponentBoard.single { it.hasSubtype("Fish") }
            fish.name shouldBe "Fish Token"
            fish.cardDefinitionId shouldBe "token:Fish"
            fish.isType("CREATURE") shouldBe true
            g.state.getEntity(fish.entityId)!!.has<com.wingedsheep.engine.state.components.identity.TokenComponent>() shouldBe true
            fish.ownerId shouldBe g.player2Id
            fish.controllerId shouldBe g.player2Id
            fish.colors shouldBe setOf("BLUE")
            fish.tapped shouldBe true
            fish.power shouldBe 1
            fish.toughness shouldBe 1
            cards.card(attacker).power shouldBe 4

            // Continue the same real state so the Gift's actual public identity must pass both
            // pilots' input boundaries. No extra fixture initialization or invented actor view.
            g.submitPolicy(18).shouldBeInstanceOf<PassPriority>()
            g.state.priorityPlayerId shouldBe g.player2Id
            val opponentInput = adapter.build(g.state, g.player2Id, fullMenu(g.state, g.player2Id, enumerator),
                ActorEpoch("artifact-policy-v0.1-author-bank", "fixed-Red18-gift-continuation", 0), 26101L)
            val opponentProposal = ArtifactControlPilot().choose(opponentInput)
            opponentProposal.inputBindingHash shouldBe opponentInput.bindingHash
            opponentProposal.nextPolicyRngState shouldBe opponentInput.policyRngState
            opponentProposal.action.shouldBeInstanceOf<PassPriority>()
            println("RED18_ARTIFACT_INPUT ${opponentInput.canonicalJson()}")
            println("RED18_ARTIFACT_PROPOSAL ${json.encodeToString(ActorProposal.serializer(), opponentProposal)}")
            g.execute(opponentProposal.action).error shouldBe null
        }
        test("19 commit Fireblast's two Mountains for actual available lethal") {
            val g = fixture(19).withLandsOnBattlefield(1, "Mountain", 2).withCardInHand(1, "Fireblast")
                .withLifeTotal(2, 4).build()
            val action = g.proposed(19).shouldBeInstanceOf<CastSpell>()
            action.additionalCostPayment!!.sacrificedPermanents.distinct().size shouldBe 2
            action.targets shouldBe listOf(ChosenTarget.Player(g.player2Id))
            g.execute(action).error shouldBe null
            g.state.getBattlefield().isEmpty() shouldBe true
            g.drain(19)
            g.state.winnerId shouldBe g.player1Id
        }
        test("20 one Mountain and artifact bridges cannot fund Fireblast's alternative cost") {
            val g = fixture(20).withLandsOnBattlefield(1, "Mountain", 1).withLandsOnBattlefield(1, "Drossforge Bridge", 2)
                .withCardInHand(1, "Fireblast").withLifeTotal(2, 4).build()
            g.submitPolicy(20).shouldBeInstanceOf<PassPriority>()
            g.state.getBattlefield().size shouldBe 3
        }
        test("21 Dart flashback sacrifices a real Mountain for a useful removal line") {
            val g = fixture(21).withLandsOnBattlefield(1, "Mountain", 1).withCardInGraveyard(1, "Lava Dart")
                .withCardOnBattlefield(1, "Kessig Flamebreather").withCardOnBattlefield(1, "Guttersnipe")
                .withCardOnBattlefield(2, "Krark-Clan Shaman").withLandsOnBattlefield(2, "Great Furnace", 1).build()
            val shaman = g.findPermanent("Krark-Clan Shaman")!!
            val action = g.proposed(21).shouldBeInstanceOf<CastSpell>()
            action.additionalCostPayment!!.sacrificedPermanents.size shouldBe 1
            action.alternativeCostType shouldBe AlternativeCostType.FLASHBACK
            action.targets shouldBe listOf(ChosenTarget.Permanent(shaman))
            g.execute(action).error shouldBe null
            g.drain(21)
            (shaman in g.state.getBattlefield()) shouldBe false
            (action.cardId in g.state.getExile(g.player1Id)) shouldBe true
        }
        test("22 a countered flashback spell leaves its Mountain paid and grants no damage") {
            val g = fixture(22).withLandsOnBattlefield(1, "Mountain", 1).withCardInGraveyard(1, "Lava Dart")
                .withCardInHand(2, "Counterspell").withLandsOnBattlefield(2, "Island", 2).withLifeTotal(2, 1).build()
            val action = g.proposed(22).shouldBeInstanceOf<CastSpell>()
            val mountain = action.additionalCostPayment!!.sacrificedPermanents.single()
            g.execute(action).error shouldBe null
            g.passPriority().error shouldBe null
            val counter = g.findCardsInHand(2, "Counterspell").single()
            g.execute(CastSpell(g.player2Id, counter, targets = listOf(ChosenTarget.Spell(action.cardId)))).error shouldBe null
            g.drain(22)
            (mountain in g.state.getGraveyard(g.player1Id)) shouldBe true
            (action.cardId in g.state.getExile(g.player1Id)) shouldBe true
            g.getLifeTotal(2) shouldBe 1
            g.state.gameOver shouldBe false
        }
        test("23 attack with the expendable flier while preserving the engine from Strix deathtouch") {
            val g = fixture(23).withCardOnBattlefield(1, "Sneaky Snacker", summoningSickness = false)
                .withCardOnBattlefield(1, "Guttersnipe", summoningSickness = false)
                .withCardOnBattlefield(2, "Baleful Strix").build()
            val snacker = g.findPermanent("Sneaky Snacker")!!
            val gutter = g.findPermanent("Guttersnipe")!!
            g.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            val action = g.proposed(23).shouldBeInstanceOf<DeclareAttackers>()
            (snacker in action.attackers) shouldBe true
            (gutter in action.attackers) shouldBe false
            g.execute(action).error shouldBe null
        }
        test("24 block the real lethal attack when racing cannot survive") {
            val g = fixture(24).withCardOnBattlefield(1, "Kessig Flamebreather")
                .withCardOnBattlefield(2, "Myr Enforcer", summoningSickness = false)
                .withLifeTotal(1, 4).withActivePlayer(2).withPriorityPlayer(2).build()
            val blocker = g.findPermanent("Kessig Flamebreather")!!
            val attacker = g.findPermanent("Myr Enforcer")!!
            g.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            g.declareAttackers(mapOf("Myr Enforcer" to 1)).error shouldBe null
            g.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
            val action = g.proposed(24).shouldBeInstanceOf<DeclareBlockers>()
            action.blockers shouldBe mapOf(blocker to listOf(attacker))
            g.execute(action).error shouldBe null
            g.passUntilPhase(Phase.COMBAT, Step.COMBAT_DAMAGE)
            g.drain(24)
            g.getLifeTotal(1) shouldBe 4
        }
    }
}
