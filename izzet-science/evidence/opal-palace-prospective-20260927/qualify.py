"""One-shot, source-bound deterministic Opal Palace mechanic fixture collector."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
PREFIX = 'izzet-science/evidence/opal-palace-prospective-20260927'
SOURCE = 'c9f07997a11f225eaa0bb0d41ab7c964111a92a0'
TREE = 'd39b055d'  # Full tree is checked through git rev-parse below after source review.
TEST = 'mtg-sets/2008-2016/tests/src/test/kotlin/com/wingedsheep/engine/scenarios/OpalPalaceScenarioTest.kt'
MODULE = 'mtg-sets/2008-2016/tests'
CLASS = 'com.wingedsheep.engine.scenarios.OpalPalaceScenarioTest'
OUT = ROOT / 'build/reports/opal-palace-once'
BANK = [
    'first command-zone cast enters with one counter when paid with Palace mana',
    'a later hand cast uses only prior command-zone casts',
    'Palace mana spent on a noncommander does not add counters',
    'two Palace mana spent on a first command-zone cast add two counters',
    'ordinary mana spent on a commander does not add Palace counters',
    'colorless commander identity makes the second Palace ability add no mana',
    'countered Palace spell loses its rider before a hand recast with ordinary mana',
    'pending Palace counters survive serialization and apply once on resolution',
    'entry observers see Palace counters before checking entering power',
    'two Palace contributions are combined before an additive counter modifier',
]


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()


def source():
    head = git('rev-parse', 'HEAD')
    assert head == os.environ['EXPECTED_HEAD'], (head, os.environ['EXPECTED_HEAD'])
    assert not git('status', '--porcelain', '--untracked-files=all')
    assert git('rev-parse', 'HEAD^') == SOURCE
    assert git('rev-parse', SOURCE + '^{tree}').startswith(TREE)
    changed = git('diff', '--name-only', SOURCE, head).splitlines()
    assert changed == ['.github/workflows/opal-palace-composed-once.yml', PREFIX + '/qualify.py'], changed
    assert git('rev-parse', head + ':' + TEST) == git('rev-parse', SOURCE + ':' + TEST)
    names = re.findall(r'\btest\("([^"]+)"\)', (ROOT / TEST).read_text())
    assert names == BANK and len(set(names)) == 10, names
    return {'head': head, 'tree': git('rev-parse', 'HEAD^{tree}'),
            'test_blob': git('rev-parse', 'HEAD:' + TEST),
            'bank': BANK, 'official_games': 0, 'gameplay_authorized': False}


def write(name, value):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / name).write_text(json.dumps(value, indent=2, sort_keys=True) + '\n')


def bind():
    s = source()
    assert not list((ROOT / MODULE / 'build/test-results/test').glob('TEST-*.xml'))
    write('source-before.json', s)


def collect():
    errors = []
    rows = []
    try:
        after = source()
        write('source-after.json', after)
        assert after == json.loads((OUT / 'source-before.json').read_text())
        assert (OUT / 'exit.txt').read_text().strip() == '0'
        log = (OUT / 'tests.log').read_text()
        task = ':mtg-sets:2008-2016:tests:test'
        lines = [line for line in log.splitlines() if re.match(r'^> Task ' + re.escape(task) + r'(?:\s|$)', line)]
        assert lines and not any(re.search(r'FROM-CACHE|UP-TO-DATE|SKIPPED|NO-SOURCE|FAILED', line) for line in lines)
        assert 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log
        paths = list((ROOT / MODULE / 'build/test-results/test').glob('TEST-*.xml'))
        assert [p.name for p in paths] == ['TEST-' + CLASS + '.xml'], [p.name for p in paths]
        raw = paths[0].read_bytes()
        root = ET.fromstring(raw)
        cases = root.findall('testcase')
        assert root.get('name') == CLASS and int(root.get('tests')) == len(cases) == 10
        assert all(int(root.get(k, 0)) == 0 for k in ('failures', 'errors', 'skipped'))
        names = [c.get('name') for c in cases]
        assert len(set(names)) == 10 and set(names) == set(BANK)
        assert all(c.get('classname') == CLASS and not any(n.tag in ('failure','error','skipped') for n in c) for c in cases)
        rows = [{'class': CLASS, 'cases': 10, 'sha256': hashlib.sha256(raw).hexdigest()}]
        dest = OUT / 'xml' / paths[0].name
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(raw)
    except (AssertionError, OSError, KeyError, ValueError, ET.ParseError, subprocess.SubprocessError) as e:
        errors.append(type(e).__name__ + ': ' + str(e))
    write('receipt.json', {'status': 'INCOMPLETE' if errors else 'AWAIT_INDEPENDENT_ARTIFACT_REVIEW',
                           'errors': errors, 'rows': rows, 'official_games': 0, 'gameplay_authorized': False})
    return bool(errors)


if __name__ == '__main__':
    if sys.argv[1:] == ['bind']:
        bind()
    elif sys.argv[1:] == ['collect']:
        sys.exit(collect())
    else:
        raise SystemExit('Use bind or collect')
