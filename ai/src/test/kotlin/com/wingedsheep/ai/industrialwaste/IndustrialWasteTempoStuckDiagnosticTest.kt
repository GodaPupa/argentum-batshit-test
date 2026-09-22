package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.arena.ArenaAgent
import com.wingedsheep.ai.arena.TableGameRunner
import com.wingedsheep.ai.arena.TableSetup
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import java.nio.file.Files
import java.nio.file.Path

class IndustrialWasteTempoStuckDiagnosticTest : FunSpec({
    test("replay exact Tempo A stuck game with named action trace").config(
        enabled = System.getenv("IW_TEMPO_STUCK_DIAG") == "true",
    ) {
        val repository = tempoDiagRepositoryRoot()
        val root = repository.resolve("industrial-waste")
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }
        val tempo = tempoDiagParseMain(root.resolve("challengers/tempo-a.dck"))
        val opponent = tempoDiagParseMain(root.resolve("gauntlet/grixis-affinity-carlos-dc-2026-09-12.dck"))
        val industrialBase = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
        val industrial = ArenaAgent(
            "industrial-waste-tempo-a-policy",
            industrialBase.copy(
                id = "industrial-waste-tempo-a-policy",
                advisorModules = industrialBase.advisorModules + IndustrialWasteAdvisorModule,
                considerAdvisedManaAbilities = true,
            ),
        )
        val grixisBase = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
        val grixis = ArenaAgent(
            "grixis-gate-6-readiness",
            grixisBase.copy(
                id = "grixis-gate-6-readiness",
                advisorModules = grixisBase.advisorModules + IndustrialWasteGrixisAdvisorModule,
            ),
        )

        val observer = IndustrialWasteGoldfishObserver(registry, 0)
        val game = TableGameRunner.play(
            registry = registry,
            setup = TableSetup.HEADS_UP,
            agents = listOf(industrial, grixis),
            decks = listOf(tempo, opponent),
            seed = 6948333721413080953L,
            groupId = 1,
            rotation = 0,
            maxTurns = 16,
            maxActions = 1_200,
            trainingObserver = observer,
            skipMulligans = false,
            recordActionStream = true,
        )
        println("TEMPO_STUCK_RESULT completed=${game.completed} winner=${game.winnerSeat} " +
            "turns=${game.turns} actions=${game.actions} drawReason=${game.drawReason} " +
            "exception=${game.exception} illegal=${game.illegalActions} metrics=${observer.snapshot()}")
        System.out.flush()
    }
})

private fun tempoDiagRepositoryRoot(): Path {
    var candidate: Path? = Path.of("").toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve("industrial-waste"))) return candidate
        candidate = candidate.parent
    }
    error("Cannot locate repository root")
}

private fun tempoDiagParseMain(path: Path): Deck {
    var inMain = false
    val cards = buildList {
        Files.readAllLines(path).forEach { raw ->
            val line = raw.trim()
            when {
                line == "[main]" -> inMain = true
                line.startsWith("[") -> inMain = false
                inMain && line.isNotEmpty() -> {
                    val split = line.indexOf(' ')
                    val count = line.substring(0, split).toInt()
                    val name = line.substring(split + 1)
                    repeat(count) { add(name) }
                }
            }
        }
    }
    return Deck(cards)
}
