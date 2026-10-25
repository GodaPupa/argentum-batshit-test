"""Original-artifact receiver for one frozen early preflight; never feature acceptance."""
from pathlib import Path
from collections import Counter,defaultdict
import hashlib,json,re,zipfile
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parent
PINS=json.loads((ROOT/'PINS.json').read_text())
P=PINS['protocol']
sha=lambda b:hashlib.sha256(b).hexdigest()
blob=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()

def parse_reports(raw, stage):
    prefix = stage + '/junit/'
    names = Counter(); cases = defaultdict(list); modules = defaultdict(Counter)
    failures = []; skips = []
    for path, data in sorted(raw.items()):
        if not (path.startswith(prefix) and path.endswith('.xml')): continue
        assert '/build/test-results/test/TEST-' in path
        module = path.removeprefix(prefix).split('/build/test-results/test/')[0]
        suite = ET.fromstring(data); rows = suite.findall('.//testcase')
        assert int(suite.get('tests')) == len(rows)
        for attr, tag in [('failures', 'failure'), ('errors', 'error'), ('skipped', 'skipped')]:
            assert int(suite.get(attr)) == sum(r.find(tag) is not None for r in rows), (path, attr)
        modules[module]['suites'] += 1
        for row in rows:
            cls, name = row.get('classname'), row.get('name')
            assert cls and name
            identity = cls + '::' + name
            names[module + '\t' + cls + '\t' + name] += 1
            cases[cls.rsplit('.', 1)[-1]].append(identity)
            modules[module]['rows'] += 1
            tags = [tag for tag in ['failure', 'error', 'skipped'] if row.find(tag) is not None]
            assert len(tags) <= 1
            if tags == ['skipped']:
                modules[module]['skipped'] += 1; skips.append(identity)
            elif tags:
                tag = tags[0]; f = row.find(tag)
                modules[module]['failures' if tag == 'failure' else 'errors'] += 1
                failures.append(dict(identity=identity, type=f.get('type'), message=f.get('message'), stack=f.text or ''))
            else: modules[module]['passed'] += 1
    totals = Counter()
    for module in modules.values(): totals.update(module)
    for key in ['rows', 'suites', 'passed', 'failures', 'errors', 'skipped']: totals.setdefault(key, 0)
    return dict(names=names, cases={k: sorted(v) for k,v in cases.items()}, modules={k: dict(v) for k,v in modules.items()},
        totals=dict(totals), failures=failures, skips=sorted(skips), duplicate_labels={k:v for k,v in names.items() if v > 1})


def verify_reports(raw,stage):
    result=parse_reports(raw,stage);t=result['totals']
    assert not result['duplicate_labels'] and t['errors']==t['skipped']==0
    assert set(result['modules'])=={'rules-engine'}
    rc=int(raw[stage+'/exit.txt']);log=raw[stage+'/gradle.log'].decode()
    assert not re.search(r'^e: |^> Task .*:compile\S* FAILED$|Compilation error|Unresolved reference',log,re.M)
    cases=sorted(x for rows in result['cases'].values() for x in rows)
    if stage=='mutation':
        assert rc!=0 and cases==sorted(P['negative_first_frames'])
        assert t['rows']==t['failures']==2 and t['passed']==0
        assert {f['identity'] for f in result['failures']}==set(P['negative_first_frames'])
        assert re.findall(r'^> Task (\S+) FAILED$',log,re.M)==[':rules-engine:test']
        for f in result['failures']:
            assert f['type']=='org.opentest4j.AssertionFailedError'
            frame=re.search(r'FixedRecipientMutationWitnessTest\.kt:(\d+)',f['stack'])
            assert frame and int(frame.group(1))==P['negative_first_frames'][f['identity']]
    else:
        assert rc==0 and cases==P['positive_cases'] and t['passed']==42 and t['failures']==0
        assert re.search(r'^> Task :rules-engine:test$',log,re.M)
    summary=json.loads(raw[stage+'/SUMMARY.json'])
    assert summary['cases']==cases and summary['errors']==summary['skips']==[] and summary['exit']==rc
    assert [(f['identity'],f['type'],f['stack']) for f in summary['failures']]==[(f['identity'],f['type'],f['stack']) for f in result['failures']]
    return result

def mutated(body):
    text=body.decode()
    for anchor,replacement in P['mutation_replacements']:
        assert text.count(anchor)==1
        text=text.replace(anchor,replacement,1)
    return text.encode()

def audit():
    m=json.loads((ROOT/'metadata.json').read_text());run=m['run'];job=m['jobs']['jobs'][0];artifact=m['artifact']
    assert len(m['jobs']['jobs'])==1
    assert run['id']==PINS['run'] and run['head_sha']==PINS['validation'] and run['workflow_id']==PINS['workflow_id']
    assert run['run_attempt']==run['run_number']==1 and run['event']==PINS['event'] and run['head_branch']==PINS['branch']
    assert run['path']==PINS['workflow'] and run['status']=='completed'
    assert job['id']==PINS['job'] and job['run_id']==PINS['run'] and job['run_attempt']==1 and job['status']=='completed'
    assert artifact['name']==PINS['artifact_name'] and not artifact['expired']
    assert artifact['workflow_run']['id']==PINS['run'] and artifact['workflow_run']['head_sha']==PINS['validation']
    for key,sha_key,tree_key,parent in [('source','source','tree',PINS['base']),('validation','validation','validation_tree',PINS['source'])]:
        commit=m[key];assert commit['sha']==PINS[sha_key] and commit['tree']['sha']==PINS[tree_key]
        assert [p['sha'] for p in commit['parents']]==[parent]
    assert m['main']['sha']==PINS['main']
    assert m['source_delta']['status']=='ahead' and {f['filename'] for f in m['source_delta']['files']}==set(PINS['source_files'])
    assert all(f['status']==('modified' if f['filename'] in P['modified_paths'] else 'added') for f in m['source_delta']['files'])
    assert [f['filename'] for f in m['validation_delta']['files']]==[PINS['workflow']]
    archive=(ROOT/'original.zip').read_bytes()
    assert len(archive)==artifact['size_in_bytes'] and 'sha256:'+sha(archive)==artifact['digest']
    with zipfile.ZipFile(ROOT/'original.zip') as z:
        assert z.testzip() is None
        names=[n for n in z.namelist() if not n.endswith('/')]
        assert len(names)==len(set(names))
        assert all(not n.startswith('/') and '..' not in Path(n).parts for n in names)
        raw={n:z.read(n) for n in names}
    manifest=json.loads(raw['MANIFEST.json'])
    assert set(manifest)==set(raw)-{'MANIFEST.json'}
    assert all(sha(raw[path])==digest for path,digest in manifest.items())
    binding=json.loads(raw['BINDING.json'])
    for key in ['source','tree','validation','base','workflow','workflow_blob']:assert binding[key]==PINS[key]
    assert binding['run']==str(PINS['run']) and binding['attempt']==binding['run_number']==1
    assert blob(raw['WORKFLOW.yml'])==PINS['workflow_blob']
    assert json.loads(raw['PROTOCOL.json'])==P
    for path,digest in PINS['source_files'].items():
        member=Path(path).name if '/research/' in path else 'source/'+path
        assert sha(raw[member])==digest,path
    assert json.loads(raw['PROTECTED.json'])==PINS['protected_archive_hashes']
    for path,digest in PINS['protected_archive_hashes'].items():assert sha(raw['source/'+path])==digest,path
    evidence=dict(source=PINS['source'],tree=PINS['tree'],validation=PINS['validation'],run=PINS['run'],job=PINS['job'],attempt=1,
        artifact=artifact['id'],artifact_bytes=len(archive),artifact_sha256=sha(archive),manifest_entries=len(manifest),gates={},
        feature_acceptance=False,official_games=0,official_seeds=0,precision_reserved=2)
    errors=[]
    def gate(label,fn):
        try:evidence['gates'][label]=fn()
        except Exception as e:errors.append(dict(gate=label,error=type(e).__name__+': '+str(e)))
    def compilation(stage):
        log=raw[stage+'/gradle.log'].decode();assert int(raw[stage+'/exit.txt'])==0
        assert 'BUILD SUCCESSFUL' in log and not re.search(r'^e: ',log,re.M)
        assert re.search(r'^> Task :rules-engine:compileKotlin$',log,re.M)
        assert re.search(r'^> Task :rules-engine:compileTestKotlin(?: UP-TO-DATE)?$',log,re.M)
        assert not re.search(r'^> Task :rules-engine:test(?: |$)',log,re.M)
        assert not any(n.startswith(stage+'/junit/') for n in raw)
        assert json.loads(raw[stage+'/RECEIPT.json'])==dict(stage=stage,compile=True,tests=0)
        return {'compile':True,'tests':0}
    for stage in ['compile-normal','compile-mutant']:
        gate(stage,lambda stage=stage:compilation(stage))
    for stage in ['targeted','mutation','restored']:
        gate(stage,lambda stage=stage:verify_reports(raw,stage))
    def forms():
        for stage in P['required_stages']:
            for path in P['implementation_sha256']:
                expected=raw['source/'+path]
                if stage in ['compile-mutant','mutation'] and path==P['mutation_path']:expected=mutated(expected)
                assert raw[stage+'/source/'+path]==expected,(stage,path)
            if stage in ['compile-normal','targeted','restored']:assert raw[stage+'/SOURCE.diff']==b''
        return 'All normal, mutant and restored bytes bound'
    gate('forms',forms)
    evidence['gate_errors']=errors
    evidence['state']='RECEIVED_EARLY_GATES_ONLY_NOT_FEATURE_ACCEPTANCE' if not errors and run['conclusion']==job['conclusion']=='success' else 'ORIGINAL_FAILURE_RECEIVED_NOT_ACCEPTED'
    if evidence['state'].startswith('ORIGINAL_FAILURE'):
        evidence['raw_available_reports']={stage:parse_reports(raw,stage) for stage in ['targeted','mutation','restored']}
        evidence['original_failure_steps']=[s for s in job['steps'] if s['conclusion']=='failure']
    (ROOT/'VERIFIED_RECEIPT.json').write_text(json.dumps(evidence,indent=2)+'\n')
    print(json.dumps({k:evidence[k] for k in ['state','run','artifact','manifest_entries','gate_errors']}))

if __name__=='__main__':audit()
