package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.fra.cards.MedicsKitesail
import com.wingedsheep.mtg.sets.definitions.sos.cards.FollowTheLumarets
import com.wingedsheep.mtg.sets.definitions.sos.cards.PestMascot
import com.wingedsheep.mtg.sets.definitions.stx.cards.BloodResearcher
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * Medic's Kitesail seed-free challenger qualification.
 *
 * These scenarios deliberately register the unreleased FRA card directly rather than globally
 * registering the set. That keeps official deck admission fail-closed while proving the exact
 * Equipment, attack-trigger, lifegain-payoff and Follow-the-Lumarets interactions required by the
 * Pest challenger protocol.
 */
class MedicsKitesailScenarioTest : FunSpec({

    val projector = StateProjector()

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(
            TestCards.all + listOf(
                MedicsKitesail,
                BloodResearcher,
                PestMascot,
                FollowTheLumarets,
            )
        )
        driver.initMirrorMatch(Deck.of("Forest" to 40), startingPlayer = 0)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return driver
    }

    fun equip(
        driver: GameTestDriver,
        equipment: com.wingedsheep.sdk.model.EntityId,
        creature: com.wingedsheep.sdk.model.EntityId,
    ) {
        val equipId = MedicsKitesail.activatedAbilities.single().id
        driver.giveColorlessMana(driver.player1, 2)
        driver.submit(
            ActivateAbility(
                playerId = driver.player1,
                sourceId = equipment,
                abilityId = equipId,
                targets = listOf(ChosenTarget.Permanent(creature)),
            )
        ).isSuccess shouldBe true
        driver.bothPass().isSuccess shouldBe true
    }

    test("equip two attaches and grants exactly plus one power and flying") {
        val driver = createDriver()
        val creature = driver.putCreatureOnBattlefield(driver.player1, "Grizzly Bears")
        val kitesail = driver.putPermanentOnBattlefield(driver.player1, MedicsKitesail.name)

        projector.getProjectedPower(driver.state, creature) shouldBe 2
        driver.state.projectedState.hasKeyword(creature, Keyword.FLYING.name) shouldBe false

        equip(driver, kitesail, creature)

        driver.state.getEntity(kitesail)?.get<AttachedToComponent>()?.targetId shouldBe creature
        projector.getProjectedPower(driver.state, creature) shouldBe 3
        projector.getProjectedToughness(driver.state, creature) shouldBe 2
        driver.state.projectedState.hasKeyword(creature, Keyword.FLYING.name) shouldBe true
    }

    test("attack trigger gains life after attackers are declared and before combat damage") {
        val driver = createDriver()
        val creature = driver.putCreatureOnBattlefield(driver.player1, "Grizzly Bears")
        val kitesail = driver.putPermanentOnBattlefield(driver.player1, MedicsKitesail.name)
        driver.removeSummoningSickness(creature)
        equip(driver, kitesail, creature)

        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        driver.getLifeTotal(driver.player1) shouldBe 20
        driver.declareAttackers(driver.player1, listOf(creature), driver.player2).isSuccess shouldBe true

        // The trigger is on the stack; attack declaration alone does not gain life.
        driver.getLifeTotal(driver.player1) shouldBe 20
        driver.stackSize shouldBe 1

        driver.bothPass().isSuccess shouldBe true
        driver.getLifeTotal(driver.player1) shouldBe 21
    }

    test("one Kitesail life-gain event grows both Blood Researcher and Pest Mascot once") {
        val driver = createDriver()
        val researcher = driver.putCreatureOnBattlefield(driver.player1, BloodResearcher.name)
        val mascot = driver.putCreatureOnBattlefield(driver.player1, PestMascot.name)
        val kitesail = driver.putPermanentOnBattlefield(driver.player1, MedicsKitesail.name)
        driver.removeSummoningSickness(researcher)
        equip(driver, kitesail, researcher)

        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        driver.declareAttackers(driver.player1, listOf(researcher), driver.player2).isSuccess shouldBe true

        // Resolve Kitesail, then both independent lifegain-payoff triggers.
        driver.bothPass().isSuccess shouldBe true
        while (driver.stackSize > 0) {
            driver.bothPass().isSuccess shouldBe true
        }

        driver.getLifeTotal(driver.player1) shouldBe 21
        (driver.state.getEntity(researcher)?.get<CountersComponent>()
            ?.getCount(Counters.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 1
        (driver.state.getEntity(mascot)?.get<CountersComponent>()
            ?.getCount(Counters.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 1
    }

    test("moving Kitesail moves its power and flying grants to the new creature") {
        val driver = createDriver()
        val first = driver.putCreatureOnBattlefield(driver.player1, "Grizzly Bears")
        val second = driver.putCreatureOnBattlefield(driver.player1, "Grizzly Bears")
        val kitesail = driver.putPermanentOnBattlefield(driver.player1, MedicsKitesail.name)

        equip(driver, kitesail, first)
        projector.getProjectedPower(driver.state, first) shouldBe 3
        driver.state.projectedState.hasKeyword(first, Keyword.FLYING.name) shouldBe true

        equip(driver, kitesail, second)

        driver.state.getEntity(kitesail)?.get<AttachedToComponent>()?.targetId shouldBe second
        projector.getProjectedPower(driver.state, first) shouldBe 2
        driver.state.projectedState.hasKeyword(first, Keyword.FLYING.name) shouldBe false
        projector.getProjectedPower(driver.state, second) shouldBe 3
        driver.state.projectedState.hasKeyword(second, Keyword.FLYING.name) shouldBe true
    }

    test("destroying the equipped creature leaves Kitesail on the battlefield unattached") {
        val driver = createDriver()
        val creature = driver.putCreatureOnBattlefield(driver.player1, "Grizzly Bears")
        val kitesail = driver.putPermanentOnBattlefield(driver.player1, MedicsKitesail.name)
        equip(driver, kitesail, creature)

        val doomBlade = driver.putCardInHand(driver.player1, "Doom Blade")
        driver.giveMana(driver.player1, Color.BLACK, 2)
        driver.castSpell(driver.player1, doomBlade, listOf(creature)).isSuccess shouldBe true
        driver.bothPass().isSuccess shouldBe true

        driver.getPermanents(driver.player1).contains(creature) shouldBe false
        driver.getPermanents(driver.player1).contains(kitesail) shouldBe true
        driver.state.getEntity(kitesail)?.get<AttachedToComponent>().shouldBeNull()
    }

    test("combat lifegain turns on Follow the Lumarets in the second main phase") {
        val driver = createDriver()
        val creature = driver.putCreatureOnBattlefield(driver.player1, "Grizzly Bears")
        val kitesail = driver.putPermanentOnBattlefield(driver.player1, MedicsKitesail.name)
        driver.removeSummoningSickness(creature)
        equip(driver, kitesail, creature)

        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        driver.declareAttackers(driver.player1, listOf(creature), driver.player2).isSuccess shouldBe true
        driver.bothPass().isSuccess shouldBe true
        driver.getLifeTotal(driver.player1) shouldBe 21

        driver.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        driver.putCardOnTopOfLibrary(driver.player1, "Centaur Courser")
        driver.putCardOnTopOfLibrary(driver.player1, "Centaur Courser")
        driver.putCardOnTopOfLibrary(driver.player1, "Grizzly Bears")
        driver.putCardOnTopOfLibrary(driver.player1, "Grizzly Bears")

        val follow = driver.putCardInHand(driver.player1, FollowTheLumarets.name)
        driver.giveMana(driver.player1, Color.GREEN, 1)
        driver.giveColorlessMana(driver.player1, 1)
        driver.castSpell(driver.player1, follow).isSuccess shouldBe true
        driver.bothPass().isSuccess shouldBe true

        val decision = driver.state.pendingDecision as SelectCardsDecision
        decision.maxSelections shouldBe 2
    }
})
