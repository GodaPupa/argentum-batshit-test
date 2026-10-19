package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.J
import com.wingedsheep.tooling.coverage.render
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.JsonObject

class BattleCardPredicateEmitterTest : FunSpec({
    fun emit(kind: String): String? {
        val node = J.parseToJsonElement("""{
            "_Action":"LookAtTheTopNumberCardsOfLibrary",
            "args":[{"_GameNumber":"Integer","args":3},[
                {"_LookAtTopOfLibraryAction":"MayRevealACardOfTypeAndPutIntoHand",
                 "args":{"_Cards":"IsCardtype","args":"$kind"}},
                {"_LookAtTopOfLibraryAction":"PutTheRemainingCardsOnTheBottomOfLibraryInARandomOrder"}
            ]]
        }""") as JsonObject
        return EmitCtx(emptySet()).renderLook(node, node["args"], null)?.let(::render)
    }
    test("Battle card filtering emits a parameterized fixed type and resolves its enum import") {
        val source = emit("Battle") ?: error("Battle filter unexpectedly scaffolded")
        source shouldContain "CardPredicate.HasCardType(CardType.BATTLE)"
        source shouldContain "SelectionMode.ChooseUpTo(DynamicAmount.Fixed(1))"
        importsFor(source.lines()) shouldContain "com.wingedsheep.sdk.core.CardType"
        importsFor(source.lines()) shouldContain "com.wingedsheep.sdk.scripting.predicates.CardPredicate"
    }
    test("an unknown card type still declines rather than dropping the selection restriction") {
        emit("UnknownType").shouldBeNull()
    }
})
