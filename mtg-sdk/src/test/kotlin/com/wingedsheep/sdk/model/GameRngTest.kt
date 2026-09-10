package com.wingedsheep.sdk.model

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import kotlin.math.abs

class GameRngTest : DescribeSpec({

    describe("determinism") {
        it("produces the same sequence from the same seed") {
            val a = generateSequence(GameRng.seeded(42L)) { it.nextLong().second }
                .drop(1).take(100).map { it.state }.toList()
            val b = generateSequence(GameRng.seeded(42L)) { it.nextLong().second }
                .drop(1).take(100).map { it.state }.toList()
            a shouldContainExactly b
        }

        it("produces different sequences from different seeds") {
            val (a, _) = GameRng.seeded(1L).nextLong()
            val (b, _) = GameRng.seeded(2L).nextLong()
            a shouldNotBe b
        }

        it("never mutates the receiver — re-drawing yields the same value") {
            val rng = GameRng.seeded(7L)
            val (first, _) = rng.nextLong()
            val (again, _) = rng.nextLong()
            first shouldBe again
        }
    }

    describe("nextInt") {
        it("stays within [0, bound)") {
            var rng = GameRng.seeded(123L)
            repeat(10_000) {
                val (v, next) = rng.nextInt(6)
                v shouldBeGreaterThanOrEqual 0
                v shouldBeLessThan 6
                rng = next
            }
        }

        it("rejects a non-positive bound") {
            try {
                GameRng.seeded(1L).nextInt(0)
                throw AssertionError("expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }

        it("is roughly uniform across buckets") {
            var rng = GameRng.seeded(999L)
            val counts = IntArray(10)
            val n = 100_000
            repeat(n) {
                val (v, next) = rng.nextInt(10)
                counts[v]++
                rng = next
            }
            val expected = n / 10.0
            counts.forEach { abs(it - expected) shouldBeLessThan expected } // within 100% of expected
        }
    }

    describe("nextDouble") {
        it("stays within [0, 1)") {
            var rng = GameRng.seeded(55L)
            repeat(10_000) {
                val (v, next) = rng.nextDouble()
                v shouldBeGreaterThanOrEqual 0.0
                v shouldBeLessThan 1.0
                rng = next
            }
        }
    }

    describe("shuffle") {
        it("is a permutation — preserves the multiset of elements") {
            val input = (1..52).toList()
            val (shuffled, _) = GameRng.seeded(2024L).shuffle(input)
            shuffled shouldContainExactlyInAnyOrder input
        }

        it("does not mutate the input list") {
            val input = (1..10).toList()
            GameRng.seeded(1L).shuffle(input)
            input shouldContainExactly (1..10).toList()
        }

        it("is deterministic for a given seed") {
            val input = (1..20).toList()
            val (a, _) = GameRng.seeded(8L).shuffle(input)
            val (b, _) = GameRng.seeded(8L).shuffle(input)
            a shouldContainExactly b
        }

        it("handles empty and singleton lists") {
            GameRng.seeded(1L).shuffle(emptyList<Int>()).first shouldBe emptyList()
            GameRng.seeded(1L).shuffle(listOf(7)).first shouldBe listOf(7)
        }
    }

    describe("pick") {
        it("returns an element of the list") {
            val list = listOf("a", "b", "c")
            val (v, _) = GameRng.seeded(3L).pick(list)
            (v in list) shouldBe true
        }
    }

    describe("split") {
        it("yields a child stream that diverges from the parent") {
            val (child, parent) = GameRng.seeded(100L).split()
            child.state shouldNotBe parent.state
            val (childVal, _) = child.nextLong()
            val (parentVal, _) = parent.nextLong()
            childVal shouldNotBe parentVal
        }

        it("is deterministic — same seed splits the same way") {
            val (c1, p1) = GameRng.seeded(5L).split()
            val (c2, p2) = GameRng.seeded(5L).split()
            c1 shouldBe c2
            p1 shouldBe p2
        }
    }

    describe("serialization") {
        it("round-trips through JSON preserving stream position") {
            val rng = GameRng.seeded(77L).nextLong().second.nextLong().second
            val json = Json.encodeToString(GameRng.serializer(), rng)
            val restored = Json.decodeFromString(GameRng.serializer(), json)
            restored shouldBe rng
            // A restored generator continues the identical sequence.
            rng.nextLong().first shouldBe restored.nextLong().first
        }
    }
})
