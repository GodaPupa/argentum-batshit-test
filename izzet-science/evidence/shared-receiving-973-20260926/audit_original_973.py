import collections, datetime, gzip, hashlib, json, pathlib, re, zipfile
import xml.etree.ElementTree as ET

R = pathlib.Path(__file__).parent
live = json.loads((R / 'completed-github-metadata.json').read_text())
pin_path = R / 'receiving-run-live-git-pins.json'
pins = json.loads(pin_path.read_bytes() if pin_path.exists() else gzip.decompress((R / 'receiving-run-live-git-pins.json.gz').read_bytes()))
sha = lambda b: hashlib.sha256(b).hexdigest()
blob = lambda b: hashlib.sha1(b'blob ' + str(len(b)).encode() + b'\0' + b).hexdigest()
archive = pathlib.Path(live['original_path'])
if not archive.exists():
    archive = R / 'original-artifact-10918805789.zip'
raw = archive.read_bytes()
meta = live['artifacts']['artifacts'][0]
assert len(raw) == meta['size_in_bytes'] == 1198221
assert 'sha256:' + sha(raw) == meta['digest']
assert live['run']['id'] == 36275982231 and live['run']['run_attempt'] == 1
assert live['run']['head_sha'] == '49677903b81afe8b3464f3ccf7fedc60002bdfcb'
assert live['run']['event'] == 'push' and live['run']['conclusion'] == 'failure'
assert len(live['jobs']['jobs']) == 1 and live['jobs']['jobs'][0]['conclusion'] == 'failure'
assert pins['source_commit']['tree']['sha'] == '4f91346617cd0d4e29e09ab8405c141883cf1820'
assert pins['control_commit']['tree']['sha'] == '577f2d205e27db472665b4fb59d97125ecbe9154'
assert pins['source_full_tree_not_truncated'] and not pins['control_tree']['truncated']
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist())) == 354
    members = {n: {'bytes': len(z.read(n)), 'sha256': sha(z.read(n))} for n in z.namelist()}
    a = json.loads(z.read('audit.json'))
    g = json.loads(z.read('control/receiving-gate.json'))
    assert g['expected_classes'] == len(g['banks']) == len(a['stages']) == 87
    assert g['expected_cases'] == sum(b['expected_cases'] for b in g['banks']) == 973
    controls = {t['path']: t['sha'] for t in pins['control_tree']['tree'] if t['type'] == 'blob'}
    assert len(controls) == 3
    for path, expected in controls.items():
        b = z.read('control/' + path)
        assert blob(b) == expected and sha(b) == a['control_files_sha256'][path]
    assert a['control_files_sha256'] == {'.github/workflows/izzet-shared-receiving.yml': 'a16ce49a49cf001c19553f1fbed99d6c193ab2d9f060d7a85e4588c91616fc88', 'izzet-receiving-qualification.py': '4472055535f616e80a048654fd2367ef70a653be9f4e49332ba4695490fffae7', 'receiving-gate.json': '5e946e007134692cb1db98e042ca9e8183a4e241cb507baecc43854224ae7b65'}
    before = json.loads(z.read('source-before.json'))
    after = json.loads(z.read('source-after.json'))
    assert before == after == a['source_before'] == a['source_after']
    assert before['head'] == g['source_head'] == pins['source_commit']['sha']
    assert before['tree'] == g['source_tree'] == pins['source_commit']['tree']['sha'] and before['status'] == ''
    assert before['authority_sha256'] == g['preserved_authority_sha256']
    assert before['test_source_git_blobs'] == {b['test_source']: b['test_source_git_blob'] for b in g['banks']}
    for path, expected in before['test_source_git_blobs'].items():
        assert pins['source_blob_map'][path] == expected
    assert len(before['authority_sha256']) == 7 and len(before['test_source_git_blobs']) == 87
    assert a['official_games'] == a['official_seeds'] == a['new_pilot_cases'] == 0
    assert not a['gameplay_authorized'] and not a['full_runtime_accepted']
    cases = []; stages = []; failures = []; seen = set(); counts = collections.Counter(); starts = finishes = 0
    for i, (bank, stage) in enumerate(zip(g['banks'], a['stages']), 1):
        prefix = f"{i:02d}-{bank['stage']}"
        command = json.loads(z.read(prefix + '/command.json'))
        assert stage == command and stage['stage'] == bank['stage'] and stage['class'] == bank['class']
        code = int(z.read(prefix + '/exit-status.txt'))
        assert code == stage['exit_status'] and code in (0, 1)
        counts['exit_' + str(code)] += 1
        assert stage['command'] == ['just', 'test-class', bank['class'].rsplit('.', 1)[-1], '--rerun', '--no-build-cache', '-DupdateSnapshots=false', '--info', '--stacktrace', '--max-workers=1', '-PkotlinCompileParallelism=1', '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=-Xmx4g']
        log = z.read(prefix + '/command.log').decode()
        task = ':' + bank['module'].replace('/', ':') + ':test'
        lines = log.splitlines()
        expected_marker = '> Task ' + task + (' FAILED' if code else '')
        assert expected_marker in lines
        assert not any('> Task ' + task + ' ' + s in lines for s in ('NO-SOURCE', 'UP-TO-DATE', 'FROM-CACHE', 'SKIPPED'))
        st = sum('Gradle Test Executor' in l and 'started executing tests' in l for l in lines)
        en = sum('Gradle Test Executor' in l and 'finished executing tests' in l for l in lines)
        assert st == en == 1
        starts += st; finishes += en
        assert "gradle-locked: 'shlock' not found; running ./gradlew unlocked" in log
        assert ('BUILD FAILED' if code else 'BUILD SUCCESSFUL') in log
        xmls = [n for n in z.namelist() if n.startswith(prefix + '/') and n.endswith('.xml')]
        assert len(xmls) == 1
        b = z.read(xmls[0]); x = ET.fromstring(b); tc = x.findall('testcase')
        assert x.tag == 'testsuite' and x.attrib['name'] == bank['class']
        assert len(tc) == int(x.attrib['tests']) == bank['expected_cases']
        assert sorted(t.attrib['name'] for t in tc) == sorted(bank['case_names'])
        timestamp = datetime.datetime.fromisoformat(x.attrib['timestamp']).replace(tzinfo=datetime.timezone.utc).timestamp() * 1e9
        assert stage['started_ns'] - 2e9 <= timestamp <= stage['finished_ns'] + 2e9
        assert int(x.attrib['errors']) == int(x.attrib['skipped']) == 0
        stage_failures = 0
        for t in tc:
            key = (t.attrib['classname'], t.attrib['name'])
            assert key[0] == bank['class'] and key not in seen; seen.add(key)
            assert t.find('error') is None and t.find('skipped') is None
            failure = t.find('failure'); failed = failure is not None
            row = {'class': key[0], 'name': key[1], 'result': 'FAIL' if failed else 'PASS', 'xml': xmls[0]}
            if failed:
                stage_failures += 1
                row.update({'message': failure.attrib.get('message'), 'type': failure.attrib.get('type'), 'full_trace': failure.text})
                failures.append(row)
            cases.append(row)
        assert stage_failures == int(x.attrib['failures'])
        assert bool(code) == bool(stage_failures)
        assert stage['status'] == ('FAILED_PRESERVED_FOR_REVIEW' if code else 'PASS_PRESERVED_CASE_IDENTITIES')
        if code == 0:
            assert stage['actual']['xml_sha256'] == sha(b) and stage['actual']['actual_cases'] == len(tc)
        stages.append({'stage': bank['stage'], 'scope': bank['scope'], 'class': bank['class'], 'cases': len(tc), 'passed': len(tc)-stage_failures, 'failures': stage_failures, 'errors': 0, 'skipped': 0, 'exit': code, 'fresh_executor_starts': st, 'fresh_executor_finishes': en, 'xml_sha256': sha(b), 'command_log_sha256': sha(z.read(prefix+'/command.log'))})
    assert len(cases) == len(seen) == 973 and len(failures) == 4
    assert counts == {'exit_1': 3, 'exit_0': 84} and starts == finishes == 87
    assert a['status'] == 'INCOMPLETE' and a['errors'] == ['AssertionError: One or more preserved classes failed; see raw artifacts']
    out = {'schema': 'izzet973-original-author-raw-audit-v1', 'review_role': 'Source/control author audit; non-author review still required', 'run': 36275982231, 'attempt': 1, 'archive': {'artifact_id': 10918805789, 'bytes': len(raw), 'sha256': sha(raw), 'crc': 'PASS', 'members': members}, 'source': before, 'control_head': a['control_head'], 'control_tree': pins['control_commit']['tree']['sha'], 'control_sha256': a['control_files_sha256'], 'stages': stages, 'case_records': cases, 'summary': {'classes': 87, 'actual_cases': 973, 'distinct_class_name_identities': 973, 'passed': 969, 'failures': 4, 'errors': 0, 'skipped': 0, 'class_exit0': 84, 'class_exit1': 3, 'fresh_worker_starts': starts, 'fresh_worker_finishes': finishes}, 'failures': failures, 'qualification': 'REJECTED_COMPLETE_SOURCE_PENDING_FOUR_REGRESSION_DISPOSITIONS', 'limitations': ['No full-CI acceptance; no current receiver acceptance.', 'Dalkovan strengthened two-case pass is a component observation, not whole-runtime admission.', 'Baseline hash prefix matches prior candidate observation, but this uninstrumented artifact does not contain its complete action stream; full stream equality is not independently claimed.', 'Arcane/Wall pending decision type is not exposed by these exceptions; no exact pending type or repair inferred.', 'All87 logs show missing-shlock fallback on isolated runner; no functioning interprocess semaphore claimed.'], 'official_games': 0, 'official_seeds': 0, 'deck_or_pilot_changes': False}
    (R / 'original-973-author-raw-audit.json').write_text(json.dumps(out, indent=2) + '\n')
    print(json.dumps(out['summary']))
