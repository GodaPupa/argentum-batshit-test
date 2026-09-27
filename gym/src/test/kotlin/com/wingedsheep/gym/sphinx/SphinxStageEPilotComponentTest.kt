package com.wingedsheep.gym.sphinx

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Exactly the prospectively frozen 14 checks for each of four immutable identities (56 total).
 * These are policy component fixtures, with no GameEnvironment, shuffled deck, seed or result.
 * They do not qualify an actor adapter, a whole-game pilot, or experimental performance.
 */
class SphinxStageEPilotComponentTest : FunSpec({
    data class InputIdentity(val id: String, val sha256: String)
    val identities = listOf(
        InputIdentity("reconstructed-v01", "274521097cc731ac5f5aa9b7645a20b13d4546fd73b17dbcd14af0b0a25c4486"),
        InputIdentity("reconstructed-hybrid", "f4cf2c64bb07847bd013e9984480f5b0fa76ac3a40dc5a47894a3609e9cb3b2e"),
        InputIdentity("closest-no-approach-v01", "548998c77f5f688d793d36cdb9f21ae8b3b63963856875c4b84d82c271196a83"),
        InputIdentity("serpico-terror-benchmark", "6c678f94112c56b0856c1fe4c008f77e7d0897bdeb290f3e2a0ec9d034147c62"),
    )
    val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_PILOT_FIXTURE_BUDGET.json")) }
    val budgetBytes = Files.readAllBytes(root.resolve("sphinx-approach/STAGE_E_PILOT_FIXTURE_BUDGET.json"))
    fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    check(sha256(budgetBytes) == "24f0826253411d9f97e151da3f941d58c0dafd13083e7159c0b90ac017b32c2d")
    val pilot = SphinxStageEPilotComponent

    identities.forEach { identity ->
        val rows = Files.readAllBytes(root.resolve("sphinx-approach/decks/${identity.id}.csv"))
        check(sha256(rows) == identity.sha256)
        val cards = rows.toString(Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }
            .associate { row -> row.substringBeforeLast(',') to row.substringAfterLast(',').toInt() }
        check(cards.values.sum() == 60)
        // This label chooses fixture content only. It never enters the policy input or signature.
        val hasApproach = "Sphinx's Approach" in cards
        val deployment = if (hasApproach) "Sphinx's Approach" else "Tolarian Terror"
        val costCard = if ("Tolarian Terror" in cards) "Tolarian Terror" else "Snap"
        fun facts(
            mana: Int = 3,
            library: Int = 20,
            graveyard: List<String> = if (hasApproach) listOf("a1", "a2", "a3", "a4") else emptyList(),
            window: SphinxStageEWindow = SphinxStageEWindow.OTHER,
        ) = SphinxStageEPilotFacts(
            actorId = "self",
            epoch = 7,
            libraryCount = library,
            availableBlueMana = mana,
            ownHandNames = listOf("Thought Scour", "Counterspell", deployment, costCard).distinct(),
            actualGraveyardApproachIds = graveyard,
            knownUnseenSphinxCount = cards["Goliath Sphinx"] ?: 0,
            window = window,
        ).also { check(it.ownHandNames.all(cards::containsKey)) }
        fun input(
            facts: SphinxStageEPilotFacts = facts(),
            card: String = "Thought Scour",
            cost: Int = 1,
            affordable: Boolean = true,
            targets: List<SphinxStageEPublicTarget> = if (card == "Thought Scour") listOf(
                SphinxStageEPublicTarget(facts.actorId, true, 0, counterable = false,
                    kind = SphinxStageETargetKind.PLAYER)
            ) else emptyList(),
        ) = SphinxStageEPolicyInput(facts, SphinxStageECastOffer("offer", facts.epoch, card, affordable, cost, targets))

        test("${identity.id}/D1 safe self-mill then draw") {
            val choice = pilot.chooseSetupDraw(input(facts(library = 3)))
            choice.actionId shouldBe "offer"
            choice.targetId shouldBe "self"
        }
        test("${identity.id}/D2 stop before an impossible mandatory draw") {
            pilot.chooseSetupDraw(input(facts(library = 2))).actionId shouldBe null
        }
        test("${identity.id}/C1 present resources support the current action") {
            if (hasApproach) {
                pilot.selectApproachPayment(facts(), "resolving", listOf("a4", "a2", "a1", "a3")) shouldBe
                    listOf("a1", "a2", "a3", "a4")
            } else {
                pilot.chooseCurrentCast(input(facts(mana = 1), "Tolarian Terror", 1)).actionId shouldBe "offer"
            }
        }
        test("${identity.id}/C2 no credit for a future cleanup discard") {
            if (hasApproach) {
                // The fourth copy is on the stack, not in the graveyard. An extra copy in the
                // hand or a later cleanup cannot pay now; the offered source is also excluded.
                val now = facts(graveyard = listOf("a1", "a2", "a3"))
                pilot.selectApproachPayment(now, "resolving", listOf("a1", "a2", "a3", "resolving")) shouldBe emptyList()
            } else {
                // A hand cantrip might later enter the graveyard. The actual current Terror
                // offer still costs three and cannot be cast with two blue mana.
                pilot.chooseCurrentCast(input(facts(mana = 2), "Tolarian Terror", 3, affordable = false))
                    .actionId shouldBe null
            }
        }
        test("${identity.id}/I1 preserve affordable Counterspell over setup") {
            pilot.chooseSetupDraw(input(facts(mana = 2))).actionId shouldBe null
        }
        test("${identity.id}/I2 use excess mana after reserving Counterspell") {
            pilot.chooseSetupDraw(input(facts(mana = 3))).actionId shouldBe "offer"
        }
        test("${identity.id}/P1 take the legal normal deployment window") {
            val window = if (hasApproach) SphinxStageEWindow.OPPONENT_END_STEP else SphinxStageEWindow.ACTOR_MAIN
            pilot.chooseDeployment(input(facts(window = window), deployment, if (hasApproach) 3 else 1))
                .actionId shouldBe "offer"
        }
        test("${identity.id}/P2 wait outside the normal deployment window") {
            val window = if (hasApproach) SphinxStageEWindow.ACTOR_MAIN else SphinxStageEWindow.OTHER
            // No opposing-turn Terror offer is made affordable by the fixture. The policy must
            // not fabricate one; Approach is legal here but its normal deployment is deferred.
            pilot.chooseDeployment(input(facts(window = window), deployment, if (hasApproach) 3 else 1,
                affordable = hasApproach))
                .actionId shouldBe null
        }
        test("${identity.id}/T1 the fresh higher cost controls the next cast") {
            val currentCost = if (costCard == "Tolarian Terror") 5 else 2
            pilot.chooseCurrentCast(input(facts(mana = 1), costCard, currentCost, affordable = false))
                .actionId shouldBe null
        }
        test("${identity.id}/T2 stale cheap offer is rejected before selection") {
            val now = input(facts(mana = 2), costCard, if (costCard == "Snap") 2 else 1)
            shouldThrow<IllegalArgumentException> {
                pilot.chooseCurrentCast(now.copy(offer = now.offer.copy(epoch = now.facts.epoch - 1)))
            }
            pilot.chooseCurrentCast(now).actionId shouldBe "offer"
        }
        test("${identity.id}/A1 select an offered public opposing stack target") {
            val offered = listOf(
                // Goliath in the pure list, or Terror in the other three, can be the actor's
                // seven-mana-value spell below an opposing Scour and Counterspell response.
                SphinxStageEPublicTarget("own-spell", true, 7),
                SphinxStageEPublicTarget("small-spell", false, 1),
                SphinxStageEPublicTarget("large-spell", false, 2),
            )
            pilot.chooseCounterspell(input(card = "Counterspell", cost = 2, targets = offered))
                .targetId shouldBe "large-spell"
        }
        test("${identity.id}/A2 no offered target means no counter cast") {
            val noTarget = pilot.chooseCounterspell(input(card = "Counterspell", cost = 2))
            noTarget.actionId shouldBe null
            noTarget.targetId shouldBe null
        }
        test("${identity.id}/H1 typed own-public input round trip preserves the decision") {
            val original = input()
            val restored = pilot.decode(pilot.encode(original))
            restored shouldBe original
            pilot.chooseSetupDraw(restored) shouldBe pilot.chooseSetupDraw(original)
        }
        test("${identity.id}/H2 undeclared hidden hand or future order is rejected") {
            val clean = Json.parseToJsonElement(pilot.encode(input())).jsonObject
            for (field in listOf("opponentHand", "futureLibraryOrder")) {
                val taintedFacts = JsonObject(clean.getValue("facts").jsonObject +
                    (field to JsonArray(listOf(JsonPrimitive("unseen card")))))
                val tainted = JsonObject(clean + ("facts" to taintedFacts))
                shouldThrow<SerializationException> { pilot.decode(tainted.toString()) }
            }
        }
    }
})
