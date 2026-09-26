package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.scripting.AbilityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Seed-free exact-card support for the frozen Mono-Blue Terror sideboard identity. */
class SpreadingSeasScenarioTest : ScenarioTestBase() {
    init {
        test("casting Spreading Seas draws a card and replaces Forest with Island abilities") {
            val game = scenario()
                .withPlayers("Seas controller", "Land controller")
                .withCardInHand(1, "Spreading Seas")
                .withCardInLibrary(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Island", 2)
                .withCardOnBattlefield(2, "Forest")
                .build()

            val forest = game.findPermanent("Forest")!!
            val aura = game.findCardsInHand(1, "Spreading Seas").single()
            game.execute(
                CastSpell(
                    game.player1Id,
                    aura,
                    listOf(ChosenTarget.Permanent(forest)),
                )
            ).error shouldBe null
            game.resolveStack()

            game.state.getEntity(aura)!!.get<AttachedToComponent>()!!.targetId shouldBe forest
            game.isInHand(1, "Grizzly Bears") shouldBe true
            game.state.projectedState.hasSubtype(forest, "Forest") shouldBe false
            game.state.projectedState.hasSubtype(forest, "Island") shouldBe true
            game.state.projectedState.getController(forest) shouldBe game.player2Id

            game.passPriority().error shouldBe null
            val beforeIllegalGreen = game.state
            game.execute(ActivateAbility(game.player2Id, forest, AbilityId.intrinsicMana('G'))).error shouldNotBe null
            game.state shouldBe beforeIllegalGreen

            game.execute(ActivateAbility(game.player2Id, forest, AbilityId.intrinsicMana('U'))).error shouldBe null
            val pool = game.state.getEntity(game.player2Id)!!.get<ManaPoolComponent>()!!
            pool.blue shouldBe 1
            pool.green shouldBe 0
        }

        test("removing Spreading Seas restores the land's original Forest subtype and mana ability") {
            val game = scenario()
                .withPlayers("Aura controller", "Land controller")
                .withCardOnBattlefield(2, "Forest")
                .withCardAttachedTo(1, "Spreading Seas", "Forest")
                .withCardInHand(2, "Boomerang")
                .withLandsOnBattlefield(2, "Island", 2)
                .build()

            val forest = game.findPermanent("Forest")!!
            val aura = game.findPermanent("Spreading Seas")!!
            game.state.projectedState.hasSubtype(forest, "Forest") shouldBe false
            game.state.projectedState.hasSubtype(forest, "Island") shouldBe true

            game.passPriority().error shouldBe null
            game.castSpell(2, "Boomerang", aura).error shouldBe null
            game.resolveStack()

            game.isInHand(1, "Spreading Seas") shouldBe true
            game.state.projectedState.hasSubtype(forest, "Forest") shouldBe true
            game.state.projectedState.hasSubtype(forest, "Island") shouldBe false

            val beforeIllegalBlue = game.state
            game.execute(ActivateAbility(game.player2Id, forest, AbilityId.intrinsicMana('U'))).error shouldNotBe null
            game.state shouldBe beforeIllegalBlue
            game.execute(ActivateAbility(game.player2Id, forest, AbilityId.intrinsicMana('G'))).error shouldBe null

            val pool = game.state.getEntity(game.player2Id)!!.get<ManaPoolComponent>()!!
            pool.green shouldBe 1
            pool.blue shouldBe 0
        }
    }
}
