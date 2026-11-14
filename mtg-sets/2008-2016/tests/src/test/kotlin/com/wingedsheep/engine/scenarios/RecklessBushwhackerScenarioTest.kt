package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.mechanics.layers.*
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.state.components.battlefield.CastChoicesComponent
import com.wingedsheep.engine.state.components.battlefield.ChoiceValue
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.mtg.sets.definitions.ogw.cards.RecklessBushwhacker
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.serialization.CardExporter
import com.wingedsheep.sdk.serialization.CardLoader
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class RecklessBushwhackerScenarioTest : ScenarioTestBase() {
    private val name="Reckless Bushwhacker"
    init {
        cardRegistry.register(card("Bushwhacker Prior") { manaCost="{0}";typeLine="Artifact";keywords(Keyword.FLASH) })
        for (n in listOf("Bushwhacker Opponent","Bushwhacker Flash Ally","Bushwhacker Haste Ally")) {
            cardRegistry.register(card(n) {
                manaCost="{0}";typeLine="Creature — Bear";power=2;toughness=2
                if(n.contains("Flash"))keywords(Keyword.FLASH)
                if(n.contains("Haste"))keywords(Keyword.HASTE)
            })
        }
        cardRegistry.register(card("Bushwhacker Discount") {
            manaCost="{0}";typeLine="Artifact"
            staticAbility { ability=ModifySpellCost(SpellCostTarget.YouCast(GameObjectFilter.Creature),CostModification.ReduceGeneric(2)) }
        })
        cardRegistry.register(card("Bushwhacker Granted") {
            manaCost="{0}";typeLine="Artifact"
            staticAbility { ability=GrantAlternativeCastingCost("{R}") }
        })
        cardRegistry.register(card("Bushwhacker Copy Fixture") {
            manaCost="{0}";typeLine="Instant"
            spell { target=Targets.Creature;effect=Effects.CreateTokenCopyOfTarget(EffectTarget.ContextTarget(0)) }
        })
        test("BW01 normal cast has intrinsic haste without pump") {
            val g=board();cast(g,false);resolve(g);val source=g.findPermanent(name)!!
            haste(g,source) shouldBe true // W_BW01_HASTE
            power(g,source) shouldBe 2;toughness(g,source) shouldBe 1
            power(g,ally(g)) shouldBe 2 // W_BW01_PUMP
            toughness(g,ally(g)) shouldBe 2;haste(g,ally(g)) shouldBe false
            choice(g,source) shouldBe null
        }
        test("BW02 surge cast pumps other creatures only") {
            val g=board();mana(g,ManaPoolComponent(colorless=1,red=1));val a=action(g)
            (g.execute(a).error==null) shouldBe true // W_BW02_CAST
            resolve(g)
            power(g,a.cardId) shouldBe 2 // W_BW02_SELF
            toughness(g,a.cardId) shouldBe 1;haste(g,a.cardId) shouldBe true
            power(g,ally(g)) shouldBe 3 // W_BW02_POWER
            toughness(g,ally(g)) shouldBe 2 // W_BW02_TOUGHNESS
            haste(g,ally(g)) shouldBe true // W_BW02_HASTE
            choice(g,a.cardId) shouldBe ChoiceValue.Flag
        }
        test("BW03 opponent creatures are unaffected") {
            val g=board();cast(g);resolve(g);val enemy=g.findPermanent("Bushwhacker Opponent")!!
            power(g,enemy) shouldBe 2 // W_BW03
            toughness(g,enemy) shouldBe 2;haste(g,enemy) shouldBe false
        }
        test("BW04 noncreature permanents are unaffected") {
            val g=board();cast(g);resolve(g)
            for(id in listOf(g.findPermanent("Forest")!!,g.findPermanent("Bushwhacker Prior")!!)) {
                haste(g,id) shouldBe false
                g.state.floatingEffects.any { id in it.effect.affectedEntities } shouldBe false
            }
        }
        test("BW05 put onto battlefield does not trigger surge payoff") {
            val g=board();val id=g.findCardsInHand(1,name).first()
            // Exercise the real entry executor on the exact creature; no cast-choice component is installed.
            val registry=com.wingedsheep.engine.handlers.effects.EffectExecutorRegistry(cardRegistry=cardRegistry)
            val context=EffectContext(null,g.player1Id,targets=listOf(ChosenTarget.Card(id,g.player1Id,Zone.HAND)))
            val result=registry.execute(g.state,Effects.PutOntoBattlefield(EffectTarget.ContextTarget(0)),context)
            result.error shouldBe null;g.state=result.state
            choice(g,id) shouldBe null;haste(g,id) shouldBe true
            power(g,ally(g)) shouldBe 2
            val detected=com.wingedsheep.engine.event.TriggerDetector(cardRegistry).detectTriggers(g.state,result.events)
            detected.any { it.sourceId==id } shouldBe false
        }
        test("BW06 normal cast at same price as surge stays unmarked") {
            val g=board(extra="Bushwhacker Discount");mana(g,ManaPoolComponent(red=1))
            cast(g,false);resolve(g)
            choice(g,g.findPermanent(name)!!) shouldBe null;power(g,ally(g)) shouldBe 2
        }
        test("BW07 surge with own countered prior spell") {
            val g=board(history="countered");cast(g);resolve(g)
            g.findCardsInGraveyard(1,"Bushwhacker Prior").size shouldBe 1
            choice(g,g.findPermanent(name)!!) shouldBe ChoiceValue.Flag;power(g,ally(g)) shouldBe 3
        }
        test("BW08 opponent only history cannot enable card") {
            val g=board(history="opponent");reject(g,action(g))
            menu(g).any { it.alternativeCostType==AlternativeCostType.SELF_ALTERNATIVE } shouldBe false
            menu(g).any { !it.useAlternativeCost } shouldBe true
        }
        test("BW09 previous turn own history cannot enable card") {
            val g=board()
            repeat(2){g.passUntilPhase(Phase.ENDING,Step.END);g.passUntilPhase(Phase.PRECOMBAT_MAIN,Step.PRECOMBAT_MAIN)}
            mana(g);reject(g,action(g))
            menu(g).any { it.alternativeCostType==AlternativeCostType.SELF_ALTERNATIVE } shouldBe false
        }
        test("BW10 surge never bypasses creature timing") {
            val g=board(history="stack");reject(g,action(g))
            menu(g) shouldBe emptyList()
        }
        test("BW11 trigger waits on stack before granting") {
            val g=board();val id=enter(g)
            g.state.stack.size shouldBe 1;power(g,ally(g)) shouldBe 2
            resolve(g);power(g,ally(g)) shouldBe 3;choice(g,id) shouldBe ChoiceValue.Flag
        }
        test("BW12 countered entry trigger gives no boost") {
            val g=board();val id=enter(g);val trigger=g.state.stack.single()
            val stifle=g.findCardsInHand(1,"Stifle").single()
            g.execute(CastSpell(g.player1Id,stifle,listOf(ChosenTarget.Spell(trigger)))).error shouldBe null
            resolve(g);power(g,ally(g)) shouldBe 2;haste(g,id) shouldBe true
        }
        test("BW13 countered creature produces no entry trigger") {
            val g=board();val id=cast(g);g.passPriority().error shouldBe null
            g.castSpellTargetingStackSpell(2,"Counterspell",name).error shouldBe null
            resolve(g);g.findPermanent(name) shouldBe null
            g.findCardsInGraveyard(1,name).contains(id) shouldBe true
            power(g,ally(g)) shouldBe 2
            g.state.spellsCastThisTurnByPlayer[g.player1Id].orEmpty().size shouldBe 2
        }
        test("BW14 source dies before trigger resolves") {
            val g=board();val id=enter(g);depart(g,id)
            resolve(g);power(g,ally(g)) shouldBe 3;haste(g,ally(g)) shouldBe true
        }
        test("BW15 source blinks before trigger resolves") {
            val g=board();val id=enter(g);val before=g.state.objectRef(id);blink(g,id)
            g.state.objectRef(id) shouldNotBe before;choice(g,id) shouldBe null;g.state.stack.size shouldBe 1
            resolve(g);power(g,id) shouldBe 3;toughness(g,id) shouldBe 1;power(g,ally(g)) shouldBe 3
        }
        test("BW16 serialized departed trigger retains paid fact") {
            val g=board();val id=enter(g);depart(g,id);val h=fork(g)
            val a=g.resolveStack();val b=h.resolveStack();a shouldBe b
            a.forEach { it.error shouldBe null };power(g,ally(g)) shouldBe 3;g.state shouldBe h.state
        }
        test("BW17 serialized blinked trigger keeps original identity") {
            val g=board();val id=enter(g);blink(g,id);val h=fork(g)
            val a=g.resolveStack();a shouldBe h.resolveStack();a.forEach { it.error shouldBe null }
            power(g,id) shouldBe 3;power(g,ally(g)) shouldBe 3;g.state shouldBe h.state
        }
        test("BW18 new creature before trigger resolves is included") {
            val g=board();enter(g);g.castSpell(1,"Bushwhacker Flash Ally").error shouldBe null
            resolve(g);val id=g.findPermanent("Bushwhacker Flash Ally")!!
            power(g,id) shouldBe 3;haste(g,id) shouldBe true
        }
        test("BW19 new creature after trigger resolves is excluded") {
            val g=board();cast(g);resolve(g);g.castSpell(1,"Bushwhacker Flash Ally").error shouldBe null
            resolve(g);val id=g.findPermanent("Bushwhacker Flash Ally")!!
            power(g,id) shouldBe 2;haste(g,id) shouldBe false
        }
        test("BW20 temporary grants survive end step and end at cleanup") {
            val g=board();val id=cast(g);resolve(g)
            g.passUntilPhase(Phase.ENDING,Step.END)
            power(g,ally(g)) shouldBe 3;haste(g,ally(g)) shouldBe true
            g.passUntilPhase(Phase.PRECOMBAT_MAIN,Step.PRECOMBAT_MAIN)
            power(g,ally(g)) shouldBe 2 // W_BW20
            toughness(g,ally(g)) shouldBe 2;haste(g,ally(g)) shouldBe false;haste(g,id) shouldBe true
        }
        test("BW21 existing intrinsic haste remains after cleanup") {
            val g=board(extra="Bushwhacker Haste Ally");cast(g);resolve(g)
            g.passUntilPhase(Phase.ENDING,Step.END);g.passUntilPhase(Phase.PRECOMBAT_MAIN,Step.PRECOMBAT_MAIN)
            val id=g.findPermanent("Bushwhacker Haste Ally")!!;haste(g,id) shouldBe true;power(g,id) shouldBe 2
        }
        test("BW22 multiple paid triggers stack power only") {
            val g=board();cast(g);resolve(g);cast(g);resolve(g)
            power(g,ally(g)) shouldBe 4;toughness(g,ally(g)) shouldBe 2
            g.findPermanents(name).size shouldBe 2
        }
        test("BW23 projected creature receives boost") {
            val g=board();val land=g.findPermanent("Forest")!!
            val context=EffectContext(null,g.player1Id)
            g.state=g.state.addFloatingEffect(Layer.TYPE,SerializableModification.AddType("CREATURE"),setOf(land),Duration.Permanent,context)
                .addFloatingEffect(Layer.POWER_TOUGHNESS,SerializableModification.SetPowerToughness(2,2),setOf(land),Duration.Permanent,context,sublayer=Sublayer.SET_VALUES)
            cast(g);resolve(g);power(g,land) shouldBe 3;toughness(g,land) shouldBe 2;haste(g,land) shouldBe true
        }
        test("BW24 controller at resolution determines recipient set") {
            val g=board();enter(g);val a=ally(g);val enemy=g.findPermanent("Bushwhacker Opponent")!!
            control(g,a,g.player2Id);control(g,enemy,g.player1Id);resolve(g)
            power(g,a) shouldBe 2;haste(g,a) shouldBe false;power(g,enemy) shouldBe 3;haste(g,enemy) shouldBe true
        }
        test("BW25 source control change does not transfer trigger") {
            val g=board();val id=enter(g);control(g,id,g.player2Id)
            g.state.getEntity(g.state.stack.single())?.get<TriggeredAbilityOnStackComponent>()?.controllerId shouldBe g.player1Id
            resolve(g);power(g,ally(g)) shouldBe 3;power(g,g.findPermanent("Bushwhacker Opponent")!!) shouldBe 2
        }
        test("BW26 zero other creatures resolves successfully") {
            val g=board(allies=false);val id=cast(g);val events=g.resolveStack().flatMap { it.error shouldBe null;it.events }
            power(g,id) shouldBe 2;g.state.floatingEffects shouldBe emptyList()
            g.state.stack shouldBe emptyList()
        }
        test("BW27 copied permanent spell retains paid choice") {
            val g=board();val id=cast(g);val before=g.state
            val r=StackResolver(cardRegistry).putSpellCopy(before,id);r.error shouldBe null
            val copy=(r.state.stack-before.stack.toSet()).single();g.state=r.state
            g.state.spellsCastThisTurnByPlayer shouldBe before.spellsCastThisTurnByPlayer
            resolve(g);choice(g,copy) shouldBe ChoiceValue.Flag;power(g,ally(g)) shouldBe 4
        }
        test("BW28 copy of permanent does not copy cast choice") {
            val g=board();val id=cast(g);resolve(g)
            g.castSpell(1,"Bushwhacker Copy Fixture",id).error shouldBe null;resolve(g)
            val copied=g.findPermanents(name).single { it!=id }
            choice(g,copied) shouldBe null
            power(g,ally(g)) shouldBe 3
        }
        test("BW29 serialized normal and surge card definitions preserve behavior") {
            val restored=CardLoader.fromJsonPreservingIds(CardExporter.exportToJson(RecklessBushwhacker))
            CardExporter.exportToJson(restored) shouldBe CardExporter.exportToJson(RecklessBushwhacker)
            for(alt in listOf(false,true)) {
                val g=board();val id=cast(g,alt);roundTrip(g);resolve(g)
                power(g,ally(g)) shouldBe if(alt)3 else 2
                choice(g,id) shouldBe if(alt)ChoiceValue.Flag else null
            }
        }
        test("BW30 literal replay reproduces full cast trigger cleanup") {
            val g=board(history="none",smallHand=true);val h=fork(g)
            val prior=CastSpell(g.player1Id,g.findCardsInHand(1,"Bushwhacker Prior").single())
            fun execute(a:GameAction){val r=g.execute(a);r.error shouldBe null;h.execute(a) shouldBe r;h.state shouldBe g.state}
            execute(prior);repeat(2){execute(PassPriority(g.state.priorityPlayerId!!))}
            execute(action(g));repeat(4){execute(PassPriority(g.state.priorityPlayerId!!))}
            power(g,ally(g)) shouldBe 3
            val legal=g.getLegalActions(1);h.getLegalActions(1) shouldBe legal
            val turn=g.state.turnNumber;var steps=0;var attacked=false;var blocked=false
            while(g.state.turnNumber==turn && steps++<60){
                val player=g.state.priorityPlayerId!!
                if(g.state.step==Step.DECLARE_ATTACKERS && !attacked && player==g.player1Id){
                    execute(DeclareAttackers(player,mapOf(ally(g) to g.player2Id)));attacked=true
                }
                if(g.state.step==Step.DECLARE_BLOCKERS && !blocked && player==g.player2Id){
                    execute(DeclareBlockers(player,emptyMap()));blocked=true
                }
                execute(PassPriority(g.state.priorityPlayerId!!))
            }
            attacked shouldBe true
            g.state.turnNumber shouldBe turn+1
            power(g,ally(g)) shouldBe 2;haste(g,ally(g)) shouldBe false
        }
        test("BW31 granted alternative cannot activate card payoff") {
            val g=board(extra="Bushwhacker Granted");val a=action(g).copy(alternativeCostType=AlternativeCostType.GRANTED)
            g.execute(a).error shouldBe null;resolve(g)
            choice(g,a.cardId) shouldBe null;haste(g,a.cardId) shouldBe true;power(g,ally(g)) shouldBe 2
        }
        test("BW32 normal and surge printed characteristics remain identical") {
            for(alt in listOf(false,true)) {
                val g=board();val id=cast(g,alt);resolve(g)
                val c=g.state.getEntity(id)?.get<CardComponent>()!!
                c.manaCost.cmc shouldBe 3;c.colors shouldBe setOf(Color.RED)
                c.typeLine.subtypes.map { it.value }.toSet() shouldBe setOf("Goblin","Warrior","Ally")
                power(g,id) shouldBe 2;toughness(g,id) shouldBe 1
            }
        }
    }
    private fun board(history:String="resolved",extra:String?=null,allies:Boolean=true,smallHand:Boolean=false):TestGame {
        var b=scenario().withPlayers().withCardsInHand(1,name,2).withCardInHand(1,"Bushwhacker Prior")
            .withCardInHand(2,"Bushwhacker Prior").withCardInHand(2,"Counterspell")
            .withCardOnBattlefield(1,"Forest").withCardOnBattlefield(2,"Bushwhacker Opponent")
        if(!smallHand)b=b.withCardInHand(1,"Doom Blade").withCardInHand(1,"Momentary Blink").withCardInHand(1,"Stifle")
            .withCardInHand(1,"Bushwhacker Flash Ally").withCardInHand(1,"Bushwhacker Copy Fixture")
        if(allies)b=b.withCardOnBattlefield(1,"Grizzly Bears",summoningSickness=true)
        if(extra!=null)b=b.withCardOnBattlefield(1,extra)
        repeat(12){b=b.withCardInLibrary(1,"Mountain").withCardInLibrary(2,"Island")}
        val g=b.build();mana(g)
        g.state=g.state.updateEntity(g.player2Id){it.with(ManaPoolComponent(blue=5))}
        if(history=="opponent"){
            g.passPriority().error shouldBe null;g.castSpell(2,"Bushwhacker Prior").error shouldBe null;resolve(g)
        }else if(history!="none"){
            g.castSpell(1,"Bushwhacker Prior").error shouldBe null
            if(history=="countered"){g.passPriority().error shouldBe null;g.castSpellTargetingStackSpell(2,"Counterspell","Bushwhacker Prior").error shouldBe null}
            if(history!="stack")resolve(g)
        }
        return g
    }
    private fun action(g:TestGame)=CastSpell(g.player1Id,g.findCardsInHand(1,name).first(),useAlternativeCost=true,alternativeCostType=AlternativeCostType.SELF_ALTERNATIVE)
    private fun cast(g:TestGame,alt:Boolean=true):EntityId {val a=action(g).copy(useAlternativeCost=alt,alternativeCostType=if(alt)AlternativeCostType.SELF_ALTERNATIVE else null);g.execute(a).error shouldBe null;return a.cardId}
    private fun enter(g:TestGame):EntityId {val id=cast(g);var n=0;while(g.findPermanent(name)==null && n++<8)g.passPriority().error shouldBe null;g.findPermanent(name) shouldBe id;return id}
    private fun resolve(g:TestGame){g.resolveStack().forEach { it.error shouldBe null }}
    private fun depart(g:TestGame,id:EntityId){g.castSpell(1,"Doom Blade",id).error shouldBe null;var n=0;while(g.findPermanent(name)!=null && n++<8)g.passPriority().error shouldBe null;g.findPermanent(name) shouldBe null;g.state.stack.size shouldBe 1}
    private fun blink(g:TestGame,id:EntityId){val ref=g.state.objectRef(id);g.castSpell(1,"Momentary Blink",id).error shouldBe null;var n=0;while(g.state.objectRef(id)==ref && n++<8)g.passPriority().error shouldBe null;g.state.objectRef(id) shouldNotBe ref;g.findPermanent(name) shouldBe id}
    private fun mana(g:TestGame,pool:ManaPoolComponent=ManaPoolComponent(colorless=10,red=10,blue=5,black=5,white=5)){g.state=g.state.updateEntity(g.player1Id){it.with(pool)}}
    private fun ally(g:TestGame)=g.findPermanent("Grizzly Bears")!!
    private fun power(g:TestGame,id:EntityId)=g.state.projectedState.getPower(id)
    private fun toughness(g:TestGame,id:EntityId)=g.state.projectedState.getToughness(id)
    private fun haste(g:TestGame,id:EntityId)=g.state.projectedState.hasKeyword(id,Keyword.HASTE)
    private fun choice(g:TestGame,id:EntityId)=g.state.getEntity(id)?.get<CastChoicesComponent>()?.chosen?.get(ChoiceSlot.SELF_ALTERNATIVE)
    private fun menu(g:TestGame)=g.getLegalActions(1).mapNotNull { it.action as? CastSpell }.filter { it.cardId==action(g).cardId }
    private fun reject(g:TestGame,a:CastSpell){val before=g.state;val r=g.execute(a);(r.error==null) shouldBe false;r.state shouldBe before;r.events shouldBe emptyList();g.state shouldBe before}
    private fun control(g:TestGame,id:EntityId,player:EntityId){g.state=g.state.addFloatingEffect(Layer.CONTROL,SerializableModification.ChangeController(player),setOf(id),Duration.Permanent,EffectContext(null,g.player1Id))}
    private fun fork(g:TestGame)=TestGame(SerializationTestSupport.roundTrip(g.state),g.player1Id,g.player2Id,cardRegistry,actionProcessor,stateTransformer)
    private fun roundTrip(g:TestGame){val before=g.state;g.state=SerializationTestSupport.roundTrip(before);g.state shouldBe before}
}
