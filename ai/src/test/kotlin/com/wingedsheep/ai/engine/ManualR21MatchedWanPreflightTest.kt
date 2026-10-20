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
 * R21 prospective sample PREPARATION ONLY. Run the actual GameInitializer to
 * verify opening state, not any ActionProcessor, AI choice, or game progression.
 * Future full Commander games require a separate explicit authorization.
 */
private const val R21_CONTROL_PATH = "experiments/manual-transmission/control/v0.7.decklist.txt"
private const val R21_PUBLISHED_WAN_PATH = "gameplay/manual-v08-wan/CHALLENGER.decklist.txt"
private const val R21_BESTIARY = "Lifecrafter’s Bestiary"
private const val R21_WAN = "Wan Shi Tong, Librarian"
private const val R21_COMMANDER = "Animar, Soul of Elements"

private fun r21DeckNames(path: String): List<String> {
    val root = Path.of(requireNotNull(System.getenv("MANUAL_R4_ROOT")) {
        "R21 needs MANUAL_R4_ROOT for the frozen, immutable input decklists"
    })
    return Files.readAllLines(root.resolve(path)).flatMap { line ->
        Regex("^(\\d+) (.+)$").matchEntire(line)?.let { match ->
            List(match.groupValues[1].toInt()) { match.groupValues[2] }
        } ?: emptyList()
    }
}

private fun r21Registry() = CardRegistry().apply {
    MtgSetCatalog.all.forEach { set ->
        register(set.cards)
        register(set.basicLands)
    }
}

private fun r21Initialize(rawNames: List<String>, seed: Long, registry: CardRegistry): GameState {
    // Frozen v0.7 uses typographic apostrophes; the compiled catalog uses
    // canonical names. Preserve the proven R18/R20 normalization.
    val names = rawNames.map(ManualMirrorInput::resolve)
    names.size shouldBe 100
    names.count { it == R21_COMMANDER } shouldBe 1
    val deck = Deck(names.filter { it != R21_COMMANDER }, commander = R21_COMMANDER)
    deck.cards.size shouldBe 99
    return GameInitializer(registry).initializeGame(
        GameConfig(
            players = (0..3).map { seat ->
                PlayerConfig(
                    "Animar-$seat",
                    deck,
                    startingLife = 40,
                    playerId = EntityId("manual-mirror-$seat"),
                    commanderCardName = R21_COMMANDER,
                )
            },
            format = Format.Commander(),
            seed = seed,
            startingPlayerIndex = 0,
            skipMulligans = true,
            useHandSmoother = false,
        )
    ).state
}

private fun r21ZoneNames(state: GameState, seat: Int, zone: Zone): List<String> {
    val owner = EntityId("manual-mirror-$seat")
    return state.getZone(ZoneKey(owner, zone)).map { cardId ->
        state.getEntity(cardId)?.get<CardComponent>()?.name
            ?: error("R21 non-card entity in seat $seat $zone: $cardId")
    }
}

class ManualR21MatchedWanPreflightTest : FunSpec({
    listOf(202610191621L, 202610191622L).forEach { seed ->
        test("seed $seed exact matched-slot initializer preflight across four seats") {
            val rawControl = r21DeckNames(R21_CONTROL_PATH)
            val rawMatched = ManualR19MatchedSlotHelper.matchedChallengerNames(
                rawControl, R21_BESTIARY, R21_WAN
            )
            rawControl.size shouldBe 100
            rawControl.count { it == R21_BESTIARY } shouldBe 1
            rawControl.indices.count { rawControl[it] != rawMatched[it] } shouldBe 1
            rawMatched.indexOf(R21_WAN) shouldBe rawControl.indexOf(R21_BESTIARY)

            val registry = r21Registry()
            val control = r21Initialize(rawControl, seed, registry)
            val matched = r21Initialize(rawMatched, seed, registry)
            control.turnOrder shouldBe matched.turnOrder

            val bestiary = ManualMirrorInput.resolve(R21_BESTIARY)
            val wan = ManualMirrorInput.resolve(R21_WAN)
            (0..3).forEach { seat ->
                val id = EntityId("manual-mirror-$seat")
                val controlLibrary = r21ZoneNames(control, seat, Zone.LIBRARY)
                val matchedLibrary = r21ZoneNames(matched, seat, Zone.LIBRARY)
                val controlHand = r21ZoneNames(control, seat, Zone.HAND)
                val matchedHand = r21ZoneNames(matched, seat, Zone.HAND)
                val controlCombined = controlLibrary + controlHand
                val matchedCombined = matchedLibrary + matchedHand

                controlLibrary.size shouldBe 92
                matchedLibrary.size shouldBe 92
                controlHand.size shouldBe 7
                matchedHand.size shouldBe 7
                controlCombined.size shouldBe 99
                matchedCombined.size shouldBe 99
                controlCombined.count { it == bestiary } shouldBe 1
                matchedCombined.count { it == wan } shouldBe 1

                // Exact seeded permutation: same card ID slots in the real post-
                // initializer zones; *names* differ at exactly one of those slots.
                control.getZone(ZoneKey(id, Zone.LIBRARY)) shouldBe
                    matched.getZone(ZoneKey(id, Zone.LIBRARY))
                control.getZone(ZoneKey(id, Zone.HAND)) shouldBe
                    matched.getZone(ZoneKey(id, Zone.HAND))
                val libraryDiff = controlLibrary.indices.count { controlLibrary[it] != matchedLibrary[it] }
                val handDiff = controlHand.indices.count { controlHand[it] != matchedHand[it] }
                (libraryDiff + handDiff) shouldBe 1
                controlCombined.filter { it != bestiary } shouldBe
                    matchedCombined.filter { it != wan }
                controlCombined.filter { it != bestiary }.size shouldBe 98
                r21ZoneNames(control, seat, Zone.COMMAND) shouldBe listOf(R21_COMMANDER)
                r21ZoneNames(matched, seat, Zone.COMMAND) shouldBe listOf(R21_COMMANDER)
                println("R21_MATCHED_SLOT_PASS seed=$seed seat=$seat shared=98 differences=1")
            }
        }

        test("seed $seed published R18 section order is a negative positional control") {
            val rawControl = r21DeckNames(R21_CONTROL_PATH)
            val rawPublished = r21DeckNames(R21_PUBLISHED_WAN_PATH)
            val rawMatched = ManualR19MatchedSlotHelper.matchedChallengerNames(
                rawControl, R21_BESTIARY, R21_WAN
            )
            rawPublished.size shouldBe 100
            rawPublished.sorted() shouldBe rawMatched.sorted()
            // Important R19 finding: shared input *relative order* was preserved,
            // but five shifted shared positions plus Wan made six slot mismatches.
            rawControl.filter { it != R21_BESTIARY } shouldBe
                rawPublished.filter { it != R21_WAN }
            rawPublished.indexOf(R21_WAN) shouldNotBe rawControl.indexOf(R21_BESTIARY)
            rawControl.indices.count { rawControl[it] != rawPublished[it] } shouldBe 6

            val registry = r21Registry()
            val control = r21Initialize(rawControl, seed, registry)
            val published = r21Initialize(rawPublished, seed, registry)
            control.turnOrder shouldBe published.turnOrder

            (0..3).forEach { seat ->
                val controlLibrary = r21ZoneNames(control, seat, Zone.LIBRARY)
                val publishedLibrary = r21ZoneNames(published, seat, Zone.LIBRARY)
                val controlHand = r21ZoneNames(control, seat, Zone.HAND)
                val publishedHand = r21ZoneNames(published, seat, Zone.HAND)
                val controlCombined = controlLibrary + controlHand
                val publishedCombined = publishedLibrary + publishedHand
                controlLibrary.size shouldBe 92
                publishedLibrary.size shouldBe 92
                controlHand.size shouldBe 7
                publishedHand.size shouldBe 7
                controlCombined.indices.count {
                    controlCombined[it] != publishedCombined[it]
                } shouldBe 6
                println("R21_R18_NEGATIVE_PASS seed=$seed seat=$seat mismatches=6")
            }
        }
    }
})
