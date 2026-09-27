"""One-shot, source-bound Monster actor typed-search receiving collector."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
PREFIX = 'docs/experiments/pest-control/monster-actor-component'
SOURCE = 'b081c56fd9dfb165408ebe121a3fbabea29b0b62'
PARENT = '89cc35064dd305fe695312a16bf9a77b73b3cc1d'
TREE = '186c15e7'  # Source review binds full parent commit; tree prefix is a second guard.
TEST = 'gym/src/test/kotlin/com/wingedsheep/gym/pest/PestMonsterTronActorDecisionsTest.kt'
MODULE = 'gym'
CLASS = 'com.wingedsheep.gym.pest.PestMonsterTronActorDecisionsTest'
OUT = ROOT / 'build/reports/pest-monster-actor-once'
BANK = [
    'MC01 actual Expedition Map resolves the unique missing-piece response',
    'MC02 normal Crop Rotation pays an explicit sacrifice and resolves its actual search',
    'MC03 real Cascade into Crop Rotation offers and accepts the unique non-Tron sacrifice',
    'MC04 actual Ancient Stirrings uses the offered authorized group and retains its reorder question',
    'MC05 actual Bojuka Bog targets and exiles the public opponent graveyard',
    'MC06 offered Stirrings group without a Tron land preserves the legacy null defer',
    'MC07 unavailable hand and future-library changes cannot alter Bog input or response',
    'MC08 a legitimate own-hand Tron piece changes the missing-piece choice',
    'MC09 stale actor epoch and edited policy-state bindings are rejected before a choice',
    'MC10 verified Map search preserves physical offered order and exact legacy choice',
]


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()


def source():
    head = git('rev-parse', 'HEAD')
    assert head == os.environ['EXPECTED_HEAD'], (head, os.environ['EXPECTED_HEAD'])
    assert not git('status', '--porcelain', '--untracked-files=all')
    assert git('rev-parse', 'HEAD^') == PARENT
    assert git('rev-parse', SOURCE + '^{tree}').startswith(TREE)
    changed = git('diff', '--name-only', SOURCE, head).splitlines()
    assert changed == ['.github/workflows/pest-monster-actor-typed-search-once.yml', PREFIX + '/qualify_typed_search.py'], changed
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
        task = ':gym:test'
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
