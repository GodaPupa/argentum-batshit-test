package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.AbilityFizzledEvent
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.DamageDealtEvent
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.SpellFizzledEvent
import com.wingedsheep.engine.core.TargetingRestrictionCreatedEvent
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.handlers.TargetFinder
import com.wingedsheep.engine.legalactions.utils.TargetEnumerationUtils
import com.wingedsheep.engine.mechanics.targeting.FloatingTargetingRestriction
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.engine.view.ClientStateTransformer
import com.wingedsheep.mtg.sets.definitions.apc.cards.FireIce
import com.wingedsheep.mtg.sets.definitions.gpt.cards.IzzetGuildmage
import com.wingedsheep.mtg.sets.definitions.zen.cards.VinesOfVastwood
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.GameObjectFilter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Fixed engine fixtures only; no frozen matchup seed, pilot or outcome is used. */
class VinesOfVastwoodScenarioTest : FunSpec({
    val wand = card("Fixture Targeting Wand") {
        manaCost = "{0}"
        typeLine = "Artifact"
        activatedAbility {
            cost = Costs.Tap
            val creature = target("creature", Targets.Creature)
            effect = Effects.DealDamage(1, creature)
        }
    }
    val strip = card("Fixture Remove Abilities") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell { effect = Effects.RemoveAllAbilities(target("creature", Targets.Creature)) }
    }
    val control = card("Fixture Gain Control") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell { effect = Effects.GainControl(target("creature", Targets.Creature)) }
    }
    val bounce = card("Fixture Return Creature") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell { effect = Effects.ReturnToHand(target("creature", Targets.Creature)) }
    }
    val sweeper = card("Fixture Destroy Creatures") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell { effect = Effects.DestroyAll(GameObjectFilter.Creature) }
    }
    val arrivalDamage = card("Fixture Targeted Arrival") {
        manaCost = "{0}"
        typeLine = "Artifact"
        keywords(Keyword.FLASH)
        triggeredAbility {
            trigger = Triggers.EntersBattlefield
            effect = Effects.DealDamage(3, target("creature", Targets.Creature))
        }
    }
    val splitArrival = card("Fixture Split Arrival") {
        manaCost = "{0}"
        typeLine = "Artifact"
        keywords(Keyword.FLASH)
        triggeredAbility {
            trigger = Triggers.EntersBattlefield
            val first = target("first", Targets.Creature)
            val second = target("second", Targets.Creature)
            effect = Effects.DealDamage(1, first).then(Effects.DealDamage(2, second))
        }
    }
    val splitDamage = card("Fixture Split Damage") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell {
            val first = target("first", Targets.Creature)
            val second = target("second", Targets.Creature)
            effect = Effects.DealDamage(1, first).then(Effects.DealDamage(2, second))
        }
    }

    fun game(): GameTestDriver = GameTestDriver().also { d ->
        d.registerCards(TestCards.all + listOf(VinesOfVastwood, IzzetGuildmage, FireIce, wand, strip, control, bounce, sweeper,
            arrivalDamage, splitArrival, splitDamage))
        d.initMirrorMatch(Deck.of("Forest" to 40), startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun vines(d: GameTestDriver, controller: EntityId, creature: EntityId, kicked: Boolean = false): EntityId {
        d.giveMana(controller, Color.GREEN, if (kicked) 2 else 1)
        val spell = d.putCardInHand(controller, "Vines of Vastwood")
        d.submitSuccess(CastSpell(controller, spell,
            targets = listOf(ChosenTarget.Permanent(creature)),
            declaredCostSlot = if (kicked) ChoiceSlot.KICKED else null,
            paymentStrategy = PaymentStrategy.FromPool))
        return spell
    }

    fun resolves(d: GameTestDriver) {
        repeat(12) {
            if (d.state.stack.isEmpty()) return
            d.pendingDecision shouldBe null
            d.bothPass().isSuccess shouldBe true
        }
        error("Fixture stack did not resolve")
    }

    test("unkicked spends one green without a boost and restricts only the caster's opponents") {
        val d = game()
        val creature = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val spell = vines(d, d.player1, creature)
        resolves(d)
        d.state.getEntity(d.player1)!!.get<ManaPoolComponent>()!!.green shouldBe 0
        d.state.projectedState.getPower(creature) shouldBe 3
        d.state.projectedState.getToughness(creature) shouldBe 3
        d.state.projectedState.hasKeyword(creature, Keyword.HEXPROOF) shouldBe false
        FloatingTargetingRestriction.prevents(d.state, creature, d.player1) shouldBe false
        FloatingTargetingRestriction.prevents(d.state, creature, d.player2) shouldBe true
        d.events.filterIsInstance<TargetingRestrictionCreatedEvent>().single() shouldBe
            TargetingRestrictionCreatedEvent(creature, setOf(d.player2), spell)
    }

    test("kicked spends two green and gives plus four plus four") {
        val d = game()
        val creature = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        vines(d, d.player1, creature, kicked = true)
        resolves(d)
        d.state.getEntity(d.player1)!!.get<ManaPoolComponent>()!!.green shouldBe 0
        d.state.projectedState.getPower(creature) shouldBe 7
        d.state.projectedState.getToughness(creature) shouldBe 7
        FloatingTargetingRestriction.prevents(d.state, creature, d.player2) shouldBe true
    }

    test("declared kicker without its second green is rejected atomically") {
        val d = game()
        val creature = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        d.giveMana(d.player1, Color.GREEN, 1)
        val spell = d.putCardInHand(d.player1, "Vines of Vastwood")
        val before = d.state
        val rejected = d.submit(CastSpell(d.player1, spell, targets = listOf(ChosenTarget.Permanent(creature)),
            declaredCostSlot = ChoiceSlot.KICKED, paymentStrategy = PaymentStrategy.FromPool))
        rejected.isSuccess shouldBe false
        rejected.newState shouldBe before
        d.state shouldBe before
    }

    test("player and noncreature targets are illegal and do not pay mana") {
        for (playerTarget in listOf(true, false)) {
            val d = game()
            val land = d.putLandOnBattlefield(d.player1, "Forest")
            d.giveMana(d.player1, Color.GREEN, 1)
            val spell = d.putCardInHand(d.player1, "Vines of Vastwood")
            val before = d.state
            val target = if (playerTarget) ChosenTarget.Player(d.player2) else ChosenTarget.Permanent(land)
            val rejected = d.submit(CastSpell(d.player1, spell, targets = listOf(target),
                paymentStrategy = PaymentStrategy.FromPool))
            rejected.isSuccess shouldBe false
            rejected.newState shouldBe before
            d.state shouldBe before
        }
    }

    test("targeting an opponent's creature prevents that opponent's spells and abilities") {
        val d = game()
        val creature = d.putCreatureOnBattlefield(d.player2, "Centaur Courser")
        val artifact = d.putPermanentOnBattlefield(d.player2, wand.name)
        vines(d, d.player1, creature)
        resolves(d)
        d.passPriority(d.player1).isSuccess shouldBe true
        d.giveMana(d.player2, Color.GREEN, 1)
        val growth = d.putCardInHand(d.player2, "Giant Growth")
        val before = d.state
        val rejectedSpell = d.castSpell(d.player2, growth, listOf(creature))
        rejectedSpell.isSuccess shouldBe false
        rejectedSpell.newState shouldBe before
        d.state shouldBe before
        val rejectedAbility = d.submit(ActivateAbility(d.player2, artifact, wand.activatedAbilities.single().id,
            targets = listOf(ChosenTarget.Permanent(creature))))
        rejectedAbility.isSuccess shouldBe false
        rejectedAbility.newState shouldBe before
        d.state shouldBe before
    }

    test("the Vines caster may still target the opposing creature") {
        val d = game()
        val creature = d.putCreatureOnBattlefield(d.player2, "Centaur Courser")
        vines(d, d.player1, creature)
        resolves(d)
        d.giveMana(d.player1, Color.GREEN, 1)
        val growth = d.putCardInHand(d.player1, "Giant Growth")
        d.castSpell(d.player1, growth, listOf(creature)).isSuccess shouldBe true
        resolves(d)
        d.state.projectedState.getPower(creature) shouldBe 6
    }

    test("Vines in response makes an opponent's already targeted removal fizzle") {
        val d = game()
        val creature = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        d.passPriority(d.player1).isSuccess shouldBe true
        d.giveMana(d.player2, Color.RED, 1)
        val bolt = d.putCardInHand(d.player2, "Lightning Bolt")
        d.castSpell(d.player2, bolt, listOf(creature)).isSuccess shouldBe true
        d.passPriority(d.player2).isSuccess shouldBe true
        vines(d, d.player1, creature)
        resolves(d)
        (creature in d.state.getBattlefield()) shouldBe true
        (bolt in d.getGraveyard(d.player2)) shouldBe true
        d.events.filterIsInstance<SpellFizzledEvent>().count { it.spellEntityId == bolt } shouldBe 1
        d.events.filterIsInstance<DamageDealtEvent>().none { it.targetId == creature } shouldBe true
    }

    test("Vines in response makes an opponent's activated ability fizzle after its tap cost is paid") {
        val d = game()
        val creature = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val artifact = d.putPermanentOnBattlefield(d.player2, wand.name)
        d.passPriority(d.player1).isSuccess shouldBe true
        d.submit(ActivateAbility(d.player2, artifact, wand.activatedAbilities.single().id,
            targets = listOf(ChosenTarget.Permanent(creature)))).isSuccess shouldBe true
        d.passPriority(d.player2).isSuccess shouldBe true
        vines(d, d.player1, creature)
        resolves(d)
        d.events.filterIsInstance<AbilityFizzledEvent>().count { it.sourceId == artifact } shouldBe 1
        d.events.filterIsInstance<DamageDealtEvent>().none { it.targetId == creature } shouldBe true
        d.state.getEntity(artifact)!!.has<com.wingedsheep.engine.state.components.battlefield.TappedComponent>() shouldBe true
    }

    test("Vines in response makes an opponent's targeted enters ability fizzle") {
        val d = game()
        val creature = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val artifact = d.putCardInHand(d.player2, arrivalDamage.name)
        d.passPriority(d.player1).isSuccess shouldBe true
        d.castSpell(d.player2, artifact).isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true
        (d.pendingDecision is ChooseTargetsDecision) shouldBe true
        d.submitTargetSelection(d.player2, listOf(creature)).isSuccess shouldBe true
        d.priorityPlayer shouldBe d.player1
        vines(d, d.player1, creature)
        resolves(d)
        (creature in d.state.getBattlefield()) shouldBe true
        d.events.filterIsInstance<AbilityFizzledEvent>().count { it.sourceId == artifact } shouldBe 1
        d.events.filterIsInstance<DamageDealtEvent>().none { it.targetId == creature } shouldBe true
    }

    test("one restricted target does not fizzle another target or shift its named damage binding") {
        val d = game()
        val first = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val second = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val spell = d.putCardInHand(d.player2, splitDamage.name)
        d.passPriority(d.player1).isSuccess shouldBe true
        d.castSpell(d.player2, spell, listOf(first, second)).isSuccess shouldBe true
        d.passPriority(d.player2).isSuccess shouldBe true
        vines(d, d.player1, first)
        resolves(d)
        d.events.filterIsInstance<DamageDealtEvent>().none { it.targetId == first } shouldBe true
        d.events.filterIsInstance<DamageDealtEvent>().filter { it.targetId == second }.sumOf { it.amount } shouldBe 2
        d.events.filterIsInstance<SpellFizzledEvent>().none { it.spellEntityId == spell } shouldBe true
    }

    test("a partially restricted enters ability preserves the surviving target's original named slot") {
        val d = game()
        val first = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val second = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val artifact = d.putCardInHand(d.player2, splitArrival.name)
        d.passPriority(d.player1).isSuccess shouldBe true
        d.castSpell(d.player2, artifact).isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true
        (d.pendingDecision is ChooseTargetsDecision) shouldBe true
        d.submitMultiTargetSelection(d.player2, mapOf(0 to listOf(first), 1 to listOf(second))).isSuccess shouldBe true
        d.priorityPlayer shouldBe d.player1
        vines(d, d.player1, first)
        resolves(d)
        d.events.filterIsInstance<DamageDealtEvent>().none { it.targetId == first } shouldBe true
        d.events.filterIsInstance<DamageDealtEvent>().filter { it.targetId == second }.sumOf { it.amount } shouldBe 2
        d.events.filterIsInstance<AbilityFizzledEvent>().none { it.sourceId == artifact } shouldBe true
    }

    test("Vines drops Fire's illegal damage share without reallocating it to the surviving player target") {
        val d = game()
        val creature = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val spell = d.putCardInHand(d.player2, "Fire // Ice")
        val lifeBefore = d.getLifeTotal(d.player1)
        d.passPriority(d.player1).isSuccess shouldBe true
        d.giveMana(d.player2, Color.RED, 2)
        d.submitSuccess(CastSpell(d.player2, spell, faceIndex = 0,
            targets = listOf(ChosenTarget.Permanent(creature), ChosenTarget.Player(d.player1)),
            damageDistribution = mapOf(creature to 1, d.player1 to 1),
            paymentStrategy = PaymentStrategy.FromPool))
        d.passPriority(d.player2).isSuccess shouldBe true
        vines(d, d.player1, creature)
        resolves(d)
        d.events.filterIsInstance<DamageDealtEvent>().none { it.targetId == creature } shouldBe true
        d.events.filterIsInstance<DamageDealtEvent>().filter { it.targetId == d.player1 }.sumOf { it.amount } shouldBe 1
        d.getLifeTotal(d.player1) shouldBe lifeBefore - 1
        d.events.filterIsInstance<SpellFizzledEvent>().none { it.spellEntityId == spell } shouldBe true
    }

    test("removing Vines' target in response leaves no boost or targeting restriction") {
        val d = game()
        val creature = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        vines(d, d.player1, creature, kicked = true)
        d.passPriority(d.player1).isSuccess shouldBe true
        val returning = d.putCardInHand(d.player2, bounce.name)
        d.castSpell(d.player2, returning, listOf(creature)).isSuccess shouldBe true
        resolves(d)
        (creature in d.getHand(d.player1)) shouldBe true
        d.state.floatingEffects.none { creature in it.effect.affectedEntities } shouldBe true
        d.events.filterIsInstance<TargetingRestrictionCreatedEvent>().isEmpty() shouldBe true
    }

    test("control change keeps the original forbidden controller set") {
        val d = game()
        val creature = d.putCreatureOnBattlefield(d.player2, "Centaur Courser")
        vines(d, d.player1, creature)
        resolves(d)
        val theft = d.putCardInHand(d.player1, control.name)
        d.castSpell(d.player1, theft, listOf(creature)).isSuccess shouldBe true
        resolves(d)
        d.state.projectedState.getController(creature) shouldBe d.player1
        FloatingTargetingRestriction.prevents(d.state, creature, d.player1) shouldBe false
        FloatingTargetingRestriction.prevents(d.state, creature, d.player2) shouldBe true
    }

    test("losing all abilities does not remove the rule restriction") {
        val d = game()
        val creature = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        vines(d, d.player1, creature)
        resolves(d)
        val removing = d.putCardInHand(d.player1, strip.name)
        d.castSpell(d.player1, removing, listOf(creature)).isSuccess shouldBe true
        resolves(d)
        d.state.projectedState.hasKeyword(creature, Keyword.HEXPROOF) shouldBe false
        FloatingTargetingRestriction.prevents(d.state, creature, d.player2) shouldBe true
    }

    test("target enumeration hides forbidden choices while nontargeted selection remains available") {
        val d = game()
        val creature = d.putCreatureOnBattlefield(d.player2, "Centaur Courser")
        vines(d, d.player1, creature)
        resolves(d)
        (creature in TargetFinder().findLegalTargets(d.state, Targets.Creature, d.player2)) shouldBe false
        (creature in TargetEnumerationUtils(PredicateEvaluator()).findValidTargets(d.state, d.player2, Targets.Creature)) shouldBe false
        (creature in TargetFinder().findLegalTargets(d.state, Targets.Creature, d.player1)) shouldBe true
        (creature in TargetFinder().findLegalTargets(d.state, Targets.Creature, d.player2,
            ignoreTargetingRestrictions = true)) shouldBe true
    }

    test("nontargeted destruction still reaches a restricted creature") {
        val d = game()
        val creature = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        vines(d, d.player1, creature)
        resolves(d)
        d.passPriority(d.player1).isSuccess shouldBe true
        val destruction = d.putCardInHand(d.player2, sweeper.name)
        d.castSpell(d.player2, destruction).isSuccess shouldBe true
        resolves(d)
        (creature in d.getGraveyard(d.player1)) shouldBe true
    }

    test("ordinary cleanup expires both the restriction and kicked boost") {
        val d = game()
        val creature = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        vines(d, d.player1, creature, kicked = true)
        resolves(d)
        d.passPriorityUntil(Step.UPKEEP, maxPasses = 200)
        d.activePlayer shouldBe d.player2
        d.state.projectedState.getPower(creature) shouldBe 3
        FloatingTargetingRestriction.prevents(d.state, creature, d.player2) shouldBe false
    }

    test("leaving and recasting the same card does not reuse its old object restriction") {
        val d = game()
        val creature = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val original = d.state.objectRef(creature)
        vines(d, d.player1, creature)
        resolves(d)
        val returning = d.putCardInHand(d.player1, bounce.name)
        d.castSpell(d.player1, returning, listOf(creature)).isSuccess shouldBe true
        resolves(d)
        d.giveMana(d.player1, Color.GREEN, 3)
        d.castSpell(d.player1, creature).isSuccess shouldBe true
        resolves(d)
        d.state.objectRef(creature) shouldNotBe original
        FloatingTargetingRestriction.prevents(d.state, creature, d.player2) shouldBe false
        d.state.projectedState.getPower(creature) shouldBe 3
    }

    test("serialized pending Vines resolution reproduces exact state events and public badge") {
        val d = game()
        val creature = d.putCreatureOnBattlefield(d.player2, "Centaur Courser")
        vines(d, d.player1, creature, kicked = true)
        val pending = d.state
        d.bothPass().isSuccess shouldBe true
        val first = d.state
        val firstEvent = d.events.filterIsInstance<TargetingRestrictionCreatedEvent>().last()
        d.replaceState(SerializationTestSupport.roundTrip(pending))
        d.bothPass().isSuccess shouldBe true
        d.state shouldBe first
        d.events.filterIsInstance<TargetingRestrictionCreatedEvent>().last() shouldBe firstEvent
        val restored = SerializationTestSupport.roundTrip(first)
        for (viewer in listOf(d.player1, d.player2)) {
            val shown = ClientStateTransformer(d.cardRegistry).transform(restored, viewer).cards.getValue(creature)
            shown.activeEffects.count {
                it.name == "Targeting restricted" && it.description?.contains("Player 2") == true
            } shouldBe 1
        }
    }

    test("Izzet Guildmage copies a kicked Vines with a new target and retains both paid choices") {
        val d = game()
        val first = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val second = d.putCreatureOnBattlefield(d.player2, "Centaur Courser")
        val guildmage = d.putCreatureOnBattlefield(d.player1, "Izzet Guildmage")
        val spell = vines(d, d.player1, first, kicked = true)
        d.giveMana(d.player1, Color.BLUE, 3)
        d.submit(ActivateAbility(d.player1, guildmage, IzzetGuildmage.activatedAbilities[0].id,
            targets = listOf(ChosenTarget.Spell(spell)))).isSuccess shouldBe true
        d.bothPass()
        (d.pendingDecision is ChooseTargetsDecision) shouldBe true
        d.replaceState(SerializationTestSupport.roundTrip(d.state))
        d.submitTargetSelection(d.player1, listOf(second)).isSuccess shouldBe true
        resolves(d)
        d.state.projectedState.getPower(first) shouldBe 7
        d.state.projectedState.getPower(second) shouldBe 7
        listOf(first, second).forEach { FloatingTargetingRestriction.prevents(d.state, it, d.player2) shouldBe true }
        d.state.getEntity(d.player1)!!.get<ManaPoolComponent>()!!.green shouldBe 0
        d.state.getEntity(d.player1)!!.get<ManaPoolComponent>()!!.blue shouldBe 0
    }
})
