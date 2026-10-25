package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.ObjectReferenceEnvironment
import com.wingedsheep.engine.mechanics.layers.*
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.state.*
import com.wingedsheep.engine.state.components.battlefield.BattlefieldEntryTimestampComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class AbilitySourceLegacyBoundaryTest : ScenarioTestBase() {
    init {
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val cases = linkedMapOf("legacy-live" to true, "legacy-missing" to false,
            "known-lost-without-snapshot" to false, "captured-without-origin" to false, "partial-snapshot" to false,
            "timestamp-returned" to true, "colorless" to false,
            "ability-controller-own-target" to false, "ability-controller-opponent-target" to true)
        for (kind in listOf("trigger", "activation")) for ((case, survives) in cases) {
            test("SB $kind $case") {
                val targetSeat = if (case == "ability-controller-own-target") 1 else 2
                val game = scenario().withPlayers("One", "Two")
                    .withCardOnBattlefield(1, "Grizzly Bears").withCardOnBattlefield(targetSeat, "Craw Wurm").build()
                val source = game.findPermanent("Grizzly Bears")!!
                val target = game.findPermanent("Craw Wurm")!!
                val origin = game.state.objectRef(source)!!
                var state = game.state
                var references = ObjectReferenceEnvironment()
                if (case == "captured-without-origin") references = ObjectReferenceEnvironment(captured = true)
                var snapshot: EntitySnapshot? = null
                var stamp: Long? = null
                if (case == "known-lost-without-snapshot" || case == "timestamp-returned") {
                    val zone = state.logicalZone(source)!!
                    val grave = ZoneKey(game.player1Id, Zone.GRAVEYARD)
                    if (case == "timestamp-returned") {
                        stamp = 10L
                        snapshot = EntitySnapshot.fromProjection(source, state).copy(objectRef = null,
                            battlefieldEntryTimestamp = 10L, colors = setOf("RED"))
                    } else references = ObjectReferenceEnvironment(captured = true, origin = origin, source = origin)
                    state = state.removeFromZone(zone, source).addToZone(grave, source)
                        .removeFromZone(grave, source).addToZone(zone, source)
                        .updateEntity(source) { it.with(BattlefieldEntryTimestampComponent(20L)) }
                    state.isCurrentObject(origin) shouldBe false
                }
                if (case == "partial-snapshot") snapshot = EntitySnapshot(source)
                if (case == "legacy-missing" || case == "partial-snapshot") state = state.removeEntity(source)
                val context = EffectContext(sourceId = source, controllerId = game.player1Id)
                if (case == "colorless") state = state.addFloatingEffect(Layer.COLOR,
                    SerializableModification.ChangeColor(emptySet()), setOf(source), Duration.EndOfTurn, context)
                if (case.startsWith("ability-controller")) state = state.updateEntity(source) {
                    it.with(ControllerComponent(game.player2Id))
                }
                val keyword = when {
                    case.startsWith("ability-controller") -> "HEXPROOF"
                    case == "timestamp-returned" -> "PROTECTION_FROM_RED"
                    else -> "PROTECTION_FROM_GREEN"
                }
                state = state.addFloatingEffect(Layer.ABILITY, SerializableModification.GrantKeyword(keyword),
                    setOf(target), Duration.EndOfTurn, context)
                val resolver = StackResolver(cardRegistry)
                val effect = Effects.Destroy(EffectTarget.ContextTarget(0))
                // Legacy target layout deliberately has no authoritative current-target plan.
                val queued = if (kind == "activation") resolver.putActivatedAbility(state,
                    ActivatedAbilityOnStackComponent(source, "Grizzly Bears", game.player1Id, effect,
                        objectReferences = references, sourceBattlefieldTimestamp = stamp, lastKnownSourceSnapshot = snapshot),
                    listOf(ChosenTarget.Permanent(target)), listOf(Targets.Creature))
                else resolver.putTriggeredAbility(state,
                    TriggeredAbilityOnStackComponent(source, "Grizzly Bears", game.player1Id, effect, "Legacy source",
                        objectReferences = references, sourceBattlefieldTimestamp = stamp, lastKnownSourceSnapshot = snapshot),
                    listOf(ChosenTarget.Permanent(target)), listOf(Targets.Creature))
                queued.error shouldBe null
                val encoded = json.encodeToString(GameState.serializer(), queued.state)
                val restored = json.decodeFromString(GameState.serializer(), encoded)
                json.encodeToString(GameState.serializer(), restored) shouldBe encoded
                val result = resolver.resolveTop(restored)
                result.error shouldBe null
                (target in result.state.getBattlefield()) shouldBe survives
            }
        }

        val spell = card("Fixture Source Compatibility Spell") {
            manaCost = "{0}"; typeLine = "Instant"
            spell { val victim = target("creature", Targets.Creature); effect = Effects.Destroy(victim) }
        }
        cardRegistry.register(listOf(spell))
        for ((restriction, survives) in linkedMapOf("PROTECTION_FROM_CARDTYPE_INSTANT" to true,
            "CANT_BE_TARGETED_BY_CARDTYPE_INSTANT_SOURCE_ABILITIES" to false)) {
            test("SB spell $restriction") {
                val game = scenario().withPlayers("One", "Two")
                    .withCardInHand(1, spell.name).withCardOnBattlefield(2, "Craw Wurm")
                    .withActivePlayer(1).withPriorityPlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                val target = game.findPermanent("Craw Wurm")!!
                game.castSpell(1, spell.name, target).error shouldBe null
                game.state = game.state.addFloatingEffect(Layer.ABILITY,
                    SerializableModification.GrantKeyword(restriction), setOf(target), Duration.EndOfTurn,
                    EffectContext(sourceId = target, controllerId = game.player2Id))
                game.state = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), game.state))
                game.resolveStack().forEach { it.error shouldBe null }
                (target in game.state.getBattlefield()) shouldBe survives
            }
        }
    }
}
