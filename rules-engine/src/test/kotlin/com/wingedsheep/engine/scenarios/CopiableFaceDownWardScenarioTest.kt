package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.identity.FaceDownModeComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json

/** Public disguise/cloak characteristics are copied; the concealed printed ward is not. */
class CopiableFaceDownWardScenarioTest : FunSpec({
    val concealed = card("Copy Ward Concealed Identity") {
        manaCost = "{4}{G}{G}"; typeLine = "Creature — Wurm"; power = 6; toughness = 7
        keywordAbility(KeywordAbility.ward("{4}"))
    }
    val copier = card("Copy Ward Copier") {
        manaCost = "{0}"; typeLine = "Creature — Shapeshifter"; power = 0; toughness = 5
        replacementEffect(EntersAsCopy())
    }
    val entry = card("Copy Ward Direct Entry") {
        manaCost = "{0}"; typeLine = "Instant"
        spell { effect = Effects.Pipeline {
            val top = gather(CardSource.TopOfLibrary(DynamicAmount.Fixed(1)))
            move(top, CardDestination.ToZone(Zone.BATTLEFIELD))
        } }
    }
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    fun restore(d: GameTestDriver) {
        val encoded = json.encodeToString(GameState.serializer(), d.state)
        val restored = json.decodeFromString(GameState.serializer(), encoded)
        json.encodeToString(GameState.serializer(), restored) shouldBe encoded
        d.replaceState(restored)
    }
    for (mode in listOf(FaceDownMode.DISGUISE, FaceDownMode.CLOAK)) {
        for (direct in listOf(false, true)) for (pay in listOf(false, true)) {
            test("copied $mode owns ward two after serialization and original departure; direct=$direct, pay=$pay") {
                val d = GameTestDriver().apply {
                    registerCards(TestCards.all + listOf(concealed, copier, entry))
                    initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
                    passPriorityUntil(Step.PRECOMBAT_MAIN)
                }
                val hidden = d.putCreatureOnBattlefield(d.player2, concealed.name)
                d.replaceState(d.state.updateEntity(hidden) { it.with(FaceDownComponent).with(FaceDownModeComponent(mode)) })
                val entrant = if (direct) d.putCardOnTopOfLibrary(d.player1, copier.name)
                    else d.putCardInHand(d.player1, copier.name)
                val spell = if (direct) d.putCardInHand(d.player1, entry.name) else entrant
                d.castSpell(d.player1, spell).error shouldBe null
                d.bothPass().error shouldBe null
                val copyQuestion = d.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
                copyQuestion.options.contains(hidden) shouldBe true
                restore(d)
                d.submitCardSelection(d.player1, listOf(hidden)).error shouldBe null
                d.pendingDecision shouldBe null
                restore(d)
                val departed = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
                    MoveToZoneEffect(EffectTarget.SpecificEntity(hidden), Zone.EXILE),
                    EffectContext(sourceId = null, controllerId = d.player2))
                departed.error shouldBe null
                d.replaceState(departed.state)
                // Enough mana for either ward cost: the negative fails at the cost, not payment setup.
                repeat(5) { d.putLandOnBattlefield(d.player2, "Mountain") }
                d.giveMana(d.player2, Color.RED, 1)
                val bolt = d.putCardInHand(d.player2, "Lightning Bolt")
                d.passPriority(d.player1).error shouldBe null
                d.castSpellWithTargets(d.player2, bolt, listOf(ChosenTarget.Permanent(entrant))).error shouldBe null
                d.bothPass().error shouldBe null
                val ward = d.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
                ward.requiredCost shouldBe "{2}"
                ward.playerId shouldBe d.player2
                ward.canDecline shouldBe true
                d.state.projectedState.getPower(entrant) shouldBe 2
                d.state.projectedState.getToughness(entrant) shouldBe 2
                d.state.projectedState.hasKeyword(entrant, Keyword.WARD) shouldBe true
                val entity = d.state.getEntity(entrant).shouldNotBeNull()
                entity.has<FaceDownComponent>() shouldBe false
                entity.get<CardComponent>().shouldNotBeNull().name shouldBe ""
                d.submitManaAutoPayOrDecline(d.player2, autoPay = pay).error shouldBe null
                repeat(3) { if (d.state.priorityPlayerId != null && d.stackSize > 0) d.bothPass().error shouldBe null }
                d.pendingDecision shouldBe null
                d.stackSize shouldBe 0
                d.state.getBattlefield().contains(entrant) shouldBe !pay
                d.getGraveyardCardNames(d.player2).contains("Lightning Bolt") shouldBe true
            }
        }
    }
})
