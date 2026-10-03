package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.identity.ProtectionComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.cmr.cards.BenevolentBlessing
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.wingedsheep.engine.state.GameState

class BenevolentBlessingScenarioTest : ScenarioTestBase() {
    private val equipment = card("Blessing White Equipment") {
        manaCost = "{W}"
        typeLine = "Artifact — Equipment"
    }
    private fun TestGame.bless(color: Color = Color.WHITE, host: String = "Grizzly Bears") {
        castSpell(1, BenevolentBlessing.name, findPermanent(host)!!).error shouldBe null
        resolveStack()
        val choice = state.pendingDecision.shouldBeInstanceOf<ChooseColorDecision>()
        submitDecision(ColorChosenResponse(choice.id, color)).error shouldBe null
        resolveStack()
    }
    private fun board() = scenario().withPlayers("A", "B")
        .withCardOnBattlefield(1, "Grizzly Bears")
        .withCardInHand(1, BenevolentBlessing.name)
        .withLandsOnBattlefield(1, "Plains", 4)

    init {
        cardRegistry.register(BenevolentBlessing)
        cardRegistry.register(equipment)
        test("chooses color on entry and source Aura retains itself") {
            val g = board().build()
            g.bless()
            val host = g.findPermanent("Grizzly Bears")!!
            val aura = g.findPermanent(BenevolentBlessing.name)!!
            g.state.projectedState.hasKeyword(host, "PROTECTION_FROM_WHITE") shouldBe true
            g.state.projectedState.hasKeyword(host, "PROTECTION_FROM_BLUE") shouldBe false
            g.state.getEntity(aura)?.get<AttachedToComponent>()?.targetId shouldBe host
        }
        test("preexisting controlled Aura and Equipment remain attached") {
            val g = board().withCardAttachedTo(1, "Holy Strength", "Grizzly Bears")
                .withCardAttachedTo(1, equipment.name, "Grizzly Bears").build()
            g.bless()
            val host = g.findPermanent("Grizzly Bears")!!
            for (name in listOf("Holy Strength", equipment.name, BenevolentBlessing.name)) {
                val id = g.findPermanent(name)!!
                g.state.getEntity(id)?.get<AttachedToComponent>()?.targetId shouldBe host
            }
        }
        test("opponent controlled same-color Aura and Equipment are removed") {
            val g = board().withCardAttachedTo(2, "Holy Strength", "Grizzly Bears")
                .withCardAttachedTo(2, equipment.name, "Grizzly Bears").build()
            val eq = g.findPermanent(equipment.name)!!
            g.bless()
            g.findPermanent("Holy Strength") shouldBe null
            g.state.getEntity(eq)?.get<AttachedToComponent>() shouldBe null
            g.findPermanent(BenevolentBlessing.name) shouldNotBe null
        }
        test("later attachment does not inherit the captured exemption") {
            val g = board().withCardOnBattlefield(1, equipment.name).build()
            g.bless()
            val eq = g.findPermanent(equipment.name)!!
            g.state = g.state.updateEntity(eq) { it.with(AttachedToComponent(g.findPermanent("Grizzly Bears")!!)) }
            g.checkStateBasedActions().error shouldBe null
            g.state.getEntity(eq)?.get<AttachedToComponent>() shouldBe null
        }
        test("unrelated same-color protection removes even the retained source and attachments") {
            val g = board().withCardAttachedTo(1, "Holy Strength", "Grizzly Bears")
                .withCardAttachedTo(1, equipment.name, "Grizzly Bears").build()
            g.bless()
            val host = g.findPermanent("Grizzly Bears")!!
            val eq = g.findPermanent(equipment.name)!!
            g.state = g.state.updateEntity(host) { it.with(ProtectionComponent(setOf(Color.WHITE))) }
            g.checkStateBasedActions().error shouldBe null
            g.findPermanent(BenevolentBlessing.name) shouldBe null
            g.findPermanent("Holy Strength") shouldBe null
            g.state.getEntity(eq)?.get<AttachedToComponent>() shouldBe null
        }
        test("protection still rejects ordinary targeting by chosen-color spells") {
            val g = board().withCardInHand(1, "Holy Strength").build()
            g.bless()
            g.castSpell(1, "Holy Strength", g.findPermanent("Grizzly Bears")!!).error shouldNotBe null
        }
        test("chosen-color nontargeted damage is prevented") {
            val g = board().withCardInHand(1, "Pyroclasm")
                .withLandsOnBattlefield(1, "Mountain", 2).build()
            g.bless(Color.RED)
            val host = g.findPermanent("Grizzly Bears")!!
            g.castSpell(1, "Pyroclasm").error shouldBe null
            g.resolveStack()
            g.findPermanent("Grizzly Bears") shouldBe host
            (g.state.getEntity(host)?.get<DamageComponent>()?.amount ?: 0) shouldBe 0
        }
        test("chosen-color creatures cannot block the enchanted creature") {
            val g = board().withCardOnBattlefield(2, "Glory Seeker").build()
            g.bless()
            g.advanceToPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            g.declareAttackers(mapOf("Grizzly Bears" to 2)).error shouldBe null
            g.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
            g.declareBlockers(mapOf("Glory Seeker" to listOf("Grizzly Bears"))).error shouldNotBe null
        }
        test("reentering on a new host does not retain the old visit attachment exemption") {
            val g = board().withCardAttachedTo(1, "Holy Strength", "Grizzly Bears")
                .withCardOnBattlefield(1, "Centaur Courser")
                .withCardInHand(1, "Boomerang").withLandsOnBattlefield(1, "Island", 2).build()
            g.bless()
            val oldAura = g.findPermanent(BenevolentBlessing.name)!!
            val oldVisit = g.state.objectRef(oldAura)!!
            val strength = g.findPermanent("Holy Strength")!!
            g.castSpell(1, "Boomerang", oldAura).error shouldBe null
            g.resolveStack()
            g.bless(host = "Centaur Courser")
            g.state.objectRef(g.findPermanent(BenevolentBlessing.name)!!) shouldNotBe oldVisit
            val newHost = g.findPermanent("Centaur Courser")!!
            g.state = g.state.updateEntity(strength) { it.with(AttachedToComponent(newHost)) }
            g.checkStateBasedActions().error shouldBe null
            g.findPermanent("Holy Strength") shouldBe null
            g.findPermanent(BenevolentBlessing.name) shouldNotBe null
        }
        test("serialized state preserves captured identities without admitting later equipment") {
            val g = board().withCardAttachedTo(1, "Holy Strength", "Grizzly Bears")
                .withCardOnBattlefield(1, equipment.name).build()
            g.bless()
            val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
            g.state = json.decodeFromString<GameState>(json.encodeToString(g.state))
            val eq = g.findPermanent(equipment.name)!!
            g.state = g.state.updateEntity(eq) { it.with(AttachedToComponent(g.findPermanent("Grizzly Bears")!!)) }
            g.checkStateBasedActions().error shouldBe null
            g.findPermanent("Holy Strength") shouldNotBe null
            g.state.getEntity(eq)?.get<AttachedToComponent>() shouldBe null
        }
    }
}
