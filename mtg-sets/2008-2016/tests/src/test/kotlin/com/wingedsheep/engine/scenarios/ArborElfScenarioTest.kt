package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.wwk.cards.ArborElf
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Fixed regression fixtures only; excluded from Phase 2 samples and seed allocations. */
class ArborElfScenarioTest : ScenarioTestBase() {
    private fun TestGame.activate(land: EntityId) = execute(ActivateAbility(
        playerId = player1Id,
        sourceId = findPermanent("Arbor Elf")!!,
        abilityId = ArborElf.activatedAbilities.single().id,
        targets = listOf(ChosenTarget.Permanent(land))
    ))

    private fun TestGame.tapped(id: EntityId) = state.getEntity(id)!!.has<TappedComponent>()

    private fun TestGame.settle() {
        resolveStack().forEach { it.error shouldBe null }
        state.stack.isEmpty() shouldBe true
        hasPendingDecision() shouldBe false
    }

    init {
        test("untapping a Forest uses the stack and pays the Elf tap cost without producing mana") {
            val game = scenario().withPlayers().withRngSeed(0x4152424f52L)
                .withCardOnBattlefield(1, "Arbor Elf")
                .withCardOnBattlefield(1, "Forest", tapped = true)
                .build()
            val forest = game.findPermanent("Forest")!!
            game.activate(forest).error shouldBe null
            game.tapped(game.findPermanent("Arbor Elf")!!) shouldBe true
            game.tapped(forest) shouldBe true
            game.state.stack.size shouldBe 1
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.green shouldBe 0
            game.settle()
            game.tapped(forest) shouldBe false
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.green shouldBe 0
        }

        test("an opponent's nonbasic Tropical Island is a legal Forest target") {
            val game = scenario().withPlayers().withRngSeed(0x4152424f52L)
                .withCardOnBattlefield(1, "Arbor Elf")
                .withCardOnBattlefield(2, "Tropical Island", tapped = true)
                .build()
            val forest = game.findPermanent("Tropical Island")!!
            game.state.projectedState.hasSubtype(forest, "Forest") shouldBe true
            game.activate(forest).error shouldBe null
            game.settle()
            game.tapped(forest) shouldBe false
            game.state.projectedState.getController(forest) shouldBe game.player2Id
        }

        test("a land without the Forest subtype is rejected without spending the tap cost") {
            val game = scenario().withPlayers().withRngSeed(0x4152424f52L)
                .withCardOnBattlefield(1, "Arbor Elf")
                .withCardOnBattlefield(1, "Plains", tapped = true)
                .build()
            game.activate(game.findPermanent("Plains")!!).error shouldNotBe null
            game.tapped(game.findPermanent("Arbor Elf")!!) shouldBe false
            game.tapped(game.findPermanent("Plains")!!) shouldBe true
            game.state.stack.isEmpty() shouldBe true
        }

        test("a freshly cast Elf cannot pay its tap cost while summoning sick") {
            val game = scenario().withPlayers().withRngSeed(0x4152424f52L)
                .withCardInHand(1, "Arbor Elf")
                .withCardOnBattlefield(1, "Forest")
                .build()
            game.castSpell(1, "Arbor Elf").error shouldBe null
            game.settle()
            val forest = game.findPermanent("Forest")!!
            game.tapped(forest) shouldBe true
            game.activate(forest).error shouldNotBe null
            game.tapped(game.findPermanent("Arbor Elf")!!) shouldBe false
            game.tapped(forest) shouldBe true
            game.state.stack.isEmpty() shouldBe true
        }

        test("an already tapped Elf cannot activate again") {
            val game = scenario().withPlayers().withRngSeed(0x4152424f52L)
                .withCardOnBattlefield(1, "Arbor Elf", tapped = true)
                .withCardOnBattlefield(1, "Forest", tapped = true)
                .build()
            game.activate(game.findPermanent("Forest")!!).error shouldNotBe null
            game.tapped(game.findPermanent("Forest")!!) shouldBe true
            game.state.stack.isEmpty() shouldBe true
        }

        test("killing the Elf in response does not remove its pending untap ability") {
            val game = scenario().withPlayers().withRngSeed(0x4152424f52L)
                .withCardOnBattlefield(1, "Arbor Elf")
                .withCardOnBattlefield(1, "Forest", tapped = true)
                .withCardInHand(2, "Lightning Bolt")
                .withCardOnBattlefield(2, "Mountain")
                .build()
            val forest = game.findPermanent("Forest")!!
            val elf = game.findPermanent("Arbor Elf")!!
            game.activate(forest).error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpell(2, "Lightning Bolt", elf).error shouldBe null
            game.settle()
            game.isInGraveyard(1, "Arbor Elf") shouldBe true
            game.tapped(forest) shouldBe false
        }

        test("a Forest bounced in response makes the untap ability fizzle without refunding its cost") {
            val game = scenario().withPlayers().withRngSeed(0x4152424f52L)
                .withCardOnBattlefield(1, "Arbor Elf")
                .withCardOnBattlefield(1, "Forest", tapped = true)
                .withCardInHand(2, "Boomerang")
                .withLandsOnBattlefield(2, "Island", 2)
                .build()
            game.activate(game.findPermanent("Forest")!!).error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpell(2, "Boomerang", game.findPermanent("Forest")!!).error shouldBe null
            game.settle()
            game.findPermanent("Forest") shouldBe null
            game.findCardsInHand(1, "Forest").size shouldBe 1
            game.tapped(game.findPermanent("Arbor Elf")!!) shouldBe true
        }

        test("an untapped Forest is still a legal target and the Elf still pays its tap cost") {
            val game = scenario().withPlayers().withRngSeed(0x4152424f52L)
                .withCardOnBattlefield(1, "Arbor Elf")
                .withCardOnBattlefield(1, "Forest")
                .build()
            val forest = game.findPermanent("Forest")!!
            game.activate(forest).error shouldBe null
            game.settle()
            game.tapped(forest) shouldBe false
            game.tapped(game.findPermanent("Arbor Elf")!!) shouldBe true
        }
    }
}
