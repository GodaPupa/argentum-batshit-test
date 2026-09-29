package com.wingedsheep.gym.manual

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.gym.contract.ObservationBuilder
import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Overlay identity composition only; action competence remains a separate gate. */
class ManualPhaseTwoRaceOverlayRouterTest : FunSpec({
    val commanders = listOf(
        "Animar, Soul of Elements", "Kraum, Ludevic's Opus", "Tymna the Weaver",
        "Rograkh, Son of Rohgahh", "Silas Renn, Seeker Adept", "Kinnan, Bonder Prodigy",
    )
    val registry = CardRegistry().apply {
        register(TestCards.all)
        commanders.forEach { name ->
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
            "EXCLUDED_RACE_ROUTER_$seat",
            Deck.of("Forest" to (100 - names.size)),
            commanderCardNames = names,
        ) },
        format = Format.Commander(), startingPlayerIndex = 0,
        skipMulligans = false, useHandSmoother = false, seed = 0x4d52524f55544552L,
    ))
    val actor = initialized.playerIds.first()
    val view = ObservationBuilder(registry).build(
        initialized.state, actor, emptyList(), revealAll = false,
    ).observation as TrainingObservation

    test("Race binds each public opponent seat to its exact accepted overlay") {
        ManualPhaseTwoRaceOverlayRouter.bind(ManualPhaseTwoPilotRole.RACE, view) shouldBe mapOf(
            initialized.playerIds[1] to "R3-BF",
            initialized.playerIds[2] to "R3-RS",
            initialized.playerIds[3] to "R3-KB",
        )
    }

    test("Cruise cannot inherit Race overlays") {
        shouldThrow<IllegalArgumentException> {
            ManualPhaseTwoRaceOverlayRouter.bind(ManualPhaseTwoPilotRole.CRUISE, view)
        }
    }

    test("Sport cannot inherit Race overlays") {
        shouldThrow<IllegalArgumentException> {
            ManualPhaseTwoRaceOverlayRouter.bind(ManualPhaseTwoPilotRole.SPORT, view)
        }
    }
})
