package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.file.Files
import java.nio.file.Path

/**
 * R20: initialization ONLY. No ActionProcessor, AI players, combat, or game turns.
 * The input swap is performed in memory using the accepted R19 helper.
 */
private const val R20_CONTROL = "experiments/manual-transmission/control/v0.7.decklist.txt"
private const val R20_PUBLISHED_WAN = "gameplay/manual-v08-wan/CHALLENGER.decklist.txt"
private const val R20_BESTIARY = "Lifecrafter’s Bestiary"
private const val R20_WAN = "Wan Shi Tong, Librarian"
private const val R20_COMMANDER = "Animar, Soul of Elements"
private const val R20_SEED = 202610191620L

private fun r20DeckNames(relativePath: String): List<String> {
    val root = Path.of(requireNotNull(System.getenv("MANUAL_R4_ROOT")) {
        "MANUAL_R4_ROOT must point at the original repository root"
    })
    val lines = Files.readAllLines(root.resolve(relativePath))
    return lines.flatMap { line ->
        Regex("^(\\d+) (.+)$").matchEntire(line)?.let { match ->
            List(match.groupValues[1].toInt()) { match.groupValues[2] }
        } ?: emptyList()
    }
}

private fun r20Registry(): CardRegistry = CardRegistry().apply {
    MtgSetCatalog.all.forEach { set ->
        register(set.cards)
        register(set.basicLands)
    }
}

private fun r20Initialize(
    rawNames: List<String>,
    registry: CardRegistry,
): GameState {
    // Mirror/R18 canonicalization is necessary: typographic apostrophes in the
    // frozen decklist are not the spellings in the compiled CardRegistry.
    val names = rawNames.map(ManualMirrorInput::resolve)
    names.size shouldBe 100
    names.count { it == R20_COMMANDER } shouldBe 1
    val deck = Deck(names.filter { it != R20_COMMANDER }, commander = R20_COMMANDER)
    deck.cards.size shouldBe 99
    return GameInitializer(registry).initializeGame(
        GameConfig(
            players = (0..3).map { seat ->
                PlayerConfig(
                    "Animar-$seat",
                    deck,
                    startingLife = 40,
                    playerId = EntityId("manual-mirror-$seat"),
                    commanderCardName = R20_COMMANDER,
                )
            },
            format = Format.Commander(),
            seed = R20_SEED,
            startingPlayerIndex = 0,
            skipMulligans = true,
            useHandSmoother = false,
        )
    ).state
}

private fun r20ZoneNames(state: GameState, seat: Int, zone: Zone): List<String> {
    val playerId = EntityId("manual-mirror-$seat")
    return state.getZone(ZoneKey(playerId, zone)).map { entity ->
        state.getEntity(entity)?.get<CardComponent>()?.name
            ?: error("Non-card / missing card identity in $zone at seat $seat: $entity")
    }
}

class ManualR20MatchedSlotInitializerTest : FunSpec({
    test("exact-slot replacement preserves 98 common library cards after real seeded shuffle") {
        val rawControl = r20DeckNames(R20_CONTROL)
        val rawMatched = ManualR19MatchedSlotHelper.matchedChallengerNames(
            rawControl, R20_BESTIARY, R20_WAN
        )
        rawControl.size shouldBe 100
        rawControl.indices.count { rawControl[it] != rawMatched[it] } shouldBe 1
        rawMatched.indexOf(R20_WAN) shouldBe rawControl.indexOf(R20_BESTIARY)

        val registry = r20Registry()
        val control = r20Initialize(rawControl, registry)
        val matched = r20Initialize(rawMatched, registry)

        control.turnOrder shouldBe matched.turnOrder
        val bestiary = ManualMirrorInput.resolve(R20_BESTIARY)
        val wan = ManualMirrorInput.resolve(R20_WAN)
        (0..3).forEach { seat ->
            val id = EntityId("manual-mirror-$seat")
            val controlLib = r20ZoneNames(control, seat, Zone.LIBRARY)
            val matchedLib = r20ZoneNames(matched, seat, Zone.LIBRARY)
            val controlHand = r20ZoneNames(control, seat, Zone.HAND)
            val matchedHand = r20ZoneNames(matched, seat, Zone.HAND)
            val controlFull = controlLib + controlHand
            val matchedFull = matchedLib + matchedHand

            controlLib.size shouldBe 92
            matchedLib.size shouldBe 92
            controlHand.size shouldBe 7
            matchedHand.size shouldBe 7
            controlFull.size shouldBe 99
            matchedFull.size shouldBe 99
            controlFull.count { it == bestiary } shouldBe 1
            matchedFull.count { it == wan } shouldBe 1

            // Actual zone indices must align, not merely the shared multiset.
            // IDs are allocated by deck-input slot; identical ID ordering shows
            // the actual seeded shuffle used the same index permutation.
            control.getZone(ZoneKey(id, Zone.LIBRARY)) shouldBe
                matched.getZone(ZoneKey(id, Zone.LIBRARY))
            control.getZone(ZoneKey(id, Zone.HAND)) shouldBe
                matched.getZone(ZoneKey(id, Zone.HAND))
            val differingLibrary = controlLib.indices.count { controlLib[it] != matchedLib[it] }
            val differingHand = controlHand.indices.count { controlHand[it] != matchedHand[it] }
            (differingLibrary + differingHand) shouldBe 1
            controlFull.filter { it != bestiary } shouldBe matchedFull.filter { it != wan }
            controlFull.filter { it != bestiary }.size shouldBe 98
            r20ZoneNames(control, seat, Zone.COMMAND) shouldBe listOf(R20_COMMANDER)
            r20ZoneNames(matched, seat, Zone.COMMAND) shouldBe listOf(R20_COMMANDER)
        }
        println("R20_MATCHED_INITIALIZER_PASS seats=4 shared_library_cards=98 substitutions_per_seat=1 seed=$R20_SEED")
    }

    test("original section-ordered Wan challenger fails exact post-shuffle position matching") {
        val rawControl = r20DeckNames(R20_CONTROL)
        val rawPublished = r20DeckNames(R20_PUBLISHED_WAN)
        val rawMatched = ManualR19MatchedSlotHelper.matchedChallengerNames(
            rawControl, R20_BESTIARY, R20_WAN
        )
        rawPublished.size shouldBe 100
        rawPublished.sorted() shouldBe rawMatched.sorted()
        // Crucial R19 discovery: shared relative order BEFORE shuffle was the same;
        // the placement at five shifted indices was different. Don't assert otherwise.
        rawControl.filter { it != R20_BESTIARY } shouldBe
            rawPublished.filter { it != R20_WAN }
        rawControl.indices.count { rawControl[it] != rawPublished[it] } shouldBe 6
        rawPublished.indexOf(R20_WAN) shouldNotBe rawControl.indexOf(R20_BESTIARY)

        val registry = r20Registry()
        val control = r20Initialize(rawControl, registry)
        val published = r20Initialize(rawPublished, registry)

        control.turnOrder shouldBe published.turnOrder
        val bestiary = ManualMirrorInput.resolve(R20_BESTIARY)
        val wan = ManualMirrorInput.resolve(R20_WAN)
        (0..3).forEach { seat ->
            val controlLib = r20ZoneNames(control, seat, Zone.LIBRARY)
            val publishedLib = r20ZoneNames(published, seat, Zone.LIBRARY)
            val controlHand = r20ZoneNames(control, seat, Zone.HAND)
            val publishedHand = r20ZoneNames(published, seat, Zone.HAND)
            val controlFull = controlLib + controlHand
            val publishedFull = publishedLib + publishedHand
            controlFull.size shouldBe 99
            publishedFull.size shouldBe 99
            val mismatches = controlFull.indices.count { controlFull[it] != publishedFull[it] }
            mismatches shouldBe 6
            // The shared card order after shuffle is generally altered by the
            // six mismatched deck positions, even though its *input* order was shared.
            controlFull.filter { it != bestiary } shouldNotBe
                publishedFull.filter { it != wan }
        }
        println("R20_R18_NEGATIVE_CONTROL_PASS seats=4 mismatched_library_slots_per_seat=6 seed=$R20_SEED")
    }
})
