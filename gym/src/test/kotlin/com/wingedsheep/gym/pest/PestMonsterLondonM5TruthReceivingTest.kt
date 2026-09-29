package com.wingedsheep.gym.pest

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

class PestMonsterLondonM5TruthReceivingTest : FunSpec({
    val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("build/reports/pest-london-m5-truth-receiving/truth-bank.json")) }
    val json = Json { ignoreUnknownKeys = false }
    val bank = json.parseToJsonElement(
        Files.readString(root.resolve("build/reports/pest-london-m5-truth-receiving/truth-bank.json"))
    ).jsonObject

    fun keyOf(atom: kotlinx.serialization.json.JsonObject): PestLondonM5TruthKey {
        val physical = atom.getValue("physical_lands").jsonArray.map { pair ->
            val p = pair.jsonArray
            p[0].jsonPrimitive.content to p[1].jsonPrimitive.int
        }
        return PestLondonM5TruthKey(
            physicalLands = physical,
            m2OpeningCandidate = atom.getValue("m2_opening_candidate").jsonPrimitive.boolean,
            earlySpell = atom.getValue("early_spell").jsonPrimitive.content,
        )
    }

    val rows = bank.getValue("rows").jsonArray.map { it.jsonObject }
    val truth = rows.associate { row ->
        keyOf(row.getValue("atom").jsonObject) to
            row.getValue("development_functional").jsonPrimitive.boolean
    }
    truth.size shouldBe 6850
    val receiver = PestMonsterLondonM5TruthReceiving(truth)

    val landColors = mapOf(
        "Bojuka Bog" to setOf('B'),
        "Conduit Pylons" to setOf('W', 'U', 'B', 'R', 'G'),
        "Forest" to setOf('G'),
        "Haunted Fengraf" to emptySet(),
        "Urza's Mine" to emptySet(),
        "Urza's Power Plant" to emptySet(),
        "Urza's Tower" to emptySet(),
    )
    val spellFacts = mapOf(
        "Rooftop Percher" to (5 to emptySet<Char>()),
        "Generous Ent" to (6 to setOf('G')),
        "Boulderbranch Golem" to (7 to emptySet()),
        "Bramble Wurm" to (7 to setOf('G')),
        "Maelstrom Colossus" to (8 to emptySet()),
        "Ancient Stirrings" to (1 to setOf('G')),
        "Crop Rotation" to (1 to setOf('G')),
        "Breath Weapon" to (3 to setOf('R')),
        "Unfathomable Truths" to (5 to setOf('U')),
        "Barrels of Blasting Jelly" to (1 to emptySet()),
        "Candy Trail" to (1 to emptySet()),
        "Expedition Map" to (1 to emptySet()),
        "Giant's Boulder" to (1 to emptySet()),
        "Bonder's Ornament" to (3 to emptySet()),
        "Pinnacle Kill-Ship" to (7 to emptySet()),
    )

    fun submittedDeck(hand: List<PestLondonCardFacts>, needsForestTarget: Boolean): Map<String, Int> {
        val counts = hand.groupingBy { it.name }.eachCount().toMutableMap()
        if (needsForestTarget) counts["Forest"] = counts.getOrDefault("Forest", 0) + 1
        val used = counts.values.sum()
        require(used <= 60)
        counts["Receiving Filler"] = 60 - used
        return counts
    }

    fun handFor(key: PestLondonM5TruthKey): List<PestLondonCardFacts> {
        var id = 0
        val hand = mutableListOf<PestLondonCardFacts>()
        key.physicalLands.forEach { (name, count) ->
            repeat(count) {
                hand += PestLondonCardFacts(
                    id = "land-${id++}",
                    name = name,
                    isLand = true,
                    cmc = 0,
                    colorsProduced = landColors.getValue(name),
                )
            }
        }
        if (key.m2OpeningCandidate) {
            hand += PestLondonCardFacts(
                id = "ent-${id++}",
                name = "Generous Ent",
                isLand = false,
                cmc = 6,
                colorsRequired = setOf('G'),
                typedCyclingTargets = setOf("Forest"),
                typedCyclingPayableBySoleLand = true,
            )
        }
        val (cmc, colors) = spellFacts.getValue(key.earlySpell)
        hand += PestLondonCardFacts(
            id = "spell-${id++}",
            name = key.earlySpell,
            isLand = false,
            cmc = cmc,
            colorsRequired = colors,
        )
        return hand
    }

    test("all 6850 accepted atom truths bind into the lawful predicate-vector M5 seam") {
        rows.forEachIndexed { index, row ->
            val key = keyOf(row.getValue("atom").jsonObject)
            val expected = row.getValue("development_functional").jsonPrimitive.boolean
            val unbound = handFor(key)
            val bound = receiver.bind(unbound, key.m2OpeningCandidate)
            val vector = PestMonsterLondonPredicateVectorExtractor.extract(
                submittedDeck = submittedDeck(bound, key.m2OpeningCandidate),
                hand = bound,
                mulliganCount = 0,
            )
            vector.guaranteedSecondLandAccess shouldBe key.m2OpeningCandidate
            vector.developmentFunctional shouldBe expected
            require(vector.earlySpellIds.size == 1) {
                "atom $index reconstructed ${vector.earlySpellIds.size} early spells"
            }
        }
    }

    test("truth lookup misses remain unknown and fail closed") {
        val hand = listOf(
            PestLondonCardFacts("f1", "Forest", true, 0, colorsProduced = setOf('G')),
            PestLondonCardFacts("u1", "Unbanked Spell", false, 1),
        )
        val bound = receiver.bind(hand, false)
        bound.single { it.id == "u1" }.deterministicDevelopmentPayable shouldBe null
        val vector = PestMonsterLondonPredicateVectorExtractor.extract(
            submittedDeck = mapOf("Forest" to 1, "Unbanked Spell" to 1, "Receiving Filler" to 58),
            hand = bound,
            mulliganCount = 0,
        )
        vector.developmentFunctional shouldBe null
        vector.qualifiedForKeepDecision shouldBe false
    }
})
