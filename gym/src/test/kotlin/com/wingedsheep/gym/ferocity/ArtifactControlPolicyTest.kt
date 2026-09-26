package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.token.CreateTokenExecutor
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.research.ferocity.FerocityOfTheHuntPrerelease
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.dsl.giftEffect
import com.wingedsheep.sdk.scripting.GiftKind
import com.wingedsheep.sdk.scripting.effects.CreateTokenEffect
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Exactly the predeclared24-case D1 bank. These are fixed policy development scenarios, not
 * random matchup samples. Inputs use the same trusted fullMenu and ObservationAdapter as the
 * journal runner; all selected actions are submitted to the actual engine without repair.
 * No synthetic game result, auto-win, omniscient callback or hidden library data reaches a pilot.
 */
class ArtifactControlPolicyTest : ScenarioTestBase() {
    private val p1 = EntityId.of("player-1")
    private val p2 = EntityId.of("player-2")
    private val pilot = ArtifactControlPilot()
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator(cardRegistry, ManaSolver(cardRegistry), CostCalculator(cardRegistry),
        PredicateEvaluator(), ConditionEvaluator(), TurnManager(cardRegistry))
    private fun base(case: Int) = scenario().withPlayers("Artifact control", "Fixed opponent")
        .withRngSeed(202609260000L + case).withActivePlayer(1).withPriorityPlayer(1)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).also { b ->
            repeat(12) { b.withCardInLibrary(1, "Vault of Whispers").withCardInLibrary(2, "Mountain") }
        }
    private fun ScenarioBuilder.hand(vararg names: String): ScenarioBuilder = also { b ->
        names.forEach { b.withCardInHand(1, it) }
    }
    private fun ScenarioBuilder.mana(vararg names: String): ScenarioBuilder = also { b ->
        names.forEach { b.withCardOnBattlefield(1, it) }
    }
    private fun TestGame.input(case: Int): ActorInput {
        val actor = state.pendingDecision?.playerId ?: state.priorityPlayerId!!
        actor shouldBe p1
        return adapter.build(state, actor, fullMenu(state, actor, enumerator),
            ActorEpoch("artifact-policy-v0.1-author-bank", "fixed-A-$case", state.stack.size.toLong()), 26101L)
    }
    private fun TestGame.choose(case: Int): GameAction {
        val input = input(case)
        val proposal = pilot.choose(input)
        proposal.inputBindingHash shouldBe input.bindingHash
        proposal.nextPolicyRngState shouldBe input.policyRngState
        return proposal.action
    }
    private fun TestGame.act(case: Int): GameAction = choose(case).also { execute(it).error shouldBe null }
    private fun TestGame.name(id: EntityId) = state.getEntity(id)!!.require<CardComponent>().name
    private fun TestGame.castChoice(case: Int, expected: String): CastSpell {
        val action = choose(case).shouldBeInstanceOf<CastSpell>()
        name(action.cardId) shouldBe expected
        execute(action).error shouldBe null
        return action
    }
    private fun TestGame.resolveOne() {
        state.pendingDecision shouldBe null
        val before = state.stack.last()
        repeat(2) {
            if (before !in state.stack || state.pendingDecision != null) return
            passPriority().error shouldBe null
        }
        (before !in state.stack || state.pendingDecision != null) shouldBe true
    }
    private fun TestGame.settle(case: Int) {
        repeat(32) {
            if (state.gameOver || (state.stack.isEmpty() && state.pendingDecision == null)) return
            if (state.pendingDecision != null) {
                state.pendingDecision!!.playerId shouldBe p1
                act(case)
            } else resolveOne()
        }
        error("Fixed fixture stack did not settle within32 steps")
    }
    private fun TestGame.mulligan(taken: Int = 0, kept: Boolean = false) {
        state = state.updateEntity(p1) { it.with(MulliganStateComponent(taken, kept)) }
            .updateEntity(p2) { it.with(MulliganStateComponent(hasKept = kept)) }
    }
    private fun TestGame.opponentBolt(target: EntityId? = null) {
        state.priorityPlayerId shouldBe p1
        passPriority().error shouldBe null
        state.priorityPlayerId shouldBe p2
        if (target == null) castSpellTargetingPlayer(2, "Lightning Bolt", 1).error shouldBe null
        else castSpell(2, "Lightning Bolt", target).error shouldBe null
        passPriority().error shouldBe null
        state.priorityPlayerId shouldBe p1
    }
    private fun TestGame.activation(case: Int, source: String): ActivateAbility {
        val action = choose(case).shouldBeInstanceOf<ActivateAbility>()
        name(action.sourceId) shouldBe source
        execute(action).error shouldBe null
        return action
    }
    init {
        val canonicalNames = setOf("Refurbished Familiar", "Baleful Strix", "Krark-Clan Shaman", "Myr Enforcer",
            "Ichor Wellspring", "Blood Fountain", "Nihil Spellbomb", "Thoughtcast", "Reckoner's Bargain",
            "Galvanic Blast", "Toxin Analysis", "Not Dead After All", "Cast Down", "Makeshift Munitions",
            "Drossforge Bridge", "Mistvault Bridge", "Silverbluff Bridge", "Vault of Whispers",
            "Seat of the Synod", "Great Furnace", "Mountain", "Guttersnipe", "Kessig Flamebreather",
            "Lightning Bolt", "Sneaky Snacker")
        registerFirstCellCanonicalCards(cardRegistry, canonicalNames)
        cardRegistry.register(FerocityOfTheHuntPrerelease)
        test("A01 mulligan a zero-land seven through the actual London action") {
            val game = base(1).hand("Ferocity of the Hunt", "Toxin Analysis", "Thoughtcast", "Refurbished Familiar",
                "Myr Enforcer", "Ichor Wellspring", "Galvanic Blast").build()
            game.mulligan()
            game.act(1).shouldBeInstanceOf<TakeMulligan>()
            game.state.getEntity(p1)!!.require<MulliganStateComponent>().mulligansTaken shouldBe 1
            game.state.getHand(p1).size shouldBe 7
        }
        test("A02 keep two colored sources with an early play and follow-up draw") {
            val game = base(2).hand("Drossforge Bridge", "Seat of the Synod", "Ichor Wellspring", "Refurbished Familiar",
                "Thoughtcast", "Galvanic Blast", "Toxin Analysis").build()
            game.mulligan()
            game.act(2).shouldBeInstanceOf<KeepHand>()
            game.state.getEntity(p1)!!.require<MulliganStateComponent>().hasKept shouldBe true
        }
        test("A03 mulligan six lands without a productive draw or threat") {
            val game = base(3).hand("Drossforge Bridge", "Mistvault Bridge", "Silverbluff Bridge", "Vault of Whispers",
                "Seat of the Synod", "Great Furnace", "Ferocity of the Hunt").build()
            game.mulligan()
            game.act(3).shouldBeInstanceOf<TakeMulligan>()
        }
        test("A04 London bottom redundant support before colors and the only threat") {
            val game = base(4).hand("Drossforge Bridge", "Seat of the Synod", "Refurbished Familiar", "Ichor Wellspring",
                "Thoughtcast", "Ferocity of the Hunt", "Toxin Analysis").build()
            game.mulligan(taken = 1, kept = true)
            val action = game.choose(4).shouldBeInstanceOf<BottomCards>()
            action.cardIds.size shouldBe 1
            (game.name(action.cardIds.single()) in setOf("Ferocity of the Hunt", "Toxin Analysis")) shouldBe true
            game.execute(action).error shouldBe null
            game.state.getHand(p1).size shouldBe 6
            game.state.getLibrary(p1).last() shouldBe action.cardIds.single()
        }
        test("A05 develop untapped red over a bridge when current Blast removal matters") {
            val game = base(5).mana("Seat of the Synod", "Vault of Whispers")
                .withCardOnBattlefield(2, "Guttersnipe").hand("Great Furnace", "Drossforge Bridge", "Galvanic Blast").build()
            val action = game.choose(5).shouldBeInstanceOf<PlayLand>()
            game.name(action.cardId) shouldBe "Great Furnace"
            game.execute(action).error shouldBe null
            game.state.getEntity(action.cardId)!!.has<TappedComponent>() shouldBe false
            ActorChoiceSupport.targetId(game.castChoice(5, "Galvanic Blast").targets.single()) shouldBe game.findPermanent("Guttersnipe")!!
        }
        test("A06 develop the missing blue and black source when immediate removal is absent") {
            val game = base(6).mana("Great Furnace").hand("Mistvault Bridge", "Great Furnace", "Thoughtcast",
                "Refurbished Familiar").build()
            val action = game.choose(6).shouldBeInstanceOf<PlayLand>()
            game.name(action.cardId) shouldBe "Mistvault Bridge"
            game.execute(action).error shouldBe null
            game.state.getEntity(action.cardId)!!.has<TappedComponent>() shouldBe true
        }
        test("A07 Familiar affordability uses the current artifact count and actual black payment") {
            fun fixture(artifacts: Int): TestGame {
                val b = base(7).withCardOnBattlefield(1, "Vault of Whispers").hand("Refurbished Familiar")
                repeat(artifacts - 1) { b.withCardOnBattlefield(1, "Seat of the Synod", tapped = true) }
                return b.build()
            }
            val short = fixture(2)
            short.choose(7).shouldBeInstanceOf<PassPriority>()
            val ready = fixture(3)
            ready.castChoice(7, "Refurbished Familiar")
            ready.state.getEntity(ready.findPermanent("Vault of Whispers")!!)!!.has<TappedComponent>() shouldBe true
            ready.state.getEntity(p1)!!.require<ManaPoolComponent>().black shouldBe 0
        }
        test("A08 free Enforcer deployment retains actual untapped interaction mana") {
            val b = base(8).mana("Great Furnace").hand("Myr Enforcer", "Galvanic Blast")
            repeat(6) { b.withCardOnBattlefield(1, "Vault of Whispers", tapped = true) }
            val game = b.build()
            game.castChoice(8, "Myr Enforcer")
            game.state.getEntity(game.findPermanent("Great Furnace")!!)!!.has<TappedComponent>() shouldBe false
        }
        test("A09 Bargain sacrifices Wellspring before a needed artifact land and resolves real draws") {
            val game = base(9).mana("Vault of Whispers", "Seat of the Synod")
                .withCardOnBattlefield(1, "Ichor Wellspring").hand("Reckoner's Bargain").build()
            val well = game.findPermanent("Ichor Wellspring")!!
            val before = game.state.getHand(p1).size
            val action = game.castChoice(9, "Reckoner's Bargain")
            action.additionalCostPayment!!.sacrificedPermanents shouldBe listOf(well)
            game.state.getBattlefield().count { game.name(it) in setOf("Vault of Whispers", "Seat of the Synod") } shouldBe 2
            game.settle(9)
            game.state.getHand(p1).size shouldBe before - 1 + 3
            game.getLifeTotal(1) shouldBe 22
        }
        test("A10 Bargain saves lethal burn with sacrificed Enforcer actual mana value") {
            val game = base(10).mana("Vault of Whispers", "Seat of the Synod").withLifeTotal(1, 1)
                .withCardOnBattlefield(1, "Myr Enforcer").withCardOnBattlefield(2, "Mountain")
                .withCardInHand(2, "Lightning Bolt").hand("Reckoner's Bargain").build()
            game.opponentBolt()
            game.castChoice(10, "Reckoner's Bargain")
            game.settle(10)
            game.getLifeTotal(1) shouldBe 5
            game.state.gameOver shouldBe false
            game.isInGraveyard(1, "Myr Enforcer") shouldBe true
        }
        test("A11 zero-mana-value token gives no invented Bargain life or rescue") {
            val game = base(11).mana("Vault of Whispers", "Seat of the Synod").withLifeTotal(1, 1)
                .withCardOnBattlefield(1, "Blood", isToken = true).withCardOnBattlefield(2, "Mountain")
                .withCardInHand(2, "Lightning Bolt").hand("Reckoner's Bargain").build()
            game.opponentBolt()
            // Drawing may still seek an answer. The measured line earns exactly zero life.
            game.castChoice(11, "Reckoner's Bargain")
            game.resolveOne()
            game.getLifeTotal(1) shouldBe 1
            game.resolveOne()
            game.state.gameOver shouldBe true
            game.state.winnerId shouldBe p2
        }
        test("A12 metalcraft Blast removes an actual four-toughness threat") {
            val game = base(12).mana("Vault of Whispers", "Seat of the Synod", "Great Furnace")
                .withCardOnBattlefield(2, "Myr Enforcer").hand("Galvanic Blast").build()
            val target = game.findPermanent("Myr Enforcer")!!
            ActorChoiceSupport.targetId(game.castChoice(12, "Galvanic Blast").targets.single()) shouldBe target
            game.resolveOne()
            game.isInGraveyard(2, "Myr Enforcer") shouldBe true
        }
        test("A13 take legal lethal burn at the opponent") {
            val game = base(13).mana("Vault of Whispers", "Seat of the Synod", "Great Furnace")
                .withLifeTotal(2, 4).withCardOnBattlefield(2, "Guttersnipe").hand("Galvanic Blast").build()
            ActorChoiceSupport.targetId(game.castChoice(13, "Galvanic Blast").targets.single()) shouldBe p2
            game.resolveOne()
            game.state.gameOver shouldBe true
            game.state.winnerId shouldBe p1
        }
        test("A14 a Ferocity sweep retains actual flyers and charges the tapped Shaman return") {
            val game = base(14).withCardOnBattlefield(1, "Krark-Clan Shaman")
                .withCardAttachedTo(1, "Ferocity of the Hunt", "Krark-Clan Shaman")
                .withCardOnBattlefield(1, "Baleful Strix").withCardOnBattlefield(1, "Refurbished Familiar")
                .withCardOnBattlefield(1, "Blood", isToken = true).withCardOnBattlefield(2, "Guttersnipe")
                .withCardOnBattlefield(2, "Kessig Flamebreather").build()
            val shaman = game.findPermanent("Krark-Clan Shaman")!!
            val old = game.state.objectRef(shaman)
            game.activation(14, "Krark-Clan Shaman")
            game.settle(14)
            game.findPermanent("Baleful Strix")?.let { game.name(it) } shouldBe "Baleful Strix"
            game.findPermanent("Refurbished Familiar")?.let { game.name(it) } shouldBe "Refurbished Familiar"
            game.state.objectRef(shaman) shouldNotBe old
            game.state.getEntity(shaman)!!.has<TappedComponent>() shouldBe true
            game.isInGraveyard(1, "Ferocity of the Hunt") shouldBe true
            game.isInGraveyard(2, "Guttersnipe") shouldBe true
            game.isInGraveyard(2, "Kessig Flamebreather") shouldBe true
            game.state.gameOver shouldBe false
        }
        test("A15 avoid an unproductive ordinary sweep with important friendly ground creatures") {
            val game = base(15).withCardOnBattlefield(1, "Krark-Clan Shaman").withCardOnBattlefield(1, "Myr Enforcer")
                .withCardOnBattlefield(1, "Blood", isToken = true).build()
            // Fixed established board: the ordinary Gift Fish has untapped since creation.
            // Use the real inline-token executor; Fish is not a registered CardDefinition.
            val fish = (giftEffect(GiftKind.TAPPED_FISH) as CreateTokenEffect)
                .copy(controller = null, tapped = false)
            val originalBoard = game.state.getBattlefield().toSet()
            val created = CreateTokenExecutor(cardRegistry = cardRegistry).execute(
                game.state, fish, EffectContext(sourceId = null, controllerId = p2))
            created.error shouldBe null
            game.state = created.state
            val token = (game.state.getBattlefield().toSet() - originalBoard).single()
            val view = ActorPublicCards(game.input(15)).card(token)
            view.name shouldBe "Fish Token"
            view.controllerId shouldBe p2
            view.power shouldBe 1
            view.toughness shouldBe 1
            view.tapped shouldBe false
            game.choose(15).shouldBeInstanceOf<PassPriority>()
        }
        test("A16 Ferocity preparation spends exactly two mana and Shaman requires only real artifact fodder") {
            val game = base(16).mana("Vault of Whispers", "Seat of the Synod")
                .withCardOnBattlefield(1, "Krark-Clan Shaman").withCardOnBattlefield(1, "Ichor Wellspring")
                .withCardOnBattlefield(2, "Guttersnipe").withCardOnBattlefield(2, "Kessig Flamebreather")
                .hand("Ferocity of the Hunt").build()
            val shaman = game.findPermanent("Krark-Clan Shaman")!!
            ActorChoiceSupport.targetId(game.castChoice(16, "Ferocity of the Hunt").targets.single()) shouldBe shaman
            game.resolveOne()
            game.state.getEntity(p1)!!.require<ManaPoolComponent>().total shouldBe 0
            game.activation(16, "Krark-Clan Shaman")
            game.settle(16)
            game.findPermanent("Krark-Clan Shaman") shouldBe shaman
            game.isInGraveyard(1, "Ichor Wellspring") shouldBe true
        }
        test("A17 saving Toxin line uses one black mana and its real Clue without a free draw") {
            val game = base(17).mana("Vault of Whispers").withLifeTotal(1, 2)
                .withCardOnBattlefield(1, "Krark-Clan Shaman").withCardOnBattlefield(2, "Guttersnipe")
                .withCardOnBattlefield(2, "Kessig Flamebreather").hand("Toxin Analysis", "Ferocity of the Hunt").build()
            game.castChoice(17, "Toxin Analysis")
            game.resolveOne()
            val clue = game.findPermanent("Clue")!!
            val hand = game.state.getHand(p1).size
            game.activation(17, "Krark-Clan Shaman").costPayment!!.sacrificedPermanents shouldBe listOf(clue)
            game.settle(17)
            game.getLifeTotal(1) shouldBe 5
            game.state.getHand(p1).size shouldBe hand
            game.isInGraveyard(1, "Krark-Clan Shaman") shouldBe true
            game.findPermanent("Vault of Whispers")?.let { game.name(it) } shouldBe "Vault of Whispers"
        }
        test("A18 efficient return protection responds to real lethal removal") {
            val game = base(18).mana("Vault of Whispers").withCardOnBattlefield(1, "Baleful Strix")
                .withCardOnBattlefield(2, "Mountain").withCardInHand(2, "Lightning Bolt").hand("Not Dead After All").build()
            val strix = game.findPermanent("Baleful Strix")!!
            val old = game.state.objectRef(strix)
            game.opponentBolt(strix)
            ActorChoiceSupport.targetId(game.castChoice(18, "Not Dead After All").targets.single()) shouldBe strix
            game.settle(18)
            game.state.objectRef(strix) shouldNotBe old
            game.findPermanent("Baleful Strix") shouldBe strix
            game.state.getEntity(strix)!!.has<TappedComponent>() shouldBe true
            game.findPermanent("Wicked Role")?.let { game.name(it) } shouldBe "Wicked Role"
        }
        test("A19 target removal before Ferocity resolves never becomes an imaginary attached Aura") {
            val game = base(19).mana("Vault of Whispers", "Seat of the Synod")
                .withCardOnBattlefield(1, "Krark-Clan Shaman").withCardOnBattlefield(1, "Blood", isToken = true)
                .withCardOnBattlefield(2, "Mountain").withCardInHand(2, "Lightning Bolt").hand("Ferocity of the Hunt").build()
            val shaman = game.findPermanent("Krark-Clan Shaman")!!
            game.castSpell(1, "Ferocity of the Hunt", shaman).error shouldBe null
            game.opponentBolt(shaman)
            game.resolveOne()
            game.isInGraveyard(1, "Krark-Clan Shaman") shouldBe true
            game.choose(19).shouldBeInstanceOf<PassPriority>()
            game.resolveOne()
            game.findPermanent("Krark-Clan Shaman") shouldBe null
            game.isInGraveyard(1, "Ferocity of the Hunt") shouldBe true
        }
        test("A20 returned Shaman has no retained Aura bonuses or immediate attack") {
            val game = base(20).withCardOnBattlefield(1, "Krark-Clan Shaman")
                .withCardAttachedTo(1, "Ferocity of the Hunt", "Krark-Clan Shaman")
                .withCardOnBattlefield(1, "Blood", isToken = true).withCardOnBattlefield(2, "Guttersnipe").build()
            val shaman = game.findPermanent("Krark-Clan Shaman")!!
            game.activation(20, "Krark-Clan Shaman"); game.settle(20)
            game.state = game.state.copy(step = Step.DECLARE_ATTACKERS, phase = Phase.COMBAT, priorityPlayerId = p1)
            val input = game.input(20)
            val view = ActorPublicCards(input).card(shaman)
            view.tapped shouldBe true
            view.power shouldBe 1
            ("DEATHTOUCH" in view.keywords) shouldBe false
            game.state.getEntity(shaman)!!.has<SummoningSicknessComponent>() shouldBe true
            val action = game.act(20).shouldBeInstanceOf<DeclareAttackers>()
            action.attackers.containsKey(shaman) shouldBe false
        }
        test("A21 do not queue a redundant deathtouch pulse from the same source") {
            val game = base(21).withCardOnBattlefield(1, "Krark-Clan Shaman")
                .withCardAttachedTo(1, "Ferocity of the Hunt", "Krark-Clan Shaman")
                .withCardOnBattlefield(1, "Blood", isToken = true).withCardOnBattlefield(1, "Blood", isToken = true)
                .withCardOnBattlefield(2, "Guttersnipe").withCardOnBattlefield(2, "Kessig Flamebreather").build()
            game.activation(21, "Krark-Clan Shaman")
            game.input(21).observation.stack.single().source!!.characteristics!!.deathtouch shouldBe true
            game.choose(21).shouldBeInstanceOf<PassPriority>()
            game.state.getBattlefield().count { game.name(it) == "Blood" } shouldBe 1
            game.state.stack.size shouldBe 1
        }
        test("A22 Spellbomb responds to an actual pending opposing Snacker return") {
            val b = base(22).mana("Vault of Whispers").withCardOnBattlefield(1, "Nihil Spellbomb")
                .withCardInGraveyard(2, "Sneaky Snacker").withCardsDrawnThisTurn(2, 1)
                .withCardInHand(2, "Thoughtcast").withActivePlayer(2).withPriorityPlayer(2)
            repeat(5) { b.withCardOnBattlefield(2, "Seat of the Synod") }
            val game = b.build()
            game.castSpell(2, "Thoughtcast").error shouldBe null
            game.resolveOne()
            game.state.stack.size shouldBe 1
            game.passPriority().error shouldBe null
            game.state.priorityPlayerId shouldBe p1
            ActorChoiceSupport.targetId(game.activation(22, "Nihil Spellbomb").targets.single()) shouldBe p2
            game.settle(22)
            game.findPermanent("Sneaky Snacker") shouldBe null
            game.state.getExile(p2).any { game.name(it) == "Sneaky Snacker" } shouldBe true
        }
        test("A23 attack with an evasive clock while respecting a public ground counterattack") {
            val game = base(23).withLifeTotal(1, 3).withCardOnBattlefield(1, "Refurbished Familiar")
                .withCardOnBattlefield(1, "Baleful Strix").withCardOnBattlefield(1, "Myr Enforcer")
                .withCardOnBattlefield(2, "Guttersnipe").withCardOnBattlefield(2, "Kessig Flamebreather")
                .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS).build()
            val familiar = game.findPermanent("Refurbished Familiar")!!
            val action = game.act(23).shouldBeInstanceOf<DeclareAttackers>()
            action.attackers[familiar] shouldBe p2
            action.attackers.isNotEmpty() shouldBe true
        }
        test("A24 a legal flying blocker prevents an otherwise lethal public attack") {
            val game = base(24).withLifeTotal(1, 3).withCardOnBattlefield(1, "Baleful Strix")
                .withCardOnBattlefield(1, "Krark-Clan Shaman").withCardOnBattlefield(2, "Sneaky Snacker")
                .withCardOnBattlefield(2, "Guttersnipe").withActivePlayer(2).withPriorityPlayer(2)
                .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS).build()
            val snacker = game.findPermanent("Sneaky Snacker")!!
            val gutter = game.findPermanent("Guttersnipe")!!
            game.execute(DeclareAttackers(p2, mapOf(snacker to p1, gutter to p1))).error shouldBe null
            var passes = 0
            while (game.state.step != Step.DECLARE_BLOCKERS) {
                require(passes++ < 6) { "Fixed A24 did not reach blockers within six priority passes" }
                game.passPriority().error shouldBe null
            }
            game.state.priorityPlayerId shouldBe p1
            val action = game.choose(24).shouldBeInstanceOf<DeclareBlockers>()
            val strix = game.findPermanent("Baleful Strix")!!
            val shaman = game.findPermanent("Krark-Clan Shaman")!!
            (snacker in action.blockers[shaman].orEmpty()) shouldBe false
            (action.blockers[strix].orEmpty().contains(snacker) || action.blockers.values.any { gutter in it }) shouldBe true
            game.execute(action).error shouldBe null
        }
    }
}
