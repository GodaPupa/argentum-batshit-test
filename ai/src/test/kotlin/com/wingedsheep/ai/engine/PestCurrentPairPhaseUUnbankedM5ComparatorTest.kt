package com.wingedsheep.ai.engine

import com.wingedsheep.ai.llm.CardSummary
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Phase U only: resolve exactly the 692 frozen Pest M5 atom misses by comparing a canonical
 * real Pest seven-card representative against an atom-only lawful certificate. No mulligan,
 * bottoming, seed, allocation or gameplay entry point is invoked here.
 */
class PestCurrentPairPhaseUUnbankedM5ComparatorTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("docs/experiments/pest-control/CURRENT_PAIR_PHASE_U_UNBANKED_M5_BUDGET_20260930.json")) }
    private val report = root.resolve("ai/build/reports/pest-current-pair-phase-u")
    private val json = Json { ignoreUnknownKeys = false }

    private val pestDeck = linkedMapOf(
        "Essence Warden" to 4, "Carrier Thrall" to 4, "Blood Researcher" to 4,
        "Pest Mascot" to 4, "Fierce Witchstalker" to 4, "Generous Ent" to 3,
        "Follow the Lumarets" to 4, "Weather the Storm" to 4, "Cast Down" to 4,
        "Bone Shards" to 2, "Chainer's Edict" to 2, "Forest" to 10,
        "Swamp" to 7, "Jungle Hollow" to 4,
    )

    private val guaranteedMethod = EngineAiPlayerController::class.java.declaredMethods.single {
        it.name.startsWith("guaranteedSecondLandAccess")
    }.apply { isAccessible = true }
    private val developmentMethod = EngineAiPlayerController::class.java.declaredMethods.single {
        it.name.startsWith("hasPayableEarlyDevelopmentLine")
    }.apply { isAccessible = true }

    private data class Eval(val m2: Boolean, val development: Boolean)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun summary(state: GameState, id: EntityId): CardSummary {
        val card = requireNotNull(state.getEntity(id)?.get<CardComponent>())
        return CardSummary(
            name = card.name,
            manaCost = card.manaCost.toString(),
            typeLine = card.typeLine.toString(),
            oracleText = card.oracleText,
        )
    }

    private fun evaluate(state: GameState, playerId: EntityId, hand: List<EntityId>, spellName: String): Eval {
        val cards = hand.associateWith { summary(state, it) }
        val controller = EngineAiPlayerController(cardRegistry, playerId, gameStateProvider = { state })
        val guaranteed = guaranteedMethod.invoke(controller, hand, cards)
        val spell = hand.first { cards.getValue(it).name == spellName }
        val value = developmentMethod.invoke(controller, state, hand, spell.value, guaranteed) as Boolean
        return Eval(guaranteed != null, value)
    }

    private fun representativeNames(row: JsonObject): List<String> = buildList {
        row.getValue("representative_hand").jsonArray.forEach { pairElement ->
            val pair = pairElement.jsonArray
            repeat(pair[1].jsonPrimitive.int) { add(pair[0].jsonPrimitive.content) }
        }
    }

    private fun certificateNames(atom: JsonObject): List<String> = buildList {
        atom.getValue("physical_lands").jsonArray.forEach { pairElement ->
            val pair = pairElement.jsonArray
            repeat(pair[1].jsonPrimitive.int) { add(pair[0].jsonPrimitive.content) }
        }
        add(atom.getValue("early_spell").jsonPrimitive.content)
        if (atom.getValue("m2_opening_candidate").jsonPrimitive.boolean) add("Generous Ent")
    }

    init {
        test("exact 692 frozen Pest M5 misses equal atom-only certificates") {
            pestDeck.values.sum() shouldBe 60
            val deckHash = sha256(pestDeck.entries.joinToString("") { (name, count) -> "$name,$count\n" }.toByteArray())
            deckHash shouldBe "7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5"

            val worklistPath = root.resolve("build/reports/pest-current-pair-phase-u/pest-unbanked-m5-worklist.json")
            val worklistRaw = Files.readAllBytes(worklistPath)
            sha256(worklistRaw) shouldBe "2174410ee36dc0e74d0d7f4335606de7236aa845ef43bab409259a3056133738"
            val worklist = json.parseToJsonElement(worklistRaw.toString(Charsets.UTF_8)).jsonObject
            worklist.getValue("row_count").jsonPrimitive.int shouldBe 692
            worklist.getValue("rng_used").jsonPrimitive.boolean shouldBe false
            worklist.getValue("hidden_library_order_used").jsonPrimitive.boolean shouldBe false

            val builder = scenario().withPlayers("Pest", "Opponent")
            pestDeck.forEach { (name, count) -> repeat(count) { builder.withCardInLibrary(1, name) } }
            val base = builder.build()
            val allIds = base.state.getLibrary(base.player1Id)
            allIds.size shouldBe 60
            val idsByName = allIds.groupBy { summary(base.state, it).name }
                .mapValues { (_, ids) -> ids.sortedBy { it.value } }

            fun stateAndHand(handNames: List<String>): Pair<GameState, List<EntityId>> {
                val usedByName = mutableMapOf<String, Int>()
                val hand = handNames.map { name ->
                    val index = usedByName.getOrDefault(name, 0)
                    val id = requireNotNull(idsByName[name]?.getOrNull(index)) {
                        "Frozen Pest main lacks requested card $name #" + (index + 1)
                    }
                    usedByName[name] = index + 1
                    id
                }
                val used = hand.toSet()
                val state = base.state.copy(
                    zones = base.state.zones +
                        (ZoneKey(base.player1Id, Zone.HAND) to hand) +
                        (ZoneKey(base.player1Id, Zone.LIBRARY) to allIds.filterNot { it in used })
                )
                return state to hand
            }

            val truthRows = ArrayList<JsonObject>(692)
            val mismatches = mutableListOf<String>()
            worklist.getValue("rows").jsonArray.forEachIndexed { index, element ->
                val row = element.jsonObject
                val atom = row.getValue("atom").jsonObject
                val spell = atom.getValue("early_spell").jsonPrimitive.content
                val expectedM2 = atom.getValue("m2_opening_candidate").jsonPrimitive.boolean

                val rawNames = representativeNames(row)
                rawNames.size shouldBe 7
                val (rawState, rawHand) = stateAndHand(rawNames)
                val raw = evaluate(rawState, base.player1Id, rawHand, spell)

                val certificate = certificateNames(atom)
                val (certState, certHand) = stateAndHand(certificate)
                val cert = evaluate(certState, base.player1Id, certHand, spell)

                if (raw.m2 != expectedM2 || cert.m2 != expectedM2 || raw.development != cert.development) {
                    mismatches += "row=$index spell=$spell expectedM2=$expectedM2 raw=$raw certificate=$cert atom=$atom representative=$rawNames"
                }
                truthRows += buildJsonObject {
                    put("atom", atom)
                    put("development_functional", cert.development)
                }
            }

            Files.createDirectories(report)
            val comparison = buildJsonObject {
                put("schema", "pest-current-pair-phase-u-unbanked-m5-comparison-v1")
                put("atoms", 692)
                put("mismatches", mismatches.size)
                put("raw_controller_blob", "4f020ea2406e9bd6a87ec389685b48f15df4c5a3")
                put("worklist_sha256", "2174410ee36dc0e74d0d7f4335606de7236aa845ef43bab409259a3056133738")
                put("mismatch_samples", buildJsonArray {
                    mismatches.take(20).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                })
            }
            Files.writeString(report.resolve("comparison-summary.json"), comparison.toString() + "\n")
            mismatches.size shouldBe 0

            val truth = buildJsonObject {
                put("schema", "pest-current-pair-phase-u-m5-truth-supplement-v1")
                put("source_unbanked_atoms_sha256", "a00aef9df49106a2611079ece2824bf4e97d40c7e89207b16bc1a9e05d7ae287")
                put("source_worklist_sha256", "2174410ee36dc0e74d0d7f4335606de7236aa845ef43bab409259a3056133738")
                put("raw_controller_blob", "4f020ea2406e9bd6a87ec389685b48f15df4c5a3")
                put("rows", JsonArray(truthRows))
            }
            Files.writeString(report.resolve("truth-supplement.json"), truth.toString() + "\n")
        }
    }
}
