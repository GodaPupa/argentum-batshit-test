package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.*
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class SurgeCostCompositionTest : ScenarioTestBase() {
    private val json=Json { serializersModule=engineSerializersModule; allowStructuredMapKeys=true }
    init {
        for ((name,flash,price) in listOf(Triple("Surge Probe",false,"{1}{R}"),
            Triple("Flash Surge Probe",true,"{1}{R}"),Triple("Red Payment Probe",false,"{1}{R}"))) {
            cardRegistry.register(card(name) {
                manaCost="{2}{R}";typeLine="Creature — Construct";power=2;toughness=1
                if (flash) keywords(Keyword.FLASH)
                selfAlternativeCost=SelfAlternativeCost(ManaCost.parse(price),condition=Conditions.YouCastSpellsThisTurn(1))
            })
        }
        cardRegistry.register(card("Surge Prior Probe") { manaCost="{0}";typeLine="Artifact";keywords(Keyword.FLASH) })
        for (n in listOf(1,2)) cardRegistry.register(card("Surge Discount $n") {
            manaCost="{0}";typeLine="Artifact"
            staticAbility { ability=ModifySpellCost(SpellCostTarget.YouCast(GameObjectFilter.Creature),CostModification.ReduceGeneric(n)) }
        })
        cardRegistry.register(card("Surge Tax") {
            manaCost="{0}";typeLine="Artifact"
            staticAbility { ability=ModifySpellCost(SpellCostTarget.YouCast(GameObjectFilter.Creature),CostModification.IncreaseGeneric(1)) }
        })
        cardRegistry.register(card("Surge Granted Alternative") {
            manaCost="{0}";typeLine="Artifact"
            staticAbility { ability=GrantAlternativeCastingCost("{R}") }
        })
        test("SP01 explicit alternative spends one generic and one red") {
            val g=board();mana(g,1,1);val a=action(g)
            g.execute(a).error shouldBe null
            pool(g) shouldBe ManaPoolComponent()
            spell(g,a.cardId)?.alternativeCost shouldBe AlternativeCostType.SELF_ALTERNATIVE
            cardRegistry.getCard("Surge Probe")!!.manaCost.cmc shouldBe 3
        }
        test("SP02 normal choice spends two generic and one red") {
            val g=board();mana(g,2,1);val a=action(g).copy(useAlternativeCost=false,alternativeCostType=null)
            menu(g).any { !it.useAlternativeCost } shouldBe true
            g.execute(a).error shouldBe null
            pool(g) shouldBe ManaPoolComponent()
            spell(g,a.cardId)?.alternativeCost shouldBe null
        }
        test("SP03 red requirement cannot be paid by two colorless") {
            val g=board(name="Red Payment Probe");mana(g,2,0);val a=action(g,"Red Payment Probe")
            val before=g.state;val r=g.execute(a)
            (r.error==null) shouldBe false // W_SP03
            r.state shouldBe before;g.state shouldBe before;r.events shouldBe emptyList()
            menu(g,a.cardId).any { it.alternativeCostType==AlternativeCostType.SELF_ALTERNATIVE } shouldBe false
        }
        test("SP04 red only cannot pay unreduced surge") {
            val g=board();mana(g,0,1);reject(g,action(g))
            menu(g).any { it.alternativeCostType==AlternativeCostType.SELF_ALTERNATIVE } shouldBe false
        }
        test("SP05 generic reduction preserves red and explicit choice") {
            val g=board(permanent="Surge Discount 1");mana(g,0,1);val a=action(g)
            g.execute(a).error shouldBe null;pool(g) shouldBe ManaPoolComponent()
            spell(g,a.cardId)?.alternativeCost shouldBe AlternativeCostType.SELF_ALTERNATIVE
        }
        test("SP06 equal effective prices do not imply selected alternative") {
            val baseline=board(permanent="Surge Discount 2");mana(baseline,0,1)
            for (alt in listOf(false,true)) {
                val g=fork(baseline);val a=action(g).copy(useAlternativeCost=alt,alternativeCostType=if(alt) AlternativeCostType.SELF_ALTERNATIVE else null)
                g.execute(a).error shouldBe null;pool(g) shouldBe ManaPoolComponent()
                spell(g,a.cardId)?.alternativeCost shouldBe if(alt) AlternativeCostType.SELF_ALTERNATIVE else null
            }
        }
        test("SP07 generic tax applies after alternative base") {
            for (alt in listOf(false,true)) {
                val g=board(permanent="Surge Tax");mana(g,if(alt)2 else 3,1)
                val a=action(g).copy(useAlternativeCost=alt,alternativeCostType=if(alt) AlternativeCostType.SELF_ALTERNATIVE else null)
                menu(g).contains(a) shouldBe true
                g.execute(a).error shouldBe null;pool(g) shouldBe ManaPoolComponent()
            }
        }
        test("SP08 granted alternative does not mark surge") {
            val g=board(permanent="Surge Granted Alternative");mana(g,2,1)
            menu(g).any { it.alternativeCostType==AlternativeCostType.SELF_ALTERNATIVE } shouldBe true
            val a=action(g).copy(alternativeCostType=AlternativeCostType.GRANTED)
            g.execute(a).error shouldBe null
            spell(g,a.cardId)?.alternativeCost shouldBe AlternativeCostType.GRANTED
            pool(g) shouldBe ManaPoolComponent(colorless=2)
        }
        test("SP09 free cast does not mark surge") {
            val g=board(permanent="Omniscience");mana(g,0,0)
            val a=action(g).copy(useAlternativeCost=false,alternativeCostType=null,useWithoutPayingManaCost=true)
            g.execute(a).error shouldBe null;pool(g) shouldBe ManaPoolComponent()
            (spell(g,a.cardId)?.alternativeCost==AlternativeCostType.SELF_ALTERNATIVE) shouldBe false
        }
        test("SP10 no prior history cannot self qualify during cast") {
            val g=board(history=false);mana(g,2,1);reject(g,action(g))
            g.state.spellsCastThisTurnByPlayer[g.player1Id].orEmpty() shouldBe emptyList()
        }
        test("SP11 sorcery timing still enforced") {
            val g=board(resolvePrior=false);mana(g,2,1)
            reject(g,action(g));reject(g,action(g).copy(useAlternativeCost=false,alternativeCostType=null))
        }
        test("SP12 flash composition uses prior spell still on stack") {
            val g=board(name="Flash Surge Probe",resolvePrior=false);mana(g,1,1)
            val prior=g.state.stack.single();val a=action(g,"Flash Surge Probe")
            g.execute(a).error shouldBe null;pool(g) shouldBe ManaPoolComponent()
            g.state.stack.toSet() shouldBe setOf(prior,a.cardId)
            spell(g,a.cardId)?.alternativeCost shouldBe AlternativeCostType.SELF_ALTERNATIVE
        }
        test("SP13 serialized alternative action preserves discriminator") {
            val original=board();mana(original,1,1);val restored=fork(original)
            val a=action(original);val b=json.decodeFromString<GameAction>(json.encodeToString<GameAction>(a))
            b shouldBe a
            val x=original.execute(a);val y=restored.execute(b)
            x.error shouldBe null;y shouldBe x
        }
        test("SP14 serialized cast history and stack preserve chosen cost") {
            val g=board();mana(g,1,1);val before=menu(g)
            roundTrip(g);menu(g) shouldBe before
            val a=action(g);g.execute(a).error shouldBe null
            val history=g.state.spellsCastThisTurnByPlayer
            roundTrip(g);g.state.spellsCastThisTurnByPlayer shouldBe history
            spell(g,a.cardId)?.alternativeCost shouldBe AlternativeCostType.SELF_ALTERNATIVE
        }
        test("SP15 copied surge spell retains choice without another cast") {
            val g=board();mana(g,1,1);val a=action(g);g.execute(a).error shouldBe null
            val before=g.state;val r=StackResolver(cardRegistry).putSpellCopy(before,a.cardId)
            r.error shouldBe null;val copy=(r.state.stack-before.stack.toSet()).single()
            r.state.getEntity(copy)?.get<SpellOnStackComponent>()?.alternativeCost shouldBe AlternativeCostType.SELF_ALTERNATIVE // W_SP15
            r.state.spellsCastThisTurnByPlayer shouldBe before.spellsCastThisTurnByPlayer
            r.state.spellsCastThisTurn shouldBe before.spellsCastThisTurn
            r.events.any { it is SpellCastEvent || it is ManaSpentEvent } shouldBe false
        }
        test("SP16 normal spell copy remains unmarked") {
            val g=board();mana(g,2,1);val a=action(g).copy(useAlternativeCost=false,alternativeCostType=null)
            g.execute(a).error shouldBe null
            val r=StackResolver(cardRegistry).putSpellCopy(g.state,a.cardId);r.error shouldBe null
            val copy=(r.state.stack-g.state.stack.toSet()).single()
            r.state.getEntity(copy)?.get<SpellOnStackComponent>()?.alternativeCost shouldBe null
        }
        test("SP17 replay alternative payment and events exactly") {
            val g=board(history=false);mana(g,1,1);val h=fork(g)
            val prior=g.findCardsInHand(1,"Surge Prior Probe").single()
            val cast=CastSpell(g.player1Id,prior)
            g.execute(cast) shouldBe h.execute(cast)
            repeat(2) { val a=PassPriority(g.state.priorityPlayerId!!);g.execute(a) shouldBe h.execute(a) }
            g.state.stack shouldBe emptyList()
            val a=action(g);g.execute(a) shouldBe h.execute(a)
            g.state shouldBe h.state
        }
        test("SP18 wrong actor and stale eligibility reject atomically") {
            val g=board();mana(g,1,1);reject(g,action(g).copy(playerId=g.player2Id))
            repeat(2) { g.passUntilPhase(Phase.ENDING,Step.END);g.passUntilPhase(Phase.PRECOMBAT_MAIN,Step.PRECOMBAT_MAIN) }
            mana(g,1,1);reject(g,action(g))
        }
        test("SP19 cost composition JSON round trip") {
            val value=SelfAlternativeCost(ManaCost.parse("{1}{R}"),condition=Conditions.YouCastSpellsThisTurn(1))
            json.decodeFromString<SelfAlternativeCost>(json.encodeToString(value)) shouldBe value
        }
        test("SP20 normal and alternative legal menus are nonmutating") {
            val g=board();mana(g,2,1);val state=g.state;val first=menu(g)
            menu(g) shouldBe first;g.state shouldBe state
            first.count { !it.useAlternativeCost } shouldBe 1
            first.count { it.alternativeCostType==AlternativeCostType.SELF_ALTERNATIVE } shouldBe 1
        }
    }
    private fun board(name:String="Surge Probe",permanent:String?=null,history:Boolean=true,resolvePrior:Boolean=true):TestGame {
        var b=scenario().withPlayers().withCardInHand(1,name).withCardInHand(1,"Surge Prior Probe")
        if(permanent!=null)b=b.withCardOnBattlefield(1,permanent)
        repeat(10){b=b.withCardInLibrary(1,"Mountain").withCardInLibrary(2,"Island")}
        val g=b.build()
        if(history){g.castSpell(1,"Surge Prior Probe").error shouldBe null;if(resolvePrior)g.resolveStack().forEach { it.error shouldBe null }}
        return g
    }
    private fun action(g:TestGame,name:String="Surge Probe")=CastSpell(g.player1Id,g.findCardsInHand(1,name).single(),useAlternativeCost=true,alternativeCostType=AlternativeCostType.SELF_ALTERNATIVE)
    private fun menu(g:TestGame,id:EntityId=g.findCardsInHand(1,"Surge Probe").single())=g.getLegalActions(1).mapNotNull { it.action as? CastSpell }.filter { it.cardId==id }
    private fun mana(g:TestGame,c:Int,r:Int){g.state=g.state.updateEntity(g.player1Id){it.with(ManaPoolComponent(colorless=c,red=r))}}
    private fun pool(g:TestGame)=g.state.getEntity(g.player1Id)?.get<ManaPoolComponent>()
    private fun spell(g:TestGame,id:EntityId)=g.state.getEntity(id)?.get<SpellOnStackComponent>()
    private fun reject(g:TestGame,a:CastSpell){val before=g.state;val r=g.execute(a);(r.error==null) shouldBe false;r.state shouldBe before;g.state shouldBe before;r.events shouldBe emptyList()}
    private fun fork(g:TestGame)=TestGame(SerializationTestSupport.roundTrip(g.state),g.player1Id,g.player2Id,cardRegistry,actionProcessor,stateTransformer)
    private fun roundTrip(g:TestGame){val before=g.state;g.state=SerializationTestSupport.roundTrip(before);g.state shouldBe before}
}
