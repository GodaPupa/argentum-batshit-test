"""Bind exact R4 source and preserve R1/R2/R3 failed evidence and protected baselines."""
from pathlib import Path
import hashlib, json, os, subprocess

record = Path('experiments/manual-transmission/research/paused-token-entry-r4-20261023')
prior = Path('experiments/manual-transmission/research/paused-token-entry-r2-20261023')
p = json.loads((record/'PROTOCOL.json').read_text())
assert p['status'] == 'R4_SOURCE_FROZEN_ACCEPTANCE_PENDING'
assert os.getenv('GITHUB_RUN_ATTEMPT','1') == '1'
assert os.getenv('GITHUB_RUN_NUMBER','1') == '1'
git = lambda *args: subprocess.check_output(['git',*args],text=True).strip()
obj = lambda commit,path: subprocess.check_output(['git','show',commit+':'+path])
sha = lambda body: hashlib.sha256(body).hexdigest()
source = os.environ['SOURCE']
r3 = p['prior_failed']['r3']['source']
baseline = p['qualified_baseline']['source']
assert os.environ['BASELINE'] == baseline
assert git('rev-parse','HEAD^') == source
assert git('rev-parse',source+'^{tree}') == os.environ['SOURCE_TREE']
assert git('diff','--name-only',source,'HEAD') == os.environ['WORKFLOW']
assert git('merge-base',r3,source) == r3
assert git('rev-parse','refs/remotes/origin/main') == p['safeguards']['main']

change_path = p['code_scope']['changed_production_path']
changed = git('diff','--name-only',r3,source).splitlines()
assert changed and set(changed) == {change_path} | {
    f for f in changed if f.startswith(str(record)+'/')}, changed
assert git('rev-parse',r3+':'+change_path) == p['code_scope']['original_blob']
assert git('rev-parse',source+':'+change_path) == p['code_scope']['candidate_blob']
prior_handler = obj(r3,change_path)
current_handler = obj(source,change_path)
assert Path(change_path).read_bytes() == current_handler
anchor = b'                val preSbaTriggers = triggerDetector.detectTriggers(result.state, eventsNeedingTriggers)\n\n'
addition = b'''                // A captured ETB observation is not "processed" merely because its original
                // instruction completed. Stamp the *exact occurrence* only after detection
                // consumed it here; later scans must not queue that same trigger a second
                // time. Use identity, not equality: two byte-identical ZoneChangeEvents
                // may represent two distinct authorized triggering occurrences.
                val consumedObservedEntries = java.util.Collections.newSetFromMap(
                    java.util.IdentityHashMap<GameEvent, Boolean>()
                )
                for (event in eventsNeedingTriggers) {
                    if (event is ZoneChangeEvent && event.toZone == com.wingedsheep.sdk.core.Zone.BATTLEFIELD &&
                        event.observedEntryTriggers != null && !event.entryTriggersAlreadyProcessed
                    ) consumedObservedEntries.add(event)
                }
                val publishedEvents = result.events.map { event ->
                    if (event is ZoneChangeEvent && event in consumedObservedEntries)
                        event.copy(entryTriggersAlreadyProcessed = true)
                    else event
                }

'''
original_sba = b'                        listOf(submittedEvent) + result.events + sbaResult.events\n'
repaired_sba = b'                        listOf(submittedEvent) + publishedEvents + sbaResult.events\n'
original_combined = b'                var combinedEvents = listOf(submittedEvent) + result.events + sbaResult.events'
repaired_combined = b'                var combinedEvents = listOf(submittedEvent) + publishedEvents + sbaResult.events'
assert prior_handler.count(anchor) == prior_handler.count(original_sba) == prior_handler.count(original_combined) == 1
exact = prior_handler.replace(anchor,anchor+addition,1).replace(original_sba,repaired_sba,1).replace(original_combined,repaired_combined,1)
assert current_handler == exact, 'Production delta does not match exact reviewed provenance repair'

# The only original test semantic change was the separately authorized R3 PT02 correction,
# which is byte-exact throughout R4. Four EP original assertions are unchanged since qualified carrier.
tests = p['original_assertions_protected']
for key,path in [('entry_provenance_git_blob',tests['entry_provenance_file']),('PT02_approved_R3_blob',tests['PT02_test_file'])]:
    assert git('rev-parse',source+':'+path) == tests[key], path
    assert Path(path).read_bytes() == obj(r3,path), path
assert git('rev-parse',baseline+':'+tests['entry_provenance_file']) == tests['entry_provenance_git_blob']
assert obj(source,tests['R2_original_annex']) == obj(p['prior_failed']['r2']['source'],tests['PT02_test_file'])
previous = json.loads((prior/'PROTOCOL.json').read_text())
for path,spec in previous['source_manifest'].items():
    if path == tests['PT02_test_file']: continue
    assert sha(Path(path).read_bytes()) == spec['sha256'], path

bank=json.loads((record/'EXPECTED_CASES.json').read_text())
previous_bank=json.loads((prior/'EXPECTED_CASES.json').read_text())
assert len(bank) == 42 and sum(len(v) for v in bank.values()) == p['qualification']['focused_passed'] == 690
assert all(bank[k] == v for k,v in previous_bank.items())
assert len(bank['EntryChoiceEventProvenanceTest']) == 4
assert all(s.endswith('EntryChoiceEventProvenanceTest::'+t) for s,t in zip(bank['EntryChoiceEventProvenanceTest'],[
    'EP01 a real as-enters token publishes its entry and resolves exactly one self ETB trigger',
    'EP02 entry-time observer survives its automatic-tail removal and the distinct leave trigger is not suppressed',
    'EP03 serialized pending token tail and repeated answers preserve exact entry records, state and two ETB effects',
    'EP04 serialized provenance skips only the marked occurrence and preserves identical unmarked occurrences',
]))
bindings=json.loads((prior/'BINDINGS.json').read_text())
assert len(bindings) == p['qualification']['protected_bindings'] == 144
for path,spec in bindings.items():
    assert sha(Path(path).read_bytes()) == spec['sha256'], path
golds=json.loads(Path('experiments/manual-transmission/research/ordinary-build-r1-20261022/GOLDENS.json').read_text())
assert len(golds) == p['qualification']['protected_goldens'] == 23
for g in golds:
    assert sha(Path(g['path']).read_bytes()) == g['sha256'], g['path']
deck_path='experiments/manual-transmission/control/v0.7.decklist.txt'
assert git('rev-parse',source+':'+deck_path) == p['safeguards']['frozen_deck_blob']
assert sha(Path(deck_path).read_bytes()) == p['safeguards']['frozen_deck_sha256']
assert not git('status','--porcelain','--untracked-files=no')

out=Path('paused-token-r4-evidence')
out.mkdir(exist_ok=True)
receipt=dict(status='R4_EXACT_SOURCE_AND_PROTECTIONS_BOUND',source=source,
    tree=git('rev-parse',source+'^{tree}'),validation=git('rev-parse','HEAD'),
    prior_failed_r3=r3,prior_artifact=p['prior_failed']['r3']['artifact_id'],
    reviewed_changed_paths=changed,protected_bindings=len(bindings),goldens=len(golds),
    original_provenance_assertions_unchanged=True,original_R3_failed=True,
    focused_cases=690,baseline=p['qualified_baseline']['source'],
    phase2_games=0,official_seeds=0,precision_reserved=2,competitive_admission=False)
(out/'SOURCE_BINDINGS.json').write_text(json.dumps(receipt,indent=2)+'\n')
print(json.dumps(receipt,indent=2))
