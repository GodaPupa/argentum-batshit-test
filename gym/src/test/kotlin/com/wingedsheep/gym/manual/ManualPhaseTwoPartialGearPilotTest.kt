package com.wingedsheep.gym.manual

import com.wingedsheep.gym.contract.ActionParams
import com.wingedsheep.gym.contract.EntityFeatures
import com.wingedsheep.gym.contract.LegalActionView
import com.wingedsheep.gym.contract.ManaPoolView
import com.wingedsheep.gym.contract.PlayerView
import com.wingedsheep.gym.contract.StackItemKind
import com.wingedsheep.gym.contract.StackItemView
import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.gym.contract.ZoneView
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Excluded four-seat masked observation fixtures. No 100-card deck initialization,
 * real gameplay, new seeds, true gear competence or performance inference.
 */
class ManualPhaseTwoPartialGearPilotTest : FunSpec({
    val actor = EntityId.of("manual")
    val farm = EntityId.of("farm")
    val rogsi = EntityId.of("rogsi")
    val kinnan = EntityId.of("kinnan")
    val seats = listOf(actor, farm, rogsi, kinnan)
    val land = EntityId.of("forest")
    val animar = EntityId.of("animar")
    val offer = EntityId.of("offer")
    val pact = EntityId.of("pact")
    val other = EntityId.of("other-spell")
    val threat = EntityId.of("threat")
    val helper = EntityId.of("helper-counter")
    val protection = EntityId.of("protection")

    fun card(
        id: EntityId, name: String, owner: EntityId, zone: Zone, type: String,
    ) = EntityFeatures(
        entityId = id, cardDefinitionId = name, name = name, zone = zone,
        ownerId = owner, controllerId = null, types = setOf(type),
        subtypes = emptySet(), colors = emptySet(), keywords = emptySet(),
        manaCost = "", manaValue = 0, power = null, toughness = null,
    )

    val forest = card(land, "Forest", actor, Zone.HAND, "LAND")
    val offerCard = card(offer, "An Offer You Can't Refuse", actor, Zone.HAND, "INSTANT")
    val pactCard = card(pact, "Pact of Negation", actor, Zone.HAND, "INSTANT")
    val pass = LegalActionView(8, "PassPriority", "Pass", true)
    val play = LegalActionView(7, "PlayLand", "Play Forest", true, land)
    val castAnimar = LegalActionView(9, "CastSpell", "Cast Animar", true, animar)
    val otherCast = LegalActionView(10, "CastSpell", "Cast unknown", true, other)
    val offerCast = LegalActionView(12, "CastSpell", "Counter protection", true,
        offer, listOf(protection), minTargets = 1, maxTargets = 1)
    val pactCast = LegalActionView(13, "CastSpell", "Pact against protection", true,
        pact, listOf(protection), minTargets = 1, maxTargets = 1)

    fun observation(
        hand: List<EntityFeatures> = listOf(forest),
        actions: List<LegalActionView> = listOf(pass, play),
        stack: List<StackItemView> = emptyList(),
        unknownOpponent: Boolean = false,
        ownHandHidden: Boolean = false,
        priority: EntityId = actor,
    ): TrainingObservation {
        val opponentCommands = listOf(
            farm to listOf("Kraum, Ludevic's Opus", "Tymna the Weaver"),
            rogsi to listOf("Rograkh, Son of Rohgahh", "Silas Renn, Seeker Adept"),
            kinnan to listOf(if (unknownOpponent) "Unknown Commander" else "Kinnan, Bonder Prodigy"),
        )
        val zones = listOf(
            ZoneView(actor, Zone.HAND, ownHandHidden, hand.size,
                if (ownHandHidden) emptyList() else hand),
            ZoneView(actor, Zone.COMMAND, false, 1,
                listOf(card(animar, "Animar, Soul of Elements", actor, Zone.COMMAND, "CREATURE"))),
        ) + opponentCommands.map { (id, names) ->
            ZoneView(id, Zone.COMMAND, false, names.size, names.mapIndexed { i, name ->
                card(EntityId.of(id.value + "-commander-" + i), name, id, Zone.COMMAND, "CREATURE")
            })
        }
        return TrainingObservation(
            schemaHash = "excluded-manual-gear-composition",
            perspectivePlayerId = actor, agentToAct = actor, turnNumber = 1,
            phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN,
            activePlayerId = actor, priorityPlayerId = priority,
            players = seats.map { id ->
                PlayerView(id, id.value, 40, if (id == actor) hand.size else 7,
                    92, 0, 0, ManaPoolView(),
                    id == actor, id == actor, id == actor, false)
            },
            zones = zones, stack = stack, pendingDecision = null,
            legalActions = actions, terminated = false, winnerId = null,
            stateDigest = "excluded-manual-gear-composition",
        )
    }

    val manual = listOf(
        ManualPhaseTwoPilotRole.CRUISE,
        ManualPhaseTwoPilotRole.SPORT,
        ManualPhaseTwoPilotRole.RACE,
    )
    for (role in manual) {
        test("$role executes unique physical land through the tested opening seam") {
            ManualPhaseTwoPartialGearPilot(role).choose(
                observation(), PhaseTwoPilotPrompt.Engine,
            ) shouldBe PhaseTwoPilotChoice.Action(7)
        }
        test("$role chooses physical Animar only when it is the sole strategic action") {
            ManualPhaseTwoPartialGearPilot(role).choose(
                observation(hand = emptyList(), actions = listOf(pass, castAnimar)),
                PhaseTwoPilotPrompt.Engine,
            ) shouldBe PhaseTwoPilotChoice.Action(9)
        }
        test("$role fails closed on competing land and commander options") {
            shouldThrow<IllegalArgumentException> {
                ManualPhaseTwoPartialGearPilot(role).choose(
                    observation(actions = listOf(pass, play, castAnimar)),
                    PhaseTwoPilotPrompt.Engine,
                )
            }
        }
        test("$role refuses to impersonate full mulligan or bottom policy") {
            val pilot = ManualPhaseTwoPartialGearPilot(role)
            shouldThrow<IllegalArgumentException> {
                pilot.choose(observation(), PhaseTwoPilotPrompt.Mulligan(true))
            }
            shouldThrow<IllegalArgumentException> {
                pilot.choose(observation(), PhaseTwoPilotPrompt.Bottom(1))
            }
        }
        test("$role refuses an unrelated cast instead of falling back") {
            shouldThrow<IllegalArgumentException> {
                ManualPhaseTwoPartialGearPilot(role).choose(
                    observation(actions = listOf(pass, otherCast)), PhaseTwoPilotPrompt.Engine,
                )
            }
        }
        test("$role refuses a hidden own hand") {
            shouldThrow<IllegalArgumentException> {
                ManualPhaseTwoPartialGearPilot(role).choose(
                    observation(ownHandHidden = true), PhaseTwoPilotPrompt.Engine,
                )
            }
        }
    }

    fun protectionStack() = listOf(
        StackItemView(threat, kinnan, "Kinnan, Bonder Prodigy", StackItemKind.SPELL),
        StackItemView(helper, farm, "Counterspell", StackItemKind.SPELL,
            targets = listOf(threat)),
        StackItemView(protection, kinnan, "Fierce Guardianship", StackItemKind.SPELL,
            targets = listOf(helper)),
    )
    for (role in listOf(ManualPhaseTwoPilotRole.SPORT, ManualPhaseTwoPilotRole.RACE)) {
        test("$role composes reviewed helper-first priority conservation") {
            val pilot = ManualPhaseTwoPartialGearPilot(role)
            val view = observation(
                hand = listOf(offerCard, pactCard),
                actions = listOf(pass, offerCast, pactCast),
                stack = protectionStack().dropLast(1),
            )
            pilot.choose(view, PhaseTwoPilotPrompt.Engine) shouldBe
                PhaseTwoPilotChoice.Action(8)
        }
        test("$role targets real protection using reviewed priority policy") {
            val pilot = ManualPhaseTwoPartialGearPilot(role)
            val view = observation(
                hand = listOf(offerCard, pactCard),
                actions = listOf(pass, offerCast, pactCast),
                stack = protectionStack(),
            )
            pilot.choose(view, PhaseTwoPilotPrompt.Engine) shouldBe
                PhaseTwoPilotChoice.Action(12, ActionParams(targets = listOf(protection)))
        }
    }
    test("Cruise cannot silently inherit Sport/Race R3-P priority seam") {
        shouldThrow<IllegalArgumentException> {
            ManualPhaseTwoPartialGearPilot(ManualPhaseTwoPilotRole.CRUISE).choose(
                observation(hand = listOf(offerCard,pactCard),
                    actions = listOf(pass,offerCast,pactCast), stack = protectionStack()),
                PhaseTwoPilotPrompt.Engine,
            )
        }
    }
    test("Race fails closed on unrecognized public opponent commander identity") {
        shouldThrow<IllegalArgumentException> {
            ManualPhaseTwoPartialGearPilot(ManualPhaseTwoPilotRole.RACE).choose(
                observation(unknownOpponent = true), PhaseTwoPilotPrompt.Engine,
            )
        }
    }
    test("Cruise and Sport never require Race-only commander identity overlay") {
        for (role in listOf(ManualPhaseTwoPilotRole.CRUISE,ManualPhaseTwoPilotRole.SPORT)) {
            ManualPhaseTwoPartialGearPilot(role).choose(
                observation(unknownOpponent = true), PhaseTwoPilotPrompt.Engine,
            ) shouldBe PhaseTwoPilotChoice.Action(7)
        }
    }
    test("the opponent pilot cannot be mislabeled as Manual gear") {
        shouldThrow<IllegalArgumentException> {
            ManualPhaseTwoPartialGearPilot(ManualPhaseTwoPilotRole.OPPONENT)
        }
    }
    test("each gear fails closed when a different actor has priority") {
        for (role in manual) {
            shouldThrow<IllegalArgumentException> {
                ManualPhaseTwoPartialGearPilot(role).choose(
                    observation(priority = farm), PhaseTwoPilotPrompt.Engine,
                )
            }
        }
    }
})
