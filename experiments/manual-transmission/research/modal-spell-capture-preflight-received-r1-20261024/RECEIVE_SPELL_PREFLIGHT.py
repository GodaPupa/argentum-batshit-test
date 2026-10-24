from pathlib import Path
import json,hashlib,zipfile,re,xml.etree.ElementTree as ET
R=Path(__file__).resolve().parent;D=R.parent/'spell-draft';B=R.parent/'argentum';O=R/'spell-preflight-original'
M=json.loads((R/'spell-original-metadata.json').read_text());A=M['artifact'];run=M['run'];job=M['jobs']['jobs'][0]
sha=lambda b:hashlib.sha256(b).hexdigest()
blob=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
zpath=R/f"original-{A['id']}.zip"
assert zpath.stat().st_size==A['size_in_bytes']==11808
assert A['digest']=='sha256:'+sha(zpath.read_bytes())=='sha256:25a9bbeafa26738aebe3c9bce63b901201c2447780bae696c51fbd49e32e4bcb'
assert run['id']==A['workflow_run']['id']==40060387021
assert run['head_sha']==A['workflow_run']['head_sha']==M['validation']['sha']=='71cc58958c446e223083ac6e08d4aaaff6795a3b'
assert run['run_attempt']==run['run_number']==job['run_attempt']==1 and run['event']=='push'
assert run['conclusion']==job['conclusion']=='success' and run['status']==job['status']=='completed'
assert M['jobs']['total_count']==1 and job['id']==120772395132
assert all(s['conclusion']=='success' for s in job['steps'])
assert A['name']=='manual-modal-spell-preflight-r1-40060387021'
assert [p['sha'] for p in M['source']['parents']]==['103ac16642d60c0d02bd12004d37f2f87cf7f324']
assert [p['sha'] for p in M['validation']['parents']]==[M['source']['sha']]
with zipfile.ZipFile(zpath) as z:
 names=z.namelist();assert len(names)==len(set(names)) and z.testzip() is None
 assert all(not Path(n).is_absolute() and '..' not in Path(n).parts for n in names)
 z.extractall(O)
 assert {n for n in names if not n.endswith('/')}=={str(p.relative_to(O)) for p in O.rglob('*') if p.is_file()}
manifest={}
for line in (O/'SHA256SUMS').read_text().splitlines():
 h,n=line.split('  ',1);assert n not in manifest and sha((O/n).read_bytes())==h;manifest[n]=h
assert set(manifest)=={str(p.relative_to(O)) for p in O.rglob('*') if p.is_file()}-{'SHA256SUMS'}
P=json.loads((O/'protocol/PROTOCOL.json').read_text());binding=json.loads((O/'BINDING.json').read_text())
assert binding['source']==M['source']['sha']=='2de4b397ad8c3f54fd18f8697e3467de4b16b25a'
assert binding['tree']==M['source']['tree']['sha']=='05508e45dd832150ea71674d2718fff519584666'
assert binding['validation']==run['head_sha'] and binding['run']==str(run['id'])
assert binding['delta']==[f['filename'] for f in M['source_delta']['files']] and len(binding['delta'])==12
for f in M['source_delta']['files']:assert blob((D/f['filename']).read_bytes())==f['sha']
for p,h in P['sha256'].items():assert sha((D/p).read_bytes())==h
for n in ['PROTOCOL.json','gate.py','validation.just']:assert (O/'protocol'/n).read_bytes()==(D/'experiments/manual-transmission/research/modal-spell-capture-preflight-r1-20261024'/n).read_bytes()
wf=M['validation_delta']['files'];assert len(wf)==1 and wf[0]['filename']==run['path'] and wf[0]['status']=='added'
assert blob((O/'WORKFLOW.yml').read_bytes())==wf[0]['sha']
assert not (O/'TRACKED_CHANGES.txt').read_text().strip()
for stage in ['compile','targeted']:
 assert int((O/(stage+'-exit.txt')).read_text())==0
 log=(O/(stage+'.log')).read_text();assert 'BUILD SUCCESSFUL' in log and not re.search(r'^e: |^> Task .* FAILED$',log,re.M)
 assert re.search(r'^> Task :rules-engine:'+('compileKotlin' if stage=='compile' else 'test')+r'$',log,re.M)
rows=[]
for p in (O/'xml').glob('*.xml'):
 root=ET.parse(p).getroot()
 for t in root.iter('testcase'):
  assert not t.findall('failure') and not t.findall('error') and not t.findall('skipped')
  rows.append(t.attrib['classname']+'::'+t.attrib['name'])
 assert int(root.attrib['tests'])==len(list(root.iter('testcase')))
assert sorted(rows)==sorted(P['tests']) and len(rows)==8
assert [r[0] for r in json.loads((O/'RAW_ROWS.json').read_text())]==rows
bindings=json.loads((B/'experiments/manual-transmission/research/paused-token-entry-r2-20261023/BINDINGS.json').read_text());assert len(bindings)==144
for p,v in bindings.items():assert sha((B/p).read_bytes())==v['sha256'] and not (D/p).exists()
result=dict(status='TARGETED_SOURCE_PREFLIGHT_PASSED_NOT_COMPONENT_ACCEPTANCE',source=binding['source'],tree=binding['tree'],validation=binding['validation'],run=run['id'],job=job['id'],attempt=1,artifact=A,manifest_entries=len(manifest),original_tests=rows,normal_compilation=True,protected_bindings=144,remaining=P['remaining'],official_games=0,official_seeds=0,precision_reserved=2)
(R/'SPELL_PREFLIGHT_VERIFIED.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
