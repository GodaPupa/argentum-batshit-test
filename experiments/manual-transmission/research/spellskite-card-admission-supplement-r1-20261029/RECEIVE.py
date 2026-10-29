"""Fail-closed original supplemental evidence receipt; classification remains a separate gate."""
from pathlib import Path
import hashlib,json,zipfile,gzip
HERE=Path(__file__).resolve().parent
sha=lambda b:hashlib.sha256(b).hexdigest()
def bound(raw,bindings):
 for label in ['initial','final']:
  assert raw[label+'/TRACKED.diff']==b''
  for path,h in bindings.items():assert sha(raw[label+'/source/'+path])==h,(label,path)
 clb='mtg-sets/src/test/resources/snapshots/cards/CLB.json'
 assert raw['initial/source/'+clb]==raw['final/source/'+clb]==raw['EARLY_CLB.json']
 assert raw['initial/UNCHANGED_TREES.json']==raw['final/UNCHANGED_TREES.json']
def receive():
 p=json.loads((HERE/'PINS.json').read_text());m=json.loads((HERE/'metadata.json').read_text());P=json.loads((HERE/'PROTOCOL.json').read_text());r=m['run'];a=m['artifact'];j=m['jobs']['jobs']
 assert r['id']==p['run'] and r['head_sha']==p['validation'] and r['head_branch']==p['branch'] and r['path']==p['workflow'] and r['workflow_id']==p['workflow_id']
 assert r['run_attempt']==r['run_number']==1 and r['event']=='push' and r['status']=='completed'
 assert len(j)==1 and j[0]['id']==p['job'] and j[0]['run_id']==r['id'] and j[0]['run_attempt']==1 and j[0]['status']=='completed'
 assert a['name']==p['artifact_name'] and not a['expired'] and a['workflow_run']['head_sha']==p['validation'] and a['workflow_run']['id']==r['id']
 for k,parent in [('source',P['base']),('validation',p['source'])]:
  assert m[k]['sha']==p[k] and m[k]['tree']['sha']==p['tree' if k=='source' else 'validation_tree'] and [v['sha'] for v in m[k]['parents']]==[parent]
 assert m['main']['sha']==P['main']
 assert sorted(f['filename'] for f in m['source_delta']['files'])==sorted(P['source_delta'])
 assert [f['filename'] for f in m['validation_delta']['files']]==[p['workflow']]
 b=(HERE/'original.zip').read_bytes();assert len(b)==a['size_in_bytes'] and 'sha256:'+sha(b)==a['digest']
 with zipfile.ZipFile(HERE/'original.zip') as z:
  assert z.testzip() is None;names=[n for n in z.namelist() if not n.endswith('/')];assert len(names)==len(set(names)) and all(not n.startswith('/') and '..' not in Path(n).parts for n in names);raw={n:z.read(n) for n in names}
 manifest=json.loads(raw['MANIFEST.json']);assert set(manifest)==set(raw)-{'MANIFEST.json'} and all(sha(raw[k])==v for k,v in manifest.items())
 binding=json.loads(raw['BINDING.json'])
 for k in ['source','tree','validation','validation_tree','workflow','workflow_blob']:assert binding[k]==p[k]
 assert binding['run']==str(r['id']) and binding['attempt']==binding['run_number']==1
 wf=raw['WORKFLOW.yml'];assert hashlib.sha1(b'blob '+str(len(wf)).encode()+b'\0'+wf).hexdigest()==p['workflow_blob']
 for n,h in p['protocol_files'].items():assert sha(raw['protocol/'+n])==h
 assert json.loads(raw['protocol/PROTOCOL.json'])==P
 errors=[];gates={}
 try:bound(raw,P['bindings']);gates['bindings']=len(P['bindings'])
 except Exception as e:errors.append({'gate':'source/restoration/CLB','error':repr(e)})
 dep=raw['DEPENDENT_RECEIPT.json'];assert hashlib.sha1(b'blob '+str(len(dep)).encode()+b'\0'+dep).hexdigest()==P['dependent_receipt_blob']
 d=json.loads(dep);assert d['artifact']==12872214018 and d['artifact_sha256']=='610ed6a29f3eccf7667e5e471990cf93d6221cb9f287aec567f20acd23c98d4f'
 assert d['gate_errors']==[{'stage':'snapshot candidate','error':"KeyError: 'final/source/mtg-sets/src/test/resources/snapshots/cards/CLB.json'"}]
 assert d['gates']['compile-normal']=={'compiled':True} and d['gates']['cards']['passed']==388 and d['gates']['ordinary']['passed']==18980 and d['gates']['ordinary']['skips']==43
 for name in ['compile-normal','printing','corpus','parse','explain','assay-compile','assay-nph','assay-clb']:
  try:
   rc=int(raw[name+'/exit.txt']);assert rc in ((0,1) if name=='assay-compile' else (0,));assert raw[name+'/stdout.log'] or raw[name+'/stderr.log'];gates[name]={'exit':rc,'stdout_sha256':sha(raw[name+'/stdout.log']),'stderr_sha256':sha(raw[name+'/stderr.log'])}
   if name=='compile-normal':assert b'BUILD SUCCESSFUL' in raw[name+'/stdout.log']
  except Exception as e:errors.append({'gate':name,'error':repr(e)})
 try:
  v=json.loads(raw['metadata/VERIFICATION.json']);assert v['pagination_complete'] and all(x['status']==200 for x in v['images'].values());gates['metadata']=v
  bulk=raw['corpus/oracle-cards.jsonl.gz'];found=[];count=0
  for line in gzip.decompress(bulk).decode().splitlines():
   line=line.strip().rstrip(',')
   if not line or line in ['[',']']:continue
   c=json.loads(line);count+=1
   if c.get('name')=='Spellskite':found.append(c)
  assert len(found)==1 and found[0]['oracle_id']=='e0aa6ce0-ca31-433b-ac6c-32b8675cdb71'
  fresh=json.loads(raw['metadata/nph.json']);assert found[0]['oracle_text']==fresh['oracle_text'];gates['corpus_identity']={'sha256':sha(bulk),'cards':count,'spellskite_rows':1}
 except Exception as e:errors.append({'gate':'metadata/corpus','error':repr(e)})
 if 'FAILURE.txt' in raw:errors.append({'gate':'collector','error':raw['FAILURE.txt'].decode()})
 state='ORIGINAL_SUPPLEMENT_RECEIVED_CLASSIFICATION_PENDING' if not errors and r['conclusion']==j[0]['conclusion']=='success' else 'ORIGINAL_FAILURE_RECEIVED_NOT_ACCEPTED'
 result=dict(p,state=state,artifact=a['id'],artifact_bytes=len(b),artifact_sha256=sha(b),manifest_entries=len(manifest),gates=gates,gate_errors=errors,card_admission=False,multiplayer_admission=False,official_games=0,official_seeds=0,precision_reserved=2)
 (HERE/'VERIFIED_RECEIPT.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({'state':state,'errors':errors}))
if __name__=='__main__':receive()
