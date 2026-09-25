package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.model.Deck
import java.security.MessageDigest

const val PEST_MONO_BLUE_TERROR_PREBOARD_PROTOCOL_ID =
    "PEST_CONTROL_V10_VS_SERPICO_CC_MONO_BLUE_TERROR_2026_09_20_PREBOARD_V1"
const val PEST_MONO_BLUE_TERROR_EVENT_SOURCE_URL =
    "https://mtgdecks.net/Pauper/mtgo-pauper-challenge-32-12854518-tournament-270693"
const val PEST_MONO_BLUE_TERROR_LIST_SOURCE_URL =
    "https://decksnipe.com/archetype/pauper/mono-blue-terror"
const val PEST_MONO_BLUE_TERROR_MAIN_SHA256 =
    "6c678f94112c56b0856c1fe4c008f77e7d0897bdeb290f3e2a0ec9d034147c62"
const val PEST_MONO_BLUE_TERROR_SIDEBOARD_SHA256 =
    "af4296c5e6af4be3b05b96d0267bd04da12188126e774ef8e63bb16c00bc908c"
const val PEST_MONO_BLUE_TERROR_COMPLETE_75_SHA256 =
    "ae25ede2663cbc2fa41b4413381485df962b272791e1b3f49e2d69c45054e7d9"

private val MONO_BLUE_TERROR_MAIN = linkedMapOf(
    "Island" to 13,
    "Snow-Covered Island" to 1,
    "Cryptic Serpent" to 4,
    "Tolarian Terror" to 4,
    "Deem Inferior" to 2,
    "Lórien Revealed" to 4,
    "Artful Dodge" to 2,
    "Ponder" to 4,
    "Preordain" to 4,
    "Sleep of the Dead" to 2,
    "Brainstorm" to 4,
    "Dispel" to 4,
    "Mental Note" to 4,
    "Thought Scour" to 4,
    "Counterspell" to 4,
)

private val MONO_BLUE_TERROR_SIDEBOARD = linkedMapOf(
    "Annul" to 4,
    "Gut Shot" to 3,
    "Hydroblast" to 4,
    "Murmuring Mystic" to 1,
    "Spreading Seas" to 3,
)

private val EXPECTED_UNSUPPORTED_MAIN: Map<String, Int> = emptyMap()

private val EXPECTED_UNSUPPORTED_SIDEBOARD = linkedMapOf(
    "Gut Shot" to 3,
    "Hydroblast" to 4,
    "Murmuring Mystic" to 1,
    "Spreading Seas" to 3,
)

// Preserve the historical readiness inventory above. Postboard support batches A and B prospectively
// qualify Mystic, Gut Shot and Hydroblast in the current registry; this does not change the frozen 75,
// readiness record, or preboard execution authority.
private val CURRENT_UNSUPPORTED_SIDEBOARD = EXPECTED_UNSUPPORTED_SIDEBOARD -
    setOf("Murmuring Mystic", "Gut Shot", "Hydroblast")

enum class TierOneMonoBlueTerrorRunnerState { DISABLED }

data class TierOneMonoBlueTerrorReadiness(
    val protocolId: String = PEST_MONO_BLUE_TERROR_PREBOARD_PROTOCOL_ID,
    val opponentPilot: String = "Serpico_CC",
    val opponentEvent: String = "MTGO Pauper Challenge 32 #12854518; fourth place; 6-2",
    val opponentEventDate: String = "2026-09-20",
    val eventSourceUrl: String = PEST_MONO_BLUE_TERROR_EVENT_SOURCE_URL,
    val listSourceUrl: String = PEST_MONO_BLUE_TERROR_LIST_SOURCE_URL,
    val pestMainSha256: String = PEST_CONTROL_V10_HASH,
    val opponentMainSha256: String = PEST_MONO_BLUE_TERROR_MAIN_SHA256,
    val opponentSideboardSha256: String = PEST_MONO_BLUE_TERROR_SIDEBOARD_SHA256,
    val opponentComplete75Sha256: String = PEST_MONO_BLUE_TERROR_COMPLETE_75_SHA256,
    val scope: String = "PREBOARD_READINESS_ONLY",
    val mainSupportStatus: String = "SUPPORTED_PREBOARD_60",
    val sideboardStatus: String = "FROZEN_15; NOT_INSTANTIATED; BLOCKED_4_IDENTITIES_11_SLOTS",
    val runnerState: TierOneMonoBlueTerrorRunnerState = TierOneMonoBlueTerrorRunnerState.DISABLED,
    val officialGamesAuthorized: Int = 0,
    val officialSeedsGenerated: Int = 0,
    val outcomeExposure: Int = 0,
)

object PestControlTierOneMonoBlueTerrorReadiness {
    val mainCounts: Map<String, Int> get() = MONO_BLUE_TERROR_MAIN.toMap()
    val sideboardCounts: Map<String, Int> get() = MONO_BLUE_TERROR_SIDEBOARD.toMap()
    val expectedUnsupportedMain: Map<String, Int> get() = EXPECTED_UNSUPPORTED_MAIN.toMap()
    val expectedUnsupportedSideboard: Map<String, Int> get() = EXPECTED_UNSUPPORTED_SIDEBOARD.toMap()
    val currentUnsupportedSideboard: Map<String, Int> get() = CURRENT_UNSUPPORTED_SIDEBOARD.toMap()

    fun mainDeck(): Deck = Deck.of(*MONO_BLUE_TERROR_MAIN.map { it.key to it.value }.toTypedArray())

    fun unresolvedMain(registry: CardRegistry): Map<String, Int> =
        MONO_BLUE_TERROR_MAIN.filterKeys { name -> registry.getCard(name) == null }

    fun unresolvedSideboard(registry: CardRegistry): Map<String, Int> =
        MONO_BLUE_TERROR_SIDEBOARD.filterKeys { name -> registry.getCard(name) == null }

    fun validationErrors(
        readiness: TierOneMonoBlueTerrorReadiness,
        registry: CardRegistry? = null,
    ): List<String> = buildList {
        if (readiness.protocolId != PEST_MONO_BLUE_TERROR_PREBOARD_PROTOCOL_ID) add("protocol mismatch")
        if (readiness.opponentPilot != "Serpico_CC") add("opponent pilot mismatch")
        if (readiness.opponentEventDate != "2026-09-20") add("opponent event date mismatch")
        if (readiness.eventSourceUrl != PEST_MONO_BLUE_TERROR_EVENT_SOURCE_URL) add("event source mismatch")
        if (readiness.listSourceUrl != PEST_MONO_BLUE_TERROR_LIST_SOURCE_URL) add("list source mismatch")
        if (readiness.pestMainSha256 != PEST_CONTROL_V10_HASH) add("Pest Control identity mismatch")
        if (MONO_BLUE_TERROR_MAIN.values.sum() != 60 || MONO_BLUE_TERROR_SIDEBOARD.values.sum() != 15) {
            add("Mono-Blue Terror deck quantities must be 60/15")
        }
        if (hashRows(MONO_BLUE_TERROR_MAIN) != readiness.opponentMainSha256) {
            add("Mono-Blue Terror main hash mismatch")
        }
        if (hashRows(MONO_BLUE_TERROR_SIDEBOARD) != readiness.opponentSideboardSha256) {
            add("Mono-Blue Terror sideboard hash mismatch")
        }
        if (hashComplete75(MONO_BLUE_TERROR_MAIN, MONO_BLUE_TERROR_SIDEBOARD) != readiness.opponentComplete75Sha256) {
            add("Mono-Blue Terror complete-75 hash mismatch")
        }
        if (readiness.scope != "PREBOARD_READINESS_ONLY") add("scope must remain preboard readiness only")
        if (readiness.mainSupportStatus != "SUPPORTED_PREBOARD_60") add("main support status mismatch")
        if (readiness.sideboardStatus != "FROZEN_15; NOT_INSTANTIATED; BLOCKED_4_IDENTITIES_11_SLOTS") {
            add("sideboard status mismatch")
        }
        if (readiness.runnerState != TierOneMonoBlueTerrorRunnerState.DISABLED) add("runner must remain disabled")
        if (readiness.officialGamesAuthorized != 0) add("official games are not authorized")
        if (readiness.officialSeedsGenerated != 0) add("official seeds must not exist")
        if (readiness.outcomeExposure != 0) add("outcome exposure must remain zero")
        registry?.let { cards ->
            if (unresolvedMain(cards) != EXPECTED_UNSUPPORTED_MAIN) {
                add("Mono-Blue Terror maindeck support audit drift")
            }
            if (unresolvedSideboard(cards) != CURRENT_UNSUPPORTED_SIDEBOARD) {
                add("Mono-Blue Terror sideboard support audit drift")
            }
        }
    }

    fun executionActivationErrors(
        readiness: TierOneMonoBlueTerrorReadiness,
        registry: CardRegistry,
    ): List<String> = buildList {
        addAll(validationErrors(readiness, registry))
        add("no execution runner is defined")
        add("no official seed vector is frozen")
        add("official Mono-Blue Terror games are not authorized")
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
