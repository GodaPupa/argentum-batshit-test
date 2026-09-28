package com.wingedsheep.gym.manual

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Identity mapping only; no four-seat pilot or gameplay competence. */
class ManualPhaseTwoPublicOpponentAxesTest : FunSpec({
    test("all seven frozen public commander axes resolve exactly") {
        val cases = listOf(
            listOf("Hashaton, Scarab's Fist") to ManualOpponentAxis.HASHATON,
            listOf("Magda, Brazen Outlaw") to ManualOpponentAxis.MAGDA,
            listOf("Kraum, Ludevic's Opus", "Tymna the Weaver") to ManualOpponentAxis.BLUE_FARM,
            listOf("Shorikai, Genesis Engine") to ManualOpponentAxis.SHORIKAI,
            listOf("Sisay, Weatherlight Captain") to ManualOpponentAxis.SISAY,
            listOf("Rograkh, Son of Rohgahh", "Silas Renn, Seeker Adept") to ManualOpponentAxis.ROGSI,
            listOf("Kinnan, Bonder Prodigy") to ManualOpponentAxis.KINNAN,
        )
        cases.forEach { (names, axis) ->
            ManualPhaseTwoPublicOpponentAxes.classify(names) shouldBe axis
            ManualPhaseTwoPublicOpponentAxes.classify(names.reversed()) shouldBe axis
        }
    }
    test("incomplete or duplicated partner identity has no overlay") {
        ManualPhaseTwoPublicOpponentAxes.classify(listOf("Tymna the Weaver")) shouldBe null
        ManualPhaseTwoPublicOpponentAxes.classify(listOf(
            "Kraum, Ludevic's Opus", "Kraum, Ludevic's Opus")) shouldBe null
    }
})
