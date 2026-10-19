package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ManaSpentEvent
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.engine.state.components.identity.CommanderRegistryComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Seed-free rules scenarios for the exact frozen commander, never sampled pod gameplay. */
class AnimarSoulOfElementsScenarioTest : ScenarioTestBase() {
    init {
        fun counters(game: TestGame): Int = game.state.getEntity(game.findPermanent("Animar, Soul of Elements")!!)
            ?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

        fun addCounters(game: TestGame, count: Int) {
            game.state = game.state.updateEntity(game.findPermanent("Animar, Soul of Elements")!!) {
                it.with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, count))
            }
        }

        for (priorCasts in 0..2) {
            test("Animar actual command cast pays base colors plus tax for $priorCasts prior casts") {
                val game = scenario().withPlayers("P1", "P2")
                    .withCardInCommandZone(1, "Animar, Soul of Elements")
                    .withLandsOnBattlefield(1, "Forest", 1 + 2 * priorCasts)
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                val commander = game.state.getZone(ZoneKey(game.player1Id, Zone.COMMAND)).single()
                game.state = game.state.updateEntity(commander) {
                    it.with(CommanderComponent(ownerId = game.player1Id, castsFromCommandZone = priorCasts))
                }.updateEntity(game.player1Id) {
                    it.with(CommanderRegistryComponent(commanderIds = listOf(commander)))
                }
                val cast = game.execute(CastSpell(game.player1Id, commander))
                cast.error shouldBe null
                cast.events.filterIsInstance<ManaSpentEvent>().sumOf { it.total } shouldBe 3 + 2 * priorCasts
                game.state.getEntity(commander)!!.get<CommanderComponent>()!!.castsFromCommandZone shouldBe priorCasts + 1
                game.resolveStack()
                game.findPermanent("Animar, Soul of Elements") shouldBe commander
                counters(game) shouldBe 0
            }
        }

        test("creature casting grows Animar after payment and before creature resolution") {
            val game = scenario().withPlayers("P1", "P2")
                .withCardOnBattlefield(1, "Animar, Soul of Elements")
                .withCardInHand(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Grizzly Bears").error shouldBe null
            counters(game) shouldBe 0
            game.state.stack.size shouldBe 2
            game.resolveStack()
            counters(game) shouldBe 1
            game.findPermanent("Grizzly Bears") shouldNotBe null
        }

        test("existing counters reduce only the generic part of the next creature spell") {
            val game = scenario().withPlayers("P1", "P2")
                .withCardOnBattlefield(1, "Animar, Soul of Elements")
                .withCardInHand(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            addCounters(game, 1)
            game.castSpell(1, "Grizzly Bears").error shouldBe null
            game.resolveStack()
            counters(game) shouldBe 2
            game.findPermanent("Grizzly Bears") shouldNotBe null
        }

        test("the triggering spell cannot spend its not-yet-earned counter") {
            val game = scenario().withPlayers("P1", "P2")
                .withCardOnBattlefield(1, "Animar, Soul of Elements")
                .withCardInHand(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Grizzly Bears").error shouldNotBe null
            counters(game) shouldBe 0
        }

        test("arbitrarily many counters cannot pay colored mana") {
            val game = scenario().withPlayers("P1", "P2")
                .withCardOnBattlefield(1, "Animar, Soul of Elements")
                .withCardInHand(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Island", 1)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            addCounters(game, 20)
            game.castSpell(1, "Grizzly Bears").error shouldNotBe null
            counters(game) shouldBe 20
        }

        test("noncreature spells receive neither reduction nor a cast counter") {
            val game = scenario().withPlayers("P1", "P2")
                .withCardOnBattlefield(1, "Animar, Soul of Elements")
                .withCardInHand(1, "Sol Ring")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            addCounters(game, 20)
            game.castSpell(1, "Sol Ring").error shouldNotBe null
            counters(game) shouldBe 20
        }

        test("white and black targeting are prevented while red targeting remains legal") {
            val game = scenario().withPlayers("P1", "P2")
                .withCardOnBattlefield(1, "Animar, Soul of Elements")
                .withCardInHand(2, "Swords to Plowshares")
                .withCardInHand(2, "Doom Blade")
                .withCardInHand(2, "Lightning Bolt")
                .withLandsOnBattlefield(2, "Plains", 1)
                .withLandsOnBattlefield(2, "Swamp", 2)
                .withLandsOnBattlefield(2, "Mountain", 1)
                .withActivePlayer(2).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val animar = game.findPermanent("Animar, Soul of Elements")!!
            game.castSpell(2, "Swords to Plowshares", animar).error shouldNotBe null
            game.castSpell(2, "Doom Blade", animar).error shouldNotBe null
            game.castSpell(2, "Lightning Bolt", animar).error shouldBe null
            game.resolveStack()
            game.findPermanent("Animar, Soul of Elements") shouldBe null
        }
    }
}
