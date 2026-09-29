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

/**
 * Exhaustive seed-free M5 atom comparator.
 *
 * Raw side: canonical seven-card representative + exact submitted-list remainder.
 * Certificate side: atom-only hand derived from public atom facts; no original hidden-library
 * identity/order or unrelated opening cards are copied into the certificate hand.
 */
class PestMonsterLondonM5AtomicComparatorTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("docs/experiments/pest-control/london-m5-atomic-comparator-budget-20260929.json")) }
    private val report = root.resolve("ai/build/reports/pest-london-m5-atomic-comparator")
    private val json = Json { ignoreUnknownKeys = false }

    private val guaranteedMethod = EngineAiPlayerController::class.java.declaredMethods.single {
        it.name.startsWith("guaranteedSecondLandAccess")
    }.apply { isAccessible = true }
    private val developmentMethod = EngineAiPlayerController::class.java.declaredMethods.single {
        it.name.startsWith("hasPayableEarlyDevelopmentLine")
    }.apply { isAccessible = true }

    private data class Eval(val m2: Boolean, val development: Boolean)

    private fun summary(state: GameState, id: EntityId): CardSummary {
        val card = requireNotNull(state.getEntity(id)?.get<CardComponent>())
        return CardSummary(
            name = card.name,
            manaCost = card.manaCost.toString(),
            typeLine = card.typeLine.toString(),
            oracleText = card.oracleText,
        )
    }

    private fun evaluate(
        state: GameState,
        playerId: EntityId,
        hand: List<EntityId>,
        spellName: String,
    ): Eval {
        val cards = hand.associateWith { summary(state, it) }
        val controller = EngineAiPlayerController(cardRegistry, playerId, gameStateProvider = { state })
        val guaranteed = guaranteedMethod.invoke(controller, hand, cards)
        val spell = hand.first { cards.getValue(it).name == spellName }
        val value = developmentMethod.invoke(controller, state, hand, spell.value, guaranteed) as Boolean
        return Eval(guaranteed != null, value)
    }

    private fun representativeNames(encoded: String): List<String> = buildList {
        if (encoded.isBlank()) return@buildList
        encoded.split(";").forEach { term ->
            val split = term.lastIndexOf('=')
            require(split > 0) { "Malformed representative term: $term" }
            val name = term.substring(0, split)
            val count = term.substring(split + 1).toInt()
            repeat(count) { add(name) }
        }
    }

    private fun monsterDeckNames(): List<String> {
        val source = json.parseToJsonElement(
            Files.readString(root.resolve("docs/experiments/pest-control/tier-one-monster-c2-exact-main-source-map.json"))
        ).jsonObject
        val monster = source.getValue("decks").jsonArray
            .map { it.jsonObject }
            .single { it.getValue("seat_policy").jsonPrimitive.content == "monster" }
        return monster.getValue("cards").jsonArray.flatMap { element ->
            val card = element.jsonObject
            List(card.getValue("count").jsonPrimitive.int) { card.getValue("name").jsonPrimitive.content }
        }.also { require(it.size == 60) }
    }

    private fun atomHandNames(atom: JsonObject): List<String> = buildList {
        atom.getValue("physical_lands").jsonArray.forEach { pairElement ->
            val pair = pairElement.jsonArray
            repeat(pair[1].jsonPrimitive.int) { add(pair[0].jsonPrimitive.content) }
        }
        add(atom.getValue("early_spell").jsonPrimitive.content)
        if (atom.getValue("m2_opening_candidate").jsonPrimitive.boolean) add("Generous Ent")
    }

    init {
        test("all 6850 raw M5 atoms equal their lawful atom-only synthetic certificates") {
            val bankPath = root.resolve("build/reports/pest-london-m5-atomic-comparator/atomic-bank.json")
            require(Files.isRegularFile(bankPath)) { "Missing generated atomic bank: $bankPath" }
            val bank = json.parseToJsonElement(Files.readString(bankPath)).jsonObject
            bank.getValue("reachable_seven_card_name_multisets").jsonPrimitive.int shouldBe 882297
            bank.getValue("unique_m5_input_signatures").jsonPrimitive.int shouldBe 79972
            bank.getValue("unique_atomic_development_questions").jsonPrimitive.int shouldBe 6850
            bank.getValue("atomic_bank_sha256").jsonPrimitive.content shouldBe
                "8825cb587a6337d8173a9d93c642a1d979617ef053833fb849362e18a519a49b"
            bank.getValue("signature_to_atoms_sha256").jsonPrimitive.content shouldBe
                "51ffc25991361eaa3bc8f457583eabb964d07442a4205b022ec890b134aac645"
            bank.getValue("truth_assigned").jsonPrimitive.boolean shouldBe false

            val deckNames = monsterDeckNames()
            val builder = scenario().withPlayers("Monster", "Opponent")
            deckNames.forEach { builder.withCardInLibrary(1, it) }
            val base = builder.build()
            val allIds = base.state.getLibrary(base.player1Id)
            val idsByName = allIds.groupBy { summary(base.state, it).name }
                .mapValues { (_, ids) -> ids.sortedBy { it.value } }

            fun stateAndHand(handNames: List<String>): Pair<GameState, List<EntityId>> {
                val usedByName = mutableMapOf<String, Int>()
                val hand = handNames.map { name ->
                    val index = usedByName.getOrDefault(name, 0)
                    val id = requireNotNull(idsByName[name]?.getOrNull(index)) {
                        "Submitted Monster main lacks requested synthetic card $name #" + (index + 1)
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

            val rows = bank.getValue("rows").jsonArray
            val truthRows = ArrayList<JsonObject>(rows.size)
            val mismatches = mutableListOf<String>()

            rows.forEachIndexed { index, rowElement ->
                val row = rowElement.jsonObject
                val atom = row.getValue("atom").jsonObject
                val spell = atom.getValue("early_spell").jsonPrimitive.content
                val expectedM2 = atom.getValue("m2_opening_candidate").jsonPrimitive.boolean

                val rawNames = representativeNames(row.getValue("representative_hand").jsonPrimitive.content)
                rawNames.size shouldBe 7
                val (rawState, rawHand) = stateAndHand(rawNames)
                val raw = evaluate(rawState, base.player1Id, rawHand, spell)

                val certificateNames = atomHandNames(atom)
                val (certificateState, certificateHand) = stateAndHand(certificateNames)
                val certificate = evaluate(certificateState, base.player1Id, certificateHand, spell)

                if (raw.m2 != expectedM2 || certificate.m2 != expectedM2 || raw.development != certificate.development) {
                    mismatches += "row=$index spell=$spell expectedM2=$expectedM2 raw=$raw certificate=$certificate atom=$atom"
                }
                truthRows += buildJsonObject {
                    put("atom", atom)
                    put("development_functional", certificate.development)
                }
            }

            Files.createDirectories(report)
            val summary = buildJsonObject {
                put("schema", "pest-monster-london-m5-atomic-comparator-summary-v1")
                put("atoms", rows.size)
                put("mismatches", mismatches.size)
                put("raw_controller_blob", "4f020ea2406e9bd6a87ec389685b48f15df4c5a3")
                put("atomic_bank_sha256", bank.getValue("atomic_bank_sha256").jsonPrimitive.content)
                put("signature_to_atoms_sha256", bank.getValue("signature_to_atoms_sha256").jsonPrimitive.content)
                put("mismatch_samples", buildJsonArray { mismatches.take(20).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            }
            Files.writeString(report.resolve("comparison-summary.json"), summary.toString() + "\n")

            mismatches.size shouldBe 0

            val truthBank = buildJsonObject {
                put("schema", "pest-monster-london-m5-atomic-truth-bank-v1")
                put("source_atomic_bank_sha256", bank.getValue("atomic_bank_sha256").jsonPrimitive.content)
                put("source_signature_to_atoms_sha256", bank.getValue("signature_to_atoms_sha256").jsonPrimitive.content)
                put("rows", JsonArray(truthRows))
            }
            Files.writeString(report.resolve("truth-bank.json"), truthBank.toString() + "\n")
        }
    }
}
