"""Excluded issuer/collector correspondence contract. No trust bootstrap or execution."""
from dataclasses import dataclass
import json
from pathlib import Path
import re

import monster_tron_replication_seedfree as f
import monster_tron_replication_evidence as evidence

VERIFIER_HEAD = '94559a1c987dd096bd50a24be29d3e05e84f3a36'
VERIFIER_SHA256 = '8abd9f1df57c42dbb1fb9762053ab1902393db920eb65ce260c08e214a33646b'
PURPOSE = 'EXCLUDED_STRUCTURAL_SEMANTIC_ADMISSION_ONLY'
ACK = 'CHECK_EXCLUDED_PIN_ISSUER_COLLECTOR_CONTRACT_ONLY'


def _hash(value):
    return type(value) is str and re.fullmatch('[0-9a-f]{64}',value) is not None


def _label(value):
    return type(value) is str and re.fullmatch('EXCLUDED_[A-Z0-9_]{1,80}',value) is not None


@dataclass(frozen=True)
class FixtureTrustAnchor:
    """Caller-selected comparison values; explicitly not authenticated credentials."""
    issuer_id: str
    receipt_id: str
    issuance_sha256: str
    collector_id: str
    collection_id: str
    collection_sha256: str


@dataclass(frozen=True)
class FixtureAdmissionCheck:
    issuer_id: str
    receipt_id: str
    collector_id: str
    collection_id: str
    issuance_sha256: str
    collection_sha256: str
    evidence_manifest_sha256: str
    profile_sha256: str
    realm: str = f.REALM
    provenance_authenticated: bool = False
    historical_execution_proven: bool = False
    execution_authorized: bool = False


def _read_contract(raw, expected_digest):
    f.require(type(raw) is bytes and 0 < len(raw) <= 16384, 'missing/oversize contract')
    f.require(f.digest(raw) == expected_digest, 'external contract digest mismatch')
    try:
        d=json.loads(raw.decode('utf-8'),object_pairs_hook=f._object)
        f.require(type(d) is dict and f.canonical(d)==raw,'noncanonical contract')
        return d
    except (UnicodeError,json.JSONDecodeError,TypeError) as error:
        raise f.Refused('invalid contract encoding') from error


def verify_fixture_admission(root, issuance_raw, collection_raw, anchor, ack):
    """Read supplied receipts and existing fixture evidence, never create/resolve trust.

    A trusted caller must separately choose both digest pins and identities. Receipt
    claims and a coherent transcript are not authenticated provenance or historical proof.
    """
    f.require(ack==ACK,'excluded contract ACK required')
    f.require(type(anchor) is FixtureTrustAnchor,'explicit typed comparison anchor required')
    f.require(all(_label(x) for x in (anchor.issuer_id,anchor.receipt_id,anchor.collector_id,anchor.collection_id)),
              'excluded identity labels required')
    f.require(anchor.issuer_id!=anchor.collector_id,'distinct issuer/collector labels required')
    f.require(_hash(anchor.issuance_sha256) and _hash(anchor.collection_sha256),'external digest pins required')
    issuance=_read_contract(issuance_raw,anchor.issuance_sha256)
    collection=_read_contract(collection_raw,anchor.collection_sha256)
    f.require(set(issuance)=={'realm','purpose','issuer_id','receipt_id','consumer_head','consumer_sha256',
                             'pins','manifest_sha256','official_bindings'},'issuance fields')
    f.require(issuance['realm']==f.REALM and issuance['purpose']==PURPOSE,'issuance scope')
    f.require((issuance['issuer_id'],issuance['receipt_id'])==(anchor.issuer_id,anchor.receipt_id),'issuer/receipt mismatch')
    f.require((issuance['consumer_head'],issuance['consumer_sha256'])==(VERIFIER_HEAD,VERIFIER_SHA256),'consumer identity')
    f.require(f.digest(Path(evidence.__file__).read_bytes())==VERIFIER_SHA256,'consumer on-disk bytes drift')
    f.require(issuance['official_bindings']==dict(f.OFFICIAL_BINDINGS),'official bindings must be null')
    f.require(_hash(issuance['manifest_sha256']),'manifest pin')
    pins=issuance['pins']
    f.require(type(pins) is dict and set(pins)=={'profile_sha256','producer_head','producer_tree',
                  'checkout_inventory_sha256','tracked_files'},'complete exact producer pins required')
    expected_collection=dict(realm=f.REALM,purpose=PURPOSE,collector_id=anchor.collector_id,
        collection_id=anchor.collection_id,issuer_id=anchor.issuer_id,receipt_id=anchor.receipt_id,
        issuance_sha256=anchor.issuance_sha256,profile_sha256=pins['profile_sha256'],
        evidence_manifest_sha256=issuance['manifest_sha256'],evidence_files=126,
        provenance_authenticated=False,historical_execution_proven=False,execution_authorized=False)
    # Compare canonical bytes, so bool/int substitutions cannot pass dictionary equality.
    f.require(collection_raw==f.canonical(expected_collection),'collector correspondence/scope mismatch')
    result=evidence.verify_fixture_evidence(root,pins['profile_sha256'],pins['producer_head'],
        pins['producer_tree'],pins['checkout_inventory_sha256'],pins['tracked_files'])
    f.require(result.manifest_sha256==issuance['manifest_sha256'],'issued/collected/evidence manifest mismatch')
    return FixtureAdmissionCheck(anchor.issuer_id,anchor.receipt_id,anchor.collector_id,
        anchor.collection_id,anchor.issuance_sha256,anchor.collection_sha256,
        result.manifest_sha256,result.profile_sha256)


def load_official(*args, **kwargs):
    return f.load_official(*args, **kwargs)
