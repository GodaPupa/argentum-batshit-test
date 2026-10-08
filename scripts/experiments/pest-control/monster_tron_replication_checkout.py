"""Read-only checkout measurement and excluded-fixture boundary. No official adapter."""
from dataclasses import asdict, dataclass
import hashlib
import os
from pathlib import Path
import re
import stat
import subprocess

import monster_tron_replication_seedfree as fixture

FIXTURE_ACK = 'VALIDATE_EXCLUDED_SEEDFREE_FIXTURE_ONLY'


def _git(root, *args):
    # No inherited Git overrides, replacement objects, optional index writes or lazy fetch.
    env = dict(PATH=os.defpath, LC_ALL='C', GIT_CONFIG_NOSYSTEM='1',
               GIT_CONFIG_GLOBAL=os.devnull, GIT_NO_REPLACE_OBJECTS='1',
               GIT_OPTIONAL_LOCKS='0', GIT_NO_LAZY_FETCH='1', GIT_TERMINAL_PROMPT='0')
    try:
        return subprocess.run(['git', '--no-replace-objects', '-C', str(root), *args],
                              env=env, check=True, capture_output=True, timeout=30).stdout
    except (OSError, subprocess.SubprocessError) as error:
        raise fixture.Refused('checkout Git measurement failed') from error


@dataclass(frozen=True)
class CheckoutMeasurement:
    head: str
    tree: str
    tracked_files: int
    inventory_sha256: str
    realm: str = fixture.REALM
    execution_authorized: bool = False


def attest_checkout(root, expected_head, expected_tree):
    """Measure actual tracked bytes/modes and exact inventory against out-of-band pins.

    Pins are comparison inputs, not permission. Single-owner point-in-time observation;
    does not attest the interpreter, loaded modules, Git binary or physical persistence.
    """
    fixture.require(all(type(x) is str and re.fullmatch('[0-9a-f]{40}', x)
                        for x in (expected_head, expected_tree)), 'exact SHA-1 pins required')
    root = Path(root).absolute()
    fixture.require(root == root.resolve() and root.is_dir(), 'checkout root alias/missing')
    fixture.require(_git(root, 'rev-parse', '--show-toplevel').rstrip(b'\n') == os.fsencode(root),
                    'must measure checkout root')
    fixture.require(_git(root, 'rev-parse', '--show-object-format').strip() == b'sha1', 'object format')
    fixture.require(_git(root, 'rev-parse', '--verify', 'HEAD').strip().decode() == expected_head,
                    'checkout HEAD mismatch')
    fixture.require(_git(root, 'rev-parse', '--verify', 'HEAD^{tree}').strip().decode() == expected_tree,
                    'checkout tree mismatch')
    raw_tree = _git(root, 'ls-tree', '-rz', '--full-tree', expected_tree)
    entries = {}
    for record in raw_tree.split(b'\0'):
        if not record:
            continue
        meta, raw_name = record.split(b'\t', 1)
        mode, kind, blob = meta.split()
        name = os.fsdecode(raw_name)
        parts = name.split('/')
        fixture.require(all(p not in ('', '.', '..', '.git') for p in parts), 'unsafe tree path')
        fixture.require(kind == b'blob' and mode in (b'100644', b'100755', b'120000'),
                        'submodules or unsupported tree modes refused')
        fixture.require(name not in entries, 'duplicate tree path')
        entries[name] = (mode, blob)
    fixture.require(bool(entries), 'empty checkout refused')

    # Index comparison catches staged modifications, conflicts and alternate sparse entries.
    index = {}
    for record in _git(root, 'ls-files', '--stage', '-z').split(b'\0'):
        if not record:
            continue
        meta, raw_name = record.split(b'\t', 1)
        mode, blob, stage = meta.split()
        name = os.fsdecode(raw_name)
        fixture.require(stage == b'0' and name not in index, 'unmerged/duplicate index')
        index[name] = (mode, blob)
    fixture.require(index == entries, 'index/tree mismatch')

    # Do not rely on status: ignored, assume-unchanged and skip-worktree must not hide bytes.
    actual = set()
    allowed_dirs = {str(parent) for name in entries for parent in Path(name).parents
                    if str(parent) != '.'}
    def scan(directory, prefix=''):
        with os.scandir(directory) as children:
            for child in children:
                if not prefix and child.name == '.git':
                    continue
                name = prefix + child.name
                if child.is_dir(follow_symlinks=False):
                    fixture.require(name in allowed_dirs, 'untracked directory')
                    scan(child.path, name + '/')
                else:
                    actual.add(name)
    scan(root)
    fixture.require(actual == set(entries), 'missing/untracked/ignored checkout files')
    inventory = []
    for name, (mode, blob) in sorted(entries.items()):
        path = root / name
        info = path.lstat()
        if mode == b'120000':
            fixture.require(stat.S_ISLNK(info.st_mode), 'tracked symlink type changed')
            raw = os.fsencode(os.readlink(path))
        else:
            fixture.require(stat.S_ISREG(info.st_mode), 'tracked regular file type changed')
            fixture.require(bool(info.st_mode & stat.S_IXUSR) == (mode == b'100755'), 'executable mode changed')
            fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
            with os.fdopen(fd, 'rb') as stream:
                raw = stream.read()
        observed = hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest()
        fixture.require(observed == blob.decode(), 'tracked bytes differ: ' + name)
        inventory.append(dict(path=name, mode=mode.decode(), blob=observed,
                              sha256=fixture.digest(raw), bytes=len(raw)))
    # Detect ordinary head/index movement during the scan; no hostile-concurrency guarantee.
    fixture.require(_git(root, 'rev-parse', '--verify', 'HEAD').strip().decode() == expected_head,
                    'HEAD moved during measurement')
    fixture.require(_git(root, 'ls-files', '--stage', '-z') == b''.join(
        mode + b' ' + blob + b' 0\t' + os.fsencode(name) + b'\0'
        for name, (mode, blob) in sorted(index.items())), 'index moved during measurement')
    return CheckoutMeasurement(expected_head, expected_tree, len(entries),
                               fixture.digest(fixture.canonical(inventory)))


class AttestedFixtureBoundary:
    """Compose unchanged fixture coordinator with checkout checks before entry/completion."""
    def __init__(self, checkout, expected_head, expected_tree, evidence_root,
                 raw, expected_digest, receipt, ack):
        fixture.require(ack == FIXTURE_ACK, 'excluded validation ACK required')
        checkout = Path(checkout).absolute()
        evidence_root = Path(evidence_root).absolute()
        fixture.require(not evidence_root.resolve().is_relative_to(checkout.resolve()),
                        'fixture evidence must be outside measured checkout')
        measured = attest_checkout(checkout, expected_head, expected_tree)
        self._checkout = checkout
        self._pins = (expected_head, expected_tree)
        self._measurement = measured
        self._coordinator = fixture.FixtureEvidenceCoordinator(
            evidence_root, raw, expected_digest, fixture.BASELINE, receipt)
        try:
            self._coordinator._write('fixture-checkout.json', fixture.canonical(asdict(measured)))
        except BaseException:
            self._coordinator.poisoned = True
            self._coordinator.close()
            raise

    def transition(self, kind, slot, token=None):
        return self._coordinator.transition(kind, slot, token)

    def finish(self):
        self._coordinator._guard()
        try:
            fixture.require(attest_checkout(self._checkout, *self._pins) == self._measurement,
                            'checkout changed before fixture completion')
            return self._coordinator.finish()
        except BaseException:
            self._coordinator.poisoned = True
            raise

    def close(self):
        self._coordinator.close()


def load_official(*args, **kwargs):
    # Deliberately no attestation/ACK-to-execution conversion.
    return fixture.load_official(*args, **kwargs)
