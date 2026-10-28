package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.event.GrantedActivatedAbility
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.Duration
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path

/** Draft fixture export: no card registration and no production behavior changes. */
class ActivatedPhyrexianBrowserFixtureTest : ScenarioTestBase() {
    init {
        for ((name, life, blue) in listOf(
            Triple("life-only", 20, 0), Triple("blue-or-life", 20, 1),
            Triple("insufficient-life", 1, 1), Triple("unaffordable", 1, 0)
        )) {
            test("Payment UI fixture serializes and enumerates $name") {
                val game = scenario().withPlayers("Payment One", "Payment Two")
                    .withLifeTotal(1, life)
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardInHand(1, "Forest").withCardInHand(2, "Island")
                    .withCardInLibrary(1, "Forest").withCardInLibrary(2, "Island").build()
                val source = game.findPermanent("Grizzly Bears")!!
                // Only extract a pure ability value; never register this temporary definition.
                val ability = card("Manual UI Ability Value") {
                    manaCost = "{2}"; typeLine = "Artifact"
                    activatedAbility { cost = Costs.Mana("{U/P}"); effect = Effects.GainLife(1) }
                }.activatedAbilities.single()
                val initial = game.state.copy(grantedActivatedAbilities = listOf(
                    GrantedActivatedAbility(source, ability, Duration.Permanent)
                )).updateEntity(game.player1Id) { it.with(ManaPoolComponent(blue = blue)) }
                val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
                val encoded = json.encodeToString(GameState.serializer(), initial)
                game.state = json.decodeFromString(GameState.serializer(), encoded)
                game.state.grantedActivatedAbilities.single().ability shouldBe ability
                val action = game.getLegalActions(1).single {
                    val a = it.action
                    a is ActivateAbility && a.sourceId == source && a.abilityId == ability.id
                }
                action.manaCostString shouldBe "{U/P}"
                action.isAffordable shouldBe (life >= 2 || blue > 0)
                game.getLegalActions(2).isEmpty() shouldBe true
                val out = Path.of(System.getenv("MANUAL_PAYMENT_FIXTURE_DIR") ?: "build/manual-payment-fixtures")
                Files.createDirectories(out)
                Files.writeString(out.resolve("$name.json"), encoded)
                Files.writeString(out.resolve("$name-meta.json"), buildJsonObject {
                    put("source", source.value); put("owner", game.player1Id.value)
                    put("other", game.player2Id.value); put("life", life); put("blue", blue)
                    put("description", action.description)
                }.toString())
            }
        }
    }
}
