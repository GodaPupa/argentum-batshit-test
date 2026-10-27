"""Derive exact final inventories from already accepted evidence; no qualification rerun."""
import json,hashlib,zipfile,xml.etree.ElementTree as ET
from pathlib import Path
from collections import Counter
O=Path(__file__).resolve().parent
read=lambda p:json.loads(Path(p).read_text())
sha=lambda b:hashlib.sha256(b).hexdigest()
receipts={n:read(p+'/VERIFIED_RECEIPT.json') for n,p in [('baseline','context-full'),('public43','public-flow-received-r4'),('ui','ui-received-r5'),('generator','human-full-received-r1'),('transport','transport-receiving-r7'),('matrix','matrix-received-r4')]}
def rows(z,prefix):
 c=Counter();skip=Counter()
 for n in z.namelist():
  if not n.startswith(prefix+'/junit/') or not n.endswith('.xml'):continue
  mod=n.split('/junit/')[1].split('/build/test-results/')[0]
  if '/build/test-results/' not in n:continue
  rt=ET.fromstring(z.read(n))
  for t in rt.findall('.//testcase'):
   key='\t'.join([mod,t.get('classname'),t.get('name')]);c[key]+=1
   if t.find('skipped') is not None:skip[key]+=1
 return c,skip
raw=Path('context-full/original.zip').read_bytes();assert sha(raw)==receipts['baseline']['artifact_sha256']
with zipfile.ZipFile('context-full/original.zip') as z:base={k:rows(z,k) for k in ['focused','broad','ordinary','actor','recording']}
new=Counter(receipts['public43']['gates']['targeted']['names']);assert sum(new.values())==43
for cls,name in receipts['ui']['gates']['actual_ui']['fixture_cases']:new['\t'.join(['rules-engine',cls,name])]+=1
for cls,name in receipts['matrix']['gates']['normal']['identities']:new['\t'.join(['rules-engine',cls,name])]+=1
assert sum(new.values())==85
server=Counter('\t'.join(['game-server',*v]) for v in receipts['transport']['gates']['server-normal']['cases']);assert sum(server.values())==4
module=receipts['generator']['gates']['full-module'];gen=Counter('\t'.join(['mtgish-tooling',*v]) for v in module['identities']);gs=Counter('\t'.join(['mtgish-tooling',*v]) for v in module['skipped_identities']);assert sum(gen.values())==204
result={};summary={}
for stage,(c,sk) in base.items():
 original=c.copy()
 if stage in ['focused','broad','ordinary']:
  assert not set(new)&set(c);c+=new
 if stage=='ordinary':
  assert not set(server)&set(c);c+=server
  old=Counter({k:v for k,v in c.items() if k.startswith('mtgish-tooling\t')});oldsk=Counter({k:v for k,v in sk.items() if k.startswith('mtgish-tooling\t')})
  assert not old-gen and oldsk==gs
  c.subtract(old);c+=gen
 assert not original-c
 result[stage]={'identities':dict(sorted(c.items())),'skips':dict(sorted(sk.items()))}
 summary[stage]={'rows':sum(c.values()),'passes':sum(c.values())-sum(sk.values()),'skips':sum(sk.values()),'added':sum(c.values())-sum(original.values())}
(O/'EXACT_INVENTORIES.json').write_text(json.dumps(result,indent=2)+'\n')
(O/'ADDITIONS.json').write_text(json.dumps(dict(engine85=dict(new),server4=dict(server),tooling204=dict(gen)),indent=2)+'\n')
(O/'INVENTORY_REVIEW.json').write_text(json.dumps(dict(state='DERIVED_NOT_EXECUTED_NOT_COMPATIBILITY_ACCEPTANCE',source='6b3c5e4d8441b25525bd45b6237525cb12883a38',baseline_receipt='a702aad71c2a3aeae9c4645a65c88d346451c697',baseline_artifact_sha256=sha(raw),inventory_sha256=sha((O/'EXACT_INVENTORIES.json').read_bytes()),stages=summary,preserved_old_identities=True,preserved_skip_identities=True,ordinary_tooling_change='147→204; all147 original identities and3skips retained,57 new passing identities',engine_additions='43 public choice +2 browser fixtures +40 public matrix',server_additions=4,client_contract=64,required_remaining=['Implement/freeze/test full runner and original receiver using these exact counters','Verify current source bindings for reused accepted generator/transport/UI/public43 evidence','Final compatibility actual execution and original receiving','Full22Spellskite','Multiplayer'],official_games=0,official_seeds=0,precision_reserved=2),indent=2)+'\n')
print(json.dumps(summary))
