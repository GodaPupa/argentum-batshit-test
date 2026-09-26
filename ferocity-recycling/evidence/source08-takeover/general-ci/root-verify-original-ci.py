from pathlib import Path
import json,zipfile,gzip,hashlib,re,collections,xml.etree.ElementTree as ET
from datetime import datetime,timezone

p=Path(__file__).parent
r=json.loads((p/'raw-ci-audit.json').read_text())
live=json.loads((p/'root-live-metadata.json').read_text())
meta=json.loads((p/'metadata.json').read_text())
src=json.loads((p/'root-source-evidence.json').read_text())
assert live['actions/runs/36268324392']['head_sha']==r['source_head']=='3a4f99a7653839506e96d19e6639f58d9e8c5ced'
assert live['actions/runs/36268324392']['conclusion']=='success'
assert live['git/commits/'+r['tested_merge_commit']]['tree']['sha']==r['tested_tree']=='56c6b8dd46dc112cdb70db496fd9d0c3e915e8a6'
observed={}; totals=collections.Counter();per_module=collections.defaultdict(collections.Counter);markers=[];archive_checks=[]
for art in live['actions/runs/36268324392/artifacts?per_page=100']['artifacts']:
 f=p/f"original-artifact-{art['id']}.zip";data=f.read_bytes();sha=hashlib.sha256(data).hexdigest()
 assert len(data)==art['size_in_bytes'] and 'sha256:'+sha==art['digest']
 claimed=r['original_artifacts'][str(art['id'])]
 assert claimed['bytes']==len(data) and claimed['sha256']==sha
 with zipfile.ZipFile(f) as z:
  assert z.testzip() is None
  assert set(z.namelist())==set(claimed['members'])
  for n in z.namelist():
   raw=z.read(n);v=claimed['members'][n]
   assert len(raw)==v['bytes'] and hashlib.sha256(raw).hexdigest()==v['sha256']
   if not n.endswith('.xml'):continue
   x=ET.fromstring(raw);cases=list(x.iter('testcase'));module=n.split('/build/')[0]
   counts={k:int(x.get(k,'0')) for k in ['tests','failures','errors','skipped']}
   assert len(cases)==counts['tests']
   actual={k:sum(c.find(k) is not None for c in cases) for k in ['failure','error','skipped']}
   assert actual=={'failure':counts['failures'],'error':counts['errors'],'skipped':counts['skipped']}
   assert counts['failures']==counts['errors']==0
   totals.update(counts)
   observed[(art['id'],n)]={'sha256':hashlib.sha256(raw).hexdigest(),'counts':counts,'module':module,'cases':[(c.get('classname'),c.get('name')) for c in cases],'timestamp':x.get('timestamp')}
   for c in cases:
    if c.get('classname','').startswith('Gradle Test Run'):
     markers.append({'artifact':art['id'],'path':n,'case':c.attrib});continue
    per_module[module]['actual']+=1
    per_module[module]['skip' if c.find('skipped') is not None else 'pass']+=1
 archive_checks.append({'id':art['id'],'bytes':len(data),'sha256':sha,'members':len(claimed['members'])})
assert len(observed)==4599 and totals=={'tests':17710,'skipped':52,'errors':0,'failures':0}
assert len(markers)==1 and markers[0]['case']['name']=='CardImageUriTest'
for row in r['xml']:
 v=observed[(row['artifact_id'],row['path'])]
 assert row['sha256']==v['sha256'] and row['counts']==v['counts']
fresh=collections.Counter();cached=collections.Counter()
for module,counts in per_module.items():
 (cached if module=='mtgish-tooling' else fresh).update(counts)
assert dict(fresh)=={'actual':17562,'pass':17513,'skip':49}
assert dict(cached)=={'actual':147,'pass':144,'skip':3}
jobs={str(j['id']):j for j in meta['jobs']['jobs']}
task_status={};logchecks=[]
for job_id,desc in r['decoded_job_logs'].items():
 raw=gzip.decompress((p/f'decoded-job-{job_id}.log.gz').read_bytes())
 assert len(raw)==desc['bytes'] and hashlib.sha256(raw).hexdigest()==desc['sha256']
 text=raw.decode();assert r['tested_merge_commit'] in text
 found=re.findall(r'> Task (:[^\s]+:test)(?=[ \r\n]|$)(?: (FROM-CACHE|UP-TO-DATE|NO-SOURCE|SKIPPED))?',text)
 for task,state in found:task_status[task]=state or 'FRESH'
 logchecks.append({'job_id':int(job_id),'sha256':desc['sha256'],'bytes':len(raw),'group':desc['group']})
groups={d['group']:jobs[j] for j,d in r['decoded_job_logs'].items()}
artgroups={a['id']:a['name'].removeprefix('ci-junit-') for a in live['actions/runs/36268324392/artifacts?per_page=100']['artifacts']}
for (art,n),v in observed.items():
 if v['module']=='mtgish-tooling': continue
 job=groups[artgroups[art]]
 stamp=datetime.fromisoformat(v['timestamp'].replace('Z','+00:00'))
 if stamp.tzinfo is None: stamp=stamp.replace(tzinfo=timezone.utc)
 lo=datetime.fromisoformat(job['started_at'].replace('Z','+00:00')); hi=datetime.fromisoformat(job['completed_at'].replace('Z','+00:00'))
 assert lo<=stamp<=hi,(n,v['timestamp'],job['started_at'],job['completed_at'])
assert len(task_status)==19
assert {k:v for k,v in task_status.items() if v!='FRESH'}=={':mtgish-tooling:test':'FROM-CACHE'}
for path,d in src.items():
 raw=d['content'].encode();assert hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==d['sha']
bankchecks=[]
for bank in r['required_receiving_token_banks']:
 v=observed[(bank['artifact_id'],bank['xml'])];code=src[bank['source_path']]['content']
 assert hashlib.sha256(code.encode()).hexdigest()==bank['source_sha256']
 names=[name for cl,name in v['cases']];assert sorted(names)==sorted(bank['cases'])
 declared=re.findall(r'\btest\("([^"\n]+)"\)',code)
 assert set(names)==set(declared),(bank['class'],set(names)^set(declared))
 assert v['counts']['failures']==v['counts']['errors']==v['counts']['skipped']==0
 assert bank['module']!='mtgish-tooling' and task_status[':'+bank['module'].replace('/',':')+':test']=='FRESH'
 bankchecks.append({'class':bank['class'],'cases':len(names),'source_path':bank['source_path'],'sha256':bank['source_sha256']})
marker=src[r['non_test_marker_source']['path']]['content']
assert '@EnabledIf(VerifyImageUrisCondition::class)' in marker and 'verifyImageUris' in marker
out={'schema':'root-independent-source08-ci-original-byte-checks-v1','source':r['source_head'],'merge':r['tested_merge_commit'],'tree':r['tested_tree'],'original_archives':archive_checks,'xml_count':len(observed),'raw_records':totals['tests'],'actual_records':sum(v['actual'] for v in per_module.values()),'marker':markers,'fresh':dict(fresh),'cached':dict(cached),'tasks':task_status,'logs':logchecks,'receiving_banks':bankchecks,'source_files_independently_fetched':len(src),'failures':0,'errors':0,'result':'RAW_BYTES_AND_RECEIVING20_PASS_CACHED147_DISPOSITION_SEPARATE'}
(p/'root-original-ci-checks.json').write_text(json.dumps(out,indent=2)+'\n')
print(json.dumps({'result':out['result'],'raw':out['raw_records'],'actual':out['actual_records'],'fresh':out['fresh'],'cached':out['cached'],'bank_cases':[x['cases'] for x in bankchecks],'sha256':hashlib.sha256((p/'root-original-ci-checks.json').read_bytes()).hexdigest()}))
