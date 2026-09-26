package com.wingedsheep.gameserver.session

import com.wingedsheep.engine.core.CombatResolutionDecision
import com.wingedsheep.engine.core.DamageEdge
import com.wingedsheep.engine.core.DamageEdgeDirection
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.ResolutionDefender
import com.wingedsheep.engine.core.ResolutionTargetKind
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.PlayerComponent
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Presentation-only fixtures: public player names change, engine identities and damage do not. */
class CombatDefenderNameEnricherTest : FunSpec({
    val attacker = EntityId.of("attacker")
    val defender = EntityId.of("defender")
    val otherDefender = EntityId.of("other-defender")
    val source = EntityId.of("combat-source")
    val enricher = DecisionEnricher(CardRegistry())

    fun decision(targets: List<ResolutionDefender>) = CombatResolutionDecision(
        id = "public-combat-decision",
        playerId = attacker,
        prompt = "Assign combat damage",
        context = DecisionContext(),
        firstStrike = false,
        attackers = emptyList(),
        blockers = emptyList(),
        defenders = targets,
        edges = listOf(DamageEdge(
            id = "combat-source->defender", sourceId = source, targetId = defender,
            direction = DamageEdgeDirection.ATTACKER_TO_PLAYER,
            amount = 5, maximum = 7, lethal = 0, orderConstrained = false,
            isTrampleDrain = true, editableBy = attacker,
        )),
    )

    test("public player labels follow exact defender identities for every viewer") {
        val state = GameState()
            .withEntity(defender, ComponentContainer.of(PlayerComponent("Defender")))
            .withEntity(otherDefender, ComponentContainer.of(PlayerComponent("Another Player")))
        val original = decision(listOf(
            ResolutionDefender(defender, ResolutionTargetKind.PLAYER, "Player", 20),
            ResolutionDefender(otherDefender, ResolutionTargetKind.PLAYER, "Player", 13),
        ))
        val expected = original.copy(defenders = listOf(
            original.defenders[0].copy(name = "Defender"),
            original.defenders[1].copy(name = "Another Player"),
        ))
        for (viewer in listOf(attacker, defender, otherDefender)) {
            enricher.enrich(original, state, viewer) shouldBe expected
        }
        original.defenders.map { it.name } shouldBe listOf("Player", "Player")
    }

    test("a missing player component retains the existing label and damage metadata") {
        val original = decision(listOf(
            ResolutionDefender(defender, ResolutionTargetKind.PLAYER, "Player", 20),
        ))
        enricher.enrich(original, GameState(), attacker) shouldBe original
    }

    test("nonplayer defender labels remain unchanged even when the entity has a player component") {
        val state = GameState()
            .withEntity(defender, ComponentContainer.of(PlayerComponent("Wrong replacement")))
            .withEntity(otherDefender, ComponentContainer.of(PlayerComponent("Wrong replacement")))
        val original = decision(listOf(
            ResolutionDefender(defender, ResolutionTargetKind.PLANESWALKER, "Planeswalker name", 4),
            ResolutionDefender(otherDefender, ResolutionTargetKind.BATTLE, "Battle name", 5),
        ))
        enricher.enrich(original, state, attacker) shouldBe original
    }
})
