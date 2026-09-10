package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.YesNoResponse
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private class DeepSacrificeDidNotCast : RuntimeException()
private class DeepSacrificeChoseWrongSpell : RuntimeException()
private class DeepSacrificeChoseWrongPermanent : RuntimeException()

/** Deterministic readiness probes for the general strategic decisions Grixis Affinity requires. */
class GrixisAffinityAgentDecisionTest : ScenarioTestBase() {
    private val profile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
    private fun ai(game: TestGame) = AIPlayer.create(cardRegistry, game.player1Id, profile)
    private fun name(game: TestGame, id: EntityId) = game.state.getEntity(id)?.get<CardComponent>()?.name
    private fun seeded() = scenario().withPlayers().withRngSeed(0xAFF1_9177L)

    init {
        cardRegistry.register(listOf(GRAVEYARD_THEFT_PROBE))

        test("untapped artifact land that enables interaction precedes a tapped bridge") {
            val game = seeded().withCardInHand(1, "Great Furnace")
                .withCardInHand(1, "Drossforge Bridge").withCardInHand(1, "Galvanic Blast")
                .withCardOnBattlefield(2, "Grizzly Bears").build()
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<PlayLand>()
            name(game, action.cardId) shouldBe "Great Furnace"
        }

        test("Bargain answers lethal burn by sacrificing productive Wellspring instead of an artifact land") {
            val game = seeded().withActivePlayer(2)
                .withLifeTotal(1, 2)
                .withCardOnBattlefield(1, "Ichor Wellspring").withCardOnBattlefield(1, "Vault of Whispers")
                .withCardOnBattlefield(1, "Great Furnace").withCardInHand(1, "Reckoner's Bargain")
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(2, "Lightning Bolt").withLandsOnBattlefield(2, "Mountain", 1)
                .withCardInLibrary(1, "Forest").withCardInLibrary(1, "Mountain").withCardInLibrary(1, "Island")
                .build()
            game.castSpellTargetingPlayer(2, "Lightning Bolt", 1).error shouldBe null
            game.execute(PassPriority(game.player2Id)).error shouldBe null
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Reckoner's Bargain"
            name(game, action.additionalCostPayment!!.sacrificedPermanents.single()) shouldBe "Ichor Wellspring"
        }

        test("metalcraft Blast removes a four-toughness threat rather than wasting damage on a smaller body") {
            val game = seeded().withCardInHand(1, "Galvanic Blast").withLandsOnBattlefield(1, "Mountain", 1)
                .withCardOnBattlefield(1, "Bonesplitter").withCardOnBattlefield(1, "Ornithopter")
                .withCardOnBattlefield(1, "Vault of Whispers")
                .withCardOnBattlefield(2, "Craw Wurm").withCardOnBattlefield(2, "Mons's Goblin Raiders")
                .build()
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Galvanic Blast"
            val target = action.targets.single().shouldBeInstanceOf<ChosenTarget.Permanent>().entityId
            name(game, target) shouldBe "Craw Wurm"
        }

        test("reduced-rate face damage is held at a high life total") {
            val game = seeded().withCardInHand(1, "Galvanic Blast")
                .withLandsOnBattlefield(1, "Mountain", 1).build()
            ai(game).chooseAction(game.state).shouldBeInstanceOf<PassPriority>()
        }

        test("full-rate face damage is used when it completes lethal") {
            val game = seeded().withLifeTotal(2, 4).withCardInHand(1, "Galvanic Blast")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withCardOnBattlefield(1, "Bonesplitter")
                .withCardOnBattlefield(1, "Ornithopter")
                .withCardOnBattlefield(1, "Vault of Whispers").build()
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            action.targets.single().shouldBeInstanceOf<ChosenTarget.Player>().playerId shouldBe game.player2Id
        }

        test("reduced-rate burn removes a creature instead of converting weakly to face") {
            val game = seeded().withCardInHand(1, "Galvanic Blast")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withCardOnBattlefield(2, "Guttersnipe").build()
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            val target = action.targets.single().shouldBeInstanceOf<ChosenTarget.Permanent>().entityId
            name(game, target) shouldBe "Guttersnipe"
        }

        test("reduced-rate face damage remains legal when visible follow-up completes lethal") {
            val game = seeded().withLifeTotal(2, 4)
                .withCardInHand(1, "Galvanic Blast").withCardInHand(1, "Galvanic Blast")
                .withLandsOnBattlefield(1, "Mountain", 1).build()
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            action.targets.single().shouldBeInstanceOf<ChosenTarget.Player>().playerId shouldBe game.player2Id
        }

        test("cheap Refurbished Familiar is deployed while its discard trigger has value") {
            val game = seeded().withCardInHand(1, "Refurbished Familiar").withLandsOnBattlefield(1, "Swamp", 1)
                .withCardOnBattlefield(1, "Vault of Whispers").withCardOnBattlefield(1, "Great Furnace")
                .withCardOnBattlefield(1, "Bonesplitter").withCardInHand(2, "Craw Wurm").build()
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Refurbished Familiar"
        }

        test("Nihil Spellbomb answers an opposing recursion line before it resolves") {
            val game = seeded().withActivePlayer(2)
                .withCardOnBattlefield(1, "Nihil Spellbomb")
                .withCardInHand(2, "Unearth").withCardInGraveyard(2, "Kessig Flamebreather")
                .withLandsOnBattlefield(2, "Swamp", 1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpellTargetingGraveyardCard(2, "Unearth", 2, "Kessig Flamebreather").error shouldBe null
            game.execute(PassPriority(game.player2Id)).error shouldBe null
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Nihil Spellbomb"
            action.targets.single().shouldBeInstanceOf<ChosenTarget.Player>().playerId shouldBe game.player2Id
        }

        test("Game 6 structure targets the opposing recursion graveyard rather than Affinity's own") {
            val game = seeded().withActivePlayer(2)
                .withCardOnBattlefield(1, "Nihil Spellbomb")
                .withCardInGraveyard(1, "Ichor Wellspring")
                .withCardInGraveyard(1, "Refurbished Familiar")
                .withCardInHand(2, "Unearth")
                .withCardInGraveyard(2, "Kessig Flamebreather")
                .withCardInGraveyard(2, "Shambling Ghast")
                .withLandsOnBattlefield(2, "Swamp", 1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()

            game.castSpellTargetingGraveyardCard(2, "Unearth", 2, "Kessig Flamebreather").error shouldBe null
            game.execute(PassPriority(game.player2Id)).error shouldBe null

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Nihil Spellbomb"
            action.targets.single().shouldBeInstanceOf<ChosenTarget.Player>().playerId shouldBe game.player2Id
        }

        test("graveyard hate correctly targets its controller when that denies opposing graveyard theft") {
            val game = seeded().withActivePlayer(2)
                .withCardOnBattlefield(1, "Nihil Spellbomb")
                .withCardInGraveyard(1, "Kessig Flamebreather")
                .withCardInHand(2, GRAVEYARD_THEFT_PROBE.name)
                .withLandsOnBattlefield(2, "Swamp", 1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpellTargetingGraveyardCard(
                2, GRAVEYARD_THEFT_PROBE.name, 1, "Kessig Flamebreather"
            ).error shouldBe null
            game.execute(PassPriority(game.player2Id)).error shouldBe null

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Nihil Spellbomb"
            action.targets.single().shouldBeInstanceOf<ChosenTarget.Player>().playerId shouldBe game.player1Id
        }

        test("an empty graveyard is not enough reason to spend a graveyard artifact") {
            val game = seeded().withCardOnBattlefield(1, "Nihil Spellbomb").build()
            ai(game).chooseAction(game.state).shouldBeInstanceOf<PassPriority>()
        }

        test("an immediate recursion target justifies activation without a draw") {
            val game = seeded().withActivePlayer(2).withCardOnBattlefield(1, "Nihil Spellbomb")
                .withCardInHand(2, "Unearth").withCardInGraveyard(2, "Kessig Flamebreather")
                .withLandsOnBattlefield(2, "Swamp", 1).build()
            game.castSpellTargetingGraveyardCard(2, "Unearth", 2, "Kessig Flamebreather").error shouldBe null
            game.execute(PassPriority(game.player2Id)).error shouldBe null
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Nihil Spellbomb"
        }

        test("graveyard activation pays black for the conditional draw when available") {
            val game = seeded().withCardOnBattlefield(1, "Nihil Spellbomb")
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(2, "Unearth").withCardInGraveyard(2, "Kessig Flamebreather")
                .withCardInLibrary(1, "Forest").build()
            val player = ai(game)
            val bomb = game.findPermanent("Nihil Spellbomb")!!
            val ability = cardRegistry.requireCard("Nihil Spellbomb").activatedAbilities.single().id
            val activation = ActivateAbility(
                game.player1Id, bomb, ability, targets = listOf(ChosenTarget.Player(game.player2Id))
            )
            game.execute(activation).error shouldBe null
            repeat(4) {
                if (game.state.pendingDecision != null) return@repeat
                val priority = game.state.priorityPlayerId ?: return@repeat
                game.execute(PassPriority(priority)).error shouldBe null
            }
            val decision = game.state.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
            val response = player.respondToDecision(game.state, decision).shouldBeInstanceOf<YesNoResponse>()
            response.choice shouldBe true
        }

        test("graveyard artifact is preserved when sacrificing it would break metalcraft") {
            val game = seeded().withCardOnBattlefield(1, "Nihil Spellbomb")
                .withCardOnBattlefield(1, "Bonesplitter").withCardOnBattlefield(1, "Vault of Whispers")
                .withCardInHand(1, "Galvanic Blast").withLandsOnBattlefield(1, "Mountain", 1).build()
            val action = ai(game).chooseAction(game.state)
            (action is ActivateAbility && name(game, action.sourceId) == "Nihil Spellbomb") shouldBe false
        }

        test("Krark-Clan Shaman cashes in Wellspring for a profitable sweep") {
            val game = seeded().withCardOnBattlefield(1, "Krark-Clan Shaman")
                .withCardOnBattlefield(1, "Ichor Wellspring")
                .withCardOnBattlefield(2, "Mons's Goblin Raiders")
                .withCardOnBattlefield(2, "Greedy Freebooter")
                .withCardOnBattlefield(2, "Voldaren Epicure").withCardInLibrary(1, "Forest").build()
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Krark-Clan Shaman"
            name(game, action.costPayment!!.sacrificedPermanents.single()) shouldBe "Ichor Wellspring"
        }

        test("a sweep that damages only its controller's creatures is held") {
            val game = seeded().withCardOnBattlefield(1, "Krark-Clan Shaman")
                .withCardOnBattlefield(1, "Vault of Whispers")
                .withCardOnBattlefield(1, "Mons's Goblin Raiders").build()
            ai(game).chooseAction(game.state).shouldBeInstanceOf<PassPriority>()
        }

        test("productive sacrifice targets are searched beyond incidental battlefield order") {
            var builder = seeded().withActivePlayer(2).withLifeTotal(1, 2)
                .withCardInHand(1, "Reckoner's Bargain")
                .withLandsOnBattlefield(1, "Swamp", 2)
            repeat(9) { builder = builder.withCardOnBattlefield(1, "Vault of Whispers") }
            val game = builder.withCardOnBattlefield(1, "Ichor Wellspring")
                .withCardInLibrary(1, "Forest").withCardInLibrary(1, "Mountain")
                .withCardInHand(2, "Lightning Bolt").withLandsOnBattlefield(2, "Mountain", 1).build()
            game.castSpellTargetingPlayer(2, "Lightning Bolt", 1).error shouldBe null
            game.execute(PassPriority(game.player2Id)).error shouldBe null
            val action = ai(game).chooseAction(game.state) as? CastSpell
                ?: throw DeepSacrificeDidNotCast()
            if (name(game, action.cardId) != "Reckoner's Bargain") {
                throw DeepSacrificeChoseWrongSpell()
            }
            if (name(game, action.additionalCostPayment!!.sacrificedPermanents.single()) != "Ichor Wellspring") {
                throw DeepSacrificeChoseWrongPermanent()
            }
        }

        test("large free affinity threat is deployed while one-mana interaction is held") {
            var builder = seeded().withCardInHand(1, "Myr Enforcer").withCardInHand(1, "Galvanic Blast")
                .withLandsOnBattlefield(1, "Mountain", 1)
            repeat(7) { builder = builder.withCardOnBattlefield(1, "Bonesplitter") }
            val game = builder.build()
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Myr Enforcer"
        }

        test("provenance-locked first-place maindeck is exactly 60 and fully resolvable") {
            val counts = grixisAffinityPasqualeMainboard().cards.groupingBy { it }.eachCount()
            counts.values.sum() shouldBe 60
            counts.entries.map { it.key to it.value }.shouldContainExactlyInAnyOrder(
                "Drossforge Bridge" to 3, "Great Furnace" to 2, "Mistvault Bridge" to 3,
                "Seat of the Synod" to 3, "Silverbluff Bridge" to 2, "Swamp" to 1,
                "Vault of Whispers" to 4, "Krark-Clan Shaman" to 2, "Myr Enforcer" to 4,
                "Refurbished Familiar" to 4, "Utrom Monitor" to 3, "Cast Down" to 3,
                "Fanatical Offering" to 2, "Galvanic Blast" to 4, "Reckoner's Bargain" to 4,
                "Thoughtcast" to 4, "Toxin Analysis" to 2, "Blood Fountain" to 3,
                "Ichor Wellspring" to 4, "Makeshift Munitions" to 1, "Nihil Spellbomb" to 2,
            )
            counts.keys.forEach { cardRegistry.requireCard(it) }
        }
    }
}

private val GRAVEYARD_THEFT_PROBE = card("Graveyard Theft Probe") {
    manaCost = "{B}"
    typeLine = "Sorcery"
    spell {
        val stolen = target("target creature card in a graveyard", Targets.CreatureCardInGraveyard)
        effect = Effects.PutOntoBattlefieldUnderYourControl(stolen)
    }
}

internal fun grixisAffinityPasqualeMainboard(): Deck = Deck.of(
    "Drossforge Bridge" to 3, "Great Furnace" to 2, "Mistvault Bridge" to 3,
    "Seat of the Synod" to 3, "Silverbluff Bridge" to 2, "Swamp" to 1,
    "Vault of Whispers" to 4, "Krark-Clan Shaman" to 2, "Myr Enforcer" to 4,
    "Refurbished Familiar" to 4, "Utrom Monitor" to 3, "Cast Down" to 3,
    "Fanatical Offering" to 2, "Galvanic Blast" to 4, "Reckoner's Bargain" to 4,
    "Thoughtcast" to 4, "Toxin Analysis" to 2, "Blood Fountain" to 3,
    "Ichor Wellspring" to 4, "Makeshift Munitions" to 1, "Nihil Spellbomb" to 2,
)
