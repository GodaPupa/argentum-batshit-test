"""Engine-free, UNINTEGRATED local staged-authority infrastructure prototype.

No engine imports, launch callback, trace creator, network client, CLI, credentials,
workflow, production backend or gameplay permission exists in this module.
SQLite provides single-file local transaction correctness, not independent time,
host attestation or cross-host production enforcement. Documents never install a
trust backend. Excluded tests inject trusted *test code* and retain its unmistakable
unauthenticated provenance; the production default is always DENY.
"""
from __future__ import annotations

from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import sqlite3
from typing import Protocol

import gameplay_authority as authority

SYNTHETIC = 'SYNTHETIC_TRUSTED_FIXTURE_NOT_AUTHENTICATED'
LOCAL_LEDGER = 'LOCAL_SQLITE_TRANSACTION_NOT_TRUSTED_TIME_OR_CROSS_HOST'
STATES = ('INTENT', 'OWNER_VERIFIED', 'HOLD', 'RELEASE_VERIFIED', 'RESERVED',
          'CONSUMED', 'CANCELLED', 'FAILED', 'RECOVERY_REQUIRED')
TERMINAL = frozenset(('CONSUMED', 'CANCELLED', 'FAILED', 'RECOVERY_REQUIRED'))
ALLOWED = {
    'INTENT': frozenset(('OWNER_VERIFIED', 'CANCELLED', 'FAILED')),
    'OWNER_VERIFIED': frozenset(('HOLD', 'CANCELLED', 'FAILED')),
    'HOLD': frozenset(('RELEASE_VERIFIED', 'CANCELLED', 'FAILED')),
    'RELEASE_VERIFIED': frozenset(('RESERVED', 'CANCELLED', 'FAILED')),
    'RESERVED': frozenset(('CONSUMED', 'RECOVERY_REQUIRED')),
    'CONSUMED': frozenset(), 'CANCELLED': frozenset(), 'FAILED': frozenset(),
    'RECOVERY_REQUIRED': frozenset(),
}


class Denied(ValueError):
    """Fail-closed refusal. No caller should convert this into a launch."""


def require(value, message):
    if not value:
        raise Denied(message)


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False)


def digest(value):
    return hashlib.sha256(canonical(value).encode()).hexdigest()


def strict_keys(value, keys, name):
    require(type(value) is dict and set(value) == set(keys), 'incomplete/ambiguous ' + name)


class IdentityBoundary(Protocol):
    """Separately installed trusted-code interfaces, NOT fields in an artifact.

    A production implementation must authenticate transport/issuer, provider-assigned
    run/head/ref, host/code measurement, complete VERSION history and immutable time
    anchors independently. None is supplied here. The methods cannot be implemented
    by merely parsing a caller document, environment or local ledger/hash chain.
    """
    origin: str

    def now(self) -> datetime: ...
    def authenticate_owner(self, reference: str) -> dict: ...
    def authenticate_hold(self, reference: str) -> dict: ...
    def authenticate_release(self, reference: str) -> dict: ...
    def authenticate_historical(self, reference: str) -> dict: ...


class UnavailableBoundary:
    origin = 'UNAVAILABLE'

    def __getattr__(self, name):
        raise Denied('UNQUALIFIED: trusted provider/owner/host/time boundary unavailable')


class ClosingConnection(sqlite3.Connection):
    """The stdlib connection context commits/rolls back but does not close."""
    def __exit__(self, *args):
        try:
            return super().__exit__(*args)
        finally:
            self.close()


def validate_provider(provider, intent, *, held):
    strict_keys(provider, ('origin', 'repository', 'run', 'sourceVerified',
                           'identityReference', 'engineStarted', 'originalSlotCreated'), 'provider identity')
    require(provider['origin'] == SYNTHETIC, 'unqualified provider evidence origin')
    require(provider['repository'] == authority.REPOSITORY and
            provider['sourceVerified'] is True and
            type(provider['identityReference']) is str and bool(provider['identityReference']),
            'missing independently authenticated provider/source identity')
    authority.check_run(intent, provider['run'])
    require(provider['engineStarted'] is False and provider['originalSlotCreated'] is False,
            'hold must be engine-free and must not create an original slot')
    require(held is True, 'provider must be verified as an engine-free hold')
    return provider['run']


def validate_host(host, run):
    strict_keys(host, ('origin', 'hostReference', 'codeReference', 'runId', 'sourceCommit',
                       'sourceTree', 'validationCommit', 'validationTree', 'codeClean',
                       'engineStarted', 'originalSlotCreated'), 'host/code identity')
    require(host['origin'] == SYNTHETIC, 'unqualified host evidence origin')
    require(type(host['hostReference']) is str and bool(host['hostReference']) and
            type(host['codeReference']) is str and bool(host['codeReference']),
            'missing independent host/code identity')
    require(host['codeClean'] is True and host['engineStarted'] is False and
            host['originalSlotCreated'] is False, 'host/code ambiguous or already initialized')
    for key in ('runId', 'sourceCommit', 'sourceTree', 'validationCommit', 'validationTree'):
        require(authority.same_json(host[key], run[key]), 'host/provider identity contradiction: ' + key)


def validate_history(bundle, owner, release, at, *, historical):
    strict_keys(bundle, ('origin', 'events', 'completeThrough', 'versionHistoryComplete',
                         'orderingVerified', 'historyReference'), 'decision-version history')
    require(bundle['origin'] == SYNTHETIC and bundle['versionHistoryComplete'] is True and
            bundle['orderingVerified'] is True and type(bundle['historyReference']) is str and
            bool(bundle['historyReference']) and authority.utc(bundle['completeThrough']) >= at,
            'history completeness/order not independently authenticated through execution')
    events = bundle['events']
    require(type(events) is list, 'complete ordered history required')
    stamps = []
    versions = {}
    for event in events:
        require(type(event) is dict and type(event.get('id')) is int and
                type(event.get('user')) is dict and type(event['user'].get('id')) is int,
                'ambiguous history issuer/event identity')
        stamp = event.get('updated_at') or event.get('submitted_at') or event.get('created_at')
        require(stamp is not None, 'undated controlling event')
        parsed = authority.utc(stamp)
        if parsed <= at:
            stamps.append(parsed)
        identity = (event['id'], event['user']['id'], stamp)
        require(identity not in versions or authority.same_json(versions[identity], event),
                'contradictory event version at authenticated time')
        require(identity not in versions, 'duplicate/ambiguous decision history version')
        versions[identity] = event
    require(stamps == sorted(stamps), 'decision history out of authenticated ordering')
    authority.check_history(events, owner, release, at, historical=historical)


def validate_owner(bundle, h, at):
    strict_keys(bundle, ('origin', 'decision', 'history'), 'authenticated owner decision')
    require(bundle['origin'] == SYNTHETIC, 'unqualified owner issuer evidence')
    decision = bundle['decision']
    require(type(decision) is dict, 'missing authenticated owner decision')
    authority.decision_claims(decision, decision.get('id'), authority.OWNER_CONTRACT,
                              authority.owner_expected(h), at)
    history = bundle['history']
    strict_keys(history, ('origin', 'events', 'completeThrough', 'versionHistoryComplete',
                          'orderingVerified', 'historyReference'), 'owner history')
    require(history['origin'] == SYNTHETIC and history['versionHistoryComplete'] is True and
            history['orderingVerified'] is True and authority.utc(history['completeThrough']) >= at,
            'owner history incomplete/unauthenticated')
    require(any(authority.same_json(e, decision) for e in history['events']), 'decision absent from authenticated history')
    for event in history['events']:
        if event.get('user', {}).get('id') == authority.OWNER_ID and event.get('id') != decision['id']:
            stamp = event.get('updated_at') or event.get('submitted_at') or event.get('created_at')
            require(stamp is not None and authority.utc(stamp) < authority.utc(decision['updated_at']),
                    'owner decision superseded before hold')
    return decision


def validate_release(bundle, frozen, prior_owner, hold, at):
    strict_keys(bundle, ('origin', 'owner', 'release', 'history', 'provider', 'host'), 'run-specific release')
    require(bundle['origin'] == SYNTHETIC, 'release issuer/host provenance unavailable')
    run = validate_provider(bundle['provider'], frozen['intent'], held=True)
    validate_host(bundle['host'], run)
    require(authority.same_json(bundle['provider'], hold['provider']) and
            authority.same_json(bundle['host'], hold['host']), 'provider/host substituted after hold')
    owner, release = bundle['owner'], bundle['release']
    require(authority.same_json(owner, prior_owner), 'owner decision changed after staged verification')
    authority.decision_claims(owner, owner['id'], authority.OWNER_CONTRACT,
                              authority.owner_expected(frozen['intentSha256']), at)
    authority.decision_claims(release, release.get('id'), authority.RELEASE_CONTRACT,
                              authority.release_expected(frozen['intentSha256'], owner['id'], run), at)
    observed = authority.utc(hold['observedAt'])
    require(owner['id'] != release['id'] and authority.utc(owner['updated_at']) <= observed <=
            authority.utc(release['created_at']) <= at and
            0 <= (at - authority.utc(release['updated_at'])).total_seconds() <= authority.MAX_RECORD_AGE_SECONDS and
            0 <= (at - observed).total_seconds() <= authority.MAX_RECORD_AGE_SECONDS,
            'interrupted/out-of-order/stale run release')
    validate_history(bundle['history'], owner, release, at, historical=False)
    return run


class LocalLedger:
    """Durable SQLite prototype: BEGIN IMMEDIATE + UNIQUE original key.

    One SQLite file on a supported local filesystem serializes competing processes.
    A successful commit durably records one reservation/consumption. An uncommitted
    transaction rolls back after exceptions or process death. No lease expiry or
    automatic reuse of RESERVED/CANCELLED/FAILED keys is allowed. The audit sequence
    records ordering, not independently trusted execution time. Malicious hosts,
    copied/deleted databases and cross-host operation are outside these guarantees.
    """
    def __init__(self, path):
        self.path = Path(path)
        require(self.path.parent.is_dir() and not self.path.is_symlink(), 'unsafe/missing local ledger path')
        with self.connection() as db:
            db.executescript('''
              CREATE TABLE IF NOT EXISTS attempts (
                request TEXT PRIMARY KEY, original_key TEXT NOT NULL UNIQUE,
                intent_hash TEXT NOT NULL, intent BLOB NOT NULL, state TEXT NOT NULL,
                evidence TEXT NOT NULL, reservation TEXT UNIQUE, reason TEXT NOT NULL DEFAULT '');
              CREATE TABLE IF NOT EXISTS audit (
                seq INTEGER PRIMARY KEY AUTOINCREMENT, request TEXT NOT NULL,
                old_state TEXT, new_state TEXT NOT NULL, evidence_digest TEXT NOT NULL);
              CREATE TABLE IF NOT EXISTS format (
                singleton INTEGER PRIMARY KEY CHECK(singleton=1), version INTEGER NOT NULL);
              INSERT OR IGNORE INTO format VALUES(1, 1);
            ''')
            require(db.execute('SELECT version FROM format WHERE singleton=1').fetchone()[0] == 1,
                    'unknown ledger version; recovery denied')

    def connection(self):
        db = sqlite3.connect(str(self.path), timeout=30, isolation_level=None, factory=ClosingConnection)
        db.row_factory = sqlite3.Row
        db.execute('PRAGMA busy_timeout=30000')
        db.execute('PRAGMA synchronous=FULL')
        require(db.execute('PRAGMA journal_mode=WAL').fetchone()[0].lower() == 'wal', 'WAL unavailable')
        return db

    @contextmanager
    def transaction(self):
        db = self.connection()
        try:
            db.execute('BEGIN IMMEDIATE')
            yield db
            db.execute('COMMIT')
        except BaseException:
            if db.in_transaction:
                db.execute('ROLLBACK')
            raise
        finally:
            db.close()

    @staticmethod
    def row(db, request):
        result = db.execute('SELECT * FROM attempts WHERE request=?', (request,)).fetchone()
        require(result is not None, 'unknown attempt; interrupted sequence')
        return dict(result)

    @staticmethod
    def transition(db, row, target, evidence, *, reservation=None, reason=''):
        require(target in ALLOWED[row['state']], 'prohibited/out-of-order/terminal transition')
        text = canonical(evidence)
        db.execute('UPDATE attempts SET state=?, evidence=?, reservation=COALESCE(?,reservation), reason=? WHERE request=? AND state=?',
                   (target, text, reservation, reason, row['request'], row['state']))
        require(db.execute('SELECT changes()').fetchone()[0] == 1, 'lost state transition')
        db.execute('INSERT INTO audit(request,old_state,new_state,evidence_digest) VALUES(?,?,?,?)',
                   (row['request'], row['state'], target, digest(evidence)))

    def begin(self, request, intent_bytes):
        require(type(request) is str and 1 <= len(request) <= 200, 'bounded nonempty request ID required')
        frozen = authority.freeze_intent(intent_bytes)
        # A logical original is scoped by block/game, independently of serialization,
        # workflow/ref/source revisions and request/provider IDs. Changing the plan,
        # seed label or source cannot turn that original into a reusable slot. These
        # are only excluded ledger keys, NEVER gameplay allocations or trace slots.
        case = frozen['intent']['case']
        original_key = digest({key: case[key] for key in ('scope', 'blockId', 'gameId')})
        with self.transaction() as db:
            existing = db.execute('SELECT * FROM attempts WHERE request=?', (request,)).fetchone()
            if existing:
                require(existing['intent_hash'] == frozen['intentSha256'] and bytes(existing['intent']) == intent_bytes,
                        'idempotency key reused with altered intent')
                return dict(existing)
            try:
                db.execute('INSERT INTO attempts(request,original_key,intent_hash,intent,state,evidence) VALUES(?,?,?,?,?,?)',
                           (request, original_key, frozen['intentSha256'], intent_bytes, 'INTENT', '{}'))
            except sqlite3.IntegrityError as error:
                raise Denied('original already reserved/consumed/quarantined; no duplicate request') from error
            db.execute('INSERT INTO audit(request,old_state,new_state,evidence_digest) VALUES(?,?,?,?)',
                       (request, None, 'INTENT', digest({})))
            return self.row(db, request)

    def inspect(self, request):
        with self.connection() as db:
            row = self.row(db, request)
            row['evidence'] = json.loads(row['evidence'])
            row['engineAllowed'] = False
            row['origin'] = LOCAL_LEDGER
            return row

    def cancel(self, request):
        with self.transaction() as db:
            row = self.row(db, request)
            if row['state'] == 'CANCELLED':
                return 'CANCELLED'  # Idempotent, permanently quarantined.
            if row['state'] == 'RESERVED':
                self.transition(db, row, 'RECOVERY_REQUIRED', json.loads(row['evidence']), reason='ambiguous reserved cancellation')
                return 'RECOVERY_REQUIRED'
            self.transition(db, row, 'CANCELLED', json.loads(row['evidence']), reason='pre-consumption cancellation; key remains quarantined')
            return 'CANCELLED'

    def recover_interrupted(self, request):
        """No auto-reuse after restart. Separate owner disposition remains required."""
        with self.transaction() as db:
            row = self.row(db, request)
            if row['state'] == 'RECOVERY_REQUIRED':
                return row['state']
            require(row['state'] == 'RESERVED', 'only ambiguous committed reservations require recovery')
            self.transition(db, row, 'RECOVERY_REQUIRED', json.loads(row['evidence']), reason='interrupted committed reservation')
            return 'RECOVERY_REQUIRED'

    def fail(self, request, reason):
        with self.transaction() as db:
            row = self.row(db, request)
            target = 'RECOVERY_REQUIRED' if row['state'] == 'RESERVED' else 'FAILED'
            if row['state'] in TERMINAL:
                return row['state']
            self.transition(db, row, target, json.loads(row['evidence']), reason=reason)
            return target


class StagedRuntime:
    """Local prototype only. No launch exists even after synthetic consumption.

    `_synthetic_fixture_backend` is an excluded-test CODE seam, never a JSON option,
    CLI, variable or production registration mechanism. Its accepted origin is
    deliberately unauthenticated. A future production implementation must replace
    this boundary under separate authorization and independent review.
    """
    def __init__(self, ledger, *, _synthetic_fixture_backend=None):
        require(isinstance(ledger, LocalLedger), 'missing transactional ledger/reservation')
        self.ledger = ledger
        self.boundary = _synthetic_fixture_backend or UnavailableBoundary()

    def _trusted(self):
        require(self.boundary.origin == SYNTHETIC,
                'UNQUALIFIED: trusted provider/owner/host/time backend unavailable')
        now = self.boundary.now()
        require(isinstance(now, datetime) and now.tzinfo == timezone.utc, 'trusted UTC observation missing')
        return now

    def _step(self, request, expected, target, validate, *, reference):
        """Validation runs inside the write transaction; failure persists quarantine.

        Backend calls here are bounded in-memory fixtures. A future real provider
        service must specify deadlines/revalidation without holding an unbounded
        remote request inside a DB lock; this is not a production integration.
        """
        error = None
        with self.ledger.transaction() as db:
            row = self.ledger.row(db, request)
            if row['state'] != expected:
                target_failure = 'RECOVERY_REQUIRED' if row['state'] == 'RESERVED' else 'FAILED'
                if row['state'] not in TERMINAL:
                    self.ledger.transition(db, row, target_failure, json.loads(row['evidence']), reason='out-of-order stage')
                error = Denied('prohibited/out-of-order/terminal transition')
            else:
                try:
                    at = self._trusted()
                    evidence = validate(row, at, reference)
                    self.ledger.transition(db, row, target, evidence)
                except (ValueError, KeyError, TypeError, AttributeError) as exc:
                    self.ledger.transition(db, row, 'FAILED', json.loads(row['evidence']), reason=str(exc))
                    error = Denied(str(exc))
        if error:
            raise error
        return self.ledger.inspect(request)

    def owner_verified(self, request, owner_reference):
        def validate(row, at, reference):
            frozen = authority.freeze_intent(bytes(row['intent']))
            owner = validate_owner(self.boundary.authenticate_owner(reference), frozen['intentSha256'], at)
            return {'owner': owner}
        return self._step(request, 'INTENT', 'OWNER_VERIFIED', validate, reference=owner_reference)

    def hold(self, request, hold_reference):
        def validate(row, at, reference):
            frozen = authority.freeze_intent(bytes(row['intent']))
            evidence = json.loads(row['evidence'])
            hold = self.boundary.authenticate_hold(reference)
            strict_keys(hold, ('origin', 'intentSha256', 'provider', 'host', 'observedAt'), 'engine-free hold')
            require(hold['origin'] == SYNTHETIC and hold['intentSha256'] == frozen['intentSha256'], 'hold substituted/unauthenticated')
            run = validate_provider(hold['provider'], frozen['intent'], held=True)
            validate_host(hold['host'], run)
            require(authority.utc(evidence['owner']['updated_at']) <= authority.utc(hold['observedAt']) <= at,
                    'hold interrupted or precedes owner decision')
            evidence['hold'] = hold
            return evidence
        return self._step(request, 'OWNER_VERIFIED', 'HOLD', validate, reference=hold_reference)

    def release(self, request, release_reference):
        def validate(row, at, reference):
            frozen = authority.freeze_intent(bytes(row['intent']))
            evidence = json.loads(row['evidence'])
            released = self.boundary.authenticate_release(reference)
            validate_release(released, frozen, evidence['owner'], evidence['hold'], at)
            evidence['release'] = released
            evidence['releaseReference'] = reference
            return evidence
        return self._step(request, 'HOLD', 'RELEASE_VERIFIED', validate, reference=release_reference)

    def reserve(self, request, reservation, *, _before_commit=None):
        require(type(reservation) is str and 1 <= len(reservation) <= 200, 'bounded reservation ID required')
        error = None
        with self.ledger.transaction() as db:
            row = self.ledger.row(db, request)
            if row['state'] == 'RESERVED':
                require(row['reservation'] == reservation, 'different reservation conflicts with committed original')
                return self.ledger.row(db, request)
            require(row['state'] == 'RELEASE_VERIFIED', 'original reservation requires independently verified run release')
            try:
                at = self._trusted()
                evidence = json.loads(row['evidence'])
                # Reauthenticate live test facts immediately before reservation.
                released = self.boundary.authenticate_release(evidence['releaseReference'])
                validate_release(released, authority.freeze_intent(bytes(row['intent'])), evidence['owner'], evidence['hold'], at)
                self.ledger.transition(db, row, 'RESERVED', evidence, reservation=reservation)
                if _before_commit is not None:
                    _before_commit(db)
            except (ValueError, KeyError, TypeError, AttributeError) as exc:
                self.ledger.transition(db, row, 'FAILED', json.loads(row['evidence']), reason=str(exc))
                error = Denied(str(exc))
        if error:
            raise error
        return self.ledger.inspect(request)

    def consume(self, request, reservation):
        """Mark one local prototype reservation consumed; NEVER initializes a game.

        Consumption is a database state, not an original gameplay slot or trusted
        execution-time anchor. A real launcher still needs separately reviewed
        host identity, cross-host ledger and immutable external execution evidence.
        """
        error = None
        with self.ledger.transaction() as db:
            row = self.ledger.row(db, request)
            require(row['reservation'] == reservation, 'missing/cross-bound reservation')
            if row['state'] == 'CONSUMED':
                return self.ledger.row(db, request)  # Idempotent response, no new side effect.
            require(row['state'] == 'RESERVED', 'consumption without reserved verified release')
            try:
                at = self._trusted()
                evidence = json.loads(row['evidence'])
                released = self.boundary.authenticate_release(evidence['releaseReference'])
                validate_release(released, authority.freeze_intent(bytes(row['intent'])), evidence['owner'], evidence['hold'], at)
                evidence['localConsumptionOnly'] = True
                evidence['timeIsNotAttested'] = True
                self.ledger.transition(db, row, 'CONSUMED', evidence)
            except (ValueError, KeyError, TypeError, AttributeError) as exc:
                self.ledger.transition(db, row, 'RECOVERY_REQUIRED', json.loads(row['evidence']), reason=str(exc))
                error = Denied(str(exc))
        if error:
            raise error
        return self.ledger.inspect(request)


def inspect_historical(intent_bytes, run, record, manifest_sha256, *, _synthetic_fixture_backend=None):
    """Independently fetched execution-time contract, never a ledger time claim.

    The caller supplies only an opaque anchor reference and exact correspondence
    pins. Backend authentication of immutable anchor and time is mandatory. Hashes
    below detect alterations but do not authenticate themselves. Only excluded
    synthetic code is installable here; default production calls fail closed.
    """
    require(_synthetic_fixture_backend is not None and _synthetic_fixture_backend.origin == SYNTHETIC,
            'UNQUALIFIED: independent immutable execution-time anchor unavailable')
    require(type(record) is dict and set(record) == {'contract', 'intentSha256', 'ownerDecisionId', 'releaseDecisionId', 'anchorRef'},
            'unsigned/ambiguous/self-declared historical record')
    envelope = _synthetic_fixture_backend.authenticate_historical(record['anchorRef'])
    strict_keys(envelope, ('origin', 'immutableReference', 'timeReference', 'timeIndependentlyVerified',
                           'anchorDigest', 'history', 'facts'), 'immutable execution anchor')
    require(envelope['origin'] == SYNTHETIC and type(envelope['immutableReference']) is str and
            bool(envelope['immutableReference']) and type(envelope['timeReference']) is str and
            bool(envelope['timeReference']) and envelope['timeIndependentlyVerified'] is True,
            'missing independently authenticated immutable/time anchor')
    facts = envelope['facts']
    require(envelope['anchorDigest'] == digest(facts), 'execution provenance altered against immutable anchor')
    at = authority.utc(facts['executedAt'])
    validate_history(envelope['history'], facts['owner'], facts['release'], at, historical=True)
    require(authority.same_json(envelope['history']['events'], facts['history']) and
            envelope['history']['completeThrough'] == facts['historyCompleteThrough'], 'history cross-bound to altered anchor')

    class NormalizedAnchor:
        origin = SYNTHETIC
        def authenticate_historical(self, reference):
            require(reference == record['anchorRef'], 'anchor reference changed during receiving')
            return facts

    result = authority.verify_historical(intent_bytes, run, record, manifest_sha256, _anchor=NormalizedAnchor())
    result['independentlyTrustedProductionOrigin'] = False
    result['engineAllowed'] = False
    result['localLedgerAuthenticatesTime'] = False
    return result
