package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.model.Deck
import java.security.MessageDigest

const val PEST_GRIXIS_PREBOARD_PROTOCOL_ID =
    "PEST_CONTROL_V10_VS_PASQUALE_GRIXIS_AFFINITY_2026_09_07_PREBOARD_V1"
const val PEST_GRIXIS_SOURCE_URL = "https://www.mtgtop8.com/event?e=90623&d=887996&f=PAU"
const val PEST_GRIXIS_MAIN_SHA256 = "2e20ec68c58dda1f913f99e8c97b7458f920a9be18bb4ee2c09784326cc2896c"
const val PEST_GRIXIS_SIDEBOARD_SHA256 = "19c64bb94ef1dbdd953409dede79b02a478e9cf959bb230fe0c67a9b43d3177a"
const val PEST_GRIXIS_COMPLETE_75_SHA256 = "b73fe84ec0dd11961f45cf0ab39f155c92d99636ff1ad961bbc8f4774504bdcd"

private val GRIXIS_MAIN = linkedMapOf(
    "Drossforge Bridge" to 3, "Great Furnace" to 2, "Mistvault Bridge" to 3,
    "Seat of the Synod" to 3, "Silverbluff Bridge" to 2, "Swamp" to 1,
    "Vault of Whispers" to 4, "Krark-Clan Shaman" to 2, "Myr Enforcer" to 4,
    "Refurbished Familiar" to 4, "Utrom Monitor" to 3, "Cast Down" to 3,
    "Fanatical Offering" to 2, "Galvanic Blast" to 4, "Reckoner's Bargain" to 4,
    "Thoughtcast" to 4, "Toxin Analysis" to 2, "Blood Fountain" to 3,
    "Ichor Wellspring" to 4, "Makeshift Munitions" to 1, "Nihil Spellbomb" to 2,
)

private val GRIXIS_SIDEBOARD = linkedMapOf(
    "Blue Elemental Blast" to 2, "Duress" to 4, "Mesmeric Fiend" to 2,
    "Nihil Spellbomb" to 1, "Red Elemental Blast" to 4, "Unexpected Fangs" to 2,
)

enum class TierOneGrixisRunnerState { DISABLED }

data class TierOneGrixisReadiness(
    val protocolId: String = PEST_GRIXIS_PREBOARD_PROTOCOL_ID,
    val opponentPilot: String = "Giandomenico Pasquale",
    val opponentEvent: String = "1.tappa Lega Str Autumn; 16 players; first place",
    val opponentEventDate: String = "2026-09-07",
    val opponentSourceUrl: String = PEST_GRIXIS_SOURCE_URL,
    val pestMainSha256: String = PEST_CONTROL_V10_HASH,
    val grixisMainSha256: String = PEST_GRIXIS_MAIN_SHA256,
    val grixisSideboardSha256: String = PEST_GRIXIS_SIDEBOARD_SHA256,
    val grixisComplete75Sha256: String = PEST_GRIXIS_COMPLETE_75_SHA256,
    val scope: String = "PREBOARD_READINESS_ONLY",
    val sideboardStatus: String = "FROZEN_15; NOT_INSTANTIATED; 11_OF_15_SLOTS_SUPPORTED",
    val runnerState: TierOneGrixisRunnerState = TierOneGrixisRunnerState.DISABLED,
    val officialGamesAuthorized: Int = 0,
    val officialSeedsGenerated: Int = 0,
    val outcomeExposure: Int = 0,
)

object PestControlTierOneGrixisReadiness {
    val mainCounts: Map<String, Int> get() = GRIXIS_MAIN.toMap()
    val sideboardCounts: Map<String, Int> get() = GRIXIS_SIDEBOARD.toMap()

    fun mainDeck(): Deck = Deck.of(*GRIXIS_MAIN.map { it.key to it.value }.toTypedArray())

    fun validationErrors(
        readiness: TierOneGrixisReadiness,
        registry: CardRegistry? = null,
    ): List<String> = buildList {
        if (readiness.protocolId != PEST_GRIXIS_PREBOARD_PROTOCOL_ID) add("protocol mismatch")
        if (readiness.opponentPilot != "Giandomenico Pasquale") add("opponent pilot mismatch")
        if (readiness.opponentEventDate != "2026-09-07") add("opponent event date mismatch")
        if (readiness.opponentSourceUrl != PEST_GRIXIS_SOURCE_URL) add("opponent source mismatch")
        if (readiness.pestMainSha256 != PEST_CONTROL_V10_HASH) add("Pest Control identity mismatch")
        if (GRIXIS_MAIN.values.sum() != 60 || GRIXIS_SIDEBOARD.values.sum() != 15) {
            add("Grixis deck quantities must be 60/15")
        }
        if (hashRows(GRIXIS_MAIN) != readiness.grixisMainSha256) add("Grixis main hash mismatch")
        if (hashRows(GRIXIS_SIDEBOARD) != readiness.grixisSideboardSha256) add("Grixis sideboard hash mismatch")
        if (hashComplete75(GRIXIS_MAIN, GRIXIS_SIDEBOARD) != readiness.grixisComplete75Sha256) {
            add("Grixis complete-75 hash mismatch")
        }
        if (readiness.scope != "PREBOARD_READINESS_ONLY") add("scope must remain preboard readiness only")
        if (readiness.sideboardStatus != "FROZEN_15; NOT_INSTANTIATED; 11_OF_15_SLOTS_SUPPORTED") {
            add("sideboard status mismatch")
        }
        if (readiness.runnerState != TierOneGrixisRunnerState.DISABLED) add("runner must remain disabled")
        if (readiness.officialGamesAuthorized != 0) add("official games are not authorized")
        if (readiness.officialSeedsGenerated != 0) add("official seeds must not exist")
        if (readiness.outcomeExposure != 0) add("outcome exposure must remain zero")
        registry?.let { cards ->
            GRIXIS_MAIN.keys.forEach { name ->
                if (cards.getCard(name) == null) add("unresolved Grixis maindeck card: $name")
            }
        }
    }

    fun executionActivationErrors(
        readiness: TierOneGrixisReadiness,
        registry: CardRegistry,
    ): List<String> = buildList {
        addAll(validationErrors(readiness, registry))
        add("no execution runner is defined")
        add("no official seed vector is frozen")
        add("official Grixis games are not authorized")
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
