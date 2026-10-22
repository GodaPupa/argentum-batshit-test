"""Exact two-card addition to R8. No engine, pilot, deck or protocol changes permitted."""
from pathlib import Path
import hashlib
import json
import subprocess

HERE = Path(__file__).resolve().parent
git = lambda *args: subprocess.check_output(['git', *args], text=True).strip()
protocol = json.loads((HERE / 'PROTOCOL.json').read_text())
manifest = json.loads((HERE / 'SOURCE_MANIFEST.json').read_text())
assert len(manifest) == 4
for path, digest in manifest.items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == digest, path
for card, record in protocol['card_cases'].items():
    assert git('hash-object', record['path']) == record['donor_blob'], card
expected = set(manifest) | {str(p.relative_to(Path.cwd())) for p in HERE.rglob('*') if p.is_file()}
expected.add('.github/workflows/manual-two-counters-r1-20261021.yml')
actual = set(git('diff', '--name-only', protocol['base'], 'HEAD').splitlines())
assert actual == expected, sorted(actual ^ expected)
assert git('hash-object', 'experiments/manual-transmission/control/v0.7.decklist.txt') == 'c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89'
assert git('hash-object', 'ai/src/main/kotlin/com/wingedsheep/ai/engine/AiProfile.kt') == '43bbad79273806f56703fad420e9f6a495d05309'
assert not protocol['gameplay_authorized'] and not protocol['seed_generation_authorized']
assert protocol['precision_reserved'] == 2
print('Two card definitions plus 13 unchanged donor cases; all R8 production/tests, frozen deck and policies unchanged')
