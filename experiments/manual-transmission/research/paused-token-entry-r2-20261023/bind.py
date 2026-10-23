"""Bind the exact immutable source, validation-only child and preserved safeguards."""
from pathlib import Path
import hashlib
import json
import os
import subprocess

root = Path(os.environ['RECORD'])
protocol = json.loads((root / 'PROTOCOL.json').read_text())
assert protocol['status'] == 'SOURCE_FROZEN_EXECUTION_REQUIRES_ACTIVITY_AND_WORKFLOW_PINS'
mode = os.environ.get('BIND_MODE', 'fixed')
assert mode in ('fixed', 'negative')
source = os.environ['SOURCE']
git = lambda *args: subprocess.check_output(['git', *args], text=True).strip()
assert git('rev-parse', 'HEAD^') == source
assert git('rev-parse', source + '^{tree}') == os.environ['SOURCE_TREE']
assert git('diff', '--name-only', source, 'HEAD') == os.environ['WORKFLOW']
assert os.environ['BASELINE'] == protocol['baseline_source']
assert os.environ.get('GITHUB_RUN_ATTEMPT', '1') == '1'
reuse = json.loads((root / 'BASELINE_REUSE.json').read_bytes())
assert reuse['receipt'] == protocol['failed_receipt']
assert hashlib.sha256((root / 'BASELINE_REUSE.json').read_bytes()).hexdigest() == protocol['baseline_reuse_sha256']
r1 = protocol['predecessor_source']
r1_record = 'experiments/manual-transmission/research/paused-token-entry-r1-20261023'
assert not git('diff', '--name-only', r1, source, '--', r1_record), 'Original R1 record changed'
invalid_path = 'rules-engine/src/test/kotlin/com/wingedsheep/engine/scenarios/PausedTokenInvalidResponseTest.kt'
r2_record = 'experiments/manual-transmission/research/paused-token-entry-r2-20261023/'
allowed_exact = {invalid_path, 'experiments/manual-transmission/research/WORK_EXECUTION.json'}
assert all(path in allowed_exact or path.startswith(r2_record)
           for path in git('diff', '--name-only', r1, source).splitlines()), 'Unreviewed R2 source/build/runtime delta'
r1_code_changes = [path for path in git('diff', '--name-only', r1, source).splitlines()
                   if path.startswith(('rules-engine/src/', 'mtg-sdk/src/', 'mtg-sets/', 'ai/', 'gym/', 'game-server/', 'web-client/'))]
assert r1_code_changes == [invalid_path], r1_code_changes
original_invalid = subprocess.check_output(['git', 'show', r1 + ':' + invalid_path])
assert (root / 'annex/PausedTokenInvalidResponseTest.R1.kt').read_bytes() == original_invalid
candidate_invalid = Path(invalid_path).read_bytes()
insertion = (b'            val counters = result.getEntity(token)!!.get<CountersComponent>()\n'
             b'            (counters != null) shouldBe true\n')
assert candidate_invalid.count(insertion) == 1 and candidate_invalid.replace(insertion, b'', 1) == original_invalid
negative_recipe = (root / 'validation.just').read_text().split('\n\n', 1)[0]
assert negative_recipe.count('--tests') == 1 and "--tests '*PausedTokenInvalidResponseTest'" in negative_recipe
code_roots = ('rules-engine/src/', 'mtg-sdk/src/', 'mtg-sets/', 'ai/', 'gym/', 'game-server/', 'web-client/')
code_changes = [path for path in git('diff', '--name-only', protocol['baseline_source'], source).splitlines()
                if path.startswith(code_roots)]
assert set(code_changes) <= set(protocol['source_manifest']), code_changes
preexisting_test_changes = [line.split('\t', 1)[1] for line in
    git('diff', '--name-status', protocol['baseline_source'], source).splitlines()
    if line.startswith('M\t') and '/src/test/' in line]
fixture_path = 'rules-engine/src/test/kotlin/com/wingedsheep/engine/handlers/effects/token/TokenBatchEntryHistoryTest.kt'
assert preexisting_test_changes == [fixture_path], preexisting_test_changes
if mode == 'fixed':
    archived_fixture = Path('experiments/manual-transmission/research/paused-token-transaction-draft-r1-20261023') / fixture_path
    assert Path(fixture_path).read_bytes() == archived_fixture.read_bytes(), 'Unexpected fixture amendment'
bindings = json.loads((root / 'BINDINGS.json').read_text())
for path, spec in bindings.items():
    body = Path(path).read_bytes()
    assert hashlib.sha256(body).hexdigest() == spec['sha256'], path
for path, spec in protocol['source_manifest'].items():
    restored = mode == 'negative' and path in protocol['baseline_restore_paths']
    if restored and spec.get('baseline_absent'):
        assert not Path(path).exists(), path
        continue
    body = Path(path).read_bytes()
    key = 'baseline_sha256' if restored else 'sha256'
    assert hashlib.sha256(body).hexdigest() == spec[key], path
goldens = json.loads(Path('experiments/manual-transmission/research/ordinary-build-r1-20261022/GOLDENS.json').read_text())
assert len(goldens) == 23
for golden in goldens:
    assert hashlib.sha256(Path(golden['path']).read_bytes()).hexdigest() == golden['sha256'], golden['path']
if mode == 'fixed':
    assert not git('status', '--porcelain', '--untracked-files=no')
out = Path('paused-token-r2-evidence')
out.mkdir(exist_ok=True)
record = dict(source=source, source_tree=git('rev-parse', source + '^{tree}'),
    validation=git('rev-parse', 'HEAD'), validation_tree=git('rev-parse', 'HEAD^{tree}'),
    baseline=protocol['baseline_source'], mode=mode, source_manifest=protocol['source_manifest'],
    bindings=bindings, goldens_verified=len(goldens), official_games=0, official_seeds=0,
    precision_reserved=2, competitive_admission=False)
(out / ('binding-' + mode + '.json')).write_text(json.dumps(record, indent=2) + '\n')
print('Bound', mode, source, 'to', record['validation'], 'with', len(bindings), 'protected paths')
