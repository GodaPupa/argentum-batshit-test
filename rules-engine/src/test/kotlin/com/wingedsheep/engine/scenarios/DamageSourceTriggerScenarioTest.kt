package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.state.components.battlefield.LastKnownPermanentComponent
import com.wingedsheep.engine.state.components.stack.ActivatedAbilityOnStackComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.GameRng
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Stack-creation boundaries and a fail-visible corrupted-evidence check for source LKI. */
class DamageSourceTriggerScenarioTest : ScenarioTestBase() {
    private val wave = Effects.ForEachInGroup(GroupFilter.AllCreatures, Effects.DealDamage(1, EffectTarget.Self))
    private val dying = card("Snapshot Dying Source") {
        manaCost = "{0}"
        typeLine = "Creature — Wizard"
        power = 1
        toughness = 2
        keywords(Keyword.DEATHTOUCH, Keyword.LIFELINK)
        oracleText = "Deathtouch, lifelink\nWhen this creature dies, it deals 1 damage to each creature."
        triggeredAbility { trigger = Triggers.Dies; effect = wave }
    }
    private val entering = card("Snapshot Entering Source") {
        manaCost = "{0}"
        typeLine = "Creature — Wizard"
        power = 1
        toughness = 2
        keywords(Keyword.DEATHTOUCH, Keyword.LIFELINK)
        oracleText = "Deathtouch, lifelink\nWhen this creature enters, it deals 1 damage to each creature."
        triggeredAbility { trigger = Triggers.EntersBattlefield; effect = wave }
    }
    private val activated = card("Snapshot Activated Source") {
        manaCost = "{0}"
        typeLine = "Creature — Wizard"
        power = 1
        toughness = 2
        keywords(Keyword.DEATHTOUCH, Keyword.LIFELINK)
        oracleText = "Deathtouch, lifelink\n{0}: This creature deals 1 damage to each creature."
        activatedAbility { cost = Costs.Mana("{0}"); effect = wave }
    }
    private val destroy = card("Snapshot Destroy") {
        manaCost = "{0}"
        typeLine = "Instant"
        oracleText = "Destroy target creature."
        spell { val t = target("target creature", Targets.Creature); effect = Effects.Destroy(t) }
    }
    private val sacrificial = card("Snapshot Sacrificial Source") {
        manaCost = "{0}"
        typeLine = "Artifact Creature — Wizard"
        power = 1
        toughness = 2
        keywords(Keyword.DEATHTOUCH, Keyword.LIFELINK)
        oracleText = "Deathtouch, lifelink\nSacrifice an artifact: This creature deals 1 damage to each creature."
        activatedAbility { cost = Costs.Sacrifice(GameObjectFilter.Artifact); effect = wave }
    }
    private val talking = card("Snapshot Talking Source") {
        manaCost = "{U}"
        colorIdentity = "U"
        typeLine = "Creature — Wizard"
        power = 1
        toughness = 2
        oracleText = "{0}: This creature deals 1 damage to each creature.\nWhenever this creature deals damage, draw a card."
        activatedAbility { cost = Costs.Mana("{0}"); effect = wave }
        triggeredAbility { trigger = Triggers.dealsDamage(); effect = Effects.DrawCards(1) }
    }
    private val observer = card("Snapshot Blue Observer") {
        manaCost = "{0}"
        typeLine = "Enchantment"
        oracleText = "Whenever a blue creature an opponent controls deals damage, you gain 1 life."
        triggeredAbility {
            trigger = Triggers.dealsDamage(
                sourceFilter = GameObjectFilter.Creature.withColor(Color.BLUE).opponentControls(),
                binding = TriggerBinding.ANY,
            )
            effect = Effects.GainLife(1)
        }
    }
    private val recall = card("Snapshot Recall") {
        manaCost = "{0}"
        typeLine = "Instant"
        oracleText = "Return target creature card from a graveyard to the battlefield under its owner's control. It becomes red until end of turn."
        spell {
            val t = target("target creature card", Targets.CreatureCardInGraveyard)
            effect = Effects.PutOntoBattlefieldFromGraveyard(t) then Effects.ChangeColor(t, setOf(Color.RED))
        }
    }

    private fun setup() = scenario().withPlayers("Source controller", "Opponent")
        .withCardOnBattlefield(2, "Craw Wurm")
        .withCardInHand(1, destroy.name)
        .withCardInLibrary(1, "Island").withCardInLibrary(2, "Plains")
        .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

    private fun TestGame.fixed() = apply { state = state.copy(rng = GameRng.seeded(0xFE000078)) }
    private fun TestGame.resolveOne() {
        passPriority().error shouldBe null
        passPriority().error shouldBe null
        state.pendingDecision shouldBe null
    }
    private fun TestGame.finish() {
        resolveStack().forEach { it.error shouldBe null }
        state.stack.isEmpty() shouldBe true
        state.pendingDecision shouldBe null
        state.gameOver shouldBe false
    }

    init {
        cardRegistry.register(listOf(dying, entering, activated, destroy, sacrificial, talking, observer, recall))

        for (token in listOf(false, true)) {
            test("a dies trigger retains source damage characteristics before token cleanup; token=$token") {
                val game = setup().withCardOnBattlefield(1, dying.name, isToken = token).build().fixed()
                val id = game.findPermanent(dying.name)!!
                game.castSpell(1, destroy.name, id).error shouldBe null
                game.resolveOne()
                game.isOnBattlefield(dying.name) shouldBe false
                if (token) game.state.getEntity(id) shouldBe null
                game.state.stack.size shouldBe 1
                game.finish()
                game.isInGraveyard(2, "Craw Wurm") shouldBe true
                game.getLifeTotal(1) shouldBe 21
            }
        }

        test("a triggered ability already on the stack gains departure LKI when its source later dies") {
            val game = setup().withCardInHand(1, entering.name).build().fixed()
            game.castSpell(1, entering.name).error shouldBe null
            game.resolveOne()
            val id = game.findPermanent(entering.name)!!
            game.state.stack.size shouldBe 1
            game.castSpell(1, destroy.name, id).error shouldBe null
            game.resolveOne()
            game.isInGraveyard(1, entering.name) shouldBe true
            game.finish()
            game.isInGraveyard(2, "Craw Wurm") shouldBe true
            game.getLifeTotal(1) shouldBe 21
        }

        test("generic sacrifice cost retains a token source before its ability enters the stack") {
            val game = setup().withCardOnBattlefield(1, sacrificial.name, isToken = true).build().fixed()
            val id = game.findPermanent(sacrificial.name)!!
            game.execute(ActivateAbility(
                game.player1Id, id, sacrificial.activatedAbilities.single().id,
                costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(id)),
            )).error shouldBe null
            game.state.getEntity(id) shouldBe null
            game.state.stack.size shouldBe 1
            game.finish()
            game.isInGraveyard(2, "Craw Wurm") shouldBe true
            game.getLifeTotal(1) shouldBe 21
        }

        test("missing required source snapshot fails visibly instead of a silent damage result") {
            // A real entry supplies the battlefield-visit timestamp. The corruption below
            // removes historical characteristics while retaining proof of battlefield origin.
            val game = setup().withCardInHand(1, activated.name).build().fixed()
            game.castSpell(1, activated.name).error shouldBe null
            game.resolveOne()
            val id = game.findPermanent(activated.name)!!
            game.execute(ActivateAbility(game.player1Id, id, activated.activatedAbilities.single().id)).error shouldBe null
            game.castSpell(1, destroy.name, id).error shouldBe null
            game.resolveOne()
            val abilityId = game.state.stack.single()
            // Deliberate corrupted replay state, not a legal gameplay operation. The error is
            // essential: absent historical data must never borrow a new object's properties.
            game.state = game.state.updateEntity(abilityId) { entity ->
                entity.with(entity.get<ActivatedAbilityOnStackComponent>()!!.copy(lastKnownSourceSnapshot = null))
            }.updateEntity(id) { it.without<LastKnownPermanentComponent>() }
            game.passPriority().error shouldBe null
            val failure = shouldThrow<IllegalStateException> { game.passPriority() }
            failure.message!!.contains("Missing last-known information") shouldBe true
            game.isOnBattlefield("Craw Wurm") shouldBe true
        }

        test("old source damage triggers the matching observer but not a returned object's own ability") {
            val game = setup().withCardOnBattlefield(1, talking.name)
                .withCardOnBattlefield(2, observer.name).withCardInHand(1, recall.name).build().fixed()
            val id = game.findPermanent(talking.name)!!
            game.execute(ActivateAbility(game.player1Id, id, talking.activatedAbilities.single().id)).error shouldBe null
            game.castSpell(1, destroy.name, id).error shouldBe null
            game.resolveOne()
            game.castSpellTargetingGraveyardCard(1, recall.name, listOf(id)).error shouldBe null
            game.resolveOne()
            game.state.projectedState.getColors(id) shouldBe setOf(Color.RED.name)
            // Resolve the old damage ability, then explicitly place the two observer triggers.
            // The returned object's own draw ability must not join this batch.
            game.passPriority().error shouldBe null
            game.passPriority().error shouldBe null
            val ordering = game.state.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
            ordering.playerId shouldBe game.player2Id
            ordering.options.size shouldBe 2
            ordering.options.all { it.contains(observer.name) } shouldBe true
            game.getLifeTotal(2) shouldBe 20
            game.submitDecision(OptionChosenResponse(ordering.id, 0)).error shouldBe null
            game.state.stack.map {
                game.state.getEntity(it)!!.get<TriggeredAbilityOnStackComponent>()!!.sourceName
            } shouldBe listOf(observer.name, observer.name)
            game.finish()
            game.state.getHand(game.player1Id).isEmpty() shouldBe true
            game.findPermanent(talking.name) shouldBe id
            game.getLifeTotal(1) shouldBe 20
            game.getLifeTotal(2) shouldBe 22
        }
    }
}
