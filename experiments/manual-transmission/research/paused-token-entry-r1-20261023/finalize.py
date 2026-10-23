"""Freeze prepared source/test bindings; never compiles, tests, dispatches or creates games."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess

root = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument('--supplemental-review', required=True)
parser.add_argument('--extra-path', action='append', default=[])
args = parser.parse_args()
protocol = json.loads((root / 'PROTOCOL.json').read_text())
assert protocol['status'] == 'DRAFT_UNCOMPILED_UNTESTED_NOT_DISPATCHABLE'
original = json.loads((root / 'ORIGINAL_CASES.json').read_text())
supplement = json.loads(Path(args.supplemental_review).read_text())
supplemental_cases = supplement['cases']
assert supplemental_cases, 'Missing preregistered invalid-answer controls'
all_cases = original + supplemental_cases
identities = [case['classname'] + '::' + case['name'] for case in all_cases]
assert len(identities) == len(set(identities))
assert all(c['predicted_baseline'] in ('pass', 'assertion_failure') for c in all_cases)
assert all(isinstance(c.get('expected_first_failure_line'), int) for c in all_cases
           if c['predicted_baseline'] == 'assertion_failure')
manifest = {}
for path in dict.fromkeys(protocol['manifest_paths'] + args.extra_path):
    body = Path(path).read_bytes()
    record = dict(bytes=len(body), sha256=hashlib.sha256(body).hexdigest(),
                  git_blob=hashlib.sha1(b'blob ' + str(len(body)).encode() + b'\0' + body).hexdigest())
    baseline = subprocess.run(['git', 'show', protocol['baseline_source'] + ':' + path], capture_output=True)
    if baseline.returncode:
        record['baseline_absent'] = True
    else:
        record['baseline_sha256'] = hashlib.sha256(baseline.stdout).hexdigest()
    manifest[path] = record
protocol['source_manifest'] = manifest
protocol['baseline_restore_paths'] = list(dict.fromkeys(protocol['baseline_restore_paths'] +
    [path for path in args.extra_path if '/src/main/' in path]))
protocol['cases'] = all_cases
protocol['expected_negative'] = dict(rows=len(all_cases),
    passed=sum(c['predicted_baseline'] == 'pass' for c in all_cases),
    failures=sum(c['predicted_baseline'] == 'assertion_failure' for c in all_cases),
    errors=0, skipped=0, classes=len({c['classname'] for c in all_cases}))
added = len(all_cases)
protocol['qualification'].update(focused_passed=616 + added, engine_passed=3908 + added,
    broad_required_passed=14683 + added, ordinary_passed=18291 + added)
protocol['status'] = 'SOURCE_FROZEN_EXECUTION_REQUIRES_ACTIVITY_AND_WORKFLOW_PINS'
protocol['supplemental_case_status'] = 'PREREGISTERED_UNEXECUTED'
protocol['supplemental_review_sha256'] = hashlib.sha256(Path(args.supplemental_review).read_bytes()).hexdigest()
(root / 'SUPPLEMENTAL_REVIEW.json').write_bytes(Path(args.supplemental_review).read_bytes())
expected = json.loads((root / 'INHERITED_CASES.json').read_text())
for case in all_cases:
    expected.setdefault(case['classname'].rsplit('.', 1)[-1], []).append(case['classname'] + '::' + case['name'])
expected = {cls: sorted(values) for cls, values in expected.items()}
(root / 'EXPECTED_CASES.json').write_text(json.dumps(expected, indent=2) + '\n')
(root / 'PROTOCOL.json').write_text(json.dumps(protocol, indent=2) + '\n')
print(json.dumps({'qualification': protocol['qualification'], 'negative': protocol['expected_negative'],
                  'source_manifest_entries': len(manifest)}, indent=2))
