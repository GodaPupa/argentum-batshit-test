package com.wingedsheep.gym.manual

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.gym.contract.ObservationBuilder
import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Identity mapping only; no four-seat pilot or gameplay competence. */
class ManualPhaseTwoPublicOpponentAxesTest : FunSpec({
    test("all seven frozen public commander axes resolve exactly") {
        val cases = listOf(
            listOf("Hashaton, Scarab's Fist") to ManualOpponentAxis.HASHATON,
            listOf("Magda, Brazen Outlaw") to ManualOpponentAxis.MAGDA,
            listOf("Kraum, Ludevic's Opus", "Tymna the Weaver") to ManualOpponentAxis.BLUE_FARM,
            listOf("Shorikai, Genesis Engine") to ManualOpponentAxis.SHORIKAI,
            listOf("Sisay, Weatherlight Captain") to ManualOpponentAxis.SISAY,
            listOf("Rograkh, Son of Rohgahh", "Silas Renn, Seeker Adept") to ManualOpponentAxis.ROGSI,
            listOf("Kinnan, Bonder Prodigy") to ManualOpponentAxis.KINNAN,
        )
        cases.forEach { (names, axis) ->
            ManualPhaseTwoPublicOpponentAxes.classify(names) shouldBe axis
            ManualPhaseTwoPublicOpponentAxes.classify(names.reversed()) shouldBe axis
        }
    }
    test("incomplete or duplicated partner identity has no overlay") {
        ManualPhaseTwoPublicOpponentAxes.classify(listOf("Tymna the Weaver")) shouldBe null
        ManualPhaseTwoPublicOpponentAxes.classify(listOf(
            "Kraum, Ludevic's Opus", "Kraum, Ludevic's Opus")) shouldBe null
    }
    test("four-seat masked observation identifies exact public partner and solo axes") {
        val commanderNames = listOf(
            "Animar, Soul of Elements", "Kraum, Ludevic's Opus", "Tymna the Weaver",
            "Rograkh, Son of Rohgahh", "Silas Renn, Seeker Adept",
            "Kinnan, Bonder Prodigy",
        )
        val registry = CardRegistry().apply {
            register(TestCards.all)
            commanderNames.forEach { name ->
                register(card(name) {
                    manaCost = "{0}"
                    typeLine = "Legendary Creature — Human"
                    power = 1
                    toughness = 1
                })
            }
        }
        val commands = listOf(
            listOf("Animar, Soul of Elements"),
            listOf("Kraum, Ludevic's Opus", "Tymna the Weaver"),
            listOf("Rograkh, Son of Rohgahh", "Silas Renn, Seeker Adept"),
            listOf("Kinnan, Bonder Prodigy"),
        )
        val initialized = GameInitializer(registry).initializeGame(GameConfig(
            players = commands.mapIndexed { seat, names -> PlayerConfig(
                "EXCLUDED_AXIS_SEAT_$seat",
                Deck.of("Forest" to (100 - names.size)),
                commanderCardNames = names,
            ) },
            format = Format.Commander(), startingPlayerIndex = 0,
            skipMulligans = false, useHandSmoother = false, seed = 0x4d41584953L,
        ))
        val actor = initialized.playerIds.first()
        val view = ObservationBuilder(registry).build(
            initialized.state, actor, emptyList(), revealAll = false,
        ).observation as TrainingObservation
        view.zones.filter { it.ownerId != actor && it.zoneType == Zone.HAND }
            .all { it.hidden && it.cards.isEmpty() } shouldBe true
        ManualPhaseTwoPublicOpponentAxes.detect(view) shouldBe mapOf(
            initialized.playerIds[1] to ManualOpponentAxis.BLUE_FARM,
            initialized.playerIds[2] to ManualOpponentAxis.ROGSI,
            initialized.playerIds[3] to ManualOpponentAxis.KINNAN,
        )
        val blueFarm = view.zones.single {
            it.ownerId == initialized.playerIds[1] && it.zoneType == Zone.COMMAND
        }
        val incomplete = view.copy(zones = view.zones.map {
            if (it === blueFarm) it.copy(size = 1, cards = it.cards.take(1)) else it
        })
        shouldThrow<IllegalArgumentException> {
            ManualPhaseTwoPublicOpponentAxes.detect(incomplete)
        }
    }
})
