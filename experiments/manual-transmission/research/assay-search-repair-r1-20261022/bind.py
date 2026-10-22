"""Bind an isolated receiving execution to its source, unchanged assertions and frozen deck."""
import hashlib
import json
import os
from pathlib import Path
import subprocess

def git(*args):
    return subprocess.check_output(['git',*args],text=True).strip()

out = Path('repair-evidence')
out.mkdir(exist_ok=True)
source = os.environ['SOURCE']
assert git('rev-parse','HEAD^') == source
assert git('diff','--name-only','HEAD^','HEAD') == '.github/workflows/manual-assay-search-repair-r1-20261022.yml'
record = Path(os.environ['RECORD'])
protocol = json.loads((record/'PROTOCOL.json').read_text())
for path, blob in protocol['original_test_blobs'].items():
    assert git('hash-object',path) == blob
frozen = 'experiments/manual-transmission/control/v0.7.decklist.txt'
assert git('hash-object',frozen) == protocol['safeguards']['frozen_v07_blob']
goldens = json.loads(Path('experiments/manual-transmission/research/ordinary-build-r1-20261022/GOLDENS.json').read_text())
for item in goldens:
    assert hashlib.sha256(Path(item['path']).read_bytes()).hexdigest() == item['sha256']
for name, ref in [('source-sha.txt','HEAD^'),('validation-sha.txt','HEAD'),('tree.txt','HEAD^{tree}')]:
    (out/name).write_text(git('rev-parse',ref)+'\n')
receipt = {'source':source,'validation':git('rev-parse','HEAD'),'stage':os.environ['STAGE'],'goldens_verified':len(goldens),'original_test_blobs':protocol['original_test_blobs'],'frozen_deck_blob':protocol['safeguards']['frozen_v07_blob'],'official_games':0,'official_seeds_generated':False}
(out/'source-binding.json').write_text(json.dumps(receipt,indent=2)+'\n')
print(json.dumps(receipt))
