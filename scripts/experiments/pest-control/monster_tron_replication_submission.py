"""Source-bound excluded token submission ordering; never a game/engine adapter."""
import json
from pathlib import Path
import re

import monster_tron_replication_seedfree as f
import monster_tron_replication_checkout as c

COMPONENTS = {
    'monster_tron_replication_seedfree.py': '49ddefe48b6226f456c28817f392f981f8852985dae3e646b315b67e8747c780',
    'monster_tron_replication_checkout.py': 'c35afec4c57233b8d0028a08aa9da48ee587df187d56ba3c73e94c260928c4e5',
}
SELF = 'monster_tron_replication_submission.py'
DIRECTORY = 'scripts/experiments/pest-control'
ACK = 'VALIDATE_EXCLUDED_SUBMISSION_BOUNDARY_ONLY'


def load_profile(raw, expected_digest):
    f.require(type(raw) is bytes and 0 < len(raw) <= 8192, 'missing/oversize fixture profile')
    f.require(f.digest(raw) == expected_digest, 'profile digest mismatch')
    try:
        p = json.loads(raw.decode('utf-8'), object_pairs_hook=f._object)
        f.require(type(p) is dict and f.canonical(p) == raw, 'noncanonical profile')
        f.require(set(p) == {'realm', 'head', 'tree', 'components', 'fixture_digest', 'attempt',
                            'slots', 'tokens_per_slot', 'claim', 'official_bindings'}, 'profile fields')
        f.require(p['realm'] == f.REALM and p['claim'] == 'EXCLUDED_LOCAL_FIXTURE_NOT_A_CLAIM', 'profile realm/claim')
        for key in ('head', 'tree'):
            f.require(type(p[key]) is str and re.fullmatch('[0-9a-f]{40}', p[key]), 'source pin')
        f.require(type(p['fixture_digest']) is str and re.fullmatch('[0-9a-f]{64}', p['fixture_digest']), 'fixture pin')
        for key, value in [('attempt', 1), ('slots', 12), ('tokens_per_slot', 2)]:
            f.require(type(p[key]) is int and p[key] == value, 'fixture count/attempt/budget')
        f.require(p['official_bindings'] == dict(f.OFFICIAL_BINDINGS), 'official bindings must remain null')
        expected_names = set(COMPONENTS) | {SELF}
        f.require(type(p['components']) is dict and set(p['components']) == expected_names, 'component coverage')
        for name, digest in p['components'].items():
            f.require(type(digest) is str and re.fullmatch('[0-9a-f]{64}', digest), 'component digest')
            if name in COMPONENTS:
                f.require(digest == COMPONENTS[name], 'accepted component changed')
        return p
    except (UnicodeError, json.JSONDecodeError, TypeError, KeyError) as error:
        raise f.Refused('invalid profile encoding') from error


def _evaluate(label, token, sequence):
    """Built-in deterministic fixture transformation; no injection/engine callback API."""
    return dict(realm=f.REALM, label=label, token=token, sequence=sequence,
                payload_sha256=f.digest(f.canonical([label, token, sequence])),
                outcome=None, no_game_played=True)


class FixtureSubmissionBoundary:
    def __init__(self, checkout, evidence_root, profile_raw, profile_digest, vector_raw, receipt, ack):
        f.require(ack == ACK, 'excluded submission ACK required')
        p = load_profile(profile_raw, profile_digest)
        f.require(type(vector_raw) is bytes and f.digest(vector_raw) == p['fixture_digest'], 'profile/vector binding')
        # Bind on-disk module bytes both in measured checkout and at loaded module paths.
        modules = {SELF: Path(__file__), 'monster_tron_replication_seedfree.py': Path(f.__file__),
                   'monster_tron_replication_checkout.py': Path(c.__file__)}
        checkout = Path(checkout).absolute()
        for name, loaded_path in modules.items():
            source = checkout / DIRECTORY / name
            f.require(not source.is_symlink() and source.is_file(), 'component missing/aliased')
            f.require(f.digest(source.read_bytes()) == p['components'][name], 'checkout component mismatch')
            f.require(f.digest(loaded_path.read_bytes()) == p['components'][name], 'loaded module disk mismatch')
        self._boundary = c.AttestedFixtureBoundary(checkout, p['head'], p['tree'], evidence_root,
            vector_raw, p['fixture_digest'], receipt, c.FIXTURE_ACK)
        self._coordinator = self._boundary._coordinator
        self._slot = 1
        self._sequence = 0
        self._active = False
        try:
            self._coordinator._write('fixture-submission-profile.json', profile_raw)
        except BaseException:
            self._coordinator.poisoned = True
            self.close()
            raise

    def _guard(self):
        self._coordinator._guard()

    def begin_slot(self, number):
        self._guard()
        try:
            f.require(type(number) is int and number == self._slot and number <= 12 and not self._active,
                      'submission slot order')
            self._boundary.transition('ATTEMPT', number)
            self._boundary.transition('INITIALIZATION_ENTRY', number)
            self._boundary.transition('FIXTURE_INITIALIZED', number)
            self._active = True
        except BaseException:
            self._coordinator.poisoned = True
            raise

    def submit_token(self, token):
        self._guard()
        try:
            f.require(self._active and self._sequence < 2, 'fixture slot inactive/budget exhausted')
            sequence = self._sequence + 1
            f.require(type(token) is str and token == f'EXCLUDED_ACTION_{sequence:05d}', 'fixture token order')
            self._boundary.transition('INTENT', self._slot, token)
            # No transform occurs before successful durable INTENT. Exceptions consume this boundary.
            raw = f.canonical(_evaluate(self._coordinator.slots[self._slot - 1].label, token, sequence))
            self._coordinator._write(f'fixture-raw-{self._slot:02d}-{sequence:02d}.json', raw)
            self._boundary.transition('RESULT', self._slot, token)
            self._sequence = sequence
            return f.digest(raw)
        except BaseException:
            self._coordinator.poisoned = True
            raise

    def end_slot(self):
        self._guard()
        try:
            f.require(self._active and self._sequence == 2, 'two fixture tokens required')
            self._boundary.transition('FIXTURE_RECORD', self._slot)
            self._slot += 1
            self._sequence = 0
            self._active = False
        except BaseException:
            self._coordinator.poisoned = True
            raise

    def finish(self):
        self._guard()
        try:
            f.require(self._slot == 13 and not self._active, 'twelve complete fixture slots required')
            return self._boundary.finish()
        except BaseException:
            self._coordinator.poisoned = True
            raise

    def close(self):
        self._boundary.close()


def load_official(*args, **kwargs):
    return f.load_official(*args, **kwargs)
