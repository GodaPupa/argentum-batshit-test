#!/usr/bin/env python3
"""Static source inventory only: never proves registry loading or card behavior."""
import collections
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def canonical(name):
    return name.split(' // ')[0].replace('’', "'")


def main():
    definitions = collections.defaultdict(list)
    for path in sorted((ROOT / 'mtg-sets').glob('**/src/main/**/*.kt')):
        for name in re.findall(r'\b(?:card|basicLand)\("([^"\n]+)"', path.read_text()):
            definitions[name].append(str(path.relative_to(ROOT)))
    deckpath = ROOT / 'experiments/manual-transmission/control/v0.7.decklist.txt'
    names = []
    for line in deckpath.read_text().splitlines():
        match = re.fullmatch(r'(\d+) (.+)', line)
        if match:
            names.extend([match[2]] * int(match[1]))

    def inventory(cards):
        return {
            'card_count': len(cards),
            'unique_names': len(set(cards)),
            'missing_source_candidates': sorted({n for n in cards if canonical(n) not in definitions}),
            'name_resolution_required': sorted({n for n in cards if n not in definitions and canonical(n) in definitions}),
            'cards': [{'name': n, 'quantity': cards.count(n),
                       'candidate_paths': definitions.get(canonical(n), [])}
                      for n in dict.fromkeys(cards)],
        }

    manual = inventory(names)
    manual['deck_file_sha256'] = hashlib.sha256(deckpath.read_bytes()).hexdigest()
    manual['nonbasic_duplicates'] = [n for n, q in collections.Counter(names).items()
                                     if q > 1 and n not in {'Forest', 'Island', 'Mountain'}]
    opponents = {}
    for axis in ['kinnan', 'magda', 'shorikai']:
        source = json.loads((ROOT / f'experiments/manual-transmission/opponents/{axis}/source-freeze.json').read_text())
        opponents[axis] = inventory([source['opponent']['commander']] +
                                    source.get('mainboard', source.get('mainboard_expanded', [])))
    print(json.dumps({
        'scope': 'STATIC_SOURCE_CANDIDATES_ONLY_NOT_RUNTIME_OR_RULES_VALIDATION',
        'source_commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'manual': manual, 'opponents': opponents,
        'games_initialized': 0, 'games_completed': 0,
    }, indent=2, ensure_ascii=False))


if __name__ == '__main__':
    main()
