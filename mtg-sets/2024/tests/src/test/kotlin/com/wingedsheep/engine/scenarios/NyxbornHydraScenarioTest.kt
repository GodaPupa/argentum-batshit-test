package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ManaSpentEvent
import com.wingedsheep.engine.core.SpellCastEvent
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.player.RestrictedManaEntry
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.scripting.effects.ManaRestriction
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.PlayersCantCastSpells
import com.wingedsheep.sdk.scripting.references.Player
import kotlinx.serialization.json.Json
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.BestowComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ProtectionComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class NyxbornHydraScenarioTest : ScenarioTestBase() {

    private val projector = StateProjector()

    private fun plusOneCounters(game: TestGame, entityId: com.wingedsheep.sdk.model.EntityId): Int =
        game.state.getEntity(entityId)?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    private fun castBestow(
        game: TestGame,
        x: Int,
        target: com.wingedsheep.sdk.model.EntityId
    ) = run {
        val hydra = game.state.getHand(game.player1Id).single { id ->
            game.state.getEntity(id)?.get<CardComponent>()?.name == "Nyxborn Hydra"
        }
        game.execute(
            CastSpell(
                playerId = game.player1Id,
                cardId = hydra,
                targets = listOf(ChosenTarget.Permanent(target)),
                xValue = x,
                useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.BESTOW
            )
        )
    }

    init {
        // Deterministic prohibition fixtures exercise the same static restriction as
        // Aether Storm; they are not admitted deck cards or sampled opponents.
        listOf(
            "Bestow Creature Prohibition Fixture" to GameObjectFilter.Creature,
            "Bestow Noncreature Prohibition Fixture" to GameObjectFilter.Noncreature,
        ).forEach { (name, filter) ->
            cardRegistry.register(card(name) {
                typeLine = "Enchantment"
                staticAbility { ability = PlayersCantCastSpells(affected = Player.Each, spellFilter = filter) }
            })
        }

        for (blocksCreature in listOf(true, false)) {
            test("filtered cast prohibition sees the chosen Bestow Aura mode (blocksCreature=$blocksCreature)") {
                val lock = if (blocksCreature) "Bestow Creature Prohibition Fixture"
                    else "Bestow Noncreature Prohibition Fixture"
                val game = scenario().withPlayers("Spy fixture", "Opponent")
                    .withCardInHand(1, "Nyxborn Hydra")
                    .withCardOnBattlefield(1, "Myr Retriever")
                    .withCardOnBattlefield(2, lock)
                    .withLandsOnBattlefield(1, "Forest", 4)
                    .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                val hydra = game.state.getHand(game.player1Id).single()
                val options = game.getLegalActions(1).mapNotNull { it.action as? CastSpell }
                    .filter { it.cardId == hydra }
                options.any { it.alternativeCostType == AlternativeCostType.BESTOW } shouldBe blocksCreature
                options.any { !it.useAlternativeCost } shouldBe !blocksCreature
                val before = game.state
                val rejected = if (blocksCreature) game.castXSpell(1, "Nyxborn Hydra", 1)
                    else castBestow(game, 1, game.findPermanent("Myr Retriever")!!)
                rejected.isSuccess shouldBe false
                rejected.events shouldBe emptyList()
                game.state shouldBe before
                val accepted = if (blocksCreature) castBestow(game, 1, game.findPermanent("Myr Retriever")!!)
                    else game.castXSpell(1, "Nyxborn Hydra", 1)
                accepted.error shouldBe null
                game.resolveStack()
                val permanent = game.findPermanent("Nyxborn Hydra")!!
                game.state.projectedState.hasSubtype(permanent, "Aura") shouldBe blocksCreature
                game.state.projectedState.isCreature(permanent) shouldBe !blocksCreature
            }
        }

        test("Nyxborn Hydra cast normally for X=3 enters as a 3/4 with three counters") {
            val game = scenario()
                .withPlayers("Spy fixture", "Opponent")
                .withCardInHand(1, "Nyxborn Hydra")
                .withLandsOnBattlefield(1, "Forest", 6)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cast = game.castXSpell(1, "Nyxborn Hydra", xValue = 3)
            cast.error shouldBe null
            cast.events.filterIsInstance<ManaSpentEvent>().sumOf { it.total } shouldBe 4
            game.resolveStack()

            val hydra = game.findPermanent("Nyxborn Hydra")!!
            plusOneCounters(game, hydra) shouldBe 3
            projector.getProjectedPower(game.state, hydra) shouldBe 3
            projector.getProjectedToughness(game.state, hydra) shouldBe 4
            projector.getProjectedKeywords(game.state, hydra).contains(Keyword.REACH) shouldBe true
            projector.getProjectedKeywords(game.state, hydra).contains(Keyword.TRAMPLE) shouldBe true
        }

        test("Nyxborn Hydra bestowed for X=2 is an Aura with two counters and grants the host +2/+2 reach and trample") {
            val game = scenario()
                .withPlayers("Spy fixture", "Opponent")
                .withCardInHand(1, "Nyxborn Hydra")
                .withCardInHand(1, "Unsummon")
                .withCardOnBattlefield(1, "Myr Retriever")
                .withLandsOnBattlefield(1, "Forest", 6)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val host = game.findPermanent("Myr Retriever")!!
            withClue("bestow cast should be legal") {
                val cast = castBestow(game, x = 2, target = host)
                cast.error shouldBe null
                cast.events.filterIsInstance<ManaSpentEvent>().sumOf { it.total } shouldBe 4
            }
            game.resolveStack()

            val hydra = game.findPermanent("Nyxborn Hydra")!!
            game.state.projectedState.hasSubtype(hydra, "Aura") shouldBe true
            game.state.projectedState.isCreature(hydra) shouldBe false
            game.state.getEntity(hydra)!!.get<BestowComponent>() shouldBe BestowComponent(
                originalTypeLine = com.wingedsheep.sdk.core.TypeLine.parse("Enchantment Creature — Hydra")
            )
            game.state.getEntity(hydra)!!.get<AttachedToComponent>()?.targetId shouldBe host
            plusOneCounters(game, hydra) shouldBe 2

            projector.getProjectedPower(game.state, host) shouldBe 3
            projector.getProjectedToughness(game.state, host) shouldBe 3
            projector.getProjectedKeywords(game.state, host).contains(Keyword.REACH) shouldBe true
            projector.getProjectedKeywords(game.state, host).contains(Keyword.TRAMPLE) shouldBe true
        }

        test("bestow target becoming illegal before resolution makes Nyxborn Hydra resolve as a creature") {
            val game = scenario()
                .withPlayers("Spy fixture", "Opponent")
                .withCardInHand(1, "Nyxborn Hydra")
                .withCardInHand(1, "Unsummon")
                .withCardOnBattlefield(1, "Myr Retriever")
                .withLandsOnBattlefield(1, "Forest", 6)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val host = game.findPermanent("Myr Retriever")!!
            val cast = castBestow(game, x = 1, target = host)
            cast.error shouldBe null
            val castEvent = cast.events.filterIsInstance<SpellCastEvent>().single()
            castEvent.alternativeCost shouldBe AlternativeCostType.BESTOW
            castEvent.xValue shouldBe 1
            castEvent.totalManaSpent shouldBe 3

            // Resolve an actual bounce above Bestow; the Aura target is now illegal.
            game.state = game.state.updateEntity(game.player1Id) { it.with(ManaPoolComponent(blue = 1)) }
            game.castSpell(1, "Unsummon", host).error shouldBe null
            game.resolveStack()

            val hydra = game.findPermanent("Nyxborn Hydra")!!
            game.state.projectedState.isCreature(hydra) shouldBe true
            game.state.projectedState.hasSubtype(hydra, "Aura") shouldBe false
            game.state.getEntity(hydra)!!.get<BestowComponent>() shouldBe null
            plusOneCounters(game, hydra) shouldBe 1
            projector.getProjectedPower(game.state, hydra) shouldBe 1
            projector.getProjectedToughness(game.state, hydra) shouldBe 2
        }

        test("bestow uses Aura card type for protection checks while being cast") {
            val game = scenario()
                .withPlayers("Spy fixture", "Opponent")
                .withCardInHand(1, "Nyxborn Hydra")
                .withCardInHand(1, "Unsummon")
                .withCardOnBattlefield(1, "Myr Retriever")
                .withLandsOnBattlefield(1, "Forest", 6)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val host = game.findPermanent("Myr Retriever")!!
            game.state = game.state.updateEntity(host) { permanent ->
                permanent.with(
                    ProtectionComponent(
                        colors = emptySet(),
                        cardTypes = setOf("CREATURE")
                    )
                )
            }

            // CR 702.103b: after choosing the bestow cost this is an Enchantment — Aura spell,
            // so protection from creatures does not make the creature an illegal target.
            castBestow(game, x = 1, target = host).error shouldBe null
        }

        test("a bestowed Nyxborn Hydra becomes its normal creature when its host leaves") {
            val game = scenario()
                .withPlayers("Spy fixture", "Opponent")
                .withCardInHand(1, "Nyxborn Hydra")
                .withCardInHand(1, "Unsummon")
                .withCardOnBattlefield(1, "Myr Retriever")
                .withLandsOnBattlefield(1, "Forest", 6)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val host = game.findPermanent("Myr Retriever")!!
            castBestow(game, x = 2, target = host).error shouldBe null
            game.resolveStack()

            val hydra = game.findPermanent("Nyxborn Hydra")!!
            game.state = game.state.updateEntity(game.player1Id) { it.with(ManaPoolComponent(blue = 1)) }
            game.castSpell(1, "Unsummon", host).error shouldBe null
            game.resolveStack()
            game.state.getHand(game.player1Id).contains(host) shouldBe true

            game.state.projectedState.isCreature(hydra) shouldBe true
            game.state.projectedState.hasSubtype(hydra, "Aura") shouldBe false
            game.state.getEntity(hydra)!!.get<BestowComponent>() shouldBe null
            game.state.getEntity(hydra)!!.get<AttachedToComponent>() shouldBe null
            plusOneCounters(game, hydra) shouldBe 2
            projector.getProjectedPower(game.state, hydra) shouldBe 2
            projector.getProjectedToughness(game.state, hydra) shouldBe 3
        }

        test("bouncing the bestowed Hydra clears its Aura marker counters and attachment before a normal recast") {
            val game = scenario().withPlayers("Spy fixture", "Opponent")
                .withCardInHand(1, "Nyxborn Hydra")
                .withCardInHand(1, "Disperse")
                .withCardOnBattlefield(1, "Myr Retriever")
                .withLandsOnBattlefield(1, "Forest", 6)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val host = game.findPermanent("Myr Retriever")!!
            castBestow(game, 2, host).error shouldBe null
            game.resolveStack()
            val hydra = game.findPermanent("Nyxborn Hydra")!!
            game.state = game.state.updateEntity(game.player1Id) { it.with(ManaPoolComponent(blue = 2)) }
            game.castSpell(1, "Disperse", hydra).error shouldBe null
            game.resolveStack()
            game.state.getHand(game.player1Id).contains(hydra) shouldBe true
            val returned = game.state.getEntity(hydra)!!
            returned.get<BestowComponent>() shouldBe null
            returned.get<AttachedToComponent>() shouldBe null
            plusOneCounters(game, hydra) shouldBe 0
            returned.get<CardComponent>()!!.typeLine.isCreature shouldBe true
            returned.get<CardComponent>()!!.typeLine.isAura shouldBe false
            projector.getProjectedPower(game.state, host) shouldBe 1
            projector.getProjectedToughness(game.state, host) shouldBe 1
            // Two untouched Forests pay the normal XG cost after the four-mana Bestow cast.
            game.castXSpell(1, "Nyxborn Hydra", 1).error shouldBe null
            game.resolveStack()
            val recast = game.findPermanent("Nyxborn Hydra")!!
            game.state.projectedState.isCreature(recast) shouldBe true
            game.state.getEntity(recast)!!.get<AttachedToComponent>() shouldBe null
            plusOneCounters(game, recast) shouldBe 1
        }

        test("Bestow has a distinct legal action and rejects payment below its XGG cost") {
            val game = scenario().withPlayers("Spy fixture", "Opponent")
                .withCardInHand(1, "Nyxborn Hydra")
                .withCardOnBattlefield(1, "Myr Retriever")
                .withLandsOnBattlefield(1, "Forest", 3)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val options = game.getLegalActions(1)
            val bestow = options.single { (it.action as? CastSpell)?.alternativeCostType == AlternativeCostType.BESTOW }
            bestow.maxAffordableX shouldBe 1
            val normal = options.single { (it.action as? CastSpell)?.let { c ->
                !c.useAlternativeCost && game.state.getEntity(c.cardId)?.get<CardComponent>()?.name == "Nyxborn Hydra"
            } == true }
            normal.maxAffordableX shouldBe 2
            val before = game.state
            val result = castBestow(game, 2, game.findPermanent("Myr Retriever")!!)
            result.isSuccess shouldBe false
            result.events shouldBe emptyList()
            game.state shouldBe before
        }

        test("creature-only mana cannot pay Bestow and cannot inflate its legal X bound") {
            val game = scenario().withPlayers("Spy fixture", "Opponent")
                .withCardInHand(1, "Nyxborn Hydra")
                .withCardOnBattlefield(1, "Myr Retriever")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.state = game.state.updateEntity(game.player1Id) { c ->
                c.with(ManaPoolComponent(restrictedMana = List(4) {
                    RestrictedManaEntry(Color.GREEN, ManaRestriction.CreatureSpellsOnly)
                }))
            }
            game.getLegalActions(1).none { (it.action as? CastSpell)?.alternativeCostType == AlternativeCostType.BESTOW } shouldBe true
            val before = game.state
            castBestow(game, 2, game.findPermanent("Myr Retriever")!!).isSuccess shouldBe false
            game.state shouldBe before
            game.castXSpell(1, "Nyxborn Hydra", 3).error shouldBe null
        }

        for ((counter, destination) in listOf(
            "Counterspell" to Zone.GRAVEYARD,
            "Remand" to Zone.HAND,
            "Dissipate" to Zone.EXILE,
            "Summary Dismissal" to Zone.EXILE,
        )) {
            test("$counter restores Bestow types and removes its marker on the actual stack exit") {
                val game = scenario().withPlayers("Spy fixture", "Opponent")
                    .withCardInHand(1, "Nyxborn Hydra")
                    .withCardOnBattlefield(1, "Myr Retriever")
                    .withLandsOnBattlefield(1, "Forest", 4)
                    .withCardInHand(2, counter)
                    .withCardInLibrary(2, "Island")
                    .withLandsOnBattlefield(2, "Island", 4)
                    .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                val hydra = game.state.getHand(game.player1Id).single()
                castBestow(game, 2, game.findPermanent("Myr Retriever")!!).error shouldBe null
                game.passPriority().error shouldBe null
                val response = if (counter == "Summary Dismissal") game.castSpell(2, counter)
                    else game.castSpellTargetingStackSpell(2, counter, "Nyxborn Hydra")
                response.error shouldBe null
                game.resolveStack()
                game.state.getZone(ZoneKey(game.player1Id, destination)).contains(hydra) shouldBe true
                game.state.getEntity(hydra)?.get<BestowComponent>() shouldBe null
                // Outside the battlefield the printed card definition is the right characteristic view.
                val card = game.state.getEntity(hydra)!!.get<CardComponent>()!!
                card.typeLine.isCreature shouldBe true
                card.typeLine.isAura shouldBe false
                card.manaCost.toString() shouldBe "{X}{G}"
            }
        }

        test("Bestow stack and permanent states round-trip with X targets counters and attachment intact") {
            val game = scenario().withPlayers("Spy fixture", "Opponent")
                .withCardInHand(1, "Nyxborn Hydra")
                .withCardOnBattlefield(1, "Myr Retriever")
                .withLandsOnBattlefield(1, "Forest", 4)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val host = game.findPermanent("Myr Retriever")!!
            castBestow(game, 2, host).error shouldBe null
            val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
            val stackState = game.state
            game.state = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), stackState))
            game.state shouldBe stackState
            game.resolveStack()
            val hydra = game.findPermanent("Nyxborn Hydra")!!
            val permanentState = game.state
            game.state = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), permanentState))
            game.state shouldBe permanentState
            plusOneCounters(game, hydra) shouldBe 2
            game.state.getEntity(hydra)?.get<AttachedToComponent>()?.targetId shouldBe host
            game.state.projectedState.isCreature(hydra) shouldBe false
            game.state.projectedState.hasSubtype(hydra, "Aura") shouldBe true
        }
    }
}
