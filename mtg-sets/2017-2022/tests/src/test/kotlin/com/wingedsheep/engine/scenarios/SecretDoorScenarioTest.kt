package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ActiveDungeonComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.afr.cards.SecretDoor
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SecretDoorScenarioTest : FunSpec({
    fun driver(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all)
        registerCards(listOf(SecretDoor, PredefinedTokens.Treasure))
        initMirrorMatch(deck = Deck.of("Island" to 40), startingLife = 20)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun choose(game: GameTestDriver, label: String) {
        val decision = game.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
        val index = decision.options.indexOf(label)
        (index >= 0) shouldBe true
        game.submitDecision(
            decision.playerId,
            OptionChosenResponse(decision.id, index),
        ).error shouldBe null
    }

    test("ventures into a chosen dungeon and follows a chosen branch") {
        val game = driver()
        val player = game.activePlayer!!
        val door = game.putCreatureOnBattlefield(player, "Secret Door")
        val abilityId = SecretDoor.activatedAbilities.single().id

        game.giveMana(player, Color.BLUE, 5)
        game.submit(ActivateAbility(player, door, abilityId)).error shouldBe null
        game.bothPass()
        choose(game, "Lost Mine of Phandelver")
        while (game.pendingDecision != null) game.autoResolveDecision()

        game.state.getEntity(player)?.get<ActiveDungeonComponent>() shouldBe
            ActiveDungeonComponent("lost-mine-of-phandelver", "cave-entrance")

        game.giveMana(player, Color.BLUE, 5)
        game.submit(ActivateAbility(player, door, abilityId)).error shouldBe null
        game.bothPass()
        choose(game, "Mine Tunnels")

        game.state.getEntity(player)?.get<ActiveDungeonComponent>() shouldBe
            ActiveDungeonComponent("lost-mine-of-phandelver", "mine-tunnels")
        game.state.getBattlefield().any {
            game.state.getEntity(it)?.get<CardComponent>()?.name == "Treasure"
        } shouldBe true
    }
})
