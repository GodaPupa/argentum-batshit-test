from pathlib import Path
import json,hashlib,zipfile
r=Path(__file__).resolve().parent;pins=json.loads((r/'PINS.json').read_text());run=json.loads((r/'RUN.json').read_text());jobs=json.loads((r/'JOBS.json').read_text());a=json.loads((r/'ARTIFACTS.json').read_text())['artifacts'][0];b=(r/'original.zip').read_bytes();sha=lambda b:hashlib.sha256(b).hexdigest();gitsha=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
assert len(b)==a['size_in_bytes'] and 'sha256:'+sha(b)==a['digest'];assert run['id']==40905841877 and run['head_sha']==pins['validation'] and run['run_attempt']==run['run_number']==1 and run['conclusion']=='failure';job=jobs['jobs'][0];assert job['id']==123560230071 and job['run_id']==run['id'] and job['conclusion']=='failure'
with zipfile.ZipFile(r/'original.zip') as z:
 assert z.testzip() is None;names=z.namelist();assert len(names)==len(set(names));data={n:z.read(n) for n in names if not n.endswith('/')}
m=json.loads(data['MANIFEST.json']);assert set(m)==set(data)-{'MANIFEST.json'}
for k,v in m.items():assert sha(data[k])==v,k
for name,blob in pins['protocol_blobs'].items():assert gitsha(data['protocol/'+name])==blob,name
assert gitsha(data['WORKFLOW.yml'])==pins['workflow_blob'];binding=json.loads(data['BINDING.json'])
for k in ['source','tree','validation','validation_tree','workflow','workflow_blob']:assert binding[k]==pins[k]
assert int(binding['run'])==run['id'] and binding['attempt']==binding['run_number']==1
p=json.loads(data['protocol/PROTOCOL.json']);counts={}
for phase in ['initial','final']:
 for path,digest in {**p['accepted_bindings'],**p['new_bindings']}.items():
  value=data[phase+'/source/'+path]
  if path in p['git_bindings']:assert gitsha(value)==p['git_bindings'][path],path
  else:assert sha(value)==digest,path
 counts[phase]=sum(k.startswith(phase+'/source/') for k in data)
restoration={}
for path in p['allowed_protected_successors']:
 value=data['final/source/'+path]
 for ad in reversed(p['protected_additions'][path]):
  at=ad['byte_offset'];v=ad['bytes'].encode();assert value[at:at+len(v)]==v;value=value[:at]+value[at+len(v):]
 restoration[path]={'expected':p['accepted_bindings'][path],'actual':sha(value),'restored':sha(value)==p['accepted_bindings'][path]}
assert [k for k,v in restoration.items() if not v['restored']]==['rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/stack/StackResolver.kt']
assert not any(k.endswith('.xml') or k.endswith('exit.txt') or k.startswith('compile-') for k in data)
failure=data['FAILURE.txt'].decode();assert failure.endswith('AssertionError: rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/stack/StackResolver.kt\n')
receipt={'state':'FAILED_ORIGINAL_RECEIVED_PRECOMPILE_SOURCE_INTEGRITY','pins':pins,'run':run['id'],'job':job['id'],'attempt':1,'artifact':a['id'],'bytes':len(b),'sha256':sha(b),'manifest_entries':len(m),'available_source_files_verified':counts,'protected_restoration':restoration,'failure':failure,'cause':'Local hydration truncated StackResolver.kt and CoreContinuations.kt tails. Frozen candidate hashes identify these incomplete files; exact protected restoration correctly rejected StackResolver before compilation.','repair':'Restore exact authoritative Git blobs386edc58 and086271fc before reapplying authorized additions. Verify every hydrated input Git blob and every successor original-byte restoration before a new freeze. No assertions relaxed.','accepted_gates':[],'feature_admitted':False,'card_admitted':False,'official_games':0,'official_seeds':0}
(r/'FAILED_RECEIPT.json').write_text(json.dumps(receipt,indent=2)+'\n');(r/'FAILURE.txt').write_text(failure);print({k:receipt[k] for k in ['state','bytes','sha256','manifest_entries','available_source_files_verified']})
