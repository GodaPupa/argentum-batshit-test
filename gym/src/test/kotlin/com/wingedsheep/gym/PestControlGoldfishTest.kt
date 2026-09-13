package com.wingedsheep.gym

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.ai.engine.EngineAiPlayerController
import com.wingedsheep.ai.llm.BottomCardsInfo
import com.wingedsheep.ai.llm.CardSummary
import com.wingedsheep.ai.llm.MulliganInfo
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.PlayerComponent
import com.wingedsheep.engine.state.components.player.LifeGainedThisTurnComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.engine.state.components.stack.ActivatedAbilityOnStackComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.gym.telemetry.ActionableManaBottleneck
import com.wingedsheep.gym.telemetry.ActionableManaBottleneckTracker
import com.wingedsheep.gym.telemetry.ManaConstraint
import com.wingedsheep.gym.telemetry.SacrificeManaTrace
import com.wingedsheep.gym.telemetry.SacrificeManaUse
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

private const val PEST_GOLDFISH_ENV = "PEST_CONTROL_GOLDFISH_SAMPLE_1_REGRESSION"
private const val PEST_GAME_25_ENV = "PEST_CONTROL_GOLDFISH_GAME_25_DIAGNOSTIC"
private const val PEST_GOLDFISH_HORIZON = 20

/** Pest Control-owned development goldfish. It is opt-in and never runs in ordinary CI. */
class PestControlGoldfishTest : FunSpec({
    test("frozen Pest Control v1.0 and Sample 1 seed vector are exact") {
        pestControlDeck().cards.groupingBy { it }.eachCount() shouldBe PEST_CONTROL_V10
        val seeds = readPestSeeds()
        seeds.size shouldBe 30
        seeds.distinct().size shouldBe 30
    }

    test("reproduce rejected Pest Control Game 25 Scion provenance").config(
        enabled = System.getenv(PEST_GAME_25_ENV) == "true",
        timeout = 10.minutes,
    ) {
        val game = runPestGoldfish(pestRegistry(), readPestSeeds()[24], 25)
        println(Json { prettyPrint = true }.encodeToString(game))
    }

    test("Pest Control v1.0 rejected Sample 1 regression replay").config(
        enabled = System.getenv(PEST_GOLDFISH_ENV) == "true",
        timeout = 60.minutes,
    ) {
        val seeds = readPestSeeds()
        val registry = pestRegistry()
        val games = seeds.mapIndexed { index, seed ->
            runPestGoldfish(registry, seed, index + 1)
        }
        val block = PestGoldfishBlock(
            deckVersion = "Pest Control v1.0",
            agentProfile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING.id,
            horizon = PEST_GOLDFISH_HORIZON,
            seeds = seeds,
            games = games,
            summary = summarizePest(games),
        )
        val reportDir = Path.of("build", "reports", "pest-control-goldfish")
        Files.createDirectories(reportDir)
        Files.writeString(
            reportDir.resolve("pest-control-v10-goldfish-sample-1-regression-replay.json"),
            Json { prettyPrint = true }.encodeToString(block),
        )
        val markdown = renderPestMarkdown(block)
        Files.writeString(reportDir.resolve("pest-control-v10-goldfish-sample-1-regression-replay.md"), markdown)
        println(markdown)
        games.flatMap(PestGoldfishGame::auditErrors) shouldBe emptyList()
    }
})

private val PEST_CONTROL_V10 = linkedMapOf(
    "Essence Warden" to 4,
    "Carrier Thrall" to 4,
    "Blood Researcher" to 4,
    "Pest Mascot" to 4,
    "Fierce Witchstalker" to 4,
    "Generous Ent" to 3,
    "Follow the Lumarets" to 4,
    "Weather the Storm" to 4,
    "Cast Down" to 4,
    "Bone Shards" to 2,
    "Chainer's Edict" to 2,
    "Forest" to 10,
    "Swamp" to 7,
    "Jungle Hollow" to 4,
)

private fun pestControlDeck(): Deck = Deck.of(*PEST_CONTROL_V10.map { it.key to it.value }.toTypedArray())

private fun readPestSeeds(): List<Long> = Files.readAllLines(
    Path.of("src", "test", "resources", "pest-control-v10-goldfish-sample-1-seeds.csv")
).drop(1).filter(String::isNotBlank).map { it.substringAfterLast(',').toLong() }

private fun pestRegistry(): CardRegistry = CardRegistry().apply {
    register(PredefinedTokens.allTokens)
    MtgSetCatalog.all.forEach { set ->
        register(set.cards)
        register(set.basicLands)
    }
}

@Serializable
internal data class PestGoldfishBlock(
    val deckVersion: String,
    val agentProfile: String,
    val horizon: Int,
    val seeds: List<Long>,
    val games: List<PestGoldfishGame>,
    val summary: PestGoldfishSummary,
)

@Serializable
internal data class PestOpeningAccess(
    val keptHand: List<String>,
    val lands: List<String>,
    val green: Boolean,
    val black: Boolean,
    val untappedGreen: Boolean,
    val untappedBlack: Boolean,
    val entForestcyclingAvailable: Boolean,
)

@Serializable
internal data class PestWeatherCast(
    val turn: Int,
    val stormCount: Int,
    val expectedCopies: Int,
    val observedCopies: Int,
)

@Serializable
internal data class PestLifeEvent(
    val turn: Int,
    val amount: Int,
    val source: String,
    val researcherPresent: Boolean,
    val mascotPresent: Boolean,
)

@Serializable
internal data class PestFollowCast(
    val turn: Int,
    val mode: String,
)

@Serializable
internal data class PestCoexistence(
    val wardenResearcher: Boolean,
    val wardenMascot: Boolean,
    val researcherMascot: Boolean,
    val wardenResearcherMascot: Boolean,
    val payoffWhenWeatherResolved: Boolean,
    val payoffDuringMultipleCreatureEntryLifeEvents: Boolean,
)

@Serializable
internal data class PestGoldfishGame(
    val game: Int,
    val seed: Long,
    val seedHex: String,
    val mulligans: Int,
    val opening: PestOpeningAccess,
    val t1Development: List<String>,
    val firstMeaningfulPermanentTurn: Int?,
    val actualWinningTurn: Int?,
    val terminalMechanism: String?,
    val essenceWardenCastTurns: List<Int>,
    val bloodResearcherCastTurns: List<Int>,
    val pestMascotCastTurns: List<Int>,
    val carrierThrallCastTurns: List<Int>,
    val carrierThrallDeaths: Int,
    val scionsCreated: Int,
    val scionsSacrificedForMana: Int,
    val scionFundedSpells: List<String>,
    val scionManaUses: List<SacrificeManaUse>,
    val fierceWitchstalkerCastTurns: List<Int>,
    val generousEntCycleTurns: List<Int>,
    val generousEntCastTurns: List<Int>,
    val followCasts: List<PestFollowCast>,
    val weatherCasts: List<PestWeatherCast>,
    val lifeEvents: List<PestLifeEvent>,
    val totalLifeGained: Int,
    val researcherCounterTriggers: Int,
    val researcherCountersAdded: Int,
    val mascotCounterTriggers: Int,
    val mascotCountersAdded: Int,
    val maximumResearcherPower: Int?,
    val maximumResearcherToughness: Int?,
    val maximumMascotPower: Int?,
    val maximumMascotToughness: Int?,
    val largestCreatureBattlefield: Int,
    val largestPermanentBattlefield: Int,
    val solitaireStrandedInteraction: List<String>,
    val genuineManaBottlenecks: List<ActionableManaBottleneck>,
    val jungleHollowTempoEvents: List<String>,
    val coexistence: PestCoexistence,
    val functionalState: String,
    val actions: Int,
    val stopReason: String,
    val auditErrors: List<String>,
)

@Serializable
internal data class PestGoldfishSummary(
    val games: Int,
    val mulliganGames: Int,
    val totalMulligans: Int,
    val mulliganRate: Double,
    val meaningfulDevelopmentByT1: Int,
    val meaningfulDevelopmentByT2: Int,
    val meaningfulDevelopmentByT3: Int,
    val medianFirstWarden: Double?,
    val medianFirstPayoff: Double?,
    val medianActualWinningTurn: Double?,
    val winsByT4: Int,
    val winsByT5: Int,
    val winsByT6: Int,
    val winsByT7: Int,
    val averageLifeEvents: Double,
    val averageLifeGained: Double,
    val weatherStormCountDistribution: Map<Int, Int>,
    val researcherMaximumSizeDistribution: Map<String, Int>,
    val mascotMaximumSizeDistribution: Map<String, Int>,
    val enginePayoffCoexistenceGames: Int,
    val enginePayoffCoexistenceRate: Double,
    val carrierDeaths: Int,
    val scionsCreated: Int,
    val scionsSacrificedForMana: Int,
    val scionFundedSpells: Map<String, Int>,
    val entCycles: Int,
    val entCreatureCasts: Int,
    val entCyclingRate: Double?,
    val followNormalCasts: Int,
    val followEnhancedCasts: Int,
    val followEnhancedRate: Double?,
    val manaBottleneckGames: Int,
    val manaBottleneckObservations: Int,
    val manaConstraintDistribution: Map<String, Int>,
    val hollowTempoGames: Int,
    val hollowTempoEvents: Int,
    val solitaireInteractionConstrainedGames: Int,
    val strandedInteractionObservations: Int,
    val functionalStateDistribution: Map<String, Int>,
)

private data class MutableWeather(
    val turn: Int,
    val stormCount: Int,
    val expectedCopies: Int,
    var observedCopies: Int = 0,
)

internal fun runPestGoldfish(registry: CardRegistry, seed: Long, gameNumber: Int): PestGoldfishGame {
    val processor = ActionProcessor(registry)
    val init = GameInitializer(registry).initializeGame(
        GameConfig(
            players = listOf(
                PlayerConfig("Pest Control", pestControlDeck()),
                PlayerConfig("Blank Goldfish", Deck.of("Plains" to 60)),
            ),
            skipMulligans = false,
            useHandSmoother = false,
            startingPlayerIndex = 0,
            seed = seed,
        )
    )
    val pestId = init.playerIds[0]
    val blankId = init.playerIds[1]
    var state = init.state

    fun name(gameState: GameState, id: EntityId): String =
        gameState.getEntity(id)?.get<CardComponent>()?.name ?: id.toString()

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

    val mulliganController = EngineAiPlayerController(registry, pestId, gameStateProvider = { state })
    for (playerId in state.turnOrder) {
        while (true) {
            val mulligan = state.getEntity(playerId)!!.get<MulliganStateComponent>()!!
            val keep = if (playerId == pestId) {
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
            val processed = processor.process(state, if (keep) KeepHand(playerId) else TakeMulligan(playerId)).result
            check(processed.error == null) { "Mulligan rejected: ${processed.error}" }
            state = processed.state
            if (keep) break
        }
    }
    val mulligans = state.getEntity(pestId)!!.get<MulliganStateComponent>()!!.mulligansTaken
    for (playerId in state.turnOrder) {
        val mulligan = state.getEntity(playerId)!!.get<MulliganStateComponent>()!!
        if (mulligan.cardsToBottom <= 0) continue
        val bottom = if (playerId == pestId) {
            mulliganController.chooseBottomCards(
                BottomCardsInfo(state.getHand(playerId), mulligan.cardsToBottom, summaries(playerId))
            )
        } else state.getHand(playerId).take(mulligan.cardsToBottom)
        val processed = processor.process(state, BottomCards(playerId, bottom)).result
        check(processed.error == null) { "Bottom cards rejected: ${processed.error}" }
        state = processed.state
    }

    val keptHand = state.getHand(pestId).map { name(state, it) }
    val openingLands = keptHand.filter { it in setOf("Forest", "Swamp", "Jungle Hollow") }
    val opening = PestOpeningAccess(
        keptHand = keptHand,
        lands = openingLands,
        green = openingLands.any { it == "Forest" || it == "Jungle Hollow" },
        black = openingLands.any { it == "Swamp" || it == "Jungle Hollow" },
        untappedGreen = "Forest" in openingLands,
        untappedBlack = "Swamp" in openingLands,
        entForestcyclingAvailable = "Generous Ent" in keptHand,
    )

    val environment = GameEnvironment.create(registry).also { it.restore(state, init.playerIds) }
    val pest = AIPlayer.create(registry, pestId, AiProfile.PRODUCTION_CANDIDATE_EXPIRING)
    val blank = AIPlayer.create(registry, blankId, AiProfile.PRODUCTION_CANDIDATE_EXPIRING)
    val bottleneckTracker = ActionableManaBottleneckTracker(registry)
    val sacrificeManaTrace = SacrificeManaTrace()
    val t1 = mutableListOf<String>()
    val wardenCasts = mutableListOf<Int>()
    val researcherCasts = mutableListOf<Int>()
    val mascotCasts = mutableListOf<Int>()
    val thrallCasts = mutableListOf<Int>()
    val witchCasts = mutableListOf<Int>()
    val entCycles = mutableListOf<Int>()
    val entCasts = mutableListOf<Int>()
    val follows = mutableListOf<PestFollowCast>()
    val weathers = mutableListOf<MutableWeather>()
    val lifeEvents = mutableListOf<PestLifeEvent>()
    val stranded = linkedSetOf<String>()
    val bottlenecks = mutableListOf<ActionableManaBottleneck>()
    val hollows = mutableListOf<String>()
    val audit = mutableListOf<String>()
    var thrallDeaths = 0
    var scionsCreated = 0
    var scionActivations = 0
    var scionSacrifices = 0
    var scionManaEvents = 0
    var researcherTriggers = 0
    var researcherCounters = 0
    var mascotTriggers = 0
    var mascotCounters = 0
    var maxResearcherPower: Int? = null
    var maxResearcherToughness: Int? = null
    var maxMascotPower: Int? = null
    var maxMascotToughness: Int? = null
    var maxCreatures = 0
    var maxPermanents = 0
    var firstPermanent: Int? = null
    var wardenResearcher = false
    var wardenMascot = false
    var researcherMascot = false
    var triple = false
    var payoffAtWeather = false
    var entryLifeEventsWithPayoff = 0
    var actions = 0
    var lastEngineTurn = -1
    var actionsThisEngineTurn = 0
    var stopReason = "T${PEST_GOLDFISH_HORIZON}_HORIZON"
    var winningTurn: Int? = null
    var terminal: String? = null

    fun pestTurn(gameState: GameState): Int = (gameState.turnNumber + 1) / 2

    fun battlefieldNames(gameState: GameState): List<String> =
        gameState.controlledBattlefield(pestId).map { name(gameState, it) }

    fun stackSourceName(gameState: GameState): String? {
        val top = gameState.stack.lastOrNull()?.let(gameState::getEntity) ?: return null
        return top.get<CardComponent>()?.name
            ?: top.get<ActivatedAbilityOnStackComponent>()?.sourceName
            ?: top.get<TriggeredAbilityOnStackComponent>()?.sourceName
    }

    fun observe(gameState: GameState) {
        val names = battlefieldNames(gameState)
        val hasWarden = "Essence Warden" in names
        val hasResearcher = "Blood Researcher" in names
        val hasMascot = "Pest Mascot" in names
        wardenResearcher = wardenResearcher || (hasWarden && hasResearcher)
        wardenMascot = wardenMascot || (hasWarden && hasMascot)
        researcherMascot = researcherMascot || (hasResearcher && hasMascot)
        triple = triple || (hasWarden && hasResearcher && hasMascot)
        val permanents = gameState.controlledBattlefield(pestId)
        maxPermanents = maxOf(maxPermanents, permanents.size)
        maxCreatures = maxOf(maxCreatures, permanents.count { gameState.getEntity(it)?.get<CardComponent>()?.isCreature == true })
        permanents.forEach { id ->
            when (name(gameState, id)) {
                "Blood Researcher" -> {
                    maxResearcherPower = maxOf(maxResearcherPower ?: Int.MIN_VALUE, gameState.projectedState.getPower(id) ?: 0)
                    maxResearcherToughness = maxOf(maxResearcherToughness ?: Int.MIN_VALUE, gameState.projectedState.getToughness(id) ?: 0)
                }
                "Pest Mascot" -> {
                    maxMascotPower = maxOf(maxMascotPower ?: Int.MIN_VALUE, gameState.projectedState.getPower(id) ?: 0)
                    maxMascotToughness = maxOf(maxMascotToughness ?: Int.MIN_VALUE, gameState.projectedState.getToughness(id) ?: 0)
                }
            }
        }
        if (gameState.priorityPlayerId == pestId && gameState.pendingDecision == null && gameState.stack.isEmpty() &&
            gameState.step in setOf(Step.PRECOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
        ) {
            val turn = pestTurn(gameState)
            val blankHasCreature = gameState.controlledBattlefield(blankId).any {
                gameState.getEntity(it)?.get<CardComponent>()?.isCreature == true
            }
            if (!blankHasCreature) {
                state.getHand(pestId).map { name(gameState, it) }.groupingBy { it }.eachCount()
                    .filterKeys { it in SOLITAIRE_INTERACTION }
                    .forEach { (card, count) -> stranded += "$card x$count@T$turn:NO_OPPONENT_CREATURE" }
            }
            bottlenecks += bottleneckTracker.observe(gameState, pestId, turn)
        }
    }

    observe(state)
    while (!state.gameOver && pestTurn(state) <= PEST_GOLDFISH_HORIZON && actions < 3_000) {
        if (state.turnNumber != lastEngineTurn) {
            lastEngineTurn = state.turnNumber
            actionsThisEngineTurn = 0
        }
        actionsThisEngineTurn++
        if (actionsThisEngineTurn > 250) {
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
        val turn = pestTurn(state)
        val action = if (decision != null) {
            SubmitDecision(acting, if (acting == pestId) pest.respondToDecision(state, decision) else blank.respondToDecision(state, decision))
        } else if (acting == pestId) pest.chooseAction(state) else blank.chooseAction(state)
        val actionName = when (action) {
            is CastSpell -> name(state, action.cardId)
            is PlayLand -> name(state, action.cardId)
            is ActivateAbility -> name(state, action.sourceId)
            is TypecycleCard -> name(state, action.cardId)
            is CycleCard -> name(state, action.cardId)
            else -> null
        }
        if (acting == pestId && decision == null && action !is PassPriority) {
            val description = when (action) {
                is CastSpell -> "cast $actionName"
                is PlayLand -> "play $actionName"
                is ActivateAbility -> "activate $actionName"
                is TypecycleCard -> "typecycle $actionName"
                is CycleCard -> "cycle $actionName"
                else -> action::class.simpleName ?: "action"
            }
            if (turn == 1) t1 += description
            if (action is CastSpell) when (actionName) {
                "Essence Warden" -> wardenCasts += turn
                "Blood Researcher" -> researcherCasts += turn
                "Pest Mascot" -> mascotCasts += turn
                "Carrier Thrall" -> thrallCasts += turn
                "Fierce Witchstalker" -> witchCasts += turn
                "Generous Ent" -> entCasts += turn
                "Follow the Lumarets" -> follows += PestFollowCast(
                    turn,
                    if (state.getEntity(pestId)?.has<LifeGainedThisTurnComponent>() == true) "ENHANCED" else "NORMAL",
                )
                "Weather the Storm" -> weathers += MutableWeather(turn, state.spellsCastThisTurn, state.spellsCastThisTurn)
                "Chainer's Edict" -> if (state.controlledBattlefield(blankId).none {
                        state.getEntity(it)?.get<CardComponent>()?.isCreature == true
                    }) audit += "agent cast Chainer's Edict into an empty opposing battlefield on T$turn"
            }
            if ((action is TypecycleCard || action is CycleCard) && actionName == "Generous Ent") entCycles += turn
        }
        val resolvingSource = stackSourceName(state)
        val beforeBlankLife = state.lifeTotal(blankId)
        val result = environment.stepExactlyOne(action)
        val step = when (result) {
            is ExactlyOneSubmissionResult.Applied -> result.step
            is ExactlyOneSubmissionResult.Rejected -> {
                audit += "illegal action ${action::class.simpleName} ${actionName ?: "unknown"}: ${result.reason}"
                stopReason = "ILLEGAL_ACTION"
                break
            }
        }
        actions++
        val events = step.events
        sacrificeManaTrace.observe(state, step.state, events, pestId, turn)
        events.filterIsInstance<AbilityActivatedEvent>()
            .filter { it.controllerId == pestId && it.sourceName == "Eldrazi Scion" && it.isManaAbility }
            .forEach { event ->
                scionActivations++
            }
        events.filterIsInstance<SpellCopiedEvent>().filter { it.controllerId == pestId && it.cardName == "Weather the Storm" }
            .forEach {
                weathers.firstOrNull { weather -> weather.observedCopies < weather.expectedCopies }?.observedCopies =
                    (weathers.firstOrNull { weather -> weather.observedCopies < weather.expectedCopies }?.observedCopies ?: 0) + 1
            }
        events.filterIsInstance<LifeChangedEvent>()
            .filter { it.playerId == pestId && it.reason == LifeChangeReason.LIFE_GAIN }
            .forEach { event ->
                val names = battlefieldNames(step.state)
                val researcherPresent = "Blood Researcher" in names
                val mascotPresent = "Pest Mascot" in names
                lifeEvents += PestLifeEvent(turn, event.newLife - event.oldLife, resolvingSource ?: "UNKNOWN", researcherPresent, mascotPresent)
                if (resolvingSource == "Weather the Storm" && (researcherPresent || mascotPresent)) payoffAtWeather = true
                if (resolvingSource in setOf("Essence Warden", "Bogwater Lumaret") && (researcherPresent || mascotPresent)) {
                    entryLifeEventsWithPayoff++
                }
            }
        events.filterIsInstance<AbilityTriggeredEvent>().filter { it.controllerId == pestId }.forEach { event ->
            when (event.sourceName) {
                "Blood Researcher" -> researcherTriggers++
                "Pest Mascot" -> mascotTriggers++
            }
        }
        events.filterIsInstance<CountersAddedEvent>().forEach { event ->
            if (event.counterType != "+1/+1" && event.counterType != "PLUS_ONE_PLUS_ONE") return@forEach
            when (event.entityName) {
                "Blood Researcher" -> researcherCounters += event.amount
                "Pest Mascot" -> mascotCounters += event.amount
            }
        }
        events.filterIsInstance<ManaAddedEvent>()
            .filter { it.playerId == pestId && it.sourceName == "Eldrazi Scion" && it.colorless == 1 }
            .forEach { scionManaEvents++ }
        events.filterIsInstance<ZoneChangeEvent>().forEach { event ->
            if (event.ownerId != pestId) return@forEach
            if (event.toZone == Zone.BATTLEFIELD && event.entityName == "Eldrazi Scion") scionsCreated++
            if (event.fromZone == Zone.BATTLEFIELD && event.toZone == Zone.GRAVEYARD && event.entityName == "Carrier Thrall") thrallDeaths++
            if (event.fromZone == Zone.BATTLEFIELD && event.entityName == "Eldrazi Scion" && event.wasSacrificed) scionSacrifices++
            if (event.toZone == Zone.BATTLEFIELD) {
                val card = step.state.getEntity(event.entityId)?.get<CardComponent>()
                if (card?.isPermanent == true && !card.isLand && firstPermanent == null) firstPermanent = turn
            }
        }
        if (acting == pestId && action is PlayLand && actionName == "Jungle Hollow") {
            val tapped = step.state.getEntity(action.cardId)?.has<TappedComponent>() == true
            hollows += "Jungle Hollow@T$turn:${if (tapped) "ENTERED_TAPPED" else "UNTAPPED"}"
        }
        events.filterIsInstance<GameEndedEvent>().lastOrNull()?.let { ended ->
            winningTurn = turn.takeIf { ended.winnerId == pestId }
            terminal = when {
                ended.winnerId == pestId && events.filterIsInstance<DamageDealtEvent>().any {
                    it.targetId == blankId && it.isCombatDamage && it.amount > 0
                } -> "COMBAT_LETHAL"
                ended.winnerId == pestId && beforeBlankLife > 0 && step.state.lifeTotal(blankId) <= 0 -> "ABILITY_OR_TRIGGER_LETHAL"
                ended.winnerId == pestId -> "OTHER_PEST_WIN:${ended.reason}"
                ended.winnerId == blankId -> "BLANK_WIN:${ended.reason}"
                else -> "DRAW:${ended.reason}"
            }
        }
        state = step.state
        observe(state)
    }

    if (state.gameOver) stopReason = "ENGINE_GAME_OVER"
    else if (actions >= 3_000) {
        stopReason = "MAX_ACTIONS"
        audit += "exceeded action cap"
    }
    weathers.forEachIndexed { index, weather ->
        if (weather.observedCopies != weather.expectedCopies) {
            audit += "Weather ${index + 1} on T${weather.turn}: expected ${weather.expectedCopies} Storm copies, observed ${weather.observedCopies}"
        }
    }
    if (researcherTriggers != researcherCounters) audit += "Researcher triggers $researcherTriggers != counters $researcherCounters"
    if (mascotTriggers != mascotCounters) audit += "Mascot triggers $mascotTriggers != counters $mascotCounters"
    if (thrallDeaths != scionsCreated) audit += "Carrier Thrall deaths $thrallDeaths != Scions created $scionsCreated"
    if (scionActivations != scionSacrifices || scionActivations != scionManaEvents) {
        audit += "Scion mana lifecycle activations=$scionActivations sacrifices=$scionSacrifices manaEvents=$scionManaEvents"
    }
    val scionManaUses = sacrificeManaTrace.snapshot().filter { it.sourceName == "Eldrazi Scion" }
    scionManaUses.filter { it.manaProduced != it.manaConsumed + it.unusedMana }.forEach {
        audit += "Scion mana provenance does not balance for ${it.sourceId}: produced=${it.manaProduced} consumed=${it.manaConsumed} unused=${it.unusedMana}"
    }
    scionManaUses.filter { it.unusedMana > 0 }.forEach {
        audit += "Scion ${it.sourceId} sacrificed for ${it.unusedMana} unused mana on T${it.activationTurn}"
    }
    val scionFunded = scionManaUses.flatMap(SacrificeManaUse::fundedActions)
    val totalLife = lifeEvents.sumOf(PestLifeEvent::amount)
    if (state.lifeTotal(pestId) != 20 + totalLife) {
        audit += "life accounting final=${state.lifeTotal(pestId)} expected=${20 + totalLife}"
    }
    if (state.gameOver && terminal == null) audit += "game ended without terminal classification"
    if (terminal?.startsWith("BLANK_WIN") == true) audit += "blank solitaire opponent won"
    if (lifeEvents.any { it.amount <= 0 || it.source == "UNKNOWN" }) audit += "unattributed or nonpositive life-gain event"

    val coexistence = PestCoexistence(
        wardenResearcher,
        wardenMascot,
        researcherMascot,
        triple,
        payoffAtWeather,
        entryLifeEventsWithPayoff >= 2,
    )
    val functional = when {
        coexistence.wardenResearcher || coexistence.wardenMascot ||
            lifeEvents.any { it.researcherPresent || it.mascotPresent } -> "ENGINE_FUNCTIONAL"
        maxCreatures >= 3 || winningTurn != null -> "FAIR_CREATURE_FUNCTIONAL"
        stranded.size >= 2 -> "INTERACTION_HEAVY_BUT_GOLDFISH_CONSTRAINED"
        else -> "GENUINELY_NONFUNCTIONAL"
    }
    return PestGoldfishGame(
        gameNumber,
        seed,
        "0x${seed.toULong().toString(16).uppercase()}",
        mulligans,
        opening,
        t1,
        firstPermanent,
        winningTurn,
        terminal,
        wardenCasts,
        researcherCasts,
        mascotCasts,
        thrallCasts,
        thrallDeaths,
        scionsCreated,
        scionActivations,
        scionFunded,
        scionManaUses,
        witchCasts,
        entCycles,
        entCasts,
        follows,
        weathers.map { PestWeatherCast(it.turn, it.stormCount, it.expectedCopies, it.observedCopies) },
        lifeEvents,
        totalLife,
        researcherTriggers,
        researcherCounters,
        mascotTriggers,
        mascotCounters,
        maxResearcherPower,
        maxResearcherToughness,
        maxMascotPower,
        maxMascotToughness,
        maxCreatures,
        maxPermanents,
        stranded.toList(),
        bottlenecks.toList(),
        hollows,
        coexistence,
        functional,
        actions,
        stopReason,
        audit,
    )
}

private val SOLITAIRE_INTERACTION = setOf("Cast Down", "Bone Shards", "Chainer's Edict")

internal fun summarizePest(games: List<PestGoldfishGame>): PestGoldfishSummary {
    fun rate(count: Int): Double = count.toDouble() / games.size
    fun median(values: List<Int>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return if (sorted.size % 2 == 1) sorted[sorted.size / 2].toDouble()
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
    }
    val weather = games.flatMap(PestGoldfishGame::weatherCasts)
    val entCycles = games.sumOf { it.generousEntCycleTurns.size }
    val entCasts = games.sumOf { it.generousEntCastTurns.size }
    val follows = games.flatMap(PestGoldfishGame::followCasts)
    val enhanced = follows.count { it.mode == "ENHANCED" }
    val coexist = games.count { it.coexistence.wardenResearcher || it.coexistence.wardenMascot }
    return PestGoldfishSummary(
        games = games.size,
        mulliganGames = games.count { it.mulligans > 0 },
        totalMulligans = games.sumOf(PestGoldfishGame::mulligans),
        mulliganRate = rate(games.count { it.mulligans > 0 }),
        meaningfulDevelopmentByT1 = games.count { (it.firstMeaningfulPermanentTurn ?: Int.MAX_VALUE) <= 1 },
        meaningfulDevelopmentByT2 = games.count { (it.firstMeaningfulPermanentTurn ?: Int.MAX_VALUE) <= 2 },
        meaningfulDevelopmentByT3 = games.count { (it.firstMeaningfulPermanentTurn ?: Int.MAX_VALUE) <= 3 },
        medianFirstWarden = median(games.mapNotNull { it.essenceWardenCastTurns.minOrNull() }),
        medianFirstPayoff = median(games.mapNotNull {
            listOfNotNull(it.bloodResearcherCastTurns.minOrNull(), it.pestMascotCastTurns.minOrNull()).minOrNull()
        }),
        medianActualWinningTurn = median(games.mapNotNull(PestGoldfishGame::actualWinningTurn)),
        winsByT4 = games.count { (it.actualWinningTurn ?: Int.MAX_VALUE) <= 4 },
        winsByT5 = games.count { (it.actualWinningTurn ?: Int.MAX_VALUE) <= 5 },
        winsByT6 = games.count { (it.actualWinningTurn ?: Int.MAX_VALUE) <= 6 },
        winsByT7 = games.count { (it.actualWinningTurn ?: Int.MAX_VALUE) <= 7 },
        averageLifeEvents = games.map { it.lifeEvents.size }.average(),
        averageLifeGained = games.map(PestGoldfishGame::totalLifeGained).average(),
        weatherStormCountDistribution = weather.groupingBy(PestWeatherCast::stormCount).eachCount().toSortedMap(),
        researcherMaximumSizeDistribution = games.filter { it.maximumResearcherPower != null }.groupingBy {
            "${it.maximumResearcherPower}/${it.maximumResearcherToughness}"
        }.eachCount().toSortedMap(),
        mascotMaximumSizeDistribution = games.filter { it.maximumMascotPower != null }.groupingBy {
            "${it.maximumMascotPower}/${it.maximumMascotToughness}"
        }.eachCount().toSortedMap(),
        enginePayoffCoexistenceGames = coexist,
        enginePayoffCoexistenceRate = rate(coexist),
        carrierDeaths = games.sumOf(PestGoldfishGame::carrierThrallDeaths),
        scionsCreated = games.sumOf(PestGoldfishGame::scionsCreated),
        scionsSacrificedForMana = games.sumOf(PestGoldfishGame::scionsSacrificedForMana),
        scionFundedSpells = games.flatMap(PestGoldfishGame::scionFundedSpells)
            .map { it.substringBefore("@T") }.groupingBy { it }.eachCount().toSortedMap(),
        entCycles = entCycles,
        entCreatureCasts = entCasts,
        entCyclingRate = (entCycles + entCasts).takeIf { it > 0 }?.let { entCycles.toDouble() / it },
        followNormalCasts = follows.count { it.mode == "NORMAL" },
        followEnhancedCasts = enhanced,
        followEnhancedRate = follows.size.takeIf { it > 0 }?.let { enhanced.toDouble() / it },
        manaBottleneckGames = games.count { it.genuineManaBottlenecks.isNotEmpty() },
        manaBottleneckObservations = games.sumOf { it.genuineManaBottlenecks.size },
        manaConstraintDistribution = games.flatMap(PestGoldfishGame::genuineManaBottlenecks)
            .groupingBy { it.constraint.name }.eachCount().toSortedMap(),
        hollowTempoGames = games.count { it.jungleHollowTempoEvents.isNotEmpty() },
        hollowTempoEvents = games.sumOf { it.jungleHollowTempoEvents.size },
        solitaireInteractionConstrainedGames = games.count { it.solitaireStrandedInteraction.isNotEmpty() },
        strandedInteractionObservations = games.sumOf { it.solitaireStrandedInteraction.size },
        functionalStateDistribution = games.groupingBy(PestGoldfishGame::functionalState).eachCount().toSortedMap(),
    )
}

internal fun renderPestMarkdown(block: PestGoldfishBlock): String = buildString {
    val s = block.summary
    appendLine("# Pest Control v1.0 — Rejected Sample #1 Regression Replay")
    appendLine()
    appendLine("Regression evidence only. This retired vector is rejected for performance/baseline inference and optimization.")
    appendLine()
    appendLine("## Aggregate")
    appendLine()
    appendLine("- Games: ${s.games}; mulligan games: ${s.mulliganGames} (${pct(s.mulliganRate)}), total mulligans: ${s.totalMulligans}")
    appendLine("- Meaningful permanent development by T1/T2/T3: ${s.meaningfulDevelopmentByT1}/${s.meaningfulDevelopmentByT2}/${s.meaningfulDevelopmentByT3}")
    appendLine("- Median first Warden: ${s.medianFirstWarden ?: "n/a"}; median first payoff: ${s.medianFirstPayoff ?: "n/a"}")
    appendLine("- Median actual win: ${s.medianActualWinningTurn ?: "n/a"}; wins by T4/T5/T6/T7: ${s.winsByT4}/${s.winsByT5}/${s.winsByT6}/${s.winsByT7}")
    appendLine("- Average separate lifegain events: ${"%.2f".format(s.averageLifeEvents)}; average life gained: ${"%.2f".format(s.averageLifeGained)}")
    appendLine("- Weather Storm counts: ${s.weatherStormCountDistribution}")
    appendLine("- Maximum Researcher sizes: ${s.researcherMaximumSizeDistribution}")
    appendLine("- Maximum Mascot sizes: ${s.mascotMaximumSizeDistribution}")
    appendLine("- Warden + payoff coexistence: ${s.enginePayoffCoexistenceGames}/${s.games} (${pct(s.enginePayoffCoexistenceRate)})")
    appendLine("- Carrier deaths / Scions / mana sacrifices: ${s.carrierDeaths}/${s.scionsCreated}/${s.scionsSacrificedForMana}; funded: ${s.scionFundedSpells}")
    appendLine("- Ent cycles / creature casts: ${s.entCycles}/${s.entCreatureCasts}; cycling rate: ${s.entCyclingRate?.let(::pct) ?: "n/a"}")
    appendLine("- Follow normal / enhanced: ${s.followNormalCasts}/${s.followEnhancedCasts}; enhanced rate: ${s.followEnhancedRate?.let(::pct) ?: "n/a"}")
    appendLine("- Actionable mana bottlenecks: ${s.manaBottleneckGames} games, ${s.manaBottleneckObservations} observations; constraints: ${s.manaConstraintDistribution}")
    appendLine("- Jungle Hollow tempo: ${s.hollowTempoGames} games, ${s.hollowTempoEvents} tapped-entry events")
    appendLine("- Solitaire-stranded interaction: ${s.solitaireInteractionConstrainedGames} games, ${s.strandedInteractionObservations} observations")
    appendLine("- Functional states: ${s.functionalStateDistribution}")
    appendLine()
    appendLine("## Games")
    appendLine()
    appendLine("| # | Seed | Mull | Open access | T1 | First perm | Win | Class | Life events/gain | R max | M max | Board | Interaction | Bottleneck |")
    appendLine("|---:|---|---:|---|---|---:|---|---|---:|---|---|---:|---:|---:|")
    block.games.forEach { g ->
        val access = listOfNotNull(
            "G".takeIf { g.opening.green }, "B".takeIf { g.opening.black },
            "uG".takeIf { g.opening.untappedGreen }, "uB".takeIf { g.opening.untappedBlack },
            "Ent".takeIf { g.opening.entForestcyclingAvailable },
        ).joinToString("/").ifEmpty { "none" }
        appendLine("| ${g.game} | `${g.seedHex}` | ${g.mulligans} | $access | ${g.t1Development.joinToString("; ").ifEmpty { "—" }} | ${g.firstMeaningfulPermanentTurn ?: "—"} | ${g.actualWinningTurn?.let { "T$it ${g.terminalMechanism}" } ?: "—"} | ${g.functionalState} | ${g.lifeEvents.size}/${g.totalLifeGained} | ${size(g.maximumResearcherPower, g.maximumResearcherToughness)} | ${size(g.maximumMascotPower, g.maximumMascotToughness)} | ${g.largestCreatureBattlefield} | ${g.solitaireStrandedInteraction.size} | ${g.genuineManaBottlenecks.size} |")
    }
    appendLine()
    appendLine("## Per-game telemetry")
    block.games.forEach { g ->
        appendLine()
        appendLine("### Game ${g.game} — `${g.seedHex}`")
        appendLine()
        appendLine("- Kept hand: ${g.opening.keptHand}; mulligans: ${g.mulligans}; T1: ${g.t1Development}")
        appendLine("- Warden/Researcher/Mascot casts: ${g.essenceWardenCastTurns}/${g.bloodResearcherCastTurns}/${g.pestMascotCastTurns}")
        appendLine("- Carrier casts/deaths; Scions created/sacrificed/funded: ${g.carrierThrallCastTurns}/${g.carrierThrallDeaths}; ${g.scionsCreated}/${g.scionsSacrificedForMana}/${g.scionFundedSpells}")
        appendLine("- Scion mana provenance: ${g.scionManaUses}")
        appendLine("- Witchstalker: ${g.fierceWitchstalkerCastTurns}; Ent cycle/cast: ${g.generousEntCycleTurns}/${g.generousEntCastTurns}")
        appendLine("- Follow: ${g.followCasts}; Weather: ${g.weatherCasts}")
        appendLine("- Lifegain: ${g.lifeEvents}; Researcher triggers/counters: ${g.researcherCounterTriggers}/${g.researcherCountersAdded}; Mascot: ${g.mascotCounterTriggers}/${g.mascotCountersAdded}")
        appendLine("- Coexistence: ${g.coexistence}; Hollow: ${g.jungleHollowTempoEvents}")
        appendLine("- Solitaire-stranded interaction: ${g.solitaireStrandedInteraction}")
        appendLine("- Genuine mana bottlenecks: ${g.genuineManaBottlenecks}")
        appendLine("- Terminal: ${g.actualWinningTurn?.let { "T$it" } ?: "none by horizon"} / ${g.terminalMechanism}; stop=${g.stopReason}; actions=${g.actions}")
        appendLine("- Audit: ${g.auditErrors.ifEmpty { listOf("clean") }}")
    }
}

private fun pct(value: Double): String = "%.1f%%".format(value * 100.0)
private fun size(power: Int?, toughness: Int?): String = if (power == null) "—" else "$power/$toughness"
