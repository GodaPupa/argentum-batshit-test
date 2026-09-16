package com.wingedsheep.engine.event

import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.TriggerSpec
import com.wingedsheep.sdk.scripting.predicates.CardPredicate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize

/** Regression coverage for color predicates on zone-change trigger filters. */
class TriggerMatcherHasColorTest : FunSpec({

    val blueCreatureWatcher = card("Blue Creature Watcher") {
        manaCost = "{2}"
        colorIdentity = ""
        typeLine = "Enchantment"
        oracleText = "Whenever a blue creature enters, draw a card."

        spell {}

        triggeredAbility {
            trigger = TriggerSpec(
                event = EventPattern.ZoneChangeEvent(
                    filter = GameObjectFilter(
                        cardPredicates = listOf(
                            CardPredicate.IsCreature,
                            CardPredicate.HasColor(Color.BLUE),
                        ),
                    ),
                    to = Zone.BATTLEFIELD,
                ),
                binding = TriggerBinding.OTHER,
            )
            effect = Effects.DrawCards(1)
        }
    }

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + blueCreatureWatcher)
        driver.initMirrorMatch(deck = Deck.of("Island" to 40))
        driver.putPermanentOnBattlefield(driver.player1, "Blue Creature Watcher")
        return driver
    }

    test("blue permanent entering satisfies HasColor") {
        val driver = createDriver()
        val entering = driver.putPermanentOnBattlefield(driver.player1, "Wind Drake")
        val event = ZoneChangeEvent(
            entityId = entering,
            entityName = "Wind Drake",
            fromZone = Zone.STACK,
            toZone = Zone.BATTLEFIELD,
            ownerId = driver.player1,
        )

        TriggerDetector(driver.cardRegistry).detectTriggers(driver.state, listOf(event))
            .filter { it.ability.trigger is EventPattern.ZoneChangeEvent } shouldHaveSize 1
    }

    test("nonblue permanent entering does not satisfy HasColor") {
        val driver = createDriver()
        val entering = driver.putPermanentOnBattlefield(driver.player1, "Grizzly Bears")
        val event = ZoneChangeEvent(
            entityId = entering,
            entityName = "Grizzly Bears",
            fromZone = Zone.STACK,
            toZone = Zone.BATTLEFIELD,
            ownerId = driver.player1,
        )

        TriggerDetector(driver.cardRegistry).detectTriggers(driver.state, listOf(event))
            .filter { it.ability.trigger is EventPattern.ZoneChangeEvent }
            .shouldBeEmpty()
    }
})
