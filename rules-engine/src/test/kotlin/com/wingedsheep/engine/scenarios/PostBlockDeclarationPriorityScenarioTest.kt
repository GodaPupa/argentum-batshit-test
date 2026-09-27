package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.battlefield.LastKnownPermanentComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.combat.AttackersDeclaredThisCombatComponent
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.combat.BlockersDeclaredThisCombatComponent
import com.wingedsheep.engine.state.components.combat.BlockingComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.engine.state.components.identity.PlayerComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.BlockTax
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json

/** Fixed mechanics only: CR 509.1/509.2/509.2a, 802.4 and the pre-priority SBA boundary. */
class PostBlockDeclarationPriorityScenarioTest : ScenarioTestBase() {
    private val targetingSentry = card("Post Block Targeting Sentry") {
        manaCost = "{0}"; typeLine = "Creature — Soldier"; power = 1; toughness = 3
        triggeredAbility {
            trigger = Triggers.Blocks
            val victim = target("creature", Targets.Creature)
            effect = Effects.DealDamage(1, victim)
        }
    }
    private val doubleSentry = card("Post Block Double Sentry") {
        manaCost = "{0}"; typeLine = "Creature — Soldier"; power = 1; toughness = 3
        triggeredAbility {
            trigger = Triggers.Blocks
            val victim = target("creature", Targets.Creature)
            effect = Effects.DealDamage(1, victim)
        }
        triggeredAbility {
            trigger = Triggers.Blocks
            val victim = target("creature", Targets.Creature)
            effect = Effects.DealDamage(2, victim)
        }
    }
    private val lifeSentry = card("Post Block Life Sentry") {
        manaCost = "{0}"; typeLine = "Creature — Soldier"; power = 1; toughness = 3
        triggeredAbility { trigger = Triggers.Blocks; effect = Effects.GainLife(1) }
    }
    private val tax = card("Post Block Tax Fixture") {
        manaCost = "{0}"; typeLine = "Artifact"
        staticAbility { ability = BlockTax(DynamicAmount.Fixed(1)) }
    }
    private val manaLord = card("Post Block Mana Lord") {
        manaCost = "{0}"; typeLine = "Creature — Wizard"; power = 1; toughness = 1
        keywords(Keyword.DEATHTOUCH, Keyword.LIFELINK)
        staticAbility { ability = ModifyStats(0, 1, GroupFilter.AllCreatures) }
        activatedAbility {
            cost = Costs.SacrificeSelf; effect = Effects.AddMana(Color.BLACK); manaAbility = true
        }
        triggeredAbility {
            trigger = Triggers.Dies
            val victim = target("creature", Targets.Creature)
            effect = Effects.DealDamage(1, victim)
        }
    }
    private val fragile = card("Post Block Fragile") {
        manaCost = "{0}"; typeLine = "Creature — Test"; power = 0; toughness = 0
    }
    private val departingObserver = card("Post Block Departing Mana Observer") {
        manaCost = "{B}"; typeLine = "Creature — Wizard"; power = 1; toughness = 1
        keywords(Keyword.DEATHTOUCH, Keyword.LIFELINK)
        activatedAbility {
            cost = Costs.SacrificeSelf; effect = Effects.AddMana(Color.BLACK); manaAbility = true
        }
        triggeredAbility {
            trigger = Triggers.blocks(binding = TriggerBinding.ANY)
            val victim = target("creature", Targets.Creature)
            effect = Effects.DealDamage(1, victim)
        }
    }
    private val json = Json {
        serializersModule = engineSerializersModule
        allowStructuredMapKeys = true
        encodeDefaults = true
    }

    private fun setup(active: Int = 1, blocker: String = "Grizzly Bears") = scenario()
        .withPlayers("First seat", "Second seat").withRngSeed(0xFE000095)
        .withActivePlayer(active).withPriorityPlayer(3 - active)
        .inPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
        .withCardOnBattlefield(active, "Craw Wurm")
        .withCardOnBattlefield(3 - active, blocker)
        .withCardInHand(active, "Giant Growth").withCardInHand(3 - active, "Giant Growth")
        .withCardOnBattlefield(active, "Forest")
        .withCardInLibrary(1, "Island").withCardInLibrary(2, "Island")

    private fun TestGame.player(number: Int) = if (number == 1) player1Id else player2Id

    private fun TestGame.beginBlocks(active: Int = 1): Pair<EntityId, EntityId> {
        val attacker = findPermanent("Craw Wurm")!!
        val defender = player(3 - active)
        state = state.updateEntity(attacker) { it.with(AttackingComponent(defenderId = defender)) }
            .updateEntity(player(active)) { it.with(AttackersDeclaredThisCombatComponent) }
            .withPriority(defender)
        return attacker to defender
    }

    private fun TestGame.roundTrip() {
        val encoded = json.encodeToString(GameState.serializer(), state)
        val restored = json.decodeFromString(GameState.serializer(), encoded)
        restored shouldBe state
        json.encodeToString(GameState.serializer(), restored) shouldBe encoded
        state = restored
    }

    private fun TestGame.assertWindow(active: EntityId) {
        state.step shouldBe Step.DECLARE_BLOCKERS
        state.priorityPlayerId shouldBe active
        state.hasPriority(active) shouldBe true
        state.pendingDecision shouldBe null
        state.pendingCastPriority shouldBe null
        state.priorityPassedBy shouldBe emptySet()
        state.gameOver shouldBe false
    }

    private fun TestGame.rejectUnchanged(action: GameAction) {
        val before = state
        execute(action).error.shouldNotBeNull()
        state shouldBe before
    }

    private fun TestGame.payBlockTax(autoPay: Boolean = true) {
        val choice = state.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
        submitDecision(ManaSourcesSelectedResponse(choice.id, emptyList(), autoPay)).error shouldBe null
    }

    private data class FourSeats(
        val game: TestGame, val active: EntityId, val first: EntityId,
        val spectator: EntityId, val last: EntityId, val attackers: List<EntityId>,
        val firstBlocker: EntityId, val lastBlocker: EntityId,
    )

    /** A coherent four-seat fixed board; no extra GameInitializer invocation or random draw. */
    private fun fourSeats(firstName: String, lastName: String, taxed: Boolean = false,
                          lateSource: String? = null): FourSeats {
        val builder = setup(blocker = firstName)
            .withCardOnBattlefield(1, "Craw Wurm").withCardOnBattlefield(2, lastName)
            .withCardInHand(2, "Giant Growth")
            .withCardOnBattlefield(2, "Forest").withCardOnBattlefield(2, "Forest")
        if (taxed) builder.withCardOnBattlefield(1, tax.name)
        if (lateSource != null) builder.withCardOnBattlefield(2, lateSource, isToken = true)
        val game = builder.build()
        val spectator = EntityId("post-block-spectator")
        val last = EntityId("post-block-last-defender")
        for (player in listOf(spectator, last)) {
            game.state = game.state.withEntity(player, ComponentContainer.of(
                PlayerComponent(player.value), LifeTotalComponent(20), ManaPoolComponent(),
            )).copy(turnOrder = game.state.turnOrder + player)
            for (zone in listOf(Zone.HAND, Zone.LIBRARY, Zone.GRAVEYARD, Zone.BATTLEFIELD)) {
                game.state = game.state.copy(zones = game.state.zones + (ZoneKey(player, zone) to emptyList()))
            }
        }
        val firstBlocker = game.findAllPermanents(firstName).first()
        val lastBlocker = game.findAllPermanents(lastName).last()
        fun transfer(id: EntityId, zone: Zone) {
            game.state = game.state.removeFromZone(ZoneKey(game.player2Id, zone), id)
                .updateEntity(id) { container ->
                    container.with(container.get<CardComponent>()!!.copy(ownerId = last))
                        .with(OwnerComponent(last)).with(ControllerComponent(last))
                }.addToZone(ZoneKey(last, zone), id)
        }
        transfer(lastBlocker, Zone.BATTLEFIELD)
        transfer(game.findAllPermanents("Forest").last(), Zone.BATTLEFIELD)
        transfer(game.findCardsInHand(2, "Giant Growth").last(), Zone.HAND)
        if (lateSource != null) transfer(game.findPermanent(lateSource)!!, Zone.BATTLEFIELD)
        val attackers = game.findAllPermanents("Craw Wurm")
        game.state = game.state.updateEntity(attackers[0]) { it.with(AttackingComponent(game.player2Id)) }
            .updateEntity(attackers[1]) { it.with(AttackingComponent(last)) }
            .updateEntity(game.player1Id) { it.with(AttackersDeclaredThisCombatComponent) }
            .withPriority(game.player2Id)
        return FourSeats(game, game.player1Id, game.player2Id, spectator, last,
            attackers, firstBlocker, lastBlocker)
    }

    init {
        cardRegistry.register(listOf(targetingSentry, doubleSentry, lifeSentry, tax, manaLord, fragile, departingObserver))

        for (active in 1..2) {
            test("PB0$active empty final declaration gives active seat $active first priority") {
                val game = setup(active).build()
                val (_, defender) = game.beginBlocks(active)
                game.execute(DeclareBlockers(defender, emptyMap())).error shouldBe null
                game.assertWindow(game.player(active))
                game.rejectUnchanged(PassPriority(defender))
                game.rejectUnchanged(CastSpell(defender, game.findCardsInHand(3 - active, "Giant Growth").single(),
                    listOf(ChosenTarget.Permanent(game.findPermanent("Grizzly Bears")!!))))
            }

            test("PB0${active + 2} actual blocks give active seat $active an immediate combat trick") {
                val game = setup(active).build()
                val (attacker, defender) = game.beginBlocks(active)
                val blocker = game.findPermanent("Grizzly Bears")!!
                game.execute(DeclareBlockers(defender, mapOf(blocker to listOf(attacker)))).error shouldBe null
                game.assertWindow(game.player(active))
                game.state.getEntity(blocker)?.get<BlockingComponent>()?.blockedAttackerIds shouldBe listOf(attacker)
                game.state.getEntity(blocker)?.get<DamageComponent>() shouldBe null
                game.castSpell(active, "Giant Growth", attacker).error shouldBe null
                game.state.stack.size shouldBe 1
                game.state.step shouldBe Step.DECLARE_BLOCKERS
                game.getLifeTotal(3 - active) shouldBe 20
            }
        }

        test("PB05 a targeted block trigger preserves active priority across serialized target choice") {
            val game = setup(blocker = targetingSentry.name).build()
            val (attacker, defender) = game.beginBlocks()
            game.execute(DeclareBlockers(defender, mapOf(game.findPermanent(targetingSentry.name)!! to listOf(attacker))))
                .error shouldBe null
            game.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>().playerId shouldBe defender
            game.state.priorityPlayerId shouldBe null
            game.rejectUnchanged(PassPriority(defender))
            game.roundTrip()
            game.selectTargets(listOf(attacker)).error shouldBe null
            game.assertWindow(game.player1Id)
            game.state.stack.size shouldBe 1
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.getEntity(attacker)?.get<DamageComponent>()?.amount shouldBe 1
            game.state.step shouldBe Step.DECLARE_BLOCKERS
        }

        test("PB06 simultaneous block triggers keep ordering and targets before active priority") {
            val game = setup(blocker = doubleSentry.name).build()
            val (attacker, defender) = game.beginBlocks()
            game.execute(DeclareBlockers(defender, mapOf(game.findPermanent(doubleSentry.name)!! to listOf(attacker))))
                .error shouldBe null
            val ordering = game.state.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
            ordering.playerId shouldBe defender
            game.state.priorityPlayerId shouldBe null
            val frame = (game.state.peekContinuation() as Suspension).answer.shouldBeInstanceOf<TriggerOrderingContinuation>()
            frame.remaining.size shouldBe 2
            val chosen = frame.remaining.indexOfFirst { it.ability.id == doubleSentry.triggeredAbilities[1].id }
            (chosen >= 0) shouldBe true
            game.roundTrip()
            game.submitDecision(OptionChosenResponse(ordering.id, chosen)).error shouldBe null
            repeat(2) {
                game.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>().playerId shouldBe defender
                game.state.priorityPlayerId shouldBe null
                game.selectTargets(listOf(attacker)).error shouldBe null
            }
            game.assertWindow(game.player1Id)
            game.state.stack.size shouldBe 2
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.getEntity(attacker)?.get<DamageComponent>()?.amount shouldBe 3
            game.state.stack shouldBe emptyList()
        }

        test("PB07 paid block tax restores active priority after a serialized mana selection") {
            val game = setup().withCardOnBattlefield(1, tax.name).withCardOnBattlefield(2, "Forest").build()
            val (attacker, defender) = game.beginBlocks()
            val blocker = game.findPermanent("Grizzly Bears")!!
            game.execute(DeclareBlockers(defender, mapOf(blocker to listOf(attacker)))).error shouldBe null
            game.state.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>().playerId shouldBe defender
            game.state.hasPriority(defender) shouldBe false
            game.state.getEntity(blocker)?.get<BlockingComponent>() shouldBe null
            game.roundTrip()
            game.payBlockTax()
            game.assertWindow(game.player1Id)
            game.state.getEntity(blocker)?.get<BlockingComponent>()?.blockedAttackerIds shouldBe listOf(attacker)
            game.state.getBattlefield().filter { game.state.projectedState.getController(it) == defender }
                .count { game.state.getEntity(it)?.has<TappedComponent>() == true } shouldBe 1
            game.state.getEntity(defender)?.get<ManaPoolComponent>()?.total shouldBe 0
        }

        test("PB08 declining a block tax preserves declaration duty and grants no priority") {
            val game = setup().withCardOnBattlefield(1, tax.name).withCardOnBattlefield(2, "Forest").build()
            val (attacker, defender) = game.beginBlocks()
            val blocker = game.findPermanent("Grizzly Bears")!!
            game.execute(DeclareBlockers(defender, mapOf(blocker to listOf(attacker)))).error shouldBe null
            game.roundTrip()
            game.payBlockTax(autoPay = false)
            game.state.pendingDecision shouldBe null
            game.state.priorityPlayerId shouldBe defender // declaration routing only
            game.state.hasPriority(defender) shouldBe false
            game.state.getEntity(defender)?.has<BlockersDeclaredThisCombatComponent>() shouldBe false
            game.state.getEntity(blocker)?.get<BlockingComponent>() shouldBe null
            game.state.getBattlefield().count { game.state.getEntity(it)?.has<TappedComponent>() == true } shouldBe 0
            game.rejectUnchanged(PassPriority(defender))
            game.rejectUnchanged(CastSpell(defender, game.findCardsInHand(2, "Giant Growth").single(),
                listOf(ChosenTarget.Permanent(blocker))))
            game.execute(DeclareBlockers(defender, emptyMap())).error shouldBe null
            game.assertWindow(game.player1Id)
        }

        test("PB09 nested block-tax mana pays before SBA and targeted death-trigger placement") {
            val game = setup().withCardOnBattlefield(1, tax.name)
                .withCardOnBattlefield(2, manaLord.name).withCardOnBattlefield(2, fragile.name).build()
            val (attacker, defender) = game.beginBlocks()
            val lord = game.findPermanent(manaLord.name)!!
            val fragileId = game.findPermanent(fragile.name)!!
            val blocker = game.findPermanent("Grizzly Bears")!!
            game.execute(DeclareBlockers(defender, mapOf(blocker to listOf(attacker)))).error shouldBe null
            game.state.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
            game.execute(ActivateAbility(defender, lord, manaLord.activatedAbilities.single().id)).error shouldBe null
            game.isInGraveyard(2, manaLord.name) shouldBe true
            game.isOnBattlefield(fragile.name) shouldBe true
            game.state.getEntity(defender)?.get<ManaPoolComponent>()?.black shouldBe 1
            game.state.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
            game.state.stack shouldBe emptyList()
            game.roundTrip()
            game.payBlockTax(autoPay = false) // existing floating black pays the locked {1}
            val target = game.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
            target.playerId shouldBe defender
            target.legalTargets.values.flatten().contains(fragileId) shouldBe false
            game.isInGraveyard(2, fragile.name) shouldBe true
            game.state.priorityPlayerId shouldBe null
            game.state.getEntity(defender)?.get<ManaPoolComponent>()?.total shouldBe 0
            game.selectTargets(listOf(attacker)).error shouldBe null
            game.assertWindow(game.player1Id)
            game.state.stack.size shouldBe 1
            game.resolveStack().forEach { it.error shouldBe null }
            game.isInGraveyard(1, "Craw Wurm") shouldBe true
            game.getLifeTotal(2) shouldBe 21
        }

        test("PB10 multiple defenders have only an APNAP declaration baton before active priority") {
            val (game, active, first, spectator, last, _, _, _) = fourSeats("Grizzly Bears", "Savannah Lions")
            game.rejectUnchanged(DeclareBlockers(last, emptyMap()))
            game.execute(DeclareBlockers(first, emptyMap())).error shouldBe null
            game.state.priorityPlayerId shouldBe last
            game.state.hasPriority(last) shouldBe false
            game.state.priorityTeam shouldBe emptyList()
            for (player in listOf(active, first, spectator, last)) game.rejectUnchanged(PassPriority(player))
            game.rejectUnchanged(DeclareBlockers(first, emptyMap()))
            val spell = game.state.getHand(last).single()
            val victim = game.state.getBattlefield().first { game.state.projectedState.getController(it) == last &&
                game.state.projectedState.isCreature(it) }
            game.rejectUnchanged(CastSpell(last, spell, listOf(ChosenTarget.Permanent(victim))))
            game.roundTrip()
            game.execute(DeclareBlockers(last, emptyMap())).error shouldBe null
            game.assertWindow(active)
            game.execute(PassPriority(active)).error shouldBe null
            game.state.priorityPlayerId shouldBe first
        }

        test("PB11 first defender's target choice waits until every block declaration is complete") {
            val (game, active, first, _, last, attackers, firstBlocker, _) =
                fourSeats(targetingSentry.name, "Grizzly Bears")
            game.execute(DeclareBlockers(first, mapOf(firstBlocker to listOf(attackers[0])))).error shouldBe null
            game.state.pendingDecision shouldBe null
            game.state.stack shouldBe emptyList()
            game.state.priorityPlayerId shouldBe last
            game.roundTrip()
            game.execute(DeclareBlockers(last, emptyMap())).error shouldBe null
            game.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>().playerId shouldBe first
            game.state.priorityPlayerId shouldBe null
            game.roundTrip()
            game.selectTargets(listOf(attackers[0])).error shouldBe null
            game.assertWindow(active)
            game.state.stack.map { game.state.getEntity(it)!!.get<TriggeredAbilityOnStackComponent>()!!.sourceId }
                .single() shouldBe firstBlocker
        }

        test("PB12 multiple defenders' block triggers wait through paid tax then stack in APNAP order") {
            val (game, active, first, _, last, attackers, firstBlocker, lastBlocker) =
                fourSeats(lifeSentry.name, lifeSentry.name, taxed = true)
            game.execute(DeclareBlockers(first, mapOf(firstBlocker to listOf(attackers[0])))).error shouldBe null
            game.payBlockTax()
            game.state.pendingDecision shouldBe null
            game.state.stack shouldBe emptyList()
            game.state.priorityPlayerId shouldBe last
            game.roundTrip()
            game.execute(DeclareBlockers(last, mapOf(lastBlocker to listOf(attackers[1])))).error shouldBe null
            game.state.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>().playerId shouldBe last
            game.state.stack shouldBe emptyList()
            game.roundTrip()
            game.payBlockTax()
            game.assertWindow(active)
            val triggers = game.state.stack.map { game.state.getEntity(it)!!.get<TriggeredAbilityOnStackComponent>()!! }
            triggers.map { it.controllerId } shouldBe listOf(first, last)
            triggers.map { it.sourceId } shouldBe listOf(firstBlocker, lastBlocker)
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.lifeTotal(first) shouldBe 21
            game.state.lifeTotal(last) shouldBe 21
        }

        test("PB13 a queued block-trigger token retains exact departure LKI through later payment and cleanup") {
            val (game, active, first, _, last, attackers, firstBlocker, lastBlocker) =
                fourSeats("Grizzly Bears", "Savannah Lions", taxed = true, lateSource = departingObserver.name)
            val source = game.findPermanent(departingObserver.name)!!
            val original = game.state.objectRef(source).shouldNotBeNull()
            game.state.projectedState.getController(source) shouldBe last
            game.execute(DeclareBlockers(first, mapOf(firstBlocker to listOf(attackers[0])))).error shouldBe null
            game.payBlockTax()
            game.state.pendingDecision shouldBe null
            game.state.stack shouldBe emptyList()
            game.state.priorityPlayerId shouldBe last
            game.roundTrip()
            game.execute(DeclareBlockers(last, mapOf(lastBlocker to listOf(attackers[1])))).error shouldBe null
            game.state.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
            game.execute(ActivateAbility(last, source, departingObserver.activatedAbilities.single().id)).error shouldBe null
            game.state.getGraveyard(last).contains(source) shouldBe true
            val departed = game.state.getEntity(source)!!.get<LastKnownPermanentComponent>()!!.snapshot
            departed.objectRef shouldBe original
            departed.controllerId shouldBe last
            departed.ownerId shouldBe last
            departed.wasToken shouldBe true
            game.state.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>().playerId shouldBe last
            game.state.stack shouldBe emptyList()
            game.state.hasPriority(last) shouldBe false
            game.roundTrip()
            game.payBlockTax(autoPay = false)
            game.state.getEntity(source) shouldBe null
            game.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>().playerId shouldBe last
            game.state.priorityPlayerId shouldBe null
            game.state.stack shouldBe emptyList()
            game.selectTargets(listOf(attackers[0])).error shouldBe null
            game.assertWindow(active)
            val trigger = game.state.getEntity(game.state.stack.single())!!
                .get<TriggeredAbilityOnStackComponent>()!!
            trigger.controllerId shouldBe last
            trigger.sourceId shouldBe source
            trigger.objectReferences.origin shouldBe original
            trigger.lastKnownSourceSnapshot shouldBe departed
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.getGraveyard(active).contains(attackers[0]) shouldBe true
            game.state.lifeTotal(first) shouldBe 20
            game.state.lifeTotal(last) shouldBe 21
            game.state.stack shouldBe emptyList()
        }

        test("PB14 a final defender's concession abandons tax but preserves the prior defender's trigger") {
            val (game, active, first, _, last, attackers, firstBlocker, lastBlocker) =
                fourSeats(targetingSentry.name, "Grizzly Bears", taxed = true)
            game.execute(DeclareBlockers(first, mapOf(firstBlocker to listOf(attackers[0])))).error shouldBe null
            game.payBlockTax()
            game.state.pendingDecision shouldBe null
            game.state.stack shouldBe emptyList()
            game.state.pendingBlockTriggers.single().sourceId shouldBe firstBlocker
            game.execute(DeclareBlockers(last, mapOf(lastBlocker to listOf(attackers[1])))).error shouldBe null
            game.state.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>().playerId shouldBe last
            game.roundTrip()
            game.execute(Concede(last)).error shouldBe null
            game.state.gameOver shouldBe false
            game.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>().playerId shouldBe first
            game.state.priorityPlayerId shouldBe null
            game.state.pendingBlockTriggers shouldBe emptyList()
            game.state.getEntity(lastBlocker) shouldBe null
            game.roundTrip()
            game.selectTargets(listOf(attackers[0])).error shouldBe null
            game.assertWindow(active)
            game.state.getEntity(game.state.stack.single())!!.get<TriggeredAbilityOnStackComponent>()!!.sourceId shouldBe firstBlocker
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.getEntity(attackers[0])!!.get<DamageComponent>()!!.amount shouldBe 1
            game.state.stack shouldBe emptyList()
            game.state.continuationStack shouldBe emptyList()
        }

        test("PB15 owner concession preserves a remaining controller's queued source LKI without early placement") {
            val (game, active, first, spectator, last, attackers, firstBlocker, _) =
                fourSeats("Grizzly Bears", "Savannah Lions", lateSource = departingObserver.name)
            val source = game.findPermanent(departingObserver.name)!!
            // Legal fixed state after control changes: the spectator owns the source and its
            // battlefield zone, while the first defender controls the source and its trigger.
            game.state = game.state.removeFromZone(ZoneKey(last, Zone.BATTLEFIELD), source)
                .updateEntity(source) { container ->
                    container.with(container.get<CardComponent>()!!.copy(ownerId = spectator))
                        .with(OwnerComponent(spectator)).with(ControllerComponent(first))
                }.addToZone(ZoneKey(spectator, Zone.BATTLEFIELD), source)
            val original = game.state.objectRef(source).shouldNotBeNull()
            game.state.projectedState.getController(source) shouldBe first
            game.execute(DeclareBlockers(first, mapOf(firstBlocker to listOf(attackers[0])))).error shouldBe null
            game.state.pendingBlockTriggers.single().controllerId shouldBe first
            game.state.pendingDecision shouldBe null
            game.state.stack shouldBe emptyList()
            game.roundTrip()
            val departure = game.execute(Concede(spectator))
            departure.error shouldBe null
            game.state.getEntity(source) shouldBe null
            departure.events.filterIsInstance<ZoneChangeEvent>().none { it.entityId == source } shouldBe true
            game.state.priorityPlayerId shouldBe last
            game.state.hasPriority(last) shouldBe false
            game.state.pendingDecision shouldBe null
            game.state.stack shouldBe emptyList()
            val snapshot = game.state.pendingBlockTriggers.single().lastKnownSourceSnapshot.shouldNotBeNull()
            snapshot.objectRef shouldBe original
            snapshot.ownerId shouldBe spectator
            snapshot.controllerId shouldBe first
            snapshot.wasToken shouldBe true
            snapshot.keywords.containsAll(setOf(Keyword.DEATHTOUCH.name, Keyword.LIFELINK.name)) shouldBe true
            game.roundTrip()
            game.execute(DeclareBlockers(last, emptyMap())).error shouldBe null
            game.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>().playerId shouldBe first
            game.state.priorityPlayerId shouldBe null
            game.selectTargets(listOf(attackers[0])).error shouldBe null
            game.assertWindow(active)
            val placed = game.state.getEntity(game.state.stack.single())!!.get<TriggeredAbilityOnStackComponent>()!!
            placed.controllerId shouldBe first
            placed.objectReferences.origin shouldBe original
            placed.lastKnownSourceSnapshot shouldBe snapshot
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.getGraveyard(active).contains(attackers[0]) shouldBe true
            game.state.lifeTotal(first) shouldBe 21
            game.state.lifeTotal(last) shouldBe 20
            game.state.stack shouldBe emptyList()
            game.state.pendingBlockTriggers shouldBe emptyList()
        }

        test("PB16 concession during two-player block payment clears the actual waiting batch at game end") {
            val game = setup().withCardOnBattlefield(1, tax.name).withCardOnBattlefield(2, manaLord.name).build()
            val (attacker, defender) = game.beginBlocks()
            val blocker = game.findPermanent("Grizzly Bears")!!
            val lord = game.findPermanent(manaLord.name)!!
            game.execute(DeclareBlockers(defender, mapOf(blocker to listOf(attacker)))).error shouldBe null
            game.state.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
            game.execute(ActivateAbility(defender, lord, manaLord.activatedAbilities.single().id)).error shouldBe null
            game.state.pendingBlockTriggers.single().sourceId shouldBe lord
            game.state.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
            game.state.stack shouldBe emptyList()
            game.roundTrip()
            game.execute(Concede(defender)).error shouldBe null
            game.state.gameOver shouldBe true
            game.state.winnerId shouldBe game.player1Id
            game.state.pendingBlockTriggers shouldBe emptyList()
            game.state.pendingDecision shouldBe null
            game.state.continuationStack shouldBe emptyList()
            game.state.pendingCastPriority shouldBe null
            game.state.priorityPlayerId shouldBe null
            game.state.stack shouldBe emptyList()
            game.state.lifeTotal(game.player1Id) shouldBe 20
        }
    }
}
