package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.ObjectReferenceEnvironment
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.mechanics.targeting.*
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.targets.*
import io.kotest.matchers.shouldBe

/** Uncompiled partial draft. Test-local registry only; no discoverable production definition. */
class SpellskiteScenarioTest : ScenarioTestBase() {
    init {
        for (kind in listOf("activation", "trigger")) for (targetCount in listOf(0, 1))
            for (payLife in listOf(false, true)) {
                test("Spellskite actual $kind targetCount=$targetCount payLife=$payLife") {
                    val definition = card("Spellskite") {
                        manaCost = "{2}"
                        typeLine = "Artifact Creature — Phyrexian Horror"
                        power = 0; toughness = 4
                        activatedAbility {
                            cost = Costs.Mana("{U/P}")
                            val selectedStack = target("spell or ability", Targets.SpellOrAbility)
                            effect = Effects.ChangeTargetTo(stackObject = selectedStack, recipient = EffectTarget.Self)
                        }
                    }
                    cardRegistry.register(definition)
                    val game = scenario().withPlayers("One", "Two").withPriorityPlayer(2)
                        .withCardOnBattlefield(1, "Grizzly Bears")
                        .withCardOnBattlefield(1, "Hill Giant")
                        .withCardOnBattlefield(2, "Spellskite")
                        .withCardInLibrary(1, "Forest").withCardInLibrary(2, "Island").build()
                    val source = game.findPermanent("Grizzly Bears")!!
                    val old = game.findPermanent("Hill Giant")!!
                    val recipient = game.findPermanent("Spellskite")!!
                    val initial = game.state.updateEntity(game.player2Id) { it.with(ManaPoolComponent(blue = 1)) }
                    val selection = TargetSlotPlan(if (targetCount == 0) emptyList() else listOf(
                        TargetInstanceSelection(listOf(TargetSlot(ChosenTarget.Permanent(old), initial.objectRef(old))))))
                    val plan = CurrentTargetPlan(selection)
                    val requirements = if (targetCount == 0) emptyList() else listOf(Targets.Creature)
                    val refs = ObjectReferenceEnvironment(captured = true, origin = initial.objectRef(source), source = initial.objectRef(source))
                    val resolver = StackResolver(cardRegistry)
                    val effect = if (targetCount == 0) Effects.GainLife(0) else Effects.DealDamage(1, EffectTarget.ContextTarget(0))
                    val queued = if (kind == "activation") resolver.putActivatedAbility(initial,
                        ActivatedAbilityOnStackComponent(source, "Grizzly Bears", game.player1Id, effect,
                            objectReferences = refs, originalTargetSlots = selection), plan.targets, requirements, currentTargetPlan = plan)
                    else resolver.putTriggeredAbility(initial,
                        TriggeredAbilityOnStackComponent(source, "Grizzly Bears", game.player1Id, effect, "Card acceptance fixture",
                            objectReferences = refs, originalTargetSlots = selection), plan.targets, requirements, currentTargetPlan = plan)
                    queued.error shouldBe null
                    val originalStack = queued.state.stack.last()
                    val choices = if (payLife) listOf(Color.BLUE) else emptyList()
                    val action = ActivateAbility(game.player2Id, recipient, definition.activatedAbilities.single().id,
                        targets = listOf(ChosenTarget.Spell(originalStack)),
                        paymentStrategy = PaymentStrategy.Explicit(emptyList(), choices))
                    val activated = ActionProcessor(cardRegistry).process(queued.state, action).result
                    activated.error shouldBe null
                    activated.state.stack.size shouldBe 2
                    activated.state.getEntity(game.player2Id)!!.get<ManaPoolComponent>()!!.blue shouldBe (if (payLife) 1 else 0)
                    val paid = activated.events.filterIsInstance<LifeChangedEvent>().filter { it.reason == LifeChangeReason.PAYMENT }
                    paid.size shouldBe (if (payLife) 1 else 0)
                    if (payLife) { paid.single().oldLife shouldBe 20; paid.single().newLife shouldBe 18 }
                    val resolved = resolver.resolveTop(activated.state)
                    resolved.error shouldBe null
                    resolved.state.pendingDecision shouldBe null
                    resolved.state.stack shouldBe listOf(originalStack)
                    val resultPlan = resolved.state.getEntity(originalStack)!!.currentTargetPlan()!!
                    if (targetCount == 0) resultPlan shouldBe plan
                    else {
                        resultPlan.targets shouldBe listOf(ChosenTarget.Permanent(recipient))
                        resultPlan.slots.single().objectRef shouldBe initial.objectRef(recipient)
                    }
                    resolved.events.filterIsInstance<BecomesTargetEvent>().size shouldBe targetCount
                    resolved.events.filterIsInstance<LifeChangedEvent>().filter { it.reason == LifeChangeReason.PAYMENT }.size shouldBe 0
                }
            }
    }
}
