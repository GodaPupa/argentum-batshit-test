package com.wingedsheep.ai.engine

import com.wingedsheep.ai.llm.BottomCardsInfo
import com.wingedsheep.ai.llm.CardSummary
import com.wingedsheep.ai.llm.MulliganInfo
import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.BottomCards
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.TakeMulligan
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/** Fixed-seed opening-hand preflight for the preserved Lilysplash Mentor control deck. */
class LilysplashOpeningHandBenchmark : FunSpec({

    data class SubmittedDeck(val commander: String, val library: List<String>)
    data class KeptHand(
        val policy: String,
        val seed: Long,
        val seat: Int,
        val mulligans: Int,
        val cards: List<String>,
        val lands: Int,
        val directBlue: Int,
        val directGreen: Int,
        val fixers: Int,
    )

    val enabled = System.getProperty("lilysplashPreflight") == "true"
    val seeds = (1L..12L).map { 2026091400L + it }

    fun submittedDeck(): SubmittedDeck {
        val relative = Path.of("docs/experiments/lilysplash/submitted-v0.1.txt")
        val snapshot = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .map { it.resolve(relative) }
            .firstOrNull { Files.isRegularFile(it) }
            ?: error("Could not locate $relative from the test working directory")
        val lines = Files.readAllLines(snapshot)
            .filter { it.isNotBlank() && !it.startsWith("#") }
        val entry = Regex("""^(\d+)\s+(.+)$""")
        val commander = entry.matchEntire(lines[1])!!.groupValues[2]
        val library = lines.drop(3).flatMap { line ->
            val match = entry.matchEntire(line) ?: error("Unparseable submitted deck line: $line")
            List(match.groupValues[1].toInt()) { match.groupValues[2] }
        }
        return SubmittedDeck(commander, library)
    }

    fun summaries(state: GameState, playerId: EntityId): Map<EntityId, CardSummary> =
        state.getHand(playerId).associateWith { cardId ->
            val card = state.getEntity(cardId)!!.get<CardComponent>()!!
            CardSummary(
                name = card.name,
                manaCost = card.manaCost.toString(),
                typeLine = card.typeLine.toString(),
                oracleText = card.oracleText,
            )
        }

    fun process(
        processor: ActionProcessor,
        state: GameState,
        action: com.wingedsheep.engine.core.GameAction,
    ): GameState {
        val result = processor.process(state, action).result
        check(result.error == null) { "${action::class.simpleName} failed: ${result.error}" }
        return result.state
    }

    val blueSources = setOf(
        "Island", "Command Tower", "Halimar Depths", "Lonely Sandbar", "Saprazzan Skerry",
        "Simic Growth Chamber", "The Surgical Bay",
    )
    val greenSources = setOf(
        "Forest", "Command Tower", "Hickory Woodlot", "Simic Growth Chamber", "The Hunter Maze",
        "Tranquil Thicket",
    )
    val fixing = setOf(
        "Ash Barrens", "Escape Tunnel", "Evolving Wilds", "Terramorphic Expanse", "Lórien Revealed",
    )
    val fetchBoth = setOf("Escape Tunnel", "Evolving Wilds", "Terramorphic Expanse")

    fun hasCommanderColors(cards: Map<EntityId, CardSummary>): Boolean {
        val names = cards.values.map { it.name }
        val lands = cards.values.count { it.typeLine?.contains("Land", ignoreCase = true) == true }
        val blue = names.any { it in blueSources || it in fetchBoth } ||
            ("Ash Barrens" in names && lands >= 2) || ("Lórien Revealed" in names && lands >= 1)
        val green = names.any { it in greenSources || it in fetchBoth } ||
            ("Ash Barrens" in names && lands >= 2)
        return blue && green
    }

    test("Lilysplash fixed-seed opening-hand preflight").config(enabled = enabled) {
        val submitted = submittedDeck()
        val deck = Deck(cards = submitted.library, commander = submitted.commander)
        val registry = CardRegistry().apply {
            register(MtgSetCatalog.all.flatMap { it.cards + it.basicLands })
        }
        val processor = ActionProcessor(registry)
        val initializer = GameInitializer(registry)
        val rows = mutableListOf<KeptHand>()

        for (policy in listOf("generic", "commander-aware")) {
            for (seed in seeds) {
            val init = initializer.initializeGame(
                GameConfig(
                    players = listOf(
                        PlayerConfig("Seat0", deck, commanderCardName = submitted.commander),
                        PlayerConfig("Seat1", deck, commanderCardName = submitted.commander),
                    ),
                    format = Format.Commander(alwaysDivertToCommand = true),
                    skipMulligans = false,
                    startingPlayerIndex = 0,
                    seed = seed,
                ),
            )
            var state = init.state
            val controllers = init.playerIds.map { playerId ->
                EngineAiPlayerController(registry, playerId, gameStateProvider = { state })
            }
            val mulligans = IntArray(init.playerIds.size)

            for ((seat, playerId) in init.playerIds.withIndex()) {
                while (true) {
                    val cards = summaries(state, playerId)
                    val mulliganState = state.getEntity(playerId)!!.get<MulliganStateComponent>()!!
                    val genericKeep = controllers[seat].decideMulligan(
                        MulliganInfo(
                            hand = state.getHand(playerId),
                            mulliganCount = mulliganState.mulligansTaken,
                            cardsToPutOnBottom = mulliganState.cardsToBottom,
                            cards = cards,
                            isOnThePlay = seat == 0,
                        ),
                    )
                    val keep = genericKeep && (
                        policy == "generic" || mulliganState.mulligansTaken >= 2 || hasCommanderColors(cards)
                    )
                    if (keep) {
                        state = process(processor, state, KeepHand(playerId))
                        break
                    }
                    state = process(processor, state, TakeMulligan(playerId))
                    mulligans[seat]++
                }
            }

            for ((seat, playerId) in init.playerIds.withIndex()) {
                val mulliganState = state.getEntity(playerId)!!.get<MulliganStateComponent>()!!
                if (mulliganState.cardsToBottom > 0) {
                    val cards = summaries(state, playerId)
                    val bottom = controllers[seat].chooseBottomCards(
                        BottomCardsInfo(
                            hand = state.getHand(playerId),
                            cardsToPutOnBottom = mulliganState.cardsToBottom,
                            cards = cards,
                        ),
                    )
                    state = process(processor, state, BottomCards(playerId, bottom))
                }
            }

            for ((seat, playerId) in init.playerIds.withIndex()) {
                val hand = state.getHand(playerId).map { state.getEntity(it)!!.get<CardComponent>()!!.name }
                val landCount = state.getHand(playerId).count {
                    state.getEntity(it)!!.get<CardComponent>()!!.typeLine.isLand
                }
                rows += KeptHand(
                    policy = policy,
                    seed = seed,
                    seat = seat,
                    mulligans = mulligans[seat],
                    cards = hand,
                    lands = landCount,
                    directBlue = hand.count { it in blueSources },
                    directGreen = hand.count { it in greenSources },
                    fixers = hand.count { it in fixing },
                )
            }
            }
        }

        println("=== LILYSPLASH OPENING-HAND PREFLIGHT ===")
        println("deck=f315b0907f3f9ae9d61ae2d45de0b778b45a9d9ff86e6ac5c343e4385d5de525 seeds=${seeds.joinToString(",")}")
        rows.forEach { row ->
            println(
                "policy=${row.policy} seed=${row.seed} seat=${row.seat} mulligans=${row.mulligans} kept=${row.cards.size} " +
                    "lands=${row.lands} U=${row.directBlue} G=${row.directGreen} fixers=${row.fixers} " +
                    "hand=${row.cards.joinToString(" | ")}",
            )
        }
        rows.groupBy { it.policy }.forEach { (policy, policyRows) ->
            println(
                "summary policy=$policy samples=${policyRows.size} " +
                    "avgMulligans=${String.format(Locale.ROOT, "%.3f", policyRows.map { it.mulligans }.average())} " +
                    "keep7=${policyRows.count { it.mulligans == 0 }} keep6=${policyRows.count { it.mulligans == 1 }} " +
                    "keep5=${policyRows.count { it.mulligans == 2 }} lowLand=${policyRows.count { it.lands <= 1 }} " +
                    "noDirectU=${policyRows.count { it.directBlue == 0 }} noDirectG=${policyRows.count { it.directGreen == 0 }} " +
                    "noUAccess=${policyRows.count { it.directBlue == 0 && it.cards.none { name -> name in fixing } }} " +
                    "noGAccess=${policyRows.count { it.directGreen == 0 && it.cards.none { name -> name in fixing - "Lórien Revealed" } }}",
            )
        }
        check(rows.size == seeds.size * 2 * 2)
    }
})
