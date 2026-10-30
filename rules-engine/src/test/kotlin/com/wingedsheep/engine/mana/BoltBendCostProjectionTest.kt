package com.wingedsheep.engine.mana

import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.Sublayer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.addFloatingEffect
import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.CostModification
import com.wingedsheep.sdk.scripting.CostReductionSource
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifySpellCost
import com.wingedsheep.sdk.scripting.SpellCostTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Additive projection/amount controls; the original five reproduction cases remain unchanged. */
class BoltBendCostProjectionTest : FunSpec({
    val creatures = (3..5).map { strength ->
        card("Bolt Bend Projection Fixture $strength") {
            manaCost = "{5}"
            typeLine = "Creature — Beast"
            power = strength
            toughness = 6
        }
    }
    val artifact = card("Bolt Bend Animation Fixture") {
        manaCost = "{2}"
        typeLine = "Artifact"
    }
    fun fixture(name: String, modification: CostModification) = card(name) {
        manaCost = "{8}{R}"
        typeLine = "Instant"
        staticAbility { ability = ModifySpellCost(target = SpellCostTarget.SelfCast, modification = modification) }
    }
    val highCost = fixture("Bolt Bend Exact Amount Fixture", CostModification.ReduceGenericBy(
        CostReductionSource.FixedIfControlFilter(amount = 3, filter = GameObjectFilter.Creature.powerAtLeast(4))))
    val fixedCost = fixture("Bolt Bend Unconditional Control", CostModification.ReduceGeneric(2))
    val artifactCost = fixture("Bolt Bend Artifact Filter Control", CostModification.ReduceGenericBy(
        CostReductionSource.FixedIfControlFilter(amount = 3, filter = GameObjectFilter.Artifact)))
    fun driver() = GameTestDriver().also {
        it.registerCards(TestCards.all + creatures + listOf(artifact, highCost, fixedCost, artifactCost))
        it.initMirrorMatch(deck = Deck.of("Island" to 40))
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun cost(d: GameTestDriver, name: String = "Bolt Bend") = CostCalculator(d.cardRegistry)
        .calculateEffectiveCost(d.state, d.cardRegistry.requireCard(name), d.player1).toString()
    fun effect(d: GameTestDriver, id: EntityId, layer: Layer, modification: SerializableModification,
               sublayer: Sublayer? = null) {
        d.replaceState(d.state.addFloatingEffect(layer = layer, modification = modification,
            affectedEntities = setOf(id), duration = Duration.EndOfTurn,
            context = EffectContext(sourceId = null, controllerId = d.player1), sublayer = sublayer))
    }
    test("BP01 gaining opponent owned creature uses projected controller") {
        val d = driver()
        val id = d.putCreatureOnBattlefield(d.player2, creatures[1].name)
        effect(d, id, Layer.CONTROL, SerializableModification.ChangeController(d.player1))
        d.state.getBattlefield(d.player2).contains(id) shouldBe true
        d.state.getEntity(id)?.get<ControllerComponent>()?.playerId shouldBe d.player2
        d.state.projectedState.getController(id) shouldBe d.player1
        cost(d) shouldBe ManaCost.parse("{R}").toString()
    }
    test("BP02 losing own creature uses projected controller") {
        val d = driver()
        val id = d.putCreatureOnBattlefield(d.player1, creatures[1].name)
        effect(d, id, Layer.CONTROL, SerializableModification.ChangeController(d.player2))
        d.state.getBattlefield(d.player1).contains(id) shouldBe true
        d.state.getEntity(id)?.get<ControllerComponent>()?.playerId shouldBe d.player1
        d.state.projectedState.getController(id) shouldBe d.player2
        cost(d) shouldBe ManaCost.parse("{3}{R}").toString()
    }
    test("BP03 projected boost crosses inclusive power threshold") {
        val d = driver()
        val id = d.putCreatureOnBattlefield(d.player1, creatures[0].name)
        effect(d, id, Layer.POWER_TOUGHNESS, SerializableModification.ModifyPowerToughness(1, 0), Sublayer.MODIFICATIONS)
        d.state.getEntity(id)?.get<CardComponent>()?.baseStats?.basePower shouldBe 3
        d.state.projectedState.getPower(id) shouldBe 4
        cost(d) shouldBe ManaCost.parse("{R}").toString()
    }
    test("BP04 projected shrink removes qualification") {
        val d = driver()
        val id = d.putCreatureOnBattlefield(d.player1, creatures[2].name)
        effect(d, id, Layer.POWER_TOUGHNESS, SerializableModification.ModifyPowerToughness(-2, 0), Sublayer.MODIFICATIONS)
        d.state.getEntity(id)?.get<CardComponent>()?.baseStats?.basePower shouldBe 5
        d.state.projectedState.getPower(id) shouldBe 3
        cost(d) shouldBe ManaCost.parse("{3}{R}").toString()
    }
    test("BP05 losing creature type removes qualification") {
        val d = driver()
        val id = d.putCreatureOnBattlefield(d.player1, creatures[1].name)
        effect(d, id, Layer.TYPE, SerializableModification.SetCardTypes(setOf("ARTIFACT")))
        d.state.getEntity(id)?.get<CardComponent>()?.typeLine?.isCreature shouldBe true
        d.state.projectedState.isCreature(id) shouldBe false
        cost(d) shouldBe ManaCost.parse("{3}{R}").toString()
    }
    test("BP06 animated artifact qualifies with projected type and power") {
        val d = driver()
        val id = d.putPermanentOnBattlefield(d.player1, artifact.name)
        effect(d, id, Layer.TYPE, SerializableModification.AddType("CREATURE"))
        effect(d, id, Layer.POWER_TOUGHNESS, SerializableModification.SetPowerToughness(4, 4), Sublayer.SET_VALUES)
        d.state.getEntity(id)?.get<CardComponent>()?.typeLine?.isCreature shouldBe false
        d.state.projectedState.isCreature(id) shouldBe true
        d.state.projectedState.getPower(id) shouldBe 4
        cost(d) shouldBe ManaCost.parse("{R}").toString()
    }
    test("BP07 multiple qualifying creatures preserve red") {
        val d = driver()
        d.putCreatureOnBattlefield(d.player1, creatures[1].name)
        d.putCreatureOnBattlefield(d.player1, creatures[2].name)
        cost(d) shouldBe ManaCost.parse("{R}").toString()
    }
    test("BP08 fixed reduction applies exactly once above generic floor") {
        val d = driver()
        d.putCreatureOnBattlefield(d.player1, creatures[1].name)
        d.putCreatureOnBattlefield(d.player1, creatures[2].name)
        cost(d, highCost.name) shouldBe ManaCost.parse("{5}{R}").toString()
    }
    test("BP09 synthetic fixed reduction remains conditional") {
        val d = driver()
        d.putCreatureOnBattlefield(d.player2, creatures[1].name)
        cost(d, highCost.name) shouldBe ManaCost.parse("{8}{R}").toString()
    }
    test("BP10 unrelated unconditional generic reduction is preserved") {
        val d = driver()
        cost(d, fixedCost.name) shouldBe ManaCost.parse("{6}{R}").toString()
        d.putCreatureOnBattlefield(d.player1, creatures[1].name)
        cost(d, fixedCost.name) shouldBe ManaCost.parse("{6}{R}").toString()
    }
    test("BP11 unrelated artifact predicate preserves positive and negative cases") {
        val d = driver()
        d.putCreatureOnBattlefield(d.player1, creatures[1].name)
        cost(d, artifactCost.name) shouldBe ManaCost.parse("{8}{R}").toString()
        d.putPermanentOnBattlefield(d.player1, artifact.name)
        cost(d, artifactCost.name) shouldBe ManaCost.parse("{5}{R}").toString()
    }
})
