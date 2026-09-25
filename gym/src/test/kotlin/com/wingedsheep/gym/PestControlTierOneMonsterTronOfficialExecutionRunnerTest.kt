package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronOneShotBoundary
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import kotlin.time.Duration.Companion.hours

/** Compiled during validation; execution additionally requires an authenticated immutable claim. */
class PestControlTierOneMonsterTronOfficialExecutionRunnerTest : FunSpec({
    test("execute the four frozen Monster Tron assignments through the sealed one-shot boundary").config(
        enabled = System.getenv("PEST_MONSTER_TRON_OFFICIAL_MODE") == "EXECUTE",
        timeout = 5.hours,
    ) {
        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        PestControlTierOneMonsterTronOneShotBoundary.executeFromEnvironment(registry)
    }
})
