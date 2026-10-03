package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.matchers.shouldBe

/** Exact rules-text diagnostic; no registry admission is implied by this inline definition. */
class SpellskiteScenarioTest : ScenarioTestBase() {
    private val spellskite = card("Spellskite") {
        manaCost = "{2}"; typeLine = "Artifact Creature — Phyrexian Horror"; power = 0; toughness = 4
        activatedAbility {
            cost = Costs.Mana("{U/P}")
            target("spellOrAbility", Targets.SpellOrAbility)
            effect = Effects.ChangeOneTargetTo(EffectTarget.ContextTarget(0), EffectTarget.Self)
        }
    }
    private val damage = card("Spellskite damage witness") {
        manaCost = "{0}"; typeLine = "Instant"
        spell { target("creature", Targets.Creature); effect = Effects.DealDamage(1, EffectTarget.ContextTarget(0)) }
    }
    private val noTargets = card("Spellskite targetless witness") {
        manaCost = "{0}"; typeLine = "Instant"
        spell { effect = Effects.GainLife(1) }
    }
    private val playerOnly = card("Spellskite player witness") {
        manaCost = "{0}"; typeLine = "Instant"
        spell { target("player", Targets.Player); effect = Effects.DealDamage(1, EffectTarget.ContextTarget(0)) }
    }
    init {
        listOf(spellskite, damage, noTargets, playerOnly).forEach(cardRegistry::register)
        for (payBlue in listOf(true, false)) {
            test("exact Spellskite activation pays ${if (payBlue) "blue mana" else "two life"} and redirects") {
                val g = scenario().withPlayers().withCardOnBattlefield(1, spellskite.name)
                    .withCardOnBattlefield(1, "Grizzly Bears").withCardInHand(1, damage.name).build()
                val skite = g.findPermanent(spellskite.name)!!
                val bear = g.findPermanent("Grizzly Bears")!!
                val spell = g.state.getHand(g.player1Id).single()
                g.execute(CastSpell(g.player1Id, spell, listOf(ChosenTarget.Permanent(bear)))).error shouldBe null
                g.state = g.state.updateEntity(g.player1Id) { it.with(ManaPoolComponent(blue = if (payBlue) 1 else 0)) }
                val life = g.state.lifeTotal(g.player1Id)
                g.execute(ActivateAbility(g.player1Id, skite, spellskite.activatedAbilities.single().id,
                    targets = listOf(ChosenTarget.Spell(spell)),
                    paymentStrategy = if (payBlue) PaymentStrategy.FromPool else PaymentStrategy.Explicit(
                        emptyList(), phyrexianLifePayments = listOf(com.wingedsheep.sdk.core.Color.BLUE)))).error shouldBe null
                g.state.lifeTotal(g.player1Id) shouldBe life - if (payBlue) 0 else 2
                val resolved = EngineServices(cardRegistry).stackResolver.resolveTop(g.state)
                resolved.error shouldBe null
                resolved.state.getEntity(spell)!!.get<TargetsComponent>()!!.targets shouldBe listOf(ChosenTarget.Permanent(skite))
            }
        }
        for (kind in listOf("targetless", "player", "source departed", "source blinked")) {
            test("exact Spellskite legally activates but leaves targets unchanged for $kind") {
                val witness = when (kind) { "targetless" -> noTargets; "player" -> playerOnly; else -> damage }
                val g = scenario().withPlayers().withCardOnBattlefield(1, spellskite.name)
                    .withCardOnBattlefield(1, "Grizzly Bears").withCardInHand(1, witness.name).build()
                val skite = g.findPermanent(spellskite.name)!!
                val spell = g.state.getHand(g.player1Id).single()
                val targets = when(kind) {
                    "targetless" -> emptyList()
                    "player" -> listOf(ChosenTarget.Player(g.player2Id))
                    else -> listOf(ChosenTarget.Permanent(g.findPermanent("Grizzly Bears")!!))
                }
                g.execute(CastSpell(g.player1Id, spell, targets)).error shouldBe null
                g.state = g.state.updateEntity(g.player1Id) { it.with(ManaPoolComponent(blue = 1)) }
                g.execute(ActivateAbility(g.player1Id, skite, spellskite.activatedAbilities.single().id,
                    targets = listOf(ChosenTarget.Spell(spell)))).error shouldBe null
                if (kind.startsWith("source")) {
                    g.state = ZoneTransitionService.moveToZone(g.state, skite, Zone.EXILE).state
                    if (kind == "source blinked") g.state = ZoneTransitionService.moveToZone(g.state, skite, Zone.BATTLEFIELD).state
                }
                val resolved = EngineServices(cardRegistry).stackResolver.resolveTop(g.state)
                resolved.error shouldBe null
                resolved.state.getEntity(spell)!!.get<TargetsComponent>()?.targets.orEmpty() shouldBe targets
            }
        }
    }
}
