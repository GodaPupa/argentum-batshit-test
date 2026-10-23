"""R5 source binding, narrow regression control and lossless report accounting."""
from pathlib import Path
from collections import Counter
import hashlib, json, os, re, shutil, subprocess, sys
import xml.etree.ElementTree as ET

ROOT = Path('experiments/manual-transmission/research/paused-token-entry-r5-20261023')
R4 = Path('experiments/manual-transmission/research/paused-token-entry-r4-20261023')
R2 = Path('experiments/manual-transmission/research/paused-token-entry-r2-20261023')
OUT = Path('paused-token-r5-evidence')
ANCESTOR = '9eb2be694761a4cae60ad4970d07e9d458d8054c'
R3 = '86a5dea55f088f1e242a151920aa83a671039b61'
BASELINE = '418cdfdacd5191549e9fc8e4a071330120b071b7'
TEST = 'rules-engine/src/test/kotlin/com/wingedsheep/engine/event/EntryChoiceEventProvenanceTest.kt'
HANDLER = 'rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/actions/decision/SubmitDecisionHandler.kt'
ANNEX = str(ROOT / 'annex/EntryChoiceEventProvenanceTest.R4.original.kt.txt')
NEGATIVE_ANCHORS = {
    'EP01': 'entries.single().entryTriggersAlreadyProcessed shouldBe true',
    'EP02': 'zoneEvents.map { it.entryTriggersAlreadyProcessed } shouldBe listOf(true, false)',
    'EP03': 'entries.map { it.entryTriggersAlreadyProcessed } shouldBe listOf(true, true)',
    'EP04': 'detector.detectTriggers(result.state, listOf(decodedMarked, decodedRaw)).size shouldBe 1',
}

def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()

def git_bytes(ref, path):
    return subprocess.check_output(['git', 'show', ref + ':' + path])

def sha(body):
    return hashlib.sha256(body).hexdigest()

def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')

def original_reports():
    return [p for p in sorted(Path('.').glob('**/build/test-results/test/TEST-*.xml'))
            if OUT.name not in p.parts]

def bind(mode='candidate'):
    assert mode in ('candidate', 'negative')
    source = os.environ['SOURCE']
    assert git('rev-parse', 'HEAD^') == source
    assert git('rev-parse', source + '^{tree}') == os.environ['SOURCE_TREE']
    assert git('diff', '--name-only', source, 'HEAD') == os.environ['WORKFLOW']
    assert git('merge-base', ANCESTOR, source) == ANCESTOR
    assert os.environ['BASELINE'] == BASELINE
    assert os.environ.get('GITHUB_RUN_ATTEMPT', '1') == '1'
    assert os.environ.get('GITHUB_RUN_NUMBER', '1') == '1'
    assert git('rev-parse', 'refs/remotes/origin/main') == '37a603617a03dfd2d81b39dae004f7f336a70dfc'
    changed = set(git('diff', '--name-only', ANCESTOR, source).splitlines())
    expected = {TEST} | {str(ROOT / p) for p in (
        'PROTOCOL.json', 'gate.py', 'QUALIFIED_DUPLICATES.json', 'POST_ORDERING_JUNIT.xml',
        'README.md', 'annex/EntryChoiceEventProvenanceTest.R4.original.kt.txt')}
    assert changed == expected, changed ^ expected
    original = git_bytes(ANCESTOR, TEST)
    amended = git_bytes(source, TEST)
    assert git('rev-parse', ANCESTOR + ':' + TEST) == '0250a12ad245eded770bec604dbbfda63f7210e9'
    assert git('rev-parse', source + ':' + TEST) == '9392987680c91a7ac75e96ecb67779ac09fe483e'
    assert Path(ANNEX).read_bytes() == original
    assert Path(TEST).read_bytes() == amended
    before = Counter(l.strip() for l in original.decode().splitlines() if ' shouldBe ' in l)
    after = Counter(l.strip() for l in amended.decode().splitlines() if ' shouldBe ' in l)
    assert not (before - after), 'Original behavioral assertions removed'
    assert re.findall(r'    test\("([^"\n]+)"', original.decode()) == re.findall(r'    test\("([^"\n]+)"', amended.decode())
    assert git_bytes(source, HANDLER) == git_bytes(ANCESTOR, HANDLER), 'Unapproved production change'
    handler_ref = R3 if mode == 'negative' else source
    assert Path(HANDLER).read_bytes() == git_bytes(handler_ref, HANDLER)
    status = git('diff', '--name-only').splitlines()
    assert status == ([HANDLER] if mode == 'negative' else []), status
    if mode == 'candidate':
        assert not git('status', '--porcelain', '--untracked-files=no')
    bindings = json.loads((R2 / 'BINDINGS.json').read_text())
    assert len(bindings) == 144
    for path, spec in bindings.items():
        body = original if path == TEST else Path(path).read_bytes()
        assert sha(body) == spec['sha256'], path
    goldens = json.loads(Path('experiments/manual-transmission/research/ordinary-build-r1-20261022/GOLDENS.json').read_text())
    assert len(goldens) == 23
    for entry in goldens:
        assert sha(Path(entry['path']).read_bytes()) == entry['sha256'], entry['path']
    assert git('rev-parse', source + ':experiments/manual-transmission/control/v0.7.decklist.txt') == 'c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89'
    assert git('rev-parse', source + ':rules-engine/src/test/kotlin/com/wingedsheep/engine/scenarios/PausedTokenInstructionScenarioTest.kt') == '2783d8891a70c93b88a1e5be8237cd4ad1afdb83'
    # Duplicate display labels are inherited unchanged, not duplicate test executions to discard.
    baseline_paths = git('ls-tree', '-r', '--name-only', BASELINE).splitlines()
    duplicate_fixtures = {}
    for name in ('CostHandlerPayLifeTest.kt', 'ConditionalControllerGrantsTest.kt'):
        paths = [p for p in baseline_paths if p.endswith('/' + name)]
        assert len(paths) == 1, paths
        assert git_bytes(BASELINE, paths[0]) == git_bytes(source, paths[0])
        duplicate_fixtures[paths[0]] = git('rev-parse', source + ':' + paths[0])
    xml = (ROOT / 'POST_ORDERING_JUNIT.xml').read_bytes()
    assert sha(xml) == '5e1f14843c4ec6504ef6c1f7ddf8e1524f590eeade1165bdf4e6fb2be3b02c61'
    suite = ET.fromstring(xml)
    assert [suite.get(k) for k in ('tests', 'failures', 'errors', 'skipped')] == ['3', '0', '0', '0']
    lines = Path(TEST).read_text().splitlines()
    frames = {}
    for label, anchor in NEGATIVE_ANCHORS.items():
        matches = [i + 1 for i, line in enumerate(lines) if line.strip() == anchor]
        assert len(matches) == 1, (label, matches)
        frames[label] = matches[0]
    write_json(OUT / ('binding-' + mode + '.json'), dict(
        source=source, tree=os.environ['SOURCE_TREE'], validation=git('rev-parse', 'HEAD'), mode=mode,
        original_test_sha256=sha(original), amended_test_sha256=sha(amended),
        original_assertion_count=sum(before.values()), protected_bindings=len(bindings), goldens=len(goldens),
        qualified_duplicate_fixtures=duplicate_fixtures, negative_first_frames=frames,
        diagnostic_run=39985587423, diagnostic_artifact=12516126080,
        official_games=0, official_seeds=0, precision_reserved=2, competitive_admission=False))
    print('R5 source and safeguards bound:', mode, source)

def prepare(stage):
    assert stage in ('negative', 'focused', 'broad', 'ordinary')
    out = OUT / stage
    records = []
    for p in original_reports():
        body = p.read_bytes()
        dest = out / 'preexisting-junit' / p
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(body)
        records.append(dict(path=str(p), bytes=len(body), sha256=sha(body)))
        p.unlink()
    write_json(out / 'PREEXISTING_REPORTS.json', records)
    print('Archived and cleared', len(records), 'prior JUnit reports before', stage)

def collect_negative():
    bind('negative')
    out = OUT / 'negative'
    reports = original_reports()
    for p in reports:
        dest = out / 'junit' / p
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(p, dest)
    assert len(reports) == 1 and reports[0].name == 'TEST-com.wingedsheep.engine.event.EntryChoiceEventProvenanceTest.xml'
    root = ET.fromstring(reports[0].read_bytes())
    rows = root.findall('.//testcase')
    binding = json.loads((OUT / 'binding-negative.json').read_text())
    failures = []
    for c in rows:
        f = c.find('failure')
        failures.append(dict(name=c.get('name'), classname=c.get('classname'),
            type=f.get('type') if f is not None else None, detail=f.text if f is not None else None))
    write_json(out / 'SUMMARY.json', dict(status='OBSERVED_NOT_YET_ACCEPTED', rows=len(rows), failures=failures,
        negative_kind='R3 handler regression reproduction; NOT a qualified engine baseline',
        source=os.environ['SOURCE'], validation=git('rev-parse', 'HEAD'), run=os.environ['GITHUB_RUN_ID']))
    assert [root.get(k) for k in ('tests', 'failures', 'errors', 'skipped')] == ['4', '4', '0', '0']
    expected = json.loads((R4 / 'EXPECTED_CASES.json').read_text())['EntryChoiceEventProvenanceTest']
    assert sorted(c.get('classname') + '::' + c.get('name') for c in rows) == expected
    for f in failures:
        assert f['type'] == 'org.opentest4j.AssertionFailedError', f
        label = f['name'].split()[0]
        frame = re.search(r'EntryChoiceEventProvenanceTest\.kt:(\d+)', f['detail'] or '')
        assert frame and int(frame.group(1)) == binding['negative_first_frames'][label], (label, frame.group(1) if frame else None)
    assert int((out / 'exit.txt').read_text()) != 0
    assert '> Task :rules-engine:test FAILED' in (out / 'gradle.log').read_text()
    write_json(out / 'ACCEPTED.json', dict(status='FOUR_PREDICTED_ASSERTION_FAILURES_OBSERVED', frames=binding['negative_first_frames'],
        meaning='Corrected tests still reject missing processed-entry markers', official_games=0, official_seeds=0))
    print('All four exact regression-control failures verified')

def replace_once(text, old, new):
    assert text.count(old) == 1, old
    return text.replace(old, new, 1)

def adapt_collector():
    code = (R4 / 'collect.py').read_text().replace('paused-token-r4-evidence', OUT.name)
    code = replace_once(code, 'import sys\n', 'import sys\nfrom collections import Counter\n')
    code = replace_once(code, 'modules, cases_by_class, identities, failures = {}, {}, set(), []',
        'modules, cases_by_class, identities, failures = {}, {}, set(), []\nidentity_counts = Counter()')
    code = replace_once(code, '        assert (module, full) not in identities, (module, full)',
        "        identity_counts['\\t'.join((module, case.get('classname'), case.get('name')))] += 1")
    marker = 'totals = {key: sum(module[key] for module in modules.values()) for key in'
    checks = """duplicates = {key: n for key, n in identity_counts.items() if n > 1}
expected_duplicates = json.loads(Path('experiments/manual-transmission/research/paused-token-entry-r5-20261023/QUALIFIED_DUPLICATES.json').read_text()) if stage in ('broad', 'ordinary') else {}
(out / 'DUPLICATE_LABEL_ACCOUNTING.json').write_text(json.dumps({'observed': duplicates, 'expected': expected_duplicates, 'all_case_rows_retained': True}, indent=2) + '\\n')
assert duplicates == expected_duplicates, ('Unrecognized or missing repeated case labels', duplicates, expected_duplicates)
"""
    code = replace_once(code, marker, checks + marker)
    # R4's ordinary branch accidentally used the obsolete key; the expected count stays 3978.
    code = replace_once(code, "protocol['qualification']['engine_passed']", "protocol['qualification']['broad_engine_passed']")
    compile(code, 'R5_EFFECTIVE_COLLECTOR.py', 'exec')
    return code

def positive(stage):
    assert stage in ('focused', 'broad', 'ordinary')
    bind()
    os.environ['STAGE'] = stage
    os.environ['R4_RECORD'] = str(R4)
    os.environ['RECORD'] = str(R2)
    code = adapt_collector()
    dest = OUT / stage / 'EFFECTIVE_COLLECTOR.py'
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(code)
    exec(compile(code, str(dest), 'exec'), {'__name__': '__main__'})

def assay():
    os.environ['RECORD'] = str(R2)
    code = (R4 / 'check_assay.py').read_text().replace('paused-token-r4-evidence', OUT.name)
    dest = OUT / 'assay/EFFECTIVE_CHECK_ASSAY.py'
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(code)
    exec(compile(code, str(dest), 'exec'), {'__name__': '__main__'})

if __name__ == '__main__':
    command = sys.argv[1]
    if command == 'bind': bind(sys.argv[2] if len(sys.argv) > 2 else 'candidate')
    elif command == 'prepare': prepare(sys.argv[2])
    elif command == 'negative': collect_negative()
    elif command == 'collect': positive(sys.argv[2])
    elif command == 'assay': assay()
    elif command == 'selfcheck':
        code = adapt_collector()
        assert "['broad_engine_passed']" in code
        print('Effective positive collector compiled; strict inherited counts and skip gates retained')
    else: raise ValueError('Unknown command: ' + command)
