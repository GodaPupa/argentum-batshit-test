package com.wingedsheep.gym.matchup

import com.wingedsheep.sdk.model.CardEntry
import com.wingedsheep.sdk.model.Deck
import java.security.MessageDigest

/** The six existing identities in the bounded Pest Tier-1 program. */
enum class PestTierOneDeck {
    PEST_CONTROL,
    MONO_RED_MADNESS,
    GRIXIS_AFFINITY,
    MONO_BLUE_TERROR,
    MONSTER_TRON,
    SPY_COMBO,
}

/** Exact simultaneous exchanges; this type has no access to a game, seed, hand or outcome. */
data class PestPostboardExchange(
    val toMain: Map<String, Int>,
    val toSideboard: Map<String, Int>,
)

/** Every identity that a separately reviewed boarding freeze must pin. */
data class PestPostboardDeckBinding(
    val deck: PestTierOneDeck,
    val originalMainSha256: String,
    val originalSideboardSha256: String,
    val originalComplete75Sha256: String,
    val postboardMainSha256: String,
    val postboardSideboardSha256: String,
    val postboardComplete75Sha256: String,
)

/**
 * A reviewable deterministic construction, not an accepted policy or gameplay authorization.
 * The original row order is retained and newly introduced names use the donor zone's row order.
 * This makes construction independent of the order in which exchange maps were supplied.
 */
class PreparedPestPostboardDeck internal constructor(
    val binding: PestPostboardDeckBinding,
    main: Map<String, Int>,
    sideboard: Map<String, Int>,
) {
    private val preparedMain = main.toMap()
    private val preparedSideboard = sideboard.toMap()

    val mainCounts: Map<String, Int> get() = preparedMain.toMap()
    val sideboardCounts: Map<String, Int> get() = preparedSideboard.toMap()

    /**
     * Requires the caller's independently frozen exact result before constructing either zone.
     * Matching this value proves byte identity only; all runtime/admission gates remain separate.
     */
    fun toDeck(frozenBinding: PestPostboardDeckBinding): Deck {
        require(frozenBinding == binding) { "postboard frozen binding mismatch" }
        return Deck(
            cards = preparedMain.flatMap { (name, count) -> List(count) { name } },
            sideboard = preparedSideboard.flatMap { (name, count) -> List(count) { CardEntry(name) } },
        )
    }
}

/** Pure 60/15 exchange compiler over the existing frozen identities; it never initializes a game. */
object PestControlTierOnePostboardExchange {
    fun prepare(deck: PestTierOneDeck, exchange: PestPostboardExchange): PreparedPestPostboardDeck {
        val source = source(deck)
        source.verify()
        val incoming = exchange.toMain.toMap()
        val outgoing = exchange.toSideboard.toMap()
        validateExchange(incoming, source.sideboard, "sideboard")
        validateExchange(outgoing, source.main, "main")
        require(incoming.keys.intersect(outgoing.keys).isEmpty()) {
            "a name must not move in both directions"
        }
        require(incoming.values.sum() == outgoing.values.sum()) { "unbalanced 60/15 exchange" }

        val main = exchangeZone(source.main, source.sideboard, outgoing, incoming)
        val sideboard = exchangeZone(source.sideboard, source.main, incoming, outgoing)
        require(main.values.sum() == 60 && sideboard.values.sum() == 15) { "postboard size drift" }
        require(combinedCounts(main, sideboard) == combinedCounts(source.main, source.sideboard)) {
            "complete-75 multiset drift"
        }
        return PreparedPestPostboardDeck(
            binding = PestPostboardDeckBinding(
                deck = deck,
                originalMainSha256 = source.mainSha256,
                originalSideboardSha256 = source.sideboardSha256,
                originalComplete75Sha256 = source.complete75Sha256,
                postboardMainSha256 = hashRows(main),
                postboardSideboardSha256 = hashRows(sideboard),
                postboardComplete75Sha256 = hashComplete75(main, sideboard),
            ),
            main = main,
            sideboard = sideboard,
        )
    }

    private fun validateExchange(rows: Map<String, Int>, donor: Map<String, Int>, zone: String) {
        require(rows.size <= 15) { "too many exchange rows" }
        rows.forEach { (name, count) ->
            require(count in 1..15) { "exchange quantity must be between 1 and 15" }
            require(name in donor) { "unknown $zone exchange card: $name" }
            require(count <= donor.getValue(name)) { "overdrawn $zone exchange card: $name" }
        }
        require(rows.values.sum() <= 15) { "exchange exceeds the 15-card sideboard" }
    }

    private fun exchangeZone(
        original: Map<String, Int>,
        donor: Map<String, Int>,
        outgoing: Map<String, Int>,
        incoming: Map<String, Int>,
    ): Map<String, Int> = buildMap {
        (original.keys + donor.keys).forEach { name ->
            val count = original.getOrDefault(name, 0) - outgoing.getOrDefault(name, 0) +
                incoming.getOrDefault(name, 0)
            if (count > 0) put(name, count)
        }
    }

    private fun combinedCounts(main: Map<String, Int>, sideboard: Map<String, Int>): Map<String, Int> =
        (main.keys + sideboard.keys).associateWith { name ->
            main.getOrDefault(name, 0) + sideboard.getOrDefault(name, 0)
        }

    private data class SourceDeck(
        val main: Map<String, Int>,
        val sideboard: Map<String, Int>,
        val mainSha256: String,
        val sideboardSha256: String,
        val complete75Sha256: String,
    ) {
        fun verify() {
            require(main.values.all { it > 0 } && sideboard.values.all { it > 0 }) {
                "frozen source has nonpositive quantities"
            }
            require(main.values.sumOf { it.toLong() } == 60L && sideboard.values.sumOf { it.toLong() } == 15L) {
                "frozen source must contain exactly 60/15"
            }
            require(hashRows(main) == mainSha256) { "frozen original main identity drift" }
            require(hashRows(sideboard) == sideboardSha256) { "frozen original sideboard identity drift" }
            require(hashComplete75(main, sideboard) == complete75Sha256) { "frozen original 75 identity drift" }
        }
    }

    private fun source(deck: PestTierOneDeck): SourceDeck = when (deck) {
        PestTierOneDeck.PEST_CONTROL -> SourceDeck(
            PestControlPreboardDecks.pestMainCounts, PestControlPreboardDecks.pestSideboardCounts,
            PEST_CONTROL_V10_HASH, PEST_CONTROL_V10_SIDEBOARD_HASH, PEST_CONTROL_V10_75_HASH,
        )
        PestTierOneDeck.MONO_RED_MADNESS -> SourceDeck(
            PestControlPreboardDecks.monoRedMainCounts, PestControlPreboardDecks.monoRedSideboardCounts,
            SOTERX_MONO_RED_MAIN_HASH, SOTERX_MONO_RED_SIDEBOARD_HASH, SOTERX_MONO_RED_75_HASH,
        )
        PestTierOneDeck.GRIXIS_AFFINITY -> SourceDeck(
            PestControlTierOneGrixisReadiness.mainCounts, PestControlTierOneGrixisReadiness.sideboardCounts,
            PEST_GRIXIS_MAIN_SHA256, PEST_GRIXIS_SIDEBOARD_SHA256, PEST_GRIXIS_COMPLETE_75_SHA256,
        )
        PestTierOneDeck.MONO_BLUE_TERROR -> SourceDeck(
            PestControlTierOneMonoBlueTerrorReadiness.mainCounts, PestControlTierOneMonoBlueTerrorReadiness.sideboardCounts,
            PEST_MONO_BLUE_TERROR_MAIN_SHA256, PEST_MONO_BLUE_TERROR_SIDEBOARD_SHA256,
            PEST_MONO_BLUE_TERROR_COMPLETE_75_SHA256,
        )
        PestTierOneDeck.MONSTER_TRON -> SourceDeck(
            PestControlTierOneMonsterTronAdmission.mainCounts, PestControlTierOneMonsterTronAdmission.sideboardCounts,
            PEST_MONSTER_TRON_MAIN_SHA256, PEST_MONSTER_TRON_SIDEBOARD_SHA256, PEST_MONSTER_TRON_COMPLETE_75_SHA256,
        )
        PestTierOneDeck.SPY_COMBO -> SourceDeck(
            PestControlTierOneSpyComboAdmission.mainCounts, PestControlTierOneSpyComboAdmission.sideboardCounts,
            PEST_SPY_COMBO_MAIN_SHA256, PEST_SPY_COMBO_SIDEBOARD_SHA256, PEST_SPY_COMBO_COMPLETE_75_SHA256,
        )
    }

    private fun hashRows(rows: Map<String, Int>): String = sha256(
        rows.entries.joinToString("\n", postfix = "\n") { (name, count) -> "$name,$count" },
    )

    private fun hashComplete75(main: Map<String, Int>, sideboard: Map<String, Int>): String = sha256(
        buildString {
            appendLine("MAIN")
            main.forEach { (name, count) -> appendLine("$name,$count") }
            appendLine("SIDEBOARD")
            sideboard.forEach { (name, count) -> appendLine("$name,$count") }
        },
    )

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
