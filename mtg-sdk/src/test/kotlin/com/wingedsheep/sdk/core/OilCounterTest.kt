package com.wingedsheep.sdk.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class OilCounterTest : FunSpec({
    test("the oil marker name resolves to its serializable counter type") {
        Counters.OIL shouldBe "oil"
        CounterType.fromName(Counters.OIL) shouldBe CounterType.OIL
    }
})
