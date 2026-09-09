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
import com.wingedsheep.engine.state.components.battlefield.PreparedSpellCopyComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThan
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

/**
 * Opt-in, fully logged Argentum-agent smoke run for the frozen Batshit Economics and Mono-Red
 * Madness maindecks. Ordinary validation compiles this harness but does not spend five full games
 * running it; `.github/workflows/batshit-preboard-smoke.yml` is the explicit entry point.
 *
 * This is observation, not a matchup benchmark. It deliberately runs only five fixed seeds and
 * makes no aggregate win-rate assertion.
 */
class BatshitEconomicsPreboardSmokeTest : FunSpec({

    val enabled = System.getenv("BATSHIT_SMOKE") == "true"

    test("five seeded preboard Argentum agent self-play games").config(
        enabled = enabled,
        timeout = 45.minutes,
    ) {
        val registry = fullRegistry()
        val seeds = listOf(0xBA75_0001L, 0xBA75_0002L, 0xBA75_0003L, 0xBA75_0004L, 0xBA75_0005L)
        val reports = seeds.mapIndexed { index, seed ->
            playLoggedGame(
                registry = registry,
                gameNumber = index + 1,
                seed = seed,
                startingPlayerIndex = index % 2,
            )
        }

        val output = buildString {
            appendLine("BATSHIT ECONOMICS VS MONO-RED MADNESS")
            appendLine("Argentum agent self-play — five-game preboard smoke test")
            appendLine("Profile: ${AiProfile.PRODUCTION_CANDIDATE_EXPIRING.id}")
            appendLine("Seeds: ${seeds.joinToString()}")
            appendLine()
            reports.forEach { append(it.log) }
        }

        val reportPath = Path.of("build", "reports", "batshit-smoke", "preboard.log")
        Files.createDirectories(reportPath.parent)
        Files.writeString(reportPath, output)
        println(output)

        reports.size shouldBe 5
        reports.forEach { report ->
            report.actions shouldBeGreaterThan 0
            report.completed.shouldBeTrue()
        }
    }
})

private data class LoggedSmokeGame(
    val completed: Boolean,
    val actions: Int,
    val log: String,
)

internal class SmokeTriggerTelemetry {
    var flamebreatherTriggers: Int = 0
        private set
    var flamebreatherDamage: Int = 0
        private set
    var guttersnipeTriggers: Int = 0
        private set
    var guttersnipeDamage: Int = 0
        private set

    private val unresolvedTriggers = mutableMapOf<String, Int>()

    fun record(event: GameEvent) {
        when (event) {
            is AbilityTriggeredEvent -> when {
                event.sourceName == "Kessig Flamebreather" &&
                    event.description == "you casts a noncreature spell, deal 1 damage to each opponent." -> {
                    flamebreatherTriggers++
                    unresolvedTriggers.merge(event.sourceName, 1) { current, added -> current + added }
                }
                event.sourceName == "Guttersnipe" &&
                    event.description == "you casts a instant or sorcery spell, deal 2 damage to each opponent." -> {
                    guttersnipeTriggers++
                    unresolvedTriggers.merge(event.sourceName, 1) { current, added -> current + added }
                }
            }
            is DamageDealtEvent -> {
                val sourceName = event.sourceName ?: return
                if (event.isCombatDamage || unresolvedTriggers.getOrDefault(sourceName, 0) == 0) return

                when (sourceName) {
                    "Kessig Flamebreather" -> flamebreatherDamage += event.amount
                    "Guttersnipe" -> guttersnipeDamage += event.amount
                    else -> return
                }
                unresolvedTriggers[sourceName] = unresolvedTriggers.getValue(sourceName) - 1
            }
            else -> Unit
        }
    }
}

private fun fullRegistry(): CardRegistry = CardRegistry().apply {
    // Prepared Craft, Fanatical Offering, Epicure, and NDAA all resolve through named predefined
    // tokens. The production game/gym registries install these explicitly; the smoke harness must
    // do the same or token creation fails during resolution and the trace falsely reports that the
    // corresponding Bats triggers never happened.
    register(PredefinedTokens.allTokens)
    MtgSetCatalog.all.forEach { set ->
        register(set.cards)
        register(set.basicLands)
    }
}

private fun batshitDeck(): Deck = Deck.of(
    "Goblin Glasswright" to 4,
    "Kessig Flamebreather" to 4,
    "Mirkwood Bats" to 3,
    "Shambling Ghast" to 3,
    "Voldaren Epicure" to 4,
    "Not Dead After All" to 3,
    "Unearth" to 4,
    "Village Rites" to 3,
    "Fanatical Offering" to 3,
    "Makeshift Munitions" to 1,
    "Lightning Bolt" to 4,
    "Cast Down" to 2,
    "Swamp" to 9,
    "Mountain" to 9,
    "Razortrap Gorge" to 4,
).copy(
    sideboard = listOf(
        "Duress" to 3,
        "Pyroblast" to 2,
        "Breath Weapon" to 3,
        "Soul-Guide Lantern" to 2,
        "Vandalblast" to 2,
        "Campfire" to 2,
        "Sovereign's Bite" to 1,
    ).flatMap { (name, count) ->
        List(count) { com.wingedsheep.sdk.model.CardEntry(name) }
    },
)

private fun monoRedDeck(): Deck = Deck.of(
    "Voldaren Epicure" to 4,
    "Kessig Flamebreather" to 4,
    "Sneaky Snacker" to 4,
    "Guttersnipe" to 3,
    "Lightning Bolt" to 4,
    "Lava Dart" to 4,
    "Fiery Temper" to 4,
    "Fireblast" to 3,
    "Faithless Looting" to 3,
    "Grab the Prize" to 4,
    "Highway Robbery" to 4,
    "Mountain" to 19,
)

private fun playLoggedGame(
    registry: CardRegistry,
    gameNumber: Int,
    seed: Long,
    startingPlayerIndex: Int,
): LoggedSmokeGame {
    val processor = ActionProcessor(registry)
    val initializer = GameInitializer(registry)
    val batshit75 = batshitDeck()
    // This run is deliberately preboard. Keep the authoritative 15 encoded above, but do not ask
    // GameInitializer to resolve sideboard-only cards that can never enter these five games.
    val batshit = batshit75.copy(sideboard = emptyList())
    val red = monoRedDeck()
    val init = initializer.initializeGame(
        GameConfig(
            players = listOf(
                PlayerConfig("Batshit Economics", batshit),
                PlayerConfig("Mono-Red Madness", red),
            ),
            skipMulligans = false,
            useHandSmoother = false,
            startingPlayerIndex = startingPlayerIndex,
            seed = seed,
        )
    )

    val batshitId = init.playerIds[0]
    val redId = init.playerIds[1]
    val names = mapOf(batshitId to "Batshit", redId to "Red")
    fun label(id: EntityId?) = names[id] ?: id?.toString() ?: "none"

    var state = init.state
    val mulliganControllers = mapOf(
        batshitId to EngineAiPlayerController(registry, batshitId, gameStateProvider = { state }),
        redId to EngineAiPlayerController(registry, redId, gameStateProvider = { state }),
    )
    val log = StringBuilder()
    val playLabel = if (startingPlayerIndex == 0) "Batshit plays; Red draws" else "Red plays; Batshit draws"
    log.appendLine("=== GAME $gameNumber ===")
    log.appendLine("Seed: $seed (0x${seed.toString(16).uppercase()})")
    log.appendLine("Play/draw: $playLabel")

    fun cardName(id: EntityId): String =
        state.getEntity(id)?.get<CardComponent>()?.name ?: id.toString()

    fun handNames(playerId: EntityId): List<String> = state.getHand(playerId).map(::cardName)

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

    // Drive the engine's real London-mulligan actions using the production engine controller's
    // keep/bottom policy. GameInitializer's playerIds stay in configured deck order even when the
    // turn order rotates, so deck labels remain stable while play/draw alternates.
    for (playerId in state.turnOrder) {
        while (true) {
            val mulligan = state.getEntity(playerId)!!.get<MulliganStateComponent>()!!
            val hand = state.getHand(playerId)
            val info = MulliganInfo(
                hand = hand,
                mulliganCount = mulligan.mulligansTaken,
                cardsToPutOnBottom = mulligan.cardsToBottom,
                cards = summaries(playerId),
                isOnThePlay = state.turnOrder.first() == playerId,
            )
            val keep = mulliganControllers.getValue(playerId).decideMulligan(info)
            log.appendLine(
                "Mulligan ${label(playerId)}: ${if (keep) "KEEP" else "MULLIGAN"} " +
                    "after ${mulligan.mulligansTaken}; hand=${handNames(playerId)}"
            )
            val result = processor.process(
                state,
                if (keep) KeepHand(playerId) else TakeMulligan(playerId),
            ).result
            check(result.error == null) { "Mulligan action failed: ${result.error}" }
            state = result.state
            if (keep) break
        }
    }

    val mulliganCounts = init.playerIds.associateWith { playerId ->
        state.getEntity(playerId)!!.get<MulliganStateComponent>()!!.mulligansTaken
    }

    for (playerId in state.turnOrder) {
        val mulligan = state.getEntity(playerId)!!.get<MulliganStateComponent>()!!
        if (mulligan.cardsToBottom <= 0) continue
        val bottom = mulliganControllers.getValue(playerId).chooseBottomCards(
            BottomCardsInfo(
                hand = state.getHand(playerId),
                cardsToPutOnBottom = mulligan.cardsToBottom,
                cards = summaries(playerId),
            )
        )
        log.appendLine("Bottom ${label(playerId)}: ${bottom.map(::cardName)}")
        val result = processor.process(state, BottomCards(playerId, bottom)).result
        check(result.error == null) { "Bottom-cards action failed: ${result.error}" }
        state = result.state
    }

    log.appendLine("Kept Batshit: ${handNames(batshitId)}")
    log.appendLine("Kept Red: ${handNames(redId)}")

    // All post-mulligan play goes through the same stateful Gym boundary used by agent clients.
    // stepExactlyOne preserves real priority (the opposing agent still gets every response window)
    // while installing the authoritative ActionProcessor state and event stream into Gym.
    val environment = GameEnvironment.create(registry).also {
        it.restore(state, init.playerIds)
    }

    val opponentDecks = mapOf(
        batshitId to com.wingedsheep.ai.engine.hidden.OpponentModel.KnownDecklist(
            red.cards.groupingBy { it }.eachCount()
        ),
        redId to com.wingedsheep.ai.engine.hidden.OpponentModel.KnownDecklist(
            batshit.cards.groupingBy { it }.eachCount()
        ),
    )
    val agents = mapOf(
        batshitId to AIPlayer.create(
            registry, batshitId, AiProfile.PRODUCTION_CANDIDATE_EXPIRING,
            opponentModels = mapOf(redId to opponentDecks.getValue(batshitId)),
        ),
        redId to AIPlayer.create(
            registry, redId, AiProfile.PRODUCTION_CANDIDATE_EXPIRING,
            opponentModels = mapOf(batshitId to opponentDecks.getValue(redId)),
        ),
    )

    var actions = 0
    var lastTurn = -1
    var actionsOnTurn = 0
    var endReason = "unfinished"
    var lastMeaningful = "none"
    var glasswrightEntries = 0
    var craftCasts = 0
    var batsTriggers = 0
    val triggerTelemetry = SmokeTriggerTelemetry()
    var gorgeTappedEntries = 0

    fun life(playerId: EntityId) = state.lifeTotal(playerId)

    fun preparedCopies(gameState: GameState, playerId: EntityId): Int =
        gameState.getExile(playerId).count { id ->
            gameState.getEntity(id)?.get<PreparedSpellCopyComponent>() != null
        }

    fun targetName(target: com.wingedsheep.engine.state.components.stack.ChosenTarget): String = when (target) {
        is com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent -> cardName(target.entityId)
        is com.wingedsheep.engine.state.components.stack.ChosenTarget.Card -> cardName(target.cardId)
        is com.wingedsheep.engine.state.components.stack.ChosenTarget.Player -> label(target.playerId)
        is com.wingedsheep.engine.state.components.stack.ChosenTarget.Spell -> cardName(target.spellEntityId)
    }

    fun describeAction(action: GameAction): String = when (action) {
        is CastSpell -> {
            val card = state.getEntity(action.cardId)
            val isCraft = card?.get<PreparedSpellCopyComponent>() != null
            val name = if (isCraft) "Craft with Pride" else cardName(action.cardId)
            val targets = action.targets.takeIf { it.isNotEmpty() }
                ?.joinToString(prefix = " targets=[", postfix = "]") { targetName(it) }.orEmpty()
            val sacrifices = action.additionalCostPayment?.sacrificedPermanents.orEmpty()
                .takeIf { it.isNotEmpty() }?.map(::cardName)
            val discards = action.additionalCostPayment?.discardedCards.orEmpty()
                .takeIf { it.isNotEmpty() }?.map(::cardName)
            buildString {
                append("cast $name$targets")
                if (sacrifices != null) append(" sacrifice=$sacrifices")
                if (discards != null) append(" discard=$discards")
                if (action.alternativeCostType != null) append(" via=${action.alternativeCostType}")
            }
        }
        is ActivateAbility -> {
            val targets = action.targets.takeIf { it.isNotEmpty() }
                ?.joinToString(prefix = " targets=[", postfix = "]") { targetName(it) }.orEmpty()
            val sacrifices = action.costPayment?.sacrificedPermanents.orEmpty()
                .takeIf { it.isNotEmpty() }?.map(::cardName)
            "activate ${cardName(action.sourceId)}$targets" +
                (sacrifices?.let { " sacrifice=$it" } ?: "")
        }
        is PlayLand -> "play ${cardName(action.cardId)}"
        is DeclareAttackers -> "attack " + action.attackers.keys.map(::cardName)
        is DeclareBlockers -> "block " + action.blockers.entries.joinToString { (blocker, attackers) ->
            "${cardName(blocker)} -> ${attackers.map(::cardName)}"
        }
        else -> action::class.simpleName ?: "action"
    }

    fun describeDecision(response: DecisionResponse): String = when (response) {
        is CardsSelectedResponse -> "cards=${response.selectedCards.map(::cardName)}"
        is TargetsResponse -> "targets=${response.selectedTargets.values.flatten().map(::cardName)}"
        is YesNoResponse -> "choice=${response.choice}"
        is ModesChosenResponse -> "modes=${response.selectedModes}"
        is NumberChosenResponse -> "number=${response.number}"
        else -> response.toString()
    }

    fun logEvents(events: List<GameEvent>, resultingState: GameState) {
        events.forEach { event ->
            when (event) {
                is SpellCastEvent -> log.appendLine(
                    "  EVENT spell ${label(event.casterId)} ${event.cardName}" +
                        (event.targetNames.takeIf { it.isNotEmpty() }?.let { " -> $it" } ?: "") +
                        (event.castFromZone?.let { " from=$it" } ?: "") +
                        (event.alternativeCost?.let { " via=$it" } ?: "")
                )
                is AbilityTriggeredEvent -> if (
                    event.sourceName in setOf("Kessig Flamebreather", "Mirkwood Bats", "Guttersnipe", "Shambling Ghast")
                ) {
                    triggerTelemetry.record(event)
                    when (event.sourceName) {
                        "Mirkwood Bats" -> batsTriggers++
                    }
                    log.appendLine("  EVENT trigger ${event.sourceName}: ${event.description}")
                }
                is AbilityActivatedEvent -> if (event.sourceName == "Makeshift Munitions" || event.sourceName == "Treasure") {
                    log.appendLine("  EVENT ability ${label(event.controllerId)} ${event.sourceName}")
                }
                is DamageDealtEvent -> {
                    triggerTelemetry.record(event)
                    if (event.sourceName in setOf(
                            "Kessig Flamebreather", "Guttersnipe", "Lightning Bolt", "Lava Dart",
                            "Fiery Temper", "Fireblast", "Makeshift Munitions", "Voldaren Epicure"
                        ) || event.isCombatDamage
                    ) {
                        log.appendLine(
                            "  EVENT damage ${event.sourceName ?: "unknown"} ${event.amount} -> " +
                                (event.targetName ?: label(event.targetId)) +
                                if (event.isCombatDamage) " (combat)" else ""
                        )
                    }
                }
                is LifeChangedEvent -> log.appendLine(
                    "  EVENT life ${label(event.playerId)} ${event.oldLife}->${event.newLife} ${event.reason}"
                )
                is CardsDiscardedEvent -> log.appendLine(
                    "  EVENT discard ${label(event.playerId)} ${event.cardNames}"
                )
                is PermanentsSacrificedEvent -> log.appendLine(
                    "  EVENT sacrifice ${label(event.playerId)} ${event.permanentNames}"
                )
                is ZoneChangeEvent -> {
                    if (event.entityName == "Goblin Glasswright" && event.toZone == Zone.BATTLEFIELD) {
                        glasswrightEntries++
                        val prepared = preparedCopies(resultingState, event.ownerId)
                        log.appendLine("  EVENT Glasswright battlefield incarnation #$glasswrightEntries; preparedCopies=$prepared")
                    }
                    if (event.entityName in setOf(
                            "Goblin Glasswright", "Kessig Flamebreather", "Mirkwood Bats", "Guttersnipe",
                            "Shambling Ghast", "Sneaky Snacker", "Treasure", "Map"
                        ) && (event.fromZone == Zone.BATTLEFIELD || event.toZone == Zone.BATTLEFIELD)
                    ) {
                        log.appendLine(
                            "  EVENT zone ${event.entityName} ${event.fromZone}->${event.toZone}" +
                                if (event.wasSacrificed) " sacrificed" else ""
                        )
                    }
                }
                is GameEndedEvent -> endReason = event.reason.name
                else -> Unit
            }
        }
    }

    val maxPlayerTurns = 60
    val maxActions = 12_000
    while (!state.gameOver && state.turnNumber <= maxPlayerTurns && actions < maxActions) {
        if (state.turnNumber != lastTurn) {
            lastTurn = state.turnNumber
            actionsOnTurn = 0
            log.appendLine(
                "TURN ${state.turnNumber} active=${label(state.activePlayerId)} " +
                    "life(B/R)=${life(batshitId)}/${life(redId)} " +
                    "hand(B/R)=${state.getHand(batshitId).size}/${state.getHand(redId).size}"
            )
        }
        actionsOnTurn++
        check(actionsOnTurn <= 500) { "Game wedged on turn ${state.turnNumber}" }

        val decision = state.pendingDecision
        val actingPlayer = decision?.playerId ?: state.priorityPlayerId
            ?: error("No priority player and no decision on turn ${state.turnNumber}")
        val ai = agents.getValue(actingPlayer)
        actions++

        val action: GameAction
        if (decision != null) {
            val response = ai.respondToDecision(state, decision)
            action = SubmitDecision(actingPlayer, response)
            val line = "T${state.turnNumber} ${label(actingPlayer)} decision ${decision::class.simpleName}: ${describeDecision(response)}"
            log.appendLine(line)
            lastMeaningful = line
        } else {
            action = ai.chooseAction(state)
            if (action !is PassPriority) {
                val line = "T${state.turnNumber} ${label(actingPlayer)} ${describeAction(action)}"
                log.appendLine(line)
                lastMeaningful = line
                if (action is CastSpell && state.getEntity(action.cardId)?.get<PreparedSpellCopyComponent>() != null) {
                    craftCasts++
                }
            }
        }

        val beforePrepared = preparedCopies(state, batshitId)
        val step = when (val result = environment.stepExactlyOne(action)) {
            is ExactlyOneSubmissionResult.Applied -> result.step
            is ExactlyOneSubmissionResult.Rejected -> error(
                "Illegal agent action $action: ${result.reason}"
            )
        }
        logEvents(step.events, step.state)

        if (action is PlayLand && cardName(action.cardId) == "Razortrap Gorge") {
            val tapped = step.state.getEntity(action.cardId)?.get<TappedComponent>() != null
            if (tapped) gorgeTappedEntries++
            log.appendLine("  TEMPO Razortrap Gorge entered ${if (tapped) "tapped" else "untapped"}")
        }
        val afterPrepared = preparedCopies(step.state, batshitId)
        if (beforePrepared != afterPrepared) {
            log.appendLine("  RESOURCE prepared Craft copies $beforePrepared->$afterPrepared")
        }
        state = step.state
    }

    if (!state.gameOver) {
        endReason = when {
            actions >= maxActions -> "MAX_ACTIONS"
            else -> "MAX_TURNS"
        }
    }
    val winner = when (state.winnerId) {
        batshitId -> "Batshit Economics"
        redId -> "Mono-Red Madness"
        else -> "none"
    }
    val tracked = setOf("Village Rites", "Unearth", "Not Dead After All")
    val strandedBatshit = handNames(batshitId).filter { it in tracked }
    val battlefieldBatshit = state.controlledBattlefield(batshitId).mapNotNull { id ->
        state.getEntity(id)?.get<CardComponent>()?.name
    }
    val battlefieldRed = state.controlledBattlefield(redId).mapNotNull { id ->
        state.getEntity(id)?.get<CardComponent>()?.name
    }

    log.appendLine("--- RESULT GAME $gameNumber ---")
    log.appendLine("Winner: $winner")
    log.appendLine("Ending turn: ${state.turnNumber}")
    log.appendLine("Final life Batshit/Red: ${life(batshitId)}/${life(redId)}")
    log.appendLine("End reason: $endReason")
    log.appendLine("Proximate last meaningful decision: $lastMeaningful")
    log.appendLine("Mulligans Batshit/Red: ${mulliganCounts.getValue(batshitId)}/${mulliganCounts.getValue(redId)}")
    log.appendLine("Glasswright entries/resets: $glasswrightEntries; Craft casts: $craftCasts")
    log.appendLine(
        "Flamebreather triggers/damage: ${triggerTelemetry.flamebreatherTriggers}/" +
            triggerTelemetry.flamebreatherDamage
    )
    log.appendLine("Mirkwood Bats triggers: $batsTriggers")
    log.appendLine(
        "Guttersnipe triggers/damage: ${triggerTelemetry.guttersnipeTriggers}/" +
            triggerTelemetry.guttersnipeDamage
    )
    log.appendLine("Tapped Razortrap Gorge entries: $gorgeTappedEntries")
    log.appendLine("Stranded Batshit Rites/Unearth/NDAA: $strandedBatshit")
    log.appendLine("Surviving Batshit battlefield: $battlefieldBatshit")
    log.appendLine("Surviving Red battlefield: $battlefieldRed")
    log.appendLine("Actions: $actions")
    log.appendLine()

    return LoggedSmokeGame(state.gameOver, actions, log.toString())
}
