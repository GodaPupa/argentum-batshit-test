"""Receive full regressions on exactly the previously received early source."""
from pathlib import Path
from collections import Counter, defaultdict
import hashlib, json, os, re, subprocess, tempfile, zipfile
import xml.etree.ElementTree as ET

ROOT = Path(os.environ.get('MANUAL_RECEIPT_ROOT', Path(__file__).resolve().parent))
PINS = json.loads((Path(__file__).resolve().parent / 'PINS.json').read_text())
P = PINS['protocol']
sha = lambda b: hashlib.sha256(b).hexdigest()
blob = lambda b: hashlib.sha1(b'blob ' + str(len(b)).encode() + b'\0' + b).hexdigest()


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


def verify_stage(raw, stage, protocol, run):
    result = parse_reports(raw, stage)
    totals = result['totals']; cases = result['cases']; failures = result['failures']
    summary = json.loads(raw[stage + '/SUMMARY.json'])
    for key in ['totals', 'cases', 'modules', 'failures', 'skips', 'duplicate_labels']:
        assert summary[key] == result[key], (stage, key)
    assert summary['source'] == PINS['source'] and summary['tree'] == PINS['tree']
    assert summary['validation'] == PINS['validation'] and summary['run'] == str(run)
    assert summary['official_games'] == summary['official_seeds'] == 0
    rc = int(raw[stage + '/exit.txt']); log = raw[stage + '/gradle.log'].decode()
    negative = stage.startswith('mutation-')
    assert totals['errors'] == 0
    assert not re.search(r'^e: |^> Task .*:compile\S* FAILED$|Compilation error|Unresolved reference', log, re.M)
    if negative:
        variant = stage.removeprefix('mutation-')
        frames = protocol['mutation_first_frames'][variant]
        assert rc != 0 and totals['rows'] == 35 and totals['failures'] == len(frames)
        assert totals['passed'] == 35-len(frames) and totals['skipped'] == 0
        assert set(result['modules']) == {'rules-engine'} and not result['duplicate_labels']
        assert sorted(x for ids in cases.values() for x in ids) == protocol['mutation_cases']
        assert {f['identity'] for f in failures} == frames.keys()
        for f in failures:
            assert f['type'] == 'org.opentest4j.AssertionFailedError'
            cls = f['identity'].split('::')[0].rsplit('.', 1)[-1]
            first = re.search(re.escape(cls) + r'\.kt:(\d+)', f['stack'])
            assert first and int(first[1]) == frames[f['identity']], f['identity']
            assert ('expected:<true> but was:<false>' in f['stack'] or
                    'expected: true but was: false' in f['stack']), f['identity']
        assert re.findall(r'^> Task (\S+) FAILED$', log, re.M) == [':rules-engine:test']
    else:
        key = 'ordinary_passes' if stage == 'ordinary' else stage
        expected = 39 if stage == 'actor' else protocol['positive_counts'][key]
        assert rc == 0 and totals['passed'] == expected and totals['failures'] == 0
        assert totals['skipped'] == (43 if stage == 'ordinary' else 0)
        if stage in ['actor','recording','targeted']:
            assert cases == protocol[stage + '_cases'] and not result['duplicate_labels']
        if stage in ['broad','ordinary']:
            assert result['modules']['rules-engine']['passed'] == protocol['positive_counts']['engine']
            assert result['duplicate_labels'] == json.loads(raw['preserved/QUALIFIED_DUPLICATES.json'])
        if stage == 'ordinary':
            assert result['skips'] == sorted(json.loads(raw['preserved/BASELINE_SKIPS.json']))
            assert result['modules']['oracle-assay']['passed'] == 568
            assert result['modules']['mtg-sdk']['passed'] == 529
        if stage in protocol['expected_expanded_identity_counters']:
            expected = protocol['expected_expanded_identity_counters'][stage]
            for identity in PINS['protocol']['positive_cases']:
                assert result['names'].pop('rules-engine\t'+identity.replace('::','\t',1),None)==1
            assert sum(result['names'].values()) == expected['rows']
            assert sha(json.dumps(result['names'],sort_keys=True,separators=(',',':')).encode()) == expected['sha256']
        assert 'BUILD SUCCESSFUL' in log
    tasks = {m.group(1):m.group(2) or 'EXECUTED' for m in re.finditer(
        r'^> Task ([^\s]+)(?: (UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?$', log, re.M)}
    for module in result['modules']:
        assert tasks.get(':' + module.replace('/', ':') + ':test') == ('FAILED' if negative else 'EXECUTED')
    assert 'scripts/gradle-locked' in log and '--max-workers=2 -PkotlinCompileParallelism=1' in log
    return {k:v for k,v in result.items() if k not in ['names','cases']}

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
    manifest={}
    for line in raw['SHA256SUMS'].decode().splitlines():
        digest,path=line.split('  ',1)
        assert path not in manifest and sha(raw[path])==digest
        manifest[path]=digest
    assert set(manifest)==set(raw)-{'SHA256SUMS'}
    binding=json.loads(raw['BINDING.json'])
    for key in ['source','tree','validation','base','workflow','workflow_blob']:assert binding[key]==PINS[key]
    assert binding['run']==str(PINS['run']) and binding['attempt']==binding['run_number']==1
    assert blob(raw['WORKFLOW.yml'])==blob(raw['protocol/WORKFLOW.yml'])==PINS['workflow_blob']
    assert raw['protocol/REGRESSION.py']==(ROOT/'regression.py').read_bytes()
    assert json.loads(raw['PROTOCOL.json'])==PINS['protocol']
    protocol=json.loads(raw['EFFECTIVE_PROTOCOL.json'])
    assert protocol==json.loads((ROOT/'EXPECTED_PROTOCOL.json').read_text())
    assert raw['EFFECTIVE_GATE.py']==(ROOT/'EXPECTED_GATE.py').read_bytes()
    for name,digest in PINS['preserved_blobs'].items():assert blob(raw['preserved/'+name])==digest
    assert sha(raw['validation.just'])==PINS['recipe_sha256']
    for path,digest in PINS['source_files'].items():
        member=Path(path).name if '/research/' in path else 'source/'+path
        assert sha(raw[member])==digest,path
    assert json.loads(raw['PROTECTED.json'])==PINS['protected_archive_hashes']
    for path,digest in PINS['protected_archive_hashes'].items():assert sha(raw['source/'+path])==digest,path
    assert raw['FINAL_TRACKED_CHANGES.txt']==b''
    assert raw['SOURCE_PINS.txt'].decode().splitlines()==[PINS['validation'],PINS['tree']]
    assert raw['SOURCE_DELTA.txt'].decode().splitlines()==['\t'.join(row) for row in binding['delta']]
    contract=json.loads(raw['REGRESSION_CONTRACT.json'])
    for key in ['source','early_receipt','early_run','early_artifact']:assert contract[key]==PINS[key]
    assert contract['positive_counts']==protocol['positive_counts'] and contract['extra_cases']==PINS['protocol']['positive_cases']
    evidence=dict(source=PINS['source'],tree=PINS['tree'],validation=PINS['validation'],run=PINS['run'],job=PINS['job'],attempt=1,
        artifact=artifact['id'],artifact_bytes=len(archive),artifact_sha256=sha(archive),manifest_entries=len(manifest),gates={},
        early_receipt_reused=PINS['early_receipt'],feature_acceptance=False,official_games=0,official_seeds=0,precision_reserved=2)
    errors=[]
    def gate(label,fn):
        try:evidence['gates'][label]=fn()
        except Exception as e:errors.append(dict(gate=label,error=type(e).__name__+': '+str(e)))
    for stage in PINS['stages']:gate(stage,lambda stage=stage:verify_stage(raw,stage,protocol,PINS['run']))
    def client():
        data=json.loads(raw['client/vitest.json']);observed={};rows=[]
        assert int(raw['client/typecheck-exit.txt'])==int(raw['client/test-exit.txt'])==0
        for suite in data['testResults']:
            path='web-client/'+suite['name'].split('/web-client/',1)[-1]
            assert path in protocol['client_cases'] and path not in observed
            cases=suite['assertionResults'];assert all(x['status']=='passed' and not x.get('failureMessages') for x in cases)
            observed[path]=sorted(x['title'] for x in cases);rows+=cases
        assert observed=={k:sorted(v) for k,v in protocol['client_cases'].items()} and len(rows)==34 and data['success']
        return {'passed':34,'cases':observed}
    gate('client',client)
    def assay():
        receipt=json.loads(raw['assay/RECEIVING.json'])
        for name in ['build-exit.txt','gate-exit.txt','differential-exit.txt','corpus-capture-exit.txt']:assert int(raw['assay/'+name])==0
        assert receipt['remaining_gates']==[]
        labels={'Cards assayed','Ambiguous — distinct readings','Print mismatch','Normalization not invertible','Full inverse not reproduced','Redundant readings (same model)'}
        assert set(receipt['metrics'])==labels
        for label,expected in receipt['metrics'].items():
            values=[l.strip()[len(label):].strip() for l in raw['assay/gate.log'].decode().splitlines() if l.strip().startswith(label)]
            assert len(values)==1 and int(re.match(r'[\s:]*([0-9]+)',values[0])[1])==expected==(2000 if label=='Cards assayed' else 0)
        corpus=raw['assay/oracle-bulk-corpus.jsonl.gz']
        assert len(corpus)==receipt['corpus']['bytes']==int(raw['assay/oracle-bulk-bytes.txt'].split()[0])
        assert sha(corpus)==receipt['corpus']['sha256']==raw['assay/oracle-bulk-sha256.txt'].decode().split()[0]
        differential=raw['assay/differential.log'];assert sha(differential)==PINS['assay_differential_sha256']
        assert len(re.findall(rb'^  (.+)  \[([A-Z0-9]+)\]$',differential,re.M))==51
        return receipt
    gate('assay',assay)
    evidence['gate_errors']=errors
    evidence['state']='RECEIVED_INTERNAL_REGRESSIONS_ONLY_NOT_FEATURE_ACCEPTANCE' if not errors and run['conclusion']==job['conclusion']=='success' else 'ORIGINAL_FAILURE_RECEIVED_NOT_ACCEPTED'
    if evidence['state'].startswith('ORIGINAL_FAILURE'):
        evidence['raw_available_reports']={stage:parse_reports(raw,stage) for stage in PINS['stages']}
        evidence['original_failure_steps']=[s for s in job['steps'] if s['conclusion']=='failure']
    (ROOT/'VERIFIED_RECEIPT.json').write_text(json.dumps(evidence,indent=2)+'\n')
    print(json.dumps({k:evidence[k] for k in ['state','run','artifact','manifest_entries','gate_errors']}))

if __name__=='__main__':audit()
