package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.TypecycleCard
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.LifeGainedThisTurnComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.doubles.shouldBeNegative
import io.kotest.matchers.doubles.shouldBePositive
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Deterministic decision probes for Project Pest Control's rules-complete card pool.
 *
 * These are positions, not deck fixtures or gameplay samples. They use the live production-candidate
 * policy, a fixed game RNG, and the real rules engine. The frozen control list remains documentation
 * owned by the laboratory and is deliberately not materialized here.
 */
class PestControlAgentDecisionTest : ScenarioTestBase() {

    private val profile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING

    private fun seeded() = scenario().withPlayers().withRngSeed(0x0E57_C017L)

    private fun ai(game: TestGame, player: EntityId = game.player1Id) =
        AIPlayer.create(cardRegistry, player, profile)

    /** Capture the production decision's own ranking so a failed strategic probe is diagnostic. */
    private fun chooseWithReport(game: TestGame): Pair<GameAction, String> {
        val (action, captured) = chooseWithInsights(game)
        val report = captured.lastOrNull()?.options.orEmpty().joinToString(" | ") { option ->
            "${option.label}: score=${option.score}, raw=${option.rawScore}, note=${option.note}"
        }.ifEmpty { "no Strategist insight was captured" }
        return action to report
    }

    private fun chooseWithInsights(game: TestGame): Pair<GameAction, List<com.wingedsheep.ai.insight.AiDecisionInsight>> {
        val captured = mutableListOf<com.wingedsheep.ai.insight.AiDecisionInsight>()
        val action = AIPlayer.create(
            cardRegistry, game.player1Id, profile,
            insightSink = { _, insight -> captured += insight },
        ).chooseAction(game.state)
        return action to captured
    }

    private fun cardName(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name

    private fun sourceName(game: TestGame, action: Any): String? = when (action) {
        is CastSpell -> cardName(game, action.cardId)
        is ActivateAbility -> cardName(game, action.sourceId)
        is TypecycleCard -> cardName(game, action.cardId)
        else -> null
    }

    private fun chosenPermanent(action: CastSpell): EntityId? =
        action.targets.filterIsInstance<ChosenTarget.Permanent>().singleOrNull()?.entityId

    private fun TestGame.markLifeGainedThisTurn() {
        state = state.updateEntity(player1Id) { it.with(LifeGainedThisTurnComponent) }
    }

    /** Deterministic stack setup only; no laboratory seed or gameplay runner is involved. */
    private fun TestGame.castPendingWeather() {
        castSpell(1, "Weather the Storm").error shouldBe null
        state.stack.isNotEmpty().shouldBeTrue()
    }

    private fun TestGame.resolveStackWith(player: AIPlayer) {
        resolveStack()
        while (state.pendingDecision != null) {
            val decision = requireNotNull(state.pendingDecision)
            submitDecision(player.respondToDecision(state, decision)).error shouldBe null
            resolveStack()
        }
    }

    init {
        test("deploys Essence Warden before the creature whose entry supplies value") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Forest", 2)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Essence Warden")
                .withCardInHand(1, "Carrier Thrall")
                .withCardOnBattlefield(1, "Blood Researcher")
                .build()

            val (chosen, report) = chooseWithReport(game)
            val action = withClue(report) { chosen.shouldBeInstanceOf<CastSpell>() }
            withClue(report) { sourceName(game, action) shouldBe "Essence Warden" }
        }

        test("does not make Essence Warden categorical over a materially stronger play") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Forest", 6)
                .withCardInHand(1, "Essence Warden")
                .withCardInHand(1, "Craw Wurm")
                .withLifeTotal(2, 5)
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Craw Wurm"
        }

        test("deploys a supported Blood Researcher ahead of an unsupported body") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Forest", 2)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Blood Researcher")
                .withCardInHand(1, "Hill Giant")
                .withCardInHand(1, "Weather the Storm")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Blood Researcher"
        }

        test("deploys a supported Pest Mascot ahead of an unsupported body") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Forest", 2)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Pest Mascot")
                .withCardInHand(1, "Hill Giant")
                .withCardInHand(1, "Weather the Storm")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Pest Mascot"
        }

        test("casts Weather immediately when the life is required to survive") {
            val game = seeded()
                .withLifeTotal(1, 2)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInHand(1, "Weather the Storm")
                .withCardInHand(1, "Grizzly Bears")
                .withCardOnBattlefield(2, "Craw Wurm")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Weather the Storm"
        }

        test("sequences a useful spell before Weather when the extra copy improves two payoffs") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Forest", 3)
                .withCardInHand(1, "Essence Warden")
                .withCardInHand(1, "Weather the Storm")
                .withCardOnBattlefield(1, "Blood Researcher")
                .withCardOnBattlefield(1, "Pest Mascot")
                .build()

            val (chosen, report) = chooseWithReport(game)
            val action = withClue(report) { chosen.shouldBeInstanceOf<CastSpell>() }
            withClue(report) { sourceName(game, action) shouldBe "Essence Warden" }
        }

        test("Game 16 reconstruction plays a land to unlock a productive spell before Weather") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Forest", 2)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Carrier Thrall")
                .withCardInHand(1, "Weather the Storm")
                .withCardOnBattlefield(1, "Pest Mascot")
                .build()
            val player = ai(game)

            val first = player.chooseAction(game.state)
            val land = withClue("first action=${sourceName(game, first) ?: first::class.simpleName}") {
                first.shouldBeInstanceOf<PlayLand>()
            }
            cardName(game, land.cardId) shouldBe "Swamp"
            game.execute(land).error shouldBe null

            val setup = player.chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, setup) shouldBe "Carrier Thrall"
            game.execute(setup).error shouldBe null
            game.resolveStack()

            val weatherChoice = player.chooseAction(game.state)
            val weather = withClue("after land=${sourceName(game, weatherChoice) ?: weatherChoice}") {
                weatherChoice.shouldBeInstanceOf<CastSpell>()
            }
            sourceName(game, weather) shouldBe "Weather the Storm"
            game.state.spellsCastThisTurn shouldBe 1
        }

        test("Game 18 reconstruction deploys a land-unlocked payoff before lifegain") {
            val game = seeded()
                .withTurnNumber(6)
                .withLifeTotal(1, 22)
                .withLandsOnBattlefield(1, "Forest", 1)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Blood Researcher")
                .withCardInHand(1, "Weather the Storm")
                .withCardInHand(1, "Chainer's Edict")
                .withCardInHand(1, "Chainer's Edict")
                .withCardOnBattlefield(1, "Essence Warden")
                .withCardOnBattlefield(1, "Carrier Thrall")
                .withCardOnBattlefield(1, "Follow the Lumarets")
                .withCardOnBattlefield(1, "Blood Researcher")
                .build()
            game.state = game.state.copy(phase = Phase.POSTCOMBAT_MAIN, step = Step.POSTCOMBAT_MAIN)
            val player = ai(game)

            val captured = mutableListOf<com.wingedsheep.ai.insight.AiDecisionInsight>()
            val traced = AIPlayer.create(
                cardRegistry, game.player1Id, profile,
                insightSink = { _, insight -> captured += insight },
            )
            val first = traced.chooseAction(game.state)
            val land = withClue("first action=${sourceName(game, first) ?: first::class.simpleName}") {
                first.shouldBeInstanceOf<PlayLand>()
            }
            withClue(captured.lastOrNull()) {
                captured.last().options.single { it.label.contains("Weather the Storm") }.note
                    .orEmpty() shouldContain "land play unlocks a superior same-turn line"
            }
            cardName(game, land.cardId) shouldBe "Swamp"
            game.execute(land).error shouldBe null

            val setup = player.chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, setup) shouldBe "Blood Researcher"
            game.execute(setup).error shouldBe null
            game.resolveStack()

            val weather = player.chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, weather) shouldBe "Weather the Storm"
        }

        test("semantically equivalent focal copies receive the same land-unlocked deferral") {
            val game = seeded()
                .withTurnNumber(6)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Blood Researcher")
                .withCardInHand(1, "Weather the Storm")
                .withCardInHand(1, "Weather the Storm")
                .withCardOnBattlefield(1, "Blood Researcher")
                .build()
            game.state = game.state.copy(phase = Phase.POSTCOMBAT_MAIN, step = Step.POSTCOMBAT_MAIN)

            val (chosen, insights) = chooseWithInsights(game)
            chosen.shouldBeInstanceOf<PlayLand>()
            chosen.cardId shouldBe game.findCardsInHand(1, "Swamp").single()
            val weatherOptions = insights.last().options.filter { it.label.contains("Weather the Storm") }
            weatherOptions.size shouldBe 2
            weatherOptions.forEach { option ->
                withClue(option) {
                    option.note.orEmpty() shouldContain
                        "land play unlocks a superior same-turn line"
                }
            }
        }

        test("land-unlocked damage payoff is deployed before the event it converts") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Forest", 2)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Marauding Blight-Priest")
                .withCardInHand(1, "Weather the Storm")
                .build()
            val player = ai(game)

            val land = player.chooseAction(game.state).shouldBeInstanceOf<PlayLand>()
            game.execute(land).error shouldBe null
            val payoff = player.chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, payoff) shouldBe "Marauding Blight-Priest"
        }

        test("payoff deployment is not preferred when it consumes mana required by lifegain") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Forest", 1)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Blood Researcher")
                .withCardInHand(1, "Weather the Storm")
                .withCardOnBattlefield(1, "Blood Researcher")
                .build()

            val (_, insights) = chooseWithInsights(game)
            withClue(insights.lastOrNull()) {
                insights.last().options.single { it.label.contains("Weather the Storm") }.note
                    .orEmpty().contains("land play unlocks a superior same-turn line") shouldBe false
            }
        }

        test("land-unlocked Storm setup does not delay lifegain required for survival") {
            val game = seeded()
                .withLifeTotal(1, 2)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Carrier Thrall")
                .withCardInHand(1, "Weather the Storm")
                .withCardOnBattlefield(1, "Pest Mascot")
                .withCardOnBattlefield(2, "Craw Wurm")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Weather the Storm"
        }

        test("land drop does not turn a strategically null spell into Storm setup") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Forest", 2)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Duress")
                .withCardInHand(1, "Weather the Storm")
                .withCardOnBattlefield(1, "Pest Mascot")
                .build()

            val chosen = ai(game).chooseAction(game.state)
            (chosen is CastSpell && sourceName(game, chosen) == "Duress").shouldBeFalse()
        }

        test("land-unlocked setup is rejected when its strategic cost exceeds the Storm benefit") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Forest", 1)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Bone Shards")
                .withCardInHand(1, "Weather the Storm")
                .withCardOnBattlefield(1, "Pest Mascot")
                .withCardOnBattlefield(2, "Mons's Goblin Raiders")
                .build()

            val chosen = ai(game).chooseAction(game.state)
            (chosen is CastSpell && sourceName(game, chosen) == "Bone Shards").shouldBeFalse()
        }

        test("does not spend an irrelevant spell only to increase Storm") {
            val game = seeded()
                .withLifeTotal(1, 2)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Weather the Storm")
                .withCardInHand(1, "Duress")
                .withCardOnBattlefield(2, "Craw Wurm")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Weather the Storm"
        }

        test("holds pure lifegain with no pressure or payoff even at low life") {
            val game = seeded()
                .withLifeTotal(1, 2)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInHand(1, "Weather the Storm")
                .build()

            val (chosen, report) = chooseWithReport(game)
            withClue(report) { chosen.shouldBeInstanceOf<PassPriority>() }
        }

        test("holds redundant Storm lifegain when no event payoff or threat exists") {
            val game = seeded()
                .withLifeTotal(1, 20)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInHand(1, "Weather the Storm")
                .build()
            game.state = game.state.copy(
                spellsCastThisTurn = 1,
                playerSpellsCastThisTurn = mapOf(game.player1Id to 1),
            )

            val (chosen, report) = chooseWithReport(game)
            withClue(report) { chosen.shouldBeInstanceOf<PassPriority>() }
        }

        test("casts pure lifegain when a visible payoff converts the event") {
            val game = seeded()
                .withLifeTotal(1, 20)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInHand(1, "Weather the Storm")
                .withCardOnBattlefield(1, "Blood Researcher")
                .build()

            val (chosen, report) = chooseWithReport(game)
            val action = withClue(report) { chosen.shouldBeInstanceOf<CastSpell>() }
            withClue(report) { sourceName(game, action) shouldBe "Weather the Storm" }
        }

        test("casts pure lifegain when it unlocks an executable enhanced follow-up") {
            val game = seeded()
                .withLifeTotal(1, 20)
                .withLandsOnBattlefield(1, "Forest", 4)
                .withCardInHand(1, "Weather the Storm")
                .withCardInHand(1, "Follow the Lumarets")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Blood Researcher")
                .build()

            val (chosen, report) = chooseWithReport(game)
            val action = withClue(report) { chosen.shouldBeInstanceOf<CastSpell>() }
            withClue(report) { sourceName(game, action) shouldBe "Weather the Storm" }
        }

        test("commits an enabling life event into the materially enhanced follow-up") {
            val game = seeded()
                .withLifeTotal(1, 29)
                .withLandsOnBattlefield(1, "Forest", 4)
                .withCardInHand(1, "Weather the Storm")
                .withCardInHand(1, "Follow the Lumarets")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Swamp")
                .withCardInLibrary(1, "Blood Researcher")
                .withCardInLibrary(1, "Pest Mascot")
                .build()
            val player = ai(game)

            val first = player.chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, first) shouldBe "Weather the Storm"
            game.execute(first)
            game.resolveStack()

            val (second, report) = chooseWithReport(game)
            val follow = withClue(report) { second.shouldBeInstanceOf<CastSpell>() }
            withClue(report) { sourceName(game, follow) shouldBe "Follow the Lumarets" }
        }

        test("continues an enabled follow-up instead of spending another pure lifegain resource") {
            val game = seeded()
                .withLifeTotal(1, 32)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInHand(1, "Follow the Lumarets")
                .withCardOnBattlefield(1, "Food", isToken = true)
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Swamp")
                .withCardInLibrary(1, "Blood Researcher")
                .withCardInLibrary(1, "Pest Mascot")
                .build()
            game.markLifeGainedThisTurn()

            val (chosen, report) = chooseWithReport(game)
            val follow = withClue(report) { chosen.shouldBeInstanceOf<CastSpell>() }
            withClue(report) { sourceName(game, follow) shouldBe "Follow the Lumarets" }
        }

        test("does not consume a pure activated lifegain resource when life has no concrete utility") {
            val game = seeded()
                .withLifeTotal(1, 29)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardOnBattlefield(1, "Food", isToken = true)
                .build()

            val (chosen, report) = chooseWithReport(game)
            withClue(report) { chosen.shouldBeInstanceOf<PassPriority>() }
        }

        test("casts the normal follow-up when a pure life event cannot be sequenced profitably first") {
            val game = seeded()
                .withLifeTotal(1, 20)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInHand(1, "Weather the Storm")
                .withCardInHand(1, "Follow the Lumarets")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Swamp")
                .withCardInLibrary(1, "Blood Researcher")
                .build()

            val (chosen, report) = chooseWithReport(game)
            val follow = withClue(report) { chosen.shouldBeInstanceOf<CastSpell>() }
            withClue(report) { sourceName(game, follow) shouldBe "Follow the Lumarets" }
        }

        test("does not suppress a lifegain spell with a concrete recursion rider") {
            val game = seeded()
                .withLifeTotal(1, 20)
                .withLandsOnBattlefield(1, "Forest", 3)
                .withCardInHand(1, "Pulse of Murasa")
                .withCardInGraveyard(1, "Grizzly Bears")
                .build()

            val (chosen, report) = chooseWithReport(game)
            val action = withClue(report) { chosen.shouldBeInstanceOf<CastSpell>() }
            withClue(report) { sourceName(game, action) shouldBe "Pulse of Murasa" }
        }

        test("Game 8 reconstruction holds redundant Food while pending Weather guarantees enhanced Follow") {
            val game = seeded()
                .withLifeTotal(1, 21)
                .withLandsOnBattlefield(1, "Forest", 6)
                .withCardInHand(1, "Weather the Storm")
                .withCardInHand(1, "Follow the Lumarets")
                .withCardOnBattlefield(1, "Food", isToken = true)
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Swamp")
                .withCardInLibrary(1, "Blood Researcher")
                .withCardInLibrary(1, "Pest Mascot")
                .build()
            game.castPendingWeather()

            val (response, responseReport) = chooseWithReport(game)
            withClue(responseReport) { response.shouldBeInstanceOf<PassPriority>() }
            (game.findPermanent("Food") != null).shouldBeTrue()

            game.execute(response).error shouldBe null
            game.resolveStack()
            val (followUp, followReport) = chooseWithReport(game)
            val follow = withClue(followReport) { followUp.shouldBeInstanceOf<CastSpell>() }
            withClue(followReport) { sourceName(game, follow) shouldBe "Follow the Lumarets" }
            (game.findPermanent("Food") != null).shouldBeTrue()
        }

        test("uses additional lifegain when a pending gain is insufficient for survival") {
            val game = seeded()
                .withLifeTotal(1, 1)
                .withLandsOnBattlefield(1, "Forest", 4)
                .withCardInHand(1, "Weather the Storm")
                .withCardOnBattlefield(1, "Food", isToken = true)
                .withCardOnBattlefield(2, "Craw Wurm")
                .build()
            game.castPendingWeather()

            val (chosen, report) = chooseWithReport(game)
            val activation = withClue(report) { chosen.shouldBeInstanceOf<ActivateAbility>() }
            withClue(report) { sourceName(game, activation) shouldBe "Food" }
        }

        test("allows additional lifegain over pending gain when a repeatable payoff consumes each event") {
            val game = seeded()
                .withLifeTotal(1, 20)
                .withLandsOnBattlefield(1, "Forest", 4)
                .withCardInHand(1, "Weather the Storm")
                .withCardOnBattlefield(1, "Food", isToken = true)
                .withCardOnBattlefield(1, "Blood Researcher")
                .build()
            game.castPendingWeather()

            val (chosen, report) = chooseWithReport(game)
            val activation = withClue(report) { chosen.shouldBeInstanceOf<ActivateAbility>() }
            withClue(report) { sourceName(game, activation) shouldBe "Food" }
        }

        test("uses Food to enable enhanced Follow when no lifegain is pending") {
            val game = seeded()
                .withLifeTotal(1, 20)
                .withLandsOnBattlefield(1, "Forest", 4)
                .withCardInHand(1, "Follow the Lumarets")
                .withCardOnBattlefield(1, "Food", isToken = true)
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Swamp")
                .withCardInLibrary(1, "Blood Researcher")
                .build()

            val (chosen, report) = chooseWithReport(game)
            val activation = withClue(report) { chosen.shouldBeInstanceOf<ActivateAbility>() }
            withClue(report) { sourceName(game, activation) shouldBe "Food" }
        }

        test("executes Food into enhanced Follow in the same turn when the line is profitable") {
            val game = seeded()
                .withLifeTotal(1, 21)
                .withLandsOnBattlefield(1, "Forest", 4)
                .withCardInHand(1, "Follow the Lumarets")
                .withCardOnBattlefield(1, "Food", isToken = true)
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Swamp")
                .withCardInLibrary(1, "Blood Researcher")
                .withCardInLibrary(1, "Pest Mascot")
                .build()
            val player = ai(game)

            val resource = player.chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            sourceName(game, resource) shouldBe "Food"
            game.execute(resource).error shouldBe null
            game.resolveStack()

            val followChoice = player.chooseAction(game.state)
            val follow = withClue("after Food=${sourceName(game, followChoice) ?: followChoice}") {
                followChoice.shouldBeInstanceOf<CastSpell>()
            }
            sourceName(game, follow) shouldBe "Follow the Lumarets"
        }

        test("holds a pure lifegain resource when its temporary condition would expire unused") {
            val game = seeded()
                .withLifeTotal(1, 21)
                .withLandsOnBattlefield(1, "Forest", 3)
                .withCardInHand(1, "Follow the Lumarets")
                .withCardOnBattlefield(1, "Food", isToken = true)
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Swamp")
                .withCardInLibrary(1, "Blood Researcher")
                .build()

            val chosen = ai(game).chooseAction(game.state)
            (chosen is ActivateAbility && sourceName(game, chosen) == "Food").shouldBeFalse()
        }

        test("does not spend lifegain when an executable enhanced mode adds no material value") {
            val game = seeded()
                .withLifeTotal(1, 21)
                .withLandsOnBattlefield(1, "Forest", 4)
                .withCardInHand(1, "Follow the Lumarets")
                .withCardOnBattlefield(1, "Food", isToken = true)
                .build()

            val chosen = ai(game).chooseAction(game.state)
            (chosen is ActivateAbility && sourceName(game, chosen) == "Food").shouldBeFalse()
        }

        test("Game 28 reconstruction completes land Weather enhanced Follow and useful follow-up") {
            val game = seeded()
                .withLifeTotal(1, 24)
                .withLandsOnBattlefield(1, "Forest", 3)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Weather the Storm")
                .withCardInHand(1, "Follow the Lumarets")
                .withCardInHand(1, "Carrier Thrall")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Swamp")
                .withCardInLibrary(1, "Blood Researcher")
                .withCardInLibrary(1, "Pest Mascot")
                .build()
            val player = ai(game)

            val first = player.chooseAction(game.state)
            val land = withClue("first action=${sourceName(game, first) ?: first::class.simpleName}") {
                first.shouldBeInstanceOf<PlayLand>()
            }
            game.execute(land).error shouldBe null

            val weather = player.chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, weather) shouldBe "Weather the Storm"
            game.execute(weather).error shouldBe null
            game.resolveStack()

            val follow = player.chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, follow) shouldBe "Follow the Lumarets"
            game.execute(follow).error shouldBe null
            game.resolveStackWith(player)

            val carrierChoice = player.chooseAction(game.state)
            val carrier = withClue("after Follow=${sourceName(game, carrierChoice) ?: carrierChoice}") {
                carrierChoice.shouldBeInstanceOf<CastSpell>()
            }
            sourceName(game, carrier) shouldBe "Carrier Thrall"
        }

        test("does not treat pending lifegain as guaranteed while an opponent can disrupt it") {
            val game = seeded()
                .withLifeTotal(1, 20)
                .withLandsOnBattlefield(1, "Forest", 6)
                .withLandsOnBattlefield(2, "Island", 2)
                .withCardInHand(1, "Weather the Storm")
                .withCardInHand(1, "Follow the Lumarets")
                .withCardInHand(2, "Counterspell")
                .withCardOnBattlefield(1, "Food", isToken = true)
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Swamp")
                .withCardInLibrary(1, "Blood Researcher")
                .build()
            game.castPendingWeather()

            val (chosen, report) = chooseWithReport(game)
            val activation = withClue(report) { chosen.shouldBeInstanceOf<ActivateAbility>() }
            withClue(report) { sourceName(game, activation) shouldBe "Food" }
        }

        test("does not suppress an independently valuable non-lifegain resource action") {
            val game = seeded()
                .withLifeTotal(2, 1)
                .withLandsOnBattlefield(1, "Forest", 3)
                .withCardInHand(1, "Weather the Storm")
                .withCardOnBattlefield(1, "Makeshift Munitions")
                .withCardOnBattlefield(1, "Eldrazi Scion", isToken = true)
                .build()
            game.castPendingWeather()

            val (chosen, report) = chooseWithReport(game)
            val activation = withClue(report) { chosen.shouldBeInstanceOf<ActivateAbility>() }
            withClue(report) { sourceName(game, activation) shouldBe "Makeshift Munitions" }
        }

        test("values Weather's separate Storm events as lethal Blight-Priest drains") {
            val game = seeded()
                .withLifeTotal(2, 2)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInHand(1, "Weather the Storm")
                .withCardInHand(1, "Grizzly Bears")
                .withCardOnBattlefield(1, "Marauding Blight-Priest")
                .build()
            game.state = game.state.copy(
                spellsCastThisTurn = 1,
                playerSpellsCastThisTurn = mapOf(game.player1Id to 1),
            )

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Weather the Storm"
        }

        test("preserves an Eldrazi Scion when no productive mana use exists") {
            val game = seeded()
                .withCardOnBattlefield(1, "Eldrazi Scion", isToken = true)
                .build()

            ai(game).chooseAction(game.state).shouldBeInstanceOf<PassPriority>()
        }

        test("blocks with Carrier Thrall when its productive death leaves a Scion") {
            val game = seeded()
                .withActivePlayer(2)
                .withCardOnBattlefield(1, "Carrier Thrall", summoningSickness = false)
                .withCardOnBattlefield(2, "Grizzly Bears", summoningSickness = false)
                .build()
            val thrall = game.findPermanent("Carrier Thrall")!!
            val attacker = game.findPermanent("Grizzly Bears")!!

            game.advanceToPhase(Phase.COMBAT, com.wingedsheep.sdk.core.Step.DECLARE_ATTACKERS)
            game.declareAttackers(mapOf("Grizzly Bears" to 1)).error shouldBe null
            game.passUntilPhase(Phase.COMBAT, com.wingedsheep.sdk.core.Step.DECLARE_BLOCKERS)

            val action = ai(game, game.player1Id).chooseAction(game.state)
                .shouldBeInstanceOf<DeclareBlockers>()
            action.blockers[thrall] shouldBe listOf(attacker)
        }

        test("spends an Eldrazi Scion when its colorless mana makes removal executable") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardOnBattlefield(1, "Eldrazi Scion", isToken = true)
                .withCardInHand(1, "Cast Down")
                .withCardOnBattlefield(2, "Hill Giant")
                .build()

            val (chosen, report) = chooseWithReport(game)
            val activation = withClue(report) { chosen.shouldBeInstanceOf<ActivateAbility>() }
            withClue(report) { sourceName(game, activation) shouldBe "Eldrazi Scion" }
            game.execute(activation).error shouldBe null
            game.findPermanent("Eldrazi Scion") shouldBe null

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Cast Down"
            game.execute(action).error shouldBe null
            game.resolveStack()
            game.isInGraveyard(2, "Hill Giant").shouldBeTrue()
        }

        test("does not sacrifice a Scion when lands already make the relevant spell executable") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardOnBattlefield(1, "Eldrazi Scion", isToken = true)
                .withCardInHand(1, "Cast Down")
                .withCardOnBattlefield(2, "Hill Giant")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Cast Down"
            (game.findPermanent("Eldrazi Scion") != null).shouldBeTrue()
        }

        test("does not sacrifice a Scion to unlock a strategically null sacrifice spell") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardOnBattlefield(1, "Eldrazi Scion", isToken = true)
                .withCardInHand(1, "Chainer's Edict")
                .build()

            ai(game).chooseAction(game.state).shouldBeInstanceOf<PassPriority>()
            (game.findPermanent("Eldrazi Scion") != null).shouldBeTrue()
        }

        test("Bone Shards sacrifices disposable Carrier Thrall instead of an engine creature") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Bone Shards")
                .withCardInHand(1, "Craw Wurm")
                .withCardOnBattlefield(1, "Carrier Thrall")
                .withCardOnBattlefield(1, "Blood Researcher")
                .withCardOnBattlefield(2, "Craw Wurm")
                .build()
            val thrall = game.findPermanent("Carrier Thrall")!!

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Bone Shards"
            cardName(game, chosenPermanent(action)!!) shouldBe "Craw Wurm"
            action.additionalCostPayment?.sacrificedPermanents shouldBe listOf(thrall)
        }

        test("Bone Shards sacrifices disposable Scion instead of an engine creature") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Bone Shards")
                .withCardInHand(1, "Craw Wurm")
                .withCardOnBattlefield(1, "Eldrazi Scion", isToken = true)
                .withCardOnBattlefield(1, "Pest Mascot")
                .withCardOnBattlefield(2, "Craw Wurm")
                .build()
            val scion = game.findPermanent("Eldrazi Scion")!!

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Bone Shards"
            action.additionalCostPayment?.sacrificedPermanents shouldBe listOf(scion)
        }

        test("Bone Shards discards a low-value hand card to preserve a valuable battlefield") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Bone Shards")
                .withCardInHand(1, "Forest")
                .withCardOnBattlefield(1, "Blood Researcher")
                .withCardOnBattlefield(2, "Craw Wurm")
                .build()
            val forest = game.findCardsInHand(1, "Forest").single()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Bone Shards"
            action.additionalCostPayment?.discardedCards shouldBe listOf(forest)
        }

        test("Bone Shards exploits Carrier Thrall's productive death instead of discarding value") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Bone Shards")
                .withCardInHand(1, "Pest Mascot")
                .withCardOnBattlefield(1, "Carrier Thrall")
                .withCardOnBattlefield(1, "Essence Warden")
                .withCardOnBattlefield(1, "Bogwater Lumaret")
                .withCardOnBattlefield(2, "Craw Wurm")
                .build()
            val thrall = game.findPermanent("Carrier Thrall")!!

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Bone Shards"
            action.additionalCostPayment?.sacrificedPermanents shouldBe listOf(thrall)
            game.execute(action).error shouldBe null
            game.resolveStack()
            (game.findPermanent("Eldrazi Scion") != null).shouldBeTrue()
            game.getLifeTotal(1) shouldBe 22
        }

        test("selected modal additional-cost friendly removal carries a complete audit") {
            val game = seeded()
                .withTurnNumber(15)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Bone Shards")
                .withCardInHand(1, "Forest")
                .withCardOnBattlefield(1, "Carrier Thrall")
                .withCardOnBattlefield(1, "Blood Artist")
                .withLifeTotal(2, 1)
                .build()

            val (chosen, insights) = chooseWithInsights(game)
            val action = chosen.shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Bone Shards"
            val audit = insights.last().options.mapNotNull { it.friendlyRemovalAudit }.single { it.selected }
            audit.removalAction shouldBe "cast Bone Shards"
            audit.targetName shouldBe "Blood Artist"
            audit.additionalCostMode shouldBe "sacrifice"
            audit.additionalCosts.single().entities.single().name shouldBe "Carrier Thrall"
            audit.deterministicLethal.shouldBeTrue()
            audit.passHoldValue.isFinite().shouldBeTrue()
            audit.friendlyTargetAlternatives.any { it.name == "Carrier Thrall" }.shouldBeTrue()
            audit.selectionReason shouldContain "selected"
            audit.policyApplied.shouldBeTrue()
        }

        test("Take 5 Game 2 structure rejects targeting and sacrificing the same Carrier below margin") {
            val game = seeded()
                .withTurnNumber(9)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Bone Shards")
                .withCardOnBattlefield(1, "Carrier Thrall")
                .build()
            val carrier = game.findPermanent("Carrier Thrall")!!

            val (chosen, insights) = chooseWithInsights(game)
            chosen.shouldBeInstanceOf<PassPriority>()
            val audit = insights.last().options.mapNotNull { it.friendlyRemovalAudit }
                .first { candidate ->
                    candidate.targetId == carrier && candidate.additionalCosts.any { cost ->
                        cost.kind == "sacrifice" && cost.entities.any { it.id == carrier }
                    }
                }
            audit.policyApplied.shouldBeTrue()
            audit.resourcesCreated.any { it.name == "Eldrazi Scion" }.shouldBeTrue()
            audit.fairTradeSurplus.shouldBeNegative()
            audit.policyDisposition shouldContain "reject"
        }

        test("Take 5 Game 26 structure charges discarded interaction and rejects the line below margin") {
            val game = seeded()
                .withTurnNumber(13)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Bone Shards")
                .withCardInHand(1, "Cast Down")
                .withCardOnBattlefield(1, "Carrier Thrall")
                .build()

            val (chosen, insights) = chooseWithInsights(game)
            chosen.shouldBeInstanceOf<PassPriority>()
            val audit = insights.last().options.mapNotNull { it.friendlyRemovalAudit }
                .first { candidate -> candidate.additionalCosts.any { cost ->
                    cost.kind == "discard" && cost.entities.any { it.name == "Cast Down" }
                } }
            audit.policyApplied.shouldBeTrue()
            audit.fairTradeSurplus.shouldBeNegative()
            audit.policyDisposition shouldContain "reject"
        }

        test("Take 5 Game 28 structure rejects minimal incidental Carrier death value") {
            val game = seeded()
                .withTurnNumber(13)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Bone Shards")
                .withCardOnBattlefield(1, "Carrier Thrall")
                .withCardOnBattlefield(1, "Essence Warden")
                .build()

            val (chosen, insights) = chooseWithInsights(game)
            chosen.shouldBeInstanceOf<PassPriority>()
            val audit = insights.last().options.mapNotNull { it.friendlyRemovalAudit }
                .first { it.resourcesCreated.any { resource -> resource.name == "Eldrazi Scion" } }
            audit.policyApplied.shouldBeTrue()
            audit.immediateEngineEffects.any { it.startsWith("life ") }.shouldBeTrue()
            audit.fairTradeSurplus.shouldBeNegative()
            audit.policyDisposition shouldContain "reject"
        }

        test("Take 5 Game 23 structure preserves productive modal friendly removal above margin") {
            val game = seeded()
                .withTurnNumber(17)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Bone Shards")
                .withCardInHand(1, "Forest")
                .withCardOnBattlefield(1, "Carrier Thrall")
                .withCardOnBattlefield(1, "Essence Warden")
                .withCardOnBattlefield(1, "Essence Warden")
                .withCardOnBattlefield(1, "Blood Researcher")
                .withCardOnBattlefield(1, "Pest Mascot")
                .build()

            val (chosen, insights) = chooseWithInsights(game)
            val action = chosen.shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Bone Shards"
            val audit = insights.last().options.mapNotNull { it.friendlyRemovalAudit }.single { it.selected }
            audit.policyApplied.shouldBeTrue()
            audit.fairTradeSurplus.shouldBePositive()
            audit.policyDisposition shouldContain "allow"
            audit.resourcesCreated.any { it.name == "Eldrazi Scion" }.shouldBeTrue()
        }

        test("Cast Down targets the highest-value legal nonlegendary threat") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Cast Down")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withCardOnBattlefield(2, "Craw Wurm")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, chosenPermanent(action)!!) shouldBe "Craw Wurm"
        }

        test("Game 9 reconstruction holds removal rather than replacing an unsupported friendly death") {
            val game = seeded()
                .withTurnNumber(15)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Cast Down")
                .withCardOnBattlefield(1, "Carrier Thrall", summoningSickness = false)
                .withCardOnBattlefield(1, "Blood Researcher", summoningSickness = false)
                .build()

            val (chosen, report) = chooseWithReport(game)
            withClue(report) { chosen.shouldBeInstanceOf<PassPriority>() }
            report shouldContain "friendly target lacks sufficient concrete downstream value"

            val (_, insights) = chooseWithInsights(game)
            val audit = insights.last().options.mapNotNull { it.friendlyRemovalAudit }.first()
            audit.targetName shouldBe "Carrier Thrall"
            audit.targetControllerId shouldBe game.player1Id
            audit.targetBattlefieldValueBefore.shouldBePositive()
            audit.passHoldValue.isFinite().shouldBeTrue()
            audit.netVersusHold.isFinite().shouldBeTrue()
            audit.policyDisposition shouldContain "reject"
            audit.selectionReason shouldContain "reject"
            val json = Json.encodeToString(insights.last())
            json shouldContain "friendlyRemovalAudit"
            json shouldContain "targetBattlefieldValueBefore"
            json shouldContain "requiredFairTradeMargin"
        }

        test("Game 29 reconstruction holds removal when a friendly death has no converted value") {
            val game = seeded()
                .withTurnNumber(15)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Cast Down")
                .withCardOnBattlefield(1, "Carrier Thrall", summoningSickness = false)
                .withCardOnBattlefield(1, "Blood Researcher", summoningSickness = false)
                .withCardOnBattlefield(1, "Pest Mascot", summoningSickness = false)
                .build()

            val (chosen, report) = chooseWithReport(game)
            withClue(report) { chosen.shouldBeInstanceOf<PassPriority>() }
            report shouldContain "friendly target lacks sufficient concrete downstream value"
        }

        test("ordinary opposing high-value target remains preferable to a friendly death") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Cast Down")
                .withCardOnBattlefield(1, "Carrier Thrall")
                .withCardOnBattlefield(2, "Craw Wurm")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, chosenPermanent(action)!!) shouldBe "Craw Wurm"
        }

        test("holds removal when neither a small opposing target nor a friendly death clears the value bar") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Cast Down")
                .withCardOnBattlefield(1, "Carrier Thrall")
                .withCardOnBattlefield(2, "Mons's Goblin Raiders")
                .build()

            val (chosen, report) = chooseWithReport(game)
            withClue(report) { chosen.shouldBeInstanceOf<PassPriority>() }
        }

        test("productive friendly death remains available when its converted engine value is superior") {
            val game = seeded()
                .withTurnNumber(15)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Cast Down")
                .withCardOnBattlefield(1, "Carrier Thrall")
                .withCardOnBattlefield(1, "Essence Warden")
                .withCardOnBattlefield(1, "Essence Warden")
                .withCardOnBattlefield(1, "Blood Researcher")
                .withCardOnBattlefield(1, "Pest Mascot")
                .build()
            val carrier = game.findPermanent("Carrier Thrall")!!

            val (chosen, report) = chooseWithReport(game)
            val action = withClue(report) { chosen.shouldBeInstanceOf<CastSpell>() }
            withClue(report) { chosenPermanent(action) shouldBe carrier }

            val (_, insights) = chooseWithInsights(game)
            val audit = insights.last().options.mapNotNull { it.friendlyRemovalAudit }.first { it.selected }
            audit.deathTriggersCreated.isNotEmpty().shouldBeTrue()
            audit.resourcesCreated.any { it.name == "Eldrazi Scion" }.shouldBeTrue()
            audit.resourceTransitionBenefit.shouldBeTrue()
            audit.fairTradeSurplus.shouldBePositive()
        }

        test("deterministic lethal self-removal remains available and is fully audited") {
            val game = seeded()
                .withTurnNumber(15)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Cast Down")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(1, "Blood Artist")
                .withLifeTotal(2, 1)
                .build()

            val (_, insights) = chooseWithInsights(game)
            val lethal = insights.last().options.mapNotNull { it.friendlyRemovalAudit }
                .firstOrNull { it.deterministicLethal }
            withClue(insights.last()) { lethal shouldNotBe null }
            lethal!!.policyDisposition shouldContain "deterministic lethal"
        }

        test("an already-winning pass baseline does not turn friendly removal into lethal") {
            val game = seeded()
                .withLifeTotal(2, 3)
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Lightning Bolt")
                .withCardInHand(1, "Cast Down")
                .withCardOnBattlefield(1, "Fierce Witchstalker")
                .build()
            game.castSpellTargetingPlayer(1, "Lightning Bolt", 2).error shouldBe null

            val (chosen, insights) = chooseWithInsights(game)
            chosen.shouldBeInstanceOf<PassPriority>()
            val castDown = insights.last().options.single { option ->
                option.cardName == "Cast Down"
            }
            castDown.action.shouldBeInstanceOf<CastSpell>().targets shouldBe
                listOf(ChosenTarget.Permanent(game.findPermanent("Fierce Witchstalker")!!))
            castDown.friendlyRemovalAudit shouldNotBe null
            castDown.friendlyRemovalAudit!!.deterministicLethal.shouldBeFalse()
            castDown.friendlyRemovalAudit!!.policyDisposition shouldContain "reject"
            castDown.productionAdmissible.shouldBeFalse()
            castDown.score shouldBe insights.last().baselineScore - 1.0
        }

        test("pass-equivalent Cast Down binds an opposing target instead of a harmful friendly one") {
            val game = seeded()
                .withLifeTotal(2, 3)
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Lightning Bolt")
                .withCardInHand(1, "Cast Down")
                .withCardOnBattlefield(1, "Fierce Witchstalker")
                .withCardOnBattlefield(2, "Craw Wurm")
                .build()
            val opposing = game.findPermanent("Craw Wurm")!!
            game.castSpellTargetingPlayer(1, "Lightning Bolt", 2).error shouldBe null

            val (chosen, insights) = chooseWithInsights(game)
            chosen.shouldBeInstanceOf<PassPriority>()
            val castDown = insights.last().options.single { option -> option.cardName == "Cast Down" }
            castDown.action.shouldBeInstanceOf<CastSpell>().targets shouldBe
                listOf(ChosenTarget.Permanent(opposing))
            castDown.chosen.shouldBeFalse()
            (castDown.score!! <= insights.last().baselineScore).shouldBeTrue()
        }

        test("friendly removal records additional resource costs that erase death value") {
            val game = seeded()
                .withTurnNumber(15)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Cast Down")
                .withCardOnBattlefield(1, "Carrier Thrall")
                .withCardOnBattlefield(1, "Essence Warden")
                .build()

            val (_, insights) = chooseWithInsights(game)
            val audits = insights.last().options.mapNotNull { it.friendlyRemovalAudit }
            val costly = audits.firstOrNull { it.resourcesCreated.any { resource -> resource.name == "Eldrazi Scion" } }
            withClue(insights.last()) { costly shouldNotBe null }
            costly!!.policyDisposition shouldContain "reject"
            costly.manaCost shouldBe "{1}{B}"
            costly.manaSources.size shouldBe 2
            costly.fairTradeSurplus.shouldBeNegative()
        }

        test("Chainer's Edict answers hexproof when targeted removal cannot") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Cast Down")
                .withCardInHand(1, "Chainer's Edict")
                .withCardOnBattlefield(2, "Plated Crusher")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Chainer's Edict"
        }

        test("holds a legal forced-sacrifice spell against an empty opposing battlefield") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Chainer's Edict")
                .build()

            ai(game).chooseAction(game.state).shouldBeInstanceOf<PassPriority>()
        }

        test("a forced-sacrifice spell has normal value when the opponent controls a creature") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Chainer's Edict")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Chainer's Edict"
        }

        test("holds a legal but strategically null forced-sacrifice flashback") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Swamp", 7)
                .withCardInGraveyard(1, "Chainer's Edict")
                .build()

            ai(game).chooseAction(game.state).shouldBeInstanceOf<PassPriority>()
        }

        test("Chainer's Edict avoids an unpaid ward that would defeat targeted removal") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Cast Down")
                .withCardInHand(1, "Chainer's Edict")
                .withCardOnBattlefield(2, "Tomakul Honor Guard")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Chainer's Edict"
        }

        test("casts Chainer's Edict through flashback when graveyard recursion is the answer") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Swamp", 7)
                .withCardInGraveyard(1, "Chainer's Edict")
                .withCardOnBattlefield(2, "Plated Crusher")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Chainer's Edict"
            action.alternativeCostType shouldBe AlternativeCostType.FLASHBACK
        }

        test("removal patience preserves interaction for a materially larger threat") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Cast Down")
                .withCardOnBattlefield(2, "Mons's Goblin Raiders")
                .build()

            val action = ai(game).chooseAction(game.state)
            (action is CastSpell && sourceName(game, action) == "Cast Down").shouldBeFalse()
        }

        test("Snuff Out pays life when free removal creates a decisive tempo line") {
            val game = seeded()
                .withLifeTotal(1, 15)
                .withLifeTotal(2, 5)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withLandsOnBattlefield(1, "Forest", 5)
                .withCardInHand(1, "Snuff Out")
                .withCardInHand(1, "Craw Wurm")
                .withCardOnBattlefield(2, "Craw Wurm")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Snuff Out"
            action.alternativeCostType shouldBe AlternativeCostType.SELF_ALTERNATIVE
        }

        test("Snuff Out uses mana rather than four life when tempo does not require the alternative") {
            val game = seeded()
                .withLifeTotal(1, 7)
                .withLandsOnBattlefield(1, "Swamp", 4)
                .withCardInHand(1, "Snuff Out")
                .withCardOnBattlefield(2, "Hill Giant")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Snuff Out"
            action.useAlternativeCost shouldBe false
        }

        test("Snuff Out does not pay four life in a lethal race-sensitive state") {
            val game = seeded()
                .withLifeTotal(1, 4)
                .withLandsOnBattlefield(1, "Swamp", 4)
                .withCardInHand(1, "Snuff Out")
                .withCardOnBattlefield(2, "Craw Wurm")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Snuff Out"
            action.useAlternativeCost shouldBe false
        }

        test("casts Tamiyo's Safekeeping only in response to removal of a high-value engine") {
            val idle = seeded()
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInHand(1, "Tamiyo's Safekeeping")
                .withCardOnBattlefield(1, "Blood Researcher")
                .build()
            (ai(idle).chooseAction(idle.state) is CastSpell).shouldBeFalse()

            val response = seeded()
                .withActivePlayer(2)
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInHand(1, "Tamiyo's Safekeeping")
                .withCardOnBattlefield(1, "Blood Researcher")
                .withLandsOnBattlefield(2, "Swamp", 3)
                .withCardInHand(2, "Murder")
                .build()
            val researcher = response.findPermanent("Blood Researcher")!!
            response.castSpell(2, "Murder", researcher).error shouldBe null
            response.execute(PassPriority(response.player2Id)).error shouldBe null

            val action = ai(response).chooseAction(response.state).shouldBeInstanceOf<CastSpell>()
            sourceName(response, action) shouldBe "Tamiyo's Safekeeping"
            chosenPermanent(action) shouldBe researcher
        }

        test("sequences lifegain before Follow the Lumarets when infusion materially improves selection") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Forest", 3)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Carrier Thrall")
                .withCardInHand(1, "Follow the Lumarets")
                .withCardOnBattlefield(1, "Essence Warden")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Swamp")
                .withCardInLibrary(1, "Blood Researcher")
                .withCardInLibrary(1, "Pest Mascot")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Carrier Thrall"
        }

        test("casts Follow without infusion when finding a land immediately matters more") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInHand(1, "Follow the Lumarets")
                .withCardInHand(1, "Generous Ent")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Swamp")
                .withCardInLibrary(1, "Blood Researcher")
                .withCardInLibrary(1, "Pest Mascot")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Follow the Lumarets"
        }

        test("Forestcycles Generous Ent during early land shortage") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInHand(1, "Generous Ent")
                .withCardInLibrary(1, "Forest")
                .build()

            val (chosen, report) = chooseWithReport(game)
            withClue(report) { chosen.shouldBeInstanceOf<TypecycleCard>() }
        }

        test("keeps and casts Generous Ent when its late-game body is feasible") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Forest", 6)
                .withCardInHand(1, "Generous Ent")
                .withCardInLibrary(1, "Forest")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Generous Ent"
            action.faceIndex shouldBe null
        }

        test("casts Sagu Wildling's Omen face for early mana development") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInHand(1, "Sagu Wildling")
                .withCardInLibrary(1, "Swamp")
                .build()

            val (chosen, report) = chooseWithReport(game)
            val action = withClue(report) { chosen.shouldBeInstanceOf<CastSpell>() }
            sourceName(game, action) shouldBe "Sagu Wildling"
            action.faceIndex shouldBe 0
        }

        test("casts Sagu Wildling as a creature when its board value is feasible") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Forest", 5)
                .withCardInHand(1, "Sagu Wildling")
                .withCardInLibrary(1, "Swamp")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Sagu Wildling"
            action.faceIndex shouldBe null
        }

        test("does not maximize life when removal advances the winning line") {
            val game = seeded()
                .withLifeTotal(1, 15)
                .withLifeTotal(2, 4)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Weather the Storm")
                .withCardInHand(1, "Cast Down")
                .withCardOnBattlefield(1, "Pest Mascot")
                .withCardOnBattlefield(2, "Craw Wurm")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Cast Down"
        }

        test("recognizes deterministic Blight-Priest lethal from separate Weather events") {
            val game = seeded()
                .withLifeTotal(2, 3)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInHand(1, "Weather the Storm")
                .withCardOnBattlefield(1, "Marauding Blight-Priest")
                .build()
            game.state = game.state.copy(
                spellsCastThisTurn = 2,
                playerSpellsCastThisTurn = mapOf(game.player1Id to 2),
            )

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Weather the Storm"
        }

        test("recognizes deterministic Researcher combat lethal after a lifegain counter") {
            val game = seeded()
                .withLifeTotal(2, 3)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInHand(1, "Weather the Storm")
                .withCardOnBattlefield(1, "Blood Researcher", summoningSickness = false)
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            withClue("Weather creates the counter that turns the next unblocked Researcher attack lethal") {
                sourceName(game, action) shouldBe "Weather the Storm"
            }
        }

        test("recognizes deterministic Pest Mascot combat lethal after a lifegain counter") {
            val game = seeded()
                .withLifeTotal(2, 3)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInHand(1, "Weather the Storm")
                .withCardOnBattlefield(1, "Pest Mascot", summoningSickness = false)
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Weather the Storm"
        }

        test("recognizes lethal combining pending lifegain drains and Researcher combat") {
            val game = seeded()
                .withLifeTotal(2, 5)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInHand(1, "Weather the Storm")
                .withCardOnBattlefield(1, "Blood Researcher", summoningSickness = false)
                .withCardOnBattlefield(1, "Marauding Blight-Priest")
                .build()
            game.state = game.state.copy(
                spellsCastThisTurn = 1,
                playerSpellsCastThisTurn = mapOf(game.player1Id to 1),
            )

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            sourceName(game, action) shouldBe "Weather the Storm"
        }

        test("life-gained-this-turn state makes Follow preferable to an ordinary life gain") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Forest", 4)
                .withCardInHand(1, "Follow the Lumarets")
                .withCardInHand(1, "Weather the Storm")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Swamp")
                .withCardInLibrary(1, "Blood Researcher")
                .withCardInLibrary(1, "Pest Mascot")
                .build()
            game.markLifeGainedThisTurn()

            val (chosen, report) = chooseWithReport(game)
            val action = withClue(report) { chosen.shouldBeInstanceOf<CastSpell>() }
            withClue(report) { sourceName(game, action) shouldBe "Follow the Lumarets" }
        }
    }
}
