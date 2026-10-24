"""Preserve and classify the original pinned run; this never grants component acceptance."""
from pathlib import Path
import hashlib,json,zipfile,xml.etree.ElementTree as ET,re
R=Path(__file__).resolve().parent;M=json.loads((R/'spell-r3-metadata.json').read_text());A=M['artifact'];S='035e0ec3da8729c43018ffcc839aa821517dcda3';V='266f4a28fecd66fabf4f34aa4e14cab20b0a771f';O=R/'spell-r3-original'
sha=lambda b:hashlib.sha256(b).hexdigest()
assert M['run']['id']==A['workflow_run']['id']==40071650391
assert M['run']['head_sha']==A['workflow_run']['head_sha']==V
assert M['run']['status']=='completed' and M['run']['run_attempt']==M['run']['run_number']==1
assert M['jobs']['total_count']==1 and M['jobs']['jobs'][0]['id']==120806179924
assert M['jobs']['jobs'][0]['run_attempt']==1
assert A['name']=='manual-modal-spell-capture-r3-40071650391'
p=R/f"original-{A['id']}.zip";assert p.stat().st_size==A['size_in_bytes']==M['artifact_bytes_pin']
assert A['id']==M['artifact_id_pin'] and A['digest']=='sha256:'+sha(p.read_bytes())=='sha256:'+M['artifact_sha256_pin']
with zipfile.ZipFile(p) as z:
 names=z.namelist();assert len(names)==len(set(names)) and z.testzip() is None
 assert all(not Path(n).is_absolute() and '..' not in Path(n).parts for n in names)
 z.extractall(O)
 assert {n for n in names if not n.endswith('/')}=={str(x.relative_to(O)) for x in O.rglob('*') if x.is_file()}
 for n in names:
  if not n.endswith('/'):assert (O/n).read_bytes()==z.read(n)
manifest={}
for line in (O/'SHA256SUMS').read_text().splitlines():
 h,n=line.split('  ',1);assert n not in manifest and sha((O/n).read_bytes())==h;manifest[n]=h
assert set(manifest)=={str(x.relative_to(O)) for x in O.rglob('*') if x.is_file()}-{'SHA256SUMS'}
B=json.loads((O/'BINDING.json').read_text());assert B['source']==S and B['validation']==V
assert B['tree']==M['source_commit']['tree']['sha']=='bb50c6e9de41619e796ac5550a449484bfcbf7cf'
assert [x['sha'] for x in M['validation_commit']['parents']]==[S]
blob=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
wf=M['validation_delta']['files'];assert len(wf)==1 and wf[0]['filename']==M['run']['path']
assert blob((O/'protocol/WORKFLOW.yml').read_bytes())==wf[0]['sha']
files={x['filename']:x for x in M['source_delta']['files']}
for name in ['PROTOCOL.json','gate.py','DESIGN.md']:
 assert blob((O/'protocol'/name).read_bytes())==files['experiments/manual-transmission/research/modal-spell-capture-r3-20261024/'+name]['sha']
rows=[]
for xml in sorted(O.rglob('TEST-*.xml')):
 suite=ET.parse(xml).getroot();tests=list(suite.iter('testcase'));assert int(suite.attrib['tests'])==len(tests)
 for attr,tag in [('failures','failure'),('errors','error'),('skipped','skipped')]:assert int(suite.attrib[attr])==sum(t.find(tag) is not None for t in tests)
 for t in tests:
  tag=next((k for k in ['failure','error','skipped'] if t.find(k) is not None),'passed');e=t.find(tag)
  rows.append(dict(report=str(xml.relative_to(O)),identity=t.attrib['classname']+'::'+t.attrib['name'],outcome=tag,type=e.get('type') if e is not None else None,text=e.text if e is not None else None))
logs={str(p.relative_to(O)):[l for l in p.read_text(errors='replace').splitlines() if re.search(r'^e: |^> Task .* FAILED$|Compilation error|Unresolved reference',l)] for p in O.rglob('*.log')}
result=dict(status='ORIGINAL_EVIDENCE_RECEIVED_NO_COMPONENT_ACCEPTANCE',run=40071650391,job=120806179924,source=S,validation=V,artifact=A,manifest_entries=len(manifest),workflow_conclusion=M['run']['conclusion'],steps=M['jobs']['jobs'][0]['steps'],raw_cases=rows,diagnostics={k:v for k,v in logs.items() if v},disposition=json.loads((O/'DISPOSITION.json').read_text()),zero_reports=len(rows)==0,behavioral_negative_accepted=False,official_games=0,official_seeds=0,precision_reserved=2)
(R/'SPELL_R3_ORIGINAL_DISPOSITION.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:result[k] for k in ['status','run','manifest_entries','workflow_conclusion','zero_reports','diagnostics']},indent=2))
print(json.dumps([r for r in rows if r['outcome']!='passed'],indent=2))
