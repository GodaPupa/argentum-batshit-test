"""Freeze R2 delta-baseline metadata only. No compilation, tests, games or dispatch."""
from pathlib import Path
import hashlib
import json
import subprocess
import sys
sys.dont_write_bytecode = True
from reuse_baseline import audit_reuse

root = Path(__file__).resolve().parent
protocol = json.loads((root / 'PROTOCOL.json').read_bytes())
assert protocol['status'] == 'DRAFT_UNCOMPILED_UNTESTED_NOT_DISPATCHABLE'
changes = subprocess.check_output(['git', 'diff', '--name-only', protocol['predecessor_source']], text=True).splitlines()
allowed = {'rules-engine/src/test/kotlin/com/wingedsheep/engine/scenarios/PausedTokenInvalidResponseTest.kt',
           'experiments/manual-transmission/research/WORK_EXECUTION.json'}
assert all(path in allowed or path.startswith('experiments/manual-transmission/research/paused-token-entry-r2-20261023/')
           for path in changes), changes
reuse = audit_reuse(write=False)
assert reuse['reused_totals'] == protocol['expected_reused_baseline']
assert len(protocol['cases']) == 14 and all(case['classname'].endswith('PausedTokenInvalidResponseTest') for case in protocol['cases'])
assert sum(case['predicted_baseline'] == 'assertion_failure' for case in protocol['cases']) == 6
assert sum(case['predicted_baseline'] == 'pass' for case in protocol['cases']) == 8
assert len(protocol['composite_baseline_cases']) == 70
assert len({case['classname'] + '::' + case['name'] for case in protocol['composite_baseline_cases']}) == 70
assert sum(case['predicted_baseline'] == 'assertion_failure' for case in protocol['composite_baseline_cases']) == 48
invalid_lines = Path('rules-engine/src/test/kotlin/com/wingedsheep/engine/scenarios/PausedTokenInvalidResponseTest.kt').read_text().splitlines()
assert invalid_lines[126].strip() == '(counters != null) shouldBe true'
assert '(paused.pendingDecision is ChooseTargetsDecision) shouldBe true' in invalid_lines[135]
for path, previous in protocol['source_manifest'].items():
    body = Path(path).read_bytes()
    record = dict(bytes=len(body), sha256=hashlib.sha256(body).hexdigest(),
                  git_blob=hashlib.sha1(b'blob ' + str(len(body)).encode() + b'\0' + body).hexdigest())
    baseline = subprocess.run(['git', 'show', protocol['baseline_source'] + ':' + path], capture_output=True)
    if baseline.returncode:
        record['baseline_absent'] = True
    else:
        record['baseline_sha256'] = hashlib.sha256(baseline.stdout).hexdigest()
    if not path.endswith('/PausedTokenInvalidResponseTest.kt'):
        assert record == previous, path
    protocol['source_manifest'][path] = record
protocol['status'] = 'SOURCE_FROZEN_EXECUTION_REQUIRES_ACTIVITY_AND_WORKFLOW_PINS'
protocol['baseline_reuse_sha256'] = hashlib.sha256((root / 'BASELINE_REUSE.json').read_bytes()).hexdigest()
protocol['supplemental_review_sha256'] = hashlib.sha256((root / 'SUPPLEMENTAL_REVIEW.json').read_bytes()).hexdigest()
(root / 'PROTOCOL.json').write_text(json.dumps(protocol, indent=2) + '\n')
print(json.dumps({'status': protocol['status'], 'fresh14': protocol['expected_negative'],
    'reused56': protocol['expected_reused_baseline'], 'composite70': protocol['expected_composite_baseline'],
    'qualification': protocol['qualification']}, indent=2))
