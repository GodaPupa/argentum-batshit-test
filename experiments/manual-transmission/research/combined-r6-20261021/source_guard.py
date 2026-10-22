"""Bind received blobs, original case bytes, and untouched R3 source outside the declared delta."""
from pathlib import Path
import hashlib
import json
import subprocess

HERE = Path(__file__).resolve().parent
protocol = json.loads((HERE/'PROTOCOL.json').read_text())
received = json.loads((HERE/'RECEIVED_FILES.json').read_text())
git = lambda *a: subprocess.check_output(['git', *a], text=True).strip()
for path, identity in received.items():
    assert git('hash-object', path) == identity['blob'], path
for path, digest in json.loads((HERE/'ORIGINAL_TESTS_SHA256.json').read_text()).items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == digest, path
expected = set(received) | {str(p.relative_to(Path.cwd())) for p in HERE.iterdir() if p.is_file()}
expected.add('.github/workflows/manual-combined-r6-20261021.yml')
actual = set(git('diff', '--name-only', protocol['base'], 'HEAD').splitlines())
assert actual == expected, sorted(actual ^ expected)
assert git('hash-object', 'experiments/manual-transmission/control/v0.7.decklist.txt') == 'c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89'
assert protocol['gameplay_authorized'] is False and protocol['seed_generation_authorized'] is False
assert protocol['precision_reserved'] == 2
print('Exact received source and original tests verified; all other R3 paths unchanged; no games')
