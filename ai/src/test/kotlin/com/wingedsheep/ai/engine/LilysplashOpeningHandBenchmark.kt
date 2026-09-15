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

    fun submittedDeck(relativePath: String = "docs/experiments/lilysplash/submitted-v0.1.txt"): SubmittedDeck {
        val relative = Path.of(relativePath)
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
        // Stage 4 land-base challenger v1 additions (all true UG duals):
        "Yavimaya Coast", "Simic Guildgate", "Thornwood Falls",
    )
    val greenSources = setOf(
        "Forest", "Command Tower", "Hickory Woodlot", "Simic Growth Chamber", "The Hunter Maze",
        "Tranquil Thicket",
        // Stage 4 land-base challenger v1 additions (all true UG duals):
        "Yavimaya Coast", "Simic Guildgate", "Thornwood Falls",
    )
    val fixing = setOf(
        "Ash Barrens", "Escape Tunnel", "Evolving Wilds", "Terramorphic Expanse", "Lórien Revealed",
        // Stage 4 aura challenger v1 addition: also a "find the color you're missing" card, just via
        // a colorless artifact instead of a fetchland.
        "Wayfarer's Bauble",
    )
    val fetchBoth = setOf("Escape Tunnel", "Evolving Wilds", "Terramorphic Expanse")

    // Every card in the submitted list that looks at extra cards and either draws, filters, or
    // fetches from the library -- the "selection" half of Stage 4 item 1 ("cheaper selection and
    // tutors"). Muddle the Mixture counts here for its search-a-CMC-2-card mode even though its
    // other mode is a counterspell (Stage 4 item 4's territory); it isn't touched by either
    // challenger so its dual purpose doesn't confound anything.
    val selectionSpells = setOf(
        "Brainstorm", "Ponder", "Faerie Seer", "Serum Visionary", "Cloudkin Seer", "Sea Gate Oracle",
        "Pond Prophet", "Mulldrifter", "Elvish Visionary", "Llanowar Visionary", "Pondering Mage",
        "Muddle the Mixture", "Coiling Oracle",
        // Stage 4 selection challenger v1 additions:
        "Opt", "Preordain",
    )
    val cheapSelectionSpells = setOf(
        "Brainstorm", "Ponder", "Faerie Seer",
        // Stage 4 selection challenger v1 additions (both {U}):
        "Opt", "Preordain",
    )

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

    data class DeckHand(
        val deck: String,
        val seed: Long,
        val seat: Int,
        val mulligans: Int,
        val cards: List<String>,
        val lands: Int,
        val directBlue: Int,
        val directGreen: Int,
        val fixers: Int,
    )

    fun checkDeckGuardrails(deck: SubmittedDeck, label: String) {
        check(deck.commander == "Lilysplash Mentor") { "$label commander mismatch: ${deck.commander}" }
        check(deck.library.size == 99) { "$label has ${deck.library.size} deck cards, expected 99" }
        val nonBasics = deck.library.filterNot { it == "Forest" || it == "Island" }
        val duplicates = nonBasics.groupingBy { it }.eachCount().filterValues { it > 1 }
        check(duplicates.isEmpty()) { "$label has repeated nonbasics: $duplicates" }
    }

    // Stage 4 item 6 (land-base speed vs. bounce-land/enhanced-land combo value): the submitted
    // control deck against a frozen land-base-only challenger, both under the commander-aware keep
    // rule that Stage 3 settled on. No ramp spell, aura, or non-land card differs between the two --
    // only the land base changes, per the plan's one-variable-at-a-time rule.
    test("Lilysplash land-base challenger v1 preflight").config(
        enabled = System.getProperty("lilysplashLandChallenger") == "true",
    ) {
        val control = submittedDeck()
        val challenger = submittedDeck("docs/experiments/lilysplash/challenger-land-v1.txt")
        checkDeckGuardrails(control, "control")
        checkDeckGuardrails(challenger, "challenger-land-v1")

        val registry = CardRegistry().apply {
            register(MtgSetCatalog.all.flatMap { it.cards + it.basicLands })
        }
        val processor = ActionProcessor(registry)
        val initializer = GameInitializer(registry)
        val rows = mutableListOf<DeckHand>()

        for ((deckLabel, deck) in listOf("control" to control, "challenger-land-v1" to challenger)) {
            val builtDeck = Deck(cards = deck.library, commander = deck.commander)
            for (seed in seeds) {
                val init = initializer.initializeGame(
                    GameConfig(
                        players = listOf(
                            PlayerConfig("Seat0", builtDeck, commanderCardName = deck.commander),
                            PlayerConfig("Seat1", builtDeck, commanderCardName = deck.commander),
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
                            mulliganState.mulligansTaken >= 2 || hasCommanderColors(cards)
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
                    rows += DeckHand(
                        deck = deckLabel,
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

        println("=== LILYSPLASH LAND-BASE CHALLENGER V1 PREFLIGHT (commander-aware policy) ===")
        println("control=f315b0907f3f9ae9d61ae2d45de0b778b45a9d9ff86e6ac5c343e4385d5de525")
        println("challenger-land-v1=12e4c15effe06e2e0a1792defbd15f075d3cc9a07fb8c2480f8742d7771fdaa6")
        println("seeds=${seeds.joinToString(",")}")
        rows.forEach { row ->
            println(
                "deck=${row.deck} seed=${row.seed} seat=${row.seat} mulligans=${row.mulligans} kept=${row.cards.size} " +
                    "lands=${row.lands} U=${row.directBlue} G=${row.directGreen} fixers=${row.fixers} " +
                    "hand=${row.cards.joinToString(" | ")}",
            )
        }
        rows.groupBy { it.deck }.forEach { (deckLabel, deckRows) ->
            println(
                "summary deck=$deckLabel samples=${deckRows.size} " +
                    "avgMulligans=${String.format(Locale.ROOT, "%.3f", deckRows.map { it.mulligans }.average())} " +
                    "keep7=${deckRows.count { it.mulligans == 0 }} keep6=${deckRows.count { it.mulligans == 1 }} " +
                    "keep5=${deckRows.count { it.mulligans == 2 }} lowLand=${deckRows.count { it.lands <= 1 }} " +
                    "noDirectU=${deckRows.count { it.directBlue == 0 }} noDirectG=${deckRows.count { it.directGreen == 0 }} " +
                    "noUAccess=${deckRows.count { it.directBlue == 0 && it.cards.none { name -> name in fixing } }} " +
                    "noGAccess=${deckRows.count { it.directGreen == 0 && it.cards.none { name -> name in fixing - "Lórien Revealed" } }}",
            )
        }
        check(rows.size == seeds.size * 2 * 2)
    }

    data class SelectionDeckHand(
        val deck: String,
        val seed: Long,
        val seat: Int,
        val mulligans: Int,
        val cards: List<String>,
        val lands: Int,
        val directBlue: Int,
        val directGreen: Int,
        val fixers: Int,
        val selectors: Int,
        val cheapSelectors: Int,
    )

    // Stage 4 item 1 (cheaper selection and tutors): the submitted control deck against a frozen
    // challenger that swaps Whirlpool Rider (a variance-heavy hand-wheel effect) and Capsize (a
    // mana-intensive repeatable bounce, uncoupled from the deck's actual game plan) for Opt and
    // Preordain -- two of the cheapest, lowest-variance selection spells that exist, both already
    // implemented at common rarity in this engine. No land, ramp/fixing card, Aura, untap/flicker
    // piece, counterspell/protection spell, or win-condition card differs between the two decks, per
    // the plan's one-variable-at-a-time rule; the land/U/G/fixer columns are reported unchanged as a
    // sanity check that the swap really is isolated to the selection suite.
    test("Lilysplash selection challenger v1 preflight").config(
        enabled = System.getProperty("lilysplashSelectionChallenger") == "true",
    ) {
        val control = submittedDeck()
        val challenger = submittedDeck("docs/experiments/lilysplash/challenger-selection-v1.txt")
        checkDeckGuardrails(control, "control")
        checkDeckGuardrails(challenger, "challenger-selection-v1")

        val registry = CardRegistry().apply {
            register(MtgSetCatalog.all.flatMap { it.cards + it.basicLands })
        }
        val processor = ActionProcessor(registry)
        val initializer = GameInitializer(registry)
        val rows = mutableListOf<SelectionDeckHand>()

        for ((deckLabel, deck) in listOf("control" to control, "challenger-selection-v1" to challenger)) {
            val builtDeck = Deck(cards = deck.library, commander = deck.commander)
            for (seed in seeds) {
                val init = initializer.initializeGame(
                    GameConfig(
                        players = listOf(
                            PlayerConfig("Seat0", builtDeck, commanderCardName = deck.commander),
                            PlayerConfig("Seat1", builtDeck, commanderCardName = deck.commander),
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
                            mulliganState.mulligansTaken >= 2 || hasCommanderColors(cards)
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
                    rows += SelectionDeckHand(
                        deck = deckLabel,
                        seed = seed,
                        seat = seat,
                        mulligans = mulligans[seat],
                        cards = hand,
                        lands = landCount,
                        directBlue = hand.count { it in blueSources },
                        directGreen = hand.count { it in greenSources },
                        fixers = hand.count { it in fixing },
                        selectors = hand.count { it in selectionSpells },
                        cheapSelectors = hand.count { it in cheapSelectionSpells },
                    )
                }
            }
        }

        println("=== LILYSPLASH SELECTION CHALLENGER V1 PREFLIGHT (commander-aware policy) ===")
        println("control=f315b0907f3f9ae9d61ae2d45de0b778b45a9d9ff86e6ac5c343e4385d5de525")
        println("challenger-selection-v1=2937001c636529e18109c7bc5649e9a8f78830b9804fd27d336b1a1e50caedcc")
        println("seeds=${seeds.joinToString(",")}")
        rows.forEach { row ->
            println(
                "deck=${row.deck} seed=${row.seed} seat=${row.seat} mulligans=${row.mulligans} kept=${row.cards.size} " +
                    "lands=${row.lands} U=${row.directBlue} G=${row.directGreen} fixers=${row.fixers} " +
                    "selectors=${row.selectors} cheapSelectors=${row.cheapSelectors} " +
                    "hand=${row.cards.joinToString(" | ")}",
            )
        }
        rows.groupBy { it.deck }.forEach { (deckLabel, deckRows) ->
            println(
                "summary deck=$deckLabel samples=${deckRows.size} " +
                    "avgMulligans=${String.format(Locale.ROOT, "%.3f", deckRows.map { it.mulligans }.average())} " +
                    "keep7=${deckRows.count { it.mulligans == 0 }} keep6=${deckRows.count { it.mulligans == 1 }} " +
                    "keep5=${deckRows.count { it.mulligans == 2 }} lowLand=${deckRows.count { it.lands <= 1 }} " +
                    "noDirectU=${deckRows.count { it.directBlue == 0 }} noDirectG=${deckRows.count { it.directGreen == 0 }} " +
                    "avgSelectors=${String.format(Locale.ROOT, "%.3f", deckRows.map { it.selectors }.average())} " +
                    "zeroSelectors=${deckRows.count { it.selectors == 0 }} " +
                    "avgCheapSelectors=${String.format(Locale.ROOT, "%.3f", deckRows.map { it.cheapSelectors }.average())} " +
                    "zeroCheapSelectors=${deckRows.count { it.cheapSelectors == 0 }}",
            )
        }
        check(rows.size == seeds.size * 2 * 2)
    }

    // Stage 4 item 2 (reduced four-mana Aura density): the submitted control deck against a frozen
    // challenger that removes Dawn's Reflection -- the deck's only Aura at converted mana cost 4 (the
    // other twelve Auras in the list all cost 1-3) -- and replaces it with Wayfarer's Bauble, a {1}
    // common artifact that fetches a basic land for {2} and a sacrifice. Both cards are colorless-cost
    // mana development; the swap isolates whether committing 4 mana to a single enchant-land Aura (a
    // real 2-for-1 risk: removal on the enchanted land also strips the Aura) is worth more or less than
    // a cheaper, non-Aura source that can't be blown out that way. No land, selection spell,
    // untap/Freed piece, counterspell/protection spell, or win-condition card differs between the two
    // decks, per the plan's one-variable-at-a-time rule. Wayfarer's Bauble is added to the shared
    // `fixing` set above (it does the same "find the color I'm missing" job as the existing fetchlands),
    // so the land/U/G/fixer columns double as both the sanity check and the one metric this narrow,
    // single-card swap can actually move.
    test("Lilysplash aura challenger v1 preflight").config(
        enabled = System.getProperty("lilysplashAuraChallenger") == "true",
    ) {
        val control = submittedDeck()
        val challenger = submittedDeck("docs/experiments/lilysplash/challenger-aura-v1.txt")
        checkDeckGuardrails(control, "control")
        checkDeckGuardrails(challenger, "challenger-aura-v1")

        val registry = CardRegistry().apply {
            register(MtgSetCatalog.all.flatMap { it.cards + it.basicLands })
        }
        val processor = ActionProcessor(registry)
        val initializer = GameInitializer(registry)
        val rows = mutableListOf<DeckHand>()

        for ((deckLabel, deck) in listOf("control" to control, "challenger-aura-v1" to challenger)) {
            val builtDeck = Deck(cards = deck.library, commander = deck.commander)
            for (seed in seeds) {
                val init = initializer.initializeGame(
                    GameConfig(
                        players = listOf(
                            PlayerConfig("Seat0", builtDeck, commanderCardName = deck.commander),
                            PlayerConfig("Seat1", builtDeck, commanderCardName = deck.commander),
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
                            mulliganState.mulligansTaken >= 2 || hasCommanderColors(cards)
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
                    rows += DeckHand(
                        deck = deckLabel,
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

        println("=== LILYSPLASH AURA CHALLENGER V1 PREFLIGHT (commander-aware policy) ===")
        println("control=f315b0907f3f9ae9d61ae2d45de0b778b45a9d9ff86e6ac5c343e4385d5de525")
        println("challenger-aura-v1=cb7cdb7cd9b52a57ef4e18934462ebee763d5a2bdb7415461ae1b274dd4fc699")
        println("seeds=${seeds.joinToString(",")}")
        rows.forEach { row ->
            println(
                "deck=${row.deck} seed=${row.seed} seat=${row.seat} mulligans=${row.mulligans} kept=${row.cards.size} " +
                    "lands=${row.lands} U=${row.directBlue} G=${row.directGreen} fixers=${row.fixers} " +
                    "hand=${row.cards.joinToString(" | ")}",
            )
        }
        rows.groupBy { it.deck }.forEach { (deckLabel, deckRows) ->
            println(
                "summary deck=$deckLabel samples=${deckRows.size} " +
                    "avgMulligans=${String.format(Locale.ROOT, "%.3f", deckRows.map { it.mulligans }.average())} " +
                    "keep7=${deckRows.count { it.mulligans == 0 }} keep6=${deckRows.count { it.mulligans == 1 }} " +
                    "keep5=${deckRows.count { it.mulligans == 2 }} lowLand=${deckRows.count { it.lands <= 1 }} " +
                    "noDirectU=${deckRows.count { it.directBlue == 0 }} noDirectG=${deckRows.count { it.directGreen == 0 }} " +
                    "avgFixers=${String.format(Locale.ROOT, "%.3f", deckRows.map { it.fixers }.average())} " +
                    "zeroFixers=${deckRows.count { it.fixers == 0 }}",
            )
        }
        check(rows.size == seeds.size * 2 * 2)
    }
})
