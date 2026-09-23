package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.PestControlTierOneMonoBlueTerrorOfficialExecutionInputLoader
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class PestControlTierOneMonoBlueTerrorOfficialExecutionInputLoaderTest : FunSpec({
    test("official loader is unreachable without exact environment acknowledgement") {
        shouldThrow<IllegalArgumentException> {
            PestControlTierOneMonoBlueTerrorOfficialExecutionInputLoader
                .loadValidatedFromEnvironment()
        }
    }

    test("arbitrary ZIP bytes cannot satisfy the pinned official loader") {
        shouldThrow<IllegalArgumentException> {
            PestControlTierOneMonoBlueTerrorOfficialExecutionInputLoader
                .loadValidatedArchive("not-the-frozen-artifact".toByteArray())
        }
    }
})
