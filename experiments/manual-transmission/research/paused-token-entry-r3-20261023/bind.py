"""Bind the immutable R3 source and protect original R2 PT02, all other tests and goldens."""
from pathlib import Path
import hashlib
import json
import os
import subprocess

root = Path('experiments/manual-transmission/research/paused-token-entry-r3-20261023')
old_root = Path('experiments/manual-transmission/research/paused-token-entry-r2-20261023')
protocol = json.loads((root / 'PROTOCOL.json').read_text())
old = json.loads((old_root / 'PROTOCOL.json').read_text())
assert protocol['schema'] == 'manual-paused-token-pt02-prospective-test-oracle-r3-v1'
assert protocol['status'] == 'SOURCE_PREPARED_VALIDATION_PIN_REQUIRED'
assert old['status'] == 'SOURCE_FROZEN_EXECUTION_REQUIRES_ACTIVITY_AND_WORKFLOW_PINS'
assert os.environ['BASELINE'] == old['baseline_source'] == protocol['frozen']['baseline']
assert os.environ.get('GITHUB_RUN_ATTEMPT', '1') == '1'
assert os.environ.get('GITHUB_RUN_NUMBER', '1') == '1'
git = lambda *a: subprocess.check_output(['git', *a], text=True).strip()
readgit = lambda commit, path: subprocess.check_output(['git', 'show', commit + ':' + path])
sha = lambda b: hashlib.sha256(b).hexdigest()

candidate = os.environ['SOURCE']
r2 = protocol['r2_failed']['source']
assert r2 == '06c27e1c31efd974300028c8ce5c1ceeac703280'
assert git('rev-parse', 'HEAD^') == candidate
assert git('rev-parse', candidate + '^{tree}') == os.environ['SOURCE_TREE']
assert git('diff', '--name-only', candidate, 'HEAD') == os.environ['WORKFLOW']
assert git('merge-base', r2, candidate) == r2

testpath = protocol['original_test']['path']
annexpath = protocol['original_test']['archived_path']
old_test = readgit(r2, testpath)
new_test = readgit(candidate, testpath)
assert readgit(candidate, annexpath) == old_test
assert Path(annexpath).read_bytes() == old_test
assert sha(old_test) == protocol['original_test']['sha256']
assert git('rev-parse', r2 + ':' + testpath) == protocol['original_test']['git_blob']
assert git('rev-parse', candidate + ':' + testpath) == protocol['corrected_test']['approved_git_blob']
assert Path(testpath).read_bytes() == new_test

old_import = b'import com.wingedsheep.engine.state.components.identity.TokenComponent\n'
new_import = (old_import +
 b'import com.wingedsheep.engine.state.components.identity.CardComponent\n'
 b'import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent\n'
 b'import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent\n')
old_assert = (b'            d.stackSize shouldBe 0 // PT02 first baseline mismatch\n'
 b'            (d.pendingDecision is ChooseColorDecision) shouldBe true\n')
new_assert = (b'            // Prospective assertion-oracle correction: a paused resolving instant remains\n'
 b'            // a spell on the stack, but no ETB trigger may be stacked before all choices.\n'
 b'            val stackObjects = d.state.stack.mapNotNull { d.state.getEntity(it) }\n'
 b'            stackObjects.count { it.has<TriggeredAbilityOnStackComponent>() } shouldBe 0\n'
 b'            tokens(d).size shouldBe 0\n'
 b'            if (sourceCopy) {\n'
 b'                d.stackSize shouldBe 0\n'
 b'            } else {\n'
 b'                d.stackSize shouldBe 1\n'
 b'                stackObjects.single().has<SpellOnStackComponent>() shouldBe true\n'
 b'                stackObjects.single().get<CardComponent>()?.name shouldBe "Paused Token Copy Action"\n'
 b'            }\n'
 b'            (d.pendingDecision is ChooseColorDecision) shouldBe true\n')
assert old_test.count(old_import) == 1 and old_test.count(old_assert) == 1
assert new_test == old_test.replace(old_import, new_import, 1).replace(old_assert, new_assert, 1)
assert new_test.count(b'test("PT02 entry triggers remain unstacked') == old_test.count(b'test("PT02 entry triggers remain unstacked') == 1
assert new_test.count(b'        test("') == old_test.count(b'        test("') == 6

allowed = {testpath, annexpath, str(root / 'PROTOCOL.json'), str(root / 'bind.py'),
 str(root / 'prepare_stage.py'), str(root / 'collect.py'), str(root / 'check_assay.py')}
changed = set(git('diff', '--name-only', r2, candidate).splitlines())
assert changed == allowed, {'added': sorted(changed - allowed), 'missing': sorted(allowed - changed)}
for path, spec in old['source_manifest'].items():
    if path == testpath:
        continue
    body = Path(path).read_bytes()
    assert sha(body) == spec['sha256'], path
    assert readgit(r2, path) == body, path
bindings = json.loads((old_root / 'BINDINGS.json').read_text())
assert len(bindings) == protocol['qualification']['protected_bindings'] == 144
for path, spec in bindings.items():
    assert sha(Path(path).read_bytes()) == spec['sha256'], path
golds = json.loads(Path('experiments/manual-transmission/research/ordinary-build-r1-20261022/GOLDENS.json').read_text())
assert len(golds) == protocol['qualification']['protected_goldens'] == 23
for spec in golds:
    assert sha(Path(spec['path']).read_bytes()) == spec['sha256'], spec['path']
deck = protocol['frozen']['manual_blob']
assert git('rev-parse', candidate + ':experiments/manual-transmission/control/v0.7.decklist.txt') == deck
assert sha(Path('experiments/manual-transmission/control/v0.7.decklist.txt').read_bytes()) == protocol['frozen']['manual_sha256']
assert git('rev-parse', 'refs/remotes/origin/main') == protocol['frozen']['main']
assert not git('status', '--porcelain', '--untracked-files=no'), 'Tracked tree unexpectedly changed'

out = Path('paused-token-r3-evidence')
out.mkdir(exist_ok=True)
record = {'status': 'SOURCE_AND_PROTECTIONS_BOUND', 'source': candidate,
 'tree': git('rev-parse', candidate + '^{tree}'), 'validation': git('rev-parse', 'HEAD'),
 'baseline': old['baseline_source'], 'prior_failed_r2_source': r2,
 'pt02_original_sha256': sha(old_test), 'pt02_amended_sha256': sha(new_test),
 'changed_paths': sorted(changed), 'protected_bindings': len(bindings), 'goldens': len(golds),
 'negative_evidence': 'R2 original70 preserved; four PT02 historical controls retired from prospective R3',
 'qualified_negative_historical_count': 66, 'official_games': 0, 'official_seeds': 0,
 'precision_reserved': 2, 'competitive_admission': False}
(out / 'SOURCE_BINDINGS.json').write_text(json.dumps(record, indent=2) + '\n')
print(json.dumps(record, indent=2))
