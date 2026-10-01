package com.wingedsheep.gym.pest

import com.wingedsheep.ai.engine.EngineAiPlayerController
import com.wingedsheep.ai.llm.BottomCardsInfo
import com.wingedsheep.ai.llm.CardSummary
import com.wingedsheep.ai.llm.MulliganInfo
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.json.*
import java.security.MessageDigest

/** Only the two accepted raw input byte streams may construct this lookup. No oracle is run here. */
internal class PestPhaseBAcceptedTruth private constructor(
    private val receiver: PestMonsterLondonM5TruthReceiving,
) {
    fun bind(hand: List<PestLondonCardFacts>, m2: Boolean): List<PestLondonCardFacts> {
        require(hand.all { it.deterministicDevelopmentPayable == null }) { "Caller-supplied M5 truth is forbidden" }
        return receiver.bind(hand, m2)
    }

    companion object {
        const val BASE_SHA = "8d7e3e97a4dc70aab696b2d27012e410d57598d477f7b97689f4de5b442f348f"
        const val SUPPLEMENT_SHA = "1bbe8b92f01e69cd679b36375ef4168893267e783441ba5d1b81c76166f0cf4a"
        private fun load(raw: ByteArray, hash: String, schema: String): JsonObject {
            require(raw.size in 1..(16 * 1024 * 1024))
            val detached = raw.copyOf()
            require(phaseBHash(detached) == hash) { "Accepted truth bytes differ" }
            return Json.parseToJsonElement(detached.toString(Charsets.UTF_8)).jsonObject.also {
                require(it.getValue("schema").jsonPrimitive.content == schema)
            }
        }
        private fun flag(value: JsonElement): Boolean {
            val p = value.jsonPrimitive
            require(!p.isString && p.content in setOf("true", "false"))
            return p.boolean
        }
        private fun atom(row: JsonObject): Pair<PestLondonM5TruthKey, Boolean> {
            require(row.keys == setOf("atom", "development_functional"))
            val a = row.getValue("atom").jsonObject
            require(a.keys == setOf("physical_lands", "m2_opening_candidate", "early_spell"))
            val lands = a.getValue("physical_lands").jsonArray.map { element ->
                val pair = element.jsonArray
                require(pair.size == 2 && pair[0].jsonPrimitive.isString && !pair[1].jsonPrimitive.isString)
                val name = pair[0].jsonPrimitive.content
                val count = pair[1].jsonPrimitive.int
                require(name.isNotBlank() && '\u0000' !in name && count in 1..7 && pair[1].jsonPrimitive.content == count.toString())
                name to count
            }
            require(lands == lands.sortedBy { it.first } && lands.map { it.first }.distinct().size == lands.size)
            require(lands.sumOf { it.second } <= 7)
            val spell = a.getValue("early_spell").jsonPrimitive
            require(spell.isString && spell.content.isNotBlank() && '\u0000' !in spell.content)
            return PestLondonM5TruthKey(lands.toList(), flag(a.getValue("m2_opening_candidate")), spell.content) to
                flag(row.getValue("development_functional"))
        }
        fun fromAcceptedBytes(base: ByteArray, supplement: ByteArray): PestPhaseBAcceptedTruth {
            val a = load(base, BASE_SHA, "pest-monster-london-m5-atomic-truth-bank-v1")
            val b = load(supplement, SUPPLEMENT_SHA, "pest-current-pair-phase-u-m5-truth-supplement-v1")
            mapOf(
                "raw_controller_blob" to "4f020ea2406e9bd6a87ec389685b48f15df4c5a3",
                "source_unbanked_atoms_sha256" to "a00aef9df49106a2611079ece2824bf4e97d40c7e89207b16bc1a9e05d7ae287",
                "source_worklist_sha256" to "2174410ee36dc0e74d0d7f4335606de7236aa845ef43bab409259a3056133738",
            ).forEach { (key, value) -> require(b.getValue(key).jsonPrimitive.content == value) }
            val merged = linkedMapOf<PestLondonM5TruthKey, Boolean>()
            listOf(a to 6850, b to 692).forEach { (document, count) ->
                val rows = document.getValue("rows").jsonArray
                require(rows.size == count)
                rows.forEach { element ->
                    val (key, value) = atom(element.jsonObject)
                    require(!merged.containsKey(key)) { "Duplicate/overlapping accepted atom; no deduplication" }
                    merged[key] = value
                }
            }
            require(merged.size == 7542)
            return PestPhaseBAcceptedTruth(PestMonsterLondonM5TruthReceiving(merged.toMap()))
        }
    }
}

internal data class PestPhaseBRowContext(
    val deck: String, val seat: Int, val onPlay: Boolean, val mulligans: Int,
    val nameMultisetOrdinal: Long, val physicalRepresentativeOrdinal: Long,
)
internal data class PestPhaseBRowComparison(
    val context: PestPhaseBRowContext,
    val orderedHandIds: List<String>, val orderedHandNames: List<String>,
    val stateSha256: String, val rawKeep: Boolean, val lawfulKeep: Boolean?,
    val rawBottomIds: List<String>, val lawfulBottomIds: List<String>?,
    val keepReason: String, val bottomReason: String,
) {
    val disposition: String get() = when {
        lawfulKeep == null || lawfulBottomIds == null -> "UNQUALIFIED_ROW"
        rawKeep != lawfulKeep || rawBottomIds != lawfulBottomIds -> "MISMATCH"
        else -> "MATCH_ROW_ONLY_NOT_BANK_COVERAGE"
    }
}

/**
 * Trusted production row comparison, NOT an actor or an admitted bank runner. The real controller
 * sees the bound synthetic state; the lawful path sees only the frozen own-deck multiset and own
 * visible hand facts. No GameInitializer, action submission, entropy, file writer or CLI is used.
 * The future gate must authenticate loaded source/runtime and supply every frozen bank row and
 * physical representative, preserve exceptions/mismatches, and prove aggregate coverage. Merely
 * calling this function does not provide that coverage or source authentication.
 */
internal object PestPhaseBProductionRowComparator {
    private val json = Json { serializersModule = engineSerializersModule; encodeDefaults = true; allowStructuredMapKeys = true }
    private val colors = mapOf(Color.WHITE to 'W', Color.BLUE to 'U', Color.BLACK to 'B', Color.RED to 'R', Color.GREEN to 'G')
    // Exact public land facts from the accepted finite-bank construction, not library order.
    private val landColors = mapOf(
        "Forest" to setOf('G'), "Swamp" to setOf('B'), "Jungle Hollow" to setOf('B','G'),
        "Bojuka Bog" to setOf('B'), "Conduit Pylons" to setOf('W','U','B','R','G'),
        "Haunted Fengraf" to emptySet(), "Urza's Mine" to emptySet(),
        "Urza's Power Plant" to emptySet(), "Urza's Tower" to emptySet(),
    )
    private val pest = linkedMapOf(
        "Essence Warden" to 4, "Carrier Thrall" to 4, "Blood Researcher" to 4, "Pest Mascot" to 4,
        "Fierce Witchstalker" to 4, "Generous Ent" to 3, "Follow the Lumarets" to 4, "Weather the Storm" to 4,
        "Cast Down" to 4, "Bone Shards" to 2, "Chainer's Edict" to 2, "Forest" to 10, "Swamp" to 7, "Jungle Hollow" to 4,
    )
    private val monster = linkedMapOf(
        "Rooftop Percher" to 2, "Generous Ent" to 2, "Boulderbranch Golem" to 2, "Bramble Wurm" to 4,
        "Maelstrom Colossus" to 4, "Ancient Stirrings" to 4, "Crop Rotation" to 3, "Breath Weapon" to 2,
        "Unfathomable Truths" to 2, "Barrels of Blasting Jelly" to 1, "Candy Trail" to 2, "Expedition Map" to 4,
        "Giant's Boulder" to 4, "Bonder's Ornament" to 2, "Pinnacle Kill-Ship" to 4, "Bojuka Bog" to 1,
        "Conduit Pylons" to 2, "Forest" to 2, "Haunted Fengraf" to 1, "Urza's Mine" to 4,
        "Urza's Power Plant" to 4, "Urza's Tower" to 4,
    )
    private fun frozenDeck(name: String): Map<String, Int> {
        val (deck, expected) = when (name) {
            "pest" -> pest to "7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5"
            "monster" -> monster to "79ffc53ac331beafeb1ef4510ce174d01fb2685d963a4c1485f04edbf47c064f"
            else -> error("Unfrozen deck")
        }
        require(deck.values.sum() == 60)
        require(phaseBHash(deck.entries.joinToString("") { "${it.key},${it.value}\n" }.toByteArray()) == expected)
        return deck.toMap()
    }

    fun compareRow(
        context: PestPhaseBRowContext, state: GameState, roster: List<EntityId>,
        registry: CardRegistry, acceptedTruth: PestPhaseBAcceptedTruth,
    ): PestPhaseBRowComparison {
        require(context.seat in 0..1 && context.mulligans in 0..2)
        val denominator = when (context.deck) { "pest" -> 71271L; "monster" -> 882297L; else -> error("Unfrozen deck") }
        require(context.nameMultisetOrdinal in 0 until denominator && context.physicalRepresentativeOrdinal >= 0)
        val players = roster.toList()
        require(players.size == 2 && players.distinct().size == 2 && state.turnOrder.toSet() == players.toSet())
        val player = players[context.seat]
        require(context.onPlay == (state.activePlayerId == player)) { "Start context differs from actual state" }
        require(state.format == Format.Standard && !state.gameOver && state.turnNumber == 1)
        require(state.stack.isEmpty() && state.pendingDecision == null && state.getBattlefield().isEmpty())
        val hand = state.getZone(ZoneKey(player, Zone.HAND)).toList()
        val library = state.getZone(ZoneKey(player, Zone.LIBRARY)).toList()
        require(hand.size == 7 && library.size == 53 && (hand + library).distinct().size == 60)
        val deck = frozenDeck(context.deck)
        fun card(id: EntityId): CardComponent = requireNotNull(state.getEntity(id)?.get<CardComponent>())
        require((hand + library).groupingBy { card(it).name }.eachCount() == deck) { "Actual own 60 differs from frozen submitted list" }
        val summaries = hand.associateWith { id -> card(id).let {
            CardSummary(name = it.name, manaCost = it.manaCost.toString(), typeLine = it.typeLine.toString(), oracleText = it.oracleText)
        } }
        val soleLand = hand.map(::card).filter { it.typeLine.isLand }.singleOrNull()
        val facts = hand.map { id ->
            val c = card(id)
            require(c.typeLine.isLand == landColors.containsKey(c.name)) { "Frozen land identity drift" }
            PestLondonCardFacts(
                id = id.value, name = c.name, isLand = c.typeLine.isLand, cmc = c.manaCost.cmc,
                colorsRequired = c.manaCost.colors.mapTo(linkedSetOf()) { colors.getValue(it) },
                colorsProduced = landColors[c.name] ?: emptySet(),
                typedCyclingTargets = if (c.name == "Generous Ent") setOf("Forest") else emptySet(),
                typedCyclingPayableBySoleLand = c.name == "Generous Ent" && soleLand != null && soleLand.name !in setOf("Jungle Hollow", "Bojuka Bog"),
            )
        }
        val preliminary = PestMonsterLondonPredicateVectorExtractor.extract(deck, facts, context.mulligans, context.mulligans)
        val bound = acceptedTruth.bind(facts, preliminary.guaranteedSecondLandAccess)
        val vector = PestMonsterLondonPredicateVectorExtractor.extract(deck, bound, context.mulligans, context.mulligans)
        require(vector.guaranteedSecondLandAccess == preliminary.guaranteedSecondLandAccess)
        val lawfulKeep = PestPhaseBKeepBottomActor.keep(vector)
        val lawfulBottom = PestPhaseBKeepBottomActor.bottom(vector, hand.map { it.value }, context.mulligans)
        val before = json.encodeToString(GameState.serializer(), state)
        try {
            val raw = EngineAiPlayerController(registry, player, gameStateProvider = { state })
            val rawKeep = raw.decideMulligan(MulliganInfo(hand, context.mulligans, context.mulligans, summaries, context.onPlay))
            val rawBottom = raw.chooseBottomCards(BottomCardsInfo(hand, context.mulligans, summaries)).map { it.value }
            require(rawBottom.size == context.mulligans && rawBottom.distinct().size == rawBottom.size && rawBottom.all { id -> hand.any { it.value == id } })
            return PestPhaseBRowComparison(context, hand.map { it.value }, hand.map { card(it).name },
                phaseBHash(before.toByteArray()), rawKeep, lawfulKeep.keep, rawBottom, lawfulBottom.orderedIds,
                lawfulKeep.reason, lawfulBottom.reason)
        } finally {
            require(json.encodeToString(GameState.serializer(), state) == before) { "Raw comparison mutated the supplied fixture state" }
        }
    }
}

private fun phaseBHash(raw: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(raw).joinToString("") { "%02x".format(it.toInt() and 255) }
