package com.wingedsheep.gameserver.lobby

import com.wingedsheep.engine.limited.BoosterGenerator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * The tournament lobby's "Random Set" pick is *deferred*: a host adds a [TournamentLobby.RANDOM_SET_CODE]
 * placeholder that stays hidden (displayed as "Random Set") until the game starts, when
 * [TournamentLobby.resolveRandomSets] rolls a concrete set. This pins that reveal contract — the pool
 * must stay unresolved in the lobby and only concretise (avoiding extension/partial/duplicate sets) at
 * start, mirroring the Quick Game deferred roll.
 */
class TournamentRandomSetTest : FunSpec({

    fun setConfig(code: String, name: String, extension: Boolean = false, incomplete: Boolean = false) =
        BoosterGenerator.SetConfig(
            setCode = code,
            setName = name,
            cards = emptyList(),
            basicLands = emptyList(),
            incomplete = incomplete,
            sealedSupported = !incomplete,
            extensionSet = extension,
        )

    // Two regular complete sets, one extension set, one partial (incomplete) set.
    val generator = BoosterGenerator(
        mapOf(
            "AAA" to setConfig("AAA", "Alpha Set"),
            "BBB" to setConfig("BBB", "Beta Set"),
            "EXT" to setConfig("EXT", "Extension Set", extension = true),
            "PAR" to setConfig("PAR", "Partial Set", incomplete = true),
        ),
    )

    fun lobby(setCodes: List<String>, setNames: List<String>, boosterCount: Int = 6) =
        TournamentLobby(
            setCodes = setCodes,
            setNames = setNames,
            boosterGenerator = generator,
            boosterCount = boosterCount,
            boosterDistribution = TournamentLobby.calculateDefaultDistribution(setCodes, boosterCount),
        )

    test("isRandomSetCode recognises the sentinel and suffixed variants, not real codes") {
        TournamentLobby.isRandomSetCode("RANDOM") shouldBe true
        TournamentLobby.isRandomSetCode("RANDOM-2") shouldBe true
        TournamentLobby.isRandomSetCode("AAA") shouldBe false
        TournamentLobby.isRandomSetCode("") shouldBe false
    }

    test("updateSets accepts a random placeholder and keeps it hidden as \"Random Set\"") {
        val l = lobby(listOf("AAA"), listOf("Alpha Set"))
        l.updateSets(listOf("AAA", "RANDOM")) shouldBe true
        l.setCodes shouldContainExactly listOf("AAA", "RANDOM")
        l.setNames shouldContainExactly listOf("Alpha Set", "Random Set")
    }

    test("updateSets still rejects an unknown concrete code") {
        val l = lobby(listOf("AAA"), listOf("Alpha Set"))
        l.updateSets(listOf("AAA", "ZZZ")) shouldBe false
        // Selection unchanged on rejection.
        l.setCodes shouldContainExactly listOf("AAA")
    }

    test("resolveRandomSets reveals a concrete complete set, never extension or partial") {
        val l = lobby(listOf("RANDOM"), listOf("Random Set"))
        l.resolveRandomSets()

        l.setCodes.single() shouldNotBe "RANDOM"
        (l.setCodes.single() in setOf("AAA", "BBB")) shouldBe true
        l.setCodes shouldNotContain "EXT"
        l.setCodes shouldNotContain "PAR"
        // Name lines up with the revealed code.
        l.setNames.single() shouldBe generator.getSetConfig(l.setCodes.single())!!.setName
    }

    test("a random slot never duplicates an already-selected concrete set") {
        // AAA is picked; the only other complete set is BBB, so the random slot must become BBB.
        val l = lobby(listOf("AAA", "RANDOM"), listOf("Alpha Set", "Random Set"))
        l.resolveRandomSets()
        l.setCodes shouldContainExactly listOf("AAA", "BBB")
    }

    test("multiple random slots resolve to distinct sets") {
        val l = lobby(listOf("RANDOM", "RANDOM-2"), listOf("Random Set", "Random Set"))
        l.resolveRandomSets()
        l.setCodes.toSet().size shouldBe 2
        l.setCodes.toSet() shouldBe setOf("AAA", "BBB")
    }

    test("resolveRandomSets remaps the distribution key, preserving the host's booster count") {
        // Host allocates 4 boosters to AAA and 2 to the random slot.
        val l = lobby(listOf("AAA", "RANDOM"), listOf("Alpha Set", "Random Set"), boosterCount = 6)
        l.boosterDistribution = mapOf("AAA" to 4, "RANDOM" to 2)
        l.resolveRandomSets()

        // Random slot resolved to BBB; its 2 boosters carry over to the concrete key.
        l.boosterDistribution["AAA"] shouldBe 4
        l.boosterDistribution["BBB"] shouldBe 2
        l.boosterDistribution.containsKey("RANDOM") shouldBe false
    }

    test("resolveRandomSets is a no-op when there are no placeholders") {
        val l = lobby(listOf("AAA", "BBB"), listOf("Alpha Set", "Beta Set"))
        val before = l.setCodes.toList()
        l.resolveRandomSets()
        l.setCodes shouldContainExactly before
    }
})
