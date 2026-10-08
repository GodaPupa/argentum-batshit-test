"""Seed-free twelve-slot input/evidence machinery; intentionally no execution adapter.

Fixture labels are not seeds. No entropy, network, subprocess, claim creation, engine
initialization or gameplay callbacks exist. Official admission is unconditionally closed.
"""
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
from types import MappingProxyType

BASELINE = 'ed35c3035d41a740aac1657708b2023b624cfc47'
DESIGN = '8d32fcee736c9226be96b3bf3a657a877489c2ed'
CONTRACT_SHA256 = '8ee8d68b16ef2c56f54e76dc8284a5094a2aec27698582a9877f25512405737a'
REALM = 'EXCLUDED_SYNTHETIC_FIXTURE_ONLY'
OFFICIAL_BINDINGS = MappingProxyType(dict(source=None, tree=None, vector=None,
    assignments=None, archive=None, exclusions=None, claim=None, workflow=None,
    freeze_authority=None, execution_authority=None))

class Refused(ValueError):
    pass

def require(condition, reason):
    if not condition:
        raise Refused(reason)

def canonical(value):
    return (json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False)+'\n').encode()

def digest(raw):
    return hashlib.sha256(raw).hexdigest()

def _object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'duplicate JSON key')
        result[key] = value
    return result

@dataclass(frozen=True)
class FixtureSlot:
    number: int
    label: str
    pest_seat: int
    monster_seat: int
    starting_deck: str

def load_fixture(raw, expected_digest, observed_baseline):
    require(type(raw) is bytes and 0 < len(raw) <= 32768, 'missing/oversize fixture vector')
    require(observed_baseline == BASELINE, 'wrong observed source')
    require(type(expected_digest) is str and len(expected_digest)==64 and digest(raw)==expected_digest, 'fixture digest mismatch')
    try:
        d=json.loads(raw.decode('utf-8'), object_pairs_hook=_object)
        require(type(d) is dict and canonical(d)==raw, 'noncanonical fixture')
        require(set(d)=={'realm','baseline','design','contract_sha256','official_seeds','slots'}, 'fixture fields')
        require(d['realm']==REALM and d['baseline']==BASELINE and d['design']==DESIGN and d['contract_sha256']==CONTRACT_SHA256, 'fixture identity')
        require(d['official_seeds'] is None, 'official seeds prohibited')
        require(type(d['slots']) is list and len(d['slots'])==12, 'twelve fixture slots required')
        slots=[]
        for n,row in enumerate(d['slots'],1):
            seat=((n-1)%4)//2
            expected=dict(number=n,label=f'EXCLUDED_FIXTURE_SLOT_{n:02d}',pest_seat=seat,
                monster_seat=1-seat,starting_deck='PEST_CONTROL' if n%2 else 'MONSTER_TRON')
            require(type(row) is dict and row==expected, 'wrong fixture order/cell/label')
            require(all(type(row[k]) is int for k in ('number','pest_seat','monster_seat')), 'integer slot types')
            slots.append(FixtureSlot(**row))
        return tuple(slots)
    except (UnicodeError, json.JSONDecodeError, TypeError, KeyError) as error:
        raise Refused('invalid fixture encoding') from error

def validate_fixture_receipt(receipt, input_digest, observed_baseline):
    expected=dict(realm=REALM,claim='EXCLUDED_LOCAL_FIXTURE_NOT_A_CLAIM',run='EXCLUDED_FIXTURE_RUN',
        attempt=1,baseline=BASELINE,vector_digest=input_digest,reserved_slots=12,execution_allowed=False)
    require(type(receipt) is dict and receipt==expected, 'fixture receipt binding')
    require(type(receipt['attempt']) is int and type(receipt['reserved_slots']) is int and receipt['execution_allowed'] is False, 'fixture receipt types')
    require(observed_baseline==BASELINE, 'receipt observed source')

def load_official(*args, **kwargs):
    raise Refused('OFFICIAL_REPLICATION_UNALLOCATED_UNAUTHORIZED: later reviewed successor required')

def _force_directory(fd):
    os.fsync(fd)

class FixtureEvidenceCoordinator:
    """Records fixture events only. Cannot initialize, submit an action, or report a winner.

    One owner; no reopening/resumption. Any invalid transition or write/force ambiguity poisons
    the object permanently. Durable prefixes survive; no deletion, overwrite or fallback path.
    """
    def __init__(self, root, raw, expected_digest, observed_baseline, receipt):
        self.slots=load_fixture(raw,expected_digest,observed_baseline)
        validate_fixture_receipt(receipt,expected_digest,observed_baseline)
        root=Path(root)
        require(root.parent.resolve()==root.absolute().parent, 'aliased parent')
        self.poisoned=False
        self.closed=False
        self.game=1
        self.phase='READY'
        self.sequence=0
        self.pending=None
        self.index=0
        self.hashes={}
        self.fd=None
        # Existing roots (including symlinks) are never adopted or reused.
        root.mkdir(mode=0o700, exist_ok=False)
        try:
            self.fd=os.open(root,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW)
            parent=os.open(root.parent,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW)
            try: _force_directory(parent)
            finally: os.close(parent)
            self._write('fixture-input.json',raw)
            self._write('fixture-receipt.json',canonical(receipt))
        except BaseException:
            self.poisoned=True
            self.close()
            raise

    def close(self):
        if self.fd is not None:
            os.close(self.fd)
            self.fd=None
        self.closed=True

    def _write(self,name,raw):
        fd=os.open(name,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600,dir_fd=self.fd)
        try:
            with os.fdopen(fd,'wb',closefd=False) as stream:
                stream.write(raw)
                stream.flush()
                os.fsync(fd)
        finally: os.close(fd)
        _force_directory(self.fd)
        self.hashes[name]=digest(raw)

    def _guard(self):
        require(not self.closed and not self.poisoned,'closed or poisoned fixture boundary')

    def _event(self,kind,payload):
        name=f'{self.index+1:05d}-{kind}.json'
        self._write(name,canonical(dict(realm=REALM,index=self.index+1,slot=self.game,kind=kind,payload=payload)))
        self.index+=1

    def transition(self,kind,slot,token=None):
        self._guard()
        try:
            require(type(slot) is int and slot==self.game and slot<=12,'out of order fixture slot')
            if kind=='ATTEMPT':
                require(self.phase=='READY' and token is None,'attempt phase')
                self._event(kind,dict(label=self.slots[slot-1].label));self.phase='ATTEMPTED'
            elif kind=='INITIALIZATION_ENTRY':
                require(self.phase=='ATTEMPTED' and token is None,'initialization entry phase')
                self._event(kind,{});self.phase='ENTERED'
            elif kind=='FIXTURE_INITIALIZED':
                require(self.phase=='ENTERED' and token is None,'fixture initialization phase')
                self._event(kind,dict(no_engine_called=True));self.phase='ACTIVE'
            elif kind=='INTENT':
                require(self.phase=='ACTIVE' and self.pending is None,'intent phase')
                require(type(token) is str and token==f'EXCLUDED_ACTION_{self.sequence+1:05d}','fixture action token')
                self._event(kind,dict(sequence=self.sequence+1,token=token));self.pending=token
            elif kind=='RESULT':
                require(self.phase=='ACTIVE' and self.pending is not None and token==self.pending,'result must match durable intent')
                self._event(kind,dict(sequence=self.sequence+1,token=token,no_engine_called=True))
                self.sequence+=1;self.pending=None
            elif kind=='FIXTURE_RECORD':
                require(self.phase=='ACTIVE' and self.pending is None and self.sequence>0 and token is None,'incomplete fixture record')
                self._event(kind,dict(action_pairs=self.sequence,outcome=None,no_game_played=True))
                self.game+=1;self.phase='READY';self.sequence=0
            else:
                raise Refused('unknown fixture transition')
        except BaseException:
            self.poisoned=True
            raise

    def finish(self):
        self._guard()
        try:
            require(self.game==13 and self.phase=='READY' and self.pending is None,'incomplete twelve-slot evidence')
            require(set(os.listdir(self.fd))==set(self.hashes),'unexpected or missing evidence')
            for name,h in self.hashes.items():
                fd=os.open(name,os.O_RDONLY|os.O_NOFOLLOW,dir_fd=self.fd)
                with os.fdopen(fd,'rb') as stream: raw=stream.read()
                require(digest(raw)==h,'evidence corruption')
            self._write('fixture-summary.json',canonical(dict(realm=REALM,recorded_slots=list(range(1,13)),official_initializations=0,official_actions=0,official_outcomes=0,execution_authorized=False)))
            inventory=''.join(f'{h}  {name}\n' for name,h in sorted(self.hashes.items())).encode()
            self._write('fixture-artifacts.sha256',inventory)
            self.close()
            return digest(inventory)
        except BaseException:
            self.poisoned=True
            raise
