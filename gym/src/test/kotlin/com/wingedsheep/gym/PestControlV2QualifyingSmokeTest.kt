package com.wingedsheep.gym

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.ai.engine.EngineAiPlayerController
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.hours

private const val V2_SMOKE_SEED: Long = 0x5045_5354_5632_0001L

/** Nonexperimental V2 qualifying smoke. Its seed is permanently excluded from official vectors. */
class PestControlV2QualifyingSmokeTest : FunSpec({
    val enabled = System.getenv("PEST_V2_SMOKE") == "true"
    test("one production Pest versus SoterX game reaches a legitimate audited terminal").config(
        enabled = enabled,
        timeout = 2.hours,
    ) {
        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
        }
        PestControlPreboardDecks.verifyFrozenIdentities()
        val session = PestControlPreboardSession.qualifyingSmoke(
            registry = registry,
            sourceCommit = System.getenv("PEST_V2_SMOKE_COMMIT") ?: error("PEST_V2_SMOKE_COMMIT required"),
            pestSeat = PestSeat.SEAT_ZERO,
            startingDeck = StartingDeck.PEST_CONTROL,
            recordId = "PEST_CONTROL_V2_QUALIFYING_SMOKE",
            seed = V2_SMOKE_SEED,
        )
        val players = session.environment.playerIds
        val mulligans = players.associateWith { player ->
            EngineAiPlayerController(registry, player, gameStateProvider = { session.environment.state })
        }
        session.driveValidatedLondonMulligans(mulligans)
        val agents = players.associateWith { player -> AIPlayer.create(registry, player, AiProfile.PRODUCTION_CANDIDATE_EXPIRING) }
        var lastTurn = session.environment.turnNumber
        var actionsThisTurn = 0
        while (!session.environment.isTerminal) {
            session.enforceLimits(maxActions = 12_000, maxTurns = 60)
            if (session.environment.turnNumber != lastTurn) { lastTurn = session.environment.turnNumber; actionsThisTurn = 0 }
            if (++actionsThisTurn > 500) session.reject(ProtocolDefectKind.WEDGE, "more than 500 exact-one actions on turn $lastTurn")
            val state = session.environment.state
            val decision = state.pendingDecision
            val acting = decision?.playerId ?: state.priorityPlayerId
                ?: session.reject(ProtocolDefectKind.WEDGE, "no pending decision or priority holder")
            val agent = agents.getValue(acting)
            val action: GameAction = if (decision != null) SubmitDecision(acting, agent.respondToDecision(state, decision))
                else agent.chooseAction(state)
            session.submit(action, fallbackUsed = false)
        }
        val game = session.rawGame()
        (game.terminal?.gameOver == true && game.protocolDefect == null).shouldBeTrue()
        val out = Path.of("").toAbsolutePath().parent.resolve("build/reports/pest-control-v2-smoke")
        Files.createDirectories(out)
        val bundle = PestControlMatchupArtifactCodec.build(game)
        check(PestControlMatchupArtifactCodec.verify(bundle).isEmpty()) { "smoke artifact verification failed" }
        Files.write(out.resolve("smoke-raw.json"), bundle.rawJson)
        Files.write(out.resolve("smoke-raw.json.gz"), bundle.compressed)
        Files.write(out.resolve("smoke-report.md"), bundle.report)
        Files.write(out.resolve("smoke-manifest.json"), bundle.manifest)
        Files.writeString(out.resolve("smoke-summary.txt"), "seed=$V2_SMOKE_SEED\nterminal=${game.terminal}\nprotocolDefect=${game.protocolDefect}\nactions=${game.priorityActions.size}\n")
    }
})
