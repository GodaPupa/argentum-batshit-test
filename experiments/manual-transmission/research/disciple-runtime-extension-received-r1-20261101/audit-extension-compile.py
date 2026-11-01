import zipfile,json,hashlib
from pathlib import Path
z=zipfile.ZipFile('extension-original.zip');names=z.namelist();assert len(names)==len(set(names)) and z.testzip() is None
f={n:z.read(n) for n in names};sha=lambda b:hashlib.sha256(b).hexdigest();gitsha=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
M=json.loads(f['MANIFEST.json']);assert set(f)==set(M)|{'MANIFEST.json'} and len(M)==16
assert all(sha(f[n])==h for n,h in M.items())
A=json.loads(Path('extension-api.json').read_text());B=json.loads(f['BINDING.json']);P=json.loads(f['protocol/PROTOCOL.json'])
assert B['source']=='9b46148f257b133bc4592729fddd3504732a710b' and B['validation']=='27a49b82a568eaac19eae07abbefa8535bdef173'
assert B['run']=='41291145372' and B['attempt']==B['run_number']=='1'
assert A['source']['tree']['sha']==B['tree'] and A['source']['parents'][0]['sha']==P['base']
assert A['validation']['tree']['sha']==B['validation_tree'] and A['validation']['parents'][0]['sha']==B['source']
r=A['run'];assert r['id']==41291145372 and r['head_sha']==B['validation'] and r['run_attempt']==r['run_number']==1 and r['status']=='completed' and r['conclusion']=='failure' and r['event']=='push'
j=A['jobs']['jobs'];assert len(j)==1 and j[0]['id']==124802502956 and j[0]['conclusion']=='failure'
a=A['artifacts']['artifacts'];assert len(a)==1 and a[0]['id']==13077682036 and not a[0]['expired']
b=Path('extension-original.zip').read_bytes();assert len(b)==a[0]['size_in_bytes']==77588 and a[0]['digest']=='sha256:'+sha(b)
assert a[0]['workflow_run']['id']==r['id'] and a[0]['workflow_run']['head_sha']==B['validation']
assert A['validation_delta']['files'][0]['filename']==B['workflow'] and len(A['validation_delta']['files'])==1
assert gitsha(f['WORKFLOW.yml'])==B['workflow_blob']==A['validation_delta']['files'][0]['sha']
delta=A['delta']['files'];assert {x['filename'] for x in delta}=={P['test']}|{P['record']+'/'+n for n in ['PROTOCOL.json','run.py','validation.just']}
for x in delta:
 p=x['filename'];blob=f['compile-normal/source/'+p] if p==P['test'] else f['protocol/'+p.split('/')[-1]]
 assert gitsha(blob)==x['sha']
expected={'PlayLandHandler.kt':'c6cdd3682ddf70190718077e02fc7d15c2500840','ModalAndCloneContinuationResumer.kt':'f88855a8d70a7c2c7449c46c228269bb0aa1b60d','CastFromZoneEnumerator.kt':'2145cae496683983b94722aa24080daec8dca63b','DiscipleOfFreyalise.kt':'1c79e49f0ab227b6f85a5c8f641c8cad4d900e03'}
hashes=json.loads(f['compile-normal/SOURCE_HASHES.json'])
for p in P['paths']+[P['test']]:
 assert sha(f['compile-normal/source/'+p])==hashes[p]
 if p!=P['test']:assert gitsha(f['compile-normal/source/'+p])==expected[p.split('/')[-1]]
assert f['initial/PROTECTED.json']==f['final/PROTECTED.json'] and 'FINAL_BINDING_FAILURE.txt' not in f
assert not any('/junit/' in n or n.startswith('observe-') for n in f) and 'ALL_FORMS_COMPILED.json' not in f
assert f['compile-normal/exit.txt'].strip()==b'1'
log=f['compile-normal/output.log'].decode();errors=[l for l in log.splitlines() if l.startswith('e:')]
assert len(errors)==1 and ':795:27 ' in errors[0] and "actual type is 'PlayersCantPlayLands'" in errors[0] and "'StaticAbilityBuilder.() -> Unit' was expected" in errors[0]
assert "AssertionError: ('normal', 1)" in f['FAILURE.txt'].decode()
out=dict(state='ORIGINAL_COMPILE_FAILURE_RECEIVED_NOT_BEHAVIORAL_EVIDENCE',**B,job=124802502956,artifact=13077682036,bytes=len(b),sha256=sha(b),manifest_entries=len(M),compiler_errors=errors,behavioral_reports=0,negative_witnesses=0,production_source_unchanged=True,protected_initial_final_equal=True,original_failure_preserved=True,admitted=False,repair='Change only the new fixture call to staticAbility { ability = PlayersCantPlayLands() }; preserve all51identities297assertionlines. Compile all4forms before behavior on a new immutable successor.')
Path('extension-compile-audit.json').write_text(json.dumps(out,indent=2)+'\n');print(json.dumps(out,indent=2))
