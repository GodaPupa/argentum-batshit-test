"""Only the declared catalog-test revision may differ from the exact R7 production tree."""
from pathlib import Path
import hashlib
import json
import subprocess

HERE = Path(__file__).resolve().parent
git = lambda *args: subprocess.check_output(['git', *args], text=True).strip()
protocol = json.loads((HERE / 'PROTOCOL.json').read_text())
manifest = json.loads((HERE / 'SOURCE_MANIFEST.json').read_text())
r6 = HERE.parent / 'combined-r6-20261021'
r7 = HERE.parent / 'combined-r7-20261021'
changed_test = 'gym/src/test/kotlin/com/wingedsheep/gym/ManualBattlebondSpireCatalogTest.kt'
assert set(manifest) == {changed_test}
for path, digest in manifest.items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == digest, path
for path, digest in json.loads((r7 / 'SOURCE_MANIFEST.json').read_text()).items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == digest, path
old_tests = json.loads((r6 / 'ORIGINAL_TESTS_SHA256.json').read_text())
for path, digest in old_tests.items():
    data = (HERE / 'ORIGINAL_ManualBattlebondSpireCatalogTest.kt.txt').read_bytes() if path == changed_test else Path(path).read_bytes()
    assert hashlib.sha256(data).hexdigest() == digest, path
for path, entry in json.loads((r6 / 'RECEIVED_FILES.json').read_text()).items():
    assert git('hash-object', path) == entry['blob'], path
expected = {changed_test, '.github/workflows/manual-combined-r8-20261021.yml'}
expected |= {str(p.relative_to(Path.cwd())) for p in HERE.rglob('*') if p.is_file()}
actual = set(git('diff', '--name-only', protocol['parent'], 'HEAD').splitlines())
assert actual == expected, sorted(actual ^ expected)
assert git('hash-object', 'experiments/manual-transmission/control/v0.7.decklist.txt') == 'c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89'
assert git('hash-object', 'ai/src/main/kotlin/com/wingedsheep/ai/engine/AiProfile.kt') == '43bbad79273806f56703fad420e9f6a495d05309'
exclusion = protocol['intentional_ai_exclusion']
assert git('hash-object', exclusion['source']) == exclusion['blob']
assert not protocol['gameplay_authorized'] and not protocol['seed_generation_authorized']
assert protocol['precision_reserved'] == 2
print('R7 production unchanged; 37 original test files unchanged; one catalog control revised and one added; all original R7 evidence retained; no games')
