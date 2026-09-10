package com.wingedsheep.gym

import com.wingedsheep.ai.engine.EngineAiPlayerController
import com.wingedsheep.ai.llm.BottomCardsInfo
import com.wingedsheep.ai.llm.CardSummary
import com.wingedsheep.ai.llm.MulliganInfo
import com.wingedsheep.ai.solitaire.ProjectXDeck
import com.wingedsheep.ai.solitaire.ProjectXSolitaireAgent
import com.wingedsheep.ai.solitaire.ProjectXStateAnalyzer
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.legalactions.EnumerationMode
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.PlayerComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.core.Step
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
        val seedPath = Path.of("src", "test", "resources", "project-x-goldfish-v02-seeds.csv")
        val seeds = Files.readAllLines(seedPath).drop(1).filter(String::isNotBlank).map { line ->
            line.substringAfterLast(',').toLong()
        }
        seeds.size shouldBe 30
        seeds.distinct().size shouldBe 30
        seeds.none(previouslyUsedProjectXOrBatshitSeeds(seedPath)::contains).shouldBeTrue()

        val registry = projectXRegistry()
        val games = seeds.mapIndexed { index, seed -> runProjectXGoldfish(registry, seed, index + 1) }
        val block = ProjectXGoldfishBlock(
            deckVersion = "Project X v0.2",
            horizon = PROJECT_X_HORIZON,
            seeds = seeds,
            games = games,
            summary = summarizeProjectX(games),
        )

        val reportDir = Path.of("build", "reports", "project-x-goldfish")
        Files.createDirectories(reportDir)
        Files.writeString(
            reportDir.resolve("project-x-v02-goldfish-30.json"),
            Json { prettyPrint = true }.encodeToString(block),
        )
        Files.writeString(
            reportDir.resolve("project-x-v02-goldfish-30.md"),
            renderProjectXMarkdown(block),
        )
        println(renderProjectXMarkdown(block))
        games.forEach { it.auditErrors shouldBe emptyList() }
    }
})

@Serializable
internal data class ProjectXGoldfishBlock(
    val deckVersion: String,
    val horizon: Int,
    val seeds: List<Long>,
    val games: List<ProjectXGoldfishGame>,
    val summary: ProjectXGoldfishSummary,
)

@Serializable
internal data class ProjectXGoldfishGame(
    val game: Int,
    val seed: Long,
    val seedHex: String,
    val mulligans: Int,
    val keptHand: List<String>,
    val t1Development: List<String>,
    val firstMeaningfulDevelopmentTurn: Int?,
    val engineTurn: Int?,
    val hugeFeederTurn: Int?,
    val infiniteLifeTurn: Int?,
    val deterministicLethalTurn: Int?,
    val lethalMechanism: String?,
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
    val birchloreManaContribution: List<String>,
    val nettleUntapContribution: List<String>,
    val quirionManaContribution: List<String>,
    val windingWay: List<String>,
    val leadTheStampede: List<String>,
    val colorStrandedCards: List<String>,
    val taplandTempoEvents: List<String>,
    val exactlyOneRoleMissingTurns: List<Int>,
    val functionalWithoutCombo: String,
    val secondaryWitnessLoopTurn: Int?,
    val actions: Int,
    val stopReason: String,
    val auditErrors: List<String>,
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
internal data class ProjectXGoldfishSummary(
    val engineByT4: Int,
    val engineByT5: Int,
    val engineByT6: Int,
    val lethalByT4: Int,
    val lethalByT5: Int,
    val lethalByT6: Int,
    val medianEngineTurn: Double?,
    val medianLethalTurn: Double?,
    val infiniteLifeGames: Int,
    val hugeFeederWithoutImmediateLethalGames: Int,
    val mulliganGames: Int,
    val totalMulligans: Int,
    val functionalWithoutComboGames: Int,
    val exactlyOneRoleMissingGames: Int,
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

internal fun runProjectXGoldfish(registry: CardRegistry, seed: Long, gameNumber: Int): ProjectXGoldfishGame {
    val processor = ActionProcessor(registry)
    val init = GameInitializer(registry).initializeGame(
        GameConfig(
            players = listOf(
                PlayerConfig("Project X", ProjectXDeck.V02),
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
    val environment = GameEnvironment.create(registry).also { it.restore(state, init.playerIds) }
    val agent = ProjectXSolitaireAgent(registry, projectId)
    val blank = com.wingedsheep.ai.engine.AIPlayer.create(registry, blankId)
    val analyzer = agent.analyzer
    val telemetryEnumerator = LegalActionEnumerator.create(registry)
    val t1 = mutableListOf<String>()
    val heraldTargets = mutableListOf<String>()
    val witnessEvents = mutableListOf<String>()
    val birchlore = mutableListOf<String>()
    val nettle = mutableListOf<String>()
    val quirion = mutableListOf<String>()
    val winding = mutableListOf<String>()
    val lead = mutableListOf<String>()
    val stranded = linkedSetOf<String>()
    val taplands = mutableListOf<String>()
    val oneMissingTurns = sortedSetOf<Int>()
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
    var selection: SelectionTelemetry? = null
    var pendingWitnessTarget: Pair<Int, String>? = null
    var pendingHeraldSearch: Pair<Int, List<String>>? = null
    var firstMeaningful: Int? = null
    var engineTurn: Int? = null
    var hugeTurn: Int? = null
    var lifeTurn: Int? = null
    var lethalTurn: Int? = null
    var lethalMechanism: String? = null
    var secondaryTurn: Int? = null
    var actions = 0
    var lastEngineTurn = -1
    var actionsThisEngineTurn = 0
    var stopReason = "T${PROJECT_X_HORIZON}_HORIZON"
    var winner: String? = null
    var gameOverTurn: Int? = null
    var terminalMechanism: String? = null

    fun projectTurn(gameState: GameState): Int = (gameState.turnNumber + 1) / 2

    fun observe(gameState: GameState) {
        val turn = projectTurn(gameState)
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
                val searchable = role in analyzer.libraryNames(gameState, projectId)
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
        val outcome = agent.outcome(gameState)
        if (outcome.completeInfiniteEngine && engineTurn == null) engineTurn = turn
        if (outcome.arbitrarilyLargeCarrionFeeder && hugeTurn == null) hugeTurn = turn
        if (outcome.arbitraryLife && lifeTurn == null) lifeTurn = turn
        if (outcome.secondaryWitnessLoop && secondaryTurn == null) secondaryTurn = turn
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
                        val chosen = (response as TargetsResponse).selectedTargets.values.flatten().firstOrNull()?.let(::name)
                        if (chosen != null) pendingWitnessTarget = turn to chosen
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
            if (witness != null && event.ownerId == projectId && event.entityName == witness.second &&
                event.fromZone == Zone.GRAVEYARD && event.toZone == Zone.HAND
            ) {
                witnessEvents += "${event.entityName}@T${witness.first}"
                pendingWitnessTarget = null
            }
        }
        events.filterIsInstance<ResolvedEvent>().forEach { event ->
            val heraldSearch = pendingHeraldSearch
            if (heraldSearch != null && event.name.contains(ProjectXStateAnalyzer.WIREWOOD_HERALD)) {
                heraldTriggerResolved += "T${heraldSearch.first}:${event.name}"
                heraldTargets += heraldSearch.second.map { "$it@T${heraldSearch.first}" }
                pendingHeraldSearch = null
            }
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

    val battlefieldCreatures = state.controlledBattlefield(projectId).count { id ->
        state.getEntity(id)?.get<CardComponent>()?.isCreature == true
    }
    val functional = when {
        engineTurn != null -> "PRIMARY_COMBO_ASSEMBLED"
        secondaryTurn != null -> "FUNCTIONAL_SECONDARY_LOOP"
        battlefieldCreatures >= 3 || winding.isNotEmpty() || lead.isNotEmpty() ||
            heraldTargets.isNotEmpty() || witnessEvents.isNotEmpty() -> "FUNCTIONAL_WITHOUT_PRIMARY_COMBO"
        else -> "NONFUNCTIONAL_WITHOUT_COMBO"
    }

    return ProjectXGoldfishGame(
        game = gameNumber,
        seed = seed,
        seedHex = "0x${seed.toULong().toString(16).uppercase()}",
        mulligans = mulligans,
        keptHand = keptHand,
        t1Development = t1,
        firstMeaningfulDevelopmentTurn = firstMeaningful,
        engineTurn = engineTurn,
        hugeFeederTurn = hugeTurn,
        infiniteLifeTurn = lifeTurn,
        deterministicLethalTurn = lethalTurn,
        lethalMechanism = lethalMechanism,
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
        birchloreManaContribution = birchlore,
        nettleUntapContribution = nettle,
        quirionManaContribution = quirion,
        windingWay = winding,
        leadTheStampede = lead,
        colorStrandedCards = stranded.toList(),
        taplandTempoEvents = taplands,
        exactlyOneRoleMissingTurns = oneMissingTurns.toList(),
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

private fun castAttemptBeforeExecution(
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
    val castOffer = full.firstOrNull { (it.action as? CastSpell)?.cardId == action.cardId }
    val autoTapPreview = castOffer?.autoTapPreview
    val quirionLines = full.filter { legal ->
        val activate = legal.action as? ActivateAbility ?: return@filter false
        analyzer.name(state, activate.sourceId) == ProjectXStateAnalyzer.QUIRION_RANGER && legal.affordable
    }.map { it.description }
    val proposed = when {
        autoTapPreview != null -> "AUTO_TAP:${autoTapPreview.map(name)}"
        action.paymentStrategy is PaymentStrategy.FromPool -> "FROM_POOL"
        action.paymentStrategy is PaymentStrategy.Explicit -> "EXPLICIT:${action.paymentStrategy}"
        else -> "AUTO_PAY:NO_ORDINARY_SOURCE_PLAN"
    }
    val legality = when {
        castOffer == null -> "NO_MATCHING_ENUMERATED_CAST"
        !castOffer.affordable -> "ENUMERATOR_UNAFFORDABLE"
        castOffer.autoTapPreview != null -> "AFFORDABLE_WITH_AUTO_TAP_PREVIEW"
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

private val PROJECT_X_MANA_REQUIREMENTS = mapOf(
    "Carrion Feeder" to (1 to 'B'), "Falkenrath Noble" to (4 to 'B'),
    "Safehold Elite" to (2 to 'G'), "Ivy Lane Denizen" to (4 to 'G'),
    "Wirewood Herald" to (2 to 'G'), "Evolution Witness" to (3 to 'G'),
    "Nettle Sentinel" to (1 to 'G'), "Birchlore Rangers" to (1 to 'G'),
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
    return ProjectXGoldfishSummary(
        engineByT4 = countBy(4, ProjectXGoldfishGame::engineTurn),
        engineByT5 = countBy(5, ProjectXGoldfishGame::engineTurn),
        engineByT6 = countBy(6, ProjectXGoldfishGame::engineTurn),
        lethalByT4 = countBy(4, ProjectXGoldfishGame::deterministicLethalTurn),
        lethalByT5 = countBy(5, ProjectXGoldfishGame::deterministicLethalTurn),
        lethalByT6 = countBy(6, ProjectXGoldfishGame::deterministicLethalTurn),
        medianEngineTurn = median(games.mapNotNull(ProjectXGoldfishGame::engineTurn)),
        medianLethalTurn = median(games.mapNotNull(ProjectXGoldfishGame::deterministicLethalTurn)),
        infiniteLifeGames = games.count { it.infiniteLifeTurn != null },
        hugeFeederWithoutImmediateLethalGames = games.count {
            it.hugeFeederTurn != null && (it.deterministicLethalTurn == null || it.hugeFeederTurn < it.deterministicLethalTurn)
        },
        mulliganGames = games.count { it.mulligans > 0 },
        totalMulligans = games.sumOf(ProjectXGoldfishGame::mulligans),
        functionalWithoutComboGames = games.count { it.functionalWithoutCombo.startsWith("FUNCTIONAL_WITHOUT") || it.functionalWithoutCombo == "FUNCTIONAL_SECONDARY_LOOP" },
        exactlyOneRoleMissingGames = games.count { it.exactlyOneRoleMissingTurns.isNotEmpty() },
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
    appendLine("# Project X v0.2 — frozen 30-game deterministic goldfish block")
    appendLine()
    appendLine("Horizon: T${block.horizon}; Project X is on the play; opponent is a no-interaction 60-Plains goldfish.")
    appendLine()
    appendLine("## Summary")
    appendLine()
    appendLine("- Engine online by T4 / T5 / T6: ${s.engineByT4}/30, ${s.engineByT5}/30, ${s.engineByT6}/30")
    appendLine("- Deterministic lethal by T4 / T5 / T6: ${s.lethalByT4}/30, ${s.lethalByT5}/30, ${s.lethalByT6}/30")
    appendLine("- Median engine turn: ${s.medianEngineTurn ?: "not reached"}")
    appendLine("- Median deterministic lethal turn: ${s.medianLethalTurn ?: "not reached"}")
    appendLine("- Infinite life: ${s.infiniteLifeGames}/30")
    appendLine("- Huge Feeder before/no immediate lethal: ${s.hugeFeederWithoutImmediateLethalGames}/30")
    appendLine("- Mulligan games / total mulligans: ${s.mulliganGames}/30 / ${s.totalMulligans}")
    appendLine("- Functional without primary combo: ${s.functionalWithoutComboGames}/30")
    appendLine("- At least one exactly-one-role-missing turn: ${s.exactlyOneRoleMissingGames}/30")
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
        appendLine("- Mulligans: ${game.mulligans}; kept: ${game.keptHand}")
        appendLine("- T1: ${game.t1Development.ifEmpty { listOf("none") }}; first meaningful: ${game.firstMeaningfulDevelopmentTurn?.let { "T$it" } ?: "none"}")
        appendLine("- Engine / huge Feeder / infinite life / lethal: ${game.engineTurn?.let { "T$it" } ?: "—"} / ${game.hugeFeederTurn?.let { "T$it" } ?: "—"} / ${game.infiniteLifeTurn?.let { "T$it" } ?: "—"} / ${game.deterministicLethalTurn?.let { "T$it" } ?: "—"} (${game.lethalMechanism ?: "none"})")
        appendLine("- Actual terminal: winner=${game.winner ?: "—"}; turn=${game.gameOverTurn?.let { "T$it" } ?: "—"}; mechanism=${game.terminalMechanism ?: "—"}")
        appendLine("- Herald: drawn=${game.herald.drawn}; cast=${game.herald.cast}; battlefield=${game.herald.battlefield}; died=${game.herald.died}; trigger-created=${game.herald.tutorTriggerCreated}; trigger-resolved=${game.herald.tutorTriggerResolved}; targets=${game.herald.tutorTargets}; one-missing=${game.herald.oneRoleMissingAvailability}")
        appendLine("- Witness: ${game.witnessRecursionEvents.ifEmpty { listOf("none") }}")
        appendLine("- Mana — Birchlore: ${game.birchloreManaContribution.ifEmpty { listOf("none") }}; Nettle: ${game.nettleUntapContribution.ifEmpty { listOf("none") }}; Quirion: ${game.quirionManaContribution.ifEmpty { listOf("none") }}")
        appendLine("- Winding Way: ${game.windingWay.ifEmpty { listOf("none") }}")
        appendLine("- Lead the Stampede: ${game.leadTheStampede.ifEmpty { listOf("none") }}")
        appendLine("- Color stranded: ${game.colorStrandedCards.ifEmpty { listOf("none") }}; taplands: ${game.taplandTempoEvents.ifEmpty { listOf("none") }}")
        appendLine("- Mana constraints: ${game.manaConstraints.ifEmpty { listOf("none") }}")
        appendLine("- Cast attempts: ${game.castAttempts.ifEmpty { listOf("none") }}")
        appendLine("- Exactly one role missing: ${game.exactlyOneRoleMissingTurns}; functional classification: ${game.functionalWithoutCombo}")
        appendLine("- Secondary Witness loop: ${game.secondaryWitnessLoopTurn?.let { "T$it" } ?: "—"}; stop: ${game.stopReason}; actions: ${game.actions}; audit: ${game.auditErrors.ifEmpty { listOf("clean") }}")
        appendLine()
    }
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
