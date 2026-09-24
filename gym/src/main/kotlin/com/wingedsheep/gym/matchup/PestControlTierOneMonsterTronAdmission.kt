package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry
import java.security.MessageDigest

const val PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID =
    "PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1"
const val PEST_MONSTER_TRON_EVENT_SOURCE_URL =
    "https://mtgdecks.net/Pauper/mtgo-pauper-challenge-32-12854527-tournament-270818"
const val PEST_MONSTER_TRON_LIST_SOURCE_URL =
    "https://decksnipe.com/archetype/pauper/green-tron"
const val PEST_MONSTER_TRON_MAIN_SHA256 =
    "79ffc53ac331beafeb1ef4510ce174d01fb2685d963a4c1485f04edbf47c064f"
const val PEST_MONSTER_TRON_SIDEBOARD_SHA256 =
    "af793fd3c1b39104f8d7f880cec010e8c0ab3233d68c552e60bb8f4455b67be5"
const val PEST_MONSTER_TRON_COMPLETE_75_SHA256 =
    "f2ed1d8a0bc55e66a815ff0bb7c0219965089ab1f401a2cc14eee17105717e2a"

private val MONSTER_TRON_MAIN = linkedMapOf(
    "Rooftop Percher" to 2,
    "Generous Ent" to 2,
    "Boulderbranch Golem" to 2,
    "Bramble Wurm" to 4,
    "Maelstrom Colossus" to 4,
    "Ancient Stirrings" to 4,
    "Crop Rotation" to 3,
    "Breath Weapon" to 2,
    "Unfathomable Truths" to 2,
    "Barrels of Blasting Jelly" to 1,
    "Candy Trail" to 2,
    "Expedition Map" to 4,
    "Giant's Boulder" to 4,
    "Bonder's Ornament" to 2,
    "Pinnacle Kill-Ship" to 4,
    "Bojuka Bog" to 1,
    "Conduit Pylons" to 2,
    "Forest" to 2,
    "Haunted Fengraf" to 1,
    "Urza's Mine" to 4,
    "Urza's Power Plant" to 4,
    "Urza's Tower" to 4,
)

private val MONSTER_TRON_SIDEBOARD = linkedMapOf(
    "Blue Elemental Blast" to 2,
    "Hydroblast" to 2,
    "Kaervek's Torch" to 1,
    "Pyroblast" to 1,
    "Relic of Progenitus" to 4,
    "Call Damage Control" to 2,
    "Breath Weapon" to 1,
    "Scour from Existence" to 2,
)

enum class TierOneMonsterTronRunnerState { DISABLED }

data class TierOneMonsterTronAdmission(
    val protocolId: String = PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID,
    val opponentPilot: String = "mehanske",
    val opponentEvent: String = "MTGO Pauper Challenge 32 #12854527; Top 8; 5-2; 64 players",
    val opponentEventDate: String = "2026-09-21",
    val eventSourceUrl: String = PEST_MONSTER_TRON_EVENT_SOURCE_URL,
    val listSourceUrl: String = PEST_MONSTER_TRON_LIST_SOURCE_URL,
    val pestMainSha256: String = PEST_CONTROL_V10_HASH,
    val opponentMainSha256: String = PEST_MONSTER_TRON_MAIN_SHA256,
    val opponentSideboardSha256: String = PEST_MONSTER_TRON_SIDEBOARD_SHA256,
    val opponentComplete75Sha256: String = PEST_MONSTER_TRON_COMPLETE_75_SHA256,
    val scope: String = "PREBOARD_ADMISSION_AND_SUPPORT_AUDIT_ONLY",
    val mainSupportStatus: String = "AUDIT_PENDING",
    val sideboardStatus: String = "FROZEN_15; IDENTITY_ONLY; NOT_INSTANTIATED",
    val runnerState: TierOneMonsterTronRunnerState = TierOneMonsterTronRunnerState.DISABLED,
    val officialGamesAuthorized: Int = 0,
    val officialSeedsGenerated: Int = 0,
    val outcomeExposure: Int = 0,
)

object PestControlTierOneMonsterTronAdmission {
    val mainCounts: Map<String, Int> get() = MONSTER_TRON_MAIN.toMap()
    val sideboardCounts: Map<String, Int> get() = MONSTER_TRON_SIDEBOARD.toMap()

    fun unresolvedMain(registry: CardRegistry): Map<String, Int> =
        MONSTER_TRON_MAIN.filterKeys { registry.getCard(it) == null }

    fun unresolvedSideboard(registry: CardRegistry): Map<String, Int> =
        MONSTER_TRON_SIDEBOARD.filterKeys { registry.getCard(it) == null }

    fun validationErrors(admission: TierOneMonsterTronAdmission): List<String> = buildList {
        if (admission.protocolId != PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID) add("protocol mismatch")
        if (admission.opponentPilot != "mehanske") add("opponent pilot mismatch")
        if (admission.opponentEventDate != "2026-09-21") add("opponent event date mismatch")
        if (admission.eventSourceUrl != PEST_MONSTER_TRON_EVENT_SOURCE_URL) add("event source mismatch")
        if (admission.listSourceUrl != PEST_MONSTER_TRON_LIST_SOURCE_URL) add("list source mismatch")
        if (admission.pestMainSha256 != PEST_CONTROL_V10_HASH) add("Pest Control identity mismatch")
        if (MONSTER_TRON_MAIN.values.sum() != 60 || MONSTER_TRON_SIDEBOARD.values.sum() != 15) {
            add("Monster Tron deck quantities must be 60/15")
        }
        if (hashRows(MONSTER_TRON_MAIN) != admission.opponentMainSha256) {
            add("Monster Tron main hash mismatch")
        }
        if (hashRows(MONSTER_TRON_SIDEBOARD) != admission.opponentSideboardSha256) {
            add("Monster Tron sideboard hash mismatch")
        }
        if (hashComplete75(MONSTER_TRON_MAIN, MONSTER_TRON_SIDEBOARD) != admission.opponentComplete75Sha256) {
            add("Monster Tron complete-75 hash mismatch")
        }
        if (admission.scope != "PREBOARD_ADMISSION_AND_SUPPORT_AUDIT_ONLY") add("scope mismatch")
        if (admission.mainSupportStatus != "AUDIT_PENDING") add("main support status mismatch")
        if (admission.sideboardStatus != "FROZEN_15; IDENTITY_ONLY; NOT_INSTANTIATED") {
            add("sideboard status mismatch")
        }
        if (admission.runnerState != TierOneMonsterTronRunnerState.DISABLED) add("runner must remain disabled")
        if (admission.officialGamesAuthorized != 0) add("official games are not authorized")
        if (admission.officialSeedsGenerated != 0) add("official seeds must not exist")
        if (admission.outcomeExposure != 0) add("outcome exposure must remain zero")
    }

    private fun hashRows(rows: Map<String, Int>): String = sha256(
        rows.entries.joinToString(separator = "\n", postfix = "\n") { (name, count) -> "$name,$count" }.toByteArray()
    )

    private fun hashComplete75(main: Map<String, Int>, sideboard: Map<String, Int>): String = sha256(
        buildString {
            appendLine("MAIN")
            main.forEach { (name, count) -> appendLine("$name,$count") }
            appendLine("SIDEBOARD")
            sideboard.forEach { (name, count) -> appendLine("$name,$count") }
        }.toByteArray()
    )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
