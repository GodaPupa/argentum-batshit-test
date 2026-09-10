package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.serialization.CardExporter
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Deterministic readiness probes for the general strategic decisions Grixis Affinity requires. */
class GrixisAffinityAgentDecisionTest : ScenarioTestBase() {
    private val profile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
    private fun ai(game: TestGame) = AIPlayer.create(cardRegistry, game.player1Id, profile)
    private fun name(game: TestGame, id: EntityId) = game.state.getEntity(id)?.get<CardComponent>()?.name
    private fun seeded() = scenario().withPlayers().withRngSeed(0xAFF1_9177L)

    init {
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

        test("cheap Refurbished Familiar is deployed while its discard trigger has value") {
            val game = seeded().withCardInHand(1, "Refurbished Familiar").withLandsOnBattlefield(1, "Swamp", 1)
                .withCardOnBattlefield(1, "Vault of Whispers").withCardOnBattlefield(1, "Great Furnace")
                .withCardOnBattlefield(1, "Bonesplitter").withCardInHand(2, "Craw Wurm").build()
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Refurbished Familiar"
        }

        test("Nihil Spellbomb is fired at a stocked opposing graveyard") {
            val game = seeded().withCardOnBattlefield(1, "Nihil Spellbomb")
                .withCardInGraveyard(2, "Kessig Flamebreather").withCardInGraveyard(2, "Lava Dart")
                .withCardInGraveyard(2, "Faithless Looting").withCardInGraveyard(2, "Unearth")
                .withCardInGraveyard(2, "Grizzly Bears").withCardInGraveyard(2, "Hill Giant")
                .withCardInGraveyard(2, "Craw Wurm").build()
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Nihil Spellbomb"
            action.targets.single().shouldBeInstanceOf<ChosenTarget.Player>().playerId shouldBe game.player2Id
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
            listOf(
                "Refurbished Familiar", "Utrom Monitor", "Galvanic Blast", "Reckoner's Bargain",
                "Ichor Wellspring", "Nihil Spellbomb",
            ).forEach { name -> println("AFFINITY_SNAPSHOT_BEGIN:$name\n${CardExporter.exportToJson(cardRegistry.requireCard(name))}\nAFFINITY_SNAPSHOT_END:$name") }
        }
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
