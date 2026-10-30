"""Build an isolated early-gate draft; never dispatch or accept evidence."""
from pathlib import Path
import hashlib,json,re
from mutants import forms,STACK
HERE=Path(__file__).resolve().parent
ROOT=HERE.parent
PREFIX='com.wingedsheep.engine.scenarios.'
ERA='mtg-sets/2008-2016/tests/src/test/kotlin/com/wingedsheep/engine/scenarios/'
OTHER='mtg-sets/2017-2022/tests/src/test/kotlin/com/wingedsheep/engine/scenarios/'
normal={str(p.relative_to(ROOT/'opal31/draft')):p.read_text() for p in (ROOT/'opal31/draft').rglob('*') if p.is_file()}
before='if (commanderManaRider != null && commanderManaRider.count > 0 && !resolvingAsSpellCopy) {'
after='if (commanderManaRider != null && commanderManaRider.count > 0 && !resolvingAsSpellCopy &&\n            newState.projectedState.canReceiveCounters(spellId)) {'
assert normal[STACK].count(before)==1
normal[STACK]=normal[STACK].replace(before,after)
normal[ERA+'CommanderManaRiderAdditionalTest.kt']=(HERE/'CommanderManaRiderAdditionalTest.kt').read_text()
tests={p:s for p,s in normal.items() if '/scenarios/' in p}
tests[ERA+'PyromancersGogglesScenarioTest.kt']=(HERE/'controls/PyromancersGogglesScenarioTest.kt').read_text()
tests[OTHER+'CarnelianOrbOfDragonkindScenarioTest.kt']=(HERE/'controls/CarnelianOrbOfDragonkindScenarioTest.kt').read_text()
cases={}
for path,source in tests.items():
 cls=None
 for number,line in enumerate(source.splitlines(),1):
  match=re.search(r'^class (\w+)',line)
  if match:cls=match[1]
  match=re.search(r'\btest\("([^"\\]*)"\)',line)
  if match:
   assert cls is not None
   ident=PREFIX+cls+'::'+match[1];assert ident not in cases
   cases[ident]=dict(classname=PREFIX+cls,name=match[1],path=path,file=Path(path).name,test_line=number,
       module=':mtg-sets:2017-2022:tests' if path.startswith('mtg-sets/2017-2022') else ':mtg-sets:2008-2016:tests')
assert len(cases)==56,len(cases)
baseline_classes={'OpalRiderBaselineTest','PyromancersGogglesScenarioTest','CarnelianOrbOfDragonkindScenarioTest'}
controls=[key for key,c in cases.items() if c['classname'].split('.')[-1] in baseline_classes]
assert len(controls)==10
# The required assertion's exact source line is frozen before executable observation.
anchors={
 'OP01':('counters(game, commanderId) shouldBe 1','expected:<1> but was:<0>'),
 'OP02':('counters(game, id) shouldBe 1','expected:<1> but was:<0>'),
 'OP03':('(rider(game, id) == null) shouldBe true','expected:<true> but was:<false>'),
 'OP04':('rider(game, id)!!.count shouldBe 6','expected:<6> but was:<3>'),
 'OP05':('counters(game, commanderId) shouldBe 2','expected:<2> but was:<0>'),
 'OP06':('counters(game, id) shouldBe 3','expected:<3> but was:<4>'),
 'OP07':('(decision is com.wingedsheep.engine.core.YesNoDecision) shouldBe true','expected:<true> but was:<false>'),
 'OP08':('roundTrip.isSuccess shouldBe true','expected:<true> but was:<false>'),
 'OP09':('has<com.wingedsheep.engine.state.components.stack.CommanderManaEntryCountersComponent>() shouldBe false','expected:<false> but was:<true>'),
 'OP10':('(rider(game, id) == null) shouldBe true','expected:<true> but was:<false>'),
 'OP11':('game.state.getEntity(copyId)!!.has<com.wingedsheep.engine.state.components.stack.CommanderManaEntryCountersComponent>() shouldBe false','expected:<false> but was:<true>'),
 'OP12':('(rider(game, id) == null) shouldBe true','expected:<true> but was:<false>'),
 'OP13':('counters(game, commanderId) shouldBe 0','expected:<0> but was:<1>'),
 'OP14':('(rider(game, id) == null) shouldBe true','expected:<true> but was:<false>'),
 'OP15':('counters(game, id) shouldBe 1','expected:<1> but was:<0>'),
 'OP17':('counters(game, id) shouldBe 0','expected:<0> but was:<1>'),
 'OP18':('counters(game, id) shouldBe 0','expected:<0> but was:<1>'),
}
allforms=forms(normal);assert len(allforms)==17
witnesses={}
for form in allforms:
 op=form.split('-')[0];anchor,message=anchors[op];wc={}
 for key,c in cases.items():
  if c['classname']!=PREFIX+'OpalWitness'+op+'Test':continue
  lines=tests[c['path']].splitlines();stop=next((i for i in range(c['test_line'],len(lines)) if re.search(r'\btest\("',lines[i])),len(lines))
  matches=[i+1 for i in range(c['test_line'],stop) if anchor in lines[i]]
  assert matches,(op,key);wc[key]=dict(line=matches[0],message=message)
 assert len(wc)==(2 if op=='OP17' else 1)
 witnesses[form]=wc
sha=lambda s:hashlib.sha256(s.encode()).hexdigest()
contract=dict(state='ISOLATED_DRAFT_UNCOMPILED_NO_DISPATCH',compile_dependency=dict(source='1831fa39f08338676f468b85123ac1641e3df5a3',run=41136407228),
 cases=cases,controls=controls,witnesses=witnesses,
 normal_hashes={p:sha(s) for p,s in normal.items()},forms={k:{p:sha(s) for p,s in v.items()} for k,v in allforms.items()},
 mandatory_order=['receive original compile R2','freeze successor including guard and exact tests/protocol','compile normal and all17mutants before any negative execution','normal56 cases','17negative groups with18exact assertion failures and170unaffected controls','compile restored and restored56cases','original receiving','full dependent/card/64client/metadata/printing/lint/Assay and original receiving'],
 feature_admitted=False,card_admitted=False)
(HERE/'EARLY_CONTRACT.json').write_text(json.dumps(contract,indent=2,ensure_ascii=False)+'\n')
lines=['set shell := ["bash", "-euo", "pipefail", "-c"]','compile:', '    scripts/gradle-locked compileTestKotlin --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1']
for recipe,keys in [('normal',list(cases)),('restored',list(cases))]+[(form,list(w)+controls) for form,w in witnesses.items()]:
 bymodule={}
 for key in keys:bymodule.setdefault(cases[key]['module'],set()).add(cases[key]['classname'])
 command='scripts/gradle-locked'
 for module,classes in sorted(bymodule.items()):
  command+=' '+module+':test '+ ' '.join("--tests '"+c+"'" for c in sorted(classes))
 command+=' --continue --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1'
 lines += [recipe+':','    '+command]
(HERE/'validation.just').write_text('\n'.join(lines)+'\n')
print(json.dumps(dict(normal_rows=len(cases),mutant_forms=len(allforms),witnesses=sum(map(len,witnesses.values())),negative_controls=17*len(controls),compiled=False)))
