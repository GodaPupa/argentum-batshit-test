package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.pls.cards.StarCompass
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class StarCompassScenarioTest : FunSpec({

    val abilityId = StarCompass.activatedAbilities.first().id

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + StarCompass)
        d.initMirrorMatch(
            deck = Deck.of("Mountain" to 40),
            skipMulligans = true,
            startingPlayer = 0
        )
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    fun pool(d: GameTestDriver, player: EntityId): ManaPoolComponent =
        d.state.getEntity(player)?.get<ManaPoolComponent>() ?: ManaPoolComponent()

    fun activate(d: GameTestDriver, compass: EntityId, color: Color) =
        d.submit(
            ActivateAbility(
                playerId = d.player1,
                sourceId = compass,
                abilityId = abilityId,
                manaColorChoice = color
            )
        )

    test("Star Compass enters tapped") {
        val d = driver()
        val compass = d.putPermanentOnBattlefield(d.player1, "Star Compass")

        d.isTapped(compass) shouldBe true
    }

    test("a tapped basic land you control still contributes its producible color") {
        val d = driver()
        val caster = d.player1
        val island = d.putLandOnBattlefield(caster, "Island")
        d.tapPermanent(island)
        val compass = d.putPermanentOnBattlefield(caster, "Star Compass")
        d.untapPermanent(compass)

        activate(d, compass, Color.BLUE).isSuccess shouldBe true
        pool(d, caster).blue shouldBe 1
    }

    test("only your basic lands widen the available color set") {
        val d = driver()
        val caster = d.player1
        d.putLandOnBattlefield(caster, "Island")
        d.putLandOnBattlefield(caster, "Mountain")
        d.putLandOnBattlefield(caster, "City of Brass")
        val compass = d.putPermanentOnBattlefield(caster, "Star Compass")
        d.untapPermanent(compass)

        activate(d, compass, Color.BLUE).isSuccess shouldBe true
        pool(d, caster).blue shouldBe 1

        d.untapPermanent(compass)
        activate(d, compass, Color.RED).isSuccess shouldBe true
        pool(d, caster).red shouldBe 1

        d.untapPermanent(compass)
        activate(d, compass, Color.GREEN)

        withClue("nonbasic City of Brass must not make green available to Star Compass") {
            pool(d, caster).green shouldBe 0
        }
    }

    test("an opponent's basic land does not contribute a color") {
        val d = driver()
        val caster = d.player1
        d.putLandOnBattlefield(caster, "Mountain")
        d.putLandOnBattlefield(d.player2, "Island")
        val compass = d.putPermanentOnBattlefield(caster, "Star Compass")
        d.untapPermanent(compass)

        activate(d, compass, Color.BLUE)

        withClue("Star Compass reads basic lands you control, not an opponent's basics") {
            pool(d, caster).blue shouldBe 0
        }
    }
})
