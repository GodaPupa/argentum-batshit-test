"""Synthetic checks of the prepared raw-report receiver, never gameplay evidence."""
import ast,hashlib,json,re,tempfile,xml.etree.ElementTree as ET
from collections import Counter,defaultdict
from pathlib import Path

HERE=Path(__file__).resolve().parent
tree=ast.parse((HERE/'RECEIVE_PUBLIC_GROUPS_R5.py').read_text())
loop=next(n for n in tree.body if isinstance(n,ast.For) and isinstance(n.target,ast.Name) and n.target.id=='stage')
loop.iter=ast.List(elts=[ast.Constant('recording')],ctx=ast.Load())
code=compile(ast.fix_missing_locations(ast.Module(body=[loop],type_ignores=[])),'receiver-stage','exec')
cases={
 'ActionRecordingCompatibilityTest':[
 'com.wingedsheep.ai.arena.ActionRecordingCompatibilityTest::RC01 absent provenance preserves every legacy action field',
 'com.wingedsheep.ai.arena.ActionRecordingCompatibilityTest::RC02 explicit public groups remain fully recorded including empty groups',
 'com.wingedsheep.ai.arena.ActionRecordingCompatibilityTest::RC03 server provenance remains fully recorded with or without public groups',
 'com.wingedsheep.ai.arena.ActionRecordingCompatibilityTest::RC04 other actions and embedded diagnostic text remain unchanged'],
 'FrozenBaselineTest':['com.wingedsheep.ai.arena.FrozenBaselineTest::LEGACY_V0 plays the frozen baseline game exactly as it always has'],
}
source='cac0616eb5abb359eb572d6d378dbf9fabae62de';validation='a5bceaa568b17e9d4b68522612d4c5cbe1b78371';tree_sha='23ab174891290a575a752e54f0e8142a8d6f3e40'
results=[]
for variant in ['valid','missing','wrong_identity','duplicate','skip','failure','wrong_module','not_executed','nonzero']:
 with tempfile.TemporaryDirectory() as temp:
  raw=Path(temp);out=raw/'recording';out.mkdir()
  identities=[x for values in cases.values() for x in values]
  if variant=='missing':identities.pop()
  if variant=='duplicate':identities.append(identities[0])
  if variant=='wrong_identity':identities[0]+=' unexpected'
  suites=defaultdict(list)
  for identity in identities:
   cls,name=identity.split('::',1);suites[cls].append(name)
  modules=defaultdict(Counter);observed=defaultdict(list);names=Counter();failures=[];skips=[]
  for index,(cls,labels) in enumerate(sorted(suites.items())):
   suite=ET.Element('testsuite',tests=str(len(labels)),failures='0',errors='0',skipped='0')
   module='gym' if variant=='wrong_module' else 'ai';modules[module]['suites']+=1
   for i,label in enumerate(labels):
    row=ET.SubElement(suite,'testcase',classname=cls,name=label);identity=cls+'::'+label
    names[module+'\t'+cls+'\t'+label]+=1;observed[cls.rsplit('.',1)[-1]].append(identity);modules[module]['rows']+=1
    if index==i==0 and variant in ['skip','failure']:
     if variant=='skip':
      ET.SubElement(row,'skipped');suite.set('skipped','1');skips.append(identity);modules[module]['skipped']+=1
     else:
      ET.SubElement(row,'failure',type='org.opentest4j.AssertionFailedError',message='synthetic').text='synthetic'
      suite.set('failures','1');modules[module]['failures']+=1;failures.append(dict(identity=identity,type='org.opentest4j.AssertionFailedError',message='synthetic',stack='synthetic'))
    else:modules[module]['passed']+=1
   path=out/f'junit/{module}/build/test-results/test/TEST-{cls}.xml';path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(ET.tostring(suite))
  totals=Counter()
  for value in modules.values():totals.update(value)
  for k in ['suites','rows','passed','failures','errors','skipped']:totals.setdefault(k,0)
  summary=dict(source=source,tree=tree_sha,validation=validation,run='40139402248',totals=totals,modules=modules,duplicate_labels={k:v for k,v in names.items() if v>1},failures=failures,skips=sorted(skips),cases={k:sorted(v) for k,v in observed.items()})
  (out/'SUMMARY.json').write_text(json.dumps(summary))
  (out/'exit.txt').write_text('1' if variant=='nonzero' else '0')
  (out/'gradle.log').write_text('> Task :ai:test'+(' UP-TO-DATE' if variant=='not_executed' else '')+'\nBUILD SUCCESSFUL\n')
  scope=dict(RAW=raw,Counter=Counter,defaultdict=defaultdict,ET=ET,re=re,json=json,sha=lambda b:hashlib.sha256(b).hexdigest(),jread=lambda p:json.loads(p.read_text()),SOURCE=source,VALIDATION=validation,binding={'tree':tree_sha},M={'run_id_pin':40139402248},P={'recording_cases':cases},stage_results={})
  accepted=True
  try:exec(code,scope)
  except AssertionError:accepted=False
  assert accepted==(variant=='valid'),variant
  results.append(dict(case=variant,expected_disposition_verified=True))
result=dict(checks=results,behavioral_evidence=False,full_artifact_audit_executed=False)
(HERE/'SYNTHETIC_CHECKS.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({'checks':len(results),'all_passed':True,'behavioral_evidence':False}))
