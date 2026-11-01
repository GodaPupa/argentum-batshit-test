import zipfile,json,hashlib,sys,subprocess
from pathlib import Path
z=zipfile.ZipFile('extension-r2-original.zip');names=z.namelist();assert len(names)==len(set(names)) and z.testzip() is None
f={n:z.read(n) for n in names};sha=lambda b:hashlib.sha256(b).hexdigest();gitsha=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
M=json.loads(f['MANIFEST.json']);assert set(f)==set(M)|{'MANIFEST.json'}
assert all(sha(f[n])==h for n,h in M.items())
A=json.loads(Path('extension-r2-api.json').read_text());B=json.loads(f['BINDING.json']);P=json.loads(f['protocol/PROTOCOL.json'])
assert B['source']=='be6b10da62d5a9e96eac8e635534e935d489cf0f' and B['validation']=='9bf8f7a66153540efae4c502f8eb9a5a495c413b'
assert B['run']=='41292141699' and B['attempt']==B['run_number']=='1'
assert A['source']['tree']['sha']==B['tree'] and A['source']['parents'][0]['sha']==P['base']
assert A['validation']['tree']['sha']==B['validation_tree'] and A['validation']['parents'][0]['sha']==B['source']
r=A['run'];assert r['id']==41292141699 and r['head_sha']==B['validation'] and r['run_attempt']==r['run_number']==1 and r['status']=='completed' and r['conclusion']=='success' and r['event']=='push'
j=A['jobs']['jobs'];assert len(j)==1 and j[0]['id']==124805382564 and j[0]['conclusion']=='success'
a=A['artifacts']['artifacts'];assert len(a)==1 and a[0]['id']==int(sys.argv[1]) and not a[0]['expired']
b=Path('extension-r2-original.zip').read_bytes();assert len(b)==a[0]['size_in_bytes'] and a[0]['digest']=='sha256:'+sha(b)
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
assert B['tree']=='e26a2c2292d5414f44decc0282536f5a5c02b769' and B['validation_tree']=='2f8cbfe642ebf1bb9f0a4f8c84afa3f133c869ee'
assert B['workflow_blob']=='fefd569fc042c0cc6824a033f690e55178a24923'
assert f['initial/PROTECTED.json']==f['restored/PROTECTED.json']==f['final/PROTECTED.json']
result=subprocess.run(['python3','extension-r2-receive.py','extension-r2-original.zip'],capture_output=True,text=True)
assert result.returncode==0,result.stderr
received=json.loads(result.stdout)
assert received['counts']==json.loads(f['RESULT.json'])['counts']
for form in P['forms']+['restored']:
 stage='observe-'+form
 raw=json.loads(f[stage+'/REPORT.json'])
 assert raw['total']==51 and len(raw['rows'])==51
 assert raw['failed']==sum(bool(row['failures'] or row['errors']) for row in raw['rows'])
 assert raw['failed']==(0 if form=='restored' else P['expected_failed'][form])
 assert all(not row['errors'] and row['skipped']==0 for row in raw['rows'])
received.update(state='ORIGINAL_RUNTIME_EXTENSION_GATE_VERIFIED_NOT_CARD_ADMISSION',source=B['source'],tree=B['tree'],validation=B['validation'],validation_tree=B['validation_tree'],run=r['id'],attempt=1,run_number=1,job=j[0]['id'],artifact=a[0]['id'],workflow=B['workflow'],workflow_blob=B['workflow_blob'],production_source_unchanged=True,protected_bindings=json.loads(f['final/PROTECTED.json']),card_admitted=False,full_qualification=False)
Path('extension-r2-audit.json').write_text(json.dumps(received,indent=2)+'\n')
print(json.dumps(received,indent=2))
