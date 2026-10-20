package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.DeckFormat
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.CommanderEligibility
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * PREINITIALIZATION-ONLY source and compiled-catalog gate.
 * No ActionProcessor, AI choice, priority progression, or four-player gameplay.
 * This gate intentionally fails for unimplemented candidate cards.
 */
internal object ManualPrecisionR1Inputs {
    const val ROOT = "experiments/manual-transmission/research/precision-upgrade-20261020/"
    const val COMMANDER = "Animar, Soul of Elements"
    const val FROZEN_BLOB = "c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89"
    const val FROZEN_SHA256 = "6c28f0629d8ff0a859784f8c7dc0a47054b298d17c6b8e041a4a0a1d8744f111"

    val LAND_SWAPS = listOf(
        "Path of Ancestry" to "Mana Confluence",
        "Frontier Bivouac" to "City of Brass",
        "Dreamroot Cascade" to "Breeding Pool",
        "Stormcarved Coast" to "Steam Vents",
        "Sheltered Thicket" to "Stomping Ground",
        "Evolving Wilds" to "Misty Rainforest",
        "Terramorphic Expanse" to "Wooded Foothills",
    )
    val ADDITIONAL_SWAPS = listOf(
        "Farhaven Elf" to "Bloom Tender",
        "Lifecrafter’s Bestiary" to "Worldly Tutor",
        "Rapid Hybridization" to "Fierce Guardianship",
    )

    fun repoRoot(): Path = Path.of(requireNotNull(System.getenv("MANUAL_R4_ROOT")) {
        "MANUAL_R4_ROOT must identify the pinned checkout"
    })
    fun file(arm: String): Path {
        val name = when (arm) {
            "control" -> "CONTROL.v0.7.decklist.txt"
            "a" -> "CHALLENGER-A.mana.decklist.txt"
            "b" -> "CHALLENGER-B.precision.decklist.txt"
            else -> error("Unrecognized Precision Upgrade arm: " + arm)
        }
        return repoRoot().resolve(ROOT).resolve(name)
    }
    fun bytes(arm: String): ByteArray = Files.readAllBytes(file(arm))
    fun names(arm: String): List<String> =
        Files.readAllLines(file(arm)).flatMap { line ->
            Regex("^(\\d+) (.+)$").matchEntire(line)?.let { hit ->
                List(hit.groupValues[1].toInt()) { hit.groupValues[2] }
            } ?: emptyList()
        }
    fun normalizedNames(arm: String): List<String> = names(arm).map(ManualMirrorInput::resolve)
    fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(b).joinToString("") { "%02x".format(it) }

    fun verifySlotDiffs() {
        val original = names("control")
        val frozen = Files.readAllBytes(repoRoot().resolve(
            "experiments/manual-transmission/control/v0.7.decklist.txt"
        ))
        check(bytes("control").contentEquals(frozen))
        check(sha256(frozen) == FROZEN_SHA256)
        check(original.size == 100 && original.count { it == COMMANDER } == 1)
        for ((arm, swaps) in listOf("a" to LAND_SWAPS, "b" to (LAND_SWAPS + ADDITIONAL_SWAPS))) {
            val candidate = names(arm)
            check(candidate.size == 100 && candidate.count { it == COMMANDER } == 1)
            val expected = original.toMutableList()
            swaps.forEach { (old, new) ->
                check(expected.count { it == old } == 1 && new !in expected)
                expected[expected.indexOf(old)] = new
            }
            check(candidate == expected) { "Not a positional substitution for arm " + arm }
            check(original.indices.count { original[it] != candidate[it] } == swaps.size)
            check(candidate.toSet().size == original.toSet().size)
            println("PRECISION_R1_SOURCE arm=" + arm + " replacements=" + swaps.size +
                " cards=100 sourceSha256=" + sha256(bytes(arm)))
        }
        println("PRECISION_R1_FROZEN_CONTROL_SHA256=" + FROZEN_SHA256)
    }

    fun registry(): CardRegistry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }
    fun verifyCompiledLegality(arm: String, registry: CardRegistry) {
        val names = normalizedNames(arm)
        check(names.size == 100 && names.count { it == COMMANDER } == 1)
        val banned = Files.readAllLines(repoRoot().resolve(
            "tools/manual-mirror/commander-banned-20261019.txt"
        )).toSet()
        val leader = requireNotNull(registry.getCard(COMMANDER)) { "Missing Animar commander" }
        check(CommanderEligibility.isLegalCommander(leader))
        val issues = mutableListOf<String>()
        names.distinct().forEach { name ->
            val def = registry.getCard(name)
            if (def == null) {
                issues.add("Not registered: " + name)
            } else {
                if (!leader.colorIdentity.containsAll(def.colorIdentity))
                    issues.add("Color identity: " + name)
                if (name in banned) issues.add("Banned: " + name)
                if (def.legalFormats.isNotEmpty() && DeckFormat.COMMANDER !in def.legalFormats)
                    issues.add("Compiled Commander format: " + name)
                if (!def.typeLine.isBasicLand && names.count { it == name } != 1)
                    issues.add("Singleton: " + name)
            }
        }
        check(issues.isEmpty()) { "Precision arm " + arm + " compiled legality: " + issues.joinToString("; ") }
        println("PRECISION_R1_REGISTRY_PASS arm=" + arm + " registered=100")
    }

    fun initialize(arm: String, seed: Long, registry: CardRegistry): GameState {
        val names = normalizedNames(arm)
        val deck = Deck(names.filter { it != COMMANDER }, commander = COMMANDER)
        check(deck.cards.size == 99)
        return GameInitializer(registry).initializeGame(GameConfig(
            players = (0..3).map { seat ->
                PlayerConfig(
                    "Animar-" + seat, deck, startingLife = 40,
                    playerId = EntityId("manual-mirror-" + seat),
                    commanderCardName = COMMANDER,
                )
            },
            format = Format.Commander(), seed = seed, startingPlayerIndex = 0,
            skipMulligans = true, useHandSmoother = false,
        )).state
    }

    private fun zone(state: GameState, seat: Int, where: Zone): List<String> {
        val player = EntityId("manual-mirror-" + seat)
        return state.getZone(ZoneKey(player, where)).map { id ->
            state.getEntity(id)?.get<CardComponent>()?.name
                ?: error("Missing card identity: seat=" + seat + " zone=" + where)
        }
    }

    fun verifyMatchedSlots(seed: Long, registry: CardRegistry, arm: String) {
        val original = initialize("control", seed, registry)
        val challenger = initialize(arm, seed, registry)
        val swaps = if (arm == "a") LAND_SWAPS else LAND_SWAPS + ADDITIONAL_SWAPS
        val canonicalRemoved = swaps.map { ManualMirrorInput.resolve(it.first) }.toSet()
        val canonicalAdded = swaps.map { ManualMirrorInput.resolve(it.second) }.toSet()
        check(original.turnOrder == challenger.turnOrder)
        (0..3).forEach { seat ->
            val owner = EntityId("manual-mirror-" + seat)
            val leftLibrary = zone(original, seat, Zone.LIBRARY)
            val rightLibrary = zone(challenger, seat, Zone.LIBRARY)
            val leftHand = zone(original, seat, Zone.HAND)
            val rightHand = zone(challenger, seat, Zone.HAND)
            check(leftLibrary.size == 92 && rightLibrary.size == 92)
            check(leftHand.size == 7 && rightHand.size == 7)
            check(original.getZone(ZoneKey(owner, Zone.LIBRARY)) ==
                challenger.getZone(ZoneKey(owner, Zone.LIBRARY)))
            check(original.getZone(ZoneKey(owner, Zone.HAND)) ==
                challenger.getZone(ZoneKey(owner, Zone.HAND)))
            check(zone(original, seat, Zone.COMMAND) == listOf(COMMANDER))
            check(zone(challenger, seat, Zone.COMMAND) == listOf(COMMANDER))
            val left = leftLibrary + leftHand
            val right = rightLibrary + rightHand
            check(left.size == 99 && right.size == 99)
            check(left.indices.count { left[it] != right[it] } == swaps.size)
            check(left.filterNot { it in canonicalRemoved } ==
                right.filterNot { it in canonicalAdded })
            check(left.count { it in canonicalRemoved } == swaps.size)
            check(right.count { it in canonicalAdded } == swaps.size)
            check(original.lifeTotal(owner) == 40 && challenger.lifeTotal(owner) == 40)
        }
        println("PRECISION_R1_INIT_PASS seed=" + seed + " arm=" + arm +
            " seats=4 distinctSlots=" + swaps.size)
    }
}

class ManualPrecisionR1PreflightTest : FunSpec({
    test("CONTROL and both challenger sources are exact 100-slot substitutions") {
        ManualPrecisionR1Inputs.verifySlotDiffs()
    }
    test("CONTROL and land-only challenger A are compiled and Commander legal") {
        val r = ManualPrecisionR1Inputs.registry()
        ManualPrecisionR1Inputs.verifyCompiledLegality("control", r)
        ManualPrecisionR1Inputs.verifyCompiledLegality("a", r)
    }
    test("A matches actual four-seat initializers on two PRE-FLIGHT-only seeds") {
        val r = ManualPrecisionR1Inputs.registry()
        listOf(202610202041L, 202610202042L).forEach { seed ->
            ManualPrecisionR1Inputs.verifyMatchedSlots(seed, r, "a")
        }
    }
    test("Challenger B all ten replacements must be compiled and legal before gameplay") {
        val r = ManualPrecisionR1Inputs.registry()
        ManualPrecisionR1Inputs.verifyCompiledLegality("b", r)
        listOf(202610202041L, 202610202042L).forEach { seed ->
            ManualPrecisionR1Inputs.verifyMatchedSlots(seed, r, "b")
        }
    }
})
