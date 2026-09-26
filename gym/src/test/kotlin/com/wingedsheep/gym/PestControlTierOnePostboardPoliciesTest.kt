package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.PestControlTierOnePostboardPolicies
import com.wingedsheep.gym.matchup.PestPostboardDeckBinding
import com.wingedsheep.gym.matchup.PestPostboardExchange
import com.wingedsheep.gym.matchup.PestTierOneDeck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Ten construction checks against the independently reviewed immutable boarding inputs. */
class PestControlTierOnePostboardPoliciesTest : FunSpec({
    val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
    val inputBytes = Files.readAllBytes(root.resolve(INPUT_PATH))
    check(sha256(inputBytes) == INPUT_SHA256) { "Frozen boarding inputs changed" }
    val inputs = Json.parseToJsonElement(inputBytes.decodeToString()).jsonObject
    val freeze = Json.parseToJsonElement(
        Files.readString(root.resolve("docs/experiments/pest-control/tier-one-postboard-policy-freeze.json"))
    ).jsonObject
    check(freeze.text("status") == "BOARDING_STRATEGY_AND_DECK_DIGESTS_FROZEN")
    check(freeze.text("policy_inputs_sha256") == INPUT_SHA256)
    check(freeze.text("policy_source_sha256") == POLICY_SHA256)
    check(sha256(Files.readAllBytes(root.resolve(freeze.text("policy_source_file")))) == POLICY_SHA256)

    val opponents = listOf(
        PestTierOneDeck.MONO_RED_MADNESS, PestTierOneDeck.GRIXIS_AFFINITY,
        PestTierOneDeck.MONO_BLUE_TERROR, PestTierOneDeck.MONSTER_TRON, PestTierOneDeck.SPY_COMBO,
    )
    val expectedPairs = opponents.flatMap {
        listOf(PestTierOneDeck.PEST_CONTROL to it, it to PestTierOneDeck.PEST_CONTROL)
    }
    val plans = inputs.getValue("plans").jsonArray.map { it.jsonObject }
    val pairs = plans.map { PestTierOneDeck.valueOf(it.text("deck")) to PestTierOneDeck.valueOf(it.text("opponent")) }
    check(pairs == expectedPairs && pairs.distinct().size == 10) { "Frozen pair inventory changed" }
    val receiptRows = freeze.getValue("frozen_bindings").jsonArray.map { it.jsonObject }
    check(receiptRows.size == 10)
    plans.zip(receiptRows).forEach { (plan, receipt) ->
        check(plan.getValue("binding") == receipt.getValue("binding"))
        check(plan.text("deck") == receipt.text("deck") && plan.text("opponent") == receipt.text("opponent"))
    }

    plans.forEach { plan ->
        val deckIdentity = PestTierOneDeck.valueOf(plan.text("deck"))
        val opponent = PestTierOneDeck.valueOf(plan.text("opponent"))
        test("frozen postboard construction $deckIdentity versus $opponent") {
            val exchange = PestControlTierOnePostboardPolicies.exchangeFor(deckIdentity, opponent)
            exchange shouldBe PestPostboardExchange(plan.counts("to_main"), plan.counts("to_sideboard"))
            val prepared = PestControlTierOnePostboardPolicies.prepare(deckIdentity, opponent)
            val binding = plan.getValue("binding").jsonObject
            val frozen = PestPostboardDeckBinding(
                deck = deckIdentity,
                originalMainSha256 = binding.text("originalMainSha256"),
                originalSideboardSha256 = binding.text("originalSideboardSha256"),
                originalComplete75Sha256 = binding.text("originalComplete75Sha256"),
                postboardMainSha256 = binding.text("postboardMainSha256"),
                postboardSideboardSha256 = binding.text("postboardSideboardSha256"),
                postboardComplete75Sha256 = binding.text("postboardComplete75Sha256"),
            )
            prepared.binding shouldBe frozen
            val main = plan.counts("postboard_main")
            val sideboard = plan.counts("postboard_sideboard")
            prepared.mainCounts shouldBe main
            prepared.sideboardCounts shouldBe sideboard
            val deck = prepared.toDeck(frozen)
            deck.size shouldBe 60
            deck.sideboard.size shouldBe 15
            deck.cards shouldBe main.flatMap { (name, count) -> List(count) { name } }
            deck.sideboard.map { it.name } shouldBe sideboard.flatMap { (name, count) -> List(count) { name } }
        }
    }
}) {
    companion object {
        private const val INPUT_PATH = "docs/experiments/pest-control/tier-one-postboard-policy-inputs.json"
        private const val INPUT_SHA256 = "85b028e0f65967b86e07a56bf352a237c7e6b0659ecfa9b6b0e5564834ce0829"
        private const val POLICY_SHA256 = "dc3955e2b9d313d200e08ca323fcc700902ab65e3e09658c929f2b72db5e3993"
    }
}

private fun JsonObject.text(name: String): String = getValue(name).jsonPrimitive.content
private fun JsonObject.counts(name: String): Map<String, Int> =
    getValue(name).jsonObject.mapValues { it.value.jsonPrimitive.int }
private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }
