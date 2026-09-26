package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.combat.BlockedComponent
import com.wingedsheep.engine.state.components.combat.BlockingComponent
import com.wingedsheep.engine.state.components.combat.AttackersDeclaredThisCombatComponent
import com.wingedsheep.engine.state.components.combat.BlockersDeclaredThisCombatComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Exact THS card on excluded fixed fixtures; every submitted action replays state and ordered events. */
class PurphorosGodOfTheForgeScenarioTest : FunSpec({
    val name = "Purphoros, God of the Forge"
    fun game(seats: Int = 2) = GameTestDriver().apply {
        MtgSetCatalog.all.forEach { set -> registerCards(set.cards); registerCards(set.basicLands) }
        registerCards(PredefinedTokens.allTokens)
        registerCards(listOf(
            card("Devotion Red Pip Fixture") { manaCost = "{R}"; typeLine = "Enchantment" },
            card("Devotion Double Pip Fixture") {
                manaCost = "{R}{R}"; typeLine = "Creature — Goblin"; power = 2; toughness = 2
            },
            card("Devotion Hybrid Fixture") { manaCost = "{R/G}{2/R}{R/P}"; typeLine = "Enchantment" },
            card("Devotion Silence Fixture") {
                manaCost = "{U}"; typeLine = "Instant"
                spell { effect = Effects.RemoveAllAbilities(target("permanent", Targets.NonlandPermanent)) }
            },
        ))
        val deck = Deck.of("Forest" to 40)
        val initialized = GameInitializer(cardRegistry).initializeGame(GameConfig(
            players = (1..seats).map { PlayerConfig("Fixture $it", deck) },
            skipMulligans = true, startingPlayerIndex = 0, seed = 0x50555250484f524fL,
        ))
        replaceState(initialized.state)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
        replaceState(state.copy(zones = state.zones.mapValues { (key, cards) ->
            if (key.zoneType == Zone.HAND) emptyList() else cards
        }))
    }
    fun p1(d: GameTestDriver) = d.state.turnOrder[0]
    fun p2(d: GameTestDriver) = d.state.turnOrder[1]
    fun pips(d: GameTestDriver, player: EntityId, n: Int) =
        List(n) { d.putPermanentOnBattlefield(player, "Devotion Red Pip Fixture") }
    fun mana(d: GameTestDriver, player: EntityId, color: Color, amount: Int) = d.giveMana(player, color, amount)
    fun submit(d: GameTestDriver, action: GameAction): ExecutionResult {
        val before = SerializationTestSupport.roundTrip(d.state)
        val actual = d.submit(action)
        val replay = ActionProcessor(d.cardRegistry).process(before, action).result
        replay.error shouldBe actual.error
        SerializationTestSupport.encodeState(replay.state) shouldBe SerializationTestSupport.encodeState(actual.state)
        SerializationTestSupport.encodeEvents(replay.events) shouldBe SerializationTestSupport.encodeEvents(actual.events)
        return actual
    }
    fun resolve(d: GameTestDriver) {
        var actions = 0
        while (d.state.stack.isNotEmpty()) {
            check(actions++ < 100) { "Fixture stack did not settle" }
            check(d.pendingDecision == null) { "Unexpected unresolved choice: ${d.pendingDecision}" }
            submit(d, PassPriority(d.state.priorityPlayerId ?: error("Stack without priority"))).error shouldBe null
        }
    }
    fun cast(d: GameTestDriver, who: EntityId, card: String, target: EntityId? = null): EntityId {
        val id = d.putCardInHand(who, card)
        submit(d, CastSpell(who, id,
            targets = target?.let { listOf(ChosenTarget.Permanent(it)) } ?: emptyList(),
            paymentStrategy = PaymentStrategy.FromPool)).error shouldBe null
        return id
    }
    fun activate(d: GameTestDriver, who: EntityId, id: EntityId): ExecutionResult =
        submit(d, ActivateAbility(who, id, d.cardRegistry.requireCard(name).activatedAbilities.single().id,
            paymentStrategy = PaymentStrategy.FromPool))
    fun nextTurn(d: GameTestDriver) {
        val before = d.state.turnNumber
        var steps = 0
        while (d.state.turnNumber == before) {
            check(steps++ < 100)
            check(d.pendingDecision == null) { "Unexpected fixture cleanup choice" }
            val actor = d.state.priorityPlayerId ?: error("No priority while advancing fixture")
            val action = when {
                d.state.step == Step.DECLARE_ATTACKERS && actor == d.state.activePlayerId &&
                    d.state.getEntity(actor)?.has<AttackersDeclaredThisCombatComponent>() != true ->
                    DeclareAttackers(actor, emptyMap())
                d.state.step == Step.DECLARE_BLOCKERS && actor != d.state.activePlayerId &&
                    d.state.getEntity(actor)?.has<BlockersDeclaredThisCombatComponent>() != true ->
                    DeclareBlockers(actor, emptyMap())
                else -> PassPriority(actor)
            }
            submit(d, action).error shouldBe null
        }
    }

    test("spell remains a creature on stack and resolves to a legendary enchantment below devotion five") {
        val d = game()
        mana(d, p1(d), Color.RED, 4)
        val god = cast(d, p1(d), name)
        d.state.getEntity(god)!!.get<CardComponent>()!!.typeLine.isCreature shouldBe true
        d.state.isSpellOnStack(god) shouldBe true
        resolve(d)
        d.state.projectedState.isCreature(god) shouldBe false
        d.state.projectedState.getSubtypes(god) shouldBe emptySet()
        d.state.projectedState.hasType(god, "ENCHANTMENT") shouldBe true
        d.state.projectedState.isLegendary(god) shouldBe true
        d.state.projectedState.hasKeyword(god, Keyword.INDESTRUCTIBLE) shouldBe true
        d.getLifeTotal(p1(d)) shouldBe 20
        d.getLifeTotal(p2(d)) shouldBe 20
    }

    test("four other red pips plus its own pip meet the exact creature threshold") {
        val d = game()
        pips(d, p1(d), 4)
        mana(d, p1(d), Color.RED, 4)
        val god = cast(d, p1(d), name)
        resolve(d)
        d.state.projectedState.isCreature(god) shouldBe true
        d.state.projectedState.getPower(god) shouldBe 6
        d.state.projectedState.getToughness(god) shouldBe 5
        d.state.projectedState.getSubtypes(god) shouldBe setOf("God")
        d.getLifeTotal(p2(d)) shouldBe 20
    }

    test("hybrid monocolored hybrid and Phyrexian symbols each contribute one red devotion") {
        val d = game()
        d.putPermanentOnBattlefield(p1(d), "Devotion Hybrid Fixture")
        val god = d.putPermanentOnBattlefield(p1(d), name)
        d.state.projectedState.isCreature(god) shouldBe false
        pips(d, p1(d), 1)
        d.state.projectedState.isCreature(god) shouldBe true
    }

    test("opposing permanents and mana symbols in rules text do not provide devotion") {
        val d = game()
        pips(d, p2(d), 6)
        repeat(6) { d.putPermanentOnBattlefield(p1(d), "Mountain") }
        val god = d.putPermanentOnBattlefield(p1(d), name)
        d.state.projectedState.isCreature(god) shouldBe false
    }

    test("stealing a red permanent immediately removes its contribution from the previous controller") {
        val d = game()
        pips(d, p2(d), 3)
        val support = d.putCreatureOnBattlefield(p2(d), "Devotion Double Pip Fixture")
        val god = d.putPermanentOnBattlefield(p2(d), name)
        d.state.projectedState.isCreature(god) shouldBe true
        mana(d, p1(d), Color.RED, 3)
        cast(d, p1(d), "Act of Treason", support)
        resolve(d)
        d.state.projectedState.getController(support) shouldBe p1(d)
        d.state.projectedState.isCreature(god) shouldBe false
    }

    test("a stolen God uses the new controller's devotion") {
        val d = game()
        pips(d, p2(d), 4)
        val god = d.putPermanentOnBattlefield(p2(d), name)
        d.state.projectedState.isCreature(god) shouldBe true
        mana(d, p1(d), Color.RED, 3)
        cast(d, p1(d), "Act of Treason", god)
        resolve(d)
        d.state.projectedState.getController(god) shouldBe p1(d)
        d.state.projectedState.isCreature(god) shouldBe false
    }

    test("a creature entry deals two to every opponent in a four-player fixture while God is not a creature") {
        val d = game(4)
        val god = d.putPermanentOnBattlefield(p1(d), name)
        d.state.projectedState.isCreature(god) shouldBe false
        mana(d, p1(d), Color.GREEN, 2)
        cast(d, p1(d), "Grizzly Bears")
        resolve(d)
        d.getLifeTotal(p1(d)) shouldBe 20
        d.state.turnOrder.drop(1).forEach { d.getLifeTotal(it) shouldBe 18 }
    }

    test("opponent creature entry does not trigger the God") {
        val d = game()
        d.putPermanentOnBattlefield(p2(d), name)
        mana(d, p1(d), Color.GREEN, 2)
        cast(d, p1(d), "Grizzly Bears")
        resolve(d)
        d.getLifeTotal(p1(d)) shouldBe 20
        d.getLifeTotal(p2(d)) shouldBe 20
    }

    test("a noncreature entry does not trigger the God") {
        val d = game()
        d.putPermanentOnBattlefield(p1(d), name)
        mana(d, p1(d), Color.RED, 1)
        cast(d, p1(d), "Sol Ring")
        resolve(d)
        d.getLifeTotal(p2(d)) shouldBe 20
    }

    test("two simultaneous creature tokens generate two independent damage triggers") {
        val d = game()
        d.putPermanentOnBattlefield(p1(d), name)
        mana(d, p1(d), Color.RED, 2)
        cast(d, p1(d), "Dragon Fodder")
        resolve(d)
        d.getLifeTotal(p2(d)) shouldBe 16
    }

    test("an already triggered damage ability survives its source returning to hand") {
        val d = game()
        val god = d.putPermanentOnBattlefield(p1(d), name)
        mana(d, p1(d), Color.GREEN, 2)
        cast(d, p1(d), "Grizzly Bears")
        repeat(2) { submit(d, PassPriority(d.state.priorityPlayerId!!)).error shouldBe null }
        d.state.stack.size shouldBe 1
        d.getLifeTotal(p2(d)) shouldBe 20
        mana(d, p1(d), Color.BLUE, 2)
        cast(d, p1(d), "Into the Roil", god)
        resolve(d)
        d.state.getHand(p1(d)).contains(god) shouldBe true
        d.getLifeTotal(p2(d)) shouldBe 18
    }

    test("ability removal preserves the type-changing rule but disables later damage triggers") {
        val d = game()
        val god = d.putPermanentOnBattlefield(p1(d), name)
        mana(d, p1(d), Color.BLUE, 1)
        cast(d, p1(d), "Devotion Silence Fixture", god)
        resolve(d)
        d.state.projectedState.isCreature(god) shouldBe false
        d.state.projectedState.hasKeyword(god, Keyword.INDESTRUCTIBLE) shouldBe false
        mana(d, p1(d), Color.GREEN, 2)
        cast(d, p1(d), "Grizzly Bears")
        resolve(d)
        d.getLifeTotal(p2(d)) shouldBe 20
    }

    test("indestructible prevents destruction while God is a noncreature enchantment") {
        val d = game()
        val god = d.putPermanentOnBattlefield(p1(d), name)
        mana(d, p1(d), Color.WHITE, 2)
        cast(d, p1(d), "Disenchant", god)
        resolve(d)
        d.state.getBattlefield().contains(god) shouldBe true
    }

    test("indestructible prevents destruction while God is a creature") {
        val d = game()
        pips(d, p1(d), 4)
        val god = d.putPermanentOnBattlefield(p1(d), name)
        d.state.projectedState.isCreature(god) shouldBe true
        mana(d, p1(d), Color.WHITE, 2)
        cast(d, p1(d), "Disenchant", god)
        resolve(d)
        d.state.getBattlefield().contains(god) shouldBe true
    }

    test("noncreature God can pump only creatures currently controlled as the ability resolves") {
        val d = game()
        val god = d.putPermanentOnBattlefield(p1(d), name)
        val own = d.putCreatureOnBattlefield(p1(d), "Grizzly Bears")
        val other = d.putCreatureOnBattlefield(p2(d), "Grizzly Bears")
        mana(d, p1(d), Color.RED, 3)
        activate(d, p1(d), god).error shouldBe null
        resolve(d)
        d.state.projectedState.getPower(own) shouldBe 3
        d.state.projectedState.getToughness(own) shouldBe 2
        d.state.projectedState.getPower(other) shouldBe 2
        d.isTapped(god) shouldBe false
        val late = d.putCreatureOnBattlefield(p1(d), "Grizzly Bears")
        d.state.projectedState.getPower(late) shouldBe 2
        pips(d, p1(d), 4)
        d.state.projectedState.isCreature(god) shouldBe true
        d.state.projectedState.getPower(god) shouldBe 6
    }

    test("creature God receives its own pump and repeated activations add independently") {
        val d = game()
        pips(d, p1(d), 4)
        val god = d.putPermanentOnBattlefield(p1(d), name)
        mana(d, p1(d), Color.RED, 6)
        activate(d, p1(d), god).error shouldBe null
        resolve(d)
        activate(d, p1(d), god).error shouldBe null
        resolve(d)
        d.state.projectedState.getPower(god) shouldBe 8
        d.state.projectedState.getToughness(god) shouldBe 5
    }

    test("insufficient activation mana is rejected without changing returned or current state") {
        val d = game()
        val god = d.putPermanentOnBattlefield(p1(d), name)
        mana(d, p1(d), Color.RED, 2)
        val before = d.state
        val result = activate(d, p1(d), god)
        result.isSuccess shouldBe false
        result.newState shouldBe before
        d.state shouldBe before
    }

    test("counters remain while God is not a creature and apply when devotion restores its creature type") {
        val d = game()
        val god = d.putPermanentOnBattlefield(p1(d), name)
        d.replaceState(d.state.updateEntity(god) { it.with(CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 1))) })
        d.state.projectedState.isCreature(god) shouldBe false
        d.state.getEntity(god)!!.get<CountersComponent>()!!.counters[CounterType.PLUS_ONE_PLUS_ONE] shouldBe 1
        pips(d, p1(d), 4)
        d.state.projectedState.isCreature(god) shouldBe true
        d.state.projectedState.getPower(god) shouldBe 7
        d.state.projectedState.getToughness(god) shouldBe 6
    }

    test("temporary pump ends through actual end-of-turn cleanup") {
        val d = game()
        pips(d, p1(d), 4)
        val god = d.putPermanentOnBattlefield(p1(d), name)
        mana(d, p1(d), Color.RED, 3)
        activate(d, p1(d), god).error shouldBe null
        resolve(d)
        d.state.projectedState.getPower(god) shouldBe 7
        nextTurn(d)
        d.state.projectedState.getPower(god) shouldBe 6
    }

    test("a creature-targeted spell fizzles after a response lowers devotion below five") {
        val d = game()
        val support = pips(d, p1(d), 4)
        val god = d.putPermanentOnBattlefield(p1(d), name)
        mana(d, p1(d), Color.GREEN, 1)
        cast(d, p1(d), "Giant Growth", god)
        mana(d, p1(d), Color.BLUE, 2)
        cast(d, p1(d), "Into the Roil", support.last())
        resolve(d)
        d.state.projectedState.isCreature(god) shouldBe false
        pips(d, p1(d), 1)
        d.state.projectedState.isCreature(god) shouldBe true
        d.state.projectedState.getPower(god) shouldBe 6
    }

    test("a declared attacking God leaves combat after a legal response lowers devotion") {
        val d = game()
        val support = pips(d, p1(d), 4)
        val god = d.putPermanentOnBattlefield(p1(d), name)
        d.removeSummoningSickness(god)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        submit(d, DeclareAttackers(p1(d), mapOf(god to p2(d)))).error shouldBe null
        d.state.getEntity(god)!!.has<AttackingComponent>() shouldBe true
        mana(d, p1(d), Color.BLUE, 2)
        cast(d, p1(d), "Into the Roil", support.last())
        resolve(d)
        d.state.projectedState.isCreature(god) shouldBe false
        d.state.getEntity(god)!!.has<AttackingComponent>() shouldBe false
        pips(d, p1(d), 1)
        d.state.projectedState.isCreature(god) shouldBe true
        d.state.getEntity(god)!!.has<AttackingComponent>() shouldBe false
    }

    test("a declared blocking God leaves combat after devotion loss while the attacker remains blocked") {
        val d = game()
        val support = pips(d, p2(d), 4)
        val god = d.putPermanentOnBattlefield(p2(d), name)
        val attacker = d.putCreatureOnBattlefield(p1(d), "Grizzly Bears")
        d.removeSummoningSickness(attacker)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        submit(d, DeclareAttackers(p1(d), mapOf(attacker to p2(d)))).error shouldBe null
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        submit(d, DeclareBlockers(p2(d), mapOf(god to listOf(attacker)))).error shouldBe null
        d.state.getEntity(god)!!.has<BlockingComponent>() shouldBe true
        val actor = d.state.priorityPlayerId!!
        mana(d, actor, Color.BLUE, 2)
        cast(d, actor, "Into the Roil", support.last())
        resolve(d)
        d.state.projectedState.isCreature(god) shouldBe false
        d.state.getEntity(god)!!.has<BlockingComponent>() shouldBe false
        d.state.getEntity(attacker)!!.get<BlockedComponent>()!!.blockerIds shouldBe emptyList()
    }

    for (otherPips in listOf(3, 4, 5)) {
        test("entry replacement uses pre-entry devotion and creature trigger uses post-entry devotion with $otherPips other pips") {
            val d = game()
            pips(d, p1(d), otherPips)
            d.putPermanentOnBattlefield(p2(d), "Authority of the Consuls")
            mana(d, p1(d), Color.RED, 4)
            val god = cast(d, p1(d), name)
            resolve(d)
            d.isTapped(god) shouldBe (otherPips >= 5)
            d.state.projectedState.isCreature(god) shouldBe (otherPips + 1 >= 5)
            d.getLifeTotal(p2(d)) shouldBe (if (otherPips + 1 >= 5) 21 else 20)
        }
    }
})
