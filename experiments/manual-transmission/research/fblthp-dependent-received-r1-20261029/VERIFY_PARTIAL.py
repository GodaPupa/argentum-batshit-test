"""Audit completed gates and failed raw reports; never accepts the dependent suite."""
from pathlib import Path
from collections import Counter,defaultdict
import sys,json,hashlib,re,xml.etree.ElementTree as ET
R=Path(__file__).resolve().parent;E=R/'extracted'
sys.path.insert(0,str(E/'protocol'));import audit
P=json.loads((E/'protocol/PROTOCOL.json').read_text());I=audit.inventory(E/'initial/source')
assert hashlib.sha256(json.dumps(I,sort_keys=True,separators=(',',':')).encode()).hexdigest()==P['inventory_sha256']
assert I==json.loads((E/'INVENTORY.json').read_text())
cards=audit.junit(E/'cards',I['cards'])
log=(E/'compile/output.log').read_text()
assert (E/'compile/exit.txt').read_text().strip()=='0' and 'BUILD SUCCESSFUL' in log
assert not re.search(r'^e: |Compilation error|Unresolved reference|^> Task .* FAILED$',log,re.M)
seen=Counter();skips=Counter();failures=[];modules=defaultdict(Counter)
for f in sorted((E/'ordinary/junit').rglob('TEST-*.xml')):
 rel=str(f.relative_to(E/'ordinary/junit'));module=rel.split('/build/test-results/test/')[0]
 rt=ET.fromstring(f.read_bytes());rows=rt.findall('.//testcase');assert int(rt.get('tests'))==len(rows)
 for attr,tag in [('failures','failure'),('errors','error'),('skipped','skipped')]:
  assert int(rt.get(attr))==sum(t.find(tag)is not None for t in rows)
 for t in rows:
  key='\t'.join([module,t.get('classname'),t.get('name')]);seen[key]+=1;modules[module]['rows']+=1
  assert t.find('error') is None
  if t.find('skipped') is not None:skips[key]+=1;modules[module]['skips']+=1
  x=t.find('failure')
  if x is not None:
   assert x.get('type')=='org.opentest4j.AssertionFailedError'
   frame=next(line for line in (x.text or '').splitlines() if 'BecomesTargetPlayerAndAbilityAxesTest.kt:331' in line)
   failures.append(dict(identity=key,type=x.get('type'),message=x.get('message'),frame=frame))
assert not seen-Counter(I['ordinary']['identities'])
assert skips==Counter(I['ordinary']['skips']) and sum(skips.values())==43
assert len(failures)==1 and sum(seen.values())==8284
assert 'emits no BecomesTargetEvent (known bug' in failures[0]['identity']
assert 'expected:<0> but was:<1>' in failures[0]['message']
original=(R/'ORIGINAL_BECOMES_TARGET_TEST.kt').read_bytes()
test_blob=hashlib.sha1(b'blob '+str(len(original)).encode()+b'\0'+original).hexdigest()
assert test_blob=='9de28c3d3ce9d3e670b04cdbf1e5f2e372d5b80c'
result=dict(state='ORIGINAL_FAILURE_VERIFIED_PARTIAL_GATES_ONLY',compile_all=True,cards=cards,
 ordinary=dict(rows=sum(seen.values()),passes=sum(seen.values())-sum(skips.values())-len(failures),skips=sum(skips.values()),failures=failures,errors=0,modules=dict(modules),missing_expected_rows=sum((Counter(I['ordinary']['identities'])-seen).values()),extra_rows=0,exact_skip_identities=dict(skips)),
 inventory_sha256=P['inventory_sha256'],initial_final_sha256_bindings=len(P['bindings']),initial_final_git_bindings=len(P['git_bindings']),main_test_blob=test_blob,source_test_blob=test_blob,test_sha256=hashlib.sha256(original).hexdigest(),feature_admitted=False,card_admitted=False)
(R/'PARTIAL_VERIFICATION.json').write_text(json.dumps(result,indent=2)+'\n')
print('Verified compile-all, 389 cards, partial ordinary 8240 passes/43 skips/1 failure; 10790 rows not run. No admission.')
