package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.engine.view.LegalActionInfo
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.player.RestrictedManaEntry
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.GameRng
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.ManaRestriction
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Legal X menus must honor the same color/spending restrictions as actual payment.
 * These are generic mechanic fixtures, not Pauper card implementations or game samples.
 * Current rules archive: ferocity-source-cache/rules-20260925.txt, retrieved 2026-09-26.
 * The controller chooses X, each printed X is paid, and all payment restrictions apply.
 */
class RestrictedActivatedXScenarioTest : ScenarioTestBase() {
    private fun fixture(name: String, mana: String = "{X}", colors: Set<Color> = setOf(Color.BLACK),
                        tap: Boolean = false, minimum: Int = 0) = card(name) {
        manaCost = "{0}"
        typeLine = "Artifact Creature — Construct"
        power = 1
        toughness = 3
        activatedAbility {
            cost = if (tap) Costs.Composite(Costs.Mana(mana), Costs.Tap) else Costs.Mana(mana)
            xManaRestriction = colors
            minimumXValue = minimum
            effect = Effects.GainLife(DynamicAmount.XValue)
            description = "Restricted X qualification"
        }
        if (tap) activatedAbility {
            cost = Costs.Tap
            effect = Effects.AddMana(Color.BLACK)
            manaAbility = true
            timing = TimingRule.ManaAbility
        }
    }

    private val black = fixture("Restricted X Black Fixture")
    private val twice = fixture("Restricted X Double Fixture", mana = "{X}{X}")
    private val mixed = fixture("Restricted X Two Colors Fixture", colors = setOf(Color.BLACK, Color.WHITE))
    private val tapping = fixture("Restricted X Tap Fixture", tap = true)
    private val positive = fixture("Restricted X Positive Fixture", minimum = 1)
    private val unrestricted = fixture("Unrestricted X Control Fixture", colors = emptySet())

    private fun setup(name: String = black.name) = scenario().withPlayers("Actor", "Opponent")
        .withCardOnBattlefield(1, name)
        .withCardInLibrary(1, "Forest").withCardInLibrary(1, "Swamp")
        .withCardInLibrary(2, "Island").withCardInLibrary(2, "Mountain")
        .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

    private fun TestGame.fixed() = apply { state = state.copy(rng = GameRng.seeded(0xFE000006)) }

    private fun TestGame.pool(value: ManaPoolComponent) {
        state = state.updateEntity(player1Id) { it.with(value) }
    }

    private fun TestGame.menu(name: String = black.name): LegalActionInfo {
        val source = findPermanent(name)!!
        return getLegalActions(1).single {
            val action = it.action as? ActivateAbility
            action?.sourceId == source && it.hasXCost
        }
    }

    private fun TestGame.activate(x: Int, name: String = black.name) = execute(
        (menu(name).action as ActivateAbility).copy(xValue = x)
    )

    init {
        cardRegistry.register(listOf(black, twice, mixed, tapping, positive, unrestricted))

        test("black-only X excludes red and colorless floating mana") {
            val game = setup().build().fixed()
            game.pool(ManaPoolComponent(black = 2, red = 3, colorless = 4))
            game.menu().maxAffordableX shouldBe 2
        }

        test("black-only X counts untapped Swamps and payment agrees with the menu") {
            val game = setup().withLandsOnBattlefield(1, "Swamp", 2)
                .withLandsOnBattlefield(1, "Mountain", 3).build().fixed()
            game.menu().maxAffordableX shouldBe 2
            game.activate(2).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.getLifeTotal(1) shouldBe 22
        }

        test("floating black and untapped black sources combine without other colors") {
            val game = setup().withLandsOnBattlefield(1, "Swamp", 2).build().fixed()
            game.pool(ManaPoolComponent(black = 1, green = 4))
            game.menu().maxAffordableX shouldBe 3
        }

        test("zero remains legal when no allowed-color mana is available") {
            val game = setup().build().fixed()
            game.pool(ManaPoolComponent(red = 4))
            game.menu().maxAffordableX shouldBe 0
            game.activate(0).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.getLifeTotal(1) shouldBe 20
        }

        test("a positive minimum is unaffordable when only disallowed mana exists") {
            val game = setup(positive.name).build().fixed()
            game.pool(ManaPoolComponent(red = 4))
            val offered = game.menu(positive.name)
            offered.maxAffordableX shouldBe 0
            offered.isAffordable shouldBe false
        }

        test("an activation above the allowed-color ceiling is rejected unchanged") {
            val game = setup().build().fixed()
            game.pool(ManaPoolComponent(black = 1, red = 5))
            val action = (game.menu().action as ActivateAbility).copy(xValue = 2)
            val before = game.state
            val result = game.execute(action)
            result.error.shouldNotBeNull()
            game.state shouldBe before
        }

        test("each X symbol requires its own allowed-color payment") {
            val game = setup(twice.name).build().fixed()
            game.pool(ManaPoolComponent(black = 5, red = 7))
            game.menu(twice.name).maxAffordableX shouldBe 2
        }

        test("the allowed color set can include more than one color") {
            val game = setup(mixed.name).build().fixed()
            game.pool(ManaPoolComponent(black = 2, white = 1, red = 4))
            game.menu(mixed.name).maxAffordableX shouldBe 3
        }

        test("a source tapped as an activation cost cannot also supply its X mana") {
            val game = setup(tapping.name).build().fixed()
            val source = game.findPermanent(tapping.name)!!
            game.state = game.state.updateEntity(source) { it.without<SummoningSicknessComponent>() }
            game.menu(tapping.name).maxAffordableX shouldBe 0
        }

        test("spells-only restricted black mana cannot fund an ability") {
            val game = setup().build().fixed()
            game.pool(ManaPoolComponent(black = 1, restrictedMana = List(3) {
                RestrictedManaEntry(Color.BLACK, ManaRestriction.CreatureSpellsOnly)
            }))
            game.menu().maxAffordableX shouldBe 1
        }

        test("eligible activation-only restricted black mana contributes to X") {
            val game = setup().build().fixed()
            game.pool(ManaPoolComponent(black = 1, restrictedMana = List(2) {
                RestrictedManaEntry(Color.BLACK, ManaRestriction.SubtypeSpellsOrAbilitiesOnly("Construct"))
            }))
            game.menu().maxAffordableX shouldBe 3
        }

        test("Treasure may be explicitly converted to black before paying X") {
            val game = setup().withCardOnBattlefield(1, "Treasure").build().fixed()
            game.menu().maxAffordableX shouldBe 1
            val treasure = game.findPermanent("Treasure")!!
            val mana = game.getLegalActions(1).first {
                (it.action as? ActivateAbility)?.sourceId == treasure && it.isManaAbility
            }.action as ActivateAbility
            game.execute(mana.copy(manaColorChoice = Color.BLACK)).error shouldBe null
            game.menu().maxAffordableX shouldBe 1
            game.activate(1).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.getLifeTotal(1) shouldBe 21
        }

        test("unrestricted X retains ordinary all-mana affordability") {
            val game = setup(unrestricted.name).build().fixed()
            game.pool(ManaPoolComponent(black = 2, red = 3, colorless = 4))
            game.menu(unrestricted.name).maxAffordableX shouldBe 9
        }

        test("Treasure does not let disallowed floating mana pay restricted X") {
            val game = setup().withCardOnBattlefield(1, "Treasure").build().fixed()
            game.pool(ManaPoolComponent(red = 5))
            game.menu().maxAffordableX shouldBe 1
        }

        test("Treasure and a black source each contribute once to restricted X") {
            val game = setup().withCardOnBattlefield(1, "Treasure")
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withLandsOnBattlefield(1, "Mountain", 3).build().fixed()
            game.menu().maxAffordableX shouldBe 2
        }

        test("an excluded Treasure cannot fund X through the explicit-mana fallback") {
            val game = setup().withCardOnBattlefield(1, "Treasure").build().fixed()
            val treasure = game.findPermanent("Treasure")!!
            ManaSolver(cardRegistry).canPay(
                game.state, game.player1Id, ManaCost.parse("{X}"), xValue = 1,
                excludeSources = setOf(treasure), xManaRestriction = setOf(Color.BLACK)
            ) shouldBe false
        }

        test("activation-only restricted black mana is actually spent by the activation") {
            val game = setup().build().fixed()
            game.pool(ManaPoolComponent(black = 1, red = 2, restrictedMana = List(2) {
                RestrictedManaEntry(Color.BLACK, ManaRestriction.SubtypeSpellsOrAbilitiesOnly("Construct"))
            }))
            game.menu().maxAffordableX shouldBe 3
            game.activate(3).error shouldBe null
            val paid = game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!
            paid.black shouldBe 0
            paid.red shouldBe 2
            paid.restrictedMana shouldBe emptyList()
            game.resolveStack().forEach { it.error shouldBe null }
            game.getLifeTotal(1) shouldBe 23
        }

        test("ineligible restricted mana cannot pay an activation by leaving X unpaid") {
            val game = setup().build().fixed()
            game.pool(ManaPoolComponent(black = 1, restrictedMana = List(2) {
                RestrictedManaEntry(Color.BLACK, ManaRestriction.CreatureSpellsOnly)
            }))
            val action = (game.menu().action as ActivateAbility).copy(xValue = 3)
            val before = game.state
            game.execute(action).error.shouldNotBeNull()
            game.state shouldBe before
        }
    }
}
