package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.TypecycleCard
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.LifeGainedThisTurnComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.core.Phase
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

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
        val captured = mutableListOf<com.wingedsheep.ai.insight.AiDecisionInsight>()
        val action = AIPlayer.create(
            cardRegistry, game.player1Id, profile,
            insightSink = { _, insight -> captured += insight },
        ).chooseAction(game.state)
        val report = captured.lastOrNull()?.options.orEmpty().joinToString(" | ") { option ->
            "${option.label}: score=${option.score}, raw=${option.rawScore}, note=${option.note}"
        }.ifEmpty { "no Strategist insight was captured" }
        return action to report
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
