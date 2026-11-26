"""Recovery-specific read-only byte/publication verification; no receipt or test execution."""
from pathlib import Path
import argparse,base64,datetime,gzip,hashlib,io,json,stat,subprocess,zipfile
p=argparse.ArgumentParser();p.add_argument('--commit',required=True);p.add_argument('--output',required=True);args=p.parse_args()
O=Path(__file__).resolve().parent;N=O.parent;R=N.parents[5];relative=O.relative_to(R).as_posix();namespace=N.relative_to(R).as_posix()
def git(*a):return subprocess.check_output(['git',*a],cwd=R)
def raw(path):return git('show',args.commit+':'+path)
def sha(b):return hashlib.sha256(b).hexdigest()
def obj(name):return json.loads(raw(relative+'/'+name))
head=git('ls-remote','origin','refs/heads/manual-transmission/clean-room-recovery-20261124').decode().split()[0]
assert head==args.commit,'live head differs from receipt publication'
entries={}
for row in git('ls-tree','-rz','--full-tree',args.commit,'--',relative).split(b'\0'):
 if not row:continue
 metadata,path=row.decode().split('\t');mode,kind,blob=metadata.split();assert mode=='100644' and kind=='blob'
 assert path.startswith(relative+'/')
 b=raw(path);assert (R/path).read_bytes()==b,'published bytes differ '+path
 entries[path]={'blob':blob,'bytes':len(b),'sha256':sha(b)}
index=obj('ARCHIVE_PARTS.json');assert index['bytes']==8308418 and index['sha256']=='499b2c7a84f5507adc4862a11f9a7c0c976e9ee54d3b9552f7a4be6adfd4f227'
assert len(index['parts'])==19
assembled=[]
for i,part in enumerate(index['parts']):
 assert part['file']==f'original.part{i:04d}.b64'
 enc=raw(relative+'/'+part['file']);assert enc.endswith(b'\n')
 b=base64.b64decode(enc[:-1],validate=True);assert base64.b64encode(b)+b'\n'==enc
 assert len(b)==part['bytes'] and sha(b)==part['sha256']
 assert len(b)==(450000 if i<18 else 208418)
 assembled.append(b)
b=b''.join(assembled);assert len(b)==index['bytes'] and sha(b)==index['sha256']
assert b==(O/'original.zip').read_bytes()
with zipfile.ZipFile(io.BytesIO(b)) as z:
 infos=z.infolist();names=[x.filename for x in infos];assert len(names)==len(set(names))
 for x in infos:
  name=x.filename.rstrip('/');assert name and ':' not in name and '\\' not in name and not any(ord(c)<32 or ord(c)==127 for c in name)
  assert all(v not in ('','.','..') for v in name.split('/')) and not x.flag_bits&1
  mode=stat.S_IFMT(x.external_attr>>16);assert mode in ((0,stat.S_IFDIR) if x.is_dir() else (0,stat.S_IFREG))
 regular={x.filename for x in infos if not x.is_dir()}
 assert not any('/'.join(n.split('/')[:i]) in regular for n in names for i in range(1,len(n.split('/'))))
 assert z.testzip() is None
 outer=json.loads(z.read('MANIFEST.json'));actual={name:sha(z.read(name)) for name in regular if name!='MANIFEST.json'};assert actual==outer
 inner=json.loads(z.read('qualification/MANIFEST.json'));actual_inner={name[len('qualification/'):]:sha(z.read(name)) for name in regular if name.startswith('qualification/') and name!='qualification/MANIFEST.json'};assert inner==actual_inner
 pins=json.loads(z.read('qualification/CANDIDATE_PINS.json'));assert len(pins['bindings'])==378
 source='9ae883029afe28e1969179b00343e2ebafc836e8';record='experiments/manual-transmission/research/surge-bushwhacker-consolidated-proposal-20261114'
 assert pins==json.loads(git('show',source+':'+record+'/CANDIDATE_PINS.json'))
 for path,pin in pins['bindings'].items():
  bb=z.read('qualification/frozen-source/'+path);assert len(bb)==pin['bytes'] and sha(bb)==pin['sha256']
  assert bb==git('show',source+':'+path)
 restoration=json.loads(z.read('qualification/RESTORATION.json'));assert restoration['exact_normal_bytes'] is True and restoration['bindings']==pins['bindings']
 runtime=json.loads(z.read('HOSTED_RUN.json'));assert runtime==obj('HOSTED_RUNTIME_OBSERVATION.json')
 assert runtime['readiness_sequences_started']==runtime['readiness_sequences_completed']==runtime['qualification_starts']==runtime['qualification_completed']==1
 assert runtime['qualification_returncode']==1 and runtime['admitted'] is False
 artifact=obj('GITHUB_ARTIFACT.json');run=obj('GITHUB_RUN.json');job=obj('GITHUB_JOB.json');expected=obj('EXPECTED.json')
 assert artifact['id']==expected['artifact_id']==15063444055 and artifact['size_in_bytes']==len(b) and artifact['digest']=='sha256:'+sha(b)
 assert artifact['name']==expected['artifact_name']=='manual-host-requalification-candidate02-20261124-45695073375'
 assert run['id']==job['run_id']==artifact['workflow_run']['id']==runtime['run_id']==45695073375
 assert job['id']==runtime['job_id']==139490227770 and run['run_number']==run['run_attempt']==job['run_attempt']==1
 assert run['head_sha']==job['head_sha']==artifact['workflow_run']['head_sha']==runtime['event']=='ce9020568ae3702f384d401fcdc2a4516c84aa0f'
 assert run['repository']['full_name']=='GodaPupa/argentum-batshit-test' and run['status']==job['status']=='completed' and run['conclusion']==job['conclusion']=='failure'
 assert expected['already_received'] is None and expected['prior_receipt_count'] is None
derived=[]
for entry in obj('DERIVED_OUTPUTS.json'):
 enc=raw(relative+'/'+entry['file']);assert len(enc)==entry['encoded_bytes'] and sha(enc)==entry['encoded_sha256']
 plain=gzip.decompress(base64.b64decode(enc,validate=False));assert len(plain)==entry['uncompressed_bytes'] and sha(plain)==entry['uncompressed_sha256']
 assert plain==(O/entry['original_name']).read_bytes();derived.append(entry['original_name'])
base='7b472f49e74e1904a02870ac424ac2213b96559c';old=json.loads(git('show',base+':'+namespace+'/CURRENT.json'));current=json.loads(raw(namespace+'/CURRENT.json'))
assert raw(namespace+'/CURRENT.json')==(N/'CURRENT.json').read_bytes()
for key in ['owner','lease','source_freeze','prior_consumed_attempts','prior_failure','ci_receiving_reservation']:assert current[key]==old[key],key
for key in ['hosted_activations','readiness_reserved','qualification_reserved','original_artifact_downloads','original_artifact_receives','local_started']:assert current['attempts'][key]==old['attempts'][key],key
assert current['admitted'] is False and current['safeguards']['phase2_games']==current['safeguards']['phase2_seeds']==0
for line in git('diff','--name-status',base,args.commit).decode().splitlines():
 status,path=line.split('\t');assert status in ('A','M') and (path==namespace+'/CURRENT.json' or path.startswith(relative+'/')),line
report={'schema':'manual-transmission/recovery-publication-readback/v1','state':'RECOVERY_ORIGINAL_BYTES_AND_EVIDENCE_IMMUTABLE_LIVE_READBACK_PASS','commit':args.commit,'live_head':head,'verified_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'published_files':entries,'archive_bytes':len(b),'archive_sha256':sha(b),'canonical_parts':19,'zip_members':len(infos),'safe_members_and_crc':True,'outer_manifest_files':len(outer),'inner_manifest_files':len(inner),'frozen_source_bindings_verified':378,'exact_source_restoration':True,'provider_tuple_verified':True,'derived_audit_outputs_reconstructed':derived,'historical_reservation_counters_owner_and_unknown_execution_preserved':True,'allowed_delta_only':True,'qualification_successful':False,'admitted':False,'formal_frozen_receipt_complete':False,'formal_frozen_readback_complete':False,'formal_limitation':'Frozen receiver rejects null prior receipt count; frozen readback rejects recovery directory. Neither modified or relabelled.'}
out=Path(args.output);assert not out.exists();out.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps({k:v for k,v in report.items() if k!='published_files'},indent=2))
