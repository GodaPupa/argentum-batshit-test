package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.RoomComponent
import com.wingedsheep.engine.state.components.identity.RoomFaceId
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.AddColorlessManaEffect
import io.kotest.matchers.shouldBe

/**
 * Independent executable R9 internal-mana provenance witnesses.
 *
 * No mocks of the mana solver or executor: every spell goes through the real
 * action processor. The two synthetic lands have legal printed mana-ability
 * shapes chosen to isolate otherwise ambiguous selection paths.
 *
 * These are intentionally strict RED-before-repair expectations. The GREEN
 * ad362ce4 isolated repair must NOT be generalized based on these witnesses.
 */
class ManualPrecisionR9ProvenanceHardeningWitnessesTest : ScenarioTestBase() {

    private val mixedFunding = card("R9 Mixed Funding Land") {
        typeLine = "Land"
        // A costlier colorless ability first makes a missing-color match unsafe.
        activatedAbility {
            cost = Costs.Composite(Costs.Tap, Costs.PayLife(2))
            effect = AddColorlessManaEffect(1)
            manaAbility = true
            timing = TimingRule.ManaAbility
        }
        // Paying another ability's generic {1} can use this free red mana.
        activatedAbility {
            cost = Costs.Tap
            effect = Effects.AddMana(Color.RED, 1)
            manaAbility = true
            timing = TimingRule.ManaAbility
        }
    }

    private val nestedFunding = card("R9 Nested Funding Land") {
        typeLine = "Land"
        // This cannot fund Hidden Grotto's {1} without ANOTHER mana to pay
        // for its own {1} activation cost.
        activatedAbility {
            cost = Costs.Composite(Costs.Mana("{1}"), Costs.Tap)
            effect = Effects.AddMana(Color.RED, 1)
            manaAbility = true
            timing = TimingRule.ManaAbility
        }
    }

    private val blueBear = card("R9 Blue Provenance Bear") {
        manaCost = "{U}"
        typeLine = "Creature — Bear"
        power = 2
        toughness = 2
    }

    init {
        cardRegistry.register(listOf(mixedFunding, nestedFunding, blueBear))

        test("multiple printed abilities: internal generic cost must not charge the unrelated colorless ability") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, "Hidden Grotto")
                .withCardOnBattlefield(1, mixedFunding.name)
                .withCardInHand(1, "Keen-Eyed Curator")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            // Forest + Grotto make {G}{G}; this land supplies free {R}
            // for Grotto's {1}. Its alternative colorless mode costs 2 life.
            game.castSpell(1, "Keen-Eyed Curator").error shouldBe null
            game.getLifeTotal(1) shouldBe 20
            game.state.getEntity(game.findPermanent(mixedFunding.name)!!)
                ?.has<TappedComponent>() shouldBe true
        }

        test("multiple printed abilities: at one life, free internal funding remains legal") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, "Hidden Grotto")
                .withCardOnBattlefield(1, mixedFunding.name)
                .withCardInHand(1, "Keen-Eyed Curator")
                .withLifeTotal(1, 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            // No life payment is necessary: only the unchosen {C} mode pays life.
            game.castSpell(1, "Keen-Eyed Curator").error shouldBe null
            game.getLifeTotal(1) shouldBe 1
        }

        test("granted ability: Greenhouse's free blue mana overrides Starting Town's paid printed blue mode") {
            val driver = GameTestDriver()
            driver.registerCards(TestCards.all)
            driver.registerCard(blueBear)
            driver.initMirrorMatch(Deck.of("Forest" to 40))
            val p1 = driver.activePlayer!!
            driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

            val town = driver.putLandOnBattlefield(p1, "Starting Town")
            val room = driver.putCardInHand(p1, "Greenhouse // Rickety Gazebo")
            driver.giveMana(p1, Color.GREEN, 3)
            driver.submitSuccess(CastSpell(p1, room, faceIndex = 0))
            driver.bothPass()
            // Verify the real Room face is unlocked; otherwise this is NOT a
            // valid grant-vs-printed provenance witness.
            driver.state.getEntity(room)?.get<RoomComponent>()?.unlocked shouldBe
                setOf(RoomFaceId("Greenhouse"))

            val before = driver.getLifeTotal(p1)
            val spell = driver.putCardInHand(p1, blueBear.name)
            driver.submitSuccess(CastSpell(p1, spell))
            driver.isTapped(town) shouldBe true
            // Greenhouse grants {T}: Add any color with no life cost. Solver
            // sees that free mode; executor must not charge Starting Town's
            // different printed {T}, Pay 1 life mode for the same blue mana.
            driver.getLifeTotal(p1) shouldBe before
        }

        test("nested activation cost: source requiring {1} cannot self-fund another internal {1}") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, "Hidden Grotto")
                .withCardOnBattlefield(1, nestedFunding.name)
                .withCardInHand(1, "Keen-Eyed Curator")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            // Grotto's green mode costs {1}. The third land can add {R},
            // but ONLY after paying an additional {1}; Forest is already
            // committed to the spell. There is no fourth source to pay it.
            ManaSolver(cardRegistry)
                .solve(game.state, game.player1Id, ManaCost.parse("{G}{G}")) shouldBe null
            val before = game.state
            (game.castSpell(1, "Keen-Eyed Curator").error != null) shouldBe true
            game.state shouldBe before
        }
    }
}
