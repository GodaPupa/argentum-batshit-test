package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.dom.cards.CastDown
import com.wingedsheep.mtg.sets.definitions.mrd.cards.SeatOfTheSynod
import com.wingedsheep.mtg.sets.definitions.mrd.cards.VaultOfWhispers
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Exactly the two predeclared canonical Cast Down fixtures; no policy or matchup samples. */
class CastDownScenarioTest : ScenarioTestBase() {
    private val ordinary = card("Cast Down Nonlegendary Fixture") {
        manaCost = "{3}"
        typeLine = "Creature — Test"
        power = 3
        toughness = 3
    }
    private val legendary = card("Cast Down Legendary Fixture") {
        manaCost = "{3}"
        typeLine = "Legendary Creature — Test"
        power = 3
        toughness = 3
    }
    private val indestructible = card("Cast Down Indestructible Fixture") {
        manaCost = "{3}"
        typeLine = "Creature — Test"
        power = 3
        toughness = 3
        keywords(Keyword.INDESTRUCTIBLE)
    }

    private fun fixture(seed: Long) = scenario().withPlayers("Caster", "Target controller")
        .withRngSeed(seed)
        .withCardInHand(1, CastDown.name)
        .withCardOnBattlefield(1, VaultOfWhispers.name)
        .withCardOnBattlefield(1, SeatOfTheSynod.name)
        .withCardInLibrary(1, VaultOfWhispers.name)
        .withCardInLibrary(2, SeatOfTheSynod.name)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

    private fun TestGame.assertPrintedManaPaid() {
        for (name in listOf(VaultOfWhispers.name, SeatOfTheSynod.name)) {
            state.getEntity(findPermanent(name)!!)!!.has<TappedComponent>() shouldBe true
        }
        state.getEntity(player1Id)!!.get<ManaPoolComponent>()!!.total shouldBe 0
    }

    init {
        // Restore the actual production identities after ScenarioTestBase's TestCards overrides.
        // The opposing fixtures are only characteristic probes, never admitted deck definitions.
        cardRegistry.register(listOf(CastDown, VaultOfWhispers, SeatOfTheSynod, ordinary, legendary, indestructible))

        test("actual one-black-plus-one-generic instant payment destroys the nonlegendary target") {
            val game = fixture(202609269101L)
                .withActivePlayer(2).withPriorityPlayer(1)
                .withCardOnBattlefield(2, ordinary.name).build()
            val target = game.findPermanent(ordinary.name)!!
            val spell = game.findCardsInHand(1, CastDown.name).single()
            val cast = game.execute(CastSpell(game.player1Id, spell,
                targets = listOf(ChosenTarget.Permanent(target)), paymentStrategy = PaymentStrategy.AutoPay))
            cast.error shouldBe null
            game.assertPrintedManaPaid()
            game.state.activePlayerId shouldBe game.player2Id
            (target in game.state.getBattlefield()) shouldBe true
            game.state.stack shouldBe listOf(spell)
            game.state.pendingDecision shouldBe null

            game.passPriority().error shouldBe null
            game.passPriority().error shouldBe null
            game.state.stack shouldBe emptyList()
            (target in game.state.getBattlefield()) shouldBe false
            (target in game.state.getGraveyard(game.player2Id)) shouldBe true
            (spell in game.state.getGraveyard(game.player1Id)) shouldBe true
            game.getLifeTotal(1) shouldBe 20
            game.getLifeTotal(2) shouldBe 20
            game.state.gameOver shouldBe false
        }

        test("legendary targeting is rejected atomically but indestructible remains a legal paid target") {
            val game = fixture(202609269102L)
                .withActivePlayer(1).withPriorityPlayer(1)
                .withCardOnBattlefield(2, legendary.name)
                .withCardOnBattlefield(2, indestructible.name).build()
            val forbidden = game.findPermanent(legendary.name)!!
            val protected = game.findPermanent(indestructible.name)!!
            val spell = game.findCardsInHand(1, CastDown.name).single()
            val before = game.state
            val rejected = game.execute(CastSpell(game.player1Id, spell,
                targets = listOf(ChosenTarget.Permanent(forbidden)), paymentStrategy = PaymentStrategy.AutoPay))
            rejected.error shouldNotBe null
            rejected.state shouldBe before
            rejected.events shouldBe emptyList()
            game.state shouldBe before
            (spell in game.state.getHand(game.player1Id)) shouldBe true
            for (name in listOf(VaultOfWhispers.name, SeatOfTheSynod.name)) {
                game.state.getEntity(game.findPermanent(name)!!)!!.has<TappedComponent>() shouldBe false
            }

            // A legal target and a successful destruction are distinct (verified CR 702.12b).
            val accepted = game.execute(CastSpell(game.player1Id, spell,
                targets = listOf(ChosenTarget.Permanent(protected)), paymentStrategy = PaymentStrategy.AutoPay))
            accepted.error shouldBe null
            game.assertPrintedManaPaid()
            game.state.stack shouldBe listOf(spell)
            game.state.pendingDecision shouldBe null
            game.passPriority().error shouldBe null
            game.passPriority().error shouldBe null
            game.state.stack shouldBe emptyList()
            (spell in game.state.getGraveyard(game.player1Id)) shouldBe true
            (protected in game.state.getBattlefield()) shouldBe true
            (protected in game.state.getGraveyard(game.player2Id)) shouldBe false
            (forbidden in game.state.getBattlefield()) shouldBe true
            game.getLifeTotal(1) shouldBe 20
            game.getLifeTotal(2) shouldBe 20
            game.state.gameOver shouldBe false
        }
    }
}
