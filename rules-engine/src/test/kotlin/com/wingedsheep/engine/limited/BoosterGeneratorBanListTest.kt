package com.wingedsheep.engine.limited

import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.ScryfallMetadata
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain

/**
 * Pins the tournament host ban list: any card name on [BoosterGenerator]'s `bannedCardNames`
 * is filtered out of every generated booster / sealed pool, across the single-set, multi-set,
 * chaos, and explicit-distribution paths. Matching is case-insensitive. The list never affects
 * basic lands and an empty list is a no-op.
 */
class BoosterGeneratorBanListTest : DescribeSpec({

    fun common(name: String) = CardDefinition.creature(
        name = name,
        manaCost = ManaCost.parse("{1}{G}"),
        subtypes = emptySet(),
        power = 2,
        toughness = 2,
        metadata = ScryfallMetadata(collectorNumber = name.hashCode().toString()),
    )

    // A pool of commons large enough that StandardBooster never exhausts it; one card is the
    // ban target. With 14 commons, a pack draws 12 distinct, so the target shows up in nearly
    // every unbanned pack — a sharp control for "banned ⇒ absent".
    val banTarget = "Grizzly Bears"
    fun poolOf(setCode: String) = (1..13).map { common("$setCode Common $it") } + common(banTarget)

    fun generator(vararg setCodes: String) = BoosterGenerator(
        setCodes.associateWith { code ->
            BoosterGenerator.SetConfig(
                setCode = code,
                setName = "Set $code",
                cards = poolOf(code),
                basicLands = emptyList(),
            )
        }
    )

    describe("single-set booster") {
        it("excludes a banned card across many packs") {
            val gen = generator("AAA")
            repeat(60) {
                gen.generateBooster("AAA", bannedCardNames = setOf(banTarget)).map { it.name } shouldNotContain banTarget
            }
        }

        it("still includes the card when not banned (control)") {
            val gen = generator("AAA")
            val names = (1..60).flatMap { gen.generateBooster("AAA").map { c -> c.name } }
            names shouldContain banTarget
        }

        it("matches the ban name case-insensitively") {
            val gen = generator("AAA")
            repeat(40) {
                gen.generateBooster("AAA", bannedCardNames = setOf("grizzly BEARS")).map { it.name } shouldNotContain banTarget
            }
        }

        it("treats an empty ban list as a no-op") {
            val gen = generator("AAA")
            val names = (1..60).flatMap { gen.generateBooster("AAA", bannedCardNames = emptySet()).map { c -> c.name } }
            names shouldContain banTarget
        }
    }

    describe("sealed pool") {
        it("excludes a banned card from a single-set pool") {
            val gen = generator("AAA")
            gen.generateSealedPool("AAA", boosterCount = 6, bannedCardNames = setOf(banTarget))
                .map { it.name } shouldNotContain banTarget
        }

        it("excludes a banned card from a seeded multi-set pool") {
            val gen = generator("AAA", "BBB")
            gen.generateSealedPool(listOf("AAA", "BBB"), boosterCount = 6, distributionSeed = 42L, bannedCardNames = setOf(banTarget))
                .map { it.name } shouldNotContain banTarget
        }

        it("excludes a banned card from an explicit-distribution pool") {
            val gen = generator("AAA", "BBB")
            gen.generateSealedPool(mapOf("AAA" to 3, "BBB" to 3), bannedCardNames = setOf(banTarget))
                .map { it.name } shouldNotContain banTarget
        }

        it("excludes a banned card from a chaos pool") {
            val gen = generator("AAA", "BBB")
            gen.generateSealedPool(listOf("AAA", "BBB"), boosterCount = 6, chaos = true, bannedCardNames = setOf(banTarget))
                .map { it.name } shouldNotContain banTarget
        }
    }
})
