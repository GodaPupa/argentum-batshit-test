"""Read-only semantic verifier of complete excluded fixture evidence, never authority."""
from dataclasses import dataclass
import json
import os
from pathlib import Path
import re
import stat

import monster_tron_replication_seedfree as f
import monster_tron_replication_submission as s

SUBMISSION_SHA256 = '345223ce22dae76e1fa14979212f0fa2282d109a7677d98b87a4c99e406299f1'
MANIFEST = 'fixture-artifacts.sha256'


@dataclass(frozen=True)
class VerifiedFixtureEvidence:
    head: str
    tree: str
    profile_sha256: str
    manifest_sha256: str
    slots: int = 12
    token_records: int = 24
    realm: str = f.REALM
    execution_authorized: bool = False


def _json(raw):
    try:
        value = json.loads(raw.decode('utf-8'), object_pairs_hook=f._object)
        f.require(f.canonical(value) == raw, 'noncanonical evidence JSON')
        return value
    except (UnicodeError, json.JSONDecodeError, TypeError) as error:
        raise f.Refused('invalid evidence JSON') from error


def _events_and_raw(slots):
    """Expected deterministic transcript, independently of producer state/evaluator."""
    expected = {}
    index = 0
    for slot in slots:
        events = [('ATTEMPT', dict(label=slot.label)), ('INITIALIZATION_ENTRY', {}),
                  ('FIXTURE_INITIALIZED', dict(no_engine_called=True))]
        for sequence in (1, 2):
            token = f'EXCLUDED_ACTION_{sequence:05d}'
            events.append(('INTENT', dict(sequence=sequence, token=token)))
            expected[f'fixture-raw-{slot.number:02d}-{sequence:02d}.json'] = f.canonical(dict(
                realm=f.REALM, label=slot.label, token=token, sequence=sequence,
                payload_sha256=f.digest(f.canonical([slot.label, token, sequence])),
                outcome=None, no_game_played=True))
            events.append(('RESULT', dict(sequence=sequence, token=token, no_engine_called=True)))
        events.append(('FIXTURE_RECORD', dict(action_pairs=2, outcome=None, no_game_played=True)))
        for kind, payload in events:
            index += 1
            expected[f'{index:05d}-{kind}.json'] = f.canonical(dict(
                realm=f.REALM, index=index, slot=slot.number, kind=kind, payload=payload))
    return expected


def verify_fixture_evidence(root, expected_profile_digest, expected_head, expected_tree,
                            expected_checkout_inventory, expected_tracked_files):
    """Compare evidence to caller's exact pins without replay, repair, writes or Git calls.

    No signature, authorship, physical durability, source remeasurement or past ordering
    attestation. A complete forged transcript can pass if all trusted inputs are forged.
    """
    for pin, length in ((expected_profile_digest,64),(expected_head,40),(expected_tree,40),
                        (expected_checkout_inventory,64)):
        f.require(type(pin) is str and re.fullmatch('[0-9a-f]{'+str(length)+'}',pin), 'exact evidence pins required')
    f.require(type(expected_tracked_files) is int and expected_tracked_files > 0, 'tracked count pin')
    root = Path(root).absolute()
    f.require(root == root.resolve(), 'evidence root alias')
    fd = os.open(root, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        names = os.listdir(fd)
        f.require(len(names) == 126, 'complete 126-file fixture required')
        content = {}
        total = 0
        for name in names:
            child = os.open(name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=fd)
            try:
                info = os.fstat(child)
                f.require(stat.S_ISREG(info.st_mode) and info.st_nlink == 1, 'regular unaliased evidence required')
                f.require(info.st_size <= 131072, 'oversize evidence member')
                with os.fdopen(child, 'rb', closefd=False) as stream:
                    raw = stream.read(131073)
                f.require(len(raw) <= 131072, 'evidence grew beyond limit')
            finally:
                os.close(child)
            total += len(raw)
            f.require(total <= 1048576, 'oversize evidence inventory')
            content[name] = raw
        f.require(set(os.listdir(fd)) == set(names), 'evidence membership changed during read')
    finally:
        os.close(fd)

    fixed = {'fixture-input.json','fixture-receipt.json','fixture-checkout.json',
             'fixture-submission-profile.json','fixture-summary.json',MANIFEST}
    f.require(fixed <= set(content), 'missing fixed evidence')
    profile = s.load_profile(content['fixture-submission-profile.json'], expected_profile_digest)
    f.require(profile['head'] == expected_head and profile['tree'] == expected_tree, 'source profile mismatch')
    f.require(profile['components'][s.SELF] == SUBMISSION_SHA256, 'unaccepted submission component')
    slots = f.load_fixture(content['fixture-input.json'], profile['fixture_digest'], f.BASELINE)
    f.validate_fixture_receipt(_json(content['fixture-receipt.json']), profile['fixture_digest'], f.BASELINE)
    expected = _events_and_raw(slots)
    f.require(set(content) == set(expected) | fixed, 'unexpected/missing transcript names')
    for name, raw in expected.items():
        f.require(content[name] == raw, 'semantic fixture transcript mismatch: '+name)
    measurement = dict(head=expected_head,tree=expected_tree,tracked_files=expected_tracked_files,
                       inventory_sha256=expected_checkout_inventory,realm=f.REALM,execution_authorized=False)
    f.require(content['fixture-checkout.json'] == f.canonical(measurement), 'checkout evidence pins mismatch')
    summary = dict(realm=f.REALM,recorded_slots=list(range(1,13)),official_initializations=0,
                   official_actions=0,official_outcomes=0,execution_authorized=False)
    f.require(content['fixture-summary.json'] == f.canonical(summary), 'fixture summary mismatch')
    inventory = ''.join(f'{f.digest(raw)}  {name}\n' for name,raw in sorted(content.items())
                        if name != MANIFEST).encode()
    f.require(content[MANIFEST] == inventory, 'exact checksum inventory mismatch')
    return VerifiedFixtureEvidence(expected_head,expected_tree,expected_profile_digest,f.digest(inventory))


def load_official(*args, **kwargs):
    return f.load_official(*args, **kwargs)
