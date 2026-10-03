package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.TurnManager
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutorRegistry
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.scripting.ConditionalStaticAbility
import com.wingedsheep.sdk.scripting.effects.TapUntapEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.engine.mechanics.layers.ProtectionAttachmentLifecycle
import com.wingedsheep.engine.mechanics.sba.permanent.UnattachedAurasCheck
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.GrantProtection
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class ProtectionAttachmentLifecycleTest : ScenarioTestBase() {
    private val host = card("Lifecycle Host") {
        manaCost = "{2}{G}"
        typeLine = "Creature — Bear"
        power = 3
        toughness = 3
    }
    private val grant = card("Lifecycle Grant") {
        manaCost = "{W}"
        typeLine = "Enchantment — Aura"
        auraTarget = Targets.Creature
        staticAbility { ability = GrantProtection(Color.WHITE, retainsPreexistingControlledAttachments = true) }
    }
    private val aura = card("Lifecycle Aura") {
        manaCost = "{W}"; typeLine = "Enchantment — Aura"; auraTarget = Targets.Creature
    }
    private val equipment = card("Lifecycle Equipment") { manaCost = "{W}"; typeLine = "Artifact — Equipment" }
    private val plain = card("Lifecycle Independent Grant") {
        manaCost = "{U}"; typeLine = "Enchantment — Aura"; auraTarget = Targets.Creature
        staticAbility { ability = GrantProtection(Color.WHITE) }
    }

    private val tappedGrant = card("Lifecycle Tapped Grant") {
        manaCost = "{W}"; typeLine = "Enchantment — Aura"; auraTarget = Targets.Creature
        staticAbility { ability = ConditionalStaticAbility(
            condition = Conditions.SourceIsTapped,
            ability = GrantProtection(Color.WHITE, retainsPreexistingControlledAttachments = true)) }
    }
    private val turnGrant = card("Lifecycle Turn Grant") {
        manaCost = "{W}"; typeLine = "Enchantment — Aura"; auraTarget = Targets.Creature
        staticAbility { ability = ConditionalStaticAbility(
            condition = Conditions.IsYourTurn,
            ability = GrantProtection(Color.WHITE, retainsPreexistingControlledAttachments = true)) }
    }

    private data class Board(val state: GameState, val host: EntityId, val grant: EntityId, val aura: EntityId, val equipment: EntityId)
    private fun board(opponentAttachments: Boolean = false): Board {
        val g = scenario().withPlayers("A", "B")
            .withCardOnBattlefield(1, host.name)
            .withCardAttachedTo(if (opponentAttachments) 2 else 1, aura.name, host.name)
            .withCardAttachedTo(if (opponentAttachments) 2 else 1, equipment.name, host.name)
            .withCardAttachedTo(1, grant.name, host.name).build()
        return Board(g.state, g.findPermanent(host.name)!!, g.findPermanent(grant.name)!!,
            g.findPermanent(aura.name)!!, g.findPermanent(equipment.name)!!)
    }
    private fun detach(state: GameState, id: EntityId): GameState = state.updateEntity(id) { it.without<AttachedToComponent>() }
    private fun attach(state: GameState, id: EntityId, host: EntityId): GameState = state.updateEntity(id) { it.with(AttachedToComponent(host)) }
    private fun activate(b: Board): GameState = ProtectionAttachmentLifecycle.reconcile(detach(b.state, b.grant), b.state)

    init {
        listOf(host, grant, aura, equipment, plain, tappedGrant, turnGrant).forEach(cardRegistry::register)

        test("activation captures controlled Aura Equipment and the source itself") {
            val b = board(); val s = activate(b)
            s.protectionAttachmentActivations.single().attachments.size shouldBe 3
            val r = UnattachedAurasCheck(cardRegistry).check(s).newState
            (b.aura in r.getBattlefield()) shouldBe true
            (b.grant in r.getBattlefield()) shouldBe true
            r.getEntity(b.equipment)?.get<AttachedToComponent>()?.targetId shouldBe b.host
        }
        test("opponent controlled attachments are not captured") {
            val b = board(true); val s = activate(b)
            s.protectionAttachmentActivations.single().attachments.size shouldBe 1
            val r = UnattachedAurasCheck(cardRegistry).check(s).newState
            (b.aura in r.getBattlefield()) shouldBe false
            r.getEntity(b.equipment)?.get<AttachedToComponent>() shouldBe null
        }
        test("a later attachment does not gain a retroactive exception") {
            val b = board(); val absent = detach(b.state, b.aura)
            val active = ProtectionAttachmentLifecycle.reconcile(detach(absent, b.grant), absent)
            val later = ProtectionAttachmentLifecycle.reconcile(active, attach(active, b.aura, b.host))
            val r = UnattachedAurasCheck(cardRegistry).check(later).newState
            (b.aura in r.getBattlefield()) shouldBe false
        }
        test("an outer composite boundary preserves the earlier child capture") {
            val b = board(); val absent = detach(b.state, b.aura)
            val before = detach(absent, b.grant)
            val active = ProtectionAttachmentLifecycle.reconcile(before, absent)
            val later = ProtectionAttachmentLifecycle.reconcile(active, attach(active, b.aura, b.host))
            val outer = ProtectionAttachmentLifecycle.reconcile(before, later)
            (b.aura in outer.protectionAttachmentActivations.single().attachments.map { it.entityId }) shouldBe false
        }
        test("simultaneous attachment and grant activation includes both new attachments") {
            val b = board(); val before = detach(detach(detach(b.state, b.grant), b.aura), b.equipment)
            val s = ProtectionAttachmentLifecycle.reconcile(before, b.state)
            s.protectionAttachmentActivations.single().attachments.size shouldBe 3
        }
        test("detaching then reattaching an object does not revive its old exception") {
            val b = board(); val active = activate(b)
            val detached = ProtectionAttachmentLifecycle.reconcile(active, detach(active, b.aura))
            val reattached = ProtectionAttachmentLifecycle.reconcile(detached, attach(detached, b.aura, b.host))
            (b.aura in reattached.protectionAttachmentActivations.single().attachments.map { it.entityId }) shouldBe false
        }
        test("grant deactivation and reactivation create a new immutable activation epoch") {
            val b = board(); val active = activate(b)
            val epoch = active.protectionAttachmentActivations.single().epoch
            val inactive = ProtectionAttachmentLifecycle.reconcile(active, detach(active, b.grant))
            inactive.protectionAttachmentActivations.isEmpty() shouldBe true
            val reactivated = ProtectionAttachmentLifecycle.reconcile(inactive, attach(inactive, b.grant, b.host))
            (reactivated.protectionAttachmentActivations.single().epoch > epoch) shouldBe true
        }
        test("legacy active grants without history fail closed instead of sampling at SBA") {
            val b = board()
            val s = ProtectionAttachmentLifecycle.reconcile(b.state, b.state.copy(timestamp = b.state.timestamp + 1))
            s.protectionAttachmentActivations.isEmpty() shouldBe true
            (b.aura in UnattachedAurasCheck(cardRegistry).check(s).newState.getBattlefield()) shouldBe false
        }
        test("capture survives serialization without being recomputed") {
            val b = board(); val active = activate(b)
            val json = Json { allowStructuredMapKeys = true; serializersModule = com.wingedsheep.engine.core.engineSerializersModule }
            val restored = json.decodeFromString<GameState>(json.encodeToString(active))
            restored.protectionAttachmentActivations shouldBe active.protectionAttachmentActivations
            (b.aura in UnattachedAurasCheck(cardRegistry).check(restored).newState.getBattlefield()) shouldBe true
        }
        test("another protection source still independently forbids a captured attachment") {
            val g = scenario().withPlayers("A", "B")
                .withCardOnBattlefield(1, host.name)
                .withCardAttachedTo(1, aura.name, host.name)
                .withCardAttachedTo(1, grant.name, host.name)
                .withCardAttachedTo(1, plain.name, host.name).build()
            val source = g.findPermanent(grant.name)!!
            val s = ProtectionAttachmentLifecycle.reconcile(detach(g.state, source), g.state)
            (g.findPermanent(aura.name)!! in UnattachedAurasCheck(cardRegistry).check(s).newState.getBattlefield()) shouldBe false
        }
        test("attachment control transfer cannot borrow its former controllers exception") {
            val b = board(); val active = activate(b)
            val oldController = active.projectedState.getController(b.grant)!!
            val opponent = active.turnOrder.first { it != oldController }
            val stolen = active.updateEntity(b.aura) { it.with(ControllerComponent(opponent)) }
            val s = ProtectionAttachmentLifecycle.reconcile(active, stolen)
            (b.aura in UnattachedAurasCheck(cardRegistry).check(s).newState.getBattlefield()) shouldBe false
        }
        test("reused source entity identity cannot borrow a departed activation") {
            val b = board(); val active = activate(b)
            val old = active.objectIdentities[b.grant]!!
            val next = active.copy(objectIdentities = active.objectIdentities + (b.grant to old.copy(generation = old.generation + 1)))
            val grant = next.projectedState.colorProtectionGrants(b.host).single()
            ProtectionAttachmentLifecycle.retains(next, b.host, b.aura, grant) shouldBe false
        }

        test("effect dispatcher captures a conditional grant when tapping activates it") {
            val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, host.name)
                .withCardAttachedTo(1, aura.name, host.name)
                .withCardAttachedTo(1, tappedGrant.name, host.name).build()
            val source = g.findPermanent(tappedGrant.name)!!
            val controller = g.state.projectedState.getController(source)!!
            val result = EffectExecutorRegistry(cardRegistry = cardRegistry).execute(g.state,
                TapUntapEffect(EffectTarget.Self), EffectContext(sourceId = source, controllerId = controller))
            result.newState.protectionAttachmentActivations.single().attachments.size shouldBe 2
            (g.findPermanent(aura.name)!! in UnattachedAurasCheck(cardRegistry).check(result.newState).newState.getBattlefield()) shouldBe true
        }
        test("turn dispatcher captures a conditional grant before following turn operations") {
            val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, host.name)
                .withCardAttachedTo(1, aura.name, host.name)
                .withCardAttachedTo(1, turnGrant.name, host.name).build()
            val source = g.findPermanent(turnGrant.name)!!
            val controller = g.state.projectedState.getController(source)!!
            val before = g.state.copy(activePlayerId = g.state.turnOrder.first { it != controller })
            val result = TurnManager(cardRegistry).startTurn(before, controller)
            result.newState.protectionAttachmentActivations.single().attachments.size shouldBe 2
            (g.findPermanent(aura.name)!! in UnattachedAurasCheck(cardRegistry).check(result.newState).newState.getBattlefield()) shouldBe true
        }
        test("host zone departure and return cannot recapture stale attachments from its old visit") {
            val b = board(); val active = activate(b)
            val departed = ZoneTransitionService.moveToZone(active, b.host, Zone.EXILE).state
            val capturedDeparture = ProtectionAttachmentLifecycle.reconcile(active, departed)
            val returned = ZoneTransitionService.moveToZone(capturedDeparture, b.host, Zone.BATTLEFIELD).state
            val result = ProtectionAttachmentLifecycle.reconcile(capturedDeparture, returned)
            result.protectionAttachmentActivations.isEmpty() shouldBe true
            (b.grant in UnattachedAurasCheck(cardRegistry).check(result).newState.getBattlefield()) shouldBe false
        }

        test("composite dispatcher preserves capture before its later attachment child") {
            val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, host.name)
                .withCardOnBattlefield(1, equipment.name)
                .withCardAttachedTo(1, tappedGrant.name, host.name).build()
            val source = g.findPermanent(tappedGrant.name)!!
            val controller = g.state.projectedState.getController(source)!!
            val equipmentId = g.findPermanent(equipment.name)!!
            val hostId = g.findPermanent(host.name)!!
            val result = EffectExecutorRegistry(cardRegistry = cardRegistry).execute(g.state,
                com.wingedsheep.sdk.scripting.effects.CompositeEffect(listOf(
                    TapUntapEffect(EffectTarget.Self),
                    com.wingedsheep.sdk.scripting.effects.AttachTargetEquipmentToCreatureEffect(
                        EffectTarget.SpecificEntity(equipmentId), EffectTarget.SpecificEntity(hostId))
                )), EffectContext(sourceId = source, controllerId = controller))
            val capture = result.newState.protectionAttachmentActivations.single()
            capture.attachments.map { it.entityId } shouldBe listOf(source)
        }

    }
}
