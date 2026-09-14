package com.wingedsheep.engine.handlers.effects

import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.CreatureStats
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/** Regression coverage for the rule preventing a departed token from changing zones again. */
class TokenZoneTransitionTest : FunSpec({

    fun driver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40), skipMulligans = true)
        return driver
    }

    fun GameTestDriver.createMouseToken(player: EntityId): EntityId {
        val tokenId = EntityId.generate()
        val card = CardComponent(
            cardDefinitionId = "token:Mouse",
            name = "Mouse Token",
            manaCost = ManaCost.ZERO,
            typeLine = TypeLine.parse("Creature — Mouse"),
            baseStats = CreatureStats(1, 1),
            colors = setOf(Color.WHITE),
            ownerId = player,
        )
        replaceState(
            state
                .withEntity(
                    tokenId,
                    ComponentContainer.of(
                        card,
                        TokenComponent,
                        ControllerComponent(player),
                        SummoningSicknessComponent,
                    ),
                )
                .addToZone(ZoneKey(player, Zone.BATTLEFIELD), tokenId),
        )
        return tokenId
    }

    test("a token may leave the battlefield but cannot move from exile back to it") {
        val driver = driver()
        val player = driver.activePlayer!!
        val token = driver.createMouseToken(player)

        val exile = ZoneTransitionService.moveToZone(driver.state, token, Zone.EXILE)
        exile.actualDestination shouldBe Zone.EXILE
        exile.state.getZone(ZoneKey(player, Zone.EXILE)) shouldContainExactly listOf(token)

        val attemptedReturn = ZoneTransitionService.moveToZone(
            exile.state,
            token,
            Zone.BATTLEFIELD,
        )

        attemptedReturn.state shouldBe exile.state
        attemptedReturn.events.shouldBeEmpty()
        attemptedReturn.transitions.shouldBeEmpty()
        attemptedReturn.state.getZone(ZoneKey(player, Zone.EXILE)) shouldContainExactly listOf(token)
        attemptedReturn.state.getZone(ZoneKey(player, Zone.BATTLEFIELD)).contains(token) shouldBe false
    }
})
