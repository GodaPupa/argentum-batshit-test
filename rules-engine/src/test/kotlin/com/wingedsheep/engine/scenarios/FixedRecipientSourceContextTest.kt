package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.*
import com.wingedsheep.engine.mechanics.layers.*
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.mechanics.targeting.*
import com.wingedsheep.engine.state.*
import com.wingedsheep.engine.state.components.identity.*
import com.wingedsheep.engine.state.components.player.PlayerProtectionComponent
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.ProtectionScope
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.targets.*
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Internal occurrence-operation boundaries, not public flow or gameplay admission. */
class FixedRecipientSourceContextTest : ScenarioTestBase() {
    init {
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        data class F(val state: GameState, val stack: EntityId, val source: EntityId, val recipient: EntityId,
            val one: EntityId, val two: EntityId, val kind: String)
        fun fixture(kind: String, players: Boolean = false, zone: Zone = Zone.BATTLEFIELD, controllerTwo: Boolean = false): F {
            val game = scenario().withPlayers("One", "Two").withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(1, "Hill Giant").withCardOnBattlefield(2, "Craw Wurm").build()
            val source = game.findPermanent("Grizzly Bears")!!; val old = game.findPermanent("Hill Giant")!!
            val recipient = game.findPermanent("Craw Wurm")!!
            var s = game.state
            if (zone != Zone.BATTLEFIELD) for (id in listOf(source, old)) {
                s = s.removeFromZone(s.logicalZone(id)!!, id).addToZone(ZoneKey(game.player1Id, zone), id)
            }
            val target = if (players) ChosenTarget.Player(game.player1Id) else if (zone == Zone.BATTLEFIELD)
                ChosenTarget.Permanent(old) else ChosenTarget.Card(old, game.player1Id, zone)
            val selection = TargetSlotPlan(listOf(TargetInstanceSelection(listOf(TargetSlot(target,
                if (players) null else s.objectRef(old))))))
            val plan = CurrentTargetPlan(selection)
            val requirement = if (players) TargetPlayer() else TargetObject(filter = TargetFilter(GameObjectFilter.Creature, zone))
            val refs = ObjectReferenceEnvironment(captured = true, origin = s.objectRef(source), source = s.objectRef(source))
            val controller = if (controllerTwo) game.player2Id else game.player1Id
            val resolver = StackResolver(cardRegistry); val effect = Effects.Destroy(EffectTarget.ContextTarget(0))
            val result = if (kind == "activation") resolver.putActivatedAbility(s,
                ActivatedAbilityOnStackComponent(source, "Source", controller, effect, objectReferences = refs,
                    originalTargetSlots = selection), plan.targets, listOf(requirement), currentTargetPlan = plan)
            else resolver.putTriggeredAbility(s, TriggeredAbilityOnStackComponent(source, "Source", controller, effect,
                "Source context", objectReferences = refs, originalTargetSlots = selection), plan.targets,
                listOf(requirement), currentTargetPlan = plan)
            result.error shouldBe null
            return F(result.state, result.state.stack.last(), source, recipient, game.player1Id, game.player2Id, kind)
        }
        fun effect(f: F, id: EntityId, layer: Layer, modification: SerializableModification): F = f.copy(
            state = f.state.addFloatingEffect(layer, modification, setOf(id), Duration.EndOfTurn,
                EffectContext(sourceId = f.source, controllerId = f.one)))
        fun rich(f: F): F = effect(effect(effect(effect(f, f.source, Layer.COLOR,
            SerializableModification.ChangeColor(setOf("RED"))), f.source, Layer.TYPE, SerializableModification.AddSubtype("Zombie")),
            f.source, Layer.TYPE, SerializableModification.AddType("ARTIFACT")), f.source, Layer.TYPE, SerializableModification.AddType("LEGENDARY"))
        fun snapshot(f: F, value: EntitySnapshot?): F = f.copy(state = f.state.updateEntity(f.stack) { c ->
            if (f.kind == "activation") c.with(c.get<ActivatedAbilityOnStackComponent>()!!.copy(lastKnownSourceSnapshot = value))
            else c.with(c.get<TriggeredAbilityOnStackComponent>()!!.copy(lastKnownSourceSnapshot = value)) })
        fun visit(f: F, kind: String): F {
            if (kind == "live") return f
            val captured = snapshot(f, EntitySnapshot.fromProjection(f.source, f.state))
            if (kind == "removed") return captured.copy(state = captured.state.removeEntity(f.source))
            val from = captured.state.logicalZone(f.source)!!
            val other = ZoneKey(f.one, if (from.zoneType == Zone.GRAVEYARD) Zone.EXILE else Zone.GRAVEYARD)
            val s = captured.state.removeFromZone(from, f.source).addToZone(other, f.source)
                .removeFromZone(other, f.source).addToZone(from, f.source).copy(floatingEffects = emptyList())
            return captured.copy(state = s)
        }
        fun eligible(f: F, target: ChosenTarget): Boolean {
            val state = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), f.state))
            val offer = FixedRecipientRetargeting.offer(state, f.stack, target, f.two) ?: return false
            val result = FixedRecipientRetargeting.apply(state, offer, offer.addresses.single())
            result.state.getEntity(f.stack)!!.currentTargetPlan()!!.targets shouldBe listOf(target)
            return true
        }
        val scopes = linkedMapOf("color" to ProtectionScope.Color(Color.RED), "subtype" to ProtectionScope.Subtype("Zombie"),
            "cardtype" to ProtectionScope.CardType("ARTIFACT"), "supertype" to ProtectionScope.Supertype("LEGENDARY"))
        for (kind in listOf("trigger", "activation")) {
            for (v in listOf("live", "removed", "returned")) for ((label, scope) in scopes) {
                test("FSC player $kind $v $label") {
                    var f = visit(rich(fixture(kind, players = true)), v)
                    f = f.copy(state = f.state.updateEntity(f.two) { it.with(PlayerProtectionComponent(listOf(scope))) })
                    eligible(f, ChosenTarget.Player(f.two)) shouldBe false
                }
            }
            for ((label, scope) in scopes) test("FSC partial $kind $label") {
                val base = fixture(kind, players = true)
                var f = rich(visit(base, "returned"))
                f = snapshot(f, EntitySnapshot(base.source, objectRef = base.state.objectRef(base.source)))
                f = f.copy(state = f.state.updateEntity(f.two) { it.with(PlayerProtectionComponent(listOf(scope))) })
                eligible(f, ChosenTarget.Player(f.two)) shouldBe true
            }
            for (v in listOf("live", "removed", "returned")) for (controllerTwo in listOf(false, true)) test("FSC controller $kind $v $controllerTwo") {
                var f = fixture(kind, players = true, controllerTwo = controllerTwo)
                f = f.copy(state = f.state.updateEntity(f.source) { it.with(ControllerComponent(if (controllerTwo) f.one else f.two)) }
                    .updateEntity(f.two) { it.with(PlayerProtectionComponent(listOf(ProtectionScope.EachOpponent))) })
                f = visit(f, v)
                eligible(f, ChosenTarget.Player(f.two)) shouldBe controllerTwo
            }
            for (v in listOf("live", "removed", "returned", "inverse")) test("FSC legendary $kind $v") {
                var f = if (v == "inverse") rich(visit(fixture(kind), "returned")) else visit(rich(fixture(kind)), v)
                f = effect(f, f.recipient, Layer.ABILITY, SerializableModification.GrantKeyword("PROTECTION_FROM_SUPERTYPE_LEGENDARY"))
                eligible(f, ChosenTarget.Permanent(f.recipient)) shouldBe (v == "inverse")
            }
            for (v in listOf("live", "removed", "returned")) test("FSC aura ability $kind $v") {
                var f = fixture(kind)
                f = f.copy(state = f.state.updateEntity(f.source) { it.with(it.get<CardComponent>()!!.copy(typeLine = TypeLine.parse("Enchantment — Aura"))) })
                f = visit(f, v)
                f = effect(f, f.recipient, Layer.ABILITY, SerializableModification.GrantKeyword("CANT_BE_ENCHANTED"))
                eligible(f, ChosenTarget.Permanent(f.recipient)) shouldBe true
            }
            for (zone in listOf(Zone.BATTLEFIELD, Zone.GRAVEYARD)) for (history in listOf("live", "returned", "unknown", "legacy")) {
                test("FSC other $kind $zone $history") {
                    var f = fixture(kind, zone = zone)
                    if (history != "live") f = visit(f, "returned")
                    f = f.copy(state = f.state.updateEntity(f.stack) { c ->
                        var out = c.with(c.get<TargetsComponent>()!!.copy(targetRequirements = listOf(TargetObject(filter = TargetFilter(GameObjectFilter.Creature, zone, excludeSelf = true)))))
                        if (history in listOf("unknown", "legacy")) {
                            val refs = ObjectReferenceEnvironment(captured = history == "unknown")
                            out = if (kind == "activation") out.with(out.get<ActivatedAbilityOnStackComponent>()!!.copy(objectReferences = refs,
                                sourceBattlefieldTimestamp = null, lastKnownSourceSnapshot = null))
                            else out.with(out.get<TriggeredAbilityOnStackComponent>()!!.copy(objectReferences = refs,
                                sourceBattlefieldTimestamp = null, lastKnownSourceSnapshot = null))
                        }
                        out
                    })
                    val target = if (zone == Zone.BATTLEFIELD) ChosenTarget.Permanent(f.source) else ChosenTarget.Card(f.source, f.one, zone)
                    eligible(f, target) shouldBe (history == "returned")
                }
            }
        }
        test("FSC aura spell remains restricted") {
            var f = fixture("trigger")
            val card = f.state.getEntity(f.source)!!.get<CardComponent>()!!.copy(typeLine = TypeLine.parse("Enchantment — Aura"))
            f = f.copy(state = f.state.updateEntity(f.stack) { it.without<TriggeredAbilityOnStackComponent>()
                .with(SpellOnStackComponent(f.one)).with(card).with(ControllerComponent(f.one)) })
            f = effect(f, f.recipient, Layer.ABILITY, SerializableModification.GrantKeyword("CANT_BE_ENCHANTED"))
            eligible(f, ChosenTarget.Permanent(f.recipient)) shouldBe false
        }
        for (sourceOwnsPlayer in listOf(false, true)) test("FSC damage controller compatibility $sourceOwnsPlayer") {
            var f = fixture("trigger", players = true)
            f = f.copy(state = f.state.updateEntity(f.two) { it.with(PlayerProtectionComponent(listOf(ProtectionScope.EachOpponent))) })
            val snap = EntitySnapshot(f.source, controllerId = if (sourceOwnsPlayer) f.two else f.one)
            PlayerProtectionRules.isProtectedFromSource(f.state, f.two, f.source, if (sourceOwnsPlayer) f.one else f.two, snap) shouldBe !sourceOwnsPlayer
        }
    }
}
