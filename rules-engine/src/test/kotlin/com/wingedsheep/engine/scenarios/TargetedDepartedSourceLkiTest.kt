package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json

/** Generic source-history stress fixture; these artificial tokens are not candidate deck cards. */
class TargetedDepartedSourceLkiTest : ScenarioTestBase() {
    private val tokenSource = card("Fixture Targeted Departed Token") {
        manaCost = "{R}"
        typeLine = "Artifact Creature — Construct"
        power = 1; toughness = 1
        keywords(Keyword.DEATHTOUCH, Keyword.LIFELINK)
        oracleText = "Deathtouch, lifelink\nWhen this creature leaves the battlefield, it deals 2 damage to any target."
        triggeredAbility {
            trigger = Triggers.leavesBattlefield()
            val recipient = target("any target", Targets.Any)
            effect = Effects.DealDamage(2, recipient)
        }
    }
    private val slay = card("Fixture Targeted Source Slay") {
        manaCost = "{0}"
        typeLine = "Instant"
        oracleText = "Destroy target creature."
        spell { val victim = target("target creature", Targets.Creature); effect = Effects.Destroy(victim) }
    }
    private val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }

    init {
        cardRegistry.register(listOf(tokenSource, slay))
        for (seat in listOf(1, 2)) for (hitPlayer in listOf(true, false)) {
            test("departed token target pause preserves full source LKI seat $seat player target $hitPlayer") {
                val opponent = 3 - seat
                val game = scenario().withPlayers("Seat one", "Seat two")
                    .withRngSeed(202609260880L + seat * 10 + if (hitPlayer) 1 else 2)
                    .withCardOnBattlefield(seat, tokenSource.name)
                    .withCardOnBattlefield(opponent, "Craw Wurm")
                    .withCardInHand(seat, slay.name)
                    .withCardInLibrary(1, "Mountain").withCardInLibrary(2, "Mountain")
                    .withActivePlayer(seat).withPriorityPlayer(seat)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                val controller = if (seat == 1) game.player1Id else game.player2Id
                val enemy = if (opponent == 1) game.player1Id else game.player2Id
                val source = game.findPermanent(tokenSource.name)!!
                val worm = game.findPermanent("Craw Wurm")!!
                // Explicit fixture token construction; all departure and cleanup actions are real.
                game.state = game.state.updateEntity(source) { it.with(TokenComponent) }
                val departed = game.state.objectRef(source)!!
                game.castSpell(seat, slay.name, source).error shouldBe null
                game.resolveStack().forEach { it.error shouldBe null }
                game.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
                game.state.getEntity(source) shouldBe null

                // Serialize the actual paused engine state; target continuation must own its LKI.
                val encoded = json.encodeToString(GameState.serializer(), game.state)
                game.state = json.decodeFromString(GameState.serializer(), encoded)
                json.encodeToString(GameState.serializer(), game.state) shouldBe encoded
                val target = if (hitPlayer) enemy else worm
                game.selectTargets(listOf(target)).error shouldBe null
                val stacked = game.state.stack.mapNotNull {
                    game.state.getEntity(it)?.get<TriggeredAbilityOnStackComponent>()
                }.single { it.sourceId == source }
                val snapshot = requireNotNull(stacked.lastKnownSourceSnapshot)
                snapshot.objectRef shouldBe departed
                snapshot.controllerId shouldBe controller
                snapshot.ownerId shouldBe controller
                snapshot.colors shouldBe setOf(Color.RED.name)
                snapshot.typeLine!!.isCreature shouldBe true
                snapshot.keywords.containsAll(setOf("DEATHTOUCH", "LIFELINK")) shouldBe true
                game.resolveStack().forEach { it.error shouldBe null }
                game.state.pendingDecision shouldBe null
                game.state.stack shouldBe emptyList()
                game.getLifeTotal(seat) shouldBe 22
                game.getLifeTotal(opponent) shouldBe if (hitPlayer) 18 else 20
                game.isOnBattlefield("Craw Wurm") shouldBe hitPlayer
                game.state.gameOver shouldBe false
            }
        }
    }
}
