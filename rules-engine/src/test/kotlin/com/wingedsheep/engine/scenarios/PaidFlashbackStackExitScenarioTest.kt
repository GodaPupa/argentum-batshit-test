package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.SpellCounteredEvent
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.model.GameRng
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.AlternativePaymentChoice
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GraveyardCardsHaveFlashback
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.MayCastFromGraveyard
import com.wingedsheep.sdk.scripting.effects.Gate
import com.wingedsheep.sdk.scripting.effects.GatedEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/**
 * CR 702.34a / 702.180a: only the alternative cost actually paid creates the any-stack-exit
 * replacement. These inline cards isolate the shared mechanic; they are not Pauper card entries.
 * The unchanged Eviscerator's Insight scenarios separately qualify the actual selected-pool card.
 */
class PaidFlashbackStackExitScenarioTest : FunSpec({
    val study = card("Paid Mode Flashback Study") {
        manaCost = "{3}"
        typeLine = "Instant"
        spell { effect = Effects.GainLife(2) }
        keywordAbility(KeywordAbility.flashback("{1}"))
    }
    val targeted = card("Paid Mode Flashback Spark") {
        manaCost = "{3}"
        typeLine = "Instant"
        spell {
            val creature = target("target creature", Targets.Creature)
            effect = Effects.Composite(Effects.DealDamage(1, creature), Effects.GainLife(2))
        }
        keywordAbility(KeywordAbility.flashback("{1}"))
    }
    val paused = card("Paid Mode Paused Study") {
        manaCost = "{3}"
        typeLine = "Instant"
        spell { effect = GatedEffect(Gate.MayDecide("Gain two life?"), Effects.GainLife(2)) }
        keywordAbility(KeywordAbility.flashback("{1}"))
    }
    val sacrificeStudy = card("Paid Mode Granted Study") {
        manaCost = "{3}"
        typeLine = "Instant"
        additionalCost(Costs.additional.SacrificePermanent(GameObjectFilter.Artifact))
        spell {
            effect = Effects.GainLife(2)
        }
    }
    val harmonizeStudy = card("Paid Mode Harmonize Study") {
        manaCost = "{3}"
        typeLine = "Instant"
        spell { effect = Effects.GainLife(2) }
        keywordAbility(KeywordAbility.harmonize("{3}"))
    }
    val flashbackGrant = card("Paid Mode Flashback Grant") {
        manaCost = "{0}"
        typeLine = "Artifact"
        staticAbility {
            ability = GraveyardCardsHaveFlashback(
                filter = GameObjectFilter.InstantOrSorcery,
                cost = ManaCost.parse("{1}")
            )
        }
    }
    val ordinaryPermission = card("Paid Mode Ordinary Graveyard Permission") {
        manaCost = "{0}"
        typeLine = "Artifact"
        staticAbility { ability = MayCastFromGraveyard(GameObjectFilter.InstantOrSorcery) }
    }
    val remand = card("Paid Mode Return Counter") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell {
            target("target spell", Targets.Spell)
            effect = Effects.Composite(Effects.CounterSpellToHand(), Effects.DrawCards(1))
        }
    }
    val movers = listOf(Zone.HAND, Zone.LIBRARY).associateWith { destination ->
        card("Paid Mode Stack Move $destination") {
            manaCost = "{0}"
            typeLine = "Instant"
            spell {
                target("target spell", Targets.Spell)
                effect = if (destination == Zone.HAND) Effects.ReturnToHand(EffectTarget.ContextTarget(0))
                    else Effects.PutOnTopOfLibrary(EffectTarget.ContextTarget(0))
            }
        }
    }
    val bounceCreature = card("Paid Mode Creature Bounce") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell {
            target("target creature", Targets.Creature)
            effect = Effects.ReturnToHand(EffectTarget.ContextTarget(0))
        }
    }
    val dedicatedBounces = listOf(false, true).map { acceptsPermanent ->
        card("Paid Mode Dedicated Bounce $acceptsPermanent") {
            manaCost = "{0}"
            typeLine = "Instant"
            spell {
                target("target spell", Targets.Spell)
                effect = if (acceptsPermanent) Effects.ReturnSpellOrPermanentToOwnersHand()
                    else Effects.ReturnSpellToOwnersHand()
            }
        }
    }
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }

    fun driver(seat: Int) = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(study, targeted, paused, sacrificeStudy, harmonizeStudy,
            flashbackGrant, ordinaryPermission, remand, bounceCreature) + movers.values + dedicatedBounces)
        initMirrorMatch(Deck.of("Swamp" to 40), skipMulligans = true, startingPlayer = seat)
        // All shuffled cards are identical. Every subsequent random action uses this recorded seed.
        replaceState(state.copy(rng = GameRng.seeded(2026092618L + seat)))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun castFlashback(d: GameTestDriver, caster: EntityId, spell: EntityId, implicit: Boolean = false,
        targets: List<ChosenTarget> = emptyList(), payment: AdditionalCostPayment? = null) {
        d.giveColorlessMana(caster, 1)
        d.submit(CastSpell(caster, spell, paymentStrategy = PaymentStrategy.FromPool,
            useAlternativeCost = true,
            alternativeCostType = if (implicit) null else AlternativeCostType.FLASHBACK,
            targets = targets, additionalCostPayment = payment)).error shouldBe null
        d.state.getEntity(caster)!!.get<ManaPoolComponent>()!!.colorless shouldBe 0
        d.state.getEntity(spell)!!.get<SpellOnStackComponent>()!!.alternativeCost shouldBe AlternativeCostType.FLASHBACK
    }

    fun respond(d: GameTestDriver, caster: EntityId, name: String, target: ChosenTarget) {
        val opponent = d.getOpponent(caster)
        if (d.state.priorityPlayerId != opponent) d.passPriority(caster).error shouldBe null
        val response = d.putCardInHand(opponent, name)
        if (name == "Counterspell") d.giveMana(opponent, Color.BLUE, 2)
        d.castSpellWithTargets(opponent, response, listOf(target)).error shouldBe null
        d.bothPass().error shouldBe null
    }

    fun assertExit(d: GameTestDriver, caster: EntityId, spell: EntityId, destination: Zone) {
        d.state.logicalZone(spell)?.zoneType shouldBe destination
        d.state.stack.contains(spell) shouldBe false
        d.state.getEntity(spell)!!.get<SpellOnStackComponent>() shouldBe null
        val departures = d.events.filterIsInstance<ZoneChangeEvent>()
            .filter { it.entityId == spell && it.fromZone == Zone.STACK }
        departures.size shouldBe 1
        departures.single().toZone shouldBe destination
        departures.single().ownerId shouldBe caster
        d.state.isCurrentObject(departures.single().oldObject!!) shouldBe false
    }

    for (seat in listOf(0, 1)) {
        test("player $seat explicit flashback pays its selected cost and exiles on resolution") {
            val d = driver(seat)
            val caster = d.activePlayer!!
            val spell = d.putCardInGraveyard(caster, study.name)
            castFlashback(d, caster, spell)
            d.bothPass().error shouldBe null
            d.getLifeTotal(caster) shouldBe 22
            assertExit(d, caster, spell, Zone.EXILE)
        }

        test("player $seat implicitly selected flashback is retained when countered") {
            val d = driver(seat)
            val caster = d.activePlayer!!
            val spell = d.putCardInGraveyard(caster, study.name)
            castFlashback(d, caster, spell, implicit = true)
            respond(d, caster, "Counterspell", ChosenTarget.Spell(spell))
            d.getLifeTotal(caster) shouldBe 20
            assertExit(d, caster, spell, Zone.EXILE)
        }

        test("player $seat a flashback spell with no legal targets exiles without resolving") {
            val d = driver(seat)
            val caster = d.activePlayer!!
            val creature = d.putCreatureOnBattlefield(d.getOpponent(caster), "Grizzly Bears")
            val spell = d.putCardInGraveyard(caster, targeted.name)
            castFlashback(d, caster, spell, targets = listOf(ChosenTarget.Permanent(creature)))
            respond(d, caster, bounceCreature.name, ChosenTarget.Permanent(creature))
            d.bothPass().error shouldBe null
            d.getLifeTotal(caster) shouldBe 20
            assertExit(d, caster, spell, Zone.EXILE)
        }

        test("player $seat a return-to-hand counter still exiles the flashback spell and draws") {
            val d = driver(seat)
            val caster = d.activePlayer!!
            val opponent = d.getOpponent(caster)
            val spell = d.putCardInGraveyard(caster, study.name)
            castFlashback(d, caster, spell)
            val handBefore = d.getHandSize(opponent)
            respond(d, caster, remand.name, ChosenTarget.Spell(spell))
            d.getHandSize(opponent) shouldBe handBefore + 1
            d.getLifeTotal(caster) shouldBe 20
            d.events.filterIsInstance<SpellCounteredEvent>().count { it.spellEntityId == spell } shouldBe 1
            assertExit(d, caster, spell, Zone.EXILE)
        }

        for ((destination, mover) in movers) {
            test("player $seat a non-counter stack move to $destination exiles the flashback spell") {
                val d = driver(seat)
                val caster = d.activePlayer!!
                val spell = d.putCardInGraveyard(caster, study.name)
                castFlashback(d, caster, spell)
                respond(d, caster, mover.name, ChosenTarget.Spell(spell))
                d.getLifeTotal(caster) shouldBe 20
                d.events.filterIsInstance<SpellCounteredEvent>().count { it.spellEntityId == spell } shouldBe 0
                assertExit(d, caster, spell, Zone.EXILE)
            }
        }

        for (bounce in dedicatedBounces) {
            test("player $seat ${bounce.name} respects the paid flashback exit replacement") {
                val d = driver(seat)
                val caster = d.activePlayer!!
                val spell = d.putCardInGraveyard(caster, study.name)
                castFlashback(d, caster, spell)
                respond(d, caster, bounce.name, ChosenTarget.Spell(spell))
                d.getLifeTotal(caster) shouldBe 20
                d.events.filterIsInstance<SpellCounteredEvent>().count { it.spellEntityId == spell } shouldBe 0
                assertExit(d, caster, spell, Zone.EXILE)
            }
        }

        for ((destination, mover) in movers) {
            test("player $seat a normal cast genuinely moves from stack to $destination") {
                val d = driver(seat)
                val caster = d.activePlayer!!
                val spell = d.putCardInHand(caster, study.name)
                d.giveColorlessMana(caster, 3)
                d.submit(CastSpell(caster, spell, paymentStrategy = PaymentStrategy.FromPool)).error shouldBe null
                d.state.getEntity(caster)!!.get<ManaPoolComponent>()!!.colorless shouldBe 0
                d.state.getEntity(spell)!!.get<SpellOnStackComponent>()!!.alternativeCost shouldBe null
                respond(d, caster, mover.name, ChosenTarget.Spell(spell))
                d.getLifeTotal(caster) shouldBe 20
                d.events.filterIsInstance<SpellCounteredEvent>().count { it.spellEntityId == spell } shouldBe 0
                assertExit(d, caster, spell, destination)
                if (destination == Zone.LIBRARY) d.state.getLibrary(caster).first() shouldBe spell
            }
        }

        for (countered in listOf(false, true)) {
            test("player $seat printed flashback does not exile an ordinary graveyard cast countered=$countered") {
                val d = driver(seat)
                val caster = d.activePlayer!!
                d.putPermanentOnBattlefield(caster, ordinaryPermission.name)
                val spell = d.putCardInGraveyard(caster, study.name)
                d.giveColorlessMana(caster, 3)
                d.submit(CastSpell(caster, spell, paymentStrategy = PaymentStrategy.FromPool)).error shouldBe null
                d.state.getEntity(caster)!!.get<ManaPoolComponent>()!!.colorless shouldBe 0
                d.state.getEntity(spell)!!.get<SpellOnStackComponent>()!!.alternativeCost shouldBe null
                if (countered) respond(d, caster, "Counterspell", ChosenTarget.Spell(spell))
                else d.bothPass().error shouldBe null
                d.getLifeTotal(caster) shouldBe if (countered) 20 else 22
                assertExit(d, caster, spell, Zone.GRAVEYARD)
            }
        }

        test("player $seat paying flashback remembers a granting source sacrificed in the same cost") {
            val d = driver(seat)
            val caster = d.activePlayer!!
            val grant = d.putPermanentOnBattlefield(caster, flashbackGrant.name)
            val spell = d.putCardInGraveyard(caster, sacrificeStudy.name)
            castFlashback(d, caster, spell, payment = AdditionalCostPayment(sacrificedPermanents = listOf(grant)))
            d.getGraveyard(caster).contains(grant) shouldBe true
            d.bothPass().error shouldBe null
            d.getLifeTotal(caster) shouldBe 22
            assertExit(d, caster, spell, Zone.EXILE)
        }

        test("player $seat paid flashback survives a serialized resolution choice") {
            val d = driver(seat)
            val caster = d.activePlayer!!
            val spell = d.putCardInGraveyard(caster, paused.name)
            castFlashback(d, caster, spell, implicit = true)
            d.bothPass().error shouldBe null
            (d.pendingDecision != null) shouldBe true
            d.replaceState(json.decodeFromString(GameState.serializer(),
                json.encodeToString(GameState.serializer(), d.state)))
            d.submitYesNo(caster, true).error shouldBe null
            d.getLifeTotal(caster) shouldBe 22
            assertExit(d, caster, spell, Zone.EXILE)
        }

        test("player $seat harmonize pays its reduced cost and exiles when countered") {
            val d = driver(seat)
            val caster = d.activePlayer!!
            val creature = d.putCreatureOnBattlefield(caster, "Grizzly Bears")
            val spell = d.putCardInGraveyard(caster, harmonizeStudy.name)
            d.giveColorlessMana(caster, 1)
            d.submit(CastSpell(caster, spell, paymentStrategy = PaymentStrategy.FromPool,
                useAlternativeCost = true, alternativeCostType = AlternativeCostType.HARMONIZE,
                alternativePayment = AlternativePaymentChoice(harmonizeCreature = creature))).error shouldBe null
            d.isTapped(creature) shouldBe true
            d.state.getEntity(caster)!!.get<ManaPoolComponent>()!!.colorless shouldBe 0
            d.state.getEntity(spell)!!.get<SpellOnStackComponent>()!!.alternativeCost shouldBe AlternativeCostType.HARMONIZE
            respond(d, caster, "Counterspell", ChosenTarget.Spell(spell))
            d.getLifeTotal(caster) shouldBe 20
            assertExit(d, caster, spell, Zone.EXILE)
        }
    }
})
