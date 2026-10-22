"""Read-only receipt audit of the retained checkout-depth failure; no test dispatch."""
from pathlib import Path
import hashlib, json, zipfile
p = Path(__file__).resolve().parent
m = json.loads((p / 'ARTIFACT.json').read_text())
b = (p / 'original.zip').read_bytes()
assert len(b) == m['size_in_bytes'] == 5132
assert hashlib.sha256(b).hexdigest() == m['digest'][7:] == '645760d7aec499317c01ec88061eba750684b649f7c11837e196e70aaadd5f3a'
z = zipfile.ZipFile(p / 'original.zip')
assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist())) == 5
manifest = {}
for line in z.read('SHA256SUMS').decode().splitlines():
    h, n = line.split('  ', 1)
    assert hashlib.sha256(z.read(n)).hexdigest() == h
    manifest[n] = h
assert set(z.namelist()) == set(manifest) | {'SHA256SUMS'}
assert not any(n.endswith('.xml') or n.endswith('exit.txt') or n.endswith('gradle.log') for n in z.namelist())
binding = json.loads(z.read('binding-fixed.json'))
assert binding['source'] == '8671acc259daec0a2740e32e97d588bc1572b8fc'
assert binding['source_tree'] == '099a9885107bdf67708c2848a4c091c202c62e08'
assert binding['validation'] == '8933ce492e276e47ffa317a7b3b259a9a7fc5f43'
assert binding['baseline'] == '7a2fbbbf8118ae8c6410c9e234f9cc866bdb926a'
assert binding['source_manifest'] == json.loads((p / 'PROTOCOL.json').read_text())['source_manifest']
assert binding['bindings'] == json.loads((p / 'BINDINGS.json').read_text())
assert binding['goldens_verified'] == 23 and len(binding['bindings']) == 23
assert binding['official_games'] == binding['official_seeds'] == 0 and binding['precision_reserved'] == 2
summary = json.loads(z.read('negative/SUMMARY.json'))
assert all(v == 0 for v in summary['totals'].values())
run = json.loads((p / 'RUN.json').read_text())
assert run['id'] == 39847525560 and run['head_sha'] == binding['validation']
assert run['status'] == 'completed' and run['conclusion'] == 'failure' and run['run_attempt'] == 1
assert len(run['jobs']) == 1 and run['jobs'][0]['id'] == 120064630866
steps = run['jobs'][0]['steps']
assert next(s for s in steps if s['name'] == 'Run24 baseline controls against qualified snapshot source')['conclusion'] == 'failure'
assert next(s for s in steps if s['name'] == 'Restore and rebind exactly the candidate source')['conclusion'] == 'success'
assert next(s for s in steps if s['name'] == 'Recheck all source and safeguards')['conclusion'] == 'success'
all_runs = json.loads((p / 'ALL_RUNS.json').read_text())
assert all_runs['total_count'] == len(all_runs['workflow_runs']) == 1
assert all_runs['workflow_runs'][0]['id'] == run['id']
assert json.loads((p / 'DUPLICATE_RUNS.json').read_text()) == []
log = (p / 'original-job.log').read_text()
assert 'fatal: unable to read tree (7a2fbbbf8118ae8c6410c9e234f9cc866bdb926a)' in log
report = {'status': 'CHECKOUT_SETUP_FAILED_UNTESTED_UNADOPTED', 'source': binding['source'], 'validation': binding['validation'], 'run': run['id'], 'artifact': m['id'], 'zip_bytes': len(b), 'zip_sha256': hashlib.sha256(b).hexdigest(), 'manifest_entries': len(manifest), 'tests_executed': 0, 'negative_evidence': False, 'skipped_steps': [s['name'] for s in steps if s['conclusion'] == 'skipped'], 'reason': 'fetch-depth16 excluded the immutable qualified baseline tree; baseline restoration failed before compilation/test execution.', 'repair': 'New guarded validation revision with fetch-depth0 and immutable baseline-tree preflight, same repaired source and all24 unchanged tests.', 'official_games': 0, 'official_seeds': 0, 'precision_reserved': 2, 'competitive_admission': False}
import sys
if '--write' in sys.argv:
    (p / 'INDEPENDENT_AUDIT.json').write_text(json.dumps(report, indent=2) + '\n')
else:
    assert json.loads((p / 'INDEPENDENT_AUDIT.json').read_text()) == report
print(json.dumps(report, indent=2))
