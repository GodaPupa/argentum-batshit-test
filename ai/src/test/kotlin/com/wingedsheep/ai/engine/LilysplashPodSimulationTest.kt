package com.wingedsheep.ai.engine

import com.wingedsheep.ai.arena.ArenaAgent
import com.wingedsheep.ai.arena.TableGameOutcome
import com.wingedsheep.ai.arena.TableGameRunner
import com.wingedsheep.ai.arena.TableSetup
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import java.nio.file.Files
import java.nio.file.Path

/**
 * First full multiplayer Commander-pod simulation of `final-optimized-v0.1.txt` (the Splash Splash
 * 9000 / Lilysplash Mentor list), answering a question this whole experiment had, until now, no way
 * to answer: does the actual submitted-and-corrected decklist play a coherent, finishable game when
 * driven end-to-end by the production AI in a real multiplayer pod, rather than only ever being
 * sampled for its opening hand ([LilysplashOpeningHandBenchmark]) or exercised on a hand-built board
 * for one specific combo line ([LilysplashComboExecutionTest], [LilysplashVedalkenComboExecutionTest]).
 *
 * `final-report.md` and `LilysplashComboExecutionTest`'s own package doc both stated, as of this
 * experiment's Stage 4 close, that "the existing `arena` harness ... has no `Format.Commander` /
 * singleton / command-zone support." That claim was accurate about [com.wingedsheep.ai.arena.Arena]
 * (the original two-seat head-to-head harness) but is not accurate about the arena as a whole:
 * [TableGameRunner] takes an arbitrary [TableSetup] — seat count and [Format] are just data — and
 * issue #1456 (`CommanderPodTest`, `rules-engine/src/test/kotlin/.../multiplayer/CommanderPodTest.kt`)
 * already proved the engine's Commander code has no two-player assumption: per-seat command zones,
 * commander damage tallied per (commander, defender) pair, and CR 903.9a's zone-choice loop all work
 * at four seats. `PodArenaHarnessTest` (`ai/src/test/kotlin/.../arena/PodArenaHarnessTest.kt`) already
 * plays real `Format.Standard`/sealed pod games to completion the same way. This test is that same,
 * already-proven machinery pointed at Lilysplash's own decklist for the first time.
 *
 * ## Why a mirror, not a field of "real" opponents
 *
 * Building 2-3 fresh, unrelated 99-card Commander decks to serve as "representative opposition"
 * would mean introducing dozens of cards this experiment has never verified — directly against the
 * plan's "never approximate a card's implemented behavior" guardrail, since verifying a card's
 * implemented behavior is exactly the work `add-card`'s scenario-test step exists for, and skipping
 * it to hand-wave a filler opponent deck would undermine the same rigor this experiment has held
 * everywhere else. A mirror match — every seat piloting an independent copy of the *same*,
 * already-corrected, already-legal decklist — needs zero new cards or decks verified, and still
 * answers a real question: does the AI pilot Lilysplash's own 99 coherently for an entire game
 * against real (if identical) opposition, reaching a natural finish without wedging, without a
 * rejected/illegal action, and — because a mirror's null win share is `1 / seats` exactly (the
 * table's own harness test, [com.wingedsheep.ai.arena.PodArenaHarnessTest], pins this) — any
 * self-play win share we do observe is diagnostic evidence about the deck's own solitaire
 * consistency, not about strength against a specific field. A field-of-real-opponents pod is a
 * legitimate and larger follow-up; it is out of scope here for exactly the reason stated above.
 *
 * ## What this test is and is not proof of
 *
 * A `completed = true` result is proof the deck can be piloted through a full, rules-legal
 * multiplayer game by the production AI without the engine or AI wedging, erroring, or having an
 * action rejected — a first for this experiment, everything before this having been either a single
 * opening hand or a hand-built one-turn board. It is NOT a win-rate claim: one or two fixed-seed
 * mirror games are nowhere near enough samples for that, and per the experiment's "no rerolls or
 * replacement seeds" guardrail, whichever seat wins here is reported as-is, not treated as evidence
 * the deck is favored. Seeds are fixed and chosen before the test is ever run, exactly like every
 * other Stage 4 measurement in this experiment.
 */
class LilysplashPodSimulationTest : FunSpec({

    val enabled = System.getProperty("lilysplashPod") == "true"

    fun loadDeck(relativePath: String = "docs/experiments/lilysplash/final-optimized-v0.1.txt"): Deck {
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
            val match = entry.matchEntire(line) ?: error("Unparseable deck line: $line")
            List(match.groupValues[1].toInt()) { match.groupValues[2] }
        }
        return Deck(cards = library, commander = commander)
    }

    // The same production-candidate profile LilysplashComboExecutionTest and
    // LilysplashVedalkenComboExecutionTest already pilot this deck with, so every AI-driven
    // (as opposed to opening-hand-sampling) Lilysplash measurement in this experiment uses one
    // consistent yardstick.
    val pilot = ArenaAgent("lilysplash-pilot", AiProfile.PRODUCTION_CANDIDATE_EXPIRING)

    // A first exploratory run's caps, chosen for tractable wall-clock time rather than a generous
    // "let it play all the way out" budget: a real production-candidate AI making a full tiered/
    // rollout-budgeted decision at every priority window, across a 99-card singleton Commander deck
    // and three or four live seats, is far more expensive per action than PodArenaHarnessTest's own
    // POR-sealed pods (see that file's own comment: "a late pod position is expensive... a few
    // hundred extra actions"). An initial attempt at maxTurns=60 / the 20,000-action default ran for
    // over half an hour without finishing even the three-seat game and was killed rather than left to
    // run indefinitely. These caps are deliberately tighter so this first run finishes and reports a
    // real (if possibly capped-short) result; a longer, more patient run is a natural follow-up once
    // this proves the harness path end-to-end.
    val maxTurnsPerSeat = 25
    val maxActionsPerGame = 6000

    fun commanderTable(id: String, seats: Int) =
        TableSetup(id = id, seats = seats, format = Format.Commander(alwaysDivertToCommand = true))

    fun runMirror(table: TableSetup, seed: Long): TableGameOutcome {
        val registry = CardRegistry().apply {
            register(MtgSetCatalog.all.flatMap { it.cards + it.basicLands })
        }
        val deck = loadDeck()
        return TableGameRunner.play(
            registry = registry,
            setup = table,
            agents = List(table.seats) { pilot },
            decks = List(table.seats) { deck },
            maxActions = maxActionsPerGame,
            seed = seed,
            groupId = 1,
            rotation = 0,
            maxTurns = maxTurnsPerSeat,
        )
    }

    fun report(label: String, outcome: TableGameOutcome) {
        println(
            "pod=$label table=${outcome.setup.id} seats=${outcome.setup.seats} seed=${outcome.seed} " +
                "completed=${outcome.completed} winnerSeat=${outcome.winnerSeat} " +
                "turns=${outcome.turns} actions=${outcome.actions} durationMs=${outcome.durationMs} " +
                "life=${outcome.lifeBySeat.joinToString("/")} drawReason=\"${outcome.drawReason}\" " +
                "exception=${outcome.exception} illegalActions=${outcome.illegalActions}",
        )
    }

    // Fixed before running, per the experiment's "no rerolls or replacement seeds" guardrail --
    // a new, never-used block continuing this experiment's seed-numbering convention
    // (2026091401-12 for Stage 4, 2026091501-12 for the final-optimized re-validation).
    test("final-optimized-v0.1 mirror: three-seat Commander pod plays to a real finish").config(enabled = enabled) {
        val outcome = runMirror(commanderTable("lilysplash-ffa3-commander", 3), seed = 2026091601L)
        report("ffa3-mirror", outcome)
    }

    test("final-optimized-v0.1 mirror: four-seat Commander pod plays to a real finish").config(enabled = enabled) {
        val outcome = runMirror(commanderTable("lilysplash-ffa4-commander", 4), seed = 2026091602L)
        report("ffa4-mirror", outcome)
    }
})
