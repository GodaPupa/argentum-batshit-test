package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.TargetsComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.targets.TargetPlayer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AnnouncedTargetGroupsTest : FunSpec({
    val requirements = listOf(TargetPlayer(optional = true, id = "first"), TargetPlayer(optional = true, id = "second"))
    val player = EntityId.of("player")
    val target = ChosenTarget.Player(player)
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }

    test("omitted optional group reserves following required cardinality without inspecting targets") {
        AnnouncedTargetGroups.counts(listOf(TargetPlayer(optional = true), TargetPlayer()), 1) shouldBe listOf(0, 1)
    }
    test("ambiguous flat declaration requires explicit group announcement") {
        AnnouncedTargetGroups.counts(requirements, 1) shouldBe null
        AnnouncedTargetGroups.counts(requirements, 1, listOf(0, 1)) shouldBe listOf(0, 1)
        AnnouncedTargetGroups.counts(requirements, 1, listOf(1, 0)) shouldBe listOf(1, 0)
    }
    test("invalid announced counts are rejected") {
        AnnouncedTargetGroups.counts(requirements, 1, listOf(-1, 2)) shouldBe null
        AnnouncedTargetGroups.counts(requirements, 1, listOf(1)) shouldBe null
        AnnouncedTargetGroups.counts(requirements, 1, listOf(1, 1)) shouldBe null
    }
    test("cast declaration serializes and survives payment action copying") {
        val action = CastSpell(player, EntityId.of("spell"), listOf(target), announcedTargetCounts = listOf(0, 1))
        val resumed = json.decodeFromString<CastSpell>(json.encodeToString(action)).copy(xValue = 2)
        resumed.announcedTargetCounts shouldBe listOf(0, 1)
    }
    test("stack targeting component preserves explicit group identity when target legality later changes") {
        val captured = TargetsComponent.capture(GameState(), listOf(target), requirements, listOf(0, 1))
        val restored = json.decodeFromString<TargetsComponent>(json.encodeToString(captured))
        restored.announcedTargetCounts shouldBe listOf(0, 1)
        restored.copy(targets = listOf(ChosenTarget.Player(EntityId.of("another")))).announcedTargetCounts shouldBe listOf(0, 1)
    }
})
