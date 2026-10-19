package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.text.Normalizer

/** Registry inspection only; never initializes actual-deck gameplay. */
class ManualTransmissionPhaseTwoRegistryAuditTest : FunSpec({
    test("inventory exact Phase 2 candidate decks without treating registry presence as rules qualification")
        .config(enabled = System.getenv("MT_P2_AUDIT_INPUT") != null) {
            val input = Path.of(System.getenv("MT_P2_AUDIT_INPUT") ?: error("input required"))
            val output = Path.of(System.getenv("MT_P2_AUDIT_OUTPUT") ?: error("output required"))
            Files.createDirectories(output)
            val json = Json { prettyPrint = true; encodeDefaults = true }
            val request = json.parseToJsonElement(Files.readString(input)).jsonObject
            request["phase1_commit"]!!.jsonPrimitive.content shouldBe "e7d37e2ea0f18dfa8d6b0fff68ef816b59555d2d"
            val registry = CardRegistry().apply {
                register(PredefinedTokens.allTokens)
                MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
            }
            val decks = request["decks"]!!.jsonArray
            decks.size shouldBe 8
            val rows = buildJsonArray {
                for (deckElement in decks) {
                    val deck = deckElement.jsonObject
                    val names = deck["commanders"]!!.jsonArray + deck["mainboard"]!!.jsonArray
                    names.size shouldBe 100
                    add(buildJsonObject {
                        put("deck_id", deck["id"]!!)
                        put("physical_cards", names.size)
                        put("commander_count", deck["commanders"]!!.jsonArray.size)
                        put("cards", buildJsonArray {
                            for ((index, element) in names.withIndex()) {
                                val requested = element.jsonPrimitive.content
                                val normalized = Normalizer.normalize(requested, Normalizer.Form.NFC).replace('\u2019', '\'')
                                val pieces = normalized.split(" // ")
                                val found = registry.getCard(normalized) ?: registry.getCard(pieces.first())
                                val faceVerified = pieces.size == 1 || found?.backFace?.name == pieces.last() || found?.cardFaces?.any { it.name == pieces.last() } == true
                                val accepted = found?.takeIf { faceVerified }
                                add(buildJsonObject {
                                    put("index", index)
                                    put("requested_name", requested)
                                    put("normalized_name", normalized)
                                    put("resolved_name", accepted?.name?.let(::JsonPrimitive) ?: JsonNull)
                                    put("registry_resolved", accepted != null)
                                    put("rules_qualified", false)
                                    if (found != null && !faceVerified) put("blocker", "FULL_DOUBLE_FACE_IDENTITY_UNVERIFIED")
                                })
                                if (accepted != null) {
                                    val filename = deck["id"]!!.jsonPrimitive.content + "-" + index + ".json"
                                    Files.writeString(output.resolve(filename), json.encodeToString(CardDefinition.serializer(), accepted))
                                }
                            }
                        })
                    })
                }
            }
            val report = buildJsonObject {
                put("schema", "mt-phase2-live-registry-audit-v1")
                put("status", "INVENTORY_ONLY_NOT_RULES_OR_PILOT_QUALIFICATION")
                put("execution_allowed", false)
                put("actual_deck_games_initialized", 0)
                put("official_outcomes", 0)
                put("decks", rows)
            }
            Files.writeString(output.resolve("registry-report.json"), json.encodeToString(JsonObject.serializer(), report))
            println("Phase 2 exact registry audit complete; gameplay remains disabled")
        }
})
