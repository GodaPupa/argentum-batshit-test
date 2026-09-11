package com.wingedsheep.gym

import com.wingedsheep.ai.engine.EngineAiPlayerController
import com.wingedsheep.ai.llm.BottomCardsInfo
import com.wingedsheep.ai.llm.CardSummary
import com.wingedsheep.ai.llm.MulliganInfo
import com.wingedsheep.ai.solitaire.ProjectXDeck
import com.wingedsheep.ai.solitaire.ProjectXSolitaireAgent
import com.wingedsheep.ai.solitaire.ProjectXStateAnalyzer
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.PredicateContext
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.legalactions.EnumerationMode
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.PlayerComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ActivatedAbilityOnStackComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

private const val PROJECT_X_GOLDFISH_ENV = "PROJECT_X_GOLDFISH"
private const val PROJECT_X_GOLDFISH_SAMPLE_1_ENV = "PROJECT_X_GOLDFISH_SAMPLE_1"
private const val PROJECT_X_GOLDFISH_SAMPLE_2_ENV = "PROJECT_X_GOLDFISH_SAMPLE_2"
private const val PROJECT_X_OPTIMIZATION_A_ENV = "PROJECT_X_OPTIMIZATION_A"
private const val PROJECT_X_HORIZON = 12

/**
 * Deterministic, no-interaction validation for the frozen Project X v0.2 deck and its dedicated
 * solitaire agent. The 30-game block is opt-in so ordinary CI compiles the harness without silently
 * consuming or replacing the frozen sample.
 */
class ProjectXGoldfishTest : FunSpec({
    test("rejected Game 14 preflights explicit-mana casts instead of submitting an illegal action").config(timeout = 5.minutes) {
        val result = runProjectXGoldfish(projectXRegistry(), 0x2B7B_990F_635F_EBDL, 14)
        result.auditErrors shouldBe emptyList()
        (result.stopReason != "ILLEGAL_ACTION").shouldBeTrue()
        result.castAttempts.any {
            it.spell == "Falkenrath Noble" &&
                it.actualAutoPaymentResult.contains("POLICY_PREFLIGHT_REJECTED:Not enough mana to auto-pay")
        }.shouldBeTrue()
        result.castAttempts.none { it.actualAutoPaymentResult.startsWith("REJECTED:") }.shouldBeTrue()
    }

    test("development goldfish exercises the real engine boundary").config(timeout = 5.minutes) {
        val result = runProjectXGoldfish(projectXRegistry(), 0x5058_4445_5600_0001L, 1)
        result.actions shouldBeGreaterThan 0
        result.auditErrors shouldBe emptyList()
        result.t1Development.isNotEmpty().shouldBeTrue()
    }

    test("Herald death uses the real collection search path and records its tutor lifecycle").config(timeout = 5.minutes) {
        val result = runProjectXGoldfish(projectXRegistry(), 0x8DAF_02FC_42EE_3A2L, 1)
        result.auditErrors shouldBe emptyList()
        result.herald.tutorTriggerCreated.isNotEmpty().shouldBeTrue()
        result.herald.tutorTriggerResolved.isNotEmpty().shouldBeTrue()
        result.herald.tutorTargets.isNotEmpty().shouldBeTrue()
    }

    test("frozen 30-seed Project X v0.2 goldfish block").config(
        enabled = System.getenv(PROJECT_X_GOLDFISH_ENV) == "true",
        timeout = 45.minutes,
    ) {
        executeProjectXGoldfishBlock(
            seedPath = Path.of("src", "test", "resources", "project-x-goldfish-v02-seeds.csv"),
            sampleName = "Corrected original 30-seed regression replay",
            reportStem = "project-x-v02-goldfish-30",
        )
    }

    test("Project X v0.2 Goldfish Sample 1").config(
        enabled = System.getenv(PROJECT_X_GOLDFISH_SAMPLE_1_ENV) == "true",
        timeout = 45.minutes,
    ) {
        executeProjectXGoldfishBlock(
            seedPath = Path.of("src", "test", "resources", "project-x-goldfish-sample-1-seeds.csv"),
            sampleName = "Goldfish Sample #1",
            reportStem = "project-x-v02-goldfish-sample-1",
        )
    }

    test("Project X v0.2 Goldfish Sample 2").config(
        enabled = System.getenv(PROJECT_X_GOLDFISH_SAMPLE_2_ENV) == "true",
        timeout = 45.minutes,
    ) {
        executeProjectXGoldfishBlock(
            seedPath = Path.of("src", "test", "resources", "project-x-goldfish-sample-2-seeds.csv"),
            sampleName = "Goldfish Sample #2",
            reportStem = "project-x-v02-goldfish-sample-2",
        )
    }

    test("Project X Optimization Experiment A paired block").config(
        enabled = System.getenv(PROJECT_X_OPTIMIZATION_A_ENV) == "true",
        timeout = 90.minutes,
    ) {
        executeProjectXOptimizationA(
            Path.of("src", "test", "resources", "project-x-optimization-a-seeds.csv")
        )
    }
})

private fun executeProjectXOptimizationA(seedPath: Path) {
    assertOnlyDeclaredDeckDifference(
        ProjectXDeck.V02,
        ProjectXDeck.EXPERIMENT_A,
        DeclaredDeckDifference(
            mainboardRemoved = mapOf("Falkenrath Noble" to 1, "Masked Vandal" to 1),
            mainboardAdded = mapOf("Llanowar Elves" to 2),
        ),
    )
    val seeds = Files.readAllLines(seedPath).drop(1).filter(String::isNotBlank).map { line ->
        line.substringAfterLast(',').toLong()
    }
    seeds.size shouldBe 30
    seeds.distinct().size shouldBe 30
    seeds.none(previouslyUsedProjectXOrBatshitSeeds(seedPath)::contains).shouldBeTrue()

    val registry = projectXRegistry()
    val pairs = seeds.mapIndexed { index, seed ->
        val control = runProjectXGoldfish(registry, seed, index + 1, ProjectXDeck.V02)
        val variant = runProjectXGoldfish(registry, seed, index + 1, ProjectXDeck.EXPERIMENT_A)
        ProjectXOptimizationPair(
            pair = index + 1,
            seed = seed,
            control = control,
            variant = variant,
            delta = pairedDelta(control, variant),
        )
    }
    pairs.flatMap { it.control.auditErrors + it.variant.auditErrors } shouldBe emptyList()
    val block = ProjectXOptimizationBlock(
        experiment = "Project X Optimization Experiment A",
        declaredChanges = listOf("-1 Falkenrath Noble", "-1 Masked Vandal", "+2 Llanowar Elves"),
        seeds = seeds,
        pairs = pairs,
        controlSummary = summarizeProjectX(pairs.map(ProjectXOptimizationPair::control)),
        variantSummary = summarizeProjectX(pairs.map(ProjectXOptimizationPair::variant)),
    )
    val reportDir = Path.of("build", "reports", "project-x-goldfish")
    Files.createDirectories(reportDir)
    Files.writeString(
        reportDir.resolve("project-x-optimization-a.json"),
        Json { prettyPrint = true }.encodeToString(block),
    )
    Files.writeString(
        reportDir.resolve("project-x-optimization-a.md"),
        renderProjectXOptimizationMarkdown(block),
    )
}

private fun executeProjectXGoldfishBlock(seedPath: Path, sampleName: String, reportStem: String) {
    val seeds = Files.readAllLines(seedPath).drop(1).filter(String::isNotBlank).map { line ->
        line.substringAfterLast(',').toLong()
    }
    seeds.size shouldBe 30
    seeds.distinct().size shouldBe 30
    seeds.none(previouslyUsedProjectXOrBatshitSeeds(seedPath)::contains).shouldBeTrue()

    val registry = projectXRegistry()
    val games = seeds.mapIndexed { index, seed -> runProjectXGoldfish(registry, seed, index + 1) }
    val block = ProjectXGoldfishBlock(
        sampleName = sampleName,
        deckVersion = "Project X v0.2",
        horizon = PROJECT_X_HORIZON,
        seeds = seeds,
        games = games,
        summary = summarizeProjectX(games),
    )

    val reportDir = Path.of("build", "reports", "project-x-goldfish")
    Files.createDirectories(reportDir)
    Files.writeString(
        reportDir.resolve("$reportStem.json"),
        Json { prettyPrint = true }.encodeToString(block),
    )
    Files.writeString(
        reportDir.resolve("$reportStem.md"),
        renderProjectXMarkdown(block),
    )
    println(renderProjectXMarkdown(block))
    games.forEach { it.auditErrors shouldBe emptyList() }
}

@Serializable
internal data class ProjectXGoldfishBlock(
    val sampleName: String,
    val deckVersion: String,
    val horizon: Int,
    val seeds: List<Long>,
    val games: List<ProjectXGoldfishGame>,
    val summary: ProjectXGoldfishSummary,
)

@Serializable
internal data class ProjectXOptimizationBlock(
    val experiment: String,
    val declaredChanges: List<String>,
    val seeds: List<Long>,
    val pairs: List<ProjectXOptimizationPair>,
    val controlSummary: ProjectXGoldfishSummary,
    val variantSummary: ProjectXGoldfishSummary,
)

@Serializable
internal data class ProjectXOptimizationPair(
    val pair: Int,
    val seed: Long,
    val control: ProjectXGoldfishGame,
    val variant: ProjectXGoldfishGame,
    val delta: ProjectXPairedDelta,
)

@Serializable
internal data class ProjectXPairedDelta(
    /** Variant turn minus control turn; a negative value is earlier. */
    val firstIvyCastTurn: Int?,
    val exactlyThreeManaIvyStalls: Int,
    val primaryEngineTurn: Int?,
    val primaryEngineStateChange: String,
    val anyInfiniteTurn: Int?,
    val anyInfiniteStateChange: String,
    val comboBeforeCombatLethalChanged: String,
    val actualWinTurn: Int?,
    val witnessRecursions: Int,
    val nobleAvailableTurns: Int,
    val noblePayoffMissingTurns: Int,
    val combatDamage: Int,
    val maximumCreatureBoard: Int,
)

@Serializable
internal data class ProjectXGoldfishGame(
    val game: Int,
    val seed: Long,
    val seedHex: String,
    val mulligans: Int,
    val keptHand: List<String>,
    val openingColorAccess: OpeningColorAccess,
    val t1Development: List<String>,
    val firstMeaningfulDevelopmentTurn: Int?,
    val actualWinningTurn: Int?,
    val winningMechanism: String?,
    val engineTurn: Int?,
    val firstValidatedComboTurn: Int?,
    val hugeFeederTurn: Int?,
    val infiniteLifeTurn: Int?,
    val nobleDeterministicDrainTurn: Int?,
    val deterministicLethalTurn: Int?,
    val lethalMechanism: String?,
    val comboAvailableBeforeOrdinaryLethal: Boolean,
    val ordinaryLethalEndedBeforeComboAssembly: Boolean,
    val winner: String?,
    val gameOverTurn: Int?,
    val terminalMechanism: String?,
    val combatLethal: Boolean,
    val triggeredAbilityLethal: Boolean,
    val deterministicComboLethal: Boolean,
    val otherTerminalState: Boolean,
    val castAttempts: List<CastAttemptTelemetry>,
    val herald: HeraldTelemetry,
    val manaConstraints: List<ManaConstraintTelemetry>,
    val heraldTutorTargets: List<String>,
    val witnessRecursionEvents: List<String>,
    val witness: WitnessTelemetry,
    val llanowar: LlanowarTelemetry,
    val firstIvyCastTurn: Int?,
    val ivyThreeManaStallTurns: List<Int>,
    val nobleCastTurns: List<Int>,
    val nobleAvailableTurns: List<Int>,
    val noblePayoffMissingTurns: List<Int>,
    val combatDamageDealt: Int,
    val maximumCreatureBoard: Int,
    val birchloreManaContribution: List<String>,
    val nettleUntapContribution: List<String>,
    val quirionManaContribution: List<String>,
    val windingWay: List<String>,
    val leadTheStampede: List<String>,
    val colorStrandedCards: List<String>,
    val taplandTempoEvents: List<String>,
    val khalniGardenTempoEvents: List<String>,
    val hauntedMireTempoEvents: List<String>,
    val exactlyOneRoleMissingTurns: List<Int>,
    val primaryRoleShortStates: List<PrimaryRoleShortState>,
    val functionalWithoutCombo: String,
    val secondaryWitnessLoopTurn: Int?,
    val actions: Int,
    val stopReason: String,
    val auditErrors: List<String>,
)

@Serializable
internal data class OpeningColorAccess(
    val green: Boolean,
    val black: Boolean,
    val untappedGreen: Boolean,
    val untappedBlack: Boolean,
    val sources: List<String>,
    val entersTappedSources: List<String>,
)

@Serializable
internal data class CastAttemptTelemetry(
    val turn: Int,
    val spell: String,
    val manaPool: String,
    val untappedLands: List<String>,
    val tappedLands: List<String>,
    val availableBirchloreMana: String,
    val availableQuirionLines: List<String>,
    val proposedManaPaymentPlan: String,
    val actualAutoPaymentResult: String,
    val preExecutionLegalityReason: String,
)

@Serializable
internal data class HeraldAvailability(
    val turn: Int,
    val missingRole: String,
    val heraldZones: List<String>,
    val missingRoleSearchable: Boolean,
    val sacrificeLineAvailable: Boolean,
)

@Serializable
internal data class HeraldTelemetry(
    val drawn: List<String>,
    val cast: List<String>,
    val battlefield: List<String>,
    val died: List<String>,
    val tutorTriggerCreated: List<String>,
    val tutorTriggerResolved: List<String>,
    val tutorTargets: List<String>,
    val oneRoleMissingAvailability: List<HeraldAvailability>,
)

@Serializable
internal data class ManaConstraintTelemetry(
    val turn: Int,
    val spell: String,
    val category: String,
    val detail: String,
)

@Serializable
internal data class PrimaryRoleShortState(
    val turn: Int,
    val missingRole: String,
    val zones: List<String>,
    val attributions: List<String>,
)

@Serializable
internal data class WitnessCounterTelemetry(
    val turn: Int,
    val amount: Int,
    val counterSource: String,
)

@Serializable
internal data class WitnessTriggerTelemetry(
    val turn: Int,
    val counterSource: String,
    val description: String,
)

@Serializable
internal data class WitnessRecursionTelemetry(
    val turn: Int,
    val entityId: String,
    val permanent: String,
    val counterSource: String,
    var deployedTurn: Int? = null,
)

@Serializable
internal data class WitnessTelemetry(
    val casts: List<Int>,
    val adaptActivations: List<Int>,
    val countersPlaced: List<WitnessCounterTelemetry>,
    val counterTriggers: List<WitnessTriggerTelemetry>,
    val successfulRecursions: List<WitnessRecursionTelemetry>,
)

@Serializable
internal data class LlanowarActivationTelemetry(
    val turn: Int,
    val entityId: String,
    val manaBefore: String,
    val manaAfter: String,
    val newlyAffordableSpells: List<String>,
)

@Serializable
internal data class LlanowarTelemetry(
    val casts: List<Int>,
    val activations: List<LlanowarActivationTelemetry>,
    val fundedSpells: List<String>,
)

@Serializable
internal data class ProjectXGoldfishSummary(
    val actualWinsByT4: Int,
    val actualWinsByT5: Int,
    val actualWinsByT6: Int,
    val medianActualWinningTurn: Double?,
    val meanActualWinningTurn: Double?,
    val winningMechanismDistribution: Map<String, Int>,
    val engineByT4: Int,
    val engineByT5: Int,
    val engineByT6: Int,
    val lethalByT4: Int,
    val lethalByT5: Int,
    val lethalByT6: Int,
    val medianEngineTurn: Double?,
    val medianLethalTurn: Double?,
    val anyInfiniteByT4: Int,
    val anyInfiniteByT5: Int,
    val anyInfiniteByT6: Int,
    val medianComboTurn: Double?,
    val comboAssemblyGames: Int,
    val comboAssemblyRate: Double,
    val comboBeforeGameEndGames: Int,
    val comboBeforeGameEndRate: Double,
    val comboBeforeOrdinaryLethalGames: Int,
    val ordinaryLethalBeforeComboGames: Int,
    val infiniteLifeGames: Int,
    val nobleDeterministicDrainGames: Int,
    val hugeFeederGames: Int,
    val hugeFeederWithoutImmediateLethalGames: Int,
    val mulliganGames: Int,
    val totalMulligans: Int,
    val mulliganGameRate: Double,
    val functionalWithoutComboGames: Int,
    val functionalWithoutComboRate: Double,
    val exactlyOneRoleMissingGames: Int,
    val exactlyOneRoleMissingRate: Double,
    val roleShortGamesByMissingRole: Map<String, Int>,
    val roleShortGamesByAttribution: Map<String, Int>,
    val heraldContributionGames: Int,
    val witnessContributionGames: Int,
    val birchloreContributionGames: Int,
    val nettleContributionGames: Int,
    val quirionContributionGames: Int,
    val colorBottleneckGames: Int,
    val khalniGardenTempoGames: Int,
    val hauntedMireTempoGames: Int,
    val intentionallyHeldGames: Int,
    val birchloreManaAvailableGames: Int,
    val quirionSequenceAvailableGames: Int,
    val tappedLandConstraintGames: Int,
    val insufficientTotalManaGames: Int,
)

internal data class SelectionTelemetry(
    val name: String,
    val turn: Int,
    var agentChoice: String? = null,
    var rulesChoice: String? = null,
    val toHand: MutableList<String> = mutableListOf(),
    val toGraveyard: MutableList<String> = mutableListOf(),
)

private data class PendingWitnessTarget(
    val turn: Int,
    val entityId: EntityId,
    val permanent: String,
    val counterSource: String,
)

private data class PendingLlanowarActivation(
    val turn: Int,
    val sourceId: EntityId,
    val manaBefore: String,
    val affordableCastIdsBefore: Set<EntityId>,
)

internal fun runProjectXGoldfish(
    registry: CardRegistry,
    seed: Long,
    gameNumber: Int,
    deck: Deck = ProjectXDeck.V02,
): ProjectXGoldfishGame {
    val processor = ActionProcessor(registry)
    val init = GameInitializer(registry).initializeGame(
        GameConfig(
            players = listOf(
                PlayerConfig("Project X", deck),
                PlayerConfig("Blank Goldfish", Deck.of("Plains" to 60)),
            ),
            skipMulligans = false,
            useHandSmoother = false,
            startingPlayerIndex = 0,
            seed = seed,
        )
    )
    val projectId = init.playerIds[0]
    val blankId = init.playerIds[1]
    var state = init.state
    val mulliganController = EngineAiPlayerController(registry, projectId, gameStateProvider = { state })

    fun name(id: EntityId): String = state.getEntity(id)?.get<CardComponent>()?.name ?: id.toString()
    fun summaries(playerId: EntityId): Map<EntityId, CardSummary> = state.getHand(playerId).associateWith { id ->
        val card = state.getEntity(id)?.get<CardComponent>()
        CardSummary(
            name = card?.name ?: id.toString(),
            manaCost = card?.manaCost?.toString(),
            typeLine = card?.typeLine?.toString(),
            power = card?.baseStats?.basePower,
            toughness = card?.baseStats?.baseToughness,
            oracleText = card?.oracleText,
        )
    }

    for (playerId in state.turnOrder) {
        while (true) {
            val mulligan = state.getEntity(playerId)!!.get<MulliganStateComponent>()!!
            val keep = if (playerId == projectId) {
                mulliganController.decideMulligan(
                    MulliganInfo(
                        hand = state.getHand(playerId),
                        mulliganCount = mulligan.mulligansTaken,
                        cardsToPutOnBottom = mulligan.cardsToBottom,
                        cards = summaries(playerId),
                        isOnThePlay = true,
                    )
                )
            } else true
            val result = processor.process(state, if (keep) KeepHand(playerId) else TakeMulligan(playerId)).result
            check(result.error == null) { "Mulligan rejected: ${result.error}" }
            state = result.state
            if (keep) break
        }
    }
    val projectMulligan = state.getEntity(projectId)!!.get<MulliganStateComponent>()!!
    val mulligans = projectMulligan.mulligansTaken
    for (playerId in state.turnOrder) {
        val mulligan = state.getEntity(playerId)!!.get<MulliganStateComponent>()!!
        if (mulligan.cardsToBottom <= 0) continue
        val bottom = if (playerId == projectId) {
            mulliganController.chooseBottomCards(
                BottomCardsInfo(state.getHand(playerId), mulligan.cardsToBottom, summaries(playerId))
            )
        } else state.getHand(playerId).take(mulligan.cardsToBottom)
        val result = processor.process(state, BottomCards(playerId, bottom)).result
        check(result.error == null) { "Bottom cards rejected: ${result.error}" }
        state = result.state
    }

    val keptHand = state.getHand(projectId).map(::name)
    val openingSources = keptHand.filter { it in setOf("Forest", "Swamp", "Khalni Garden", "Haunted Mire") }
    val openingColorAccess = OpeningColorAccess(
        green = openingSources.any { it in setOf("Forest", "Khalni Garden", "Haunted Mire") },
        black = openingSources.any { it in setOf("Swamp", "Haunted Mire") },
        untappedGreen = "Forest" in openingSources,
        untappedBlack = "Swamp" in openingSources,
        sources = openingSources,
        entersTappedSources = openingSources.filter { it in setOf("Khalni Garden", "Haunted Mire") },
    )
    val environment = GameEnvironment.create(registry).also { it.restore(state, init.playerIds) }
    val agent = ProjectXSolitaireAgent(registry, projectId)
    val blank = com.wingedsheep.ai.engine.AIPlayer.create(registry, blankId)
    val analyzer = agent.analyzer
    val telemetryEnumerator = LegalActionEnumerator.create(registry)
    val t1 = mutableListOf<String>()
    val heraldTargets = mutableListOf<String>()
    val witnessEvents = mutableListOf<String>()
    val witnessCasts = mutableListOf<Int>()
    val witnessAdaptActivations = mutableListOf<Int>()
    val witnessCounters = mutableListOf<WitnessCounterTelemetry>()
    val witnessCounterTriggers = mutableListOf<WitnessTriggerTelemetry>()
    val witnessRecursions = mutableListOf<WitnessRecursionTelemetry>()
    val pendingWitnessCounterSources = ArrayDeque<String>()
    val pendingWitnessTriggerSources = ArrayDeque<String>()
    val llanowarCasts = mutableListOf<Int>()
    val llanowarActivations = mutableListOf<LlanowarActivationTelemetry>()
    val llanowarFundedSpells = mutableListOf<String>()
    val pendingLlanowarFundedCandidates = mutableMapOf<EntityId, Int>()
    val birchlore = mutableListOf<String>()
    val nettle = mutableListOf<String>()
    val quirion = mutableListOf<String>()
    val winding = mutableListOf<String>()
    val lead = mutableListOf<String>()
    val stranded = linkedSetOf<String>()
    val taplands = mutableListOf<String>()
    val nobleCastTurns = mutableListOf<Int>()
    val nobleAvailableTurns = sortedSetOf<Int>()
    val noblePayoffMissingTurns = sortedSetOf<Int>()
    val oneMissingTurns = sortedSetOf<Int>()
    val roleShortStates = linkedSetOf<PrimaryRoleShortState>()
    val audit = mutableListOf<String>()
    val manaConstraints = linkedSetOf<ManaConstraintTelemetry>()
    val castAttempts = mutableListOf<CastAttemptTelemetry>()
    val heraldDrawn = mutableListOf<String>().apply {
        if (ProjectXStateAnalyzer.WIREWOOD_HERALD in keptHand) add("OPENING_HAND")
    }
    val heraldCast = mutableListOf<String>()
    val heraldBattlefield = linkedSetOf<String>()
    val heraldDied = mutableListOf<String>()
    val heraldTriggerCreated = mutableListOf<String>()
    val heraldTriggerResolved = mutableListOf<String>()
    val heraldAvailability = linkedSetOf<HeraldAvailability>()
    val primaryRolesSeen = mutableSetOf<String>().apply {
        addAll(keptHand.filter { it in analyzer.primaryRoles })
    }
    val primaryRolesTutored = mutableSetOf<String>()
    val primaryRoleDeparture = mutableMapOf<String, String>()
    val primaryRoleAttackers = mutableSetOf<EntityId>()
    var selection: SelectionTelemetry? = null
    var pendingWitnessTarget: PendingWitnessTarget? = null
    var pendingLlanowarActivation: PendingLlanowarActivation? = null
    var pendingHeraldSearch: Pair<Int, List<String>>? = null
    var firstIvyCastTurn: Int? = null
    var firstMeaningful: Int? = null
    var engineTurn: Int? = null
    var hugeTurn: Int? = null
    var lifeTurn: Int? = null
    var lethalTurn: Int? = null
    var lethalMechanism: String? = null
    var secondaryTurn: Int? = null
    var primaryRecognitionAction: Int? = null
    var secondaryRecognitionAction: Int? = null
    var ordinaryTerminalAction: Int? = null
    var actions = 0
    var lastEngineTurn = -1
    var actionsThisEngineTurn = 0
    var stopReason = "T${PROJECT_X_HORIZON}_HORIZON"
    var winner: String? = null
    var gameOverTurn: Int? = null
    var terminalMechanism: String? = null
    var combatDamageDealt = 0
    var maximumCreatureBoard = 0

    fun projectTurn(gameState: GameState): Int = (gameState.turnNumber + 1) / 2

    fun manaPool(gameState: GameState): String =
        gameState.getEntity(projectId)?.get<ManaPoolComponent>()?.toString() ?: "unavailable"

    fun affordableCastIds(gameState: GameState): Set<EntityId> =
        telemetryEnumerator.enumerate(gameState, projectId, EnumerationMode.FULL)
            .filter { it.affordable && it.action is CastSpell }
            .map { (it.action as CastSpell).cardId }
            .toSet()

    fun resolvingSourceName(gameState: GameState): String? {
        val top = gameState.stack.lastOrNull()?.let(gameState::getEntity) ?: return null
        return top.get<ActivatedAbilityOnStackComponent>()?.sourceName
            ?: top.get<TriggeredAbilityOnStackComponent>()?.sourceName
    }

    fun observe(gameState: GameState) {
        val turn = projectTurn(gameState)
        val handNames = analyzer.handNames(gameState, projectId)
        val battlefieldNames = analyzer.battlefieldNames(gameState, projectId)
        if (ProjectXStateAnalyzer.FALKENRATH_NOBLE in handNames ||
            ProjectXStateAnalyzer.FALKENRATH_NOBLE in battlefieldNames
        ) nobleAvailableTurns += turn
        if (agent.outcome(gameState).completeInfiniteEngine &&
            ProjectXStateAnalyzer.FALKENRATH_NOBLE !in battlefieldNames
        ) noblePayoffMissingTurns += turn
        maximumCreatureBoard = maxOf(
            maximumCreatureBoard,
            gameState.controlledBattlefield(projectId).count { id ->
                gameState.getEntity(id)?.get<CardComponent>()?.isCreature == true
            },
        )
        if (analyzer.missingPrimaryRoles(gameState, projectId).size == 1) oneMissingTurns += turn
        val missing = analyzer.missingPrimaryRoles(gameState, projectId)
        if (missing.size == 1) {
            val role = missing.single()
            val zones = buildList {
                if (analyzer.handNames(gameState, projectId).contains(ProjectXStateAnalyzer.WIREWOOD_HERALD)) add("HAND")
                if (analyzer.battlefieldNames(gameState, projectId).contains(ProjectXStateAnalyzer.WIREWOOD_HERALD)) add("BATTLEFIELD")
                if (analyzer.graveyardNames(gameState, projectId).contains(ProjectXStateAnalyzer.WIREWOOD_HERALD)) add("GRAVEYARD")
            }
            if (zones.isNotEmpty()) {
                val searchable = isHeraldSearchableRole(gameState, projectId, role)
                heraldAvailability += HeraldAvailability(
                    turn, role, zones, searchable,
                    searchable && "BATTLEFIELD" in zones &&
                        analyzer.battlefieldNames(gameState, projectId).contains(ProjectXStateAnalyzer.CARRION_FEEDER)
                )
            }
        }
        if (analyzer.battlefieldNames(gameState, projectId).contains(ProjectXStateAnalyzer.WIREWOOD_HERALD)) {
            heraldBattlefield += "T$turn"
        }
        if (gameState.priorityPlayerId == projectId && gameState.pendingDecision == null &&
            gameState.step in setOf(Step.PRECOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
        ) {
            val constraints = classifyManaConstraints(gameState, projectId, analyzer, telemetryEnumerator, turn)
            manaConstraints += constraints
            stranded += constraints.filter { it.category == "GENUINE_COLOR_UNCASTABLE" }.map { "${it.spell}@T$turn" }
        }
        classifyPrimaryRoleShortState(
            gameState, projectId, analyzer, turn, manaConstraints,
            primaryRolesSeen, primaryRolesTutored, primaryRoleDeparture,
        )?.let { snapshot ->
            roleShortStates.removeIf { it.turn == snapshot.turn && it.missingRole == snapshot.missingRole }
            roleShortStates += snapshot
        }
        val outcome = agent.outcome(gameState)
        if (outcome.completeInfiniteEngine && engineTurn == null) {
            engineTurn = turn
            primaryRecognitionAction = actions
        }
        if (outcome.arbitrarilyLargeCarrionFeeder && hugeTurn == null) hugeTurn = turn
        if (outcome.arbitraryLife && lifeTurn == null) lifeTurn = turn
        if (outcome.secondaryWitnessLoop && secondaryTurn == null) {
            secondaryTurn = turn
            secondaryRecognitionAction = actions
        }
        if (outcome.immediateDeterministicLethal && lethalTurn == null) {
            lethalTurn = turn
            lethalMechanism = if (outcome.nobleDeterministicLethal) "FALKENRATH_NOBLE_DRAIN" else "CARRION_FEEDER_COMBAT"
        }
    }

    observe(state)
    while (!state.gameOver && projectTurn(state) <= PROJECT_X_HORIZON && actions < 4_000 && lethalTurn == null) {
        if (state.turnNumber != lastEngineTurn) {
            lastEngineTurn = state.turnNumber
            actionsThisEngineTurn = 0
        }
        actionsThisEngineTurn++
        if (actionsThisEngineTurn > 300) {
            audit += "wedged on engine turn ${state.turnNumber}"
            stopReason = "WEDGED"
            break
        }

        val decision = state.pendingDecision
        val acting = decision?.playerId ?: state.priorityPlayerId
        if (acting == null) {
            audit += "no acting player on engine turn ${state.turnNumber}"
            stopReason = "NO_ACTOR"
            break
        }
        val turn = projectTurn(state)
        val action: GameAction
        if (decision != null) {
            val response = if (acting == projectId) agent.respondToDecision(state, decision)
            else blank.respondToDecision(state, decision)
            if (acting == projectId) {
                when {
                    decision is SearchLibraryDecision && decision.context.sourceName == ProjectXStateAnalyzer.WIREWOOD_HERALD -> {
                        val chosen = (response as CardsSelectedResponse).selectedCards.map(::name)
                        pendingHeraldSearch = turn to chosen
                    }
                    decision is SelectCardsDecision && decision.context.sourceName == ProjectXStateAnalyzer.WIREWOOD_HERALD -> {
                        val chosen = (response as CardsSelectedResponse).selectedCards.map(::name)
                        pendingHeraldSearch = turn to chosen
                    }
                    decision is ChooseTargetsDecision && decision.context.sourceName == ProjectXStateAnalyzer.EVOLUTION_WITNESS -> {
                        val chosenId = (response as TargetsResponse).selectedTargets.values.flatten().firstOrNull()
                        if (chosenId != null) {
                            pendingWitnessTarget = PendingWitnessTarget(
                                turn = turn,
                                entityId = chosenId,
                                permanent = name(chosenId),
                                counterSource = pendingWitnessTriggerSources.removeLastOrNull() ?: "UNKNOWN",
                            )
                        }
                    }
                    decision is ChooseModeDecision && selection?.name == ProjectXStateAnalyzer.WINDING_WAY -> {
                        selection?.let { recordAgentModeChoice(it, decision, response as ModesChosenResponse) }
                    }
                    decision is ChooseOptionDecision && selection?.name == ProjectXStateAnalyzer.WINDING_WAY -> {
                        selection?.let { recordAgentOptionChoice(it, decision, response as OptionChosenResponse) }
                    }
                }
            }
            action = SubmitDecision(acting, response)
        } else if (acting == projectId) {
            val choice = agent.chooseActionWithDiagnostics(state)
            choice.rejectedSubmissions.forEach { rejected ->
                val rejectedCast = rejected.action as? CastSpell ?: return@forEach
                val attempt = castAttemptBeforeExecution(
                    state, projectId, rejectedCast, turn, name(rejectedCast.cardId), analyzer,
                    telemetryEnumerator, ::name
                ).copy(actualAutoPaymentResult = "POLICY_PREFLIGHT_REJECTED:${rejected.executorReason}")
                if (castAttempts.none { it.turn == attempt.turn && it.spell == attempt.spell &&
                        it.actualAutoPaymentResult == attempt.actualAutoPaymentResult
                    }) castAttempts += attempt
            }
            action = choice.action
        } else {
            action = blank.chooseAction(state)
        }

        if (acting == projectId && decision == null && action is PassPriority && state.stack.isEmpty()) {
            val chosenCast = (action as? CastSpell)?.cardId
            val held = telemetryEnumerator.enumerate(state, projectId, EnumerationMode.FULL)
                .filter { it.affordable && it.action is CastSpell && (it.action as CastSpell).cardId != chosenCast }
            held.forEach { legal ->
                val cardId = (legal.action as CastSpell).cardId
                manaConstraints += ManaConstraintTelemetry(
                    turn, name(cardId), "INTENTIONALLY_HELD",
                    "another legal action was selected while this cast was executable"
                )
            }
        }

        val actionName = when (action) {
            is CastSpell -> name(action.cardId)
            is PlayLand -> name(action.cardId)
            is ActivateAbility -> name(action.sourceId)
            else -> null
        }
        pendingLlanowarFundedCandidates.entries.removeIf { it.value != turn }
        val resolvingSource = resolvingSourceName(state)
        if (acting == projectId && action is ActivateAbility && actionName == "Llanowar Elves") {
            pendingLlanowarActivation = PendingLlanowarActivation(
                turn = turn,
                sourceId = action.sourceId,
                manaBefore = manaPool(state),
                affordableCastIdsBefore = affordableCastIds(state),
            )
        }
        if (acting == projectId && action !is PassPriority && action !is SubmitDecision) {
            val description = when (action) {
                is CastSpell -> "cast ${actionName}"
                is PlayLand -> "play ${actionName}"
                is ActivateAbility -> "activate ${actionName}"
                else -> action::class.simpleName ?: "action"
            }
            if (turn == 1) t1 += description
            if (firstMeaningful == null && (action is CastSpell || action is ActivateAbility || actionName == "Khalni Garden")) {
                firstMeaningful = turn
            }
            when (actionName) {
                ProjectXStateAnalyzer.BIRCHLORE_RANGERS -> if (action is ActivateAbility) {
                    birchlore += "T$turn:${action.manaColorChoice ?: "decision"}:${action.costPayment?.tappedPermanents?.map(::name).orEmpty()}"
                }
                ProjectXStateAnalyzer.QUIRION_RANGER -> if (action is ActivateAbility) {
                    quirion += "T$turn:return=${action.costPayment?.bouncedPermanents?.map(::name).orEmpty()} target=${action.targets}"
                }
                ProjectXStateAnalyzer.WINDING_WAY, ProjectXStateAnalyzer.LEAD_THE_STAMPEDE -> if (action is CastSpell) {
                    selection = SelectionTelemetry(actionName, turn)
                }
            }
        }

        val beforeHuge = agent.outcome(state).arbitrarilyLargeCarrionFeeder
        val castSnapshot = if (acting == projectId && action is CastSpell) {
            castAttemptBeforeExecution(
                state, projectId, action, turn, actionName ?: name(action.cardId), analyzer, telemetryEnumerator, ::name
            )
        } else null
        val result = environment.stepExactlyOne(action)
        val step = when (result) {
            is ExactlyOneSubmissionResult.Applied -> {
                castSnapshot?.let { castAttempts += it.copy(actualAutoPaymentResult = "APPLIED") }
                if (acting == projectId && action is SubmitDecision && pendingHeraldSearch != null &&
                    ((decision is SearchLibraryDecision || decision is SelectCardsDecision) &&
                        decision.context.sourceName == ProjectXStateAnalyzer.WIREWOOD_HERALD)
                ) {
                    val heraldSearch = pendingHeraldSearch!!
                    heraldTriggerResolved += "T${heraldSearch.first}:search selection accepted"
                    heraldTargets += heraldSearch.second.map { "$it@T${heraldSearch.first}" }
                    primaryRolesTutored += heraldSearch.second.filter { it in analyzer.primaryRoles }
                    primaryRolesSeen += heraldSearch.second.filter { it in analyzer.primaryRoles }
                    pendingHeraldSearch = null
                }
                if (acting == projectId && action is SubmitDecision &&
                    (decision is ChooseModeDecision || decision is ChooseOptionDecision) &&
                    selection?.name == ProjectXStateAnalyzer.WINDING_WAY
                ) selection?.let(::acceptRulesModeChoice)
                result.step
            }
            is ExactlyOneSubmissionResult.Rejected -> {
                castSnapshot?.let { castAttempts += it.copy(actualAutoPaymentResult = "REJECTED:${result.reason}") }
                val pool = state.getEntity(projectId)?.get<com.wingedsheep.engine.state.components.player.ManaPoolComponent>()
                val battlefield = state.controlledBattlefield(projectId).map { id ->
                    "${name(id)}:${if (state.getEntity(id)?.has<TappedComponent>() == true) "tapped" else "untapped"}"
                }
                audit += "illegal action ${action::class.simpleName} ${actionName ?: "unknown"}: ${result.reason}; " +
                    "pool=$pool; battlefield=$battlefield"
                stopReason = "ILLEGAL_ACTION"
                break
            }
        }
        actions++
        val events = step.events
        events.filterIsInstance<SpellCastEvent>()
            .filter { it.casterId == projectId }
            .forEach { event ->
                when (event.cardName) {
                    ProjectXStateAnalyzer.EVOLUTION_WITNESS -> witnessCasts += turn
                    ProjectXStateAnalyzer.IVY_LANE_DENIZEN -> if (firstIvyCastTurn == null) firstIvyCastTurn = turn
                    ProjectXStateAnalyzer.FALKENRATH_NOBLE -> nobleCastTurns += turn
                    "Llanowar Elves" -> llanowarCasts += turn
                }
                pendingLlanowarFundedCandidates.remove(event.spellEntityId)?.let { activationTurn ->
                    llanowarFundedSpells += "${event.cardName}@T$turn:enabled-by-activation-T$activationTurn"
                }
            }
        combatDamageDealt += events.filterIsInstance<DamageDealtEvent>()
            .filter { it.sourceId != null && it.targetId == blankId && it.isCombatDamage }
            .sumOf(DamageDealtEvent::amount)
        events.filterIsInstance<AbilityActivatedEvent>()
            .filter { it.controllerId == projectId && it.sourceName == ProjectXStateAnalyzer.EVOLUTION_WITNESS }
            .forEach { witnessAdaptActivations += turn }
        if (acting == projectId && action is CastSpell) {
            events.filterIsInstance<AbilityActivatedEvent>()
                .filter { it.controllerId == projectId && it.sourceName == "Llanowar Elves" && it.isManaAbility }
                .forEach { event ->
                    llanowarActivations += LlanowarActivationTelemetry(
                        turn = turn,
                        entityId = event.sourceId.toString(),
                        manaBefore = castSnapshot?.manaPool ?: manaPool(state),
                        manaAfter = manaPool(step.state),
                        newlyAffordableSpells = listOf(actionName ?: name(action.cardId)),
                    )
                    llanowarFundedSpells += "${actionName ?: name(action.cardId)}@T$turn:auto-tap"
                }
        }
        events.filterIsInstance<CountersAddedEvent>()
            .filter {
                it.entityName == ProjectXStateAnalyzer.EVOLUTION_WITNESS &&
                    (it.counterType == "+1/+1" || it.counterType == com.wingedsheep.sdk.core.CounterType.PLUS_ONE_PLUS_ONE.name)
            }
            .forEach { event ->
                val counterSource = when (resolvingSource) {
                    ProjectXStateAnalyzer.EVOLUTION_WITNESS -> "ADAPT"
                    ProjectXStateAnalyzer.IVY_LANE_DENIZEN -> "IVY_LANE_DENIZEN"
                    null -> "UNKNOWN"
                    else -> "OTHER:$resolvingSource"
                }
                witnessCounters += WitnessCounterTelemetry(turn, event.amount, counterSource)
                pendingWitnessCounterSources.addLast(counterSource)
            }
        events.filterIsInstance<AbilityTriggeredEvent>()
            .filter { it.controllerId == projectId && it.sourceName == ProjectXStateAnalyzer.EVOLUTION_WITNESS }
            .forEach { event ->
                val counterSource = pendingWitnessCounterSources.removeLastOrNull() ?: "UNKNOWN"
                witnessCounterTriggers += WitnessTriggerTelemetry(turn, counterSource, event.description)
                pendingWitnessTriggerSources.addLast(counterSource)
            }
        events.filterIsInstance<AbilityTriggeredEvent>()
            .filter { it.controllerId == projectId && it.sourceName == ProjectXStateAnalyzer.NETTLE_SENTINEL }
            .forEach { nettle += "T$turn:green-spell untap trigger" }
        events.filterIsInstance<SpellCastEvent>()
            .filter { it.casterId == projectId && it.cardName == ProjectXStateAnalyzer.WIREWOOD_HERALD }
            .forEach { heraldCast += "T$turn" }
        events.filterIsInstance<AbilityTriggeredEvent>()
            .filter { it.controllerId == projectId && it.sourceName == ProjectXStateAnalyzer.WIREWOOD_HERALD }
            .forEach { heraldTriggerCreated += "T$turn:${it.description}" }
        events.filterIsInstance<ZoneChangeEvent>().forEach { event ->
            if (event.ownerId == projectId && event.entityName in analyzer.primaryRoles) {
                if (event.toZone in setOf(Zone.HAND, Zone.BATTLEFIELD)) primaryRolesSeen += event.entityName
                if (event.fromZone == Zone.BATTLEFIELD && event.toZone != Zone.BATTLEFIELD) {
                    primaryRoleDeparture[event.entityName] = when {
                        event.wasSacrificed -> "SACRIFICED"
                        event.entityId in primaryRoleAttackers -> "USED_IN_COMBAT"
                        else -> "OTHERWISE_LEFT_BATTLEFIELD"
                    }
                }
            }
            if (event.ownerId == projectId && event.entityName == ProjectXStateAnalyzer.WIREWOOD_HERALD) {
                if (event.fromZone == Zone.LIBRARY && event.toZone == Zone.HAND) heraldDrawn += "T$turn"
                if (event.fromZone == Zone.BATTLEFIELD && event.toZone == Zone.GRAVEYARD) {
                    heraldDied += "T$turn:${if (event.wasSacrificed) "sacrificed" else "died"}"
                }
            }
            if (event.ownerId == projectId && event.fromZone == Zone.LIBRARY) {
                when (event.toZone) {
                    Zone.HAND -> selection?.toHand?.add(event.entityName)
                    Zone.GRAVEYARD -> selection?.toGraveyard?.add(event.entityName)
                    else -> Unit
                }
            }
            val witness = pendingWitnessTarget
            if (witness != null && event.ownerId == projectId && event.entityId == witness.entityId &&
                event.fromZone == Zone.GRAVEYARD && event.toZone == Zone.HAND
            ) {
                witnessEvents += "${event.entityName}@T${witness.turn}"
                witnessRecursions += WitnessRecursionTelemetry(
                    turn = witness.turn,
                    entityId = event.entityId.toString(),
                    permanent = event.entityName,
                    counterSource = witness.counterSource,
                )
                pendingWitnessTarget = null
            }
            if (event.ownerId == projectId && event.toZone == Zone.BATTLEFIELD) {
                witnessRecursions.lastOrNull { it.entityId == event.entityId.toString() && it.deployedTurn == null }
                    ?.deployedTurn = turn
            }
        }
        pendingLlanowarActivation?.takeIf { pending ->
            events.any { event ->
                event is AbilityActivatedEvent && event.controllerId == projectId &&
                    event.sourceId == pending.sourceId && event.sourceName == "Llanowar Elves"
            }
        }?.let { pending ->
            val afterAffordable = affordableCastIds(step.state)
            val newlyAffordable = afterAffordable - pending.affordableCastIdsBefore
            newlyAffordable.forEach { pendingLlanowarFundedCandidates[it] = turn }
            llanowarActivations += LlanowarActivationTelemetry(
                turn = turn,
                entityId = pending.sourceId.toString(),
                manaBefore = pending.manaBefore,
                manaAfter = manaPool(step.state),
                newlyAffordableSpells = newlyAffordable.map(::name).sorted(),
            )
            pendingLlanowarActivation = null
        }
        events.filterIsInstance<AttackersDeclaredEvent>()
            .filter { it.attackingPlayerId == projectId }
            .forEach { event ->
                primaryRoleAttackers += event.attackers.filter { id -> name(id) in analyzer.primaryRoles }
            }
        events.filterIsInstance<ResolvedEvent>().forEach { event ->
            val current = selection
            if (current != null && event.name == current.name) {
                val line = renderSelectionLine(current)
                if (current.name == ProjectXStateAnalyzer.WINDING_WAY) winding += line else lead += line
                selection = null
            }
        }
        events.filterIsInstance<GameEndedEvent>().lastOrNull()?.let { ended ->
            winner = ended.winnerId?.let { id -> state.getEntity(id)?.get<PlayerComponent>()?.name ?: id.toString() }
            gameOverTurn = turn
            ordinaryTerminalAction = actions + 1
            terminalMechanism = classifyTerminal(events, ended, lethalTurn != null)
        }
        if (acting == projectId && action is PlayLand && actionName in setOf("Khalni Garden", "Haunted Mire")) {
            val tapped = step.state.getEntity(action.cardId)?.has<TappedComponent>() == true
            val plant = events.filterIsInstance<ZoneChangeEvent>().any { it.entityName == "Plant" && it.toZone == Zone.BATTLEFIELD }
            taplands += "${actionName}@T$turn:${if (tapped) "tapped" else "untapped"}${if (plant) ":Plant" else ""}"
        }
        if (beforeHuge && action is ActivateAbility && actionName == ProjectXStateAnalyzer.CARRION_FEEDER) {
            audit += "agent physically iterated an already-proven loop on T$turn"
        }
        state = step.state
        observe(state)
    }

    if (lethalTurn != null) stopReason = "DETERMINISTIC_LETHAL"
    else if (state.gameOver) stopReason = "ENGINE_GAME_OVER"
    else if (actions >= 4_000) {
        stopReason = "MAX_ACTIONS"
        audit += "exceeded action cap"
    }
    selection?.let { audit += "selection spell ${it.name} remained unresolved" }
    if (lifeTurn != null && hugeTurn == null) audit += "infinite life without an unbounded loop"
    if (lethalMechanism == "FALKENRATH_NOBLE_DRAIN" && hugeTurn == null) audit += "Noble lethal without a death loop"
    if (engineTurn != null && hugeTurn == null) audit += "primary engine without huge Feeder classification"
    if (witnessCounterTriggers.any { it.counterSource == "UNKNOWN" || it.counterSource.startsWith("OTHER:") }) {
        audit += "Evolution Witness counter-trigger source was not attributable"
    }
    if (witnessRecursions.size != witnessEvents.size) {
        audit += "structured and legacy Evolution Witness recursion counts disagree"
    }

    val battlefieldCreatures = state.controlledBattlefield(projectId).count { id ->
        state.getEntity(id)?.get<CardComponent>()?.isCreature == true
    }
    val firstComboTurn = listOfNotNull(engineTurn, secondaryTurn).minOrNull()
    val firstComboAction = listOfNotNull(primaryRecognitionAction, secondaryRecognitionAction).minOrNull()
    val actualWinningTurn = listOfNotNull(gameOverTurn, lethalTurn).minOrNull()
    val winningMechanism = when {
        lethalTurn != null && (gameOverTurn == null || lethalTurn!! <= gameOverTurn!!) ->
            "DETERMINISTIC_COMBO:${lethalMechanism ?: "UNCLASSIFIED"}"
        terminalMechanism != null -> terminalMechanism
        else -> null
    }
    if (winner == null && lethalTurn != null) winner = "Project X"
    val comboBeforeOrdinaryLethal = firstComboAction != null &&
        (ordinaryTerminalAction == null || firstComboAction!! < ordinaryTerminalAction!!)
    val ordinaryLethalBeforeCombo = ordinaryTerminalAction != null &&
        (firstComboAction == null || ordinaryTerminalAction!! < firstComboAction!!)
    val functional = when {
        engineTurn != null -> "PRIMARY_COMBO_ASSEMBLED"
        secondaryTurn != null -> "FUNCTIONAL_SECONDARY_LOOP"
        battlefieldCreatures >= 3 || winding.isNotEmpty() || lead.isNotEmpty() ||
            heraldTargets.isNotEmpty() || witnessEvents.isNotEmpty() -> "FUNCTIONAL_WITHOUT_PRIMARY_COMBO"
        else -> "NONFUNCTIONAL_WITHOUT_COMBO"
    }
    classifyPrimaryRoleShortState(
        state, projectId, analyzer, projectTurn(state), manaConstraints,
        primaryRolesSeen, primaryRolesTutored, primaryRoleDeparture,
        gameEnded = state.gameOver,
    )?.let { snapshot ->
        roleShortStates.removeIf { it.turn == snapshot.turn && it.missingRole == snapshot.missingRole }
        roleShortStates += snapshot
    }
    val ivyThreeManaStallTurns = manaConstraints.asSequence()
        .filter {
            it.spell == ProjectXStateAnalyzer.IVY_LANE_DENIZEN &&
                it.category == "INSUFFICIENT_TOTAL_MANA" &&
                "green=true" in it.detail
        }
        .mapNotNull { constraint ->
            Regex("availableMana=(\\d+)").find(constraint.detail)?.groupValues?.get(1)?.toIntOrNull()
                ?.let { constraint.turn to it }
        }
        .groupBy({ it.first }, { it.second })
        .filterValues { observed -> observed.maxOrNull() == 3 }
        .keys
        .filter { turn -> firstIvyCastTurn == null || turn < firstIvyCastTurn!! }
        .sorted()

    return ProjectXGoldfishGame(
        game = gameNumber,
        seed = seed,
        seedHex = "0x${seed.toULong().toString(16).uppercase()}",
        mulligans = mulligans,
        keptHand = keptHand,
        openingColorAccess = openingColorAccess,
        t1Development = t1,
        firstMeaningfulDevelopmentTurn = firstMeaningful,
        actualWinningTurn = actualWinningTurn,
        winningMechanism = winningMechanism,
        engineTurn = engineTurn,
        firstValidatedComboTurn = firstComboTurn,
        hugeFeederTurn = hugeTurn,
        infiniteLifeTurn = lifeTurn,
        nobleDeterministicDrainTurn = if (lethalMechanism == "FALKENRATH_NOBLE_DRAIN") lethalTurn else null,
        deterministicLethalTurn = lethalTurn,
        lethalMechanism = lethalMechanism,
        comboAvailableBeforeOrdinaryLethal = comboBeforeOrdinaryLethal,
        ordinaryLethalEndedBeforeComboAssembly = ordinaryLethalBeforeCombo,
        winner = winner,
        gameOverTurn = gameOverTurn,
        terminalMechanism = terminalMechanism,
        combatLethal = terminalMechanism == "COMBAT_LETHAL",
        triggeredAbilityLethal = terminalMechanism == "TRIGGERED_OR_ABILITY_LETHAL",
        deterministicComboLethal = lethalTurn != null,
        otherTerminalState = terminalMechanism != null && terminalMechanism !in setOf("COMBAT_LETHAL", "TRIGGERED_OR_ABILITY_LETHAL", "DETERMINISTIC_COMBO_LETHAL"),
        castAttempts = castAttempts,
        herald = HeraldTelemetry(
            heraldDrawn, heraldCast, heraldBattlefield.toList(), heraldDied,
            heraldTriggerCreated, heraldTriggerResolved, heraldTargets, heraldAvailability.toList()
        ),
        manaConstraints = manaConstraints.toList(),
        heraldTutorTargets = heraldTargets,
        witnessRecursionEvents = witnessEvents,
        witness = WitnessTelemetry(
            casts = witnessCasts,
            adaptActivations = witnessAdaptActivations,
            countersPlaced = witnessCounters,
            counterTriggers = witnessCounterTriggers,
            successfulRecursions = witnessRecursions,
        ),
        llanowar = LlanowarTelemetry(
            casts = llanowarCasts,
            activations = llanowarActivations,
            fundedSpells = llanowarFundedSpells,
        ),
        firstIvyCastTurn = firstIvyCastTurn,
        ivyThreeManaStallTurns = ivyThreeManaStallTurns,
        nobleCastTurns = nobleCastTurns,
        nobleAvailableTurns = nobleAvailableTurns.toList(),
        noblePayoffMissingTurns = noblePayoffMissingTurns.toList(),
        combatDamageDealt = combatDamageDealt,
        maximumCreatureBoard = maximumCreatureBoard,
        birchloreManaContribution = birchlore,
        nettleUntapContribution = nettle,
        quirionManaContribution = quirion,
        windingWay = winding,
        leadTheStampede = lead,
        colorStrandedCards = stranded.toList(),
        taplandTempoEvents = taplands,
        khalniGardenTempoEvents = taplands.filter { it.startsWith("Khalni Garden") },
        hauntedMireTempoEvents = taplands.filter { it.startsWith("Haunted Mire") },
        exactlyOneRoleMissingTurns = oneMissingTurns.toList(),
        primaryRoleShortStates = roleShortStates.toList(),
        functionalWithoutCombo = functional,
        secondaryWitnessLoopTurn = secondaryTurn,
        actions = actions,
        stopReason = stopReason,
        auditErrors = audit,
    )
}

internal fun recordAgentModeChoice(
    selection: SelectionTelemetry,
    decision: ChooseModeDecision,
    response: ModesChosenResponse,
) {
    val selected = response.selectedModes.single()
    selection.agentChoice = decision.modes.first { it.index == selected }.text
}

internal fun recordAgentOptionChoice(
    selection: SelectionTelemetry,
    decision: ChooseOptionDecision,
    response: OptionChosenResponse,
) {
    selection.agentChoice = decision.options[response.optionIndex]
}

internal fun acceptRulesModeChoice(selection: SelectionTelemetry) {
    selection.rulesChoice = selection.agentChoice
}

internal fun renderSelectionLine(selection: SelectionTelemetry): String {
    val mode = if (selection.name == ProjectXStateAnalyzer.WINDING_WAY) {
        "agent=${selection.agentChoice ?: "UNRECORDED"}:rules=${selection.rulesChoice ?: "UNRESOLVED"}"
    } else "rules=all-creatures"
    return "T${selection.turn}:$mode:hand=${selection.toHand}:grave=${selection.toGraveyard}"
}

internal fun castAttemptBeforeExecution(
    state: GameState,
    playerId: EntityId,
    action: CastSpell,
    turn: Int,
    spell: String,
    analyzer: ProjectXStateAnalyzer,
    enumerator: LegalActionEnumerator,
    name: (EntityId) -> String,
): CastAttemptTelemetry {
    val pool = state.getEntity(playerId)
        ?.get<com.wingedsheep.engine.state.components.player.ManaPoolComponent>()
    val battlefield = state.controlledBattlefield(playerId)
    val lands = battlefield.filter { state.getEntity(it)?.get<CardComponent>()?.isLand == true }
    val (tappedLands, untappedLands) = lands.partition { state.getEntity(it)?.has<TappedComponent>() == true }
    val untappedElves = battlefield.filter { analyzer.isElf(state, it) && analyzer.isUntapped(state, it) }
    val birchlore = battlefield.firstOrNull {
        analyzer.name(state, it) == ProjectXStateAnalyzer.BIRCHLORE_RANGERS
    }
    val full = enumerator.enumerate(state, playerId, EnumerationMode.FULL)
    // One card can expose several CastSpell variants (notably Birchlore Rangers face up or as a
    // morph). Correlate the exact submitted action; matching only the entity ID can attach another
    // variant's cost and auto-tap plan to the trace.
    val castOffer = full.firstOrNull { it.action == action }
    val autoTapPreview = castOffer?.autoTapPreview
    val quirionLines = full.filter { legal ->
        val activate = legal.action as? ActivateAbility ?: return@filter false
        analyzer.name(state, activate.sourceId) == ProjectXStateAnalyzer.QUIRION_RANGER && legal.affordable
    }.map { it.description }
    val proposed = when {
        autoTapPreview != null -> "AUTO_TAP:${autoTapPreview.map(name)}"
        action.paymentStrategy is PaymentStrategy.FromPool -> "FROM_POOL"
        action.paymentStrategy is PaymentStrategy.Explicit -> "EXPLICIT:${action.paymentStrategy}"
        (pool?.total ?: 0) > 0 -> "AUTO_PAY:EXISTING_POOL"
        else -> "AUTO_PAY:NO_ORDINARY_SOURCE_PLAN"
    }
    val legality = when {
        castOffer == null -> "NO_MATCHING_ENUMERATED_CAST"
        !castOffer.affordable -> "ENUMERATOR_UNAFFORDABLE"
        castOffer.autoTapPreview != null -> "AFFORDABLE_WITH_AUTO_TAP_PREVIEW"
        (pool?.total ?: 0) > 0 -> "AFFORDABLE_FROM_EXISTING_POOL"
        birchlore != null && untappedElves.size >= 2 ->
            "AFFORDABLE_VIA_EXPLICIT_BIRCHLORE_MANA; AUTO_PAY_CANNOT_ACTIVATE_IT"
        else -> "ENUMERATOR_AFFORDABLE; NO_AUTO_TAP_PREVIEW"
    }
    return CastAttemptTelemetry(
        turn = turn,
        spell = spell,
        manaPool = "W=${pool?.white ?: 0},U=${pool?.blue ?: 0},B=${pool?.black ?: 0},R=${pool?.red ?: 0},G=${pool?.green ?: 0},C=${pool?.colorless ?: 0}",
        untappedLands = untappedLands.map(name),
        tappedLands = tappedLands.map(name),
        availableBirchloreMana = if (birchlore != null && untappedElves.size >= 2) {
            "YES:${untappedElves.map(name)}"
        } else "NO",
        availableQuirionLines = quirionLines,
        proposedManaPaymentPlan = proposed,
        actualAutoPaymentResult = "NOT_EXECUTED",
        preExecutionLegalityReason = legality,
    )
}

internal fun classifyTerminal(
    events: List<GameEvent>,
    ended: GameEndedEvent,
    deterministicComboAlreadyRecognized: Boolean,
): String = when {
    events.filterIsInstance<DamageDealtEvent>().any { it.targetIsPlayer && it.isCombatDamage } -> "COMBAT_LETHAL"
    deterministicComboAlreadyRecognized -> "DETERMINISTIC_COMBO_LETHAL"
    events.any { it is LifeChangedEvent && it.reason == LifeChangeReason.LIFE_LOSS } ||
        events.filterIsInstance<DamageDealtEvent>().any { it.targetIsPlayer && !it.isCombatDamage } ->
        "TRIGGERED_OR_ABILITY_LETHAL"
    else -> "OTHER:${ended.reason}"
}

/** Whether Wirewood Herald can legally find the named role in this exact library state. */
internal fun isHeraldSearchableRole(
    state: GameState,
    playerId: EntityId,
    role: String,
): Boolean = state.getZone(playerId, Zone.LIBRARY).any { cardId ->
    state.getEntity(cardId)?.get<CardComponent>()?.name == role &&
        PredicateEvaluator().matches(
            state,
            state.projectedState,
            cardId,
            GameObjectFilter.Any.withSubtype("Elf"),
            PredicateContext(controllerId = playerId),
        )
}

internal fun classifyManaConstraints(
    state: GameState,
    playerId: EntityId,
    analyzer: ProjectXStateAnalyzer,
    enumerator: LegalActionEnumerator,
    turn: Int,
): Set<ManaConstraintTelemetry> {
    val battlefield = state.controlledBattlefield(playerId)
    val untappedNames = battlefield.filter { analyzer.isUntapped(state, it) }.mapNotNull { analyzer.name(state, it) }
    val tappedTempoLandCount = battlefield.count { id ->
        analyzer.name(state, id) in setOf("Khalni Garden", "Haunted Mire") && !analyzer.isUntapped(state, id)
    }
    val tappedForest = battlefield.any { id ->
        analyzer.name(state, id) == "Forest" && !analyzer.isUntapped(state, id)
    }
    val birchloreAvailable = battlefield.count { analyzer.isElf(state, it) && analyzer.isUntapped(state, it) } >= 2 &&
        battlefield.any { analyzer.name(state, it) == ProjectXStateAnalyzer.BIRCHLORE_RANGERS }
    val pool = state.getEntity(playerId)?.get<com.wingedsheep.engine.state.components.player.ManaPoolComponent>()
    val green = (pool?.green ?: 0) > 0 || birchloreAvailable || untappedNames.any { it in setOf("Forest", "Khalni Garden", "Haunted Mire") }
    val black = (pool?.black ?: 0) > 0 || birchloreAvailable || untappedNames.any { it in setOf("Swamp", "Haunted Mire") }
    val totalMana = untappedNames.count { it in setOf("Forest", "Swamp", "Khalni Garden", "Haunted Mire") } +
        (pool?.total ?: 0) + if (birchloreAvailable) 1 else 0
    val legal = enumerator.enumerate(state, playerId, EnumerationMode.FULL)
    val castOffers = legal.mapNotNull { offer ->
        (offer.action as? CastSpell)?.cardId?.let { it to offer }
    }.toMap()
    val quirionAbilityAvailable = legal.any { offer ->
        val ability = offer.action as? ActivateAbility
        ability != null && offer.affordable && analyzer.name(state, ability.sourceId) == ProjectXStateAnalyzer.QUIRION_RANGER
    }
    val landDropAvailable = (state.getEntity(playerId)
        ?.get<com.wingedsheep.engine.state.components.player.LandDropsComponent>()?.remaining ?: 0) > 0
    val quirionAvailable = quirionAbilityAvailable && ((landDropAvailable && tappedForest) ||
        (battlefield.any { analyzer.name(state, it) == ProjectXStateAnalyzer.BIRCHLORE_RANGERS } &&
            battlefield.count { analyzer.isElf(state, it) && analyzer.isUntapped(state, it) } == 1 &&
            battlefield.any { analyzer.isElf(state, it) && !analyzer.isUntapped(state, it) }))
    return state.getHand(playerId).mapNotNull { id ->
        val cardName = analyzer.name(state, id) ?: return@mapNotNull null
        val offer = castOffers[id]
        if (offer?.affordable == true && offer.autoTapPreview != null) return@mapNotNull null
        val (manaValue, color) = PROJECT_X_MANA_REQUIREMENTS[cardName] ?: return@mapNotNull null
        val lacksColor = (color == 'G' && !green) || (color == 'B' && !black)
        val category = when {
            birchloreAvailable -> "BIRCHLORE_MANA_AVAILABLE"
            quirionAvailable -> "QUIRION_SEQUENCE_AVAILABLE"
            totalMana + tappedTempoLandCount >= manaValue && totalMana < manaValue -> "TAPPED_LAND_TEMPO"
            totalMana < manaValue -> "INSUFFICIENT_TOTAL_MANA"
            lacksColor -> "GENUINE_COLOR_UNCASTABLE"
            else -> "OTHER_PAYMENT_CONSTRAINT"
        }
        ManaConstraintTelemetry(
            turn, cardName, category,
            "availableMana=$totalMana,tappedTaplands=$tappedTempoLandCount,green=$green,black=$black"
        )
    }.toSet()
}

/**
 * A battlefield-assembly view that complements (and does not redefine) the historical
 * hand-or-battlefield exactly-one-role-missing metric used by Sample #1.
 */
internal fun classifyPrimaryRoleShortState(
    state: GameState,
    playerId: EntityId,
    analyzer: ProjectXStateAnalyzer,
    turn: Int,
    constraints: Collection<ManaConstraintTelemetry>,
    seenRoles: Set<String>,
    tutoredRoles: Set<String>,
    departureReasons: Map<String, String>,
    gameEnded: Boolean = false,
): PrimaryRoleShortState? {
    val missing = analyzer.primaryRoles - analyzer.battlefieldNames(state, playerId)
    if (missing.size != 1) return null
    val role = missing.single()
    val zones = buildList {
        if (role in analyzer.handNames(state, playerId)) add("HAND")
        if (role in analyzer.libraryNames(state, playerId)) add("LIBRARY")
        if (role in analyzer.graveyardNames(state, playerId)) add("GRAVEYARD")
        if (state.getZone(playerId, Zone.EXILE).any { analyzer.name(state, it) == role }) add("EXILE")
        if (state.stack.any { analyzer.name(state, it) == role }) add("STACK")
    }
    val roleConstraints = constraints.filter { it.turn == turn && it.spell == role }
    val attributions = buildList {
        if ("HAND" in zones && role in tutoredRoles) add("TUTORED_BUT_NOT_YET_CAST")
        if ("HAND" in zones && roleConstraints.any {
                it.category in setOf(
                    "GENUINE_COLOR_UNCASTABLE", "BIRCHLORE_MANA_AVAILABLE",
                    "QUIRION_SEQUENCE_AVAILABLE", "TAPPED_LAND_TEMPO",
                    "INSUFFICIENT_TOTAL_MANA", "OTHER_PAYMENT_CONSTRAINT",
                )
            }
        ) add("MANA_CONSTRAINED")
        departureReasons[role]?.let(::add)
        if ("LIBRARY" in zones && role !in seenRoles) add("NEVER_DRAWN")
        if (gameEnded && "HAND" in zones) add("GAME_ENDED_BEFORE_DEPLOYMENT")
        if (isEmpty()) add("OTHER_UNDEPLOYED_OR_TRANSIENT")
    }.distinct()
    return PrimaryRoleShortState(turn, role, zones, attributions)
}

private val PROJECT_X_MANA_REQUIREMENTS = mapOf(
    "Carrion Feeder" to (1 to 'B'), "Falkenrath Noble" to (4 to 'B'),
    "Safehold Elite" to (2 to 'G'), "Ivy Lane Denizen" to (4 to 'G'),
    "Wirewood Herald" to (2 to 'G'), "Evolution Witness" to (3 to 'G'),
    "Nettle Sentinel" to (1 to 'G'), "Birchlore Rangers" to (1 to 'G'),
    "Llanowar Elves" to (1 to 'G'),
    "Essence Warden" to (1 to 'G'), "Masked Vandal" to (2 to 'G'),
    "Quirion Ranger" to (1 to 'G'), "Winding Way" to (2 to 'G'),
    "Lead the Stampede" to (3 to 'G'),
)

internal fun summarizeProjectX(games: List<ProjectXGoldfishGame>): ProjectXGoldfishSummary {
    fun countBy(turn: Int, value: (ProjectXGoldfishGame) -> Int?) = games.count { (value(it) ?: Int.MAX_VALUE) <= turn }
    fun median(values: List<Int>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle].toDouble() else (sorted[middle - 1] + sorted[middle]) / 2.0
    }
    val actualWinTurns = games.mapNotNull(ProjectXGoldfishGame::actualWinningTurn)
    val comboTurns = games.mapNotNull(ProjectXGoldfishGame::firstValidatedComboTurn)
    fun rate(count: Int): Double = count.toDouble() / games.size
    val comboAssemblyGames = comboTurns.size
    val comboBeforeGameEndGames = games.count {
        it.firstValidatedComboTurn != null && it.actualWinningTurn != null &&
            it.firstValidatedComboTurn <= it.actualWinningTurn
    }
    val mulliganGames = games.count { it.mulligans > 0 }
    val functionalWithoutComboGames = games.count {
        it.functionalWithoutCombo.startsWith("FUNCTIONAL_WITHOUT") || it.functionalWithoutCombo == "FUNCTIONAL_SECONDARY_LOOP"
    }
    val exactlyOneRoleMissingGames = games.count { it.exactlyOneRoleMissingTurns.isNotEmpty() }
    val roleShortGamesByMissingRole = ProjectXStateAnalyzer().primaryRoles.associateWith { role ->
        games.count { game -> game.primaryRoleShortStates.any { it.missingRole == role } }
    }.toSortedMap()
    val roleShortGamesByAttribution = games.flatMap { game ->
        game.primaryRoleShortStates.flatMap(PrimaryRoleShortState::attributions).distinct()
    }.groupingBy { it }.eachCount().toSortedMap()
    return ProjectXGoldfishSummary(
        actualWinsByT4 = countBy(4, ProjectXGoldfishGame::actualWinningTurn),
        actualWinsByT5 = countBy(5, ProjectXGoldfishGame::actualWinningTurn),
        actualWinsByT6 = countBy(6, ProjectXGoldfishGame::actualWinningTurn),
        medianActualWinningTurn = median(actualWinTurns),
        meanActualWinningTurn = actualWinTurns.takeIf { it.isNotEmpty() }?.average(),
        winningMechanismDistribution = games.mapNotNull(ProjectXGoldfishGame::winningMechanism)
            .groupingBy { it }.eachCount().toSortedMap(),
        engineByT4 = countBy(4, ProjectXGoldfishGame::engineTurn),
        engineByT5 = countBy(5, ProjectXGoldfishGame::engineTurn),
        engineByT6 = countBy(6, ProjectXGoldfishGame::engineTurn),
        lethalByT4 = countBy(4, ProjectXGoldfishGame::deterministicLethalTurn),
        lethalByT5 = countBy(5, ProjectXGoldfishGame::deterministicLethalTurn),
        lethalByT6 = countBy(6, ProjectXGoldfishGame::deterministicLethalTurn),
        medianEngineTurn = median(games.mapNotNull(ProjectXGoldfishGame::engineTurn)),
        medianLethalTurn = median(games.mapNotNull(ProjectXGoldfishGame::deterministicLethalTurn)),
        anyInfiniteByT4 = countBy(4, ProjectXGoldfishGame::firstValidatedComboTurn),
        anyInfiniteByT5 = countBy(5, ProjectXGoldfishGame::firstValidatedComboTurn),
        anyInfiniteByT6 = countBy(6, ProjectXGoldfishGame::firstValidatedComboTurn),
        medianComboTurn = median(comboTurns),
        comboAssemblyGames = comboAssemblyGames,
        comboAssemblyRate = rate(comboAssemblyGames),
        comboBeforeGameEndGames = comboBeforeGameEndGames,
        comboBeforeGameEndRate = rate(comboBeforeGameEndGames),
        comboBeforeOrdinaryLethalGames = games.count { it.comboAvailableBeforeOrdinaryLethal },
        ordinaryLethalBeforeComboGames = games.count { it.ordinaryLethalEndedBeforeComboAssembly },
        infiniteLifeGames = games.count { it.infiniteLifeTurn != null },
        nobleDeterministicDrainGames = games.count { it.nobleDeterministicDrainTurn != null },
        hugeFeederGames = games.count { it.hugeFeederTurn != null },
        hugeFeederWithoutImmediateLethalGames = games.count {
            it.hugeFeederTurn != null && (it.deterministicLethalTurn == null || it.hugeFeederTurn < it.deterministicLethalTurn)
        },
        mulliganGames = mulliganGames,
        totalMulligans = games.sumOf(ProjectXGoldfishGame::mulligans),
        mulliganGameRate = rate(mulliganGames),
        functionalWithoutComboGames = functionalWithoutComboGames,
        functionalWithoutComboRate = rate(functionalWithoutComboGames),
        exactlyOneRoleMissingGames = exactlyOneRoleMissingGames,
        exactlyOneRoleMissingRate = rate(exactlyOneRoleMissingGames),
        roleShortGamesByMissingRole = roleShortGamesByMissingRole,
        roleShortGamesByAttribution = roleShortGamesByAttribution,
        heraldContributionGames = games.count { it.heraldTutorTargets.isNotEmpty() },
        witnessContributionGames = games.count { it.witnessRecursionEvents.isNotEmpty() },
        birchloreContributionGames = games.count { it.birchloreManaContribution.isNotEmpty() },
        nettleContributionGames = games.count { it.nettleUntapContribution.isNotEmpty() },
        quirionContributionGames = games.count { it.quirionManaContribution.isNotEmpty() },
        colorBottleneckGames = games.count { it.colorStrandedCards.isNotEmpty() },
        khalniGardenTempoGames = games.count { game -> game.taplandTempoEvents.any { it.startsWith("Khalni Garden") } },
        hauntedMireTempoGames = games.count { game -> game.taplandTempoEvents.any { it.startsWith("Haunted Mire") } },
        intentionallyHeldGames = games.count { game -> game.manaConstraints.any { it.category == "INTENTIONALLY_HELD" } },
        birchloreManaAvailableGames = games.count { game -> game.manaConstraints.any { it.category == "BIRCHLORE_MANA_AVAILABLE" } },
        quirionSequenceAvailableGames = games.count { game -> game.manaConstraints.any { it.category == "QUIRION_SEQUENCE_AVAILABLE" } },
        tappedLandConstraintGames = games.count { game -> game.manaConstraints.any { it.category == "TAPPED_LAND_TEMPO" } },
        insufficientTotalManaGames = games.count { game -> game.manaConstraints.any { it.category == "INSUFFICIENT_TOTAL_MANA" } },
    )
}

internal fun renderProjectXMarkdown(block: ProjectXGoldfishBlock): String = buildString {
    val s = block.summary
    appendLine("# Project X v0.2 — ${block.sampleName}")
    appendLine()
    appendLine("Horizon: T${block.horizon}; Project X is on the play; opponent is a no-interaction 60-Plains goldfish.")
    appendLine()
    appendLine("## Summary")
    appendLine()
    appendLine("- Actual wins by T4 / T5 / T6: ${s.actualWinsByT4}/30, ${s.actualWinsByT5}/30, ${s.actualWinsByT6}/30")
    appendLine("- Median / mean actual winning turn: ${s.medianActualWinningTurn ?: "not reached"} / ${s.meanActualWinningTurn ?: "not reached"}")
    appendLine("- Winning mechanisms: ${s.winningMechanismDistribution}")
    appendLine("- Primary engine online by T4 / T5 / T6: ${s.engineByT4}/30, ${s.engineByT5}/30, ${s.engineByT6}/30")
    appendLine("- Any validated infinite online by T4 / T5 / T6: ${s.anyInfiniteByT4}/30, ${s.anyInfiniteByT5}/30, ${s.anyInfiniteByT6}/30")
    appendLine("- Combo assembly / median combo turn: ${s.comboAssemblyGames}/30 (${s.comboAssemblyRate * 100}%) / ${s.medianComboTurn ?: "not reached"}")
    appendLine("- Combo assembled before game end: ${s.comboBeforeGameEndGames}/30 (${s.comboBeforeGameEndRate * 100}%)")
    appendLine("- Combo before ordinary lethal / ordinary lethal before combo: ${s.comboBeforeOrdinaryLethalGames}/30 / ${s.ordinaryLethalBeforeComboGames}/30")
    appendLine("- Deterministic combo lethal by T4 / T5 / T6: ${s.lethalByT4}/30, ${s.lethalByT5}/30, ${s.lethalByT6}/30")
    appendLine("- Infinite life: ${s.infiniteLifeGames}/30")
    appendLine("- Falkenrath Noble deterministic drain: ${s.nobleDeterministicDrainGames}/30")
    appendLine("- Huge Feeder / huge Feeder before or without immediate lethal: ${s.hugeFeederGames}/30 / ${s.hugeFeederWithoutImmediateLethalGames}/30")
    appendLine("- Mulligan games / rate / total mulligans: ${s.mulliganGames}/30 / ${s.mulliganGameRate * 100}% / ${s.totalMulligans}")
    appendLine("- Functional without primary combo: ${s.functionalWithoutComboGames}/30 (${s.functionalWithoutComboRate * 100}%)")
    appendLine("- At least one exactly-one-role-missing turn: ${s.exactlyOneRoleMissingGames}/30 (${s.exactlyOneRoleMissingRate * 100}%)")
    appendLine("- Battlefield one-role-short games by missing role: ${s.roleShortGamesByMissingRole}")
    appendLine("- Battlefield one-role-short games by determinable attribution: ${s.roleShortGamesByAttribution}")
    appendLine("- Herald / Witness contribution: ${s.heraldContributionGames}/30 / ${s.witnessContributionGames}/30")
    appendLine("- Birchlore / Nettle / Quirion contribution: ${s.birchloreContributionGames}/30 / ${s.nettleContributionGames}/30 / ${s.quirionContributionGames}/30")
    appendLine("- Color / Khalni Garden / Haunted Mire bottleneck games: ${s.colorBottleneckGames}/30 / ${s.khalniGardenTempoGames}/30 / ${s.hauntedMireTempoGames}/30")
    appendLine("- Mana classification games — intentionally held / Birchlore available / Quirion sequence / tapped-land constraint / insufficient total: ${s.intentionallyHeldGames}/30 / ${s.birchloreManaAvailableGames}/30 / ${s.quirionSequenceAvailableGames}/30 / ${s.tappedLandConstraintGames}/30 / ${s.insufficientTotalManaGames}/30")
    appendLine()
    appendLine("## Per-game telemetry")
    appendLine()
    block.games.forEach { game ->
        appendLine("### Game ${game.game} — `${game.seedHex}` (`${game.seed}`)")
        appendLine()
        appendLine("- Mulligans: ${game.mulligans}; kept: ${game.keptHand}; opening colors: ${game.openingColorAccess}")
        appendLine("- T1: ${game.t1Development.ifEmpty { listOf("none") }}; first meaningful: ${game.firstMeaningfulDevelopmentTurn?.let { "T$it" } ?: "none"}")
        appendLine("- Actual win: ${game.actualWinningTurn?.let { "T$it" } ?: "—"} (${game.winningMechanism ?: "none"})")
        appendLine("- Primary / secondary / first combo: ${game.engineTurn?.let { "T$it" } ?: "—"} / ${game.secondaryWitnessLoopTurn?.let { "T$it" } ?: "—"} / ${game.firstValidatedComboTurn?.let { "T$it" } ?: "—"}")
        appendLine("- Huge Feeder / infinite life / Noble drain / deterministic combo lethal: ${game.hugeFeederTurn?.let { "T$it" } ?: "—"} / ${game.infiniteLifeTurn?.let { "T$it" } ?: "—"} / ${game.nobleDeterministicDrainTurn?.let { "T$it" } ?: "—"} / ${game.deterministicLethalTurn?.let { "T$it" } ?: "—"} (${game.lethalMechanism ?: "none"})")
        appendLine("- Combo before ordinary lethal: ${game.comboAvailableBeforeOrdinaryLethal}; ordinary lethal before combo: ${game.ordinaryLethalEndedBeforeComboAssembly}")
        appendLine("- Actual terminal: winner=${game.winner ?: "—"}; turn=${game.gameOverTurn?.let { "T$it" } ?: "—"}; mechanism=${game.terminalMechanism ?: "—"}")
        appendLine("- Herald: drawn=${game.herald.drawn}; cast=${game.herald.cast}; battlefield=${game.herald.battlefield}; died=${game.herald.died}; trigger-created=${game.herald.tutorTriggerCreated}; trigger-resolved=${game.herald.tutorTriggerResolved}; targets=${game.herald.tutorTargets}; one-missing=${game.herald.oneRoleMissingAvailability}")
        appendLine("- Witness legacy returns: ${game.witnessRecursionEvents.ifEmpty { listOf("none") }}")
        appendLine("- Witness detail: ${game.witness}")
        appendLine("- Llanowar: ${game.llanowar}")
        appendLine("- First Ivy cast: ${game.firstIvyCastTurn?.let { "T$it" } ?: "—"}; exactly-three-mana Ivy stalls: ${game.ivyThreeManaStallTurns}")
        appendLine("- Noble: cast=${game.nobleCastTurns}; available=${game.nobleAvailableTurns}; primary-engine-without-Noble=${game.noblePayoffMissingTurns}")
        appendLine("- Fair board: combat damage=${game.combatDamageDealt}; maximum creatures=${game.maximumCreatureBoard}")
        appendLine("- Mana — Birchlore: ${game.birchloreManaContribution.ifEmpty { listOf("none") }}; Nettle: ${game.nettleUntapContribution.ifEmpty { listOf("none") }}; Quirion: ${game.quirionManaContribution.ifEmpty { listOf("none") }}")
        appendLine("- Winding Way: ${game.windingWay.ifEmpty { listOf("none") }}")
        appendLine("- Lead the Stampede: ${game.leadTheStampede.ifEmpty { listOf("none") }}")
        appendLine("- Color stranded: ${game.colorStrandedCards.ifEmpty { listOf("none") }}; Khalni Garden: ${game.khalniGardenTempoEvents.ifEmpty { listOf("none") }}; Haunted Mire: ${game.hauntedMireTempoEvents.ifEmpty { listOf("none") }}")
        appendLine("- Mana constraints: ${game.manaConstraints.ifEmpty { listOf("none") }}")
        appendLine("- Cast attempts: ${game.castAttempts.ifEmpty { listOf("none") }}")
        appendLine("- Exactly one role missing: ${game.exactlyOneRoleMissingTurns}; functional classification: ${game.functionalWithoutCombo}")
        appendLine("- Battlefield one-role-short states: ${game.primaryRoleShortStates.ifEmpty { listOf("none") }}")
        appendLine("- Secondary Witness loop: ${game.secondaryWitnessLoopTurn?.let { "T$it" } ?: "—"}; stop: ${game.stopReason}; actions: ${game.actions}; audit: ${game.auditErrors.ifEmpty { listOf("clean") }}")
        appendLine()
    }
}

private fun pairedDelta(
    control: ProjectXGoldfishGame,
    variant: ProjectXGoldfishGame,
): ProjectXPairedDelta {
    fun turnDelta(controlTurn: Int?, variantTurn: Int?): Int? =
        if (controlTurn != null && variantTurn != null) variantTurn - controlTurn else null
    fun stateChange(controlTurn: Int?, variantTurn: Int?): String =
        "${controlTurn?.let { "T$it" } ?: "NONE"}->${variantTurn?.let { "T$it" } ?: "NONE"}"
    val controlInfinite = control.firstValidatedComboTurn
    val variantInfinite = variant.firstValidatedComboTurn
    return ProjectXPairedDelta(
        firstIvyCastTurn = turnDelta(control.firstIvyCastTurn, variant.firstIvyCastTurn),
        exactlyThreeManaIvyStalls = variant.ivyThreeManaStallTurns.size - control.ivyThreeManaStallTurns.size,
        primaryEngineTurn = turnDelta(control.engineTurn, variant.engineTurn),
        primaryEngineStateChange = stateChange(control.engineTurn, variant.engineTurn),
        anyInfiniteTurn = turnDelta(controlInfinite, variantInfinite),
        anyInfiniteStateChange = stateChange(controlInfinite, variantInfinite),
        comboBeforeCombatLethalChanged =
            "${control.comboAvailableBeforeOrdinaryLethal}->${variant.comboAvailableBeforeOrdinaryLethal}",
        actualWinTurn = turnDelta(control.actualWinningTurn, variant.actualWinningTurn),
        witnessRecursions = variant.witness.successfulRecursions.size - control.witness.successfulRecursions.size,
        nobleAvailableTurns = variant.nobleAvailableTurns.size - control.nobleAvailableTurns.size,
        noblePayoffMissingTurns = variant.noblePayoffMissingTurns.size - control.noblePayoffMissingTurns.size,
        combatDamage = variant.combatDamageDealt - control.combatDamageDealt,
        maximumCreatureBoard = variant.maximumCreatureBoard - control.maximumCreatureBoard,
    )
}

private fun renderProjectXOptimizationMarkdown(block: ProjectXOptimizationBlock): String = buildString {
    appendLine("# ${block.experiment}")
    appendLine()
    appendLine("Declared changes: ${block.declaredChanges.joinToString()}")
    appendLine("Each pair uses the same seed, starting-player assignment, mulligan policy, engine, and solitaire policy.")
    appendLine("Turn deltas are variant minus control; negative is earlier.")
    appendLine()
    appendLine("## Paired primary outcomes")
    appendLine()
    appendLine("| Pair | Seed | Ivy delta | 3-mana stalls | Primary state | Any-infinite state | Combo-before-lethal | Win delta | Witness returns | Noble availability |")
    appendLine("|---:|---:|---:|---:|---|---|---|---:|---:|---:|")
    block.pairs.forEach { pair ->
        val d = pair.delta
        appendLine("| ${pair.pair} | ${pair.seed} | ${d.firstIvyCastTurn ?: "N/A"} | ${d.exactlyThreeManaIvyStalls} | ${d.primaryEngineStateChange} | ${d.anyInfiniteStateChange} | ${d.comboBeforeCombatLethalChanged} | ${d.actualWinTurn ?: "N/A"} | ${d.witnessRecursions} | ${d.nobleAvailableTurns} |")
    }
    appendLine()
    appendLine("Control summary: ${block.controlSummary}")
    appendLine()
    appendLine("Variant summary: ${block.variantSummary}")
}

private fun projectXRegistry(): CardRegistry = CardRegistry().apply {
    register(PredefinedTokens.allTokens)
    MtgSetCatalog.all.forEach { set ->
        register(set.cards)
        register(set.basicLands)
    }
}

private fun previouslyUsedProjectXOrBatshitSeeds(current: Path): Set<Long> {
    val csvSeeds = Files.list(current.parent).use { paths ->
        paths.filter { path ->
            path != current && path.fileName.toString().contains("seed", ignoreCase = true) &&
                path.fileName.toString().endsWith(".csv")
        }.toList().flatMap { path ->
            Files.readAllLines(path).drop(1).filter(String::isNotBlank).map { it.substringAfterLast(',').toLong() }
        }.toSet()
    }
    val developmentRegressionAndSmokeSeeds = setOf(
        // Project X harness-development seed. It is permanently excluded from the final block.
        0x5058_4445_5600_0001L,
        // Batshit development/regression/smoke seeds that predate the frozen CSV vectors.
        0xBA75_0001L, 0xBA75_0002L, 0xBA75_0003L, 0xBA75_0004L, 0xBA75_0005L,
        0x033D_F483_8D71_94AL, 0x0B97_FCDF_E979_9C87L, 0x0ED3_B535_8E12_DED3L,
        0x0AB1_B251_D164_E979L, 0x0A48_B5B9_0FAF_A44CL, 0x0679_B6F9_EEF7_74BCL,
        0x0766_3C65_A87D_6DD5L, 0x03A2_548F_BBB2_DECL, 0x0F3B_7E43_F328_8345L,
        0x0906_6DF1_0204_FD56L,
    )
    return csvSeeds + developmentRegressionAndSmokeSeeds
}
