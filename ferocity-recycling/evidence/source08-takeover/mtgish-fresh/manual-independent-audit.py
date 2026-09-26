#!/usr/bin/env python3
"""Read retained originals only; independently produce a bounded software review."""
from pathlib import Path
import json,zipfile,hashlib,gzip,re,collections,datetime
import xml.etree.ElementTree as ET
P=Path(__file__).parent
sha=lambda b:hashlib.sha256(b).hexdigest()
def filebind(p):
 b=p.read_bytes();return {'path':str(p),'bytes':len(b),'sha256':sha(b)}
def load(p):return json.loads(p.read_text())
SOURCE='3a4f99a7653839506e96d19e6639f58d9e8c5ced';TREE='56c6b8dd46dc112cdb70db496fd9d0c3e915e8a6';CONTROL='c0124122528ca66c598cfc27a7d6c3f9bad45af8'
raw=P/'original-artifact-10918016982.zip';blob=raw.read_bytes();assert sha(blob)=='d97a0331a0de7f6490684c31a4f3cfed1800dcaff87290edddeea0c73fce691e' and len(blob)==2317681
z=zipfile.ZipFile(raw);assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))==28
report=load(P/'raw-audit.json'); members={x['path']:x for x in report['members']};assert set(members)==set(z.namelist())
for n in z.namelist():
 b=z.read(n);m=members[n];assert m['sha256']==sha(b) and m['bytes']==len(b) and m['crc32']==f'{z.getinfo(n).CRC:08x}'
live={r['path']:r['data'] for r in load(P/'manual-live-source-control-run.json')}
assert live['git/commits/'+SOURCE]['tree']['sha']==TREE
ct=live['git/commits/'+CONTROL]['tree']['sha'];assert ct=='050dc68bdfc8014058773e48bcd4e500c9fa6811'
tr=live['git/trees/'+ct+'?recursive=1'];assert not tr['truncated'];blobs={r['path']:r for r in tr['tree'] if r['type']=='blob'};assert len(blobs)==3
runtime=json.loads(z.read('audit.json'));assert runtime['status']=='PASS_REQUIRES_ARTIFACT_REVIEW' and not runtime['errors']
assert (runtime['source_head'],runtime['source_tree'],runtime['control_head'])==(SOURCE,TREE,CONTROL)
for n,r in blobs.items():
 b=z.read('control/'+n);assert len(b)==r['size'];assert hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()==r['sha'];assert sha(b)==runtime['control_files_sha256'][n]
run=live['actions/runs/36278648943'];assert (run['head_sha'],run['event'],run['run_attempt'],run['conclusion'])==(CONTROL,'push',1,'success')
jobs=live['actions/runs/36278648943/jobs?per_page=100'];assert jobs['total_count']==1 and len(jobs['jobs'])==1
job=jobs['jobs'][0];assert job['id']==108506146945 and job['conclusion']=='success'
arts=live['actions/runs/36278648943/artifacts?per_page=100'];assert arts['total_count']==1
art=arts['artifacts'][0];assert art['id']==10918016982 and art['size_in_bytes']==len(blob) and art['digest']=='sha256:'+sha(blob)
freeze=json.loads(z.read('control/existing-mtgish-case-identities.json'));assert (freeze['source'],freeze['merge_tree'],freeze['expected_cases'],freeze['expected_skips'])==(SOURCE,TREE,147,3)
old=Path('ferocity-takeover/general-ci/original-artifact-10915365754.zip');assert sha(old.read_bytes())==freeze['original_artifact_sha256'];oz=zipfile.ZipFile(old);assert oz.testzip() is None
cmd=json.loads(z.read('mtgish-tooling/command.json'));assert cmd['exit_status']==0 and cmd['task']==':mtgish-tooling:test'
assert len(runtime['stages'])==1;stage=runtime['stages'][0]
for k in ['command','cwd','started_ns','finished_ns','exit_status','owned_process_group','task']:assert cmd[k]==stage[k]
assert '--rerun' in cmd['command'] and '--no-build-cache' in cmd['command'] and '--max-workers=1' in cmd['command']
expected={r['class']:collections.Counter((c['name'],c['skipped']) for c in r['cases']) for r in freeze['rows']};assert len(expected)==20
newrows=[];skips=[];seen=set();oldseen=set()
def parse(data):
 t=ET.fromstring(data);cases=t.findall('testcase');assert len(cases)==int(t.attrib['tests']);assert int(t.attrib.get('failures',0))==int(t.attrib.get('errors',0))==0;assert not t.findall('.//failure') and not t.findall('.//error')
 counts=collections.Counter((c.attrib['name'],c.find('skipped') is not None) for c in cases);assert sum(s*n for (_,s),n in counts.items())==int(t.attrib.get('skipped',0));return t,counts
for n in oz.namelist():
 if n.startswith('mtgish-tooling/build/test-results/test/TEST-') and n.endswith('.xml'):
  t,c=parse(oz.read(n));name=t.attrib['name'];assert name not in oldseen and c==expected[name];oldseen.add(name)
assert oldseen==set(expected)
for n in z.namelist():
 if n.startswith('mtgish-tooling/TEST-') and n.endswith('.xml'):
  b=z.read(n);t,c=parse(b);name=t.attrib['name'];assert name not in seen and c==expected[name];seen.add(name)
  stamp=datetime.datetime.fromisoformat(t.attrib['timestamp'].replace('Z','+00:00')).timestamp();assert cmd['started_ns']/1e9<=stamp<=cmd['finished_ns']/1e9
  skips.extend({'class':name,'name':k} for (k,s),num in c.items() if s for _ in range(num));newrows.append({'class':name,'cases':sum(c.values()),'skipped':sum(n for (_,s),n in c.items() if s),'sha256':sha(b),'timestamp':t.attrib['timestamp']})
assert seen==set(expected) and sum(r['cases'] for r in newrows)==147 and len(skips)==3
assert sorted(newrows,key=lambda r:r['class'])==sorted(report['suites'],key=lambda r:r['class'])
before=z.read('executable-and-read-inputs-before.json');assert before==z.read('executable-and-read-inputs-after.json');inputs=json.loads(before);assert len(inputs)==22743 and sha(before)==runtime['input_map_sha256']
for p,h in inputs.items():
 assert re.fullmatch('[0-9a-f]{64}',h) and not p.startswith('/') and '..' not in Path(p).parts
 assert p.startswith(('mtgish-tooling/','mtg-sdk/src/main/','mtg-sets/','buildSrc/','gradle/')) or p in ['build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew','justfile','scripts/test-class','scripts/gradle-locked']
assert len(runtime['dependencies_sha256'])==8
for p,h in runtime['dependencies_sha256'].items():assert inputs[p]==h
log=z.read('mtgish-tooling/command.log').decode();lines=log.splitlines();assert sum(x.strip()=='> Task :mtgish-tooling:test' for x in lines)==2
for suffix in ['FROM-CACHE','UP-TO-DATE','NO-SOURCE','SKIPPED']:assert '> Task :mtgish-tooling:test '+suffix not in log
assert "Gradle Test Executor 1 started executing tests." in log and "Gradle Test Executor 1 finished executing tests." in log
assert log.count("Starting process 'Gradle Test Executor 1'")==1 and '/usr/lib/jvm/temurin-21-jdk-amd64/bin/java' in log and 'BUILD SUCCESSFUL' in log
joblog=gzip.decompress((P/'decoded-job-108506146945.log.gz').read_bytes());assert sha(joblog)==report['decoded_job_log']['sha256'] and len(joblog)==report['decoded_job_log']['bytes'];jl=joblog.decode()
for pin in [SOURCE,CONTROL]:assert re.search(r'git log -1 --format=%H\n[^\n]*'+pin,jl)
assert 'SHA256 digest of uploaded artifact is '+sha(blob) in jl
status=Path('ferocity-takeover/MTGISH_FRESH_CURRENT_STATUS.md');assert sha(status.read_bytes())=='db478849e2b76474ba61320d834cf7a5b55cfaf18c018a1ded34c130f15c22d3'
assert sha((P/'README.md').read_bytes())=='86fe46212ce9abffdbf4f4c7c8a171ed33119a0a3321f6f649833c94ee02d6f2'
assert sha((P/'raw-audit.json').read_bytes())=='9f89b61366a995c32f1b07affb252baba96b212c79a670e462dab1f63ce9fbe2'
prior=Path('ferocity-takeover/general-ci/root-independent-ci-artifact-review.json');prospective=Path('ferocity-takeover/mtgish-fresh/root-one-use-control-source-review.json')
assert sha(prior.read_bytes())=='0a5b700c046919f993763fa6db9ced4bc0431d1d8d4aca60d7b49da0c40ccbb0';assert sha(prospective.read_bytes())=='43db085d2434691d3eb692cb9659d9bac32839c86ff313eba17a34b1e5b3f70d'
review={'schema':'ferocity-source08-mtgish147-independent-review-v1','reviewer':'actual /root/manual','source_author':False,'status':'ACCEPTED_FRESH_EXISTING_147_SOFTWARE_CASES_AND_BOUNDED_PUBLICATION','source':SOURCE,'tree':TREE,'control':CONTROL,'control_tree':ct,'run_id':36278648943,'attempt':1,'job_id':108506146945,'artifact_id':10918016982,'bound_files':[filebind(p) for p in [raw,P/'raw-audit.json',P/'README.md',P/'audit_original.py',P/'decoded-job-108506146945.log.gz',P/'manual-live-source-control-run.json',status,prior,prospective,old,Path(__file__)]],'independent_checks':['Reopened original new ZIP; whole size/SHA256 matches independently fetched live artifact metadata; CRC and all 28 members match author inventory. All three exact control files including hidden workflow retained.','Independently fetched source/control commit trees, complete three-file control Git tree, live run/job/artifact metadata; every retained control blob recomputed with Git SHA1 and SHA256. Read complete workflow and collector.','Read original cached source08 artifact10915365754 and all20 mtgish XML; exact case/skip multiplicities equal both immutable freeze and fresh20 XML. No new test identity, missing class or silently changed skip.','147 actual records:144 pass/3 original data-dependent skips,0 failures/errors/reporting markers. Declared XML counts match actual children; all timestamps within actual command window.','One actual :mtgish-tooling:test command (two repeated Gradle task headings, one worker), --rerun and --no-build-cache, zero exit, one complete Java21 Gradle worker start/finish pair, BUILD SUCCESSFUL; no required-task cached/up-to-date/no-source/skipped label.','Before/after input maps byte-identical with22743 valid path/hash entries; all8 dependency hashes agree. Independently checked emitted maps and source/control assertions, not all22743 source bodies.','Decoded complete job log records both exact checkouts via git log stdout and uploaded ZIP digest; command log and gzip decoded bytes independently hashed.','Read prospective root control review and earlier root general-CI review. Current supplement satisfies only that named cached147 gap at exact source08; no donor source substitution.','Read and hash-bound author report, README and final23:20 status. Historical original147 remain described as cached; current147 fresh; token20 stays a subset and disabled CardImageUri Gradle marker counts zero actual cases. Resource attempts remain consumed/stopped and no current calibration authority is granted.'],'results':{'classes':20,'cases':147,'passes':144,'skips':3,'failures':0,'errors':0,'input_bindings':22743,'unchanged_original_case_and_skip_multiset':True},'skip_identities':skips,'combined_general_ci':{'actual_case_identities':17709,'passes':17657,'declared_skips':52,'non_test_reporting_markers_excluded':1,'basis':'17513 fresh passes+49 skips from separately attributed root general-CI review, plus this independently verified144+3. This review does not repeat all seven original CI archives. Required token20 are already included.','distinct_execution_evidence':'Original cached147 XML remain cached historical records; this separate exact-source fresh147 supplement provides software freshness closure.'},'publication_disposition':'Bound original ZIP, audit/report/README and exact23:20 CURRENT_STATUS bytes are suitable for reviewed evidence publication with this attributed independent review. Root/Ferocity own publication; no mutation performed here.','limits':['No complete runtime, loaded binary equivalence, dedicated gym equivalence, offline card export, policy/protocol closure, resource calibration or capacity acceptance.','No gameplay permit, entropy, seed/claim allocation, game, outcome, browser retry or GitHub approval.','Other prior program/source/resource/counter claims in the status are bounded previously reviewed root/agent checkpoints; this is not a fresh independent gameplay or historical-resource audit.','This review confirms emitted input-map consistency and immutable checkout/control chain; it does not claim independently refetched22743 source bodies.'],'official_games':0,'allocations_consumed':0,'outcomes_exposed':0,'deck_performance_evidence_changed':False}
out=P/'manual-independent-artifact-review.json';out.write_text(json.dumps(review,indent=2)+'\n');print(json.dumps({'review':filebind(out),'results':review['results'],'combined':review['combined_general_ci']},indent=2))
