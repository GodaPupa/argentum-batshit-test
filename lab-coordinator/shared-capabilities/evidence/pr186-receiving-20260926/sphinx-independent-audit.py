"""Read-only independent audit of three already-completed PR186 artifacts."""
import collections, datetime, hashlib, json, pathlib, re, zipfile
import xml.etree.ElementTree as ET

ROOT = pathlib.Path('/workspace/scratch/94f5141e9448')
D = ROOT / 'shared-audit/pr186-audit'
sha = lambda b: hashlib.sha256(b).hexdigest()
blob = lambda b: hashlib.sha1(b'blob ' + str(len(b)).encode() + b'\0' + b).hexdigest()
live = json.loads((D/'sphinx-live-review-inputs.json').read_text())
extra = json.loads((D/'sphinx-live-review-extra.json').read_text()) + json.loads((D/'sphinx-live-review-authorities.json').read_text())
controls = {r['path']: r['result'] for r in live['controls'] + extra if r.get('path') and 'error' not in r['result']}
meta = [json.loads(r['result']['content']) for r in live['metadata']]
head = 'b6fc446543d3ce566bb12c60d1ab6b84e1c96d8c'
tree = '3d6a178f3b8168444d7f09b7dd212d3e4d567036'
assert meta[0]['sha'] == head and meta[0]['tree']['sha'] == tree
roots = [{r['path']:r['sha'] for r in j['tree']} for j in meta[1:3]]
assert set(roots[0]) == set(roots[1])
assert [k for k in roots[0] if roots[0][k] != roots[1][k]] == ['lab-coordinator']
oldtree = json.loads((ROOT/'sphinx-audit/shared-review-live-tree.json').read_text())
assert oldtree['sha'] == '994383d4a9495bcb34d5aae0e69181bcf4c04d45' and not oldtree['truncated']
old = {e['path']:e for e in oldtree['tree']}
assert {p for p,s in roots[0].items() if old[p]['sha'] != s} == {'.github','lab-coordinator'}
runs = {j['id']:j for j in meta[3:]}
jobs = {r['run']: json.loads(r['result']['content'])['jobs'][0] for r in extra if r.get('run')}
for run,j in runs.items():
    assert j['head_sha'] == head and j['run_attempt'] == 1 and j['conclusion'] == 'success'
    assert jobs[run]['conclusion'] == 'success'
assert runs[36277026049]['event'] == 'push'
assert runs[36277029835]['event'] == runs[36277029881]['event'] == 'pull_request'
for p,c in controls.items():
    assert blob(c['content'].encode()) == c['sha'], p

pin_rows = {}
def bytes_at(p):
    if p in controls:
        return controls[p]['content'].encode()
    b = (ROOT/'shared-audit/source'/p).read_bytes()
    assert blob(b) == old[p]['sha'], p
    # Root subtree identity binds source bytes to b6fc, not only old994.
    assert roots[0][p.split('/')[0]] == old[p.split('/')[0]]['sha'], p
    return b
def verify_pins(m):
    for p,h in m.items():
        b = bytes_at(p)
        assert sha(b) == h, p
        pin_rows[p] = {'sha256':h,'git_blob':blob(b)}

def iso(t):
    return datetime.datetime.fromisoformat(t.replace('Z','+00:00')).replace(tzinfo=datetime.timezone.utc)
def check_xml(raw, cls, names, step):
    x=ET.fromstring(raw);cs=x.findall('testcase')
    assert x.attrib['name']==cls and int(x.attrib['tests'])==len(cs)==len(names)
    assert all(int(x.attrib.get(k,0))==0 for k in ('failures','errors','skipped'))
    assert not any(x.findall('.//'+k) for k in ('failure','error','skipped'))
    assert collections.Counter(c.attrib['name'] for c in cs)==collections.Counter(names)
    assert all(c.attrib['classname']==cls for c in cs)
    assert len(set(names))==len(names)
    assert step['conclusion']=='success'
    stamp=iso(x.attrib['timestamp'])
    assert iso(step['started_at']) <= stamp <= iso(step['completed_at']), (cls,x.attrib['timestamp'],step)
    return {'class':cls,'cases':len(cs),'xml_sha256':sha(raw),'timestamp':x.attrib['timestamp']}
def check_log(raw, task, first):
    s=raw.decode();ls=s.splitlines()
    assert ls[0]==first, (ls[0],first)
    lines=[l for l in ls if re.match('^> Task '+re.escape(task)+r'(?:\s|$)',l)]
    assert lines and set(lines)=={'> Task '+task},lines
    started=re.findall(r'^Gradle Test Executor (\d+) started executing tests\.$',s,re.M)
    finished=re.findall(r'^Gradle Test Executor (\d+) finished executing tests\.$',s,re.M)
    assert started==finished and len(started)==1,(started,finished)
    assert 'BUILD SUCCESSFUL' in s and 'Using 1 worker leases.' in s
    return {'sha256':sha(raw),'command':first,'fresh_task':task,'fresh_worker':started[0],
            'shlock_unavailable_route_fallback_observed':"'shlock' not found; running ./gradlew unlocked" in s}

oldflows={x['path']:x['content'] for x in json.loads((ROOT/'pest-takeover/shared-review/workflows-994383.json').read_text())}
# The token/combat controls and exact commands are unchanged from accepted shared994.
for p in ['.github/workflows/shared-current-combat-qualification.yml','.github/workflows/attacking-token-defender-qualification.yml']:
    assert controls[p]['content']==oldflows[p]
postwf=controls['.github/workflows/shared-postblock-source-qualification.yml']['content']
oldpost=oldflows['.github/workflows/shared-postblock-source-qualification.yml']
for start,end in [('      - name: Execute the 115 exact engine cases','      - name: Require actual case completeness')]:
    assert postwf[postwf.index(start):postwf.index(end)] == oldpost[oldpost.index(start):oldpost.index(end)]

previous = zipfile.ZipFile(ROOT/'attachments/1497c0e2-c3ab-4d6e-971f-9d6db541d101/sphinx-receiving-999-original-10916917146.zip')
previous_names={}
for n in previous.namelist():
    if n.endswith('.xml'):
        x=ET.fromstring(previous.read(n));previous_names[x.attrib['name']]=[c.attrib['name'] for c in x.findall('testcase')]

components=[]
expected_archives=[(10916579358,36277029835,239100,'0f6845650440e604c88e7929061fe865258100359fb16a4d8d2cb717c11206e2','combat',32),
 (10917414511,36277029881,262495,'e838e107a96130bbd22bb0c9f756da5a2f0373c6d6e74614370a23ee420ae5d5','token',20),
 (10917771412,36277026049,291915,'a227398dd2179725dced6318e340a23b0adca9a6efb24ebd128eb7c378c79c09','postblock-push',127)]
for aid,run,size,digest,label,total in expected_archives:
    data=(D/f'original-artifact-{aid}.zip').read_bytes()
    assert len(data)==size and sha(data)==digest
    with zipfile.ZipFile(D/f'original-artifact-{aid}.zip') as z:
        assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))
        rows=[];logs=[]
        if label != 'postblock-push':
            p=json.loads(z.read('source-provenance.json'))
            assert p['candidate_head']==head and p['tree']==tree and p['official_games']==0 and p['gameplay_authorized'] is False
            mp='lab-coordinator/shared-capabilities/'+('current-combat-receiving.json' if label=='combat' else 'attacking-token-defender-receiving.json')
            m=json.loads(bytes_at(mp));assert sha(bytes_at(mp))==p['manifest_sha256']
            verify_pins(p['source_sha256'])
            names={x['class']:x['case_names'] for x in m['banks']}
            audit=json.loads(z.read('actual-case-audit.json')); assert audit['actual_cases']==total and audit['errors']==[]
            step=next(s for s in jobs[run]['steps'] if s['name'].startswith('Execute '))
            for stage in m['stages']:
                cls=stage['class'];stage_name=stage['stage']
                assert collections.Counter(names[cls]) == collections.Counter(previous_names[cls])
                raw=z.read('tests/'+stage_name+'/TEST-'+cls+'.xml')
                rows.append(check_xml(raw,cls,names[cls],step))
                assert int(z.read('tests/'+stage_name+'/exit-status.txt'))==0
                if label=='token':
                    assert int(z.read(stage_name+'-exit-status.txt'))==0
                    cmd='scripts/test-class "'+cls.split('.')[-1]+'" --rerun --info --stacktrace --max-workers=1 -PkotlinCompileParallelism=1 -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx4g'
                    logs.append(check_log(z.read(stage_name+'.log'),':'+stage['module'].replace('/',':')+':test',cmd))
            if label=='combat':
                assert int(z.read('command-exit-status.txt'))==0
                cmd='scripts/test-class "CombatAssignmentCurrentRulesTest" --tests *CombatResolutionBoardTest --tests *CombatDamageAssignmentTest --tests *BandingTrampleDrainScenarioTest --rerun --info --stacktrace --max-workers=1 -PkotlinCompileParallelism=1 -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx4g'
                logs.append(check_log(z.read('qualification.log'),':rules-engine:test',cmd))
        else:
            prefix='build/reports/shared-postblock-source/'
            p=json.loads(z.read(prefix+'source-binding.json'));a=json.loads(z.read(prefix+'binding-attempt.json'));m=json.loads(z.read(prefix+'manifest.json'))
            assert p['actual_head']==p['requested_head']==a['actual_head']==a['requested_head']==head
            assert p['tree']==a['tree']==m['source_tree']==tree and p['checkout_status']==a['checkout_status']==''
            assert m['source_head']==head and m['run_id']==str(run) and m['run_attempt']=='1'
            assert not m['full_integration_accepted'] and not m['production_actor_accepted'] and not m['gameplay_authorized']
            assert m['official_allocations_initialized']==m['official_outcomes_exposed']==0
            verify_pins(p['source_files_sha256']);verify_pins(p['combined_source_files_sha256']);verify_pins(p['receiving_control_files_sha256'])
            assert len(p['source_files_sha256'])==54 and len(p['combined_source_files_sha256'])==250
            for name,key in [('postblock-source-extraction.json','extraction_manifest_sha256'),('postblock-fixture-receiving.json','fixture_receiving_manifest_sha256')]:
                assert sha(bytes_at('lab-coordinator/shared-capabilities/'+name))==p[key]==m[key]
            orig=json.loads(bytes_at('lab-coordinator/shared-capabilities/postblock-source-extraction.json'))
            expected={s['class_name']:s for s in orig['test_files']}
            for n in z.namelist():
                if not n.endswith('.xml'): continue
                raw=z.read(n);x=ET.fromstring(raw);cls=x.attrib['name'];e=expected[cls]
                olds=list((ROOT/'shared-audit/raw/observer').rglob('TEST-'+cls+'.xml'));assert len(olds)==1
                names=[c.attrib['name'] for c in ET.parse(olds[0]).getroot().findall('testcase')]
                assert len(names)==e['expected_cases']
                step=next(s for s in jobs[run]['steps'] if s['name']=='Execute the '+('115 exact engine' if e['scope']=='engine' else '12 exact server')+' cases')
                rows.append(check_xml(raw,cls,names,step))
                assert sha(raw)==m['suites'][cls]['xml_sha256']
                assert collections.Counter(names)==collections.Counter(m['suites'][cls]['cases'])
            assert {r['class'] for r in rows}==set(expected)==set(m['suites'])
            for scope,module in [('engine','rules-engine'),('server','game-server')]:
                names=[e['class_name'].split('.')[-1] for e in orig['test_files'] if e['scope']==scope]
                cmd='scripts/test-class "'+names[0]+'"'+''.join(' --tests *'+name for name in names[1:])+' --rerun --max-workers=1 -PkotlinCompileParallelism=1 -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx4g --info --stacktrace'
                logs.append(check_log(z.read(prefix+scope+'.txt'),':'+module+':test',cmd))
        assert len(rows)==sum(n.endswith('.xml') for n in z.namelist())
        assert sum(r['cases'] for r in rows)==total
        components.append({'component':label,'run_id':run,'event':runs[run]['event'],'attempt':1,'job_id':jobs[run]['id'],'artifact_id':aid,
            'original_sha256':digest,'original_bytes':size,'members':len(z.namelist()),'crc':'PASS','raw_cases':total,'failures':0,'errors':0,'skips':0,
            'suites':rows,'logs':logs,'exit_evidence':'Retained command and per-bank exit-status files all zero' if label!='postblock-push' else 'No standalone exit-status file; unchanged set -euo pipefail / subprocess check=True commands, successful individual GitHub steps, BUILD SUCCESSFUL, and subsequent verified result manifest establish successful exit.'})

report={'schema':'pr186-three-component-independent-artifact-review-v1','reviewer':'/root/sphinx','disposition':'PASS_BOUNDED_COMPONENT_ARTIFACTS_ONLY',
 'reviewer_is_author_of_reviewed_shared_components_or_receiving_gate':False,
 'authorship_disclosure':'Authored later c38fd802 actor/mulligan constructor correction and Sphinx composition/control outside b6fc; no authored code or gate is the subject of this review.',
 'source_head':head,'source_tree':tree,'pr_test_merge':'fc5c443fc9d674d85dc1942a553319fee8b69d87','pr_test_merge_tree':'88967f3a9ba0479661f02150f82ddcc9a2066e42',
 'root_entries_equal_between_head_and_pr_merge':43,'root_difference':['lab-coordinator'],
 'source_binding_method':'Fresh immutable GitHub head/root trees and control bodies; local source SHA256 and Git blob SHA1 matched previously independently fetched complete shared994 tree, with identical production/test/build root subtree hashes at b6fc. Changed control-root files independently refetched at b6fc.',
 'unique_source_or_control_pins_verified':len(pin_rows),'source_pins_sha256':sha(json.dumps(pin_rows,sort_keys=True,separators=(',',':')).encode()),
 'case_identity_method':'Combat and token original names independently matched accepted original999 XML; postblock names independently matched accepted original994 observer XML. Exact command blocks retained from shared994.',
 'components':components,'total_testcase_records':179,
 'limitations':['Push postblock run36277026049 only; separate PR postblock run36277029821 is not covered.',
 'Only bounded original32 combat, original16 token plus separately admitted4 provenance, and original127 postblock case scopes. No broader reachable-behavior qualification inferred.',
 'Before-source maps are retained; final successful collectors rehash unchanged source and clean HEAD. Archives do not contain independent after maps.',
 'All six fresh Gradle invocations used the prescribed just/test-class/gradle-locked route but explicitly reported shlock absent and its unlocked fallback on isolated GitHub runners; no working interprocess semaphore is claimed.',
 'Postblock has no standalone numeric exit files; successful exits are established by unchanged fail-fast command structure, GitHub step results and original logs.',
 'Full CI, priority bank, project runtime/pilots/replay/durability, independent gameplay admission, repository approval and merge remain separate.'],
 'official_games_initialized':0,'allocations_consumed':0,'outcomes_exposed':0,'gameplay_authorized':False,'github_approval':False,
 'inputs':{n:sha((D/n).read_bytes()) for n in ['sphinx-live-review-inputs.json','sphinx-live-review-extra.json','sphinx-live-review-authorities.json','token-combat-artifact-audit.json','postblock-push-author-artifact-audit.json']}}
out=D/'sphinx-independent-three-component-review.json';out.write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({'path':str(out),'sha256':sha(out.read_bytes()),'pins':len(pin_rows),'cases':179,'components':len(components)}))
