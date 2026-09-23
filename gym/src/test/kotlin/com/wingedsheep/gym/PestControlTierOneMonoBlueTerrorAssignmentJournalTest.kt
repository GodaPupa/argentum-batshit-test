package com.wingedsheep.gym

import io.kotest.core.spec.style.FunSpec

class PestControlTierOneMonoBlueTerrorAssignmentJournalTest : FunSpec({
    terrorAssignmentJournalCases().forEach { (name, scenario) ->
        test(name) { scenario() }
    }
})
