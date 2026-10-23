package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.ActivatedAbilityOnStackComponent
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/**
 * Diagnostic only: distinguish a suspended resolving spell from an ETB trigger.
 * The original PausedTokenInstructionScenarioTest/PT02 is byte-exact and remains failed.
 * This standalone synthetic fixture consumes no official Manual allocation.
 */
class PausedTokenStackProvenanceDiagnosticTest : FunSpec({
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }

    fun roundTrip(d: GameTestDriver) {
        val raw = json.encodeToString(GameState.serializer(), d.state)
        val restored = json.decodeFromString(GameState.serializer(), raw)
        json.encodeToString(GameState.serializer(), restored) shouldBe raw
        d.replaceState(restored)
    }

    fun begin(sourceCopy: Boolean): GameTestDriver {
        val body = card("Paused Token Choice Body") {
            manaCost = "{0}"
            typeLine = "Creature — Bear"
            power = 2
            toughness = 3
            replacementEffect(EntersWithChoice(ChoiceType.COLOR))
            activatedAbility {
                cost = Costs.Free
                effect = CreateTokenCopyOfSourceEffect(count = 2)
            }
        }
        val watcher = card("Paused Token External Observer") {
            manaCost = "{0}"
            typeLine = "Enchantment"
            triggeredAbility {
                trigger = Triggers.OtherCreatureEnters
                effect = Effects.GainLife(1)
            }
        }
        val d = GameTestDriver().apply {
            registerCards(TestCards.all + listOf(body, watcher))
            initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val original = d.putCreatureOnBattlefield(if (sourceCopy) d.player1 else d.player2, body.name)
        d.putPermanentOnBattlefield(d.player1, watcher.name)
        if (sourceCopy) {
            d.submit(ActivateAbility(d.player1, original, body.activatedAbilities.single().id)).error shouldBe null
        } else {
            val action = card("Paused Token Copy Action") {
                manaCost = "{0}"
                typeLine = "Instant"
                spell {
                    effect = CreateTokenCopyOfTargetEffect(
                        target = EffectTarget.SpecificEntity(original),
                        count = DynamicAmount.Fixed(2),
                    )
                }
            }
            d.registerCards(listOf(action))
            d.castSpell(d.player1, d.putCardInHand(d.player1, action.name)).error shouldBe null
        }
        d.bothPass().error shouldBe null
        (d.pendingDecision is ChooseColorDecision) shouldBe true
        return d
    }

    for (sourceCopy in listOf(false, true)) for (serialized in listOf(false, true)) {
        test("PT02 diagnostic: only a resolving spell may remain before second choice; sourceCopy=$sourceCopy, serialized=$serialized") {
            val d = begin(sourceCopy)
            if (serialized) roundTrip(d)

            val first = d.pendingDecision as ChooseColorDecision
            d.submitDecision(first.playerId, ColorChosenResponse(first.id, Color.BLUE)).error shouldBe null
            if (serialized) roundTrip(d)
            (d.pendingDecision is ChooseColorDecision) shouldBe true

            // No first token may enter before all the same-instruction replacement choices.
            d.state.getBattlefield().count {
                d.state.getEntity(it)?.has<TokenComponent>() == true
            } shouldBe 0

            val objects = d.state.stack.mapNotNull { d.state.getEntity(it) }
            objects.count { it.has<TriggeredAbilityOnStackComponent>() } shouldBe 0
            objects.count { it.has<ActivatedAbilityOnStackComponent>() } shouldBe 0

            if (sourceCopy) {
                objects.count { it.has<SpellOnStackComponent>() } shouldBe 0
                d.state.stack.size shouldBe 0
            } else {
                objects.count { it.has<SpellOnStackComponent>() } shouldBe 1
                d.state.stack.size shouldBe 1
                objects.single().get<CardComponent>()?.name shouldBe "Paused Token Copy Action"
            }
        }
    }
})
