package com.wingedsheep.gym

import com.wingedsheep.ai.engine.EngineAiPlayerController
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.Concede
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.TakeMulligan
import com.wingedsheep.engine.core.BottomCards
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.combat.BlockersDeclaredThisCombatComponent
import com.wingedsheep.gym.matchup.MatchupEnvironmentIdentity
import com.wingedsheep.gym.matchup.MatchupProvenance
import com.wingedsheep.gym.matchup.MatchupProtocolRejected
import com.wingedsheep.gym.matchup.PEST_CONTROL_V10_HASH
import com.wingedsheep.gym.matchup.PEST_CONTROL_V10_SIDEBOARD_HASH
import com.wingedsheep.gym.matchup.PEST_CONTROL_V10_75_HASH
import com.wingedsheep.gym.matchup.PestControlPreboardDecks
import com.wingedsheep.gym.matchup.PestControlPreboardSession
import com.wingedsheep.gym.matchup.PestSeat
import com.wingedsheep.gym.matchup.ProtocolDefectKind
import com.wingedsheep.gym.matchup.SOTERX_MONO_RED_75_HASH
import com.wingedsheep.gym.matchup.SOTERX_MONO_RED_MAIN_HASH
import com.wingedsheep.gym.matchup.SOTERX_MONO_RED_SIDEBOARD_HASH
import com.wingedsheep.gym.matchup.StartingDeck
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe

private const val GATE3_HEAD = "b6fc0fb6fc31efa2148e3e3174782d267656e44c"

class PestControlPreboardProtocolTest : ScenarioTestBase() {
  init {
    val registry = CardRegistry().apply {
        register(PredefinedTokens.allTokens)
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }

    test("frozen preboard identities instantiate exactly two 20-life maindecks and no sideboards") {
        PestControlPreboardDecks.verifyFrozenIdentities()
        PestControlPreboardDecks.pestMainCounts.values.sum() shouldBe 60
        PestControlPreboardDecks.pestSideboardCounts.values.sum() shouldBe 15
        (PestControlPreboardDecks.pestMainCounts.values.sum() +
            PestControlPreboardDecks.pestSideboardCounts.values.sum()) shouldBe 75
        PestControlPreboardDecks.monoRedMainCounts.values.sum() shouldBe 60
        PestControlPreboardDecks.monoRedSideboardCounts.values.sum() shouldBe 15

        val session = PestControlPreboardSession.fixture(
            registry, GATE3_HEAD, PestSeat.SEAT_ZERO, StartingDeck.PEST_CONTROL,
            "NONEXPERIMENTAL_GATE4_SEAT_ZERO_PEST_PLAYS",
        )
        val game = session.rawGame()
        game.provenance.pestControlMainSha256 shouldBe PEST_CONTROL_V10_HASH
        game.provenance.pestControlSideboardSha256 shouldBe PEST_CONTROL_V10_SIDEBOARD_HASH
        game.provenance.pestControlComplete75Sha256 shouldBe PEST_CONTROL_V10_75_HASH
        game.provenance.monoRedMainSha256 shouldBe SOTERX_MONO_RED_MAIN_HASH
        game.provenance.monoRedSideboardSha256 shouldBe SOTERX_MONO_RED_SIDEBOARD_HASH
        game.provenance.monoRedComplete75Sha256 shouldBe SOTERX_MONO_RED_75_HASH
        game.provenance.sideboardsInstantiated.shouldBeFalse()
        game.openingZones.map { it.handCount } shouldBe listOf(7, 7)
        game.openingZones.map { it.libraryCount } shouldBe listOf(53, 53)
        game.openingZones.map { it.sideboardCount } shouldBe listOf(0, 0)
        session.environment.playerIds.forEach { session.environment.state.lifeTotal(it).shouldBeExactly(20) }
        session.environment.state.turnOrder.first() shouldBe session.environment.playerIds[0]
    }

    test("Pest seat and starting player are independently parameterized") {
        val session = PestControlPreboardSession.fixture(
            registry, GATE3_HEAD, PestSeat.SEAT_ONE, StartingDeck.PEST_CONTROL,
            "NONEXPERIMENTAL_GATE4_SEAT_ONE_PEST_PLAYS",
        )
        val game = session.rawGame()
        game.openingZones[1].deckIdentity shouldBe "PEST_CONTROL_V10"
        session.environment.state.turnOrder.first() shouldBe session.environment.playerIds[1]
    }

    test("the player observation hides the opponent hand and library order from selection") {
        val session = PestControlPreboardSession.fixture(
            registry, GATE3_HEAD, PestSeat.SEAT_ZERO, StartingDeck.PEST_CONTROL,
            "NONEXPERIMENTAL_GATE4_VISIBILITY",
        )
        val context = session.nextDecision()
        val opponent = session.environment.playerIds.single { it != context.actingPlayerId }
        val hiddenHand = context.visibleObservation.zones.single { it.ownerId == opponent && it.zoneType == Zone.HAND }
        val hiddenLibrary = context.visibleObservation.zones.single { it.ownerId == opponent && it.zoneType == Zone.LIBRARY }
        hiddenHand.hidden.shouldBeTrue()
        hiddenHand.cards.shouldBeEmpty()
        hiddenLibrary.hidden.shouldBeTrue()
        hiddenLibrary.cards.shouldBeEmpty()
        session.rawGame().openingZones.single { it.playerId == opponent }.libraryOrderedCardNames.size shouldBe 53
    }

    test("legal-action and state digests are stable for an unchanged decision") {
        val session = PestControlPreboardSession.fixture(
            registry, GATE3_HEAD, PestSeat.SEAT_ZERO, StartingDeck.MONO_RED_MADNESS,
            "NONEXPERIMENTAL_GATE4_DIGEST",
        )
        val first = session.nextDecision()
        val second = session.nextDecision()
        first.legalActionSha256 shouldBe second.legalActionSha256
        first.visibleObservation.stateDigest shouldBe second.visibleObservation.stateDigest
    }

    test("validated London mulligan and bottoming policies are recorded through exact-one actions") {
        val session = PestControlPreboardSession.fixture(
            registry, GATE3_HEAD, PestSeat.SEAT_ZERO, StartingDeck.PEST_CONTROL,
            "NONEXPERIMENTAL_GATE4_LONDON",
        )
        val controllers = session.environment.playerIds.associateWith { player ->
            EngineAiPlayerController(registry, player, gameStateProvider = { session.environment.state })
        }
        session.driveValidatedLondonMulligans(controllers)
        val record = session.rawGame()
        record.mulligans.any { it.action == "KEEP" }.shouldBeTrue()
        record.mulligans.all { it.action in setOf("KEEP", "MULLIGAN", "BOTTOM") }.shouldBeTrue()
        session.environment.playerIds.sumOf { session.environment.state.getHand(it).size } shouldBe
            (14 - record.mulligans.filter { it.action == "MULLIGAN" }.size)
    }

    test("a deterministic London mulligan records the kept hand and exact bottomed card") {
        val session = PestControlPreboardSession.fixture(
            registry, GATE3_HEAD, PestSeat.SEAT_ZERO, StartingDeck.PEST_CONTROL,
            "NONEXPERIMENTAL_GATE4_LONDON_BOTTOM_RECORD",
        )
        val first = session.environment.state.turnOrder[0]
        val second = session.environment.state.turnOrder[1]
        session.submit(TakeMulligan(first))
        session.submit(KeepHand(first))
        session.submit(KeepHand(second))
        val bottom = session.environment.state.getHand(first).first()
        val bottomName = session.environment.state.getEntity(bottom)
            ?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()!!.name
        session.submit(BottomCards(first, listOf(bottom)))
        val audits = session.rawGame().mulligans
        audits.map { it.action } shouldBe listOf("MULLIGAN", "KEEP", "KEEP", "BOTTOM")
        audits.last().bottomedCards shouldBe listOf(bottomName)
        session.environment.state.getHand(first).size.shouldBeExactly(6)
    }

    test("one submission records one authoritative transition and no automatic priority pass") {
        val game = scenario()
            .withPlayers("Pest", "Red")
            .withCardInHand(2, "Lightning Bolt")
            .withLandsOnBattlefield(2, "Mountain", 1)
            .withActivePlayer(2)
            .build()
        val environment = GameEnvironment.create(registry).also {
            it.restore(game.state, listOf(game.player1Id, game.player2Id))
        }
        val session = PestControlPreboardSession.fromEnvironment(
            registry,
            provenance(PestSeat.SEAT_ZERO, StartingDeck.MONO_RED_MADNESS),
            "NONEXPERIMENTAL_GATE4_EXACT_ONE",
            environment,
        )
        val bolt = environment.state.getHand(game.player2Id).single()

        session.submit(CastSpell(game.player2Id, bolt, targets = listOf(ChosenTarget.Player(game.player1Id))))
        environment.state.stack shouldBe listOf(bolt)
        environment.state.priorityPlayerId shouldBe game.player2Id
        session.rawGame().priorityActions.size.shouldBeExactly(1)

        session.submit(PassPriority(game.player2Id))
        environment.state.stack shouldBe listOf(bolt)
        environment.state.priorityPlayerId shouldBe game.player1Id
        session.rawGame().priorityActions.size.shouldBeExactly(2)
    }

    test("normal noncombat terminal is recorded from an exact submitted action") {
        val game = scenario().withPlayers().build()
        val environment = GameEnvironment.create(registry).also {
            it.restore(game.state, listOf(game.player1Id, game.player2Id))
        }
        val session = PestControlPreboardSession.fromEnvironment(
            registry, provenance(), "NONEXPERIMENTAL_GATE4_NONCOMBAT_TERMINAL", environment,
        )
        session.submit(Concede(game.player1Id))
        session.rawGame().terminal?.gameOver.shouldBeTrue()
        session.rawGame().terminal?.winnerId shouldBe game.player2Id
    }

    test("combat response windows remain explicit through a normal combat terminal") {
        val game = scenario()
            .withPlayers()
            .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
            .withLifeTotal(2, 2)
            .withActivePlayer(1)
            .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            .build()
        val attacker = game.state.getBattlefield(game.player1Id).single()
        val combatState = game.state.copy(priorityPlayerId = game.player1Id)
        val environment = GameEnvironment.create(registry).also {
            it.restore(combatState, listOf(game.player1Id, game.player2Id))
        }
        val session = PestControlPreboardSession.fromEnvironment(
            registry, provenance(), "NONEXPERIMENTAL_GATE4_COMBAT_TERMINAL", environment,
        )
        session.submit(DeclareAttackers(game.player1Id, mapOf(attacker to game.player2Id)))
        var guard = 0
        while (!environment.isTerminal && guard++ < 20) {
            val actor = environment.agentToAct ?: error("combat fixture lost priority")
            val blockersDeclared = environment.state.getEntity(game.player2Id)
                ?.has<BlockersDeclaredThisCombatComponent>() == true
            val action = if (
                environment.state.step == Step.DECLARE_BLOCKERS && actor == game.player2Id &&
                environment.state.pendingDecision == null && !blockersDeclared
            ) DeclareBlockers(actor, emptyMap()) else PassPriority(actor)
            session.submit(action)
        }
        withClue(
            session.rawGame().priorityActions.joinToString("\n") {
                "${it.sequence} ${it.phase}/${it.step} actor=${it.actingPlayerId} action=${it.selectedAction}"
            }
        ) { environment.isTerminal.shouldBeTrue() }
        session.rawGame().terminal?.winnerId shouldBe game.player1Id
        session.rawGame().indexedTelemetry.combat.isNotEmpty().shouldBeTrue()
        (guard < 20).shouldBeTrue()
    }

    test("rejections and limits are protocol defects rather than game results") {
        fun fresh(suffix: String) = PestControlPreboardSession.fixture(
            registry, GATE3_HEAD, PestSeat.SEAT_ZERO, StartingDeck.PEST_CONTROL,
            "NONEXPERIMENTAL_GATE4_$suffix",
        )
        val cases = listOf(
            ProtocolDefectKind.ACTION_LIMIT to { session: PestControlPreboardSession ->
                session.enforceLimits(maxActions = 0, maxTurns = 100)
            },
            ProtocolDefectKind.TURN_LIMIT to { session: PestControlPreboardSession ->
                session.enforceLimits(maxActions = 100, maxTurns = -1)
            },
            ProtocolDefectKind.WEDGE to { session: PestControlPreboardSession ->
                session.reject(ProtocolDefectKind.WEDGE, "deterministic repeated state")
            },
            ProtocolDefectKind.WATCHDOG_LIMIT to { session: PestControlPreboardSession ->
                session.reject(ProtocolDefectKind.WATCHDOG_LIMIT, "deterministic watchdog")
            },
        )
        cases.forEach { (kind, operation) ->
            val session = fresh(kind.name)
            val error = shouldThrow<MatchupProtocolRejected> { operation(session) }
            error.defect.kind shouldBe kind
            session.rawGame().terminal shouldBe null
            session.rawGame().protocolDefect?.kind shouldBe kind
        }

        val fallback = fresh("ILLEGAL_FALLBACK")
        val actor = fallback.nextDecision().actingPlayerId
        shouldThrow<MatchupProtocolRejected> {
            fallback.submit(PassPriority(actor), fallbackUsed = true)
        }.defect.kind shouldBe ProtocolDefectKind.ILLEGAL_FALLBACK
    }
  }
}

private fun provenance(
    pestSeat: PestSeat = PestSeat.SEAT_ZERO,
    startingDeck: StartingDeck = StartingDeck.PEST_CONTROL,
) = MatchupProvenance(
    sourceCommit = GATE3_HEAD,
    pestSeat = pestSeat,
    startingDeck = startingDeck,
    environment = MatchupEnvironmentIdentity.current(),
)
