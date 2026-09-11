package com.wingedsheep.gym

import com.wingedsheep.ai.solitaire.ProjectXDeck
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes

class ProjectXBirchloreReplayRegressionTest : FunSpec({
    test("Experiment A pairs 3 and 20 replay cleanly under executable-use Birchlore policy").config(timeout = 10.minutes) {
        val regressions = listOf(
            3 to 747476981334286865L,
            20 to 375820755781804720L,
        )
        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }

        regressions.forEach { (pair, seed) ->
            val replay = runProjectXGoldfish(registry, seed, pair, ProjectXDeck.V02)
            replay.auditErrors shouldBe emptyList()
            (replay.stopReason !in setOf("WEDGED", "ILLEGAL_ACTION", "MAX_ACTIONS")).shouldBeTrue()
        }
    }
})
