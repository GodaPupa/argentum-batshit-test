package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry
import java.security.MessageDigest

const val PEST_SPY_COMBO_PREBOARD_PROTOCOL_ID =
    "PEST_CONTROL_V10_VS_DR_DEJ96_SPY_COMBO_2026_09_15_PREBOARD_V1"
const val PEST_SPY_COMBO_SOURCE_URL =
    "https://www.spellbinder.gg/decks/spy-combo-h3qcmmdd42"
const val PEST_SPY_COMBO_MAIN_SHA256 =
    "8a0ac71dd9873b001d22a4ea73286c75d448e24a1b517772f836d6dd5f2db97f"
const val PEST_SPY_COMBO_SIDEBOARD_SHA256 =
    "e7f36bb14eec42b7967a33656c082861695da2d3c7a7d7d2f3d50060b2abff12"
const val PEST_SPY_COMBO_COMPLETE_75_SHA256 =
    "d6fc701b272ff15bc2baf9ec5b2acdaf75566d780ff6d499efbac4aa77b19b15"

private val SPY_MAIN = linkedMapOf(
    "Elves of Deep Shadow" to 2,
    "Nyxborn Hydra" to 2,
    "Quirion Ranger" to 3,
    "Saruli Caretaker" to 4,
    "Gatecreeper Vine" to 4,
    "Masked Vandal" to 3,
    "Mesmeric Fiend" to 3,
    "Overgrown Battlement" to 4,
    "Wall of Roots" to 3,
    "Balustrade Spy" to 4,
    "Sagu Wildling" to 4,
    "Generous Ent" to 4,
    "Lotleth Giant" to 2,
    "Land Grant" to 4,
    "Winding Way" to 4,
    "Lead the Stampede" to 4,
    "Dread Return" to 2,
    "Forest" to 3,
    "Swamp" to 1,
)

private val SPY_SIDEBOARD = linkedMapOf(
    "Swamp" to 1,
    "Jack-o'-Lantern" to 1,
    "Nyxborn Hydra" to 1,
    "Flaring Pain" to 1,
    "Masked Vandal" to 1,
    "Mesmeric Fiend" to 1,
    "Undergrowth Leopard" to 2,
    "Faerie Macabre" to 2,
    "Acorn Harvest" to 1,
    "Nylea's Disciple" to 4,
)

data class TierOneSpyComboAdmission(
    val protocolId: String = PEST_SPY_COMBO_PREBOARD_PROTOCOL_ID,
    val opponentPilot: String = "Dr_dej96",
    val opponentEvent: String = "MTGO Pauper League; 5-0",
    val opponentDate: String = "2026-09-15",
    val sourceUrl: String = PEST_SPY_COMBO_SOURCE_URL,
    val pestMainSha256: String = PEST_CONTROL_V10_HASH,
    val opponentMainSha256: String = PEST_SPY_COMBO_MAIN_SHA256,
    val opponentSideboardSha256: String = PEST_SPY_COMBO_SIDEBOARD_SHA256,
    val opponentComplete75Sha256: String = PEST_SPY_COMBO_COMPLETE_75_SHA256,
    val strategicAxis: String = "DEDICATED_COMBO_LIBRARY_EMPTY_REANIMATION",
    val scope: String = "PREBOARD_ADMISSION_AND_SUPPORT_AUDIT_ONLY",
    val officialSeedsGenerated: Int = 0,
    val officialGamesInitialized: Int = 0,
    val officialActionsSubmitted: Int = 0,
    val outcomeExposure: Int = 0,
)

object PestControlTierOneSpyComboAdmission {
    val mainCounts: Map<String, Int> get() = SPY_MAIN.toMap()
    val sideboardCounts: Map<String, Int> get() = SPY_SIDEBOARD.toMap()

    fun unresolvedMain(registry: CardRegistry): Map<String, Int> =
        SPY_MAIN.filterKeys { registry.getCard(it) == null }

    fun unresolvedSideboard(registry: CardRegistry): Map<String, Int> =
        SPY_SIDEBOARD.filterKeys { registry.getCard(it) == null }

    fun validationErrors(
        admission: TierOneSpyComboAdmission = TierOneSpyComboAdmission(),
    ): List<String> = buildList {
        if (admission.protocolId != PEST_SPY_COMBO_PREBOARD_PROTOCOL_ID) add("protocol mismatch")
        if (admission.opponentPilot != "Dr_dej96") add("opponent pilot mismatch")
        if (admission.opponentDate != "2026-09-15") add("opponent date mismatch")
        if (admission.sourceUrl != PEST_SPY_COMBO_SOURCE_URL) add("source URL mismatch")
        if (admission.pestMainSha256 != PEST_CONTROL_V10_HASH) add("Pest identity mismatch")
        if (admission.strategicAxis != "DEDICATED_COMBO_LIBRARY_EMPTY_REANIMATION") {
            add("strategic-axis mismatch")
        }
        if (SPY_MAIN.values.sum() != 60 || SPY_SIDEBOARD.values.sum() != 15) {
            add("Spy Combo quantities must be 60/15")
        }
        if (hashRows(SPY_MAIN) != admission.opponentMainSha256) add("main hash mismatch")
        if (hashRows(SPY_SIDEBOARD) != admission.opponentSideboardSha256) add("sideboard hash mismatch")
        if (hashComplete75(SPY_MAIN, SPY_SIDEBOARD) != admission.opponentComplete75Sha256) {
            add("complete-75 hash mismatch")
        }
        if (admission.scope != "PREBOARD_ADMISSION_AND_SUPPORT_AUDIT_ONLY") add("scope mismatch")
        if (admission.officialSeedsGenerated != 0) add("official seeds must remain zero")
        if (admission.officialGamesInitialized != 0) add("official games must remain zero")
        if (admission.officialActionsSubmitted != 0) add("official actions must remain zero")
        if (admission.outcomeExposure != 0) add("outcome exposure must remain zero")
    }

    private fun hashRows(rows: Map<String, Int>): String = sha256Spy(
        rows.entries.joinToString(separator = "\n", postfix = "\n") { (name, count) ->
            "$name,$count"
        }.toByteArray()
    )

    private fun hashComplete75(main: Map<String, Int>, sideboard: Map<String, Int>): String =
        sha256Spy(
            buildString {
                appendLine("MAIN")
                main.forEach { (name, count) -> appendLine("$name,$count") }
                appendLine("SIDEBOARD")
                sideboard.forEach { (name, count) -> appendLine("$name,$count") }
            }.toByteArray()
        )

    private fun sha256Spy(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
