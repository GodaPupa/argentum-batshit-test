"""Bind this validation-only child to the reviewed source and immutable safeguards."""
import hashlib
import json
import os
from pathlib import Path
import subprocess

def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()

record = Path(os.environ['RECORD'])
out = Path('repair-evidence')
out.mkdir(exist_ok=True)
protocol = json.loads((record / 'PROTOCOL.json').read_text())
source = os.environ['SOURCE']
assert git('rev-parse', 'HEAD^') == source
assert git('rev-parse', 'HEAD^^') == protocol['parent_source']
assert git('diff', '--name-only', 'HEAD^', 'HEAD') == protocol['validation_workflow']
changed = git('diff', '--name-only', protocol['repair_baseline'], 'HEAD^').splitlines()
assert set(p for p in changed if not p.startswith(str(record) + '/')) == set(protocol['source_changes'])
assert not git('status', '--porcelain', '--untracked-files=no')
bindings = json.loads((record / 'BINDINGS.json').read_text())
for path, expected in bindings.items():
    assert git('hash-object', path) == expected['git_blob'], path
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == expected['sha256'], path
goldens = json.loads(Path('experiments/manual-transmission/research/ordinary-build-r1-20261022/GOLDENS.json').read_text())
for entry in goldens:
    assert hashlib.sha256(Path(entry['path']).read_bytes()).hexdigest() == entry['sha256'], entry['path']
receipt = {'source': source, 'validation': git('rev-parse', 'HEAD'),
           'source_tree': git('rev-parse', source + '^{tree}'), 'bindings': bindings,
           'goldens_verified': len(goldens), 'official_games': 0, 'official_seeds': 0,
           'precision_reserved': 2}
(out / 'source-binding.json').write_text(json.dumps(receipt, indent=2) + '\n')
print(json.dumps({'source': source, 'validation': receipt['validation'],
                  'goldens_verified': len(goldens), 'bindings_verified': len(bindings)}))
