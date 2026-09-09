package com.wingedsheep.gym

import com.wingedsheep.engine.core.AbilityTriggeredEvent
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.LifeChangedEvent
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PermanentsSacrificedEvent
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.state.components.battlefield.PreparedSpellCopyComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe

/**
 * End-to-end regression probes for the same [GameEnvironment] event stream consumed by agents.
 *
 * The card scenario tests remain the rules source of truth. These tests deliberately restore the
 * same deterministic boards into Gym, submit actions through [GameEnvironment.step], and assert
 * both the installed state and the returned telemetry. This catches registry/wiring failures that
 * a scenario fixture with its own complete registry cannot expose.
 */
class BatshitEconomicsGymInteractionTest : ScenarioTestBase() {

    private fun environment(game: TestGame): GameEnvironment =
        GameEnvironment.create(cardRegistry).also {
            it.restore(game.state, listOf(game.player1Id, game.player2Id))
        }

    private fun cardName(env: GameEnvironment, id: EntityId): String? =
        env.state.getEntity(id)?.get<CardComponent>()?.name

    private fun castNamed(env: GameEnvironment, name: String): CastSpell =
        env.legalActions().map { it.action }.filterIsInstance<CastSpell>().single {
            cardName(env, it.cardId) == name
        }

    private fun List<GameEvent>.batsTriggers(): List<AbilityTriggeredEvent> =
        filterIsInstance<AbilityTriggeredEvent>().filter { it.sourceName == "Mirkwood Bats" }

    private fun List<GameEvent>.lifeChangesFor(playerId: EntityId): List<LifeChangedEvent> =
        filterIsInstance<LifeChangedEvent>().filter { it.playerId == playerId }

    private fun applyOne(env: GameEnvironment, action: com.wingedsheep.engine.core.GameAction): StepResult =
        when (val result = env.stepExactlyOne(action)) {
            is ExactlyOneSubmissionResult.Applied -> result.step
            is ExactlyOneSubmissionResult.Rejected -> error("Gym rejected $action: ${result.reason}")
        }

    init {
        test("Gym reports Craft Treasure creation and separate Bats creation and sacrifice triggers") {
            val game = scenario()
                .withPlayers("Batshit", "Red")
                .withCardOnBattlefield(1, "Mirkwood Bats", summoningSickness = false)
                .withCardInHand(1, "Goblin Glasswright")
                .withLandsOnBattlefield(1, "Mountain", 3)
                .withLifeTotal(2, 20)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val env = environment(game)

            env.step(castNamed(env, "Goblin Glasswright"))
            val craft = env.state.getExile(game.player1Id).single { id ->
                env.state.getEntity(id)?.get<PreparedSpellCopyComponent>() != null
            }
            val creation = env.step(
                env.legalActions().map { it.action }.filterIsInstance<CastSpell>().single {
                    it.cardId == craft
                }
            )

            val treasure = env.state.controlledBattlefield(game.player1Id).single { id ->
                cardName(env, id) == "Treasure"
            }
            withClue("the Gym state and event stream agree on Craft's token creation") {
                env.state.getEntity(treasure)?.get<TokenComponent>() shouldBe TokenComponent
                creation.events.filterIsInstance<ZoneChangeEvent>().filter {
                    it.entityName == "Treasure" && it.toZone == Zone.BATTLEFIELD
                }.size.shouldBeExactly(1)
                creation.events.batsTriggers().size.shouldBeExactly(1)
                creation.events.lifeChangesFor(game.player2Id).map { it.oldLife to it.newLife }
                    .shouldContainExactly(20 to 19)
                env.state.lifeTotal(game.player2Id).shouldBeExactly(19)
            }

            val sacrifice = env.step(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = treasure,
                    abilityId = PredefinedTokens.Treasure.activatedAbilities.single().id,
                    manaColorChoice = Color.RED,
                )
            )
            withClue("sacrificing the Treasure is a second independently telemetered trigger") {
                sacrifice.events.filterIsInstance<PermanentsSacrificedEvent>()
                    .flatMap { it.permanentIds }.shouldContainExactly(treasure)
                sacrifice.events.batsTriggers().size.shouldBeExactly(1)
                sacrifice.events.lifeChangesFor(game.player2Id).map { it.oldLife to it.newLife }
                    .shouldContainExactly(19 to 18)
                env.state.lifeTotal(game.player2Id).shouldBeExactly(18)
            }
        }

        test("Gym distinguishes Offering's nontoken sacrifice from Map creation and later sacrifice") {
            val game = scenario()
                .withPlayers("Batshit", "Red")
                .withCardOnBattlefield(1, "Mirkwood Bats", summoningSickness = false)
                .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                .withCardOnBattlefield(1, "Kessig Flamebreather", summoningSickness = false)
                .withCardInHand(1, "Fanatical Offering")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Mountain")
                .withLandsOnBattlefield(1, "Swamp", 3)
                .withLifeTotal(2, 20)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val env = environment(game)
            val bear = env.state.controlledBattlefield(game.player1Id).single { id ->
                cardName(env, id) == "Grizzly Bears"
            }
            val offering = castNamed(env, "Fanatical Offering").copy(
                additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(bear)),
                paymentStrategy = PaymentStrategy.AutoPay,
            )
            val creation = env.step(offering)

            val map = env.state.controlledBattlefield(game.player1Id).single { id ->
                cardName(env, id) == "Map"
            }
            withClue("the nontoken cost does not trigger Bats, while the created Map does") {
                creation.events.filterIsInstance<PermanentsSacrificedEvent>()
                    .flatMap { it.permanentIds }.shouldContainExactly(bear)
                creation.events.batsTriggers().size.shouldBeExactly(1)
                creation.events.lifeChangesFor(game.player2Id).map { it.oldLife to it.newLife }
                    .shouldContainExactly(20 to 19)
                env.state.lifeTotal(game.player2Id).shouldBeExactly(19)
            }

            val exploreTarget = env.state.controlledBattlefield(game.player1Id).single { id ->
                cardName(env, id) == "Kessig Flamebreather"
            }
            val mapSacrifice = env.step(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = map,
                    abilityId = PredefinedTokens.Map.activatedAbilities.single().id,
                    targets = listOf(ChosenTarget.Permanent(exploreTarget)),
                    paymentStrategy = PaymentStrategy.AutoPay,
                )
            )
            withClue("the later Map sacrifice creates its own Bats trigger") {
                mapSacrifice.events.filterIsInstance<PermanentsSacrificedEvent>()
                    .flatMap { it.permanentIds }.shouldContainExactly(map)
                mapSacrifice.events.batsTriggers().size.shouldBeExactly(1)
                mapSacrifice.events.lifeChangesFor(game.player2Id).map { it.oldLife to it.newLife }
                    .shouldContainExactly(19 to 18)
                env.state.lifeTotal(game.player2Id).shouldBeExactly(18)
            }
        }

        test("Gym end state contains no Bats after both Game 2 permanents leave") {
            val game = scenario()
                .withPlayers("Batshit", "Red")
                .withCardOnBattlefield(1, "Mirkwood Bats", summoningSickness = false)
                .withCardOnBattlefield(1, "Mirkwood Bats", summoningSickness = false)
                .withCardInHand(1, "Fanatical Offering")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Mountain")
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(2, "Lightning Bolt")
                .withCardInHand(2, "Fiery Temper")
                .withLandsOnBattlefield(2, "Mountain", 2)
                .withActivePlayer(2)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val env = environment(game)
            val firstBats = env.state.controlledBattlefield(game.player1Id).first { id ->
                cardName(env, id) == "Mirkwood Bats"
            }
            val bolt = env.state.getHand(game.player2Id).single { cardName(env, it) == "Lightning Bolt" }
            applyOne(
                env,
                CastSpell(
                    playerId = game.player2Id,
                    cardId = bolt,
                    targets = listOf(ChosenTarget.Permanent(firstBats)),
                    paymentStrategy = PaymentStrategy.AutoPay,
                )
            )
            applyOne(env, PassPriority(game.player2Id))
            applyOne(env, PassPriority(game.player1Id))

            val secondBats = env.state.controlledBattlefield(game.player1Id).single { id ->
                cardName(env, id) == "Mirkwood Bats"
            }
            val temper = env.state.getHand(game.player2Id).single { cardName(env, it) == "Fiery Temper" }
            applyOne(
                env,
                CastSpell(
                    playerId = game.player2Id,
                    cardId = temper,
                    targets = listOf(ChosenTarget.Permanent(secondBats)),
                    paymentStrategy = PaymentStrategy.AutoPay,
                )
            )
            applyOne(env, PassPriority(game.player2Id))

            val offering = env.state.getHand(game.player1Id).single {
                cardName(env, it) == "Fanatical Offering"
            }
            applyOne(
                env,
                CastSpell(
                    playerId = game.player1Id,
                    cardId = offering,
                    additionalCostPayment = AdditionalCostPayment(
                        sacrificedPermanents = listOf(secondBats)
                    ),
                    paymentStrategy = PaymentStrategy.AutoPay,
                )
            )
            applyOne(env, PassPriority(game.player1Id))
            applyOne(env, PassPriority(game.player2Id))
            applyOne(env, PassPriority(game.player2Id))
            applyOne(env, PassPriority(game.player1Id))

            withClue("the final Gym state and smoke-summary source agree that neither Bats survived") {
            env.state.controlledBattlefield(game.player1Id).mapNotNull { id -> cardName(env, id) }
                .count { it == "Mirkwood Bats" }.shouldBeExactly(0)
            env.state.getGraveyard(game.player1Id).mapNotNull { id -> cardName(env, id) }
                .count { it == "Mirkwood Bats" }.shouldBeExactly(2)
            }
        }
    }
}
