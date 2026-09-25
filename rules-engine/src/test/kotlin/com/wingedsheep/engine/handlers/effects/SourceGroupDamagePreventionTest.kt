package com.wingedsheep.engine.handlers.effects

import com.wingedsheep.engine.core.DamageDealtEvent
import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.PreventDamageEffect
import com.wingedsheep.sdk.scripting.effects.PreventionDirection
import com.wingedsheep.sdk.scripting.effects.PreventionScope
import com.wingedsheep.sdk.scripting.effects.PreventionSourceFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.predicates.CardPredicate
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class SourceGroupDamagePreventionTest : FunSpec({
    val red = card("Group Fixture Red") {
        manaCost = "{R}"
        typeLine = "Creature — Elemental"
        power = 3
        toughness = 3
    }
    val multi = card("Group Fixture Multicolor") {
        manaCost = "{R}{G}"
        typeLine = "Creature — Elemental"
        power = 3
        toughness = 3
    }
    val walker = card("Group Fixture Walker") {
        manaCost = "{4}{W}"
        typeLine = "Planeswalker — Test"
        startingLoyalty = 7
    }
    val battle = card("Group Fixture Battle") {
        manaCost = "{4}{W}"
        typeLine = "Battle — Siege"
    }
    fun fixture() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(red, multi, walker, battle))
        initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    val effect = PreventDamageEffect(scope = PreventionScope.AllDamage,
        direction = PreventionDirection.FromTarget,
        sourceFilter = PreventionSourceFilter.FromGroup(GroupFilter(
            GameObjectFilter(cardPredicates = listOf(CardPredicate.HasChosenColor)))))
    fun shield(d: GameTestDriver, color: Color = Color.RED): GameState =
        EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state, effect,
            EffectContext(sourceId = null, controllerId = d.player1, chosenColor = color)).also {
                it.isSuccess shouldBe true
            }.state

    for (combat in listOf(false, true)) for (kind in listOf("player", "creature", "planeswalker", "battle")) {
        test("all-source shield prevents matching damage to $kind with combat=$combat") {
            val d = fixture()
            val source = d.putCreatureOnBattlefield(d.player2, red.name)
            val recipient = when (kind) {
                "player" -> d.player1
                "creature" -> d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
                "planeswalker" -> d.putPermanentOnBattlefield(d.player1, walker.name).also {
                    d.addComponent(it, CountersComponent(mapOf(CounterType.LOYALTY to 7)))
                }
                else -> d.putPermanentOnBattlefield(d.player1, battle.name).also {
                    d.addComponent(it, CountersComponent(mapOf(CounterType.DEFENSE to 7)))
                }
            }
            val protected = shield(d)
            val result = DamageUtils.dealDamageToTarget(protected, recipient, 3, source, isCombatDamage = combat)
            result.isSuccess shouldBe true
            result.events.filterIsInstance<DamageDealtEvent>() shouldBe emptyList()
            result.state shouldBe protected
        }
    }
    test("source groups include multicolor and nonbattlefield spell sources, independent of either controller") {
        val d = fixture()
        val sources = listOf(d.putCreatureOnBattlefield(d.player1, multi.name),
            d.putCreatureOnBattlefield(d.player2, multi.name), d.putCardInHand(d.player1, "Lightning Bolt"))
        val protected = shield(d)
        for (source in sources) for (recipient in listOf(d.player1, d.player2)) {
            DamageUtils.dealDamageToTarget(protected, recipient, 3, source)
                .events.filterIsInstance<DamageDealtEvent>() shouldBe emptyList()
        }
        val green = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val greenResult = DamageUtils.dealDamageToTarget(shield(d), d.player2, 2, green)
        greenResult.events.filterIsInstance<DamageDealtEvent>().single().amount shouldBe 2
    }
    test("current projected source color replaces printed color and newly qualifying sources are covered") {
        val d = fixture()
        val source = d.putCreatureOnBattlefield(d.player1, red.name)
        d.replaceState(shield(d))
        val services = EngineServices(d.cardRegistry)
        val changed = services.effectExecutorRegistry.execute(d.state,
            Effects.ChangeColor(EffectTarget.SpecificEntity(source), setOf(Color.BLUE)),
            EffectContext(sourceId = null, controllerId = d.player1))
        changed.isSuccess shouldBe true
        changed.state.projectedState.getColors(source) shouldBe setOf("BLUE")
        DamageUtils.dealDamageToTarget(changed.state, d.player2, 3, source)
            .events.filterIsInstance<DamageDealtEvent>().single().amount shouldBe 3
        d.replaceState(changed.state)
        val laterSource = d.putCreatureOnBattlefield(d.player2, red.name)
        DamageUtils.dealDamageToTarget(d.state, d.player1, 3, laterSource)
            .events.filterIsInstance<DamageDealtEvent>() shouldBe emptyList()
    }
    test("chosen color survives full state persistence and produces the identical damage result") {
        val d = fixture()
        val source = d.putCreatureOnBattlefield(d.player1, red.name)
        val protected = shield(d)
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
        val decoded = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), protected))
        decoded shouldBe protected
        (decoded.floatingEffects.single().effect.modification as SerializableModification.PreventAllDamageFromGroup)
            .chosenColor shouldBe Color.RED
        DamageUtils.dealDamageToTarget(decoded, d.player2, 3, source) shouldBe
            DamageUtils.dealDamageToTarget(protected, d.player2, 3, source)
    }
    test("explicit and global unpreventable damage ignore source-group prevention") {
        val d = fixture()
        val source = d.putCreatureOnBattlefield(d.player1, red.name)
        val protected = shield(d)
        DamageUtils.dealDamageToTarget(protected, d.player2, 3, source, cantBePrevented = true)
            .events.filterIsInstance<DamageDealtEvent>().single().amount shouldBe 3
        val disabled = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(protected,
            com.wingedsheep.sdk.scripting.effects.DamageCantBePreventedThisTurnEffect,
            EffectContext(sourceId = null, controllerId = d.player1)).state
        DamageUtils.dealDamageToTarget(disabled, d.player2, 3, source)
            .events.filterIsInstance<DamageDealtEvent>().single().amount shouldBe 3
    }
    test("chosen-color global shield is visible to both players without rewriting the stored choice") {
        val d = fixture()
        d.replaceState(shield(d))
        val transformer = com.wingedsheep.engine.view.ClientStateTransformer(cardRegistry = d.cardRegistry)
        for (player in listOf(d.player1, d.player2)) {
            val visible = transformer.transform(d.state, viewingPlayerId = player)
                .players.single { it.playerId == player }.activeEffects
                .single { it.effectId.startsWith("prevent_all_damage_group_") }
            val description = requireNotNull(visible.description) { "Source-group shield must expose its public description" }
            description.contains("All damage") shouldBe true
            description.contains(Color.RED.displayName) shouldBe true
        }
    }
    test("legacy combat-only source groups retain their representation and do not prevent spell damage") {
        val d = fixture()
        val source = d.putCreatureOnBattlefield(d.player1, red.name)
        val legacy = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
            effect.copy(scope = PreventionScope.CombatOnly,
                sourceFilter = PreventionSourceFilter.FromGroup(GroupFilter(GameObjectFilter.Any.withColor(Color.RED)))),
            EffectContext(sourceId = null, controllerId = d.player1)).state
        (legacy.floatingEffects.single().effect.modification is SerializableModification.PreventCombatDamageFromGroup) shouldBe true
        DamageUtils.dealDamageToTarget(legacy, d.player2, 3, source)
            .events.filterIsInstance<DamageDealtEvent>().single().amount shouldBe 3
    }
})
