package com.wingedsheep.gym.matchup

import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.ai.engine.EngineAiPlayerController
import com.wingedsheep.ai.llm.BottomCardsInfo
import com.wingedsheep.ai.llm.CardSummary
import com.wingedsheep.ai.llm.MulliganInfo
import com.wingedsheep.engine.core.BottomCards
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.TakeMulligan
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.gym.ExactlyOneSubmissionResult
import com.wingedsheep.gym.GameEnvironment
import com.wingedsheep.gym.contract.ObservationBuilder
import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import java.security.MessageDigest

const val PEST_MONO_RED_PREBOARD_PROTOCOL_ID =
    "PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1"
const val PEST_MATCHUP_SCHEMA = "pest-control-preboard-matchup@v1"
const val PEST_CONTROL_V10_HASH = "7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5"
const val PEST_CONTROL_V10_SIDEBOARD_HASH = "c1910468c228662b21647eb7ca8481cd11691906e56e0a37990e00f22886368c"
const val PEST_CONTROL_V10_75_HASH = "2927737eb084657cda58fd1877db933037c383273062f0bd209c7ff3046c1cf5"
const val SOTERX_MONO_RED_MAIN_HASH = "38c7850d1b9b070637502cedfffc6116d3504a525db8b51223505d7935134258"
const val SOTERX_MONO_RED_SIDEBOARD_HASH = "d0aab592e6c82ad019dba0eadc028db75ef5cf13c9b3e4e0531a04d513bfc77a"
const val SOTERX_MONO_RED_75_HASH = "e9ff7ecbdbc8f41ebe526fe8fee4f87f706e0630491f9d78121677922fbd647d"
const val PREBOARD_MATCH_RESULT = "NOT_APPLICABLE_PREBOARD_INDEPENDENT_GAMES"

/** Fixed construction-test entropy. It is not an experimental seed and must never enter a seed registry. */
const val NONEXPERIMENTAL_GATE4_FIXTURE_ENTROPY: Long = 0x5045_5354_4734L

private val PEST_MAIN = linkedMapOf(
    "Essence Warden" to 4, "Carrier Thrall" to 4, "Blood Researcher" to 4,
    "Pest Mascot" to 4, "Fierce Witchstalker" to 4, "Generous Ent" to 3,
    "Follow the Lumarets" to 4, "Weather the Storm" to 4, "Cast Down" to 4,
    "Bone Shards" to 2, "Chainer's Edict" to 2, "Forest" to 10,
    "Swamp" to 7, "Jungle Hollow" to 4,
)

private val PEST_SIDEBOARD = linkedMapOf(
    "Tamiyo's Safekeeping" to 4, "Nature's Claim" to 3, "Snuff Out" to 3,
    "Suffocating Fumes" to 3, "Pulse of Murasa" to 2,
)

private val RED_MAIN = linkedMapOf(
    "Mountain" to 20, "Guttersnipe" to 4, "Kessig Flamebreather" to 3,
    "Sneaky Snacker" to 4, "Faithless Looting" to 2, "Fiery Temper" to 4,
    "Fireblast" to 4, "Grab the Prize" to 4, "Highway Robbery" to 4,
    "Lava Dart" to 4, "Lightning Bolt" to 4, "Melded Moxite" to 3,
)

private val RED_SIDEBOARD = linkedMapOf(
    "Campfire" to 3, "Pyroblast" to 2, "Red Elemental Blast" to 3,
    "Relic of Progenitus" to 3, "Smash to Smithereens" to 4,
)

object PestControlPreboardDecks {
    val pestMainCounts: Map<String, Int> get() = PEST_MAIN.toMap()
    val pestSideboardCounts: Map<String, Int> get() = PEST_SIDEBOARD.toMap()
    val monoRedMainCounts: Map<String, Int> get() = RED_MAIN.toMap()
    val monoRedSideboardCounts: Map<String, Int> get() = RED_SIDEBOARD.toMap()

    fun pestMain(): Deck = Deck.of(*PEST_MAIN.map { it.key to it.value }.toTypedArray())
    fun monoRedMain(): Deck = Deck.of(*RED_MAIN.map { it.key to it.value }.toTypedArray())

    fun verifyFrozenIdentities() {
        require(
            PEST_MAIN.values.sum() == 60 && PEST_SIDEBOARD.values.sum() == 15 &&
                RED_MAIN.values.sum() == 60 && RED_SIDEBOARD.values.sum() == 15
        )
        require(hashRows(PEST_MAIN) == PEST_CONTROL_V10_HASH)
        require(hashRows(PEST_SIDEBOARD) == PEST_CONTROL_V10_SIDEBOARD_HASH)
        require(hashComplete75(PEST_MAIN, PEST_SIDEBOARD) == PEST_CONTROL_V10_75_HASH)
        require(hashRows(RED_MAIN) == SOTERX_MONO_RED_MAIN_HASH)
        require(hashRows(RED_SIDEBOARD) == SOTERX_MONO_RED_SIDEBOARD_HASH)
        require(hashComplete75(RED_MAIN, RED_SIDEBOARD) == SOTERX_MONO_RED_75_HASH)
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
}

enum class PestSeat(val index: Int) { SEAT_ZERO(0), SEAT_ONE(1) }
enum class StartingDeck { PEST_CONTROL, MONO_RED_MADNESS }

@Serializable
data class MatchupEnvironmentIdentity(
    val javaVersion: String,
    val javaVendor: String,
    val osName: String,
    val osArch: String,
    val locale: String,
    val timezone: String,
) {
    companion object {
        fun current() = MatchupEnvironmentIdentity(
            javaVersion = System.getProperty("java.version"),
            javaVendor = System.getProperty("java.vendor"),
            osName = System.getProperty("os.name"),
            osArch = System.getProperty("os.arch"),
            locale = java.util.Locale.getDefault().toLanguageTag(),
            timezone = java.util.TimeZone.getDefault().id,
        )
    }
}

@Serializable
data class MatchupProvenance(
    val protocolId: String = PEST_MONO_RED_PREBOARD_PROTOCOL_ID,
    val schema: String = PEST_MATCHUP_SCHEMA,
    val sourceCommit: String,
    val pestControlMainSha256: String = PEST_CONTROL_V10_HASH,
    val pestControlSideboardSha256: String = PEST_CONTROL_V10_SIDEBOARD_HASH,
    val pestControlComplete75Sha256: String = PEST_CONTROL_V10_75_HASH,
    val pestControlSideboardStatus: String = "FROZEN_15; NOT_INSTANTIATED",
    val monoRedMainSha256: String = SOTERX_MONO_RED_MAIN_HASH,
    val monoRedSideboardSha256: String = SOTERX_MONO_RED_SIDEBOARD_HASH,
    val monoRedComplete75Sha256: String = SOTERX_MONO_RED_75_HASH,
    val opponentPilot: String = "SoterX",
    val opponentEvent: String = "52-player MTGO Pauper Challenge 32 #12854069; first place",
    val opponentSnapshotDate: String = "2026-09-11",
    val opponentSourceUrl: String = "https://mtgdecks.net/Pauper/mono-red-madness-decklist-by-soterx-3083867",
    val sideboardsInstantiated: Boolean = false,
    val scope: String = "PREBOARD_INDEPENDENT_GAME",
    val matchResult: String = PREBOARD_MATCH_RESULT,
    val pestSeat: PestSeat,
    val startingDeck: StartingDeck,
    val pestProfile: String = AiProfile.PRODUCTION_CANDIDATE_EXPIRING.id,
    val monoRedProfile: String = AiProfile.PRODUCTION_CANDIDATE_EXPIRING.id,
    val environment: MatchupEnvironmentIdentity,
    val entropyClassification: String = "NONEXPERIMENTAL_GATE4_FIXTURE",
)

@Serializable
data class OpeningZoneAudit(
    val playerId: EntityId,
    val deckIdentity: String,
    val libraryOrderedCardNames: List<String>,
    val libraryStateDigest: String,
    val handCardNames: List<String>,
    val libraryCount: Int,
    val handCount: Int,
    val sideboardCount: Int,
)

@Serializable
data class MulliganAudit(
    val playerId: EntityId,
    val action: String,
    val mulligansTakenBefore: Int,
    val handBefore: List<String>,
    val bottomedCards: List<String> = emptyList(),
)

@Serializable
data class PriorityAudit(
    val sequence: Int,
    val turn: Int,
    val phase: String,
    val step: String,
    val activePlayerId: EntityId?,
    val priorityPlayerId: EntityId?,
    val actingPlayerId: EntityId,
    val pendingDecisionType: String?,
    val playerVisibleStateDigest: String,
    val omniscientAuditStateDigest: String,
    val legalActionSha256: String,
    val selectedAction: JsonElement,
    val selectedTargets: List<String>,
    val selectedModes: List<Int>,
    val costsAndPayments: JsonElement,
    val beforeStateDigest: String,
    val afterStateDigest: String,
    val beforeAuditState: AuditStateSnapshot,
    val afterAuditState: AuditStateSnapshot,
    val emittedEvents: List<JsonElement>,
    val accepted: Boolean,
    val rejectionReason: String? = null,
    val fallbackUsed: Boolean = false,
)

@Serializable
data class AuditZoneSnapshot(
    val zone: String,
    val orderedCards: List<String>,
)

@Serializable
data class AuditPlayerSnapshot(
    val playerId: EntityId,
    val life: Int,
    val mana: String,
    val zones: List<AuditZoneSnapshot>,
)

@Serializable
data class AuditStateSnapshot(
    val turn: Int,
    val phase: String,
    val step: String,
    val activePlayerId: EntityId?,
    val priorityPlayerId: EntityId?,
    val pendingDecisionType: String?,
    val players: List<AuditPlayerSnapshot>,
    val stackBottomToTop: List<String>,
    val gameOver: Boolean,
    val winnerId: EntityId?,
)

@Serializable
enum class ProtocolDefectKind {
    ILLEGAL_OR_REJECTED_ACTION, ILLEGAL_FALLBACK, WEDGE, WATCHDOG_LIMIT, ACTION_LIMIT, TURN_LIMIT,
}

@Serializable
data class ProtocolDefect(
    val kind: ProtocolDefectKind,
    val transition: Int,
    val detail: String,
)

@Serializable
data class TerminalAudit(
    val gameOver: Boolean,
    val winnerId: EntityId?,
    val reason: String?,
    val turn: Int,
    val mechanism: String,
)

@Serializable
data class FrozenMetricDefinitions(
    val meaningfulDevelopment: String = "Earliest turn with a raw zone-change to battlefield for a nonland permanent, a nonzero damage event, a nonzero life-gain event, or a spell/ability that removes an opposing battlefield object.",
    val weatherStabilization: String = "A Weather the Storm cast stabilizes iff raw ordered events through the next priority boundary show life gained and the controller does not lose before receiving that next priority.",
    val productiveRemoval: String = "A removal action is productive iff its raw action targets an opposing object and raw caused events move that object from battlefield, prevent deterministic lethal, or produce a strictly favorable immediate resource transition.",
    val survivalDuration: String = "For Warden, Researcher, or Mascot: number of completed turn changes from its raw battlefield-entry event through its raw battlefield-departure event or terminal event; same-turn departure is zero.",
    val lethalOrLossTurn: String = "The state turn number attached to the first raw GameEndedEvent; mechanism derives only from the preceding ordered damage, draw-empty-library, concession, or explicit loss events.",
)

@Serializable
data class MatchupRawGame(
    val provenance: MatchupProvenance,
    val fixtureId: String,
    val fixtureIsNonexperimental: Boolean = true,
    val excludedFromFutureSeedOverlapRegistry: Boolean = true,
    val openingZones: List<OpeningZoneAudit>,
    val mulligans: List<MulliganAudit>,
    val priorityActions: List<PriorityAudit>,
    val protocolDefect: ProtocolDefect? = null,
    val terminal: TerminalAudit? = null,
    val metricDefinitions: FrozenMetricDefinitions = FrozenMetricDefinitions(),
    val indexedTelemetry: MatchupTelemetryIndex = MatchupTelemetryIndex(),
)

@Serializable
data class TraceReference(val actionSequence: Int, val eventIndex: Int? = null)

/** Index only; every entry points back to canonical raw action/event data and adds no new facts. */
@Serializable
data class MatchupTelemetryIndex(
    val lifeAndDamage: List<TraceReference> = emptyList(),
    val combat: List<TraceReference> = emptyList(),
    val stackAndPriority: List<TraceReference> = emptyList(),
    val zoneChanges: List<TraceReference> = emptyList(),
    val wardenResearcherMascot: List<TraceReference> = emptyList(),
    val lifegainAndPayoffConversion: List<TraceReference> = emptyList(),
    val weatherAndFollow: List<TraceReference> = emptyList(),
    val carrierThrallAndScion: List<TraceReference> = emptyList(),
    val removalAndTargets: List<TraceReference> = emptyList(),
    val manaAndJungleHollow: List<TraceReference> = emptyList(),
    val burnTargets: List<TraceReference> = emptyList(),
    val madnessAndDiscardOutlets: List<TraceReference> = emptyList(),
    val sneakySnackerRecursion: List<TraceReference> = emptyList(),
    val fireblastAndLavaDart: List<TraceReference> = emptyList(),
    val meldedMoxite: List<TraceReference> = emptyList(),
    val guttersnipeAndFlamebreather: List<TraceReference> = emptyList(),
)

/**
 * Exact-one-action protocol recorder. It never calls GameEnvironment.step(), never auto-passes,
 * and never exposes [omniscientObservation] through the player decision context.
 */
class PestControlPreboardSession private constructor(
    val provenance: MatchupProvenance,
    val fixtureId: String,
    val environment: GameEnvironment,
    private val observationBuilder: ObservationBuilder,
    private val openingZones: List<OpeningZoneAudit>,
) {
    private val mulligans = mutableListOf<MulliganAudit>()
    private val actions = mutableListOf<PriorityAudit>()
    private var defect: ProtocolDefect? = null

    data class PlayerDecisionContext(
        val actingPlayerId: EntityId,
        val visibleObservation: TrainingObservation,
        val legalActionSha256: String,
    )

    fun nextDecision(): PlayerDecisionContext {
        check(defect == null) { "Protocol already rejected: $defect" }
        check(!environment.isTerminal) { "Game is terminal" }
        val player = environment.agentToAct ?: error("No player has priority or a pending decision")
        val observation = observationBuilder.build(
            environment.state, player, environment.legalActions(), false
        ).observation as TrainingObservation
        return PlayerDecisionContext(player, observation, hashLegalActions(observation))
    }

    fun submit(action: GameAction, fallbackUsed: Boolean = false): PriorityAudit {
        val isMulliganAction = action is KeepHand || action is TakeMulligan || action is BottomCards
        val context = if (isMulliganAction) decisionContextFor(action.playerId) else nextDecision()
        if (action.playerId != context.actingPlayerId) {
            reject(ProtocolDefectKind.ILLEGAL_OR_REJECTED_ACTION, "Action belongs to ${action.playerId}; ${context.actingPlayerId} must act")
        }
        if (fallbackUsed) reject(ProtocolDefectKind.ILLEGAL_FALLBACK, "Agent fallback was required")
        val before = environment.state
        val beforeAudit = omniscientObservation(before, context.actingPlayerId)
        recordMulliganAction(before, action)
        val outcome = environment.stepExactlyOne(action)
        val accepted = outcome is ExactlyOneSubmissionResult.Applied
        val reason = (outcome as? ExactlyOneSubmissionResult.Rejected)?.reason
        val after = environment.state
        val trace = PriorityAudit(
            sequence = actions.size + 1,
            turn = before.turnNumber,
            phase = before.phase.name,
            step = before.step.name,
            activePlayerId = before.activePlayerId,
            priorityPlayerId = before.priorityPlayerId,
            actingPlayerId = context.actingPlayerId,
            pendingDecisionType = before.pendingDecision?.let { it::class.simpleName },
            playerVisibleStateDigest = context.visibleObservation.stateDigest,
            omniscientAuditStateDigest = beforeAudit.stateDigest,
            legalActionSha256 = context.legalActionSha256,
            selectedAction = PROTOCOL_JSON.encodeToJsonElement(GameAction.serializer(), action),
            selectedTargets = actionJsonStrings(action, "targets"),
            selectedModes = actionJsonInts(action, "chosenModes"),
            costsAndPayments = actionCosts(action),
            beforeStateDigest = beforeAudit.stateDigest,
            afterStateDigest = omniscientObservation(after, context.actingPlayerId).stateDigest,
            beforeAuditState = auditSnapshot(before),
            afterAuditState = auditSnapshot(after),
            emittedEvents = environment.lastStepEvents.map { PROTOCOL_JSON.encodeToJsonElement(GameEvent.serializer(), it) },
            accepted = accepted,
            rejectionReason = reason,
            fallbackUsed = fallbackUsed,
        )
        actions += trace
        if (!accepted) reject(ProtocolDefectKind.ILLEGAL_OR_REJECTED_ACTION, reason ?: "Rejected without reason")
        return trace
    }

    fun reject(kind: ProtocolDefectKind, detail: String): Nothing {
        defect = ProtocolDefect(kind, actions.size, detail)
        throw MatchupProtocolRejected(defect!!)
    }

    fun enforceLimits(maxActions: Int, maxTurns: Int) {
        if (actions.size >= maxActions) reject(ProtocolDefectKind.ACTION_LIMIT, "Reached $maxActions actions")
        if (environment.turnNumber > maxTurns) reject(ProtocolDefectKind.TURN_LIMIT, "Reached turn ${environment.turnNumber}")
    }

    /** Applies the production London-mulligan and bottoming policies through exact-one actions. */
    fun driveValidatedLondonMulligans(controllers: Map<EntityId, EngineAiPlayerController>) {
        var transitions = 0
        for (mulliganPlayer in environment.state.turnOrder) {
            val controller = controllers[mulliganPlayer]
                ?: error("No validated mulligan controller for $mulliganPlayer")
            while (true) {
                if (++transitions > 30) reject(ProtocolDefectKind.WEDGE, "London mulligan did not settle")
                val state = environment.state
                val component = state.getEntity(mulliganPlayer)
                        ?.get<com.wingedsheep.engine.state.components.player.MulliganStateComponent>()
                        ?: error("Player lacks MulliganStateComponent")
                if (component.hasKept) break
                val hand = state.getHand(mulliganPlayer)
                val summaries = cardSummaries(state, hand)
                val keep = controller.decideMulligan(
                    MulliganInfo(
                        hand = hand,
                        mulliganCount = component.mulligansTaken,
                        cardsToPutOnBottom = component.cardsToBottom,
                        cards = summaries,
                        isOnThePlay = state.turnOrder.first() == mulliganPlayer,
                    )
                )
                submit(if (keep) KeepHand(mulliganPlayer) else TakeMulligan(mulliganPlayer))
            }
        }
        for (mulliganPlayer in environment.state.turnOrder) {
            val state = environment.state
            val component = state.getEntity(mulliganPlayer)
                ?.get<com.wingedsheep.engine.state.components.player.MulliganStateComponent>() ?: continue
            if (component.cardsToBottom == 0) continue
            val hand = state.getHand(mulliganPlayer)
            val bottom = controllers.getValue(mulliganPlayer).chooseBottomCards(
                BottomCardsInfo(hand, component.cardsToBottom, cardSummaries(state, hand))
            )
            submit(BottomCards(mulliganPlayer, bottom))
        }
    }

    fun rawGame(): MatchupRawGame = MatchupRawGame(
        provenance = provenance,
        fixtureId = fixtureId,
        openingZones = openingZones,
        mulligans = mulligans.toList(),
        priorityActions = actions.toList(),
        protocolDefect = defect,
        terminal = environment.state.takeIf { it.gameOver }?.let { terminalAudit(it, environment.lastStepEvents) },
        indexedTelemetry = indexTelemetry(actions),
    )

    private fun recordMulliganAction(state: GameState, action: GameAction) {
        val label = when (action::class.simpleName) {
            "KeepHand" -> "KEEP"
            "TakeMulligan" -> "MULLIGAN"
            "BottomCards" -> "BOTTOM"
            else -> return
        }
        val player = action.playerId
        val count = state.getEntity(player)
            ?.get<com.wingedsheep.engine.state.components.player.MulliganStateComponent>()?.mulligansTaken ?: 0
        mulligans += MulliganAudit(
            playerId = player,
            action = label,
            mulligansTakenBefore = count,
            handBefore = state.getHand(player).map { cardName(state, it) },
            bottomedCards = (action as? BottomCards)?.cardIds?.map { cardName(state, it) }.orEmpty(),
        )
    }

    private fun omniscientObservation(state: GameState, perspective: EntityId): TrainingObservation =
        observationBuilder.build(
            state, perspective, environment.legalActions(), true
        ).observation as TrainingObservation

    private fun decisionContextFor(player: EntityId): PlayerDecisionContext {
        val observation = observationBuilder.build(
            environment.state, player, environment.legalActions(), false
        ).observation as TrainingObservation
        return PlayerDecisionContext(player, observation, hashLegalActions(observation))
    }

    companion object {
        fun fixture(
            registry: CardRegistry,
            sourceCommit: String,
            pestSeat: PestSeat,
            startingDeck: StartingDeck,
            fixtureId: String,
            fixtureEntropy: Long = NONEXPERIMENTAL_GATE4_FIXTURE_ENTROPY,
        ): PestControlPreboardSession {
            require(fixtureId.startsWith("NONEXPERIMENTAL_GATE4_"))
            PestControlPreboardDecks.verifyFrozenIdentities()
            val seats = if (pestSeat == PestSeat.SEAT_ZERO) {
                listOf("Pest Control v1.0" to PestControlPreboardDecks.pestMain(), "SoterX Mono Red Madness" to PestControlPreboardDecks.monoRedMain())
            } else {
                listOf("SoterX Mono Red Madness" to PestControlPreboardDecks.monoRedMain(), "Pest Control v1.0" to PestControlPreboardDecks.pestMain())
            }
            val startIndex = seats.indexOfFirst { (name) ->
                (startingDeck == StartingDeck.PEST_CONTROL && name.startsWith("Pest")) ||
                    (startingDeck == StartingDeck.MONO_RED_MADNESS && name.startsWith("SoterX"))
            }
            val env = GameEnvironment.create(registry)
            env.reset(
                com.wingedsheep.engine.core.GameConfig(
                    players = seats.map { (name, deck) -> com.wingedsheep.engine.core.PlayerConfig(name, deck, startingLife = 20) },
                    skipMulligans = false,
                    useHandSmoother = false,
                    startingPlayerIndex = startIndex,
                    seed = fixtureEntropy,
                )
            )
            val provenance = MatchupProvenance(
                sourceCommit = sourceCommit,
                pestSeat = pestSeat,
                startingDeck = startingDeck,
                environment = MatchupEnvironmentIdentity.current(),
            )
            return fromEnvironment(registry, provenance, fixtureId, env)
        }

        fun fromEnvironment(
            registry: CardRegistry,
            provenance: MatchupProvenance,
            fixtureId: String,
            environment: GameEnvironment,
        ): PestControlPreboardSession {
            val builder = ObservationBuilder(registry)
            val openings = environment.playerIds.mapIndexed { index, player ->
                val state = environment.state
                val library = state.getLibrary(player).map { cardName(state, it) }
                val hand = state.getHand(player).map { cardName(state, it) }
                OpeningZoneAudit(
                    playerId = player,
                    deckIdentity = if (index == provenance.pestSeat.index) "PEST_CONTROL_V10" else "SOTERX_MONO_RED_MADNESS_60",
                    libraryOrderedCardNames = library,
                    libraryStateDigest = sha256(library.joinToString("\n", postfix = "\n").toByteArray()),
                    handCardNames = hand,
                    libraryCount = library.size,
                    handCount = hand.size,
                    sideboardCount = 0,
                )
            }
            return PestControlPreboardSession(provenance, fixtureId, environment, builder, openings)
        }
    }
}

class MatchupProtocolRejected(val defect: ProtocolDefect) : IllegalStateException("Matchup protocol rejected: $defect")

internal val PROTOCOL_JSON = Json {
    encodeDefaults = true
    explicitNulls = true
    prettyPrint = false
    classDiscriminator = "type"
    ignoreUnknownKeys = true
}

fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }

private fun cardName(state: GameState, id: EntityId): String =
    state.getEntity(id)?.get<CardComponent>()?.name ?: id.value

private fun cardSummaries(state: GameState, ids: List<EntityId>): Map<EntityId, CardSummary> =
    ids.associateWith { id ->
        val card = state.getEntity(id)?.get<CardComponent>()
        CardSummary(
            name = card?.name ?: id.value,
            manaCost = card?.manaCost?.toString(),
            typeLine = card?.typeLine?.toString(),
            power = card?.baseStats?.basePower,
            toughness = card?.baseStats?.baseToughness,
            oracleText = card?.oracleText,
        )
    }

private fun hashLegalActions(observation: TrainingObservation): String = sha256(
    PROTOCOL_JSON.encodeToString(observation.legalActions).toByteArray()
)

private fun actionObject(action: GameAction): JsonObject =
    PROTOCOL_JSON.encodeToJsonElement(GameAction.serializer(), action) as JsonObject

private fun actionJsonStrings(action: GameAction, key: String): List<String> =
    actionObject(action)[key]?.let(::jsonStringValues).orEmpty()

private fun actionJsonInts(action: GameAction, key: String): List<Int> =
    (actionObject(action)[key] as? kotlinx.serialization.json.JsonArray)
        ?.mapNotNull { it.toString().toIntOrNull() }.orEmpty()

private fun jsonStringValues(element: JsonElement): List<String> =
    (element as? kotlinx.serialization.json.JsonArray)?.map { it.toString() }.orEmpty()

private fun actionCosts(action: GameAction): JsonElement {
    val obj = actionObject(action)
    val keys = setOf(
        "paymentStrategy", "alternativePayment", "additionalCostPayment", "declaredCostSlot",
        "useAlternativeCost", "alternativeCostType", "costPayment", "graveyardLifeCost",
        "conspiredCreatures", "casualtyCreature",
    )
    return JsonObject(obj.filterKeys { it in keys })
}

private fun terminalAudit(state: GameState, events: List<GameEvent>): TerminalAudit {
    val encoded = events.map { PROTOCOL_JSON.encodeToJsonElement(GameEvent.serializer(), it) }
    val gameEnded = encoded.lastOrNull { (it as? JsonObject)?.get("type")?.toString()?.contains("GameEndedEvent") == true }
        as? JsonObject
    return TerminalAudit(
        gameOver = state.gameOver,
        winnerId = state.winnerId,
        reason = gameEnded?.get("reason")?.toString()?.trim('"'),
        turn = state.turnNumber,
        mechanism = gameEnded?.get("reason")?.toString()?.trim('"') ?: "ENGINE_TERMINAL",
    )
}

private fun auditSnapshot(state: GameState): AuditStateSnapshot {
    val playerZones = listOf(Zone.HAND, Zone.LIBRARY, Zone.GRAVEYARD, Zone.EXILE, Zone.BATTLEFIELD, Zone.SIDEBOARD)
    return AuditStateSnapshot(
        turn = state.turnNumber,
        phase = state.phase.name,
        step = state.step.name,
        activePlayerId = state.activePlayerId,
        priorityPlayerId = state.priorityPlayerId,
        pendingDecisionType = state.pendingDecision?.let { it::class.simpleName },
        players = state.turnOrder.map { player ->
            val mana = state.getEntity(player)?.get<ManaPoolComponent>()
            AuditPlayerSnapshot(
                playerId = player,
                life = state.lifeTotal(player),
                mana = mana?.toString() ?: "EMPTY",
                zones = playerZones.map { zone ->
                    val ids = when (zone) {
                        Zone.HAND -> state.getHand(player)
                        Zone.LIBRARY -> state.getLibrary(player)
                        Zone.GRAVEYARD -> state.getGraveyard(player)
                        Zone.EXILE -> state.getExile(player)
                        Zone.BATTLEFIELD -> state.getBattlefield(player)
                        Zone.SIDEBOARD -> state.getZone(player, Zone.SIDEBOARD)
                        else -> emptyList()
                    }
                    AuditZoneSnapshot(zone.name, ids.map { id -> "${id.value}:${cardName(state, id)}" })
                },
            )
        },
        stackBottomToTop = state.stack.map { id -> "${id.value}:${cardName(state, id)}" },
        gameOver = state.gameOver,
        winnerId = state.winnerId,
    )
}

private fun indexTelemetry(actions: List<PriorityAudit>): MatchupTelemetryIndex {
    fun refs(vararg terms: String): List<TraceReference> = buildList {
        actions.forEach { action ->
            val actionText = action.selectedAction.toString()
            if (terms.any(actionText::contains)) add(TraceReference(action.sequence))
            action.emittedEvents.forEachIndexed { index, event ->
                if (terms.any(event.toString()::contains)) add(TraceReference(action.sequence, index))
            }
        }
    }.distinct()
    return MatchupTelemetryIndex(
        lifeAndDamage = refs("LifeChangedEvent", "DamageDealtEvent", "LifeGained"),
        combat = refs("DeclareAttackers", "DeclareBlockers", "Combat", "Attacker", "Blocker"),
        stackAndPriority = refs("PassPriority", "SpellCastEvent", "AbilityTriggeredEvent", "Stack"),
        zoneChanges = refs("ZoneChangeEvent"),
        wardenResearcherMascot = refs("Essence Warden", "Blood Researcher", "Pest Mascot"),
        lifegainAndPayoffConversion = refs("LifeGained", "LifeChangedEvent", "Counter"),
        weatherAndFollow = refs("Weather the Storm", "Follow the Lumarets"),
        carrierThrallAndScion = refs("Carrier Thrall", "Eldrazi Scion"),
        removalAndTargets = refs("Cast Down", "Bone Shards", "Chainer's Edict", "Destroy", "Sacrifice"),
        manaAndJungleHollow = refs("Mana", "Payment", "Jungle Hollow", "Tapped"),
        burnTargets = refs("Lightning Bolt", "Fiery Temper", "Fireblast", "Lava Dart", "DamageDealtEvent"),
        madnessAndDiscardOutlets = refs("Madness", "Faithless Looting", "Grab the Prize", "Highway Robbery", "CardsDiscardedEvent"),
        sneakySnackerRecursion = refs("Sneaky Snacker"),
        fireblastAndLavaDart = refs("Fireblast", "Lava Dart"),
        meldedMoxite = refs("Melded Moxite", "Robot"),
        guttersnipeAndFlamebreather = refs("Guttersnipe", "Kessig Flamebreather"),
    )
}
