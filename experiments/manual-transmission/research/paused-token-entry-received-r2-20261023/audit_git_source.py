"""Audit pinned Git blobs only. Never execute candidate code, builds or games."""
import argparse
import json
import subprocess
import sys
sys.dont_write_bytecode = True
from common import ROOT, digest, frozen_inputs, load_json, save_or_compare


def audit(repo, config):
    def git(*args):
        return subprocess.check_output(['git', '-C', str(repo), *args])
    def require(condition, message):
        if not condition:
            raise ValueError(message)
    source, validation, baseline = config['source'], config['validation'], config['baseline']
    protocol, bindings, goldens = load_json('PROTOCOL.json'), load_json('BINDINGS.json'), load_json('GOLDENS.json')
    require(git('rev-parse', source + '^{tree}').decode().strip() == config['source_tree'], 'Source tree differs')
    require(git('rev-parse', validation + '^{tree}').decode().strip() == config['validation_tree'], 'Validation tree differs')
    require(git('rev-parse', validation + '^').decode().strip() == source, 'Validation is not direct source child')
    validation_delta = git('diff', '--name-status', source, validation).decode()
    require(validation_delta == 'A\t' + config['workflow'] + '\n', 'Non-workflow validation delta')
    for name, spec in config['inputs'].items():
        require(git('show', spec['commit'] + ':' + spec['path']) == (ROOT / name).read_bytes(), 'Git input differs: ' + name)
    require(protocol['baseline_source'] == baseline, 'Baseline protocol drift')
    require(protocol['qualified_baseline_receipt'] == config['baseline_receipt'], 'Baseline receipt drift')
    require(protocol['expected_negative'] == config['expected_negative'], 'Negative contract drift')
    require(len(protocol['cases']) == 14 and len(protocol['composite_baseline_cases']) == 70 and len(protocol['source_manifest']) == 16, 'Frozen control/source count differs')
    require(len(bindings) == 144 and len(goldens) == 23, 'Protected binding/golden count differs')
    require(not set(bindings) & set(protocol['source_manifest']), 'Mutable/protected path overlap')
    records = {}
    for path, spec in (bindings | protocol['source_manifest']).items():
        body = git('show', source + ':' + path)
        blob = git('rev-parse', source + ':' + path).decode().strip()
        require(digest(body) == spec['sha256'], 'Git SHA-256 differs: ' + path)
        require('bytes' not in spec or len(body) == spec['bytes'], 'Git length differs: ' + path)
        require('git_blob' not in spec or blob == spec['git_blob'], 'Git blob differs: ' + path)
        records[path] = dict(sha256=digest(body), bytes=len(body), git_blob=blob)
        previous = subprocess.run(['git', '-C', str(repo), 'show', baseline + ':' + path], capture_output=True)
        if spec.get('baseline_absent'):
            require(previous.returncode != 0, 'Expected baseline absence: ' + path)
        elif 'baseline_sha256' in spec:
            require(previous.returncode == 0 and digest(previous.stdout) == spec['baseline_sha256'], 'Baseline SHA-256 differs: ' + path)
    for golden in goldens:
        require(digest(git('show', source + ':' + golden['path'])) == golden['sha256'], 'Golden changed: ' + golden['path'])
        require(git('show', source + ':' + golden['path']) == git('show', baseline + ':' + golden['path']), 'Golden baseline differs')
    deck = 'experiments/manual-transmission/control/v0.7.decklist.txt'
    require(git('rev-parse', source + ':' + deck).decode().strip() == 'c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89', 'Frozen deck blob changed')
    require(digest(git('show', source + ':' + deck)) == '2a9f47d2edcfbdb54a9ea15eadc719f85b36f74da9eb682233b85bd56f034da1', 'Frozen deck plaintext changed')
    diff = git('diff', '--name-status', baseline, source).decode()
    roots = ('rules-engine/src/', 'mtg-sdk/src/', 'mtg-sets/', 'ai/', 'gym/', 'game-server/', 'web-client/')
    changed = git('diff', '--name-only', baseline, source).decode().splitlines()
    require(set(path for path in changed if path.startswith(roots)) <= set(protocol['source_manifest']), 'Unbound production/test delta')
    fixture = 'rules-engine/src/test/kotlin/com/wingedsheep/engine/handlers/effects/token/TokenBatchEntryHistoryTest.kt'
    old_tests = [line for line in diff.splitlines() if '/src/test/' in line and not line.startswith('A\t')]
    require(old_tests == ['M\t' + fixture], 'Unapproved preexisting test change')
    r1 = config['predecessor_source']
    invalid = 'rules-engine/src/test/kotlin/com/wingedsheep/engine/scenarios/PausedTokenInvalidResponseTest.kt'
    r2_record = config['record'] + '/'
    r2_delta = git('diff', '--name-only', r1, source).decode().splitlines()
    allowed = {invalid, 'experiments/manual-transmission/research/WORK_EXECUTION.json'}
    require(all(path in allowed or path.startswith(r2_record) for path in r2_delta), 'Unreviewed R2 source/build/runtime delta')
    original_invalid = git('show', r1 + ':' + invalid)
    candidate_invalid = git('show', source + ':' + invalid)
    insertion = (b'            val counters = result.getEntity(token)!!.get<CountersComponent>()\n'
                 b'            (counters != null) shouldBe true\n')
    require(original_invalid == (ROOT / 'R1_InvalidResponse.kt').read_bytes(), 'R1 original fixture annex differs')
    require(candidate_invalid.count(insertion) == 1 and candidate_invalid.replace(insertion, b'', 1) == original_invalid,
            'R2 fixture change differs from exactly two strengthening lines')
    invalid_lines = candidate_invalid.decode().splitlines()
    require(invalid_lines[126].strip() == '(counters != null) shouldBe true', 'R2 PI02 first frame drift')
    require('(paused.pendingDecision is ChooseTargetsDecision) shouldBe true' in invalid_lines[135], 'R2 PI03 first frame drift')
    reuse = load_json('BASELINE_REUSE.json')
    require(reuse['receipt'] == config['failed_r1_receipt'], 'Failed receipt pin differs')
    require(protocol['expected_reused_baseline'] == config['expected_reused'], 'Reused56 count contract differs')
    require(protocol['expected_composite_baseline'] == config['expected_composite'], 'Composite70 count contract differs')
    require(len(protocol['cases']) == 14 and all(case['classname'].endswith('PausedTokenInvalidResponseTest') for case in protocol['cases']), 'Fresh negative scope differs')
    require({case['classname'] + '::' + case['name'] for case in protocol['cases']}.isdisjoint(
        {case['classname'] + '::' + case['name'] for case in reuse['reused_cases']}), 'Fresh/reused identity overlap')
    archived = 'experiments/manual-transmission/research/paused-token-transaction-draft-r1-20261023/' + fixture
    require(git('show', source + ':' + fixture) == git('show', source + ':' + archived), 'Original archived fixture differs')
    workflow = (ROOT / 'WORKFLOW.yml').read_text()
    require('SOURCE: ' + source in workflow and 'SOURCE_TREE: ' + config['source_tree'] in workflow, 'Workflow pin mismatch')
    require("github.run_number == '1' && github.run_attempt == '1'" in workflow, 'One-shot workflow guard differs')
    return dict(status='PASS', source=source, source_tree=config['source_tree'], validation=validation,
        validation_tree=config['validation_tree'], baseline=baseline, protected_bindings=len(bindings),
        protected_goldens=len(goldens), source_manifest_entries=len(protocol['source_manifest']), records=records,
        source_diff=diff, validation_diff=validation_delta, original_fixture_amendment_verified=True,
        source_review_sha256=digest((ROOT / 'SOURCE_REVIEW.json').read_bytes()))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo', required=True)
    parser.add_argument('--write', action='store_true')
    args = parser.parse_args()
    config = frozen_inputs()
    report = audit(args.repo, config)
    save_or_compare('GIT_SOURCE_AUDIT.json', report, args.write)
    print(json.dumps({key: value for key, value in report.items() if key not in {'records', 'source_diff'}}, indent=2))
