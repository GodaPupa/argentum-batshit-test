package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.CastSpellRecord
import com.wingedsheep.engine.state.components.battlefield.CastChoicesComponent
import com.wingedsheep.engine.state.components.identity.TeamComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.ogw.cards.RecklessBushwhacker
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Exact Reckless Bushwhacker qualification on the durable alternative-cost provenance seam.
 * No Manual pilot/gameplay state is initialized here.
 */
class RecklessBushwhackerScenarioTest : FunSpec({
    val setupSpell = card("Bushwhacker Setup Spell") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        spell { effect = Effects.GainLife(1) }
    }
    val bounce = card("Bushwhacker Provenance Bounce") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell {
            val target = target("target creature", Targets.Creature)
            effect = Effects.ReturnToHand(target)
        }
    }
    val blink = card("Bushwhacker Provenance Blink") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell {
            target("target creature", Targets.Creature)
            effect = Effects.Pipeline {
                val selected = gather(CardSource.ChosenTargets)
                val exiled = moveTracked(selected, CardDestination.ToZone(Zone.EXILE))
                move(exiled, CardDestination.ToZone(Zone.BATTLEFIELD))
            }
        }
    }

    fun driver(): GameTestDriver = GameTestDriver().also {
        it.registerCards(TestCards.all + listOf(RecklessBushwhacker, setupSpell, bounce, blink))
        it.initMirrorMatch(Deck.of("Mountain" to 40))
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun castSetup(d: GameTestDriver) {
        val id = d.putCardInHand(d.player1, setupSpell.name)
        d.castSpell(d.player1, id).error shouldBe null
        d.bothPass().error shouldBe null
    }

    fun actionsFor(d: GameTestDriver, cardId: com.wingedsheep.sdk.model.EntityId) =
        d.legalActions(d.player1).filter { (it.action as? CastSpell)?.cardId == cardId }

    fun surgeActionExists(d: GameTestDriver, cardId: com.wingedsheep.sdk.model.EntityId): Boolean =
        actionsFor(d, cardId).any {
            val cast = it.action as CastSpell
            cast.useAlternativeCost && cast.alternativeCostType == AlternativeCostType.SELF_ALTERNATIVE
        }

    fun normalActionExists(d: GameTestDriver, cardId: com.wingedsheep.sdk.model.EntityId): Boolean =
        actionsFor(d, cardId).any {
            val cast = it.action as CastSpell
            !cast.useAlternativeCost
        }

    fun castSurged(d: GameTestDriver): com.wingedsheep.sdk.model.EntityId {
        castSetup(d)
        val id = d.putCardInHand(d.player1, RecklessBushwhacker.name)
        d.giveMana(d.player1, Color.RED, 2)
        d.submit(
            CastSpell(
                playerId = d.player1,
                cardId = id,
                useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.SELF_ALTERNATIVE,
                paymentStrategy = PaymentStrategy.FromPool
            )
        ).error shouldBe null
        d.bothPass().error shouldBe null
        return id
    }

    fun bushwhackerTriggers(d: GameTestDriver) =
        d.state.stack.mapNotNull {
            d.state.getEntity(it)?.get<TriggeredAbilityOnStackComponent>()
        }.filter { it.sourceName == RecklessBushwhacker.name }

    test("surge is not offered without a prior spell while the normal cost remains available") {
        val d = driver()
        val id = d.putCardInHand(d.player1, RecklessBushwhacker.name)
        d.giveMana(d.player1, Color.RED, 3)

        normalActionExists(d, id) shouldBe true
        surgeActionExists(d, id) shouldBe false
    }

    test("after you cast another spell both normal and surge costs are offered at their exact prices") {
        val d = driver()
        castSetup(d)
        val id = d.putCardInHand(d.player1, RecklessBushwhacker.name)
        d.giveMana(d.player1, Color.RED, 3)
        val actions = actionsFor(d, id)

        actions.any {
            val cast = it.action as CastSpell
            !cast.useAlternativeCost && it.manaCostString == "{2}{R}"
        } shouldBe true
        actions.any {
            val cast = it.action as CastSpell
            cast.useAlternativeCost &&
                cast.alternativeCostType == AlternativeCostType.SELF_ALTERNATIVE &&
                it.manaCostString == "{1}{R}"
        } shouldBe true
    }

    test("a teammate's earlier spell also enables surge") {
        val d = driver()
        val id = d.putCardInHand(d.player1, RecklessBushwhacker.name)
        val record = CastSpellRecord(
            typeLine = setupSpell.typeLine,
            manaValue = 0,
            colors = emptySet(),
            isFaceDown = false,
            castFromZone = Zone.HAND,
            name = setupSpell.name
        )
        var teamed = d.state
            .updateEntity(d.player1) { it.with(TeamComponent(7)) }
            .updateEntity(d.player2) { it.with(TeamComponent(7)) }
        teamed = teamed.copy(
            spellsCastThisTurnByPlayer = teamed.spellsCastThisTurnByPlayer + (d.player2 to listOf(record))
        )
        d.replaceState(teamed)
        d.giveMana(d.player1, Color.RED, 2)

        surgeActionExists(d, id) shouldBe true
    }

    test("surge-paid ETB gives exactly plus one power and haste to other existing creatures only") {
        val d = driver()
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val bushwhacker = castSurged(d)

        bushwhackerTriggers(d).size shouldBe 1
        d.bothPass().error shouldBe null

        d.state.projectedState.getPower(bear) shouldBe 3
        d.state.projectedState.getToughness(bear) shouldBe 2
        d.state.projectedState.getKeywords(bear).contains(Keyword.HASTE.name) shouldBe true
        d.state.projectedState.getPower(bushwhacker) shouldBe 2
        d.state.projectedState.getToughness(bushwhacker) shouldBe 1
        d.state.projectedState.getKeywords(bushwhacker).contains(Keyword.HASTE.name) shouldBe true
    }

    test("ordinary cast after another spell does not receive the surge ETB payoff") {
        val d = driver()
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        castSetup(d)
        val id = d.putCardInHand(d.player1, RecklessBushwhacker.name)
        d.giveMana(d.player1, Color.RED, 3)

        d.castSpell(d.player1, id).error shouldBe null
        d.bothPass().error shouldBe null

        bushwhackerTriggers(d).size shouldBe 0
        d.state.projectedState.getPower(bear) shouldBe 2
        d.state.projectedState.getKeywords(bear).contains(Keyword.HASTE.name) shouldBe false
        d.state.getEntity(id)?.get<CastChoicesComponent>()?.alternativeCost shouldBe null
    }

    test("pending surged ETB keeps paid-cost truth after Bushwhacker leaves") {
        val d = driver()
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val bushwhacker = castSurged(d)
        val oldRef = d.state.objectRef(bushwhacker)!!
        bushwhackerTriggers(d).size shouldBe 1

        val bounceId = d.putCardInHand(d.player1, bounce.name)
        d.castSpell(d.player1, bounceId, listOf(bushwhacker)).error shouldBe null
        d.bothPass().error shouldBe null

        d.state.isCurrentObject(oldRef) shouldBe false
        bushwhackerTriggers(d).size shouldBe 1
        d.bothPass().error shouldBe null
        d.state.projectedState.getPower(bear) shouldBe 3
        d.state.projectedState.getKeywords(bear).contains(Keyword.HASTE.name) shouldBe true
    }

    test("blink makes a fresh non-surge visit without duplicating the old pending payoff") {
        val d = driver()
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val bushwhacker = castSurged(d)
        val oldRef = d.state.objectRef(bushwhacker)!!
        bushwhackerTriggers(d).size shouldBe 1

        val blinkId = d.putCardInHand(d.player1, blink.name)
        d.castSpell(d.player1, blinkId, listOf(bushwhacker)).error shouldBe null
        d.bothPass().error shouldBe null

        d.state.isCurrentObject(oldRef) shouldBe false
        d.state.getEntity(bushwhacker)?.get<CastChoicesComponent>()?.alternativeCost shouldBe null
        bushwhackerTriggers(d).size shouldBe 1

        d.bothPass().error shouldBe null
        d.state.projectedState.getPower(bear) shouldBe 3
        d.state.projectedState.getKeywords(bear).contains(Keyword.HASTE.name) shouldBe true
    }

    test("surge payoff snapshots recipients and expires at cleanup") {
        val d = driver()
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        castSurged(d)
        d.bothPass().error shouldBe null

        val lateBear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.state.projectedState.getPower(bear) shouldBe 3
        d.state.projectedState.getPower(lateBear) shouldBe 2
        d.state.projectedState.getKeywords(lateBear).contains(Keyword.HASTE.name) shouldBe false

        d.passPriorityUntil(Step.CLEANUP)
        d.state.projectedState.getPower(bear) shouldBe 2
        d.state.projectedState.getKeywords(bear).contains(Keyword.HASTE.name) shouldBe false
    }

    test("canonical Oath of the Gatewatch printing metadata and characteristics are preserved") {
        RecklessBushwhacker.manaCost shouldBe ManaCost.parse("{2}{R}")
        RecklessBushwhacker.typeLine.cardTypes shouldBe setOf(CardType.CREATURE)
        RecklessBushwhacker.typeLine.subtypes.map { it.value }.toSet() shouldBe setOf("Goblin", "Warrior", "Ally")
        RecklessBushwhacker.keywords.contains(Keyword.HASTE) shouldBe true
        RecklessBushwhacker.metadata.rarity shouldBe Rarity.UNCOMMON
        RecklessBushwhacker.metadata.collectorNumber shouldBe "116"
        RecklessBushwhacker.metadata.artist shouldBe "Kieran Yanner"
        RecklessBushwhacker.metadata.imageUri shouldBe
            "https://cards.scryfall.io/normal/front/0/4/0405b1b9-976a-4aaf-bec6-fa006decea74.jpg"
        RecklessBushwhacker.oracleText shouldBe
            "Surge {1}{R} (You may cast this spell for its surge cost if you or a teammate has cast another spell this turn.)\n" +
            "Haste\n" +
            "When this creature enters, if its surge cost was paid, other creatures you control get +1/+0 and gain haste until end of turn."
    }
})
