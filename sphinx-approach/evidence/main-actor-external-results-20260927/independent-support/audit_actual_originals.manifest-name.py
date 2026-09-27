"""Independent read-only audit of already downloaded original Sphinx actor artifacts."""
from pathlib import Path
import collections, datetime as dt, gzip, hashlib, json, re, zipfile
import xml.etree.ElementTree as ET

BASE=Path('/workspace/scratch/94f5141e9448')
HERE=Path(__file__).resolve().parent
ACT=BASE/'ferocity-takeover/sphinx-main-binding/actual-result'
PREP=BASE/'ferocity-takeover/sphinx-main-binding/publication/source-review-evidence.zip'
H=lambda b:hashlib.sha256(b).hexdigest()
G=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
def info(b):return {'bytes':len(b),'sha256':H(b)}
def stamp(s):return dt.datetime.fromisoformat(s.replace('Z','+00:00')).replace(tzinfo=dt.timezone.utc).timestamp()
meta=json.loads((HERE/'current-actual-github-metadata.json').read_bytes())
run,jobs,rc,cc=meta['run_and_jobs_and_commits']
RUNTIME='22cf67791fda69a65d7e1eeb76c2ae369407cb01'
CONTROL='0377a9639061b75bf04f01e466dee247963066b8'
RTREE='3b4e50dd7a0fe484b796bb191bd848b048bea12b'
CTREE='062e5624c74795d38bd937c299d3750b764a729f'
PARENTS=['ff34ac8fa2efbd41f233e877cb2908c2cab5929a','8ed9787ad75b8bc6c2438a8c0b526ff822aa776e']
commits={c['sha']:c for c in [rc,cc]}
assert run['id']==36290490623 and run['run_attempt']==1 and run['event']=='push' and run['head_sha']==CONTROL
assert run['status']=='completed' and run['conclusion']=='success'
for head,tree,parents in [(RUNTIME,RTREE,PARENTS),(CONTROL,CTREE,[PARENTS[1]])]:
    assert commits[head]['tree']['sha']==tree
    assert [x['sha'] for x in commits[head]['parents']]==parents
tree_bytes=gzip.decompress((ACT/'immutable-runtime-full-tree.json.gz').read_bytes())
tree=json.loads(tree_bytes);assert tree['sha']==RTREE and tree['truncated'] is False
blobs={x['path']:x['sha'] for x in tree['tree'] if x['type']=='blob'}
prep=zipfile.ZipFile(PREP);assert prep.testzip() is None
root=json.loads(prep.read('root-source-byte-proof.json'))
bodies={x['path']:x for x in root['source_bodies_rehashed']};assert len(bodies)==60
for path,row in bodies.items():
    b=prep.read('runtime-source/'+path)
    assert info(b)=={k:row[k] for k in ['bytes','sha256']}
    assert G(b)==row['git_blob_sha1']==blobs[path]
controls={n.removeprefix('candidate/'):prep.read(n) for n in prep.namelist() if n.startswith('candidate/') and not n.endswith('/')}
assert len(controls)==5
wrappers=json.loads((HERE/'independent-additional-wrapper-source.json').read_bytes())
for path,row in wrappers.items():
    assert row['encoding']=='utf-8'
    assert G(row['content'].encode())==row['sha']==blobs[path]
artifact_meta={a['id']:a for a in meta['artifacts']['artifacts']}
jobs={j['id']:j for j in jobs['jobs']}
specs=[
 ('shared-50','shared_actor_50',10922142148,108539489794,50,4,419121,'11617efc668f25b4d443110d1679f2f9ad4c12e4c058ea19d40c89ebc8fd4d28',BASE/'attachments/97647720-452c-4ff7-9be2-61f6f22b5bcc/sphinx-pr205-10919342186.zip'),
 ('sphinx-136','sphinx_actor_136',10922675113,108539489879,136,7,430441,'759fbcb77ef8dccc0d78cc954f28931e4138636cbd8e6024945ed6d1becb96c5',BASE/'attachments/0a1aa472-2af9-4d03-918a-6daa685e3827/sphinx-pr205-10919885062.zip')]
report={'schema':'izzet-independent-sphinx-original-actor-audit/v1','reviewer':'/root/izzet','runtime':RUNTIME,'runtime_tree':RTREE,'control':CONTROL,'control_tree':CTREE,'run_id':run['id'],'attempt':1,'originals':{},'source_limits':'All 75 actual recorded Git blobs compared with full immutable runtime tree; 60 independently rehashed retained source bodies. Fifteen remaining paths have metadata/blob binding, not a new independent body retrieval.','metadata_sha256':H((HERE/'current-actual-github-metadata.json').read_bytes()),'full_tree_json':info(tree_bytes)}
identity_sets=[]
for tag,group,aid,jid,total,nclass,size,digest,oldpath in specs:
    raw=(ACT/(tag+'-original.zip')).read_bytes();assert len(raw)==size and H(raw)==digest
    am=artifact_meta[aid];assert am['size_in_bytes']==size and am['digest']=='sha256:'+digest and not am['expired']
    j=jobs[jid];assert j['conclusion']=='success' and j['run_attempt']==1 and j['head_sha']==CONTROL
    z=zipfile.ZipFile(ACT/(tag+'-original.zip'));assert z.testzip() is None
    assert len(z.namelist())==len(set(z.namelist()))
    members={n:info(z.read(n)) for n in z.namelist()}
    cmd=json.loads(z.read('test-command.json'));assert cmd['exit_status']==0 and 'timeout' not in cmd
    assert z.read('test-exit-code.txt').strip()==b'0' and z.read('receiving-reports/test-exit-code.txt').strip()==b'0'
    assert cmd['cwd']=='runtime' and cmd['command'][:2]==['just','test-class'] and '--rerun' in cmd['command'] and '--max-workers=1' in cmd['command']
    start,end=cmd['started_ns']/1e9,cmd['finished_ns']/1e9
    assert stamp(j['started_at'])<=start<end<=stamp(j['completed_at'])
    log=z.read('test-command.log').decode()
    markers={k:len(re.findall(p,log,re.M)) for k,p in {
      'start_process':r"^Starting process 'Gradle Test Executor \d+'\.",
      'started_process':r"^Successfully started process 'Gradle Test Executor \d+'$",
      'worker_started':r'^Gradle Test Executor \d+ started executing tests\.$',
      'worker_finished':r'^Gradle Test Executor \d+ finished executing tests\.$',
      'fresh_test_headers':r'^> Task :gym:test$',
      'nonfresh_test_headers':r'^> Task :gym:test (?:FROM-CACHE|UP-TO-DATE|NO-SOURCE|SKIPPED)$',
      'build_success':r'^BUILD SUCCESSFUL in ',
    }.items()}
    assert all(markers[k]==1 for k in ['start_process','started_process','worker_started','worker_finished','build_success'])
    assert markers['fresh_test_headers']>=1 and markers['nonfresh_test_headers']==0
    assert '/temurin-21-jdk-amd64/bin/java' in log and "Task ':gym:test' is not up-to-date because:" in log
    assert '48 actionable tasks: 42 executed, 6 from cache' in log
    suites=[];ids=[]
    for n in z.namelist():
      if n.startswith('original-test-xml/TEST-') and n.endswith('.xml'):
        r=ET.fromstring(z.read(n));ts=list(r.findall('testcase'))
        assert int(r.attrib['tests'])==len(ts)
        assert all(int(r.attrib.get(k,0))==0 for k in ['failures','errors','skipped'])
        assert start<=stamp(r.attrib['timestamp'])<=end
        for t in ts:
          assert len(list(t))==0 and t.attrib['classname']==r.attrib['name']
          ids.append((t.attrib['classname'],t.attrib['name']))
        suites.append({'member':n,'attributes':r.attrib,'cases':[t.attrib for t in ts]})
    assert len(suites)==nclass and len(ids)==len(set(ids))==total
    old=zipfile.ZipFile(oldpath);assert old.testzip() is None
    oldids=[(t.attrib['classname'],t.attrib['name']) for n in old.namelist() if n.endswith('.xml') and '/test-results/test/TEST-' in n for t in ET.fromstring(old.read(n)).findall('testcase')]
    assert collections.Counter(oldids)==collections.Counter(ids)
    identity_sets.append(set(ids))
    selected=[cmd['command'][2]]+[cmd['command'][i+1] for i,x in enumerate(cmd['command']) if x=='--tests']
    assert {x.lstrip('*') for x in selected}=={c.rsplit('.',1)[-1] for c,_ in ids}
    pre=json.loads(z.read('pre-bind-attempt.json'));assert pre['errors']==[] and pre['github_sha']==CONTROL and pre['event_name']=='push' and pre['group']==group
    steps=json.loads(z.read('step-outcomes.json'));assert all(steps[k]=='success' for k in ['preserve','bind','tests','collect'])
    observations=[];bindings=[]
    for number in [1,2,3]:
      base=f'receiving-reports/external-main-binding-attempts/attempt-{number:03d}/'
      a=json.loads(z.read(base+'attempt.json'))
      assert a['group']==group and a['status']=='BOUND_FOR_ORIGINAL_SOFTWARE_BANK_ONLY' and a['run_id']=='36290490623' and a['run_attempt']=='1'
      assert a['official_games']==0 and a['gameplay_authorized'] is False
      obs=a['observed_source'];assert obs['scope_read_errors']==[] and len(obs['runtime_files'])==75
      for role,head,tr,parents in [('runtime',RUNTIME,RTREE,PARENTS),('control',CONTROL,CTREE,[PARENTS[1]])]:
        assert obs['identities'][role]=={'head':head,'tree':tr,'parents':' '.join(parents),'status':''}
      for path,row in obs['runtime_files'].items():
        assert row['git_blob_sha1']==blobs[path]
        if path in bodies:assert row=={k:bodies[path][k] for k in ['bytes','sha256','git_blob_sha1']}
      for path,b in controls.items():
        assert z.read('control/'+path)==b==z.read(base+'control/'+path)
        assert obs['control_files'][path]==dict(info(b),git_blob_sha1=G(b))
      assert len(a['preserved_files'])==11
      for rel,sha in a['preserved_files'].items():
        b=z.read(base+rel);assert H(b)==sha
        if rel.startswith('runtime-authority/'):
          path=rel.removeprefix('runtime-authority/');assert b==prep.read('runtime-source/'+path)
      bind=a['binding'];assert bind['context']=='sphinx-main-actor-external-22cf-v1' and bind['group']==group
      assert bind['activation_event']=={'event_name':'push','ref':'refs/heads/lab/sphinx-main-actor-external-20260927','before':'0'*40,'after':CONTROL,'created':True,'run_id':'36290490623','run_attempt':1}
      assert bind['official_games']==0 and not bind['gameplay_authorized']
      assert len(bind['runtime']['complete_main_only_files'])==14
      for f in bind['runtime']['complete_main_only_files']:assert f['git_blob_sha1']==blobs[f['path']]
      observations.append(obs);bindings.append(bind)
    assert observations[0]==observations[1]==observations[2]
    assert bindings[0]==bindings[1]==bindings[2]
    if tag=='shared-50':
      m=json.loads(z.read('receiving-reports/manifest.json'));assert m['actual_cases']==50 and all(m[k]==0 for k in ['failures','errors','skipped'])
      sb=json.loads(z.read('receiving-reports/source-binding.json'));assert sb['prospective_receiving_binding']==bindings[0] and sb['checkout_status']==''
      for p,sha in m['files_sha256'].items():
        if p in observations[0]['runtime_files']:assert observations[0]['runtime_files'][p]['sha256']==sha
        else:assert H(wrappers[p]['content'].encode())==sha
      assert sorted((c.rsplit('.',1)[-1],n) for c,n in ids)==sorted((c,n) for c,names in m['banks'].items() for n in names)
    else:
      before=json.loads(z.read('receiving-reports/source-before.json'));after=json.loads(z.read('receiving-reports/source-after.json'));assert before==after and before['prospective_receiving_binding']==bindings[0]
      audit=json.loads(z.read('receiving-reports/audit.json'));assert audit['actual_distinct_cases']==136 and audit['errors']==[] and int(audit['test_exit_code'])==0
    compressed=(ACT/(tag+'-decoded-job.log.gz')).read_bytes();decoded=gzip.decompress(compressed)
    assert decoded==(ACT/(tag+'-decoded-job.log')).read_bytes()
    decoded_text=decoded.decode();assert 'Cleaning up orphan processes' in decoded_text and 'run-bank' in decoded_text
    report['originals'][tag]={'artifact_id':aid,'job_id':jid,'original':info(raw),'members':members,'commands':cmd,'suites':suites,'freshness_markers':markers,'dependency_tasks':'48 actionable tasks: 42 executed, 6 from cache; required gym:test fresh','prior_original':dict(path=str(oldpath),**info(oldpath.read_bytes())),'exact_old_case_multiset_equal':True,'binding_observations':3,'observed_source':observations[0],'binding':bindings[0],'decoded_job_log':info(decoded),'decoded_job_log_gzip':info(compressed),'step_outcomes':steps}
assert identity_sets[0] < identity_sets[1]
report['totals']={'case_executions':186,'distinct_case_identities':136,'shared_subset':50,'failures':0,'errors':0,'skipped':0,'fresh_test_workers':2,'bank_executions':2,'binding_observations':6,'official_games':0}
outer=zipfile.ZipFile(ACT/'original-results-evidence.zip');assert outer.testzip() is None
manifest=json.loads(outer.read('archive-member-manifest.json'))
report['outer_packet']={'zip':info((ACT/'original-results-evidence.zip').read_bytes()),'members':{n:info(outer.read(n)) for n in outer.namelist()}}
for n in outer.namelist():
    if n!='archive-member-manifest.json':assert outer.read(n)==(ACT/n).read_bytes()
report['README']=info((ACT/'README.md').read_bytes())
report['status']='PASS_BOUNDED_ORIGINAL_SOFTWARE_BANKS_ONLY'
(HERE/'independent-actual-raw-checks.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({'status':report['status'],'totals':report['totals'],'report_sha256':H((HERE/'independent-actual-raw-checks.json').read_bytes())}))
