import json,re,hashlib,itertools
from pathlib import Path
from collections import Counter
card=Path('disciple-card-frozen.kt').read_text();tests=Path('disciple-card-controls-isolated.kt').read_text()
base=Path('extension-r2-test.kt').read_text()
assert tests.startswith(base[:-3])
asserts=lambda s:Counter(l.strip() for l in s.splitlines() if 'shouldBe' in l)
assert not(asserts(base)-asserts(tests))
record=[]
def add(id,reqs,edits,case,assertion,occurrence=1):
 s=card
 for before,after in edits:
  assert s.count(before)==1,(id,before,s.count(before));s=s.replace(before,after)
 start=tests.index('test("'+case+'")')
 end=tests.index('\n    }',start)
 body=tests[start:end]
 lines=[i+1 for i,l in enumerate(tests.splitlines()) if l.strip()==assertion and sum(len(x)+1 for x in tests.splitlines()[:i])>=start and sum(len(x)+1 for x in tests.splitlines()[:i])<end]
 assert len(lines)>=occurrence,(id,case,assertion,lines)
 record.append(dict(id=id,requirements=reqs,edits=[dict(before=a,after=b) for a,b in edits],source_sha256=hashlib.sha256(s.encode()).hexdigest(),witness=dict(case=case,line=lines[occurrence-1],assertion=assertion)))
var='val sacrificedPower = DynamicAmount.VariableReference("discipleSacrificedPower")'
add('fixed_one',['DF01','DF06','DF11'],[(var,'val sacrificedPower = DynamicAmount.Fixed(1)')],'DF01 sacrifice gives exactly five life and five cards','d.getLifeTotal(me) shouldBe 25')
add('fixed_two',['DF05'],[(var,'val sacrificedPower = DynamicAmount.Fixed(2)')],'DF05 projected boost is captured before the sacrifice','d.getLifeTotal(me) shouldBe 25')
add('source_power',['DF09'],[(var,'val sacrificedPower = DynamicAmount.EntityProperty(EntityReference.Source, EntityNumericProperty.Power)')],'DF09 token sacrifice retains projected five power after departure','d.getLifeTotal(me) shouldBe 25')
end='        )))\n        description ='
add('no_may',['DF02'],[('effect = MayEffect(Effects.Composite(listOf(','effect = Effects.Composite(listOf('),(end,'        ))\n        description =')],'DF02 declining a singleton optional sacrifice preserves its actual creature','d.getPermanents(me).contains(fodder) shouldBe true')
add('allow_self',['DF03'],[('TargetFilter.CreatureYouControl.other()','TargetFilter.CreatureYouControl')],'DF03 accepting with only Disciple preserves the source creature','d.getPermanents(me).contains(source) shouldBe true')
add('allow_opponent',['DF04'],[('TargetFilter.CreatureYouControl.other()','TargetFilter.Creature.other()'),('Effects.SacrificeTarget(EffectTarget.PipelineTarget("discipleSacrifice"))','com.wingedsheep.sdk.scripting.effects.SacrificeTargetEffect(EffectTarget.PipelineTarget("discipleSacrifice"), sacrificedByItsController = true)')],'DF04 accepting with only opponent fodder preserves that creature','d.getPermanents(opp).contains(fodder) shouldBe true')
add('gate_bypass',['DF07'],[('successCriterion = SuccessCriterion.PermanentsSacrificed','successCriterion = SuccessCriterion.Always')],'DF07 an unsacrificable chosen creature gives no payoff','d.getLifeTotal(me) shouldBe 20')
add('require_source',['DF08'],[(end,'        )), sourceRequiredZone = com.wingedsheep.sdk.core.Zone.BATTLEFIELD)\n        description =')],'DF08 departed source singleton trigger still pays five life','d.getLifeTotal(me) shouldBe 25')
count='DynamicAmount.Count(com.wingedsheep.sdk.scripting.references.Player.You, com.wingedsheep.sdk.core.Zone.GRAVEYARD)'
guard='com.wingedsheep.sdk.scripting.effects.ConditionalEffect(com.wingedsheep.sdk.scripting.conditions.Compare('+count+', com.wingedsheep.sdk.scripting.conditions.ComparisonOperator.GT, DynamicAmount.VariableReference("beforeGraveyard")), Effects.Composite(listOf('
add('graveyard_growth',['DF10'],[('            Effects.IfYouDo(','            Effects.StoreNumber("beforeGraveyard", '+count+'),\n            Effects.IfYouDo('),('ifYouDo = Effects.Composite(listOf(','ifYouDo = '+guard),('                )),\n                successCriterion','                ))),\n                successCriterion')],'DF10 successful sacrifice redirected to exile retains five power payoff','d.getLifeTotal(me) shouldBe 25')
selection='            Effects.SelectTarget(\n                TargetObject(filter = TargetFilter.CreatureYouControl.other()),\n                storeAs = "discipleSacrifice"\n            ),\n'
add('triggered_target',['DF12'],[('        trigger = Triggers.EntersBattlefield','        trigger = Triggers.EntersBattlefield\n        target = TargetObject(filter = TargetFilter.CreatureYouControl.other())'),(selection,''),('EntityReference.FromCostStorage("discipleSacrifice")','EntityReference.Target(0)'),('EffectTarget.PipelineTarget("discipleSacrifice")','EffectTarget.ContextTarget(0)')],'DF12 selecting own fodder emits no triggered-target event before payoff','d.events.drop(start).filterIsInstance<BecomesTargetEvent>().size shouldBe 0')
add('no_modal_layout',['DF13','DF19'],[('layout = CardLayout.MODAL_DFC','layout = CardLayout.NORMAL')],'DF13 legal Garden land action is distinct from a creature cast','offered.size shouldBe 1')
add('life_cost_two',['DF14'],[('EntersTapped(payLifeCost = 3)','EntersTapped(payLifeCost = 2)')],'DF14 Garden pays exactly three life to enter untapped','d.getLifeTotal(me) shouldBe 17')
add('no_tapped_replacement',['DF15'],[('    replacementEffect(EntersTapped(payLifeCost = 3))','    // Isolated mutant: no enters-tapped replacement.')],'DF15 Garden declines any offered payment and ends tapped','d.isTapped(id) shouldBe true')
add('life_cost_one',['DF16'],[('EntersTapped(payLifeCost = 3)','EntersTapped(payLifeCost = 1)')],'DF16 Garden at two life cannot buy an untapped final state','d.isTapped(id) shouldBe true')
add('white_mana',['DF17'],[('Effects.AddMana(Color.GREEN, 1)','Effects.AddMana(Color.WHITE, 1)')],'DF17 Garden activation produces exactly one green and no other mana','pool.green shouldBe 1')
add('back_cost_six',['DF18'],[('    typeLine = "Land"','    typeLine = "Land"\n    manaCost = "{3}{G}{G}{G}"')],'DF18 Garden battlefield uses land characteristics and mana value zero','component.manaCost.cmc shouldBe 0')
normal_end='}.copy(layout = CardLayout.MODAL_DFC, backFace = gardenOfFreyaliseDefinition())'
add('canonical_land',['DF20'],[(normal_end,normal_end+'.let { front -> front.backFace!!.copy(name = front.name, metadata = front.metadata, layout = front.layout, backFace = front.backFace) }')],'DF20 noncast entry uses the front creature and creates exactly one trigger','d.getCreatures(me).contains(id) shouldBe true')
add('wrong_back_collector',['DF22'],[('    typeLine = "Land"','    typeLine = "Land"'),('private fun gardenOfFreyaliseDefinition(): CardDefinition = card("Garden of Freyalise") {','private fun gardenOfFreyaliseDefinition(): CardDefinition = card("Garden of Freyalise") {')], 'DF22 both card faces and metadata survive polymorphic JSON roundtrip','decoded.backFace!!.metadata.collectorNumber shouldBe "250"')
# Bind back-face metadata through the unique enclosing prefix, not a blind replace-all.
prefix=card[:card.index('\nval DiscipleOfFreyalise:')]
mutprefix=prefix.replace('collectorNumber = "250"','collectorNumber = "251"')
assert prefix.count('collectorNumber = "250"')==1
record[-1]['edits']=[dict(before=prefix,after=mutprefix)]
record[-1]['source_sha256']=hashlib.sha256(card.replace(prefix,mutprefix).encode()).hexdigest()
# Required distinct requirement witnesses, even when one mutation covers multiple requirements.
def extra_witness(case_template, case_name, assertion):
 start=tests.index('test("'+case_template+'")')
 line=next(i+1 for i,l in enumerate(tests.splitlines()) if l.strip()==assertion and sum(len(x)+1 for x in tests.splitlines()[:i])>=start)
 return dict(case=case_name,line=line,assertion=assertion)
record[0]['additional_witnesses']=[
 extra_witness('DF06 power $power sacrifices without drawing or losing life','DF06 power '+str(v)+' sacrifices without drawing or losing life','d.getLifeTotal(me) shouldBe 20') for v in [0,-2]
]+[
 extra_witness('DF11 full state serialization at $pause preserves five power payoff','DF11 full state serialization at '+v+' preserves five power payoff','d.getLifeTotal(me) shouldBe 25') for v in ['optional','selection']
]
next(m for m in record if m['id']=='no_modal_layout')['additional_witnesses']=[extra_witness('DF19 exile permission castOnly=$castOnly distinguishes Garden play','DF19 exile permission castOnly=false distinguishes Garden play','offered shouldBe !castOnly')]
result=dict(state='ISOLATED_NOT_COMPILED_NOT_APPLIED_NOT_QUALIFIED',base_source='be6b10da62d5a9e96eac8e635534e935d489cf0f',card_source='12adbbbcb5f8957c3efbbc70dfdef4ca2b876279',card_blob='1c79e49f0ab227b6f85a5c8f641c8cad4d900e03',test_sha256=hashlib.sha256(tests.encode()).hexdigest(),prior_identities=51,prior_assertions=sum(asserts(base).values()),new_expanded_cases=14,total_assertions=sum(asserts(tests).values()),mutations=record,remaining=['Compile every exact normal/mutant form before any behavior; immutable protocol must bind all expanded identities and affected/unaffected controls.','R1 compilefailure41291145372 received847cc965; R2 corrected qualification41292141699 pending. Do not change frozen source or duplicate qualification.','DF21 saved Garden play fixture excludes unspent Palace rider mana and counters; preserve accepted Opal/Fblthp dependent controls. Finish DF22 discovery/printing/metadata/snapshot and full source-bound compatibility.','DF06 and DF11 have separately bound required assertion lines for fixed-one; all remain unexecuted.','No compiler/setup failures count as behavioral witnesses; the newly composed graveyard-growth/targeted/canonical forms remain uncompiled.'],scope='Only isolated candidate card data and named scenario tests; production runtime remains unchanged. Preserve every original assertion and all downstream outcomes.')
Path('card-controls-review.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({'mutants':len(record),'prior_assertions':result['prior_assertions'],'total_assertions':result['total_assertions']},indent=2))
