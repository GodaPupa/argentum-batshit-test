package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.combat.MustAttackDefenderThisTurnComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.LossReason
import com.wingedsheep.engine.state.components.player.PlayerLostComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.cmr.cards.AmphinMutineer
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.EncoreCopiesEffect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Exact Amphin Mutineer qualification. General Encore invariants also live in EncoreEngineTest;
 * this suite proves the real card composes that engine with Amphin's own optional ETB correctly.
 */
class AmphinMutineerScenarioTest : FunSpec({
    val salamanderWitness = card("Amphin Test Salamander") {
        manaCost = "{1}{U}"
        colorIdentity = "U"
        typeLine = "Creature — Salamander"
        power = 2
        toughness = 2
    }

    fun driver(): GameTestDriver = GameTestDriver().also {
        it.registerCards(TestCards.all + listOf(AmphinMutineer, salamanderWitness))
        it.initMirrorMatch(deck = Deck.of("Island" to 40))
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun castAmphin(d: GameTestDriver) {
        val amphin = d.putCardInHand(d.player1, AmphinMutineer.name)
        d.giveMana(d.player1, Color.BLUE, 4)
        d.castSpell(d.player1, amphin).error shouldBe null
        d.bothPass().error shouldBe null
    }

    fun amphinTokens(d: GameTestDriver, controller: EntityId): List<EntityId> =
        d.getPermanents(controller).filter { id ->
            val entity = d.state.getEntity(id)
            entity?.has<TokenComponent>() == true &&
                entity.get<CardComponent>()?.name == AmphinMutineer.name
        }

    fun salamanderWarriors(d: GameTestDriver, controller: EntityId): List<EntityId> =
        d.getPermanents(controller).filter { id ->
            val entity = d.state.getEntity(id)
            entity?.has<TokenComponent>() == true &&
                entity.get<CardComponent>()?.name == "Salamander Warrior Token"
        }

    fun activateEncore(d: GameTestDriver, controller: EntityId, sourceId: EntityId) {
        d.giveMana(controller, Color.BLUE, 6)
        val activation = d.legalActions(controller)
            .mapNotNull { it.action as? ActivateAbility }
            .single { it.sourceId == sourceId }
        d.submit(activation).error shouldBe null
    }

    test("front ETB targets only non-Salamanders, exiles the target, and gives its controller exactly one 4/3 blue Salamander Warrior") {
        val d = driver()
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val salamander = d.putCreatureOnBattlefield(d.player2, salamanderWitness.name)

        castAmphin(d)
        val decision = d.pendingDecision.shouldNotBeNull() as com.wingedsheep.engine.core.ChooseTargetsDecision
        val legal = decision.legalTargets.getValue(0)
        legal shouldContain bear
        legal shouldNotContain salamander

        d.submitTargetSelection(d.player1, listOf(bear)).error shouldBe null
        d.bothPass().error shouldBe null

        (bear in d.state.getZone(ZoneKey(d.player2, Zone.EXILE))) shouldBe true
        salamanderWarriors(d, d.player2).size shouldBe 1
        salamanderWarriors(d, d.player1).size shouldBe 0
        val token = salamanderWarriors(d, d.player2).single()
        d.state.projectedState.getPower(token) shouldBe 4
        d.state.projectedState.getToughness(token) shouldBe 3
        d.state.getEntity(token)?.get<CardComponent>()?.colors shouldBe setOf(Color.BLUE)
    }

    test("front ETB may choose zero targets and then creates no replacement token") {
        val d = driver()
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        castAmphin(d)

        (d.pendingDecision as? com.wingedsheep.engine.core.ChooseTargetsDecision).shouldNotBeNull()
        d.submitTargetSelection(d.player1, emptyList()).error shouldBe null
        d.bothPass().error shouldBe null

        salamanderWarriors(d, d.player1).size shouldBe 0
        salamanderWarriors(d, d.player2).size shouldBe 0
    }

    test("front ETB with a target that left before resolution fizzles and creates no replacement token") {
        val d = driver()
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        castAmphin(d)

        d.submitTargetSelection(d.player1, listOf(bear)).error shouldBe null
        d.moveToGraveyard(bear)
        d.bothPass()

        (bear in d.state.getZone(ZoneKey(d.player2, Zone.GRAVEYARD))) shouldBe true
        salamanderWarriors(d, d.player1).size shouldBe 0
        salamanderWarriors(d, d.player2).size shouldBe 0
    }

    test("exact Encore activation pays six, exiles Amphin as a cost, creates one hasty assigned copy, and round-trips its assignment and delayed sacrifice") {
        val d = driver()
        val source = d.putCardInGraveyard(d.player1, AmphinMutineer.name)
        activateEncore(d, d.player1, source)

        (source in d.state.getZone(ZoneKey(d.player1, Zone.EXILE))) shouldBe true
        d.bothPass().error shouldBe null

        val token = amphinTokens(d, d.player1).single()
        d.state.projectedState.hasKeyword(token, Keyword.HASTE) shouldBe true
        d.state.getEntity(token)?.get<AttackingComponent>() shouldBe null
        d.state.getEntity(token)?.get<MustAttackDefenderThisTurnComponent>()?.defenderId shouldBe d.player2
        d.state.delayedTriggers.size shouldBe 1

        val json = Json {
            serializersModule = engineSerializersModule
            allowStructuredMapKeys = true
            encodeDefaults = true
        }
        val restored = json.decodeFromString<GameState>(json.encodeToString(d.state))
        restored.getEntity(token)?.get<MustAttackDefenderThisTurnComponent>()?.defenderId shouldBe d.player2
        restored.delayedTriggers.size shouldBe 1
    }

    test("exact Encore copy is sacrificed at the next end step and its one-turn defender requirement does not survive cleanup") {
        val d = driver()
        val source = d.putCardInGraveyard(d.player1, AmphinMutineer.name)
        activateEncore(d, d.player1, source)
        d.bothPass().error shouldBe null
        val token = amphinTokens(d, d.player1).single()

        // With no legal non-Salamander target the copied Amphin's ETB can only choose zero.
        if (d.pendingDecision is com.wingedsheep.engine.core.ChooseTargetsDecision) {
            d.submitTargetSelection(d.player1, emptyList()).error shouldBe null
        }
        while (d.stackSize > 0) d.bothPass()
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.submit(DeclareAttackers(d.player1, mapOf(token to d.player2))).error shouldBe null
        d.passPriorityUntil(Step.END)
        while (d.stackSize > 0) d.bothPass()
        d.passPriorityUntil(Step.CLEANUP)

        (token in d.state.getBattlefield(d.player1)) shouldBe false
        d.state.getEntity(token)?.get<MustAttackDefenderThisTurnComponent>() shouldBe null
    }

    test("multiplayer Encore creates one distinct assigned Amphin per opponent and each copy gets its own ETB replacement") {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + AmphinMutineer)
        val players = d.initMultiplayer(List(3) { Deck.of("Island" to 40) })
        val controller = players[0]
        val opponentA = players[1]
        val opponentB = players[2]
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val bearA = d.putCreatureOnBattlefield(opponentA, "Grizzly Bears")
        val bearB = d.putCreatureOnBattlefield(opponentB, "Centaur Courser")
        val source = d.putCardInGraveyard(controller, AmphinMutineer.name)
        activateEncore(d, controller, source)

        var guard = 0
        while (amphinTokens(d, controller).size < 2 && guard++ < 12) {
            d.passPriority(d.state.priorityPlayerId.shouldNotBeNull()).error shouldBe null
        }
        amphinTokens(d, controller).size shouldBe 2

        val copies = amphinTokens(d, controller)
        copies.map {
            d.state.getEntity(it)?.get<MustAttackDefenderThisTurnComponent>()?.defenderId
        }.toSet() shouldBe setOf(opponentA, opponentB)
        copies.forEach {
            d.state.projectedState.hasKeyword(it, Keyword.HASTE) shouldBe true
            d.state.getEntity(it)?.get<AttackingComponent>() shouldBe null
        }
        d.state.delayedTriggers.size shouldBe 2

        // Each token entered separately, so each Amphin ETB must ask for/retain its own target.
        val chosen = mutableListOf<EntityId>()
        guard = 0
        while (chosen.size < 2 && guard++ < 20) {
            when (val pending = d.pendingDecision) {
                is com.wingedsheep.engine.core.ChooseOptionDecision -> {
                    d.submitDecision(
                        controller,
                        com.wingedsheep.engine.core.OptionChosenResponse(pending.id, 0),
                    ).error shouldBe null
                }
                is com.wingedsheep.engine.core.ChooseTargetsDecision -> {
                    val target = if (bearA !in chosen) bearA else bearB
                    pending.legalTargets.getValue(0) shouldContain target
                    d.submitTargetSelection(controller, listOf(target)).error shouldBe null
                    chosen += target
                }
                else -> {
                    d.passPriority(d.state.priorityPlayerId.shouldNotBeNull()).error shouldBe null
                }
            }
        }
        chosen shouldBe listOf(bearA, bearB)

        guard = 0
        while (salamanderWarriors(d, opponentA).size + salamanderWarriors(d, opponentB).size < 2 && guard++ < 30) {
            d.passPriority(d.state.priorityPlayerId.shouldNotBeNull()).error shouldBe null
        }
        salamanderWarriors(d, opponentA).size shouldBe 1
        salamanderWarriors(d, opponentB).size shouldBe 1
        (bearA in d.state.getZone(ZoneKey(opponentA, Zone.EXILE))) shouldBe true
        (bearB in d.state.getZone(ZoneKey(opponentB, Zone.EXILE))) shouldBe true
    }

    test("multiplayer Encore excludes an opponent already out of the game from exact Amphin fanout") {
        val registry = CardRegistry().also { it.register(AmphinMutineer) }
        val initialized = GameInitializer(registry).initializeGame(
            GameConfig(
                players = (1..3).map {
                    PlayerConfig("Player $it", Deck(List(40) { AmphinMutineer.name }), 20)
                },
                startingPlayerIndex = 0,
                skipMulligans = true,
                seed = 0x414D5048494EL,
            )
        )
        var state = initialized.state.copy(phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN)
        val controller = state.turnOrder[0]
        val departed = state.turnOrder[2]
        state = state.updateEntity(departed) {
            it.with(PlayerLostComponent(LossReason.CONCESSION))
        }
        val (sourceId, withId) = state.newEntity()
        state = withId.withEntity(
            sourceId,
            ComponentContainer.of(
                CardComponent(
                    cardDefinitionId = AmphinMutineer.name,
                    name = AmphinMutineer.name,
                    manaCost = AmphinMutineer.manaCost,
                    typeLine = AmphinMutineer.typeLine,
                    baseStats = AmphinMutineer.creatureStats,
                    baseKeywords = AmphinMutineer.keywords,
                    ownerId = controller,
                ),
                OwnerComponent(controller),
            )
        ).addToZone(ZoneKey(controller, Zone.EXILE), sourceId)

        val result = EngineServices(registry).effectExecutorRegistry.execute(
            state,
            EncoreCopiesEffect,
            EffectContext(sourceId, controller),
        )
        result.error shouldBe null
        val activeOpponent = result.state.getOpponents(controller).single()
        val tokens = result.state.getBattlefield(controller).filter {
            result.state.getEntity(it)?.has<TokenComponent>() == true &&
                result.state.getEntity(it)?.get<CardComponent>()?.name == AmphinMutineer.name
        }
        tokens.size shouldBe 1
        result.state.getEntity(tokens.single())
            ?.get<MustAttackDefenderThisTurnComponent>()?.defenderId shouldBe activeOpponent
        result.state.delayedTriggers.size shouldBe 1
    }
})
