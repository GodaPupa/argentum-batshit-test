package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.nph.cards.PhyrexianMetamorph
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.EntersAsCopy
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/** Exact NPH #42 scenarios. No Manual gameplay, policies, or admission. */
class PhyrexianMetamorphScenarioTest : FunSpec({
    val specimen = card("Metamorph Test Specimen") {
        manaCost = "{2}{G}"
        typeLine = "Enchantment Creature — Bear"
        power = 2
        toughness = 3
        keywords(Keyword.TRAMPLE)
        activatedAbility { cost = Costs.Free; effect = Effects.GainLife(1) }
    }
    val rock = card("Metamorph Test Rock") {
        manaCost = "{3}"
        typeLine = "Artifact"
        activatedAbility { cost = Costs.Free; effect = Effects.GainLife(1) }
    }
    val etb = card("Metamorph Test ETB Specimen") {
        manaCost = "{2}{G}"
        typeLine = "Creature — Bear"
        power = 2
        toughness = 3
        etb { effect = Effects.DrawCards(1) }
    }
    val plainClone = card("Metamorph Test Plain Clone") {
        manaCost = "{0}"
        typeLine = "Creature — Shapeshifter"
        power = 0
        toughness = 5
        replacementEffect(EntersAsCopy())
    }
    val entry = card("Metamorph Test Library Entry") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell {
            effect = Effects.Pipeline {
                val top = gather(CardSource.TopOfLibrary(DynamicAmount.Fixed(1)))
                move(top, CardDestination.ToZone(Zone.BATTLEFIELD))
            }
        }
    }
    val bounce = card("Metamorph Test Bounce") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell {
            val creature = target("target creature", Targets.Creature)
            effect = Effects.ReturnToHand(creature)
        }
    }
    val boost = card("Metamorph Test Boost") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell {
            val creature = target("target creature", Targets.Creature)
            effect = Effects.ModifyStats(5, 5, creature)
        }
    }
    val enchantment = card("Metamorph Test Enchantment") {
        manaCost = "{0}"
        typeLine = "Enchantment"
    }

    fun driver() = GameTestDriver().also {
        it.registerCards(TestCards.all + listOf(PhyrexianMetamorph, specimen, rock, etb, plainClone, entry, bounce, boost, enchantment))
        it.initMirrorMatch(Deck.of("Island" to 40))
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun component(d: GameTestDriver, id: EntityId) = d.state.getEntity(id)?.get<CardComponent>().shouldNotBeNull()
    fun choose(d: GameTestDriver, selected: EntityId?) {
        (d.state.pendingDecision as? SelectCardsDecision).shouldNotBeNull()
        d.submitCardSelection(d.player1, listOfNotNull(selected)).error shouldBe null
    }
    fun cast(d: GameTestDriver): EntityId {
        val id = d.putCardInHand(d.player1, PhyrexianMetamorph.name)
        d.giveMana(d.player1, Color.BLUE, 4)
        d.castSpell(d.player1, id).error shouldBe null
        d.bothPass().error shouldBe null
        return id
    }
    fun artifactCopyOf(source: CardComponent, owner: EntityId) = source.copy(
        ownerId = owner,
        typeLine = source.typeLine.copy(cardTypes = source.typeLine.cardTypes + CardType.ARTIFACT),
    )

    test("blue mana casts a creature copy preserving all characteristics and adding artifact") {
        val d = driver()
        val source = d.putCreatureOnBattlefield(d.player2, specimen.name)
        val expected = artifactCopyOf(component(d, source), d.player1)
        val copy = cast(d)
        d.getLifeTotal(d.player1) shouldBe 20
        choose(d, source)
        component(d, copy) shouldBe expected
        d.state.getBattlefield(d.player1).contains(copy) shouldBe true
        d.submit(ActivateAbility(d.player1, copy, specimen.activatedAbilities.single().id)).error shouldBe null
        d.bothPass().error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 21
    }

    test("three mana and two life cast an artifact copy without making it a creature") {
        val d = driver()
        val source = d.putCreatureOnBattlefield(d.player2, rock.name)
        val expected = artifactCopyOf(component(d, source), d.player1)
        val islands = List(3) { d.putLandOnBattlefield(d.player1, "Island") }
        val copy = d.putCardInHand(d.player1, PhyrexianMetamorph.name)
        d.submit(CastSpell(d.player1, copy, paymentStrategy = PaymentStrategy.Explicit(
            manaAbilitiesToActivate = islands, phyrexianLifePayments = listOf(Color.BLUE),
        ))).error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 18
        d.bothPass().error shouldBe null
        choose(d, source)
        component(d, copy) shouldBe expected
        component(d, copy).typeLine.cardTypes shouldBe setOf(CardType.ARTIFACT)
    }

    test("declining a copy puts the printed zero toughness artifact creature into the graveyard") {
        val d = driver()
        d.putCreatureOnBattlefield(d.player2, specimen.name)
        val copy = cast(d)
        choose(d, null)
        d.state.getBattlefield().contains(copy) shouldBe false
        d.getGraveyardCardNames(d.player1).contains(PhyrexianMetamorph.name) shouldBe true
        component(d, copy).typeLine shouldBe PhyrexianMetamorph.typeLine
    }

    test("no eligible artifact or creature means no copy decision and the printed card dies") {
        val d = driver()
        val copy = cast(d)
        d.state.pendingDecision shouldBe null
        d.state.getBattlefield().contains(copy) shouldBe false
        d.getGraveyardCardNames(d.player1).contains(PhyrexianMetamorph.name) shouldBe true
    }

    test("direct library entry chooses a creature and still adds artifact") {
        val d = driver()
        val source = d.putCreatureOnBattlefield(d.player2, specimen.name)
        val expected = artifactCopyOf(component(d, source), d.player1)
        val copy = d.putCardOnTopOfLibrary(d.player1, PhyrexianMetamorph.name)
        val spell = d.putCardInHand(d.player1, entry.name)
        d.castSpell(d.player1, spell).error shouldBe null
        d.bothPass().error shouldBe null
        choose(d, source)
        component(d, copy) shouldBe expected
    }

    test("copying a permanent that is already a copy uses its copiable identity") {
        val d = driver()
        val source = d.putCreatureOnBattlefield(d.player2, specimen.name)
        val first = d.putCardInHand(d.player1, plainClone.name)
        d.castSpell(d.player1, first).error shouldBe null
        d.bothPass().error shouldBe null
        choose(d, source)
        val expected = artifactCopyOf(component(d, first), d.player1)
        val copy = cast(d)
        choose(d, first)
        component(d, copy) shouldBe expected
    }

    test("leaving the battlefield restores Phyrexian Metamorph's printed identity") {
        val d = driver()
        val source = d.putCreatureOnBattlefield(d.player2, specimen.name)
        val copy = cast(d)
        choose(d, source)
        val spell = d.putCardInHand(d.player1, bounce.name)
        d.castSpell(d.player1, spell, listOf(copy)).error shouldBe null
        d.bothPass().error shouldBe null
        d.getHand(d.player1).contains(copy) shouldBe true
        val c = component(d, copy)
        c.name shouldBe PhyrexianMetamorph.name
        c.typeLine shouldBe PhyrexianMetamorph.typeLine
        c.manaCost shouldBe PhyrexianMetamorph.manaCost
        c.baseStats shouldBe PhyrexianMetamorph.creatureStats
    }

    test("the copied creature's enter ability triggers exactly once") {
        val d = driver()
        val source = d.putCreatureOnBattlefield(d.player2, etb.name)
        val copy = cast(d)
        choose(d, source)
        val hand = d.getHandSize(d.player1)
        d.state.stack.size shouldBe 1
        d.bothPass().error shouldBe null
        d.getHandSize(d.player1) shouldBe hand + 1
        component(d, copy).typeLine.cardTypes.contains(CardType.ARTIFACT) shouldBe true
    }

    test("tapped state and a temporary power toughness boost are not copied") {
        val d = driver()
        val source = d.putCreatureOnBattlefield(d.player2, specimen.name)
        d.tapPermanent(source)
        val boostSpell = d.putCardInHand(d.player1, boost.name)
        d.castSpell(d.player1, boostSpell, listOf(source)).error shouldBe null
        d.bothPass().error shouldBe null
        d.state.projectedState.getPower(source) shouldBe 7
        d.state.projectedState.getToughness(source) shouldBe 8
        val copy = cast(d)
        choose(d, source)
        d.state.getEntity(copy)?.has<TappedComponent>() shouldBe false
        d.state.projectedState.getPower(copy) shouldBe 2
        d.state.projectedState.getToughness(copy) shouldBe 3
    }

    test("plain enchantments are excluded while both artifact and creature choices are offered") {
        val d = driver()
        val creature = d.putCreatureOnBattlefield(d.player2, specimen.name)
        val artifact = d.putCreatureOnBattlefield(d.player2, rock.name)
        val excluded = d.putCreatureOnBattlefield(d.player2, enchantment.name)
        cast(d)
        val decision = (d.state.pendingDecision as? SelectCardsDecision).shouldNotBeNull()
        decision.options.contains(creature) shouldBe true
        decision.options.contains(artifact) shouldBe true
        decision.options.contains(excluded) shouldBe false
        choose(d, creature)
    }
})
