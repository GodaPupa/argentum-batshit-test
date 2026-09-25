package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.sos.cards.PrismariTheInspiration
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Supertype
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.effects.CopyExceptions
import com.wingedsheep.sdk.scripting.effects.CreateTokenCopyOfTargetEffect
import com.wingedsheep.sdk.scripting.effects.StormCopyEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Prismari, the Inspiration: "Instant and sorcery spells you cast have storm." The grant is a
 * [com.wingedsheep.sdk.scripting.GrantKeywordToOwnSpells] static ability read by the cast handler
 * via [com.wingedsheep.engine.mechanics.mana.GrantedKeywordResolver]. A non-storm instant cast
 * while Prismari is in play should produce a storm trigger; two Prismaris should produce two.
 */
class PrismariTheInspirationScenarioTest : FunSpec({

    fun stormTriggers(driver: GameTestDriver): List<StormCopyEffect> =
        driver.state.stack.mapNotNull {
            driver.state.getEntity(it)?.get<TriggeredAbilityOnStackComponent>()
        }.mapNotNull { it.effect as? StormCopyEffect }

    test("instant cast with Prismari in play gets a storm trigger with copyCount = spells cast before it") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(PrismariTheInspiration))
        driver.initMirrorMatch(deck = Deck.of("Mountain" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val caster = driver.activePlayer!!
        val opponent = driver.getOpponent(caster)

        driver.putCreatureOnBattlefield(caster, "Prismari, the Inspiration")
        driver.replaceState(driver.state.copy(spellsCastThisTurn = 2))
        driver.putLandOnBattlefield(caster, "Mountain")
        val bolt = driver.putCardInHand(caster, "Lightning Bolt")

        driver.castSpell(caster, bolt, listOf(opponent)).isSuccess shouldBe true

        val triggers = stormTriggers(driver)
        triggers.size shouldBe 1
        triggers.single().copyCount shouldBe 2
    }

    test("two Prismaris produce two separate storm triggers (CR 702.40b)") {
        val copySpell = card("Test Nonlegendary Prismari Copy") {
            manaCost = "{0}"
            typeLine = "Sorcery"
            spell {
                target = Targets.PermanentYouControl
                effect = CreateTokenCopyOfTargetEffect(
                    target = EffectTarget.ContextTarget(0),
                    exceptions = CopyExceptions(removedSupertypes = setOf(Supertype.LEGENDARY))
                )
            }
        }
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(PrismariTheInspiration, copySpell))
        driver.initMirrorMatch(deck = Deck.of("Mountain" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val caster = driver.activePlayer!!
        val opponent = driver.getOpponent(caster)

        val original = driver.putCreatureOnBattlefield(caster, "Prismari, the Inspiration")
        val copy = driver.putCardInHand(caster, copySpell.name)
        driver.castSpell(caster, copy, listOf(original)).error shouldBe null
        // The real setup spell has storm with zero prior casts. Resolve that trigger and the
        // spell itself; the resulting nonlegendary token can legally coexist with its source.
        stormTriggers(driver).single().copyCount shouldBe 0
        repeat(2) { driver.bothPass().error shouldBe null }
        driver.state.stack.isEmpty() shouldBe true
        driver.pendingDecision shouldBe null
        val copies = driver.state.getBattlefield().filter {
            driver.state.getEntity(it)?.get<CardComponent>()?.name == "Prismari, the Inspiration"
        }
        copies.size shouldBe 2
        val token = copies.single { driver.state.getEntity(it)?.get<TokenComponent>() != null }
        driver.state.getEntity(token)!!.get<CardComponent>()!!.typeLine.isLegendary shouldBe false
        driver.state.getEntity(original)!!.get<CardComponent>()!!.typeLine.isLegendary shouldBe true
        driver.state.spellsCastThisTurn shouldBe 1
        driver.putLandOnBattlefield(caster, "Mountain")
        val bolt = driver.putCardInHand(caster, "Lightning Bolt")

        driver.castSpell(caster, bolt, listOf(opponent)).isSuccess shouldBe true

        stormTriggers(driver).size shouldBe 2
        stormTriggers(driver).map { it.copyCount } shouldBe listOf(1, 1)
    }

    test("without Prismari, an instant produces no storm trigger") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(PrismariTheInspiration))
        driver.initMirrorMatch(deck = Deck.of("Mountain" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val caster = driver.activePlayer!!
        val opponent = driver.getOpponent(caster)

        driver.replaceState(driver.state.copy(spellsCastThisTurn = 2))
        driver.putLandOnBattlefield(caster, "Mountain")
        val bolt = driver.putCardInHand(caster, "Lightning Bolt")

        driver.castSpell(caster, bolt, listOf(opponent)).isSuccess shouldBe true

        stormTriggers(driver).size shouldBe 0
    }
})
