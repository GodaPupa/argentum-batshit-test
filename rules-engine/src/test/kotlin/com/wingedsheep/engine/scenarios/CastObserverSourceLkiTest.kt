package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json

/** Artificial fixed engine fixtures; no experimental deck or allocation is initialized. */
class CastObserverSourceLkiTest : ScenarioTestBase() {
    private val lord = card("Cast Observer Toughness Support") {
        manaCost = "{0}"; typeLine = "Creature — Wizard"; power = 1; toughness = 1
        oracleText = "Creatures you control get +0/+1."
        staticAbility { ability = ModifyStats(0, 1, GroupFilter.AllCreaturesYouControl) }
    }
    private val sacrificeSpell = card("Cast Observer Sacrifice Spell") {
        manaCost = "{0}"; typeLine = "Instant"
        oracleText = "As an additional cost to cast this spell, sacrifice a creature. You gain 1 life."
        additionalCost(Costs.additional.SacrificePermanent(GameObjectFilter.Creature))
        spell { effect = Effects.GainLife(1) }
    }
    private fun observer(survives: Boolean) = card("Cast Observer Token $survives") {
        manaCost = "{R}"; typeLine = "Creature — Wizard"; power = 1
        toughness = if (survives) 1 else 0
        keywords(Keyword.DEATHTOUCH, Keyword.LIFELINK)
        oracleText = "Deathtouch, lifelink. Whenever you cast a spell, this creature deals 2 damage to any target."
        triggeredAbility {
            trigger = Triggers.YouCastSpell
            val recipient = target("any target", Targets.Any)
            effect = Effects.DealDamage(2, recipient)
        }
    }
    private val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }

    init {
        cardRegistry.register(listOf(lord, sacrificeSpell, observer(false), observer(true)))
        for (seat in listOf(1, 2)) for (hitPlayer in listOf(true, false)) for (survives in listOf(false, true)) {
            test("cast observer keeps its own source history seat $seat player target $hitPlayer survives $survives") {
                val opponent = 3 - seat
                val definition = observer(survives)
                val game = scenario().withPlayers("First seat", "Second seat")
                    .withRngSeed(202609261100L + seat * 10 + if (hitPlayer) 1 else 2)
                    .withCardOnBattlefield(seat, lord.name)
                    .withCardOnBattlefield(seat, definition.name, isToken = true)
                    .withCardOnBattlefield(opponent, "Craw Wurm")
                    .withCardInHand(seat, sacrificeSpell.name)
                    .withCardInLibrary(1, "Mountain").withCardInLibrary(2, "Mountain")
                    .withActivePlayer(seat).withPriorityPlayer(seat)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                val controller = if (seat == 1) game.player1Id else game.player2Id
                val enemy = if (seat == 1) game.player2Id else game.player1Id
                val source = requireNotNull(game.findPermanent(definition.name))
                val original = requireNotNull(game.state.objectRef(source))
                val worm = requireNotNull(game.findPermanent("Craw Wurm"))

                game.castSpellWithAdditionalSacrifice(seat, sacrificeSpell.name, lord.name).error shouldBe null
                game.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
                (game.state.getEntity(source) != null) shouldBe survives
                game.state.priorityPlayerId shouldBe null
                val encoded = json.encodeToString(GameState.serializer(), game.state)
                game.state = json.decodeFromString(GameState.serializer(), encoded)
                json.encodeToString(GameState.serializer(), game.state) shouldBe encoded
                game.selectTargets(listOf(if (hitPlayer) enemy else worm)).error shouldBe null
                val ability = game.state.stack.mapNotNull {
                    game.state.getEntity(it)?.get<TriggeredAbilityOnStackComponent>()
                }.single { it.sourceId == source }
                ability.objectReferences.origin shouldBe original
                if (survives) {
                    ability.lastKnownSourceSnapshot shouldBe null
                } else {
                    val snapshot = requireNotNull(ability.lastKnownSourceSnapshot)
                    snapshot.objectRef shouldBe original
                    snapshot.controllerId shouldBe controller
                    snapshot.ownerId shouldBe controller
                    snapshot.wasToken shouldBe true
                    snapshot.colors shouldBe setOf(Color.RED.name)
                    snapshot.keywords.containsAll(setOf("DEATHTOUCH", "LIFELINK")) shouldBe true
                }
                game.resolveStack().forEach { it.error shouldBe null }
                game.getLifeTotal(seat) shouldBe 23
                game.getLifeTotal(opponent) shouldBe if (hitPlayer) 18 else 20
                game.isOnBattlefield("Craw Wurm") shouldBe hitPlayer
                game.state.pendingDecision shouldBe null
                game.state.stack shouldBe emptyList()
                game.state.gameOver shouldBe false
            }
        }
    }
}
