package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.BattlefieldEntryTimestampComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.TargetsComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class RetargetHistoryTest : FunSpec({
    fun id(n: String) = EntityId(n)
    fun target(n: String) = ChosenTarget.Permanent(id(n))
    val owner = id("owner")
    fun board() = GameState(
        entities = listOf("a", "b", "c").associate { id(it) to ComponentContainer.of(BattlefieldEntryTimestampComponent(9L)) },
        zones = mapOf(ZoneKey(owner, Zone.BATTLEFIELD) to listOf(id("a"), id("b"), id("c"))),
    )
    fun original() = TargetsComponent(listOf(target("a"), target("b")), targetEntryStamps = mapOf(id("a") to 1L, id("b") to 2L))
    test("RH01 changed recipient captures its current visit while unchanged target retains its old visit") {
        val q = requireNotNull(original().withRetargetedHistory(board(), listOf(target("a"), target("c"))))
        q.targetEntryStamps shouldBe mapOf(id("a") to 1L, id("c") to 9L)
    }
    test("RH02 keeping every occurrence returns the exact original component") {
        val p = original()
        (p.withRetargetedHistory(board(), p.targets) === p) shouldBe true
    }
    test("RH03 untracked unchanged history remains absent") {
        val p = original().copy(targetEntryStamps = emptyMap())
        requireNotNull(p.withRetargetedHistory(board(), listOf(target("a"), target("c")))).targetEntryStamps shouldBe mapOf(id("c") to 9L)
    }
    test("RH04 repeated physical id with different visits is rejected instead of collapsed") {
        original().withRetargetedHistory(board(), listOf(target("a"), target("a"))) shouldBe null
    }
    test("RH05 known and unknown occurrences of one physical id cannot be merged") {
        original().copy(targetEntryStamps = emptyMap()).withRetargetedHistory(board(), listOf(target("a"), target("a"))) shouldBe null
    }
    test("RH06 representable repeated current visit is retained without global deduplication") {
        val p = original().copy(targetEntryStamps = mapOf(id("a") to 9L, id("b") to 2L))
        val q = requireNotNull(p.withRetargetedHistory(board(), listOf(target("a"), target("a"))))
        q.targets shouldBe listOf(target("a"), target("a"))
        q.targetEntryStamps shouldBe mapOf(id("a") to 9L)
    }
    test("RH07 player replacements remove only the departed permanent occurrence stamp") {
        val q = requireNotNull(original().withRetargetedHistory(board(), listOf(target("a"), ChosenTarget.Player(owner))))
        q.targetEntryStamps shouldBe mapOf(id("a") to 1L)
    }
    test("RH08 changing target count is rejected") {
        original().withRetargetedHistory(board(), listOf(target("a"))) shouldBe null
    }
    test("RH09 unchanged input and requirements survive an accepted update") {
        val p = original(); val before = Json.encodeToString(TargetsComponent.serializer(), p)
        val q = requireNotNull(p.withRetargetedHistory(board(), listOf(target("a"), target("c"))))
        Json.encodeToString(TargetsComponent.serializer(), p) shouldBe before
        (q.targetRequirements === p.targetRequirements) shouldBe true
    }
    test("RH10 serialization preserves old visit and newly captured visit exactly") {
        val q = requireNotNull(original().withRetargetedHistory(board(), listOf(target("a"), target("c"))))
        val json = Json { encodeDefaults = true }
        val bytes = json.encodeToString(TargetsComponent.serializer(), q)
        val loaded = json.decodeFromString(TargetsComponent.serializer(), bytes)
        loaded shouldBe q
        json.encodeToString(TargetsComponent.serializer(), loaded) shouldBe bytes
    }
    test("RH11 missing replacement permanent is not assigned a made-up battlefield visit") {
        val q = requireNotNull(original().withRetargetedHistory(board(), listOf(target("a"), target("missing"))))
        q.targetEntryStamps shouldBe mapOf(id("a") to 1L)
    }
    test("RH12 later changes preserve earlier unchanged history across multiple updates") {
        val q = requireNotNull(original().withRetargetedHistory(board(), listOf(target("a"), target("c"))))
        val r = requireNotNull(q.withRetargetedHistory(board(), listOf(target("a"), target("b"))))
        r.targetEntryStamps shouldBe mapOf(id("a") to 1L, id("b") to 9L)
        q.targetEntryStamps shouldBe mapOf(id("a") to 1L, id("c") to 9L)
    }
})
