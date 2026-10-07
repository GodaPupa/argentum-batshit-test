package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.sba.permanent.UnattachedAurasCheck
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.mechanics.layers.ActiveFloatingEffect
import com.wingedsheep.engine.mechanics.layers.FloatingEffectData
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.GrantProtection
import io.kotest.matchers.shouldBe

/** Per-instance protection attachment legality; no registry admission. */
class ProtectionAttachmentProvenanceTest : ScenarioTestBase() {
    private val host = card("Provenance Host") {
        manaCost = "{2}{G}"
        typeLine = "Creature — Bear"
        power = 3
        toughness = 3
    }
    private val ward = card("Provenance Ward") {
        manaCost = "{W}"
        typeLine = "Enchantment — Aura"
        auraTarget = Targets.Creature
        staticAbility { ability = GrantProtection(Color.WHITE, retainsSourceAttachment = true) }
    }
    private val otherWard = card("Independent Provenance Ward") {
        manaCost = "{W}"
        typeLine = "Enchantment — Aura"
        auraTarget = Targets.Creature
        staticAbility { ability = GrantProtection(Color.WHITE, retainsSourceAttachment = true) }
    }
    private val aura = card("Provenance White Aura") {
        manaCost = "{W}"
        typeLine = "Enchantment — Aura"
        auraTarget = Targets.Creature
    }
    private val equipment = card("Provenance White Equipment") {
        manaCost = "{W}"
        typeLine = "Artifact — Equipment"
    }

    init {
        listOf(host, ward, otherWard, aura, equipment).forEach(cardRegistry::register)

        test("control - protection source retains itself under its own grant") {
            val g = scenario().withPlayers("A", "B")
                .withCardOnBattlefield(1, host.name)
                .withCardAttachedTo(1, ward.name, host.name).build()
            val id = g.findPermanent(ward.name)!!
            val r = UnattachedAurasCheck(cardRegistry).check(g.state)
            (id in r.state.getBattlefield()) shouldBe true
            r.state.getEntity(id)?.get<AttachedToComponent>()?.targetId shouldBe g.findPermanent(host.name)
        }

        test("control - unrelated same-color Aura normally falls off") {
            val g = scenario().withPlayers("A", "B")
                .withCardOnBattlefield(1, host.name)
                .withCardAttachedTo(1, aura.name, host.name)
                .withCardAttachedTo(1, ward.name, host.name).build()
            val id = g.findPermanent(aura.name)!!
            val r = UnattachedAurasCheck(cardRegistry).check(g.state)
            (id in r.state.getBattlefield()) shouldBe false
        }

        test("control - opponent-controlled Aura and Equipment do not receive a global exemption") {
            val g = scenario().withPlayers("A", "B")
                .withCardOnBattlefield(1, host.name)
                .withCardAttachedTo(2, aura.name, host.name)
                .withCardAttachedTo(2, equipment.name, host.name)
                .withCardAttachedTo(1, ward.name, host.name).build()
            val a = g.findPermanent(aura.name)!!
            val e = g.findPermanent(equipment.name)!!
            val r = UnattachedAurasCheck(cardRegistry).check(g.state)
            (a in r.state.getBattlefield()) shouldBe false
            (e in r.state.getBattlefield()) shouldBe true
            r.state.getEntity(e)?.get<AttachedToComponent>() shouldBe null
        }

        test("control - later controlled attachment does not receive a global exemption") {
            val g = scenario().withPlayers("A", "B")
                .withCardOnBattlefield(1, host.name)
                .withCardAttachedTo(1, ward.name, host.name)
                .withCardAttachedTo(1, aura.name, host.name).build()
            val id = g.findPermanent(aura.name)!!
            val r = UnattachedAurasCheck(cardRegistry).check(g.state)
            (id in r.state.getBattlefield()) shouldBe false
        }

        test("required provenance - independent floating protection still removes the granting Aura") {
            val g = scenario().withPlayers("A", "B")
                .withCardOnBattlefield(1, host.name)
                .withCardAttachedTo(1, ward.name, host.name).build()
            val h = g.findPermanent(host.name)!!
            val a = g.findPermanent(ward.name)!!
            val independent = ActiveFloatingEffect(
                id = EntityId.generate(),
                effect = FloatingEffectData(
                    layer = Layer.ABILITY,
                    modification = SerializableModification.GrantProtectionFromColor("WHITE"),
                    affectedEntities = setOf(h)
                ),
                duration = Duration.EndOfTurn,
                sourceId = null,
                controllerId = g.player1Id,
                timestamp = 100L
            )
            val protected = g.state.copy(floatingEffects = g.state.floatingEffects + independent)
            val r = UnattachedAurasCheck(cardRegistry).check(protected)
            (a in r.state.getBattlefield()) shouldBe false
        }

        test("required provenance - two distinct granting Auras each see the other protection instance") {
            val g = scenario().withPlayers("A", "B")
                .withCardOnBattlefield(1, host.name)
                .withCardAttachedTo(1, ward.name, host.name)
                .withCardAttachedTo(2, otherWard.name, host.name).build()
            val first = g.findPermanent(ward.name)!!
            val second = g.findPermanent(otherWard.name)!!
            val r = UnattachedAurasCheck(cardRegistry).check(g.state)
            (first in r.state.getBattlefield()) shouldBe false
            (second in r.state.getBattlefield()) shouldBe false
        }
        test("independent protection from the same source still removes its Aura") {
            val g = scenario().withPlayers("A", "B")
                .withCardOnBattlefield(1, host.name)
                .withCardAttachedTo(1, ward.name, host.name).build()
            val h = g.findPermanent(host.name)!!
            val a = g.findPermanent(ward.name)!!
            val independent = ActiveFloatingEffect(
                id = EntityId.generate(),
                effect = FloatingEffectData(layer = Layer.ABILITY,
                    modification = SerializableModification.GrantProtectionFromColor("WHITE"),
                    affectedEntities = setOf(h)),
                duration = Duration.EndOfTurn, sourceId = a,
                controllerId = g.player1Id, timestamp = 100L
            )
            val r = UnattachedAurasCheck(cardRegistry).check(g.state.copy(floatingEffects = listOf(independent)))
            (a in r.state.getBattlefield()) shouldBe false
        }

        test("innate protection is an independent non-exempt instance") {
            val g = scenario().withPlayers("A", "B")
                .withCardOnBattlefield(1, host.name)
                .withCardAttachedTo(1, ward.name, host.name).build()
            val h = g.findPermanent(host.name)!!
            val a = g.findPermanent(ward.name)!!
            val state = g.state.updateEntity(h) { it.with(
                com.wingedsheep.engine.state.components.identity.ProtectionComponent(setOf(Color.WHITE))
            ) }
            val r = UnattachedAurasCheck(cardRegistry).check(state)
            (a in r.state.getBattlefield()) shouldBe false
        }

    }
}
