package com.wingedsheep.engine.mechanics.encore

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.combat.MustAttackDefenderThisTurnComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.PlayerLostComponent
import com.wingedsheep.engine.state.components.player.LossReason
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.EncoreCopiesEffect
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Generalized Encore engine regressions; no real card is admitted by this class. */
class EncoreEngineTest : ScenarioTestBase() {
    private val witness = card("Encore Engine Witness") {
        manaCost = "{2}{U}"
        colorIdentity = "U"
        typeLine = "Creature — Test"
        power = 2
        toughness = 2
        activatedAbility {
            cost = Costs.Composite(Costs.Mana("{1}{U}"), Costs.ExileSelf)
            effect = EncoreCopiesEffect
            activateFromZone = Zone.GRAVEYARD
            timing = TimingRule.SorcerySpeed
        }
    }

    private val json = Json {
        serializersModule = com.wingedsheep.engine.core.engineSerializersModule
        allowStructuredMapKeys = true
        encodeDefaults = true
    }

    init {
        cardRegistry.register(witness)

        test("graveyard activation pays mana and exiles source before creating one hasty assigned token in two-player") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInGraveyard(1, witness.name)
                .withLandsOnBattlefield(1, "Island", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val sourceId = game.findCardsInGraveyard(1, witness.name).single()
            val activation = game.getLegalActions(1)
                .mapNotNull { it.action as? ActivateAbility }
                .single { it.sourceId == sourceId }

            game.execute(activation).error shouldBe null
            game.isInGraveyard(1, witness.name) shouldBe false
            (sourceId in game.state.getZone(game.player1Id, Zone.EXILE)) shouldBe true

            game.resolveStack().lastOrNull()?.error shouldBe null
            val tokens = game.state.getBattlefield(game.player1Id)
                .filter { game.state.getEntity(it)?.has<TokenComponent>() == true }
            tokens.size shouldBe 1
            val token = tokens.single()
            game.state.projectedState.hasKeyword(token, Keyword.HASTE) shouldBe true
            game.state.getEntity(token)?.get<AttackingComponent>() shouldBe null
            game.state.getEntity(token)?.get<MustAttackDefenderThisTurnComponent>()?.defenderId shouldBe game.player2Id
            game.state.delayedTriggers.size shouldBe 1
        }

        test("designated defender is enforced at declare attackers rather than at token entry") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInGraveyard(1, witness.name)
                .withLandsOnBattlefield(1, "Island", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val sourceId = game.findCardsInGraveyard(1, witness.name).single()
            val activation = game.getLegalActions(1).mapNotNull { it.action as? ActivateAbility }
                .single { it.sourceId == sourceId }
            game.execute(activation).error shouldBe null
            game.resolveStack()
            val token = game.state.getBattlefield(game.player1Id)
                .single { game.state.getEntity(it)?.has<TokenComponent>() == true }

            game.state = game.state.copy(
                phase = Phase.COMBAT,
                step = Step.DECLARE_ATTACKERS,
                priorityPlayerId = game.player1Id,
            )
            val omitted = game.execute(DeclareAttackers(game.player1Id, emptyMap()))
            omitted.error shouldNotBe null

            val declared = game.execute(DeclareAttackers(game.player1Id, mapOf(token to game.player2Id)))
            declared.error shouldBe null
            game.state.getEntity(token)?.get<AttackingComponent>()?.defenderId shouldBe game.player2Id
        }

        test("multiplayer creates one copy per active opponent with stable distinct assignments and survives serialization") {
            val registry = com.wingedsheep.engine.registry.CardRegistry().also { it.register(witness) }
            val initialized = GameInitializer(registry).initializeGame(
                GameConfig(
                    players = (1..3).map { PlayerConfig("Player $it", Deck(List(40) { witness.name }), 20) },
                    startingPlayerIndex = 0,
                    skipMulligans = true,
                    seed = 0x454E434F5245L,
                )
            )
            var state = initialized.state.copy(phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN)
            val controller = state.turnOrder[0]
            val (sourceId, withId) = state.newEntity()
            state = withId.withEntity(
                sourceId,
                com.wingedsheep.engine.state.ComponentContainer.of(
                    CardComponent(
                        cardDefinitionId = witness.name,
                        name = witness.name,
                        manaCost = witness.manaCost,
                        typeLine = witness.typeLine,
                        baseStats = witness.creatureStats,
                        baseKeywords = witness.keywords,
                        ownerId = controller,
                    ),
                    OwnerComponent(controller),
                )
            ).addToZone(ZoneKey(controller, Zone.EXILE), sourceId)

            val result = EngineServices(registry).effectExecutorRegistry.execute(
                state, EncoreCopiesEffect, EffectContext(sourceId, controller)
            )
            result.error shouldBe null
            val opponents = result.state.getOpponents(controller)
            opponents.size shouldBe 2
            val tokens = result.state.getBattlefield(controller)
                .filter { result.state.getEntity(it)?.has<TokenComponent>() == true }
            tokens.size shouldBe 2
            tokens.map {
                result.state.getEntity(it)?.get<MustAttackDefenderThisTurnComponent>()?.defenderId
            } shouldBe opponents
            tokens.forEach {
                result.state.getEntity(it)?.get<AttackingComponent>() shouldBe null
                result.state.projectedState.hasKeyword(it, Keyword.HASTE) shouldBe true
            }
            result.state.delayedTriggers.size shouldBe 2

            val restored = json.decodeFromString<GameState>(json.encodeToString(result.state))
            tokens.map {
                restored.getEntity(it)?.get<MustAttackDefenderThisTurnComponent>()?.defenderId
            } shouldBe opponents
            restored.delayedTriggers.size shouldBe 2
        }

        test("opponents already out of the game receive no token and no dangling attack assignment") {
            val registry = com.wingedsheep.engine.registry.CardRegistry().also { it.register(witness) }
            val initialized = GameInitializer(registry).initializeGame(
                GameConfig(
                    players = (1..3).map { PlayerConfig("Player $it", Deck(List(40) { witness.name }), 20) },
                    startingPlayerIndex = 0,
                    skipMulligans = true,
                    seed = 0x454E434F5246L,
                )
            )
            var state = initialized.state
            val controller = state.turnOrder[0]
            val departed = state.turnOrder[2]
            state = state.updateEntity(departed) { it.with(PlayerLostComponent(LossReason.CONCESSION)) }
            val (sourceId, withId) = state.newEntity()
            state = withId.withEntity(
                sourceId,
                com.wingedsheep.engine.state.ComponentContainer.of(
                    CardComponent(
                        cardDefinitionId = witness.name,
                        name = witness.name,
                        manaCost = witness.manaCost,
                        typeLine = witness.typeLine,
                        baseStats = witness.creatureStats,
                        baseKeywords = witness.keywords,
                        ownerId = controller,
                    ),
                    OwnerComponent(controller),
                )
            ).addToZone(ZoneKey(controller, Zone.EXILE), sourceId)

            val result = EngineServices(registry).effectExecutorRegistry.execute(
                state, EncoreCopiesEffect, EffectContext(sourceId, controller)
            )
            result.error shouldBe null
            val activeOpponent = result.state.getOpponents(controller).single()
            val tokens = result.state.getBattlefield(controller)
                .filter { result.state.getEntity(it)?.has<TokenComponent>() == true }
            tokens.size shouldBe 1
            result.state.getEntity(tokens.single())
                ?.get<MustAttackDefenderThisTurnComponent>()?.defenderId shouldBe activeOpponent
        }

        test("designated Encore attack is not mandatory when that opponent charges an attack tax") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                .withCardOnBattlefield(2, "Propaganda")
                .withActivePlayer(1)
                .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                .build()
            val attacker = game.findPermanent("Grizzly Bears")!!
            game.state = game.state.updateEntity(attacker) {
                it.with(MustAttackDefenderThisTurnComponent(game.player2Id))
            }

            // A requirement can't force its controller to pay {2}; declaring nobody is legal.
            game.execute(DeclareAttackers(game.player1Id, emptyMap())).error shouldBe null
        }

        test("designated Encore attack is not mandatory when the attacker itself has a sacrifice attack cost") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Leviathan", tapped = false, summoningSickness = false)
                .withLandsOnBattlefield(1, "Island", 2)
                .withActivePlayer(1)
                .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                .build()
            val attacker = game.findPermanent("Leviathan")!!
            game.state = game.state.updateEntity(attacker) {
                it.with(MustAttackDefenderThisTurnComponent(game.player2Id))
            }

            // Even though the two-Island cost is affordable, the requirement doesn't force payment.
            game.execute(DeclareAttackers(game.player1Id, emptyMap())).error shouldBe null
        }

        test("cleanup clears designated-defender requirement") {
            val game = scenario()
                .withPlayers()
                .withCardInGraveyard(1, witness.name)
                .withLandsOnBattlefield(1, "Island", 2)
                .build()
            val sourceId = game.findCardsInGraveyard(1, witness.name).single()
            val activation = game.getLegalActions(1).mapNotNull { it.action as? ActivateAbility }
                .single { it.sourceId == sourceId }
            game.execute(activation).error shouldBe null
            game.resolveStack()
            val token = game.state.getBattlefield(game.player1Id)
                .single { game.state.getEntity(it)?.has<TokenComponent>() == true }
            game.state.getEntity(token)?.get<MustAttackDefenderThisTurnComponent>() shouldNotBe null

            val cleaned = com.wingedsheep.engine.core.CleanupPhaseManager
                .applyCleanupTurnBasedActions(game.state, cardRegistry)
            cleaned.getEntity(token)?.get<MustAttackDefenderThisTurnComponent>() shouldBe null
        }
    }
}
