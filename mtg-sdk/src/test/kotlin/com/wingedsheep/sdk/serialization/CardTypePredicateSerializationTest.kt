package com.wingedsheep.sdk.serialization

import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.predicates.CardPredicate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CardTypePredicateSerializationTest : FunSpec({
    for (cardType in CardType.entries) {
        test("fixed $cardType predicate round-trips without discriminator collision") {
            val predicate: CardPredicate = CardPredicate.HasCardType(cardType)
            val encoded = CardSerialization.json.encodeToString(CardPredicate.serializer(), predicate)
            val fields = CardSerialization.json.parseToJsonElement(encoded).jsonObject
            fields["type"]!!.jsonPrimitive.content shouldBe "HasCardType"
            fields["cardType"]!!.jsonPrimitive.content shouldBe cardType.name
            CardSerialization.json.decodeFromString(CardPredicate.serializer(), encoded) shouldBe predicate
        }
    }
    test("fixed type composes in an alternative filter without changing legacy predicates") {
        val filter = GameObjectFilter.CreatureOrPlaneswalker or GameObjectFilter.Any.withCardType(CardType.BATTLE)
        val encoded = CardSerialization.json.encodeToString(GameObjectFilter.serializer(), filter)
        CardSerialization.json.decodeFromString(GameObjectFilter.serializer(), encoded) shouldBe filter
    }
})
