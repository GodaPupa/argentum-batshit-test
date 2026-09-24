package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.PEST_MONSTER_TRON_FROZEN_SMOKE_ARCHIVE_SHA256
import com.wingedsheep.gym.matchup.PEST_MONSTER_TRON_FROZEN_SMOKE_VECTOR_SHA256
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronOfficialExecutionInputLoader
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PestControlTierOneMonsterTronOfficialArtifactValidationTest : FunSpec({
    test("official frozen ZIP is read-only validated without gameplay") {
        val input = PestControlTierOneMonsterTronOfficialExecutionInputLoader.loadValidatedFromEnvironment()
        input.archiveSha256 shouldBe PEST_MONSTER_TRON_FROZEN_SMOKE_ARCHIVE_SHA256
        input.vectorIdentity.orderedVectorSha256 shouldBe PEST_MONSTER_TRON_FROZEN_SMOKE_VECTOR_SHA256
        input.assignments.size shouldBe 4
        input.assignments.map { it.gameNumber } shouldBe listOf(1, 2, 3, 4)
        input.seeds.size shouldBe 4
        input.seeds.distinct().size shouldBe 4
        input.seeds.none { it == 0L } shouldBe true
    }
})
