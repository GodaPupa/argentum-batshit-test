package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.GameEnvironment
import com.wingedsheep.gym.matchup.PEST_MONSTER_TRON_MAIN_SHA256
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronAdmission
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

/** Pregame own/public existence only; no keep/bottom parity or pair-pilot qualification. */
class PestMonsterLondonForestCertificateTest : ScenarioTestBase() {
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)
    private val frozenMain = PestControlTierOneMonsterTronAdmission.mainCounts.flatMap { (name, count) ->
        List(count) { name }
    }

    init {
        for (seat in listOf(0, 1)) {
            test("London Monster seat ${seat + 1} Forest existence ignores hidden library order") {
                val players = (0..1).map { index ->
                    PlayerConfig(if (index == seat) "Monster" else "Fixture opponent",
                        Deck(cards = if (index == seat) frozenMain else List(60) { "Forest" }))
                }
                val environment = GameEnvironment.create(cardRegistry)
                environment.reset(GameConfig(players = players, startingPlayerIndex = 0,
                    seed = 0xC25110L + seat))
                if (seat == 1) {
                    environment.stepExactlyOne(KeepHand(environment.playerIds[0]))
                }
                val actor = environment.playerIds[seat]
                val state = environment.state
                val epoch = ActorEpoch("monster-london-forest-certificate-v1", "seat-$seat", 0)
                val original = adapter.build(state, actor,
                    completeActorLegalActions(state, actor, enumerator), epoch, 0xC25120L + seat)
                val actual = state.getLibrary(actor).any { id ->
                    state.getEntity(id)!!.get<CardComponent>()!!.name == "Forest"
                }
                PestMonsterLondonForestCertificate.exists(original, PEST_MONSTER_TRON_MAIN_SHA256) shouldBe actual
                val library = ZoneKey(actor, Zone.LIBRARY)
                val reordered = state.copy(zones = state.zones + (library to state.getLibrary(actor).reversed()))
                val projected = adapter.build(reordered, actor,
                    completeActorLegalActions(reordered, actor, enumerator), epoch, 0xC25120L + seat)
                PestMonsterLondonForestCertificate.exists(projected, PEST_MONSTER_TRON_MAIN_SHA256) shouldBe actual
                shouldThrow<IllegalArgumentException> {
                    PestMonsterLondonForestCertificate.exists(original, "0".repeat(64))
                }
            }
        }
    }
}
