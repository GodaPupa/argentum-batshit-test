package com.wingedsheep.engine.mechanics.layers

import com.wingedsheep.engine.core.CardEntityFactory
import com.wingedsheep.engine.handlers.effects.BattlefieldEntry
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.player.PermanentsEnteredUnderControlThisTurnComponent
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.conditions.Compare
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.conditions.PermanentTypeEnteredBattlefieldThisTurn
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Fixed, excluded characteristic previews; no gameplay allocations or pilot choices. */
class BattlefieldEntryProjectionTest : FunSpec({
    val owner = EntityId.of("entry-owner")
    val opponent = EntityId.of("entry-opponent")
    val support = EntityId.of("entry-support")
    val bear = EntityId.of("entry-existing-creature")
    val entering = EntityId.of("entry-new-creature")
    val red = CardDefinition.enchantment("Entry Red Support Fixture", ManaCost.parse("{R}"))
    val creature = CardDefinition.creature("Entry Creature Fixture", ManaCost.parse("{R}"), emptySet(), 2, 2)
    val ordinary = CardDefinition.creature("Entry Existing Creature Fixture", ManaCost.ZERO, emptySet(), 2, 2)
    val projector = StateProjector()

    fun before(supportEffects: List<ContinuousEffectData> = emptyList()) = GameState(
        entities = mapOf(
            owner to ComponentContainer(), opponent to ComponentContainer(),
            support to CardEntityFactory.create(red, owner).with(ContinuousEffectSourceComponent(supportEffects)),
            bear to CardEntityFactory.create(ordinary, owner),
        ),
        zones = mapOf(ZoneKey(owner, Zone.BATTLEFIELD) to listOf(support, bear)),
    )
    fun prepared(before: GameState, effects: List<ContinuousEffectData>) = before.withEntity(
        entering, CardEntityFactory.create(creature, owner).with(ContinuousEffectSourceComponent(effects)),
    )
    fun removeBelow(n: Int) = ContinuousEffectData(
        Modification.RemoveType("CREATURE"),
        sourceCondition = Compare(DynamicAmount.DevotionTo(listOf(Color.RED)), ComparisonOperator.LT, DynamicAmount.Fixed(n)),
    )

    test("the entering object's own mana cost is excluded from the pre-entry aggregate") {
        val before = before()
        val prepared = prepared(before, listOf(removeBelow(2)))
        val after = BattlefieldEntry.place(prepared, owner, entering)
        val bytes = SerializationTestSupport.encodeState(after)
        projector.projectForEntry(before, after, entering, owner).isCreature(entering) shouldBe false
        after.projectedState.isCreature(entering) shouldBe true
        SerializationTestSupport.encodeState(after) shouldBe bytes
        before.getBattlefield() shouldBe listOf(support, bear)
    }

    test("the entrant's static effect applies to itself without changing other permanents") {
        val before = before()
        val prepared = prepared(before, listOf(ContinuousEffectData(
            Modification.ModifyPowerToughness(1, 1), AffectsFilter.AllCreatures,
        )))
        val preview = projector.projectForEntry(before, prepared, entering, owner)
        preview.getPower(entering) shouldBe 3
        preview.getPower(bear) shouldBe 2
        BattlefieldEntry.place(prepared, owner, entering).projectedState.getPower(bear) shouldBe 3
    }

    test("existing continuous effects apply to the hypothetical entering permanent") {
        val before = before(listOf(ContinuousEffectData(
            Modification.ModifyPowerToughness(2, 0), AffectsFilter.AllCreatures,
        )))
        val preview = projector.projectForEntry(before, prepared(before, emptyList()), entering, owner)
        preview.getPower(entering) shouldBe 4
        preview.getPower(bear) shouldBe 4
    }

    test("an entry recorded in the prepared state cannot satisfy its own pre-entry history gate") {
        val before = before()
        val prepared = prepared(before, listOf(ContinuousEffectData(
            Modification.RemoveType("CREATURE"),
            sourceCondition = PermanentTypeEnteredBattlefieldThisTurn(CardType.CREATURE),
        )))
        val after = BattlefieldEntry.place(prepared, owner, entering)
        after.getEntity(owner)!!.get<PermanentsEnteredUnderControlThisTurnComponent>()!!.entries.size shouldBe 1
        projector.projectForEntry(before, after, entering, owner).isCreature(entering) shouldBe true
        after.projectedState.isCreature(entering) shouldBe false
    }

    test("pre-entry aggregate evaluation preserves existing earlier-layer control changes") {
        val before = before(listOf(ContinuousEffectData(Modification.ChangeController(opponent))))
        before.projectedState.getController(support) shouldBe opponent
        val prepared = prepared(before, listOf(removeBelow(1)))
        projector.projectForEntry(before, prepared, entering, owner).isCreature(entering) shouldBe false
        BattlefieldEntry.place(prepared, owner, entering).projectedState.isCreature(entering) shouldBe true
    }

    test("an already-entered state is rejected as a pre-entry checkpoint") {
        val before = before()
        val after = BattlefieldEntry.place(prepared(before, emptyList()), owner, entering)
        shouldThrow<IllegalArgumentException> { projector.projectForEntry(after, after, entering, owner) }
    }
})
