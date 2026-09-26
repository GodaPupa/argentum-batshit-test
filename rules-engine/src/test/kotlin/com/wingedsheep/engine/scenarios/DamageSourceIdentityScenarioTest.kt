package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.battlefield.DamageDealtThisTurnComponent
import com.wingedsheep.engine.state.components.battlefield.DamageDealtToCreaturesThisTurnComponent
import com.wingedsheep.engine.state.components.battlefield.HasDealtDamageComponent
import com.wingedsheep.engine.state.components.battlefield.DealtDamageToThisGameComponent
import com.wingedsheep.engine.state.components.player.DamageSourcesThisTurnComponent
import com.wingedsheep.engine.state.components.battlefield.BattlefieldEntryTimestampComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.model.GameRng
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json

/**
 * Generic noncombat-damage identity regressions, independent of any Ferocity card definition.
 * A pending ability reads live characteristics while its original source remains on the
 * battlefield, then the characteristics of that exact object at its departure. Neither an
 * activation-time snapshot nor a returned object's new abilities is a substitute.
 *
 * The custom cards are minimal engine fixtures. Silver Knight supplies real protection from
 * red to distinguish projected color at departure from the source's printed blue color.
 */
class DamageSourceIdentityScenarioTest : ScenarioTestBase() {
    private val source = card("Damage Identity Source") {
        manaCost = "{U}"
        colorIdentity = "U"
        typeLine = "Creature — Wizard"
        power = 1
        toughness = 2
        oracleText = "{0}: This creature deals 1 damage to each creature."
        activatedAbility {
            cost = Costs.Mana("{0}")
            effect = Effects.ForEachInGroup(
                GroupFilter.AllCreatures,
                Effects.DealDamage(1, EffectTarget.Self)
            )
        }
    }
    private val empower = card("Damage Identity Empower") {
        manaCost = "{0}"
        typeLine = "Instant"
        oracleText = "Target creature gains deathtouch and lifelink until end of turn."
        spell {
            val t = target("target creature", Targets.Creature)
            effect = Effects.GrantKeyword(Keyword.DEATHTOUCH, t) then
                Effects.GrantKeyword(Keyword.LIFELINK, t)
        }
    }
    private val stealAndRed = card("Damage Identity Steal Red") {
        manaCost = "{0}"
        typeLine = "Instant"
        oracleText = "Gain control of target creature. It becomes red until end of turn."
        spell {
            val t = target("target creature", Targets.Creature)
            effect = Effects.GainControl(t) then Effects.ChangeColor(t, setOf(Color.RED))
        }
    }
    private val destroy = card("Damage Identity Destroy") {
        manaCost = "{0}"
        typeLine = "Instant"
        oracleText = "Destroy target creature."
        spell {
            val t = target("target creature", Targets.Creature)
            effect = Effects.Destroy(t)
        }
    }
    private val recall = card("Damage Identity Recall") {
        manaCost = "{0}"
        typeLine = "Instant"
        oracleText = "Return target creature card from a graveyard to the battlefield under its owner's control."
        spell {
            val t = target("target creature card", Targets.CreatureCardInGraveyard)
            effect = Effects.PutOntoBattlefieldFromGraveyard(t)
        }
    }
    private val json = Json {
        serializersModule = engineSerializersModule
        allowStructuredMapKeys = true
        encodeDefaults = true
    }

    private fun setup(token: Boolean = false) = scenario().withPlayers("Original controller", "New controller")
        .withCardOnBattlefield(1, source.name, isToken = token)
        .withCardOnBattlefield(1, "Grizzly Bears")
        .withCardInLibrary(1, "Island").withCardInLibrary(2, "Plains")
        .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

    private fun TestGame.fixed(): TestGame = apply { state = state.copy(rng = GameRng.seeded(0xFE000077)) }

    private fun TestGame.finish() {
        resolveStack().forEach { it.error shouldBe null }
        state.pendingDecision shouldBe null
        state.stack.isEmpty() shouldBe true
        state.gameOver shouldBe false
    }

    private fun TestGame.resolveOne() {
        passPriority().error shouldBe null
        passPriority().error shouldBe null
        state.pendingDecision shouldBe null
        state.gameOver shouldBe false
    }

    private fun TestGame.activate(id: EntityId) {
        execute(ActivateAbility(player1Id, id, source.activatedAbilities.single().id)).error shouldBe null
    }

    private fun TestGame.roundTrip() {
        val before = state
        state = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), before))
        state shouldBe before
    }

    private fun damage(game: TestGame, id: EntityId): Int =
        game.state.getEntity(id)?.get<DamageComponent>()?.amount ?: 0

    init {
        cardRegistry.register(listOf(source, empower, stealAndRed, destroy, recall))

        test("a live original source gains effective damage keywords after activation") {
            val game = setup().withCardInHand(1, empower.name).build().fixed()
            val id = game.findPermanent(source.name)!!
            game.activate(id)
            game.castSpell(1, empower.name, id).error shouldBe null
            game.resolveOne()
            game.state.stack.size shouldBe 1
            game.state.projectedState.hasKeyword(id, Keyword.DEATHTOUCH) shouldBe true
            game.state.projectedState.hasKeyword(id, Keyword.LIFELINK) shouldBe true
            game.finish()

            game.isInGraveyard(1, source.name) shouldBe true
            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            game.getLifeTotal(1) shouldBe 22
            game.getLifeTotal(2) shouldBe 20
        }

        test("a live original source uses its new projected controller and color") {
            val game = setup().withCardOnBattlefield(2, "Silver Knight")
                .withCardInHand(1, empower.name).withCardInHand(2, stealAndRed.name).build().fixed()
            val id = game.findPermanent(source.name)!!
            val knight = game.findPermanent("Silver Knight")!!
            game.castSpell(1, empower.name, id).error shouldBe null
            game.finish()
            game.activate(id)
            game.passPriority().error shouldBe null
            game.castSpell(2, stealAndRed.name, id).error shouldBe null
            game.resolveOne()
            game.state.projectedState.getController(id) shouldBe game.player2Id
            game.state.projectedState.getColors(id) shouldBe setOf(Color.RED.name)
            game.finish()

            game.isInGraveyard(1, source.name) shouldBe true
            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            game.findPermanent("Silver Knight") shouldBe knight
            damage(game, knight) shouldBe 0
            game.getLifeTotal(1) shouldBe 20
            game.getLifeTotal(2) shouldBe 22
        }

        for (token in listOf(false, true)) {
            test("departed source uses final controller color and keywords after serialization; token=$token") {
                val game = setup(token).withCardOnBattlefield(2, "Silver Knight")
                    .withCardInHand(1, empower.name).withCardInHand(1, destroy.name)
                    .withCardInHand(2, stealAndRed.name).build().fixed()
                val id = game.findPermanent(source.name)!!
                val knight = game.findPermanent("Silver Knight")!!
                val original = game.state.objectRef(id)!!
                game.castSpell(1, empower.name, id).error shouldBe null
                game.finish()
                game.activate(id)
                game.passPriority().error shouldBe null
                game.castSpell(2, stealAndRed.name, id).error shouldBe null
                game.resolveOne()
                game.state.projectedState.getController(id) shouldBe game.player2Id
                game.state.projectedState.getColors(id) shouldBe setOf(Color.RED.name)
                game.castSpell(1, destroy.name, id).error shouldBe null
                game.resolveOne()
                game.isOnBattlefield(source.name) shouldBe false
                game.state.isCurrentObject(original) shouldBe false
                if (token) game.state.getEntity(id) shouldBe null
                else game.isInGraveyard(1, source.name) shouldBe true
                game.state.stack.size shouldBe 1

                game.roundTrip()
                game.finish()
                game.isInGraveyard(1, "Grizzly Bears") shouldBe true
                game.findPermanent("Silver Knight") shouldBe knight
                damage(game, knight) shouldBe 0
                // Only the Bear takes damage. Lifelink belongs to the controller at departure,
                // even though the pending ability and physical card belong to the other player.
                game.getLifeTotal(1) shouldBe 20
                game.getLifeTotal(2) shouldBe 21
            }
        }

        test("old damage does not borrow deathtouch or lifelink newly granted to the returned source") {
            val game = setup().withCardInHand(1, destroy.name).withCardInHand(1, recall.name)
                .withCardInHand(1, empower.name).build().fixed()
            val id = game.findPermanent(source.name)!!
            val bears = game.findPermanent("Grizzly Bears")!!
            val original = game.state.objectRef(id)!!
            // Directly seeded battlefield fixtures omit the optional entry timestamp; the source ledger uses zero.
        val originalTimestamp = game.state.getEntity(id)!!
            .get<BattlefieldEntryTimestampComponent>()?.timestamp ?: 0L
            game.activate(id)
            game.castSpell(1, destroy.name, id).error shouldBe null
            game.resolveOne()
            game.isInGraveyard(1, source.name) shouldBe true
            game.castSpellTargetingGraveyardCard(1, recall.name, listOf(id)).error shouldBe null
            game.resolveOne()
            game.findPermanent(source.name) shouldBe id
            game.state.objectRef(id) shouldNotBe original
            game.castSpell(1, empower.name, id).error shouldBe null
            game.resolveOne()
            game.state.projectedState.hasKeyword(id, Keyword.DEATHTOUCH) shouldBe true
            game.state.projectedState.hasKeyword(id, Keyword.LIFELINK) shouldBe true
            game.state.stack.size shouldBe 1
            game.roundTrip()
            game.finish()

            game.findPermanent(source.name) shouldBe id
            game.findPermanent("Grizzly Bears") shouldBe bears
            damage(game, id) shouldBe 1
            damage(game, bears) shouldBe 1
            game.getLifeTotal(1) shouldBe 20
            game.getLifeTotal(2) shouldBe 20
            // Pending old damage must not stamp the returned object's personal damage history.
            val returned = game.state.getEntity(id)!!
            returned.get<DamageDealtThisTurnComponent>() shouldBe null
            returned.get<DamageDealtToCreaturesThisTurnComponent>() shouldBe null
            returned.get<HasDealtDamageComponent>() shouldBe null
            returned.get<DealtDamageToThisGameComponent>() shouldBe null
            val sources = game.state.getEntity(game.player1Id)!!.get<DamageSourcesThisTurnComponent>()!!.sources
            sources.size shouldBe 1
            sources.single().entityId shouldBe id
            sources.single().incarnation shouldBe originalTimestamp
            // Reading old characteristics must not strip or overwrite the new object's effects.
            game.state.projectedState.hasKeyword(id, Keyword.DEATHTOUCH) shouldBe true
            game.state.projectedState.hasKeyword(id, Keyword.LIFELINK) shouldBe true
        }
    }
}
