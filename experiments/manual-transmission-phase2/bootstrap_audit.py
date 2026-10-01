#!/usr/bin/env python3
"""Read-only Phase 2 inventory. Never imports pilots or creates an actual-deck game."""
from __future__ import annotations
import argparse
import hashlib
import json
import re
import subprocess
import unicodedata
import zipfile
from pathlib import Path

PHASE1 = 'e7d37e2ea0f18dfa8d6b0fff68ef816b59555d2d'
HARDWARE = '6c28f0629d8ff0a859784f8c7dc0a47054b298d17c6b8e041a4a0a1d8744f111'
DECK = 'experiments/manual-transmission/control/v0.7.decklist.txt'
DECK_BLOB = 'c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89'
FINAL = 'experiments/manual-transmission/results/FINAL_CONCLUSION_2026_09_24.md'
FINAL_BLOB = 'dbf081b0c6e205c65750866adcb6f7d43f532caf'
AXES = ('blue-farm', 'rogsi', 'kinnan', 'sisay', 'magda', 'hashaton', 'shorikai')


def git(root: Path, *args: str) -> str:
    return subprocess.check_output(['git', '-C', str(root), *args], text=True).strip()


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def write(path: Path, obj: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, sort_keys=True, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def source_cards(obj: dict, axis: str) -> tuple[list[str], list[str], dict[str, str]]:
    """Accept only the two observed, explicit Phase 1 schemas; preserve every copy."""
    opp = obj['opponent']
    names = opp.get('commanders') or [opp['commander']]
    keys = [key for key in ('mainboard', 'mainboard_expanded') if key in obj]
    if len(keys) != 1:
        raise ValueError(f'Ambiguous or missing mainboard schema: {axis}: {keys}')
    cards = obj[keys[0]]
    if not isinstance(cards, list) or not all(isinstance(x, str) for x in cards):
        raise ValueError(f'Unsupported source card schema: {axis}')
    if len(names) + len(cards) != 100:
        raise ValueError(f'Wrong exact card count: {axis}')
    main = ''.join(f'1 {unicodedata.normalize("NFC", name)}\n' for name in sorted(cards))
    header = 'COMMANDERS\n' if len(names) == 2 else 'COMMANDER\n'
    complete = header + ''.join(f'1 {name}\n' for name in names) + 'MAINBOARD\n' + main
    main_key = f'mainboard_{len(cards)}_sha256'
    full_key = 'commanders_plus_98_sha256' if len(names) == 2 else 'commander_plus_99_sha256'
    calculated = {main_key: sha(main.encode('utf-8')), full_key: sha(complete.encode('utf-8'))}
    for key, digest in calculated.items():
        if obj['digests'].get(key) != digest:
            raise ValueError(f'Source digest mismatch {axis} {key}: {digest}')
    return names, cards, calculated


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--phase1', type=Path, required=True)
    ap.add_argument('--out', type=Path, required=True)
    ap.add_argument('--bundle', action='store_true')
    args = ap.parse_args()
    root = Path(__file__).resolve().parents[2]
    prior = args.phase1.resolve()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    if git(prior, 'rev-parse', 'HEAD') != PHASE1:
        raise ValueError('Wrong Phase 1 checkout')
    for path, expected in ((DECK, DECK_BLOB), (FINAL, FINAL_BLOB)):
        if git(prior, 'rev-parse', f'HEAD:{path}') != expected:
            raise ValueError(f'Phase 1 blob mismatch: {path}')
        if (root / path).read_bytes() != (prior / path).read_bytes():
            raise ValueError(f'Frozen Phase 1 file changed on Phase 2 branch: {path}')
    deck_bytes = (prior / DECK).read_bytes()
    text = deck_bytes.decode('utf-8')
    if HARDWARE not in text:
        raise ValueError('Inherited hardware identifier missing')
    if 'PASS_BOUNDED_POLICY_ELASTICITY_KEEP_V07' not in (prior / FINAL).read_text():
        raise ValueError('Phase 1 conclusion mismatch')
    commanders: list[str] = []
    mainboard: list[str] = []
    section = ''
    for line in text.splitlines():
        if line in ('Commander', 'Creatures', 'Noncreatures', 'Lands'):
            section = line
        match = re.fullmatch(r'(\d+) (.+)', line)
        if match:
            count, name = int(match[1]), match[2]
            (commanders if section == 'Commander' else mainboard).extend([name] * count)
    if len(commanders) != 1 or len(mainboard) != 99:
        raise ValueError('Frozen v0.7 must contain one commander plus 99')
    decks = [{'id': 'manual-transmission-v07', 'commanders': commanders, 'mainboard': mainboard}]
    sources = {'control': {'git_blob_sha1': DECK_BLOB, 'plaintext_sha256': sha(deck_bytes), 'inherited_identifier': HARDWARE}}
    admission_bytes = (root / 'experiments/manual-transmission-phase2/opponent-admission-r1.json').read_bytes()
    admission = json.loads(admission_bytes)
    if admission['phase1_source_commit'] != PHASE1:
        raise ValueError('Opponent admission uses a different Phase 1 source')
    admitted = {entry['id']: entry for entry in admission['decks']}
    if len(admission['decks']) != len(AXES) or set(admitted) != set(AXES):
        raise ValueError('Opponent admission does not contain exactly the seven frozen identities')
    for axis in AXES:
        path = prior / f'experiments/manual-transmission/opponents/{axis}/source-freeze.json'
        raw = path.read_bytes()
        obj = json.loads(raw)
        names, cards, calculated = source_cards(obj, axis)
        decks.append({'id': axis, 'commanders': names, 'mainboard': cards})
        complete_key = 'commanders_plus_98_sha256' if len(names) == 2 else 'commander_plus_99_sha256'
        if (admitted[axis]['complete_100_sha256'] != calculated[complete_key]
                or admitted[axis]['commanders'] != names
                or admitted[axis]['disposition'] != 'ADMITTED_EXACT_IDENTITY'):
            raise ValueError(f'Exact opponent admission mismatch: {axis}')
        sources[axis] = {'source_file_sha256': sha(raw), 'source_metadata': obj['opponent'], 'verified_digests': calculated, 'phase2_admission': 'ADMITTED_EXACT_IDENTITY', 'rules_and_pilot_qualification': 'PENDING'}
    request = {'schema': 'mt-phase2-registry-requests-v1', 'phase1_commit': PHASE1, 'decks': decks}
    write(out / 'registry-requests.json', request)
    tracked = git(root, 'ls-files').splitlines()
    signals = []
    pattern = re.compile(r'getOpponent\(|opponentId\b|firstOrNull\s*\{.*!=|single commander|Phase 4|Partner|APNAP|opponent.*hidden', re.I)
    for name in tracked:
        if name.startswith(('ai/src/main/', 'gym/src/main/', 'rules-engine/src/main/')) and name.endswith('.kt'):
            for number, line in enumerate((root / name).read_text(encoding='utf-8').splitlines(), 1):
                if pattern.search(line):
                    signals.append({'file': name, 'line': number, 'text': line.strip()[:280]})
    write(out / 'source-signals.json', signals)
    report = {
        'schema': 'mt-phase2-bootstrap-audit-v1',
        'source_commit': git(root, 'rev-parse', 'HEAD'),
        'source_tree': git(root, 'rev-parse', 'HEAD^{tree}'),
        'phase1_commit': PHASE1,
        'phase1_preserved': True,
        'sources': sources,
        'opponent_admission_manifest_sha256': sha(admission_bytes),
        'registry_request_sha256': sha((out / 'registry-requests.json').read_bytes()),
        'deck_count': len(decks),
        'requested_physical_cards': sum(len(x['commanders']) + len(x['mainboard']) for x in decks),
        'source_signals_are_diagnostic_not_automatic_defect_classifications': True,
        'execution_allowed': False,
        'official_counters': {'seeds_generated': 0, 'attempts': 0, 'games_initialized': 0, 'actions': 0, 'outcomes': 0},
        'status': 'INVENTORY_ONLY_NOT_CEDH_QUALIFICATION',
    }
    write(out / 'bootstrap-report.json', report)
    if args.bundle:
        with zipfile.ZipFile(out / 'source-bundle.zip', 'w', compression=zipfile.ZIP_DEFLATED) as archive:
            for name in tracked:
                production = name.startswith(('rules-engine/', 'mtg-sdk/', 'ai/', 'gym/', 'mtg-sets/', '.agents/skills/', 'experiments/manual-transmission-phase2/'))
                if production and Path(name).suffix in ('.kt', '.kts', '.md', '.py', '.json', '.txt', '.toml') and '/build/' not in name and '/src/test/resources/' not in name:
                    archive.write(root / name, 'source/' + name)
                elif name in ('build.gradle.kts', 'settings.gradle.kts', 'gradle/libs.versions.toml', 'scripts/test-class', 'scripts/gradle-locked'):
                    archive.write(root / name, 'source/' + name)
            for name in git(prior, 'ls-files', 'experiments/manual-transmission').splitlines():
                archive.write(prior / name, 'phase1/' + name)
    print(json.dumps({'status': report['status'], 'decks': len(decks), 'cards': report['requested_physical_cards'], 'execution_allowed': False}))


if __name__ == '__main__':
    main()
