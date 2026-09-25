package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CastTimeCreatureTypeSource
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Fixed engine semantic fixtures. No official deck, seed allocation or sampled game is used. */
class CastPrioritySbaTest : FunSpec({
    val shot = card("Postcast Phyrexian Shot") {
        manaCost = "{R/P}"
        typeLine = "Instant"
        spell { val victim = target("target", Targets.Any); effect = Effects.DealDamage(1, victim) }
    }
    val sacrificeSpell = card("Postcast Sacrifice Fixture") {
        manaCost = "{U}"
        typeLine = "Instant"
        additionalCost(Costs.additional.SacrificePermanent(GameObjectFilter.Creature))
        spell { effect = Effects.GainLife(1) }
    }
    val lord = card("Postcast Toughness Lord") {
        manaCost = "{U}"
        typeLine = "Creature — Wizard"
        power = 1; toughness = 1
        staticAbility { ability = ModifyStats(0, 1, GroupFilter.AllCreaturesYouControl) }
        triggeredAbility { trigger = Triggers.Dies; effect = Effects.DrawCards(1) }
    }
    val fragile = card("Postcast Fragile Fixture") {
        manaCost = "{U}"
        typeLine = "Creature — Wizard"
        power = 0; toughness = 0
        triggeredAbility { trigger = Triggers.Dies; effect = Effects.DrawCards(1) }
    }
    val globalLord = card("Postcast Global Toughness Fixture") {
        manaCost = "{U}"; typeLine = "Creature — Wizard"; power = 1; toughness = 1
        staticAbility { ability = ModifyStats(0, 1, GroupFilter.AllCreatures) }
    }
    val castWatcher = card("Postcast Cast Life Fixture") {
        manaCost = "{U}"; typeLine = "Creature — Wizard"; power = 2; toughness = 3
        triggeredAbility { trigger = Triggers.YouCastSpell; effect = Effects.GainLife(1) }
    }
    val watcher = card("Postcast Target Watcher") {
        manaCost = "{U}"
        typeLine = "Creature — Wizard"
        power = 2; toughness = 3
        triggeredAbility {
            trigger = Triggers.YouCastSpell
            val victim = target("creature", Targets.Creature)
            effect = Effects.DealDamage(1, victim)
        }
    }
    val creature = card("Postcast Creature Fixture") {
        manaCost = "{U}"; typeLine = "Creature — Wizard"; power = 2; toughness = 2
        triggeredAbility { trigger = Triggers.Dies; effect = Effects.DrawCards(1) }
    }
    val typeSpell = card("Postcast Creature Type Fixture") {
        manaCost = "{0}"; typeLine = "Instant"
        additionalCost(Costs.additional.PayLife(2))
        castTimeCreatureTypeChoice = CastTimeCreatureTypeSource.GRAVEYARD
        spell { effect = Effects.GainLife(1) }
    }
    val modal = card("Postcast Nested Life Cost") {
        manaCost = "{U}"; typeLine = "Instant"
        additionalCost(Costs.additional.PayLife(2))
        spell {
            modal(chooseCount = 1) {
                mode("Gain 1 life") { effect = Effects.GainLife(1) }
                mode("Draw a card") { effect = Effects.DrawCards(1) }
            }
        }
    }
    val granter = card("Postcast Nested Recovery") {
        manaCost = "{U}"; typeLine = "Instant"
        spell {
            effect = Effects.Composite(
                GatherCardsEffect(CardSource.FromZone(Zone.GRAVEYARD,
                    filter = GameObjectFilter.InstantOrSorcery), storeAs = "nested"),
                Effects.CastFromCollectionWithoutPayingCost(from = "nested"),
                Effects.GainLife(3)
            )
        }
    }
    val json = Json { serializersModule = engineSerializersModule; encodeDefaults = true; allowStructuredMapKeys = true }

    fun fixture(seats: Int = 2): Pair<GameTestDriver, List<EntityId>> {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(shot, sacrificeSpell, lord, fragile, globalLord, castWatcher,
            watcher, creature, typeSpell, modal, granter))
        val players = driver.initMultiplayer(List(seats) { Deck.of("Island" to 40) }, startingPlayer = 0)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return driver to players
    }
    fun shotAction(caster: EntityId, spell: EntityId, victim: EntityId) = CastSpell(caster, spell,
        listOf(ChosenTarget.Player(victim)), paymentStrategy = PaymentStrategy.Explicit(emptyList(), listOf(Color.RED)))
    fun sacrifice(caster: EntityId, spell: EntityId, victim: EntityId) = CastSpell(caster, spell,
        additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(victim)))
    fun resolveOne(driver: GameTestDriver) {
        val top = driver.state.stack.last()
        repeat(driver.state.activePlayers.size) {
            if (driver.pendingDecision != null || driver.state.stack.lastOrNull() != top) return
            driver.passPriority(driver.priorityPlayer!!).error shouldBe null
        }
    }

    for (life in listOf(2, 3)) {
        test("a real Phyrexian life payment settles SBAs before returning priority (life=$life)") {
            val (driver, players) = fixture()
            val caster = players[0]; val opponent = players[1]
            driver.setLifeTotal(caster, life)
            val spell = driver.putCardInHand(caster, shot.name)
            val cast = driver.submit(shotAction(caster, spell, opponent))
            cast.error shouldBe null
            driver.getLifeTotal(caster) shouldBe life - 2
            driver.getLifeTotal(opponent) shouldBe 20
            cast.events.filterIsInstance<ResolvedEvent>() shouldBe emptyList()
            driver.state.pendingCastPriority shouldBe null
            driver.state.gameOver shouldBe (life == 2)
            driver.priorityPlayer shouldBe if (life == 2) null else caster
            if (life == 2) driver.state.winnerId shouldBe opponent
            else driver.state.stack shouldBe listOf(spell)
        }
    }

    test("a caster who paid their last life never receives a cast-trigger target prompt") {
        val (driver, players) = fixture()
        driver.putPermanentOnBattlefield(players[0], watcher.name)
        driver.setLifeTotal(players[0], 2)
        val spell = driver.putCardInHand(players[0], shot.name)
        driver.submit(shotAction(players[0], spell, players[1])).error shouldBe null
        driver.state.gameOver shouldBe true
        driver.pendingDecision shouldBe null
        driver.priorityPlayer shouldBe null
    }

    test("cost-sacrifice and subsequent zero-toughness death precede cast-trigger target selection") {
        val (driver, players) = fixture()
        val caster = players[0]
        val observer = driver.putPermanentOnBattlefield(caster, watcher.name)
        val anthem = driver.putPermanentOnBattlefield(caster, lord.name)
        val dependent = driver.putPermanentOnBattlefield(caster, fragile.name)
        val spell = driver.putCardInHand(caster, sacrificeSpell.name)
        driver.giveMana(caster, Color.BLUE, 1)
        val cast = driver.submit(sacrifice(caster, spell, anthem))
        cast.error shouldBe null
        val choice = driver.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
        choice.legalTargets.values.flatten().contains(dependent) shouldBe false
        choice.legalTargets.values.flatten().contains(anthem) shouldBe false
        driver.state.getGraveyard(caster).containsAll(listOf(anthem, dependent)) shouldBe true
        val deathIndex = cast.events.indexOfFirst { it is ZoneChangeEvent && it.entityId == dependent && it.toZone == Zone.GRAVEYARD }
        val choiceIndex = cast.events.indexOfFirst { it is DecisionRequestedEvent }
        (deathIndex >= 0 && deathIndex < choiceIndex) shouldBe true
        driver.submitTargetSelection(caster, listOf(observer)).error shouldBe null
        val sources = driver.state.stack.mapNotNull { driver.state.getEntity(it)?.get<TriggeredAbilityOnStackComponent>()?.sourceId }
        sources.count { it == anthem } shouldBe 1
        sources.count { it == dependent } shouldBe 1
        sources.count { it == observer } shouldBe 1
        driver.priorityPlayer shouldBe caster
        driver.state.pendingCastPriority shouldBe null
    }

    test("a nonactive cast trigger and active player's SBA death trigger form one APNAP batch") {
        val (driver, players) = fixture()
        val active = players[0]; val caster = players[1]
        val anthem = driver.putPermanentOnBattlefield(caster, globalLord.name)
        val dependent = driver.putPermanentOnBattlefield(active, fragile.name)
        val observer = driver.putPermanentOnBattlefield(caster, castWatcher.name)
        val spell = driver.putCardInHand(caster, sacrificeSpell.name)
        driver.giveMana(caster, Color.BLUE, 1)
        driver.passPriority(active).error shouldBe null
        driver.submit(sacrifice(caster, spell, anthem)).error shouldBe null
        driver.state.getGraveyard(active).contains(dependent) shouldBe true
        val waiting = driver.state.stack.mapNotNull {
            driver.state.getEntity(it)?.get<TriggeredAbilityOnStackComponent>()
        }
        waiting.map { it.controllerId } shouldBe listOf(active, caster)
        waiting.map { it.sourceId } shouldBe listOf(dependent, observer)
        driver.priorityPlayer shouldBe caster
        driver.state.pendingCastPriority shouldBe null
    }

    for (mode in listOf("answer", "owner-leaves", "third-party-leaves")) {
        test("an opposing commander choice preserves the nonactive caster's priority and death trigger ($mode)") {
            val (driver, players) = fixture(4)
            val caster = players[1]; val owner = players[2]
            driver.replaceState(driver.state.copy(format = Format.Commander()))
            // A deterministic ownership/control fixture: the caster controls another player's
            // commander. The actual sacrifice and optional command-zone decision are public actions.
            val controlled = driver.putPermanentOnBattlefield(caster, creature.name)
            driver.replaceState(driver.state.updateEntity(controlled) { entity ->
                entity.with(entity.get<CardComponent>()!!.copy(ownerId = owner))
                    .with(OwnerComponent(owner)).with(CommanderComponent(ownerId = owner))
            })
            val spell = driver.putCardInHand(caster, sacrificeSpell.name)
            driver.giveMana(caster, Color.BLUE, 1)
            driver.passPriority(players[0]).error shouldBe null
            driver.submit(sacrifice(caster, spell, controlled)).error shouldBe null
            val choice = driver.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
            choice.playerId shouldBe owner
            driver.state.pendingCastPriority!!.playerId shouldBe caster
            driver.state.pendingCastPriority!!.triggers.size shouldBe 1
            val encoded = json.encodeToString(driver.state)
            val restored = json.decodeFromString<GameState>(encoded)
            restored shouldBe driver.state
            driver.replaceState(restored)
            if (mode == "third-party-leaves") {
                val saved = driver.state.pendingCastPriority
                val frames = driver.state.continuationStack
                val conceded = driver.submit(Concede(players[3]))
                conceded.error shouldBe null
                driver.pendingDecision shouldBe choice
                driver.state.continuationStack shouldBe frames
                driver.state.pendingCastPriority shouldBe saved
                conceded.events.filterIsInstance<PriorityChangedEvent>() shouldBe emptyList()
            }
            val completed = if (mode == "owner-leaves") driver.submit(Concede(owner))
                else driver.submitDecision(owner, YesNoResponse(choice.id, true))
            completed.error shouldBe null
            completed.events.filterIsInstance<PriorityChangedEvent>() shouldBe listOf(PriorityChangedEvent(caster))
            driver.pendingDecision shouldBe null
            driver.priorityPlayer shouldBe caster
            driver.state.pendingCastPriority shouldBe null
            driver.state.stack.count { driver.state.getEntity(it)?.get<TriggeredAbilityOnStackComponent>()?.sourceId == controlled } shouldBe 1
        }
    }

    test("concession ending a game clears a pending commander question and both priority markers") {
        val (driver, players) = fixture()
        val caster = players[0]; val owner = players[1]
        driver.replaceState(driver.state.copy(format = Format.Commander()))
        val endingBoard = driver.putPermanentOnBattlefield(owner, creature.name)
        val controlled = driver.putPermanentOnBattlefield(caster, creature.name)
        driver.replaceState(driver.state.updateEntity(controlled) { entity ->
            entity.with(entity.get<CardComponent>()!!.copy(ownerId = owner))
                .with(OwnerComponent(owner)).with(CommanderComponent(ownerId = owner))
        })
        val spell = driver.putCardInHand(caster, sacrificeSpell.name)
        driver.giveMana(caster, Color.BLUE, 1)
        driver.submit(sacrifice(caster, spell, controlled)).error shouldBe null
        driver.pendingDecision.shouldBeInstanceOf<YesNoDecision>().playerId shouldBe owner
        driver.state.pendingCastPriority!!.playerId shouldBe caster
        val beforeEndEntity = driver.state.getEntity(endingBoard)
        val conceded = driver.submit(Concede(owner))
        conceded.error shouldBe null
        conceded.events.filterIsInstance<PlayerLostEvent>() shouldBe listOf(PlayerLostEvent(owner, GameEndReason.CONCESSION))
        conceded.events.filterIsInstance<GameEndedEvent>() shouldBe listOf(GameEndedEvent(caster, GameEndReason.CONCESSION))
        conceded.events.filterIsInstance<PriorityChangedEvent>() shouldBe emptyList()
        driver.state.gameOver shouldBe true
        driver.state.winnerId shouldBe caster
        driver.priorityPlayer shouldBe null
        driver.pendingDecision shouldBe null
        driver.state.continuationStack shouldBe emptyList()
        driver.state.pendingCastPriority shouldBe null
        driver.state.stackResolutionPendingPriority shouldBe false
        driver.state.getEntity(endingBoard) shouldBe beforeEndEntity
    }

    test("the creature-type casting continuation cannot bypass the last-life SBA boundary") {
        val (driver, players) = fixture()
        driver.putCardInGraveyard(players[0], creature.name)
        driver.setLifeTotal(players[0], 2)
        val spell = driver.putCardInHand(players[0], typeSpell.name)
        driver.castSpell(players[0], spell).error shouldBe null
        val choice = driver.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
        driver.state.gameOver shouldBe false // The cast is still incomplete.
        driver.submitDecision(players[0], OptionChosenResponse(choice.id, 0)).error shouldBe null
        driver.state.gameOver shouldBe true
        driver.state.winnerId shouldBe players[1]
        driver.priorityPlayer shouldBe null
        driver.state.pendingCastPriority shouldBe null
    }

    test("a nested modal life payment may recover within the same outer resolution before SBAs") {
        val (driver, players) = fixture()
        val caster = players[1]
        driver.setLifeTotal(caster, 2)
        val nested = driver.putCardInGraveyard(caster, modal.name)
        val outer = driver.putCardInHand(caster, granter.name)
        driver.giveMana(caster, Color.BLUE, 1)
        driver.passPriority(players[0]).error shouldBe null
        driver.castSpell(caster, outer).error shouldBe null
        resolveOne(driver)
        val choice = driver.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
        driver.state.stackResolutionPendingPriority shouldBe true
        driver.state.pendingCastPriority shouldBe null
        val completed = driver.submitDecision(caster, OptionChosenResponse(choice.id, 0))
        completed.error shouldBe null
        completed.events.filterIsInstance<LifeChangedEvent>().any { it.playerId == caster && it.newLife == 0 } shouldBe true
        driver.getLifeTotal(caster) shouldBe 3
        driver.state.gameOver shouldBe false
        driver.state.stack shouldBe listOf(nested)
        driver.state.continuationStack shouldBe emptyList()
        driver.priorityPlayer shouldBe players[0]
        driver.state.pendingCastPriority shouldBe null
    }

    test("an unaffordable life payment is rejected atomically and ordinary serialized states gain no field") {
        val (driver, players) = fixture()
        driver.setLifeTotal(players[0], 1)
        val spell = driver.putCardInHand(players[0], shot.name)
        val before = driver.state
        val rejected = driver.submit(shotAction(players[0], spell, players[1]))
        (rejected.error != null) shouldBe true
        rejected.state shouldBe before
        rejected.events shouldBe emptyList()
        json.encodeToString(GameState()).contains("pendingCastPriority") shouldBe false
    }
})
