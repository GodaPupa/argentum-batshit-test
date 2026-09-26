package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Filters
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.GameRng
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Project-local qualification of Crypt Rats through the real ActionProcessor.
 * Text: ferocity-recycling/sources/crypt-rats-canonical.json, retrieved 2026-09-26.
 * Common-printing eligibility is established independently (Visions); this fixture is not
 * corpus registration. No match outcomes or pilot-quality claims are made from these scenarios.
 */
internal val cryptRatsResearchFixture = card("Crypt Rats") {
    manaCost = "{2}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Rat"
    power = 1
    toughness = 1
    oracleText = "{X}: This creature deals X damage to each creature and each player. Spend only black mana on X."
    activatedAbility {
        cost = Costs.Mana("{X}")
        xManaRestriction = setOf(Color.BLACK)
        effect = Effects.Composite(
            Effects.ForEachInGroup(Filters.Group.allCreatures, Effects.DealXDamage(EffectTarget.Self)),
            Effects.DealXDamage(EffectTarget.PlayerRef(Player.Each))
        )
    }
}

class CryptRatsScenarioTest : ScenarioTestBase() {
    private val preventOne = card("Crypt Rats Fixture Prevention") {
        manaCost = "{0}"; typeLine = "Instant"
        oracleText = "Prevent the next 1 damage that would be dealt to target creature this turn."
        spell { val t = target("target creature", Targets.Creature); effect = Effects.PreventNextDamage(1, t) }
    }

    private fun setup() = scenario().withPlayers("Rats", "Opponent")
        .withCardInLibrary(1, "Forest").withCardInLibrary(1, "Swamp")
        .withCardInLibrary(2, "Island").withCardInLibrary(2, "Mountain")
        .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

    private fun TestGame.fixed(): TestGame = apply { state = state.copy(rng = GameRng.seeded(0xFE000002)) }

    private fun TestGame.finish() {
        resolveStack().forEach { it.error shouldBe null }
        state.pendingDecision shouldBe null
        state.stack shouldBe emptyList()
        state.gameOver shouldBe false
    }

    private fun TestGame.pulse(x: Int) = execute(ActivateAbility(
        player1Id, findPermanent(cryptRatsResearchFixture.name)!!,
        cryptRatsResearchFixture.activatedAbilities.single().id, xValue = x
    ))

    init {
        cardRegistry.register(listOf(cryptRatsResearchFixture, ferocityResearchFixture, preventOne))

        test("X black mana pays for damage to all creatures including fliers and both players") {
            val game = setup().withCardOnBattlefield(1, cryptRatsResearchFixture.name)
                .withCardOnBattlefield(1, "Grizzly Bears").withCardOnBattlefield(2, "Ornithopter")
                .withCardOnBattlefield(2, "Craw Wurm").withLandsOnBattlefield(1, "Swamp", 2).build().fixed()
            game.pulse(2).error shouldBe null
            game.finish()
            game.isInGraveyard(1, cryptRatsResearchFixture.name) shouldBe true
            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            game.isInGraveyard(2, "Ornithopter") shouldBe true
            game.isOnBattlefield("Craw Wurm") shouldBe true
            game.getLifeTotal(1) shouldBe 18
            game.getLifeTotal(2) shouldBe 18
            game.findPermanents("Swamp").all { game.state.getEntity(it)!!.has<TappedComponent>() } shouldBe true
        }

        test("generic-looking X rejects nonblack mana without partial payment") {
            val game = setup().withCardOnBattlefield(1, cryptRatsResearchFixture.name)
                .withLandsOnBattlefield(1, "Swamp", 1).withLandsOnBattlefield(1, "Forest", 1).build().fixed()
            val before = game.state
            game.pulse(2).error.shouldNotBeNull()
            game.state shouldBe before
        }

        test("X zero is legal and zero damage does not apply deathtouch") {
            val game = setup().withCardOnBattlefield(1, cryptRatsResearchFixture.name)
                .withCardAttachedTo(1, ferocityResearchFixture.name, cryptRatsResearchFixture.name)
                .withCardOnBattlefield(2, "Craw Wurm").build().fixed()
            game.pulse(0).error shouldBe null
            game.finish()
            game.isOnBattlefield(cryptRatsResearchFixture.name) shouldBe true
            game.isOnBattlefield("Craw Wurm") shouldBe true
            game.getLifeTotal(1) shouldBe 20
            game.getLifeTotal(2) shouldBe 20
        }

        test("Ferocity deathtouch sweeps large and flying creatures but grants no lifegain") {
            val game = setup().withCardOnBattlefield(1, cryptRatsResearchFixture.name)
                .withCardAttachedTo(1, ferocityResearchFixture.name, cryptRatsResearchFixture.name)
                .withCardOnBattlefield(1, "Grizzly Bears").withCardOnBattlefield(2, "Craw Wurm")
                .withCardOnBattlefield(2, "Ornithopter").withLandsOnBattlefield(1, "Swamp", 1).build().fixed()
            game.pulse(1).error shouldBe null
            game.finish()
            game.isOnBattlefield(cryptRatsResearchFixture.name) shouldBe true
            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            game.isInGraveyard(2, "Craw Wurm") shouldBe true
            game.isInGraveyard(2, "Ornithopter") shouldBe true
            game.isInGraveyard(1, ferocityResearchFixture.name) shouldBe true
            game.getLifeTotal(1) shouldBe 19
            game.getLifeTotal(2) shouldBe 19
        }

        test("Toxin comparator counts actual lifelink damage to own creatures and both players") {
            val game = setup().withCardOnBattlefield(1, cryptRatsResearchFixture.name)
                .withCardOnBattlefield(1, "Grizzly Bears").withCardOnBattlefield(2, "Craw Wurm")
                .withCardInHand(1, "Toxin Analysis").withLandsOnBattlefield(1, "Swamp", 2).build().fixed()
            val rats = game.findPermanent(cryptRatsResearchFixture.name)!!
            game.castSpell(1, "Toxin Analysis", rats).error shouldBe null
            game.finish()
            game.findPermanents("Clue").size shouldBe 1
            game.pulse(1).error shouldBe null
            game.finish()
            game.isInGraveyard(1, cryptRatsResearchFixture.name) shouldBe true
            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            game.isInGraveyard(2, "Craw Wurm") shouldBe true
            withClue("five actual damage: three creatures and two players; controller also loses one life") {
                game.getLifeTotal(1) shouldBe 24
                game.getLifeTotal(2) shouldBe 19
            }
            game.findPermanents("Clue").size shouldBe 1
        }

        test("prevention stops deathtouch and indestructible survives nonzero deathtouch damage") {
            val game = setup().withCardOnBattlefield(1, cryptRatsResearchFixture.name)
                .withCardAttachedTo(1, ferocityResearchFixture.name, cryptRatsResearchFixture.name)
                .withCardOnBattlefield(2, "Craw Wurm").withCardOnBattlefield(2, "Darksteel Myr")
                .withCardInHand(1, preventOne.name).withLandsOnBattlefield(1, "Swamp", 1).build().fixed()
            game.castSpell(1, preventOne.name, game.findPermanent("Craw Wurm")).error shouldBe null
            game.finish()
            game.pulse(1).error shouldBe null
            game.finish()
            game.isOnBattlefield("Craw Wurm") shouldBe true
            game.isOnBattlefield("Darksteel Myr") shouldBe true
            val protected = game.findPermanent("Craw Wurm")!!
            (game.state.getEntity(protected)?.get<DamageComponent>()?.amount ?: 0) shouldBe 0
            game.getLifeTotal(1) shouldBe 19
            game.getLifeTotal(2) shouldBe 19
        }

        test("returned tapped summoning-sick Rats may immediately pay its ability with fresh black mana") {
            val game = setup().withCardOnBattlefield(1, cryptRatsResearchFixture.name)
                .withCardAttachedTo(1, ferocityResearchFixture.name, cryptRatsResearchFixture.name)
                .withLandsOnBattlefield(1, "Swamp", 2).build().fixed()
            game.pulse(1).error shouldBe null
            game.finish()
            val rats = game.findPermanent(cryptRatsResearchFixture.name)!!
            game.state.getEntity(rats)!!.has<TappedComponent>() shouldBe true
            game.state.getEntity(rats)!!.has<SummoningSicknessComponent>() shouldBe true
            game.state.projectedState.hasKeyword(rats, Keyword.HASTE) shouldBe false
            game.state.projectedState.hasKeyword(rats, Keyword.DEATHTOUCH) shouldBe false
            game.pulse(1).error shouldBe null
            game.finish()
            game.isInGraveyard(1, cryptRatsResearchFixture.name) shouldBe true
            game.getLifeTotal(1) shouldBe 18
            game.getLifeTotal(2) shouldBe 18
        }

        test("two queued pulses can kill the returned Rats before a new action") {
            val game = setup().withCardOnBattlefield(1, cryptRatsResearchFixture.name)
                .withCardAttachedTo(1, ferocityResearchFixture.name, cryptRatsResearchFixture.name)
                .withLandsOnBattlefield(1, "Swamp", 2).build().fixed()
            game.pulse(1).error shouldBe null
            game.pulse(1).error shouldBe null
            game.state.stack.size shouldBe 2
            game.finish()
            game.isInGraveyard(1, cryptRatsResearchFixture.name) shouldBe true
            game.isInGraveyard(1, ferocityResearchFixture.name) shouldBe true
            game.getLifeTotal(1) shouldBe 18
            game.getLifeTotal(2) shouldBe 18
        }
    }
}
