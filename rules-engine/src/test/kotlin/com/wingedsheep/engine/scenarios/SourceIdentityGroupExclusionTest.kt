package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.handlers.*
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.handlers.effects.composite.ForEachExecutor
import com.wingedsheep.engine.mechanics.layers.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.ForEachEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class SourceIdentityGroupExclusionTest : ScenarioTestBase() {
    private val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    private val other = GroupFilter(GameObjectFilter.Creature.youControl()).other()

    init {
        test("GX01 current origin is excluded") {
            val (g,a,b,c) = fixture()
            val recipients = recipients(g.state,c)
            (a in recipients) shouldBe false // W_GX01
            recipients shouldBe listOf(b)
        }
        test("GX02 returned source generation is included") {
            val (g,a,b,c) = fixture(); blink(g,a)
            val recipients = recipients(g.state,c)
            (a in recipients) shouldBe true // W_GX02
            recipients.toSet() shouldBe setOf(a,b)
        }
        test("GX03 departed absent origin does not suppress others") {
            val (g,a,b,c) = fixture()
            g.state = ZoneTransitionService.moveToZone(g.state,a,Zone.GRAVEYARD).state
            recipients(g.state,c) shouldBe listOf(b)
        }
        test("GX04 other flag false preserves all recipients") {
            val (g,a,b,c) = fixture()
            recipients(g.state,c,other.copy(excludeSelf=false)).toSet() shouldBe setOf(a,b)
        }
        test("GX05 legacy unbound source retains old exclusion") {
            val (g,_,b,c) = fixture()
            recipients(g.state,c.copy(objectReferences=ObjectReferenceEnvironment())) shouldBe listOf(b)
        }
        test("GX06 captured missing origin never recaptures current source") {
            val (g,a,b,c) = fixture(); blink(g,a)
            recipients(g.state,c.copy(objectReferences=ObjectReferenceEnvironment(captured=true))).toSet() shouldBe setOf(a,b)
        }
        test("GX07 serialized origin exclusion is identical") {
            for (returned in listOf(false,true)) {
                val (g,a,_,c) = fixture()
                if (returned) blink(g,a)
                val before = recipients(g.state,c)
                val restored = json.decodeFromString<EffectContext>(json.encodeToString(c))
                restored shouldBe c
                val state = SerializationTestSupport.roundTrip(g.state)
                state shouldBe g.state
                recipients(state,restored) shouldBe before
            }
        }
        test("GX08 nested iteration does not replace originating source") {
            val (g,_,b,c) = fixture()
            val nested = c.copy(objectReferences=c.objectReferences.copy(selfBinding=CapturedObjectBinding(b,g.state.objectRef(b))),
                pipeline=c.pipeline.copy(iterationTarget=b))
            recipients(g.state,nested) shouldBe listOf(b)
        }
        test("GX09 permitted move does not redefine original other") {
            val (g,a,b,c) = fixture(); blink(g,a)
            val followed = c.copy(objectReferences=c.objectReferences.copy(
                permittedMoves=listOf(PermittedObjectMove(c.objectReferences.origin!!,g.state.objectRef(a)!!))))
            followed.objectReferences.isCurrent(c.objectReferences.origin,g.state) shouldBe true
            val recipients = recipients(g.state,followed)
            (a in recipients) shouldBe true // W_GX09
            recipients.toSet() shouldBe setOf(a,b)
        }
        test("GX10 projected creatures and control determine membership") {
            val (g,_,b,c) = fixture()
            val land = g.findPermanent("Forest")!!
            val enemy = g.findPermanent("Hill Giant")!!
            g.state = g.state.addFloatingEffect(Layer.TYPE,SerializableModification.AddType("CREATURE"),setOf(land),Duration.Permanent,c)
                .addFloatingEffect(Layer.CONTROL,SerializableModification.ChangeController(g.player1Id),setOf(enemy),Duration.Permanent,c)
            recipients(g.state,c).toSet() shouldBe setOf(b,land,enemy)
            val targeted=c.copy(targets=listOf(ChosenTarget.Permanent(b)))
            recipients(g.state,targeted,other.copy(excludeTarget=true)).toSet() shouldBe setOf(land,enemy)
            recipients(g.state,c,other.copy(chosenSubtypeKey="kind")) shouldBe emptyList()
            val chosen=c.copy(pipeline=c.pipeline.copy(chosenValues=mapOf("kind" to "Bear")))
            recipients(g.state,chosen,other.copy(chosenSubtypeKey="kind")) shouldBe listOf(b)
        }
    }
    private data class Fixture(val game: TestGame,val source:EntityId,val ally:EntityId,val context:EffectContext)
    private fun fixture():Fixture {
        val g=scenario().withPlayers().withCardOnBattlefield(1,"Llanowar Elves")
            .withCardOnBattlefield(1,"Grizzly Bears").withCardOnBattlefield(2,"Hill Giant")
            .withCardOnBattlefield(1,"Forest").build()
        val a=g.findPermanent("Llanowar Elves")!!;val b=g.findPermanent("Grizzly Bears")!!
        val ref=g.state.objectRef(a)!!
        return Fixture(g,a,b,EffectContext(a,g.player1Id,objectReferences=ObjectReferenceEnvironment(captured=true,origin=ref,source=ref)))
    }
    private fun blink(g:TestGame,id:EntityId) {
        g.state=ZoneTransitionService.moveToZone(g.state,id,Zone.EXILE).state
        val from=g.state.logicalZone(id)!!
        g.state=g.state.moveToZone(id,from,ZoneKey(from.ownerId,Zone.BATTLEFIELD))
    }
    private fun recipients(state:GameState,context:EffectContext,filter:GroupFilter=other):List<EntityId> {
        val seen=mutableListOf<EntityId>()
        val executor=ForEachExecutor { s,_,c ->
            seen.add(c.pipeline.iterationTarget!!)
            c.objectReferences.selfBinding?.objectRef shouldBe s.objectRef(c.pipeline.iterationTarget!!)
            EffectResult.success(s)
        }
        val result=executor.execute(state,Effects.ForEachInGroup(filter,Effects.GainLife(1)) as ForEachEffect,context)
        result.error shouldBe null
        result.state shouldBe state
        result.events shouldBe emptyList()
        return seen
    }
}
