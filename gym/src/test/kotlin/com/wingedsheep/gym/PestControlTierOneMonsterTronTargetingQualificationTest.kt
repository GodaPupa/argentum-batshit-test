package com.wingedsheep.gym

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.PestMonsterTronPolicy
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.DamageDealtEvent
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.matchup.NONEXPERIMENTAL_GATE4_FIXTURE_ENTROPY
import com.wingedsheep.gym.matchup.PestControlPreboardDecks
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronAdmission
import com.wingedsheep.gym.matchup.TierOneMonsterTronAdmission
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AbilityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.serialization.CardSerialization
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Exact-deck interaction fixtures for the Monster replacement proposal's targeting gate.
 * Only cards in the two frozen mains and their Food/Scion tokens are admitted here. These are
 * deterministic construction fixtures, not sampled games, seed allocation or C2/A2 acceptance.
 * The shared AnyTarget regression remains separately required; pilots do not define reachability.
 */
class PestControlTierOneMonsterTronTargetingQualificationTest : ScenarioTestBase() {
    private fun fixture() = scenario().withPlayers("Monster Tron", "Pest Control")
        .withRngSeed(NONEXPERIMENTAL_GATE4_FIXTURE_ENTROPY)

    private fun ScenarioBuilder.withTron() = withLandsOnBattlefield(1, "Urza's Mine", 1)
        .withLandsOnBattlefield(1, "Urza's Power Plant", 1)
        .withLandsOnBattlefield(1, "Urza's Tower", 1)

    private fun TestGame.resolveCompletely() {
        if (getPendingDecision() is SelectManaSourcesDecision) submitManaSourcesAutoPay().error shouldBe null
        resolveStack().forEach { it.error shouldBe null }
        hasPendingDecision() shouldBe false
        state.stack.size shouldBe 0
    }

    private fun TestGame.abilityTargets(source: EntityId, abilityId: AbilityId): Set<EntityId> =
        getLegalActions(1).filter { legal ->
            val action = legal.action as? ActivateAbility
            action != null && action.sourceId == source && action.abilityId == abilityId && legal.isAffordable
        }.flatMap { legal ->
            legal.validTargets.orEmpty() + legal.targetRequirements.orEmpty().flatMap { it.validTargets }
        }.toSet()

    private fun typeTags(value: JsonElement): Set<String> = when (value) {
        is JsonObject -> value.values.flatMap(::typeTags).toSet() +
            listOfNotNull((value["type"] as? JsonPrimitive)?.content)
        is JsonArray -> value.flatMap(::typeTags).toSet()
        else -> emptySet()
    }

    init {
        test("the frozen mains and generated Food and Scion definitions contain no AnyTarget requirement") {
            PestControlPreboardDecks.verifyFrozenIdentities()
            PestControlTierOneMonsterTronAdmission.validationErrors(TierOneMonsterTronAdmission()) shouldBe emptyList()
            val names = PestControlPreboardDecks.pestMainCounts.keys +
                PestControlTierOneMonsterTronAdmission.mainCounts.keys + setOf("Food", "Eldrazi Scion")
            for (name in names) {
                val definition = cardRegistry.requireCard(name)
                val tree = CardSerialization.json.encodeToJsonElement(CardDefinition.serializer(), definition)
                withClue(name) { ("AnyTarget" in typeTags(tree)) shouldBe false }
            }
            // The protocol excludes sideboards from this preboard gate. Their different target
            // surface is explicit; this check does not claim the engine globally supports it.
            PestControlTierOneMonsterTronAdmission.sideboardCounts["Kaervek's Torch"] shouldBe 1
        }

        test("Barrels offers creatures and rejects Forest and noncreature artifacts before paying costs") {
            val game = fixture().withTron()
                .withCardOnBattlefield(1, "Barrels of Blasting Jelly")
                .withCardOnBattlefield(1, "Candy Trail")
                .withCardOnBattlefield(1, "Pinnacle Kill-Ship")
                .withCardOnBattlefield(2, "Essence Warden")
                .withLandsOnBattlefield(2, "Forest", 1).build()
            val source = game.findPermanent("Barrels of Blasting Jelly")!!
            val ability = cardRegistry.requireCard("Barrels of Blasting Jelly").activatedAbilities.single { !it.isManaAbility }
            val warden = game.findPermanent("Essence Warden")!!
            val offered = game.abilityTargets(source, ability.id)
            (warden in offered) shouldBe true
            for (name in listOf("Forest", "Candy Trail", "Pinnacle Kill-Ship")) {
                val target = game.findPermanent(name)!!
                withClue(name) { (target in offered) shouldBe false }
                val before = game.state
                game.execute(ActivateAbility(game.player1Id, source, ability.id,
                    targets = listOf(ChosenTarget.Permanent(target)))).error shouldNotBe null
                game.state shouldBe before
            }
            game.execute(ActivateAbility(game.player1Id, source, ability.id,
                targets = listOf(ChosenTarget.Permanent(warden)))).error shouldBe null
            game.resolveCompletely()
            game.isInGraveyard(1, "Barrels of Blasting Jelly") shouldBe true
            game.isInGraveyard(2, "Essence Warden") shouldBe true
        }

        test("Giant's Boulder offers and destroys the frozen Forest and noncreature artifact targets") {
            for (targetName in listOf("Forest", "Candy Trail")) {
                val game = fixture().withTron().withCardOnBattlefield(1, "Giant's Boulder")
                    .withCardOnBattlefield(1, "Candy Trail")
                    .withLandsOnBattlefield(2, "Forest", 1).build()
                val source = game.findPermanent("Giant's Boulder")!!
                val target = game.findPermanent(targetName)!!
                val ability = cardRegistry.requireCard("Giant's Boulder").activatedAbilities.single { !it.isManaAbility }
                withClue(targetName) { (target in game.abilityTargets(source, ability.id)) shouldBe true }
                game.execute(ActivateAbility(game.player1Id, source, ability.id,
                    targets = listOf(ChosenTarget.Permanent(target)))).error shouldBe null
                game.resolveCompletely()
                game.isInGraveyard(1, "Giant's Boulder") shouldBe true
                game.isInGraveyard(if (targetName == "Forest") 2 else 1, targetName) shouldBe true
            }
        }

        test("Pinnacle trigger and the frozen Monster pilot select only an offered creature") {
            val game = fixture().withTron().withCardInHand(1, "Pinnacle Kill-Ship")
                .withCardOnBattlefield(1, "Candy Trail")
                .withCardOnBattlefield(2, "Essence Warden")
                .withLandsOnBattlefield(2, "Forest", 1).build()
            game.castSpell(1, "Pinnacle Kill-Ship").error shouldBe null
            if (game.getPendingDecision() is SelectManaSourcesDecision) game.submitManaSourcesAutoPay().error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            val decision = game.getPendingDecision().shouldBeInstanceOf<ChooseTargetsDecision>()
            val warden = game.findPermanent("Essence Warden")!!
            decision.legalTargets.values.flatten().toSet() shouldBe setOf(warden)
            val response = AIPlayer.create(cardRegistry, game.player1Id, PestMonsterTronPolicy.profile)
                .respondToDecision(game.state, decision).shouldBeInstanceOf<TargetsResponse>()
            response.selectedTargets.values.flatten() shouldBe listOf(warden)
            game.submitDecision(response).error shouldBe null
            val events = game.resolveStack().flatMap { result -> result.error shouldBe null; result.events }
            events.filterIsInstance<DamageDealtEvent>().single().amount shouldBe 10
            game.isInGraveyard(2, "Essence Warden") shouldBe true
        }

        test("Pest Cast Down rejects an unstationed Kill-Ship without spending mana or moving the spell") {
            val game = fixture().withCardOnBattlefield(1, "Pinnacle Kill-Ship")
                .withCardOnBattlefield(1, "Bramble Wurm")
                .withCardInHand(2, "Cast Down").withLandsOnBattlefield(2, "Swamp", 2)
                .withActivePlayer(2).build()
            val ship = game.findPermanent("Pinnacle Kill-Ship")!!
            val wurm = game.findPermanent("Bramble Wurm")!!
            val cast = game.getLegalActions(2).single { it.actionType == "CastSpell" && it.description.contains("Cast Down") }
            val targets = cast.validTargets.orEmpty() + cast.targetRequirements.orEmpty().flatMap { it.validTargets }
            (wurm in targets) shouldBe true
            (ship in targets) shouldBe false
            val before = game.state
            game.execute(CastSpell(game.player2Id, game.findCardsInHand(2, "Cast Down").single(),
                listOf(ChosenTarget.Permanent(ship)))).error shouldNotBe null
            game.state shouldBe before
        }

        test("actual Station changes Kill-Ship projected type before Pest Cast Down is enumerated and resolves") {
            val game = fixture().withCardOnBattlefield(1, "Pinnacle Kill-Ship")
                .withCardOnBattlefield(1, "Bramble Wurm")
                .withCardInHand(2, "Cast Down").withLandsOnBattlefield(2, "Swamp", 2).build()
            val ship = game.findPermanent("Pinnacle Kill-Ship")!!
            val wurm = game.findPermanent("Bramble Wurm")!!
            game.state.projectedState.isCreature(ship) shouldBe false
            val station = cardRegistry.requireCard("Pinnacle Kill-Ship").activatedAbilities.single()
            game.execute(ActivateAbility(game.player1Id, ship, station.id,
                costPayment = AdditionalCostPayment(tappedPermanents = listOf(wurm)))).error shouldBe null
            game.resolveCompletely()
            game.state.getEntity(ship)!!.get<CountersComponent>()!!.getCount(CounterType.CHARGE) shouldBe 7
            game.state.projectedState.isCreature(ship) shouldBe true
            game.state.priorityPlayerId shouldBe game.player1Id
            game.passPriority().error shouldBe null
            val cast = game.getLegalActions(2).single { it.actionType == "CastSpell" && it.description.contains("Cast Down") }
            val targets = cast.validTargets.orEmpty() + cast.targetRequirements.orEmpty().flatMap { it.validTargets }
            (ship in targets) shouldBe true
            game.castSpell(2, "Cast Down", ship).error shouldBe null
            game.resolveCompletely()
            game.isInGraveyard(1, "Pinnacle Kill-Ship") shouldBe true
        }
    }
}
