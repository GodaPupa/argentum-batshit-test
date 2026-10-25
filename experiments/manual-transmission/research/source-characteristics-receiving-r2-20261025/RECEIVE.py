"""Receive one pinned original source-characteristics artifact; never executes engine tests."""
from pathlib import Path
from collections import Counter, defaultdict
import hashlib, json, os, re, subprocess, tempfile, zipfile
import xml.etree.ElementTree as ET

ROOT = Path(os.environ.get('MANUAL_RECEIPT_ROOT', Path(__file__).resolve().parent))
PINS = json.loads((Path(__file__).resolve().parent / 'PINS.json').read_text())
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
            assert sum(result['names'].values()) == expected['rows']
            assert sha(json.dumps(result['names'],sort_keys=True,separators=(',',':')).encode()) == expected['sha256']
        assert 'BUILD SUCCESSFUL' in log
    tasks = {m.group(1):m.group(2) or 'EXECUTED' for m in re.finditer(
        r'^> Task ([^\s]+)(?: (UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?$', log, re.M)}
    for module in result['modules']:
        assert tasks.get(':' + module.replace('/', ':') + ':test') == ('FAILED' if negative else 'EXECUTED')
    assert 'scripts/gradle-locked' in log and '--max-workers=2 -PkotlinCompileParallelism=1' in log
    return {k:v for k,v in result.items() if k not in ['names','cases']}


def verify_patch(patch, normal, changed):
    assert [line for line in patch.splitlines() if line.startswith('diff --git ')] == [
        'diff --git a/' + p + ' b/' + p for p in normal if normal[p] != changed[p]]
    with tempfile.TemporaryDirectory(prefix='manual-source-patch-audit-') as temp:
        for path, body in normal.items():
            p = Path(temp) / path; p.parent.mkdir(parents=True, exist_ok=True); p.write_bytes(body)
        if patch:
            subprocess.run(['git','apply','--check','-'], cwd=temp, input=patch.encode(), check=True)
            subprocess.run(['git','apply','-'], cwd=temp, input=patch.encode(), check=True)
        assert {p.relative_to(temp).as_posix():p.read_bytes() for p in Path(temp).rglob('*') if p.is_file()} == changed


def receive():
    m = json.loads((ROOT / 'metadata.json').read_text()); r = m['run']; a = m['artifact']
    assert r['id'] == PINS['run'] and r['head_sha'] == PINS['validation']
    assert r['event'] == 'push' and r['path'] == PINS['workflow'] and r['workflow_id'] == PINS['workflow_id']
    assert r['run_attempt'] == r['run_number'] == 1 and r['status'] == 'completed'
    assert m['jobs']['total_count'] == len(m['jobs']['jobs']) == 1
    job = m['jobs']['jobs'][0]
    assert job['id'] == PINS['job'] and job['run_id'] == PINS['run'] and job['run_attempt'] == 1
    assert job['status'] == 'completed' and job['conclusion'] == r['conclusion']
    assert m['main']['object']['sha'] == PINS['main']
    for key, commit, tree, parent in [('source_commit','source','tree','base'), ('validation_commit','validation','validation_tree','source')]:
        c = m[key]
        assert c['sha'] == PINS[commit] and c['tree']['sha'] == PINS[tree]
        assert [p['sha'] for p in c['parents']] == [PINS[parent]]
    delta = m['source_delta']['files']; assert m['source_delta']['base_commit']['sha'] == PINS['base']
    assert len(delta) == len(PINS['source_blobs']) and {f['filename']:f['sha'] for f in delta} == PINS['source_blobs']
    vd = m['validation_delta']['files']
    assert len(vd) == 1 and vd[0]['filename'] == PINS['workflow'] and vd[0]['sha'] == PINS['workflow_blob'] and vd[0]['status'] == 'added'
    assert a['workflow_run']['id'] == PINS['run'] and a['workflow_run']['head_sha'] == PINS['validation']
    assert a['name'] == PINS['artifact_name'] and not a['expired']
    archive = ROOT / ('original-' + str(a['id']) + '.zip'); payload = archive.read_bytes()
    assert len(payload) == a['size_in_bytes'] and 'sha256:' + sha(payload) == a['digest']
    with zipfile.ZipFile(archive) as z:
        names = z.namelist(); assert len(names) == len(set(names)) and z.testzip() is None
        assert all(not Path(n).is_absolute() and '..' not in Path(n).parts for n in names)
        raw = {n:z.read(n) for n in names if not n.endswith('/')}
    manifest = {}
    for line in raw['SHA256SUMS'].decode().splitlines():
        digest, path = line.split('  ', 1)
        assert path not in manifest and sha(raw[path]) == digest
        manifest[path] = digest
    assert set(manifest) == set(raw) - {'SHA256SUMS'}
    assert blob(raw['protocol/WORKFLOW.yml']) == PINS['workflow_blob']
    for name in ['gate.py','PROTOCOL.json','DESIGN.md']:
        assert blob(raw['protocol/' + name]) == PINS['source_blobs'][PINS['record'] + name]
    protocol = json.loads(raw['protocol/PROTOCOL.json'])
    assert protocol['base'] == PINS['base'] and protocol['authorization']['remaining_protected_bindings'] == 143
    for f in delta: assert f['status'] == ('modified' if f['filename'] in protocol['modified_paths'] else 'added')
    # Preserve and classify a failed original without treating compilation or unrelated failures as controls.
    common = dict(source=PINS['source'], tree=PINS['tree'], validation=PINS['validation'],
        run=PINS['run'], job=PINS['job'], attempt=1, artifact=a, verified_manifest_entries=len(manifest),
        official_games=0, official_seeds=0, precision_reserved=2, external_independent_review=False)
    available_source_paths = {n.removeprefix('source/') for n in raw if n.startswith('source/')}
    verified_source_paths = []
    for path, digest in protocol['implementation_sha256'].items():
        if path in available_source_paths:
            assert sha(raw['source/' + path]) == digest and blob(raw['source/' + path]) == PINS['source_blobs'][path]
            verified_source_paths.append(path)
    if 'BINDING.json' in raw:
        bound = json.loads(raw['BINDING.json'])
        for key in ['source','tree','validation','base']: assert bound[key] == PINS[key]
    if 'validation.just' in raw: assert sha(raw['validation.just']) == PINS['recipe_sha256']
    if r['conclusion'] != 'success':
        observed = {stage:parse_reports(raw, stage) for stage in PINS['stages'] if any(n.startswith(stage+'/junit/') for n in raw)}
        for result in observed.values(): result.pop('names')
        failed_steps = [s for s in job['steps'] if s['conclusion'] == 'failure']
        compiler_errors = {n:[l for l in b.decode(errors='replace').splitlines() if re.search(r'^e: |^> Task .* FAILED$|AssertionError', l)]
            for n,b in raw.items() if n.endswith('.log')}
        result = dict(common, state='ORIGINAL_FAILURE_PRESERVED_NOT_ACCEPTED', failed_steps=failed_steps,
            observed_reports=observed, compiler_and_gate_errors={k:v for k,v in compiler_errors.items() if v},
            verified_archived_implementation_paths=sorted(verified_source_paths),
            missing_archived_implementation_paths=sorted(set(protocol['implementation_sha256'])-set(verified_source_paths)),
            required_gates_not_completed=[s['name'] for s in job['steps'] if s['name'] in PINS['required_steps'] and s['conclusion'] != 'success'],
            behavioral_negative_control_acceptance=False, original_archive_preserved=True)
        (ROOT/'VERIFIED_FAILURE.json').write_text(json.dumps(result,indent=2)+'\n')
        return result
    for name in PINS['required_steps']:
        hits = [s for s in job['steps'] if s['name'] == name]
        assert len(hits) == 1 and hits[0]['conclusion'] == 'success', name
    assert all(s['conclusion'] == 'success' or (s['name'] == 'Post Run actions/setup-node@v4' and s['conclusion'] == 'skipped') for s in job['steps'])
    binding = json.loads(raw['BINDING.json'])
    assert sha(raw['validation.just']) == PINS['recipe_sha256']
    for key in ['source','tree','validation','base']: assert binding[key] == PINS[key]
    assert binding['protected_bindings'] == 143 and binding['authorized_resolver_source_characteristics_successor']
    assert raw['FINAL_TRACKED_CHANGES.txt'] == b''
    assert raw['SOURCE_PINS.txt'].decode().splitlines() == [PINS['validation'], PINS['tree']]
    expected_delta = [('M' if f['status'] == 'modified' else 'A') + '\t' + f['filename'] for f in delta]
    assert raw['SOURCE_DELTA.txt'].decode().splitlines() == expected_delta
    assert binding['delta'] == [row.split('\t') for row in expected_delta]
    for path, digest in protocol['implementation_sha256'].items():
        assert sha(raw['source/' + path]) == digest and blob(raw['source/' + path]) == PINS['source_blobs'][path]
    bindings = json.loads(raw['preserved/BINDINGS.json']); goldens = json.loads(raw['preserved/GOLDENS.json'])
    assert len(bindings) == 144 and len(goldens) == 23
    for path,spec in bindings.items():
        expected = protocol['implementation_sha256'][path] if path == protocol['authorization']['replaced_binding'] else spec['sha256']
        assert sha(raw['source/' + path]) == expected
    for spec in goldens: assert sha(raw['source/' + spec['path']]) == spec['sha256']
    assert len(protocol['legacy_fixture_sha256']) == 82
    fixture_names = {n.removeprefix('source/') for n in raw if n.startswith(('source/rules-engine/src/test/resources/legacy-suspensions/', 'source/rules-engine/src/test/resources/suspension-traces/'))}
    assert fixture_names == protocol['legacy_fixture_sha256'].keys()
    for path,digest in (protocol['legacy_fixture_sha256'] | protocol['recording_protected_sha256']).items(): assert sha(raw['source/' + path]) == digest
    assert blob(raw['source/experiments/manual-transmission/control/v0.7.decklist.txt']) == protocol['protected']['deck_blob']
    stages = {stage:verify_stage(raw, stage, protocol, PINS['run']) for stage in PINS['stages']}
    normal = {path:raw['source/' + path] for path in protocol['implementation_sha256'] if '/src/main/' in path}
    contract = protocol['mutation_contract']; anchor = contract['anchor'].encode()
    assert normal[contract['path']].count(anchor) == 1
    compilations = {}
    for stage in PINS['compilation']:
        expected = normal.copy()
        if stage != 'normal': expected[contract['path']] = expected[contract['path']].replace(anchor, contract[stage].encode(), 1)
        prefix = 'compilation/' + stage + '/'
        for path,body in expected.items(): assert raw[prefix + Path(path).name] == body
        verify_patch(raw[prefix+'SOURCE.diff'].decode(), normal, expected)
        if stage != 'normal': verify_patch(raw['MUTATION-' + stage + '.diff'].decode(), normal, expected)
        receipt = json.loads(raw[prefix+'RECEIPT.json']); log = raw[prefix+'gradle.log'].decode()
        assert receipt['source'] == PINS['source'] and receipt['validation'] == PINS['validation'] and receipt['run'] == str(PINS['run'])
        assert receipt['implementation_sha256'] == {p:sha(b) for p,b in expected.items()}
        assert receipt['log_sha256'] == sha(raw[prefix+'gradle.log'])
        assert receipt['exit'] == int(raw[prefix+'exit.txt']) == 0 and receipt['tests'] == 0
        assert receipt['expected_disposition_verified'] and not receipt['behavioral_evidence']
        assert not re.search(r'^e: |^> Task .* FAILED$', log, re.M) and 'BUILD SUCCESSFUL' in log
        assert re.search(r'^> Task :rules-engine:compileKotlin$', log, re.M)
        assert re.search(r'^> Task :rules-engine:compileTestKotlin(?: UP-TO-DATE)?$', log, re.M)
        assert not any(n.startswith(prefix) and n.endswith('.xml') for n in raw)
        assert 'scripts/gradle-locked :rules-engine:compileKotlin :rules-engine:compileTestKotlin' in log
        compilations[stage] = receipt
    client = json.loads(raw['client/vitest.json']); observed = {}; rows = []
    assert int(raw['client/typecheck-exit.txt']) == int(raw['client/test-exit.txt']) == 0
    for suite in client['testResults']:
        path = 'web-client/' + suite['name'].split('/web-client/',1)[-1]
        assert path in protocol['client_cases'] and path not in observed
        cases = suite['assertionResults']; assert all(x['status'] == 'passed' and not x.get('failureMessages') for x in cases)
        observed[path] = sorted(x['title'] for x in cases); rows += cases
    assert observed == {k:sorted(v) for k,v in protocol['client_cases'].items()} and len(rows) == 34 and client['success']
    disposition = json.loads(raw['DISPOSITION.json'])
    assert disposition['compilation_controls'] == {stage:True for stage in PINS['compilation']}
    assert disposition['original_reports_present'] == {stage:True for stage in PINS['stages']}
    assert disposition['assay_receipt_present'] and not disposition['component_adopted']
    assert disposition['official_games'] == disposition['official_seeds'] == 0 and disposition['precision_reserved'] == 2
    assay = json.loads(raw['assay/RECEIVING.json'])
    for name in ['build-exit.txt','gate-exit.txt','differential-exit.txt','corpus-capture-exit.txt']: assert int(raw['assay/' + name]) == 0
    assert assay['remaining_gates'] == []
    labels = {'Cards assayed','Ambiguous — distinct readings','Print mismatch','Normalization not invertible','Full inverse not reproduced','Redundant readings (same model)'}
    assert set(assay['metrics']) == labels
    for label,expected in assay['metrics'].items():
        values = [l.strip()[len(label):].strip() for l in raw['assay/gate.log'].decode().splitlines() if l.strip().startswith(label)]
        assert len(values) == 1 and int(re.match(r'[\s:]*([0-9]+)', values[0])[1]) == expected == (2000 if label == 'Cards assayed' else 0)
    corpus = raw['assay/oracle-bulk-corpus.jsonl.gz']
    assert len(corpus) == assay['corpus']['bytes'] == int(raw['assay/oracle-bulk-bytes.txt'].split()[0])
    assert sha(corpus) == assay['corpus']['sha256'] == raw['assay/oracle-bulk-sha256.txt'].decode().split()[0]
    differential = raw['assay/differential.log']
    assert sha(differential) == PINS['assay_differential_sha256']
    assert len(re.findall(rb'^  (.+)  \[([A-Z0-9]+)\]$', differential, re.M)) == 51
    result = dict(common, state='ACCEPTED_BOUNDED_SOURCE_CHARACTERISTICS_ONLY', stages=stages,
        compilation_controls=compilations, client_cases=observed, assay=assay,
        protected_bindings=143, authorized_resolver_extension=True, goldens=23, legacy_fixtures=82,
        original_diagnostic_preserved=True, accepted_r5_receipt_reused=True, remaining_component_gates=[],
        spellskite_registered=False, multiplayer_admitted=False)
    (ROOT/'VERIFIED_RECEIPT.json').write_text(json.dumps(result,indent=2)+'\n')
    return result


if __name__ == '__main__':
    result = receive()
    print(json.dumps({k:result[k] for k in ['state','source','run','job','attempt','verified_manifest_entries']},indent=2))
