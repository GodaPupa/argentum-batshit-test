package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.targets.TargetPlayer
import io.kotest.matchers.shouldBe

class AnnouncedSpliceGroupsTest : ScenarioTestBase() {
    private val host = card("Splice group host") { manaCost = "{0}"; typeLine = "Instant"; spell { effect = Effects.DrawCards(1) } }
    private val tail = card("Splice group tail") {
        manaCost = "{0}"; typeLine = "Instant"
        spell { target("omitted", TargetPlayer(optional = true)); target("chosen", TargetPlayer()); effect = Effects.DrawCards(1) }
    }
    init {
        listOf(host, tail).forEach(cardRegistry::register)
        test("announced optional splice group keeps its actual slice and survives copy retarget") {
            val g = scenario().withPlayers().withCardInHand(1, host.name).build()
            val id = g.state.getHand(g.player1Id).single()
            val resolver = EngineServices(cardRegistry).stackResolver
            val result = resolver.castSpell(g.state, id, g.player1Id,
                targets = listOf(ChosenTarget.Player(g.player2Id)),
                targetRequirements = tail.script.targetRequirements, splicedCardNames = listOf(tail.name),
                announcedTargetCounts = listOf(0, 1))
            result.error shouldBe null
            val spell = result.state.getEntity(id)!!.get<SpellOnStackComponent>()!!
            spell.splicedTargetsOrdered shouldBe listOf(listOf(ChosenTarget.Player(g.player2Id)))
            spell.splicedTargetRequirements.single().map { it.count } shouldBe listOf(0, 1)
            val copy = resolver.putSpellCopy(result.state, id, targets = listOf(ChosenTarget.Player(g.player1Id)))
            copy.error shouldBe null
            val copied = copy.state.getEntity(copy.state.stack.last())!!.get<SpellOnStackComponent>()!!
            copied.splicedTargetsOrdered shouldBe listOf(listOf(ChosenTarget.Player(g.player1Id)))
            copied.splicedTargetRequirements shouldBe spell.splicedTargetRequirements
        }
    }
}
