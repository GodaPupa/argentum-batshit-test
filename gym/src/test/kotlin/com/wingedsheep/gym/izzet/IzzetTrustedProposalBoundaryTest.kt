package com.wingedsheep.gym.izzet

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonPrimitive

/** Parameter-free synthetic fixtures only. No Izzet strategy or opponent package is represented. */
class IzzetTrustedProposalBoundaryTest : FunSpec({
    val registry = CardRegistry().apply { register(TestCards.all) }
    val processor = ActionProcessor(registry)
    val pins = IzzetSourcePins("1".repeat(40), "2".repeat(64), "3".repeat(64), "4".repeat(64))
    fun initial(opening: Boolean = false): GameState =
        GameInitializer(registry).initializeGame(GameConfig(
            players = listOf(PlayerConfig("Synthetic actor", Deck(List(60) { "Forest" })),
                PlayerConfig("Excluded synthetic seat", Deck(List(60) { "Island" }))),
            startingPlayerIndex = 0, skipMulligans = !opening, useHandSmoother = false,
            seed = 0x495A5A45545052L)).state.let {
                if (opening) it else it.copy(phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN)
            }
    fun open(state: GameState) = IzzetTrustedProposalBoundary.open(registry, state, pins, "synthetic-izzet-projection", 0)
    fun proposal(boundary: IzzetTrustedProposalBoundary, kind: String): IzzetNumberedProposal {
        val menu = boundary.pilotMenu()
        return IzzetNumberedProposal(menu.windowSha256, menu.offers.first { it.actionKind == kind }.id)
    }
    fun resolve(b: IzzetTrustedProposalBoundary, p: IzzetNumberedProposal, s: GameState,
                current: IzzetSourcePins = pins, seq: Long = 0) =
        b.resolve(p, s, current, "synthetic-izzet-projection", seq)

    test("trusted land materialization corresponds to the legal current action and is engine accepted") {
        val s = initial(); val b = open(s)
        val action = resolve(b, proposal(b, "PlayLand"), s).shouldBeInstanceOf<PlayLand>()
        (action.cardId in s.getHand(action.playerId)) shouldBe true
        processor.process(s, action).result.error shouldBe null
        shouldThrowAny { resolve(b, proposal(b, "PassPriority"), s) }
    }
    test("pass and opening keep and mulligan are materialized and engine accepted") {
        for ((s, kind) in listOf(initial() to "PassPriority", initial(true) to "KeepHand",
                initial(true) to "TakeMulligan")) {
            val b = open(s)
            processor.process(s, resolve(b, proposal(b, kind), s)).result.error shouldBe null
        }
    }
    test("stale visible observation consumes the boundary") {
        val s = initial(); val b = open(s); val p = proposal(b, "PassPriority")
        shouldThrowAny { resolve(b, p, s.copy(turnNumber = s.turnNumber + 1)) }
        shouldThrowAny { resolve(b, p, s) }
    }
    test("changed action menu cannot authorize an old land proposal") {
        val s = initial(); val b = open(s); val p = proposal(b, "PlayLand")
        val action = resolve(open(s), p, s)
        val next = processor.process(s, action).result.state
        shouldThrowAny { resolve(b, p, next) }
    }
    test("private hand and library identities and order never enter pilot projection") {
        val s = initial(); val actor = requireNotNull(s.priorityPlayerId)
        val other = s.turnOrder.single { it != actor }
        val menu = open(s).pilotMenu()
        val encoded = menu.maskedObservation + menu.offers.joinToString { it.canonicalAction }
        for (id in s.getHand(other) + s.getLibrary(other) + s.getLibrary(actor))
            encoded.contains(JsonPrimitive(id.value).toString()) shouldBe false
        encoded.contains("\"rng\"") shouldBe false
        encoded.contains("\"continuationStack\"") shouldBe false
        encoded.contains("\"entities\"") shouldBe false
        encoded.contains("Island") shouldBe false
        val changed = s.copy(zones = s.zones +
            (ZoneKey(other, Zone.HAND) to s.getHand(other).reversed()) +
            (ZoneKey(actor, Zone.LIBRARY) to s.getLibrary(actor).reversed()))
        open(changed).pilotMenu() shouldBe menu
    }
    test("source deck policy and runtime drift each reject") {
        val s = initial()
        for (changed in listOf(pins.copy(sourceCommit = "5".repeat(40)),
            pins.copy(deckSha256 = "6".repeat(64)), pins.copy(policySha256 = "7".repeat(64)),
            pins.copy(runtimeSha256 = "8".repeat(64)))) {
            val b = open(s)
            shouldThrowAny { resolve(b, proposal(b, "PlayLand"), s, changed) }
        }
    }
    test("actor priority and decision epoch drift reject") {
        val s = initial(); val b = open(s); val p = proposal(b, "PassPriority")
        shouldThrowAny { resolve(b, p, s.copy(priorityPlayerId = s.turnOrder.last())) }
        val c = open(s)
        shouldThrowAny { resolve(c, proposal(c, "PassPriority"), s, seq = 1) }
    }
    test("unknown offer and wrong window cannot be used or retried") {
        val s = initial()
        for (invalid in listOf(IzzetNumberedProposal(open(s).pilotMenu().windowSha256, Int.MAX_VALUE),
            IzzetNumberedProposal("0".repeat(64), 0))) {
            val b = open(s)
            shouldThrowAny { resolve(b, invalid, s) }
            shouldThrowAny { resolve(b, proposal(b, "PassPriority"), s) }
        }
    }
    test("unsupported affordable activation fails closed after a land enters the battlefield") {
        val s = initial(); val b = open(s)
        val next = processor.process(s, resolve(b, proposal(b, "PlayLand"), s)).result.state
        shouldThrowAny { open(next) }
    }
    test("editing pilot menu kind and payload cannot alter captured trusted materialization") {
        val s = initial(); val b = open(s); val p = proposal(b, "PlayLand")
        val menu = b.pilotMenu()
        val altered = menu.copy(offers = menu.offers.map {
            it.copy(actionKind = "CastSpell", canonicalAction = "{invalid private injection}")
        })
        altered.offers.all { it.actionKind == "CastSpell" } shouldBe true
        val action = resolve(b, p, s).shouldBeInstanceOf<PlayLand>()
        processor.process(s, action).result.error shouldBe null
    }
})
