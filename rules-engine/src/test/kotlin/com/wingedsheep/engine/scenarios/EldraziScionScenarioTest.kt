package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EldraziScionScenarioTest : FunSpec({

    val maker = card("Scion Conjuration") {
        manaCost = "{1}"
        typeLine = "Sorcery"
        spell { effect = Effects.CreateEldraziScion() }
    }
    val manaSink = card("Scion Mana Sink") {
        manaCost = "{1}"
        typeLine = "Artifact"
    }
    val entryObserver = card("Scion Entry Observer") {
        manaCost = "{G}"
        typeLine = "Creature — Elf"
        power = 1
        toughness = 1
        triggeredAbility {
            trigger = Triggers.OtherCreatureEnters
            effect = Effects.GainLife(1)
        }
    }
    val deathObserver = card("Scion Death Observer") {
        manaCost = "{B}"
        typeLine = "Creature — Cleric"
        power = 1
        toughness = 1
        triggeredAbility {
            trigger = Triggers.AnyCreatureDies
            effect = Effects.GainLife(1)
        }
    }

    fun driver(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all + PredefinedTokens.allTokens + listOf(maker, manaSink, entryObserver, deathObserver))
        initMirrorMatch(Deck.of("Forest" to 20, "Swamp" to 20), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun GameTestDriver.resolveAll() {
        var guard = 0
        while (state.stack.isNotEmpty() && guard++ < 20) bothPass()
        check(guard < 20) { "Stack did not settle" }
    }

    test("authoritative Scion has the printed characteristics and its entry event reaches normal triggers") {
        val game = driver()
        val player = game.player1
        game.putCreatureOnBattlefield(player, entryObserver.name)
        val lifeBefore = game.getLifeTotal(player)
        val makerId = game.putCardInHand(player, maker.name)
        game.giveMana(player, Color.GREEN, 1)

        game.castSpell(player, makerId).error shouldBe null
        game.resolveAll()

        val scion = requireNotNull(game.findPermanent(player, "Eldrazi Scion"))
        val card = requireNotNull(game.state.getEntity(scion)?.get<CardComponent>())
        card.baseStats?.basePower shouldBe 1
        card.baseStats?.baseToughness shouldBe 1
        card.colors shouldBe emptySet()
        card.typeLine.cardTypes shouldBe setOf(CardType.CREATURE)
        card.typeLine.subtypes shouldBe setOf(Subtype("Eldrazi"), Subtype("Scion"))
        game.state.getEntity(scion)?.get<TokenComponent>() shouldBe TokenComponent
        game.getLifeTotal(player) shouldBe lifeBefore + 1
    }

    test("sacrifice pays the mana-ability cost, emits a visible death, and the mana pays a normal spell") {
        val game = driver()
        val player = game.player1
        game.putCreatureOnBattlefield(player, deathObserver.name)
        val scion = game.putCreatureOnBattlefield(player, PredefinedTokens.EldraziScion.name)
        game.replaceState(game.state.updateEntity(scion) { it.with(TokenComponent) })
        val sink = game.putCardInHand(player, manaSink.name)
        val lifeBefore = game.getLifeTotal(player)
        val abilityId = PredefinedTokens.EldraziScion.activatedAbilities.single().id

        game.submitSuccess(ActivateAbility(playerId = player, sourceId = scion, abilityId = abilityId))

        game.findPermanent(player, "Eldrazi Scion") shouldBe null
        game.state.getEntity(scion) shouldBe null
        game.state.getEntity(player)?.get<ManaPoolComponent>()?.colorless shouldBe 1

        game.resolveAll()
        game.getLifeTotal(player) shouldBe lifeBefore + 1

        game.castSpell(player, sink).error shouldBe null
        game.state.getEntity(player)?.get<ManaPoolComponent>()?.colorless shouldBe 0
        game.resolveAll()
    }
})
