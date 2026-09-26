#!/usr/bin/env python3
"""Require actual deterministic cases for Pest's combined postboard support gate."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

SCENARIOS = {
    'KaerveksTorchScenarioTest': ('1993-1999', 4),
    'FlaringPainScenarioTest': ('2000-2002', 2),
    'AcornHarvestScenarioTest': ('2000-2002', 3),
    'SpreadingSeasScenarioTest': ('2008-2016', 2),
    'RelicOfProgenitusScenarioTest': ('2008-2016', 3),
    'FaerieMacabreScenarioTest': ('2008-2016', 3),
    'JackOLanternScenarioTest': ('2017-2022', 3),
}
DECKS = ('pest_control', 'mono_red_madness', 'grixis_affinity',
         'mono_blue_terror', 'monster_tron', 'spy_combo')


def inspect(root: Path, inventory: Path, hygiene: bool) -> dict:
    expected = {
        name: (root / f'mtg-sets/{era}/tests/build/test-results/test/'
               f'TEST-com.wingedsheep.engine.scenarios.{name}.xml', count)
        for name, (era, count) in SCENARIOS.items()
    }
    expected['PestControlTierOnePostboardSupportAuditTest'] = (
        root / 'gym/build/test-results/test/TEST-com.wingedsheep.gym.'
               'PestControlTierOnePostboardSupportAuditTest.xml', 1)
    if hygiene:
        for name, count in [('CardDefinitionSnapshotTest', 1), ('CardLintTest', 3),
                            ('FacadeBoundaryTest', 1)]:
            expected[name] = (root / 'mtg-sets/build/test-results/test/'
                             f'TEST-com.wingedsheep.mtg.sets.{name}.xml', count)
    failures, results = [], {}
    for name, (path, minimum) in expected.items():
        try:
            raw = path.read_bytes()
            suite = ET.fromstring(raw)
            cases = suite.findall('.//testcase')
            bad = [c for c in cases if any(c.find(tag) is not None
                   for tag in ('failure', 'error', 'skipped'))]
            count = int(suite.attrib['tests'])
            if count != len(cases) or count < minimum or bad:
                raise ValueError(f'{count} declared, {len(cases)} present, '
                                 f'{len(bad)} failed/error/skipped; minimum {minimum}')
            if any(int(suite.attrib.get(k, '0')) for k in ('failures', 'errors', 'skipped')):
                raise ValueError('nonzero suite failure/error/skip count')
            if any(c.attrib.get('classname', '').split('.')[-1] != name for c in cases):
                raise ValueError('testcase class does not match expected exact class')
            results[name] = {'cases': count, 'sha256': hashlib.sha256(raw).hexdigest(),
                             'path': str(path.relative_to(root))}
        except (OSError, ValueError, KeyError, ET.ParseError) as exc:
            failures.append(f'{name}: {exc}')
    try:
        rows = inventory.read_text().splitlines()
        for deck in DECKS:
            actual = [row for row in rows if row.startswith(deck + '=')]
            if actual != [deck + '=']:
                raise ValueError(f'{deck} inventory is absent, repeated or unresolved: {actual}')
    except (OSError, ValueError) as exc:
        failures.append(f'combined inventory: {exc}')
    changed = subprocess.check_output(['git', 'diff', '--name-only', 'HEAD'], cwd=root, text=True).splitlines()
    allowed_snapshot = 'mtg-sets/src/test/resources/snapshots/cards/SHM.json'
    if set(changed) - {allowed_snapshot}:
        failures.append(f'unexpected source modifications: {changed}')
    snapshot = root / allowed_snapshot
    snapshot_sha256 = hashlib.sha256(snapshot.read_bytes()).hexdigest() if snapshot.is_file() else None
    return {
        'schema': 'pest-postboard-support-closure-evidence-v1',
        'status': 'PASS' if not failures else 'FAIL_CLOSED',
        'source_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
        'source_tree': subprocess.check_output(['git', 'rev-parse', 'HEAD^{tree}'], cwd=root, text=True).strip(),
        'worktree_changes': changed,
        'tested_shm_snapshot_sha256': snapshot_sha256,
        'classes': results,
        'failures': failures,
        'scope': 'SEED_FREE_SUPPORT_ONLY',
        'official': {'new_seeds': 0, 'new_games': 0, 'new_actions': 0, 'new_outcomes': 0},
        'deck_changes': False,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument('--inventory', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--hygiene', action='store_true')
    args = parser.parse_args()
    result = inspect(args.root.resolve(), args.inventory, args.hygiene)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))
    return 0 if result['status'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
