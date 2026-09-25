package com.wingedsheep.engine.handlers.effects

import com.wingedsheep.engine.core.PermanentAttachedEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.ObjectReferenceEnvironment
import com.wingedsheep.engine.handlers.effects.permanent.attachments.AttachEquipmentExecutor
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.AttachmentsComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.effects.AttachEquipmentEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AttachEquipmentObjectIdentityTest : FunSpec({
    val equipmentCard = card("Attachment Identity Equipment") {
        manaCost = "{2}"
        typeLine = "Artifact — Equipment"
    }
    fun fixture() = GameTestDriver().apply {
        registerCards(TestCards.all + equipmentCard)
        initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    for (intervention in listOf("none", "leave", "return")) {
        test("attachment source identity remains valid only in its captured battlefield visit: $intervention") {
            val d = fixture()
            val me = d.activePlayer!!
            val equipment = d.putPermanentOnBattlefield(me, equipmentCard.name)
            val host = d.putCreatureOnBattlefield(me, "Centaur Courser")
            val ref = d.state.objectRef(equipment)!!
            val context = EffectContext(sourceId = equipment, controllerId = me,
                objectReferences = ObjectReferenceEnvironment(captured = true, origin = ref, source = ref))
            if (intervention != "none") {
                d.replaceState(d.state.moveToZone(equipment, ZoneKey(me, Zone.BATTLEFIELD), ZoneKey(me, Zone.EXILE)))
            }
            if (intervention == "return") {
                d.replaceState(d.state.moveToZone(equipment, ZoneKey(me, Zone.EXILE), ZoneKey(me, Zone.BATTLEFIELD)))
            }
            val prior = d.state
            val result = AttachEquipmentExecutor().execute(prior,
                AttachEquipmentEffect(EffectTarget.SpecificEntity(host)), context)
            result.isSuccess shouldBe true
            if (intervention == "none") {
                result.state.getEntity(equipment)!!.get<AttachedToComponent>()!!.targetId shouldBe host
                result.state.getEntity(host)!!.get<AttachmentsComponent>()!!.attachedIds shouldBe listOf(equipment)
                result.events.filterIsInstance<PermanentAttachedEvent>().size shouldBe 1
            } else {
                result.state shouldBe prior
                result.events shouldBe emptyList()
            }
        }
    }
    test("a legacy raw source in another zone cannot acquire battlefield attachments") {
        val d = fixture()
        val me = d.activePlayer!!
        val equipment = d.putCardInHand(me, equipmentCard.name)
        val host = d.putCreatureOnBattlefield(me, "Centaur Courser")
        val context = EffectContext(sourceId = equipment, controllerId = me)
        val result = AttachEquipmentExecutor().execute(d.state,
            AttachEquipmentEffect(EffectTarget.SpecificEntity(host)), context)
        result.isSuccess shouldBe true
        result.state shouldBe d.state
        result.events shouldBe emptyList()
    }
    test("iteration Self binding does not replace the originating Equipment") {
        val d = fixture()
        val me = d.activePlayer!!
        val equipment = d.putPermanentOnBattlefield(me, equipmentCard.name)
        val host = d.putCreatureOnBattlefield(me, "Centaur Courser")
        val iterated = d.putCreatureOnBattlefield(me, "Grizzly Bears")
        val ref = d.state.objectRef(equipment)!!
        val context = EffectContext(sourceId = equipment, controllerId = me,
            objectReferences = ObjectReferenceEnvironment(captured = true, origin = ref, source = ref,
                selfBinding = com.wingedsheep.engine.handlers.CapturedObjectBinding(iterated, d.state.objectRef(iterated))))
        val result = AttachEquipmentExecutor().execute(d.state,
            AttachEquipmentEffect(EffectTarget.SpecificEntity(host)), context)
        result.isSuccess shouldBe true
        result.state.getEntity(equipment)!!.get<AttachedToComponent>()!!.targetId shouldBe host
        result.state.getEntity(iterated)!!.get<AttachedToComponent>() shouldBe null
        result.state.getEntity(host)!!.get<AttachmentsComponent>()!!.attachedIds shouldBe listOf(equipment)
    }
    test("an unavailable destination is a successful no-op and emits no attachment event") {
        val d = fixture()
        val me = d.activePlayer!!
        val equipment = d.putPermanentOnBattlefield(me, equipmentCard.name)
        val absentHost = d.putCardInHand(me, "Centaur Courser")
        val context = EffectContext(sourceId = equipment, controllerId = me)
        val result = AttachEquipmentExecutor().execute(d.state,
            AttachEquipmentEffect(EffectTarget.SpecificEntity(absentHost)), context)
        result.isSuccess shouldBe true
        result.state shouldBe d.state
        result.events shouldBe emptyList()
    }
})
