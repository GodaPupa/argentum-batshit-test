"""Compile-only receiving gates. This does not qualify any rider behavior."""
import hashlib,json,re,xml.etree.ElementTree as ET
sha=lambda b:hashlib.sha256(b).hexdigest()
blob=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
def verify(raw,p):
 for phase in ['initial','final']:
  assert raw[phase+'/TRACKED.diff']==b''
  assert set(json.loads(raw[phase+'/UNCHANGED_TREES.json']))==set(p['unchanged_modules'])
  for path,digest in p['bindings'].items():assert sha(raw[phase+'/source/'+path])==digest,path
  for path,digest in p['git_bindings'].items():assert blob(raw[phase+'/source/'+path])==digest,path
  for path,digest in p['original_bindings'].items():assert sha(raw[phase+'/original/'+path])==digest,path
 assert raw['initial/UNCHANGED_TREES.json']==raw['final/UNCHANGED_TREES.json']
 for stage in ['compile-normal','snapshot-export']:
  assert raw[stage+'/exit.txt'].strip()==b'0'
  log=raw[stage+'/output.log'].decode();assert 'BUILD SUCCESSFUL' in log
  assert not re.search(r'^e: |Compilation error|Unresolved reference|^> Task .* FAILED$',log,re.M)
 assert not any(k.startswith('compile-normal/junit/') for k in raw)
 reports=[k for k in raw if k.startswith('snapshot-export/junit/') and k.endswith('.xml')]
 assert len(reports)==1
 report=ET.fromstring(raw[reports[0]])
 assert [int(report.get(k)) for k in ['tests','failures','errors','skipped']]==[1,0,0,0]
 rows=report.findall('testcase');assert len(rows)==1
 row=rows[0];assert row.get('classname')=='com.wingedsheep.mtg.sets.OpalPalaceSnapshotExportTest'
 assert row.get('name')=='Opal Palace canonical discovery and lossless snapshot candidate'
 assert not any(row.find(k) is not None for k in ['failure','error','skipped'])
 original=raw['initial/source/mtg-sets/src/test/resources/snapshots/cards/C13.json']
 block=raw['snapshot/OpalPalace.block.json'];candidate=raw['snapshot/C13.candidate.json']
 assert block.startswith(b'// Opal Palace\n') and block.count(b'// Opal Palace\n')==1
 assert candidate.count(block)==1 and candidate==original.rstrip(b'\n')+b'\n\n'+block
 # Preserve exactly the existing bytes when creating the eventual committed successor.
 assert sha(original)==p['snapshot_original_sha256']
 card=json.loads(block.split(b'\n',1)[1]);assert card['name']=='Opal Palace'
 return dict(state='NORMAL_COMPILE_ONLY_VERIFIED_NO_BEHAVIORAL_ACCEPTANCE',sha_bindings=len(p['bindings']),git_bindings=len(p['git_bindings']),compiled_normal=True,compiled_mutants=False,snapshot_original_sha256=sha(original),candidate_sha256=sha(candidate),block_sha256=sha(block),snapshot_export_cases=1,skips=0,feature_admitted=False,card_admitted=False)
