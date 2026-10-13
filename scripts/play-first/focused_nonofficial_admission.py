"""Non-executing, single-host correspondence prototype. No gameplay launcher.

Trusted CODE seams are excluded synthetic fixtures only. CLI cannot install one.
Owner-controlled clocks/readbacks/SQLite records are not independent attestation.
The accepted integration remains unchanged; executable activation is always DENY.
"""
from __future__ import annotations
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import sqlite3
import sys
import time

if sys.flags.isolated:
    sys.path.insert(0, str(Path(__file__).resolve().parent))
import gameplay_authority as auth
import staged_runtime as runtime

CONTRACT = 'ARGENTUM_FOCUSED_NONOFFICIAL_INTENT_V1'
OWNER_CONTRACT = 'ARGENTUM_FOCUSED_OWNER_DECISION_V1'
RELEASE_CONTRACT = 'ARGENTUM_FOCUSED_FINAL_RELEASE_V1'
RECEIPT_CONTRACT = 'ARGENTUM_FOCUSED_OWNER_CONTROLLED_RECEIPT_V1'
SOURCE_PARENT = 'f98d850f3883493de5abf303da0e68c8902d16c6'
SYNTHETIC = runtime.SYNTHETIC
STATES = dict(INTENT='INTENT_FROZEN', OWNER_VERIFIED='OWNER_APPROVED', HOLD='ENGINE_FREE_HOLD',
              RELEASE_VERIFIED='FINAL_RELEASE_VERIFIED', RESERVED='FINAL_RELEASE_VERIFIED',
              CONSUMED='ORIGINAL_CONSUMED', CANCELLED='CANCELLED', FAILED='REFUSED',
              RECOVERY_REQUIRED='QUARANTINED')


def keys(value, expected, message):
    runtime.strict_keys(value, expected, message)


def text(value, message):
    auth.require(type(value) is str and bool(re.fullmatch(r'[A-Za-z0-9_.:-]{1,160}', value)), message)


def hex_id(value, length, message):
    auth.require(type(value) is str and bool(re.fullmatch('[0-9a-f]{' + str(length) + '}', value)), message)


def freeze_intent(data):
    auth.require(type(data) is bytes and len(data) <= 65536, 'bounded immutable intent bytes required')
    intent = auth.decode(data)
    keys(intent, ('contract', 'case', 'identities', 'executor'), 'focused intent; no future comment/run IDs')
    auth.require(intent['contract'] == CONTRACT, 'focused intent contract')
    case = intent['case']
    keys(case, ('scope', 'blockId', 'gameId', 'seats', 'originalOnly', 'maxGames'), 'focused case')
    auth.require(case['scope'] == auth.SCOPE and case['originalOnly'] is True and
                 type(case['maxGames']) is int and case['maxGames'] == 1, 'nonofficial original-only scope')
    auth.require(type(case['blockId']) is str and re.fullmatch(r'PLAY_FIRST_[A-Z0-9_]{1,80}', case['blockId'])
                 and 'B01' not in case['blockId'], 'historical B01 excluded')
    auth.require(type(case['gameId']) is str and re.fullmatch(r'G[0-9]{2,4}', case['gameId'])
                 and case['gameId'] != 'G08', 'historical G08 excluded')
    keys(case['seats'], ('pest', 'monster'), 'explicit seat assignment')
    auth.require(all(type(x) is int for x in case['seats'].values()) and
                 set(case['seats'].values()) == {0, 1}, 'distinct exact seats')
    ids = intent['identities']
    keys(ids, ('sourceCommit', 'sourceTree', 'sourceParent', 'engineManifestSha256',
               'deckManifestSha256', 'pilotManifestSha256'), 'source/engine/deck/pilot identities')
    for key in ('sourceCommit', 'sourceTree', 'sourceParent'):
        hex_id(ids[key], 40, key)
    auth.require(ids['sourceParent'] == SOURCE_PARENT, 'focused successor sole-parent binding')
    for key in ('engineManifestSha256', 'deckManifestSha256', 'pilotManifestSha256'):
        hex_id(ids[key], 64, key)
    executor = intent['executor']
    keys(executor, ('executorId', 'storageId', 'ledgerPath'), 'one designated executor/storage')
    text(executor['executorId'], 'executor identity required'); text(executor['storageId'], 'storage identity required')
    auth.require(type(executor['ledgerPath']) is str and len(executor['ledgerPath']) <= 4096 and
                 Path(executor['ledgerPath']).is_absolute(), 'absolute approved ledger path')
    return dict(intent=intent, intentSha256=auth.sha(data), stage='INTENT_FROZEN',
                engineAllowed=False, gameplayAuthorized=False, productionQualified=False)


def deny_activation(*args, **kwargs):
    raise ValueError('DISABLED: source review, actual exclusive persistent executor qualification and distinct case activation absent')


def inventory_payloads(payload_root):
    root = Path(payload_root)
    auth.require(root.is_dir() and not root.is_symlink(), 'existing original evidence directory required')
    inventory = []; total = 0
    for path in sorted(root.rglob('*')):
        auth.require(not path.is_symlink(), 'evidence symlink refused')
        if path.is_dir(): continue
        auth.require(path.is_file() and len(inventory) < 128 and path.stat().st_size <= 1024*1024, 'bounded original evidence payloads')
        data = path.read_bytes(); total += len(data); auth.require(total <= 8*1024*1024, 'bounded total evidence')
        inventory.append(dict(path=path.relative_to(root).as_posix(), bytes=len(data), sha256=auth.sha(data)))
    return inventory


class FocusedAdmission:
    """Compose the accepted durable ledger and positive GitHub decision helpers.

    No initializer/callback/trace creation exists. Synthetic CODE inspection can
    model persistent/exclusive deployment but cannot qualify the actual host.
    Advisory flock/device/inode pins detect tested local copies, not hostile hosts.
    """
    def __init__(self, ledger_path, *, _executor=None, _client=None):
        auth.require(os.name == 'posix' and _executor is not None and
                     getattr(_executor, 'origin', None) == SYNTHETIC, 'UNQUALIFIED: actual executor/persistence verifier absent')
        self.executor = _executor
        self.client = auth.LiveGitHub() if _client is None else _client
        self.path = Path(ledger_path)
        auth.require(self.path.is_absolute() and self.path.parent.is_dir() and
                     str(self.path.resolve()) == str(self.path), 'noncanonical/missing/symlink ledger path')
        self.closed = False
        self.uncertain = False
        self.lock_path = self.path.parent / '.argentum-focused-executor.lock'
        self.lock_fd = None
        self.session = str(os.getpid()) + ':' + str(time.monotonic_ns())
        self.reservations = set()
        try:
            facts = self._facts()
            flags = os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW
            self.lock_fd = os.open(self.lock_path, flags, 0o600)
            auth.require(os.fstat(self.lock_fd).st_nlink == 1, 'aliased executor lock')
            try:
                fcntl.flock(self.lock_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError as exc:
                raise ValueError('conflicting active executor/session') from exc
            self.ledger = runtime.LocalLedger(self.path)
            stat = self.path.stat()
            auth.require(stat.st_nlink == 1, 'aliased ledger storage')
            self.binding = dict(executorId=facts['executorId'], storageId=facts['storageId'],
                                ledgerPath=str(self.path), device=stat.st_dev, inode=stat.st_ino)
            self.binding_sha = runtime.digest(self.binding)
            # One designated ledger in this qualified storage-root model. This
            # is owner-controlled local registration, not authority/attestation.
            pinned = os.pread(self.lock_fd, 65537, 0)
            expected_pin = runtime.canonical(self.binding).encode()
            auth.require(len(pinned) <= 65536, 'corrupt/ambiguous storage-root registration')
            if pinned:
                auth.require(pinned == expected_pin, 'storage root already registered to another ledger/executor')
            else:
                auth.require(os.pwrite(self.lock_fd, expected_pin, 0) == len(expected_pin), 'incomplete registration write')
                os.ftruncate(self.lock_fd, len(expected_pin)); os.fsync(self.lock_fd)
                directory_fd = os.open(self.path.parent, os.O_RDONLY | os.O_DIRECTORY)
                try: os.fsync(directory_fd)
                finally: os.close(directory_fd)
            with self.ledger.transaction() as db:
                db.execute('CREATE TABLE IF NOT EXISTS focused_executor (singleton INTEGER PRIMARY KEY CHECK(singleton=1), binding TEXT NOT NULL)')
                db.execute('CREATE TABLE IF NOT EXISTS focused_protocol (request TEXT PRIMARY KEY)')
                existing = db.execute('SELECT binding FROM focused_executor WHERE singleton=1').fetchone()
                if existing:
                    auth.require(existing['binding'] == runtime.canonical(self.binding), 'copied/conflicting executor or ledger identity')
                else:
                    db.execute('INSERT INTO focused_executor VALUES(1,?)', (runtime.canonical(self.binding),))
                # A new session never inherits executable release/reservation.
                for record in db.execute('SELECT attempts.* FROM attempts JOIN focused_protocol USING(request)').fetchall():
                    row = dict(record)
                    if row['state'] in ('OWNER_VERIFIED', 'HOLD', 'RELEASE_VERIFIED', 'RESERVED'):
                        target = 'RECOVERY_REQUIRED' if row['state'] == 'RESERVED' else 'FAILED'
                        self.ledger.transition(db, row, target, auth.decode(row['evidence']), reason='interrupted session; no automatic resume/replay')
        except BaseException:
            self.close()
            raise

    def _facts(self):
        auth.require(not self.closed and not self.uncertain, 'closed/quarantined uncertain executor session')
        facts = self.executor.inspect()
        keys(facts, ('executorId', 'storageId', 'ledgerPath', 'persistent', 'exclusive', 'posix', 'identities'), 'executor CODE observation')
        text(facts['executorId'], 'unidentified executor'); text(facts['storageId'], 'unidentified storage')
        auth.require(facts['ledgerPath'] == str(self.path) and facts['persistent'] is True and
                     facts['exclusive'] is True and facts['posix'] is True, 'ephemeral/disposable/nonexclusive/wrong ledger executor')
        return facts

    def _observe(self, frozen):
        facts = self._facts(); stat = self.path.stat()
        held_lock = os.fstat(self.lock_fd)
        observed_lock = self.lock_path.lstat()
        auth.require((held_lock.st_dev, held_lock.st_ino) == (observed_lock.st_dev, observed_lock.st_ino), 'executor lock replaced')
        auth.require(os.pread(self.lock_fd, 65537, 0) == runtime.canonical(self.binding).encode(), 'storage-root registration altered')
        auth.require(str(self.path.resolve()) == str(self.path) and stat.st_nlink == 1 and
                     (stat.st_dev, stat.st_ino) == (self.binding['device'], self.binding['inode']), 'ledger replaced/aliased')
        intent = frozen['intent']
        auth.require(auth.same_json(intent['executor'], {k: self.binding[k] for k in intent['executor']}) and
                     auth.same_json({k: facts[k] for k in intent['executor']}, intent['executor']) and
                     auth.same_json(intent['identities'], facts['identities']), 'actual executor/source/engine/deck/pilot differs from intent')

    @contextmanager
    def _atomic(self):
        try:
            with self.ledger.transaction() as db: yield db
        except sqlite3.IntegrityError:
            raise  # Known uniqueness conflict, transaction rolled back.
        except (sqlite3.Error, OSError, RuntimeError):
            # An ambiguous caller/IO/COMMIT return is not permission to retry.
            # Durable state may require separate disposition; no second original.
            self.uncertain = True
            raise

    def _clock(self):
        at = self.executor.now()
        auth.require(type(at) is datetime and at.tzinfo == timezone.utc, 'owner-controlled UTC observation unavailable')
        return at

    def close(self):
        if self.lock_fd is not None:
            os.close(self.lock_fd); self.lock_fd = None
        self.closed = True

    def __enter__(self): return self
    def __exit__(self, *args): self.close()

    def _row(self, db, request):
        auth.require(db.execute('SELECT 1 FROM focused_protocol WHERE request=?', (request,)).fetchone() is not None,
                     'missing focused request; cannot inherit another protocol')
        return self.ledger.row(db, request)

    def inspect(self, request, *, new_consumption=False):
        with self.ledger.connection() as db:
            row = self._row(db, request)
        return dict(stage=STATES[row['state']], state=row['state'], intentSha256=row['intent_hash'],
                    reservation=row['reservation'], reason=row['reason'], newConsumption=new_consumption,
                    engineAllowed=False, gameplayAuthorized=False, productionQualified=False,
                    authenticatedProductionOrigin=False, provenance=SYNTHETIC)

    def freeze(self, request, data):
        text(request, 'bounded request id'); frozen = freeze_intent(data); self._observe(frozen)
        case = frozen['intent']['case']
        original = runtime.digest({k: case[k] for k in ('scope', 'blockId', 'gameId')})
        with self._atomic() as db:
            old = db.execute('SELECT * FROM attempts WHERE request=?', (request,)).fetchone()
            if old:
                self._row(db, request)
                auth.require(bytes(old['intent']) == data, 'idempotency conflict: substituted immutable intent')
            else:
                evidence = dict(contract=CONTRACT, binding=self.binding, observations=[], session=self.session)
                db.execute('INSERT INTO attempts(request,original_key,intent_hash,intent,state,evidence) VALUES(?,?,?,?,?,?)',
                           (request, original, frozen['intentSha256'], data, 'INTENT', runtime.canonical(evidence)))
                db.execute('INSERT INTO focused_protocol VALUES(?)', (request,))
                db.execute('INSERT INTO audit(request,old_state,new_state,evidence_digest) VALUES(?,?,?,?)',
                           (request, None, 'INTENT', runtime.digest(evidence)))
        return self.inspect(request)

    def _expected(self, frozen, contract, **extra):
        return dict(contract=contract, intentSha256=frozen['intentSha256'], executorId=self.binding['executorId'],
                    ledgerBindingSha256=self.binding_sha, scope=auth.SCOPE, originalOnly=True, maxGames=1, **extra)

    def _read_owner(self, decision_id, frozen):
        auth.require(auth.client_owner_matches(self.client), 'actual repository/controller issuer mismatch')
        owner = self.client.get('issues/comments/' + str(decision_id))
        history = auth.live_history(self.client)
        auth.require(auth.same_json(self.client.get('issues/comments/' + str(decision_id)), owner), 'owner changed during readback')
        at = self._clock()
        auth.decision_claims(owner, decision_id, OWNER_CONTRACT, self._expected(frozen, OWNER_CONTRACT), at)
        auth.check_history(history, owner, owner, at)
        return dict(owner=owner, history=history, verificationAt=at.strftime('%Y-%m-%dT%H:%M:%SZ'),
                    readbackOrigin='LIVE_GITHUB_TLS_OWNER_FACTS' if type(self.client) is auth.LiveGitHub else SYNTHETIC,
                    historyGuarantee='CURRENT_MUTABLE_API_VIEW_ONLY', independentTimeAttestation=False)

    def _read_release(self, decision_id, frozen, evidence):
        prior = evidence['ownerRecord']['owner']; owner_id = prior['id']
        auth.require(auth.client_owner_matches(self.client), 'actual repository/controller issuer mismatch')
        owner = self.client.get('issues/comments/' + str(owner_id))
        release = self.client.get('issues/comments/' + str(decision_id))
        history = auth.live_history(self.client)
        auth.require(auth.same_json(self.client.get('issues/comments/' + str(owner_id)), owner) and
                     auth.same_json(self.client.get('issues/comments/' + str(decision_id)), release) and
                     auth.same_json(owner, prior), 'owner/release changed since approval or during readback')
        at = self._clock()
        auth.decision_claims(owner, owner_id, OWNER_CONTRACT, self._expected(frozen, OWNER_CONTRACT), at)
        expected = self._expected(frozen, RELEASE_CONTRACT, ownerDecisionId=owner_id,
                                  requestId=evidence['requestId'], holdSha256=runtime.digest(evidence['hold']))
        auth.decision_claims(release, decision_id, RELEASE_CONTRACT, expected, at)
        auth.check_history(history, owner, release, at)
        hold_at = auth.utc(evidence['hold']['observedAt'])
        auth.require(owner_id != decision_id and auth.utc(owner['updated_at']) <= hold_at <= auth.utc(release['created_at']) <= at and
                     0 <= (at - hold_at).total_seconds() <= auth.MAX_RECORD_AGE_SECONDS and
                     0 <= (at - auth.utc(release['updated_at'])).total_seconds() <= auth.MAX_RECORD_AGE_SECONDS,
                     'interrupted/stale/out-of-order final release')
        return dict(owner=owner, release=release, history=history, verificationAt=at.strftime('%Y-%m-%dT%H:%M:%SZ'),
                    readbackOrigin='LIVE_GITHUB_TLS_OWNER_FACTS' if type(self.client) is auth.LiveGitHub else SYNTHETIC,
                    historyGuarantee='CURRENT_MUTABLE_API_VIEW_ONLY', independentTimeAttestation=False)

    def _step(self, request, expected, target, verify, *, reservation=None, _before_commit=None):
        error = None
        with self._atomic() as db:
            row = self._row(db, request); evidence = auth.decode(row['evidence'])
            try:
                auth.require(row['state'] == expected and evidence['session'] == self.session, 'out-of-order/interrupted/cancelled admission')
                frozen = freeze_intent(bytes(row['intent'])); self._observe(frozen)
                evidence = verify(frozen, evidence)
                self.ledger.transition(db, row, target, evidence, reservation=reservation)
                if _before_commit is not None: _before_commit(db)  # excluded crash fixture, never CLI
            except (ValueError, KeyError, TypeError, AttributeError) as exc:
                if row['state'] not in runtime.TERMINAL:
                    fail = 'RECOVERY_REQUIRED' if row['state'] == 'RESERVED' else 'FAILED'
                    self.ledger.transition(db, row, fail, auth.decode(row['evidence']), reason=str(exc))
                error = exc
        if error is not None: raise ValueError(str(error)) from error
        return self.inspect(request)

    def owner_approved(self, request, decision_id):
        def verify(frozen, evidence):
            evidence['ownerRecord'] = self._read_owner(decision_id, frozen)
            evidence['requestId'] = request
            return evidence
        return self._step(request, 'INTENT', 'OWNER_VERIFIED', verify)

    def hold(self, request):
        def verify(frozen, evidence):
            # Current authority still has to exist; no process/trace/engine starts.
            checked = self._read_owner(evidence['ownerRecord']['owner']['id'], frozen)
            auth.require(auth.same_json(checked['owner'], evidence['ownerRecord']['owner']), 'owner changed before hold')
            evidence['hold'] = dict(intentSha256=frozen['intentSha256'], ledgerBindingSha256=self.binding_sha,
                                   session=self.session, engineStarted=False, originalSlotCreated=False,
                                   observedAt=self._clock().strftime('%Y-%m-%dT%H:%M:%SZ'))
            return evidence
        return self._step(request, 'OWNER_VERIFIED', 'HOLD', verify)

    def final_release(self, request, decision_id):
        def verify(frozen, evidence):
            evidence['releaseRecord'] = self._read_release(decision_id, frozen, evidence)
            evidence['releaseDecisionId'] = decision_id
            return evidence
        return self._step(request, 'HOLD', 'RELEASE_VERIFIED', verify)

    def reserve(self, request, reservation, *, _before_commit=None):
        auth.require(not self.uncertain, 'unknown outcome; no reservation retry')
        text(reservation, 'bounded original reservation')
        current = self.inspect(request)
        if current['state'] in ('RESERVED', 'CONSUMED'):
            auth.require(current['reservation'] == reservation, 'conflicting reservation')
            return current  # Response only, NEVER a renewed execution capability.
        def verify(frozen, evidence):
            evidence['reservationReadback'] = self._read_release(evidence['releaseDecisionId'], frozen, evidence)
            return evidence
        result = self._step(request, 'RELEASE_VERIFIED', 'RESERVED', verify, reservation=reservation, _before_commit=_before_commit)
        self.reservations.add((request, reservation))
        return result

    def consume(self, request, reservation, *, _before_commit=None):
        auth.require(not self.uncertain, 'unknown outcome; no consumption retry')
        current = self.inspect(request)
        auth.require(current['reservation'] == reservation, 'missing/conflicting reservation')
        if current['state'] == 'CONSUMED': return current
        auth.require((request, reservation) in self.reservations, 'unowned/interrupted reservation; recovery required')
        def verify(frozen, evidence):
            # Last controllable prototype boundary: identity, live issuer/content,
            # complete current API readback, expiry/revocation, then SQLite COMMIT.
            # No physical initializer exists; network/COMMIT-to-initializer gap is
            # deliberately NOT claimed solved by this software ordering.
            evidence['finalPrelaunchReadback'] = self._read_release(evidence['releaseDecisionId'], frozen, evidence)
            evidence['localConsumptionOnly'] = True
            return evidence
        self._step(request, 'RESERVED', 'CONSUMED', verify, _before_commit=_before_commit)
        self.reservations.discard((request, reservation))
        return self.inspect(request, new_consumption=True)

    def cancel(self, request):
        with self.ledger.transaction() as db:
            row = self._row(db, request)
            if row['state'] not in runtime.TERMINAL:
                target = 'RECOVERY_REQUIRED' if row['state'] == 'RESERVED' else 'CANCELLED'
                self.ledger.transition(db, row, target, auth.decode(row['evidence']), reason='owner-controlled cancellation; no replacement')
        return self.inspect(request)

    def interrupt(self, request):
        with self.ledger.transaction() as db:
            row = self._row(db, request)
            if row['state'] not in runtime.TERMINAL:
                target = 'RECOVERY_REQUIRED' if row['state'] == 'RESERVED' else 'FAILED'
                self.ledger.transition(db, row, target, auth.decode(row['evidence']), reason='interrupted; disposition required, no replay')
        return self.inspect(request)

    def observe_process(self, request, kind, *, pid=None, exit_code=None):
        auth.require(kind in ('START', 'FINISH', 'INTERRUPTED', 'CLEANUP'), 'bounded process observation')
        with self.ledger.transaction() as db:
            row = self._row(db, request); auth.require(row['state'] == 'CONSUMED', 'no process observation before final release/consumption')
            evidence = auth.decode(row['evidence']); observations = evidence['observations']
            previous = observations[-1]['kind'] if observations else None
            allowed = {None: {'START'}, 'START': {'FINISH', 'INTERRUPTED'}, 'FINISH': {'CLEANUP'}, 'INTERRUPTED': {'CLEANUP'}, 'CLEANUP': set()}
            auth.require(kind in allowed[previous], 'contradictory/duplicate process observation')
            auth.require(type(pid) is int and pid > 0 and (not observations or pid == observations[0]['pid']), 'missing/mismatched process identity')
            auth.require((kind == 'FINISH' and type(exit_code) is int) or (kind != 'FINISH' and exit_code is None), 'process exit observation')
            observations.append(dict(kind=kind, pid=pid, exitCode=exit_code, observedAt=self._clock().strftime('%Y-%m-%dT%H:%M:%SZ'), evidenceOrigin='OWNER_CONTROLLED_NOT_INDEPENDENTLY_ATTESTED'))
            db.execute('UPDATE attempts SET evidence=? WHERE request=?', (runtime.canonical(evidence), request))
        return self.inspect(request)

    def receipt(self, request, payload_root):
        inventory = inventory_payloads(payload_root)
        with self.ledger.connection() as db:
            row = self._row(db, request); audit = [dict(x) for x in db.execute('SELECT * FROM audit WHERE request=? ORDER BY seq', (request,))]
        evidence = auth.decode(row['evidence'])
        required_log = 'child.log' if evidence['observations'] else 'verifier.log'
        auth.require(any(x['path'] == required_log for x in inventory), 'original log required; never fabricate a child before launch')
        return dict(contract=RECEIPT_CONTRACT, intent=auth.decode(bytes(row['intent'])), intentSha256=row['intent_hash'],
                    request=request, reservation=row['reservation'], stage=STATES[row['state']], reason=row['reason'],
                    evidence=evidence, transitions=audit, payloadInventory=inventory, inventorySha256=runtime.digest(inventory),
                    gameplayAuthorized=False, engineAllowed=False, productionQualified=False,
                    provenanceLimitations=['OWNER_CONTROLLED_NOT_INDEPENDENTLY_ATTESTED', 'NO_PHYSICAL_HOST_ATTESTATION',
                       'CURRENT_GITHUB_VIEW_NOT_IMMUTABLE_HISTORY', 'SQLITE_SINGLE_FILE_NOT_DISTRIBUTED_UNIQUENESS',
                       'CONSUMPTION_IS_NOT_ENGINE_INITIALIZATION', SYNTHETIC])


def inspect_historical(receipt, archive_reference, payload_root, *, _owner_archive=None):
    """Reduced-assurance owner-record correspondence; never launch permission.

    No artifact timestamp authenticates itself. This excluded seam obtains the
    separately archived original record through CODE, not case JSON. Owner archive
    is still owner-controlled/mutable, not the accepted receiver's trusted anchor.
    """
    auth.require(_owner_archive is not None and getattr(_owner_archive, 'origin', None) == SYNTHETIC,
                 'UNQUALIFIED: separately identified owner original archive unavailable')
    archived = _owner_archive.read_original(archive_reference)
    auth.require(auth.same_json(archived, receipt), 'altered/mismatched/replayed historical receipt')
    auth.require(receipt['contract'] == RECEIPT_CONTRACT and receipt['stage'] == 'ORIGINAL_CONSUMED' and
                 receipt['gameplayAuthorized'] is False and receipt['engineAllowed'] is False and
                 receipt['productionQualified'] is False, 'incomplete/nonoriginal receipt')
    # The original byte digest may differ from canonical serialization; archive
    # preserves exact intent bytes separately. Never infer identity by reserializing.
    original = _owner_archive.intent_bytes(archive_reference)
    frozen = freeze_intent(original)
    auth.require(auth.same_json(frozen['intent'], receipt['intent']) and frozen['intentSha256'] == receipt['intentSha256'], 'historical intent source/case mismatch')
    evidence = receipt['evidence']; final = evidence['finalPrelaunchReadback']
    observed = evidence['observations']
    auth.require([x['kind'] for x in observed] in (['START', 'FINISH', 'CLEANUP'], ['START', 'INTERRUPTED', 'CLEANUP']),
                 'incomplete process start/finish/interruption/cleanup correspondence')
    stamps = [auth.utc(x['observedAt']) for x in observed]
    auth.require(all(x['pid'] == observed[0]['pid'] and x['evidenceOrigin'] == 'OWNER_CONTROLLED_NOT_INDEPENDENTLY_ATTESTED' for x in observed) and
                 auth.utc(final['verificationAt']) <= stamps[0] <= stamps[1] <= stamps[2], 'contradictory owner-controlled process ordering')
    at = stamps[0]  # Owner-controlled recorded start, NEVER independently attested.
    binding_sha = runtime.digest(evidence['binding'])
    expected = dict(contract=OWNER_CONTRACT, intentSha256=frozen['intentSha256'], executorId=evidence['binding']['executorId'],
                    ledgerBindingSha256=binding_sha, scope=auth.SCOPE, originalOnly=True, maxGames=1)
    owner = final['owner']; release = final['release']
    auth.decision_claims(owner, owner['id'], OWNER_CONTRACT, expected, at)
    released = dict(expected, contract=RELEASE_CONTRACT, ownerDecisionId=owner['id'], requestId=receipt['request'], holdSha256=runtime.digest(evidence['hold']))
    auth.decision_claims(release, release['id'], RELEASE_CONTRACT, released, at)
    auth.check_history(final['history'], owner, release, at, historical=True)
    auth.require(evidence['localConsumptionOnly'] is True and evidence['hold']['engineStarted'] is False and
                 evidence['hold']['originalSlotCreated'] is False and receipt['reservation'] is not None,
                 'missing original consumption/engine-free hold')
    auth.require(receipt['inventorySha256'] == runtime.digest(receipt['payloadInventory']) and
                 any(x['path'] == 'child.log' for x in receipt['payloadInventory']), 'historical original log/inventory missing')
    auth.require(auth.same_json(inventory_payloads(payload_root), receipt['payloadInventory']), 'historical payload missing/altered')
    states = [t['new_state'] for t in receipt['transitions']]
    auth.require(states == ['INTENT', 'OWNER_VERIFIED', 'HOLD', 'RELEASE_VERIFIED', 'RESERVED', 'CONSUMED'], 'historical transition correspondence')
    return dict(status='OWNER_CONTROLLED_RECORD_CORRESPONDENCE_ONLY', intentSha256=frozen['intentSha256'],
                independentExecutionTimeAttestation=False, authenticatedProductionOrigin=False,
                gameplayAuthorized=False, engineAllowed=False, permissionToLaunch=False, productionQualified=False,
                provenance=SYNTHETIC)


def main(argv=None):
    parser = argparse.ArgumentParser(description='Non-executing focused intent inspection; no backend activation')
    parser.add_argument('operation', choices=('inspect-intent', 'activation-status'))
    parser.add_argument('--intent', type=Path)
    args = parser.parse_args(argv)
    if args.operation == 'activation-status':
        print(json.dumps(dict(status='DISABLED', engineAllowed=False, gameplayAuthorized=False, productionQualified=False))); return 1
    try:
        auth.require(args.intent is not None, 'immutable intent file required')
        frozen = freeze_intent(args.intent.read_bytes())
        print(json.dumps(dict(status='INTENT_CORRESPONDENCE_ONLY', intentSha256=frozen['intentSha256'], engineAllowed=False, gameplayAuthorized=False, productionQualified=False)))
        return 0
    except (OSError, ValueError, TypeError, KeyError) as exc:
        print(json.dumps(dict(status='REFUSED', errors=[str(exc)], engineAllowed=False, gameplayAuthorized=False, productionQualified=False))); return 1


if __name__ == '__main__': raise SystemExit(main())
