package com.wingedsheep.ai.arena

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.mechanics.targeting.TargetSlotPlan
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ActionRecordingCompatibilityTest : FunSpec({
    test("RC01 absent provenance preserves every legacy action field") {
        val action = CastSpell(
            playerId = EntityId("player"), cardId = EntityId("card"), xValue = 7,
            splicedCardIds = listOf(EntityId("splice")), chosenModes = listOf(2, 0),
        )
        TableGameRunner.recordedActionText(action) shouldBe
            "CastSpell(playerId=player, cardId=card, targets=[], xValue=7, " +
            "paymentStrategy=AutoPay, alternativePayment=null, additionalCostPayment=null, " +
            "castFaceDown=false, declaredCostSlot=null, wasWaterbendPaid=false, giftRecipient=null, " +
            "splicedCardIds=[splice], damageDistribution=null, castForPrototype=false, " +
            "useAlternativeCost=false, chosenModes=[2, 0], modalSelectionCompleted=false, " +
            "modeTargetsOrdered=[], modeDamageDistribution={}, graveyardLifeCost=0, " +
            "graveyardCastRider=null, conspiredCreatures=[], casualtyCreature=null, faceIndex=null, " +
            "useWithoutPayingManaCost=false, alternativeCostType=null)"
    }

    test("RC02 explicit public groups remain fully recorded including empty groups") {
        val action = CastSpell(EntityId("player"), EntityId("card"), targetGroups = emptyList())
        for (withGroups in listOf(action, action.copy(targetGroups = listOf(emptyList())))) {
            TableGameRunner.recordedActionText(withGroups) shouldBe withGroups.toString()
        }
    }

    test("RC03 server provenance remains fully recorded with or without public groups") {
        val action = CastSpell(
            EntityId("player"), EntityId("card"), originalTargetSlots = TargetSlotPlan(emptyList()),
        )
        for (withHistory in listOf(action, action.copy(targetGroups = emptyList()))) {
            TableGameRunner.recordedActionText(withHistory) shouldBe withHistory.toString()
        }
    }

    test("RC04 other actions and embedded diagnostic text remain unchanged") {
        val id = EntityId("literal, targetGroups=null, originalTargetSlots=null)")
        val pass = PassPriority(id)
        TableGameRunner.recordedActionText(pass) shouldBe pass.toString()
        val cast = CastSpell(EntityId("player"), id)
        TableGameRunner.recordedActionText(cast).substringBefore(", targets=") shouldBe
            "CastSpell(playerId=player, cardId=$id"
    }
})
