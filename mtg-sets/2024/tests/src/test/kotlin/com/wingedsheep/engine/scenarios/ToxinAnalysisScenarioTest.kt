package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.matchers.shouldBe

/**
 * Toxin Analysis: its temporary keywords and Clue are separate resources. These deterministic
 * real-engine fixtures qualify the comparison package for Ferocity Recycling; they are not
 * randomized matchup outcomes. Darksteel Myr is an indestructible rules fixture, not a proposed
 * Pauper deck inclusion.
 */
class ToxinAnalysisScenarioTest : ScenarioTestBase() {

    private val clueAbilityId = PredefinedTokens.Clue.activatedAbilities.single().id

    private fun resolve(game: TestGame) {
        game.resolveStack().forEach { it.error shouldBe null }
        game.state.pendingDecision shouldBe null
        game.state.stack.isEmpty() shouldBe true
    }

    private fun activateShaman(game: TestGame, player: Int, shaman: EntityId, fodder: EntityId) {
        val playerId = if (player == 1) game.player1Id else game.player2Id
        val abilityId = cardRegistry.requireCard("Krark-Clan Shaman").activatedAbilities.single().id
        game.execute(
            ActivateAbility(
                playerId = playerId,
                sourceId = shaman,
                abilityId = abilityId,
                costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(fodder))
            )
        ).error shouldBe null
    }

    private fun damage(game: TestGame, permanent: EntityId): Int =
        game.state.getEntity(permanent)?.get<DamageComponent>()?.amount ?: 0

    init {
        test("keywords expire at cleanup while the caster retains the Clue") {
            val game = scenario().withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Toxin Analysis")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInLibrary(1, "Swamp").withCardInLibrary(2, "Forest")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val bears = game.findPermanent("Grizzly Bears")!!

            game.castSpell(1, "Toxin Analysis", bears).error shouldBe null
            resolve(game)
            game.state.projectedState.hasKeyword(bears, Keyword.DEATHTOUCH) shouldBe true
            game.state.projectedState.hasKeyword(bears, Keyword.LIFELINK) shouldBe true
            game.state.projectedState.getPower(bears) shouldBe 2
            game.state.projectedState.getToughness(bears) shouldBe 2
            val clue = game.findPermanents("Clue").single()
            game.state.projectedState.getController(clue) shouldBe game.player1Id
            game.state.projectedState.hasType(clue, "ARTIFACT") shouldBe true

            val turn = game.state.turnNumber
            game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
            game.state.turnNumber shouldBe turn + 1
            game.state.projectedState.hasKeyword(bears, Keyword.DEATHTOUCH) shouldBe false
            game.state.projectedState.hasKeyword(bears, Keyword.LIFELINK) shouldBe false
            game.findPermanents("Clue") shouldBe listOf(clue)
        }

        test("the Clue needs two mana and its own sacrifice before drawing one card") {
            val game = scenario().withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Toxin Analysis")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Swamp", 3)
                .withCardInLibrary(1, "Forest")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Toxin Analysis", game.findPermanent("Grizzly Bears")!!).error shouldBe null
            resolve(game)
            val clue = game.findPermanents("Clue").single()
            game.state.getHand(game.player1Id).size shouldBe 0

            game.execute(ActivateAbility(game.player1Id, clue, clueAbilityId)).error shouldBe null
            game.findPermanents("Clue").size shouldBe 0
            game.state.getHand(game.player1Id).size shouldBe 0
            game.state.stack.size shouldBe 1
            resolve(game)
            game.state.getHand(game.player1Id).size shouldBe 1
            game.isInHand(1, "Forest") shouldBe true
        }

        test("one remaining mana cannot activate the Clue and does not sacrifice it") {
            val game = scenario().withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Toxin Analysis")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInLibrary(1, "Forest")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Toxin Analysis", game.findPermanent("Grizzly Bears")!!).error shouldBe null
            resolve(game)
            val clue = game.findPermanents("Clue").single()

            val attempt = game.execute(ActivateAbility(game.player1Id, clue, clueAbilityId))
            (attempt.error != null) shouldBe true
            game.findPermanents("Clue") shouldBe listOf(clue)
            game.state.getHand(game.player1Id).size shouldBe 0
            game.state.stack.isEmpty() shouldBe true
        }

        test("removing the only target before resolution gives neither keywords nor a Clue") {
            val game = scenario().withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Toxin Analysis").withCardInHand(2, "Shock")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withLandsOnBattlefield(2, "Mountain", 1)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val bears = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, "Toxin Analysis", bears).error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpell(2, "Shock", bears).error shouldBe null
            game.state.stack.size shouldBe 2
            resolve(game)

            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            game.isInGraveyard(1, "Toxin Analysis") shouldBe true
            game.findPermanents("Clue").size shouldBe 0
            game.getLifeTotal(1) shouldBe 20
        }

        test("Shaman can consume the Clue for symmetric deathtouch damage and lifelink but no draw") {
            val game = scenario().withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Toxin Analysis")
                .withCardOnBattlefield(1, "Krark-Clan Shaman")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(2, "Craw Wurm")
                .withCardOnBattlefield(2, "Ornithopter")
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInLibrary(1, "Forest")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val shaman = game.findPermanent("Krark-Clan Shaman")!!
            val flier = game.findPermanent("Ornithopter")!!
            game.castSpell(1, "Toxin Analysis", shaman).error shouldBe null
            resolve(game)
            activateShaman(game, 1, shaman, game.findPermanents("Clue").single())

            // Sacrifice is already paid before either player receives a response window.
            game.findPermanents("Clue").size shouldBe 0
            game.isOnBattlefield("Krark-Clan Shaman") shouldBe true
            game.getLifeTotal(1) shouldBe 20
            resolve(game)
            game.isInGraveyard(1, "Krark-Clan Shaman") shouldBe true
            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            game.isInGraveyard(2, "Craw Wurm") shouldBe true
            game.findPermanent("Ornithopter") shouldBe flier
            damage(game, flier) shouldBe 0
            game.getLifeTotal(1) shouldBe 23
            game.getLifeTotal(2) shouldBe 20
            game.state.getHand(game.player1Id).size shouldBe 0
            game.findCardsInLibrary(1, "Forest").size shouldBe 1
        }

        test("queued Shaman damage retains the departed source keywords and damages indestructible creatures") {
            val game = scenario().withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Toxin Analysis")
                .withCardOnBattlefield(1, "Krark-Clan Shaman")
                .withCardOnBattlefield(1, "Bonesplitter")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withCardOnBattlefield(2, "Darksteel Myr")
                .withCardOnBattlefield(2, "Ornithopter")
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val shaman = game.findPermanent("Krark-Clan Shaman")!!
            val myr = game.findPermanent("Darksteel Myr")!!
            val flier = game.findPermanent("Ornithopter")!!
            game.castSpell(1, "Toxin Analysis", shaman).error shouldBe null
            resolve(game)
            activateShaman(game, 1, shaman, game.findPermanent("Bonesplitter")!!)
            activateShaman(game, 1, shaman, game.findPermanents("Clue").single())
            game.state.stack.size shouldBe 2
            resolve(game)

            game.isInGraveyard(1, "Krark-Clan Shaman") shouldBe true
            game.isInGraveyard(2, "Grizzly Bears") shouldBe true
            game.findPermanent("Darksteel Myr") shouldBe myr
            damage(game, myr) shouldBe 2
            damage(game, flier) shouldBe 0
            // Three damage from the first resolution, then one from the departed Shaman.
            game.getLifeTotal(1) shouldBe 24
        }

        test("prevented damage causes neither deathtouch deaths nor lifelink gain") {
            val game = scenario().withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Toxin Analysis").withCardInHand(1, "Blinding Fog")
                .withCardOnBattlefield(1, "Krark-Clan Shaman")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withLandsOnBattlefield(1, "Forest", 3)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val shaman = game.findPermanent("Krark-Clan Shaman")!!
            val bears = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, "Toxin Analysis", shaman).error shouldBe null
            resolve(game)
            game.castSpell(1, "Blinding Fog").error shouldBe null
            resolve(game)
            activateShaman(game, 1, shaman, game.findPermanents("Clue").single())
            resolve(game)

            game.findPermanent("Krark-Clan Shaman") shouldBe shaman
            game.findPermanent("Grizzly Bears") shouldBe bears
            damage(game, shaman) shouldBe 0
            damage(game, bears) shouldBe 0
            game.getLifeTotal(1) shouldBe 20
            game.getLifeTotal(2) shouldBe 20
        }

        test("zero power combat damage does not gain life even with lifelink") {
            val game = scenario().withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Toxin Analysis")
                .withCardOnBattlefield(1, "Ornithopter")
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInLibrary(1, "Swamp").withCardInLibrary(2, "Forest")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val flier = game.findPermanent("Ornithopter")!!
            game.castSpell(1, "Toxin Analysis", flier).error shouldBe null
            resolve(game)
            game.state.projectedState.hasKeyword(flier, Keyword.LIFELINK) shouldBe true
            val turn = game.state.turnNumber
            game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            game.declareAttackers(mapOf("Ornithopter" to 2)).error shouldBe null
            game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
            game.declareNoBlockers().error shouldBe null
            game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
            game.state.turnNumber shouldBe turn
            game.getLifeTotal(1) shouldBe 20
            game.getLifeTotal(2) shouldBe 20
            game.findPermanents("Clue").size shouldBe 1
        }

        test("an opposing target grants lifelink to its controller while the caster gets the Clue") {
            val game = scenario().withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Toxin Analysis")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(2, "Krark-Clan Shaman")
                .withCardOnBattlefield(2, "Bonesplitter")
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val shaman = game.findPermanent("Krark-Clan Shaman")!!
            game.castSpell(1, "Toxin Analysis", shaman).error shouldBe null
            resolve(game)
            val clue = game.findPermanents("Clue").single()
            game.passPriority().error shouldBe null
            activateShaman(game, 2, shaman, game.findPermanent("Bonesplitter")!!)
            resolve(game)

            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            game.isInGraveyard(2, "Krark-Clan Shaman") shouldBe true
            game.getLifeTotal(1) shouldBe 20
            game.getLifeTotal(2) shouldBe 22
            game.findPermanents("Clue") shouldBe listOf(clue)
            game.state.projectedState.getController(clue) shouldBe game.player1Id
        }
    }
}
