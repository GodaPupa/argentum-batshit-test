"""Excluded local receipt copies only; no issuer enrollment, signatures, Git or producer."""
from dataclasses import asdict, replace, FrozenInstanceError
import json
import os
from pathlib import Path
import shutil
import unittest
from unittest.mock import patch

import monster_tron_replication_pin_contract as m
import monster_tron_replication_seedfree as f
from test_monster_tron_replication_evidence import PINS


def contracts():
    issuance=dict(realm=f.REALM,purpose=m.PURPOSE,issuer_id='EXCLUDED_ISSUER_A',receipt_id='EXCLUDED_RECEIPT_01',
        consumer_head=m.VERIFIER_HEAD,consumer_sha256=m.VERIFIER_SHA256,
        pins=dict(zip(('profile_sha256','producer_head','producer_tree','checkout_inventory_sha256','tracked_files'),PINS)),
        manifest_sha256='82124243d93ea0427f0bd04cae2d318b6e569c6551ebd73af7bcef60c349c15c',official_bindings=dict(f.OFFICIAL_BINDINGS))
    collection=dict(realm=f.REALM,purpose=m.PURPOSE,collector_id='EXCLUDED_COLLECTOR_A',collection_id='EXCLUDED_COLLECTION_01',
        issuer_id=issuance['issuer_id'],receipt_id=issuance['receipt_id'],issuance_sha256=f.digest(f.canonical(issuance)),
        profile_sha256=PINS[0],evidence_manifest_sha256=issuance['manifest_sha256'],evidence_files=126,
        provenance_authenticated=False,historical_execution_proven=False,execution_authorized=False)
    return issuance,collection


def anchor(issuance,collection):
    return m.FixtureTrustAnchor(issuance['issuer_id'],issuance['receipt_id'],f.digest(f.canonical(issuance)),
        collection['collector_id'],collection['collection_id'],f.digest(f.canonical(collection)))


class PinContractTests(unittest.TestCase):
    def setUp(self):
        self.area=Path(os.environ['PEST_PIN_TEST_OUTPUT']).absolute()/self._testMethodName
        self.area.mkdir(parents=True,exist_ok=False);self.root=self.area/'evidence'
        shutil.copytree(Path(os.environ['PEST_SEALED_SOURCE_EVIDENCE']),self.root,symlinks=True)
        self.i,self.c=contracts();self.a=anchor(self.i,self.c)
        self.entropy=patch.object(os,'urandom',side_effect=AssertionError('entropy prohibited'));self.entropy.start()
    def tearDown(self):
        (self.area/'issuance.json').write_bytes(f.canonical(self.i));(self.area/'collection.json').write_bytes(f.canonical(self.c))
        (self.area/'anchor.json').write_bytes(f.canonical(asdict(self.a)));self.entropy.stop()
    def verify(self,ack=m.ACK):
        return m.verify_fixture_admission(self.root,f.canonical(self.i),f.canonical(self.c),self.a,ack)
    def repin(self):
        # Deliberately model caller choosing new pins, never real issuer/controller authority.
        self.c['issuance_sha256']=f.digest(f.canonical(self.i));self.a=anchor(self.i,self.c)
    def test_exact_pair_no_writes_no_authority(self):
        before={p.name:p.read_bytes() for p in self.root.iterdir()};r=self.verify()
        self.assertEqual(before,{p.name:p.read_bytes() for p in self.root.iterdir()})
        self.assertFalse(r.execution_authorized);self.assertFalse(r.provenance_authenticated);self.assertFalse(r.historical_execution_proven)
        with self.assertRaises(FrozenInstanceError):r.execution_authorized=True
    def test_coherent_relabelled_pair_does_not_authenticate_itself(self):
        self.i['issuer_id']='EXCLUDED_OTHER_ISSUER';self.c['issuer_id']=self.i['issuer_id'];self.repin()
        result=self.verify();self.assertFalse(result.provenance_authenticated);self.assertFalse(result.historical_execution_proven)
    def test_changed_receipt_without_external_repin(self):
        self.i['receipt_id']='EXCLUDED_OTHER_RECEIPT'
        with self.assertRaises(f.Refused):self.verify()
    def test_wrong_anchor_identities(self):
        for key in ('issuer_id','receipt_id','collector_id','collection_id'):
            old=self.a;self.a=replace(old,**{key:'EXCLUDED_WRONG'})
            with self.subTest(key=key),self.assertRaises(f.Refused):self.verify()
            self.a=old
    def test_wrong_anchor_digests(self):
        for key in ('issuance_sha256','collection_sha256'):
            old=self.a;self.a=replace(old,**{key:'0'*64})
            with self.subTest(key=key),self.assertRaises(f.Refused):self.verify()
            self.a=old
    def test_missing_anchor(self):
        self.a=None
        with self.assertRaises(f.Refused):self.verify()
        self.a=anchor(self.i,self.c)
    def test_official_identity_labels(self):
        self.a=replace(self.a,issuer_id='OFFICIAL_REVIEWER')
        with self.assertRaises(f.Refused):self.verify()
    def test_same_issuer_collector_refused(self):
        self.c['collector_id']=self.i['issuer_id'];self.repin()
        with self.assertRaises(f.Refused):self.verify()
    def test_stale_collection_for_new_issuance(self):
        self.i['receipt_id']='EXCLUDED_RECEIPT_02';self.a=anchor(self.i,self.c)
        with self.assertRaises(f.Refused):self.verify()
    def test_wrong_consumer_source(self):
        self.i['consumer_head']='0'*40;self.repin()
        with self.assertRaises(f.Refused):self.verify()
    def test_wrong_consumer_hash(self):
        self.i['consumer_sha256']='0'*64;self.repin()
        with self.assertRaises(f.Refused):self.verify()
    def test_consumer_disk_drift(self):
        with patch.object(m.Path,'read_bytes',return_value=b'changed'):
            with self.assertRaises(f.Refused):self.verify()
    def test_activation_purpose_refused(self):
        self.i['purpose']='ACTIVATE_OFFICIAL_EXECUTION';self.c['purpose']=self.i['purpose'];self.repin()
        with self.assertRaises(f.Refused):self.verify()
    def test_populated_official_binding(self):
        self.i['official_bindings']['claim']='invented';self.repin()
        with self.assertRaises(f.Refused):self.verify()
    def test_unrecognized_signature_field_refused(self):
        self.i['signature']='not-authentication';self.repin()
        with self.assertRaises(f.Refused):self.verify()
    def test_collector_permission_and_historical_claims_refused(self):
        for key in ('execution_authorized','historical_execution_proven','provenance_authenticated'):
            self.c[key]=True;self.repin()
            with self.subTest(key=key),self.assertRaises(f.Refused):self.verify()
            self.c[key]=False
    def test_collector_integer_false_substitution(self):
        self.c['execution_authorized']=0;self.repin()
        with self.assertRaises(f.Refused):self.verify()
    def test_wrong_collector_member_count(self):
        self.c['evidence_files']=125;self.repin()
        with self.assertRaises(f.Refused):self.verify()
    def test_manifest_correspondence_refused(self):
        self.c['evidence_manifest_sha256']='0'*64;self.repin()
        with self.assertRaises(f.Refused):self.verify()
    def test_both_manifest_claims_wrong(self):
        self.i['manifest_sha256']='0'*64;self.c['evidence_manifest_sha256']='0'*64;self.repin()
        with self.assertRaises(f.Refused):self.verify()
    def test_profile_correspondence_refused(self):
        self.c['profile_sha256']='0'*64;self.repin()
        with self.assertRaises(f.Refused):self.verify()
    def test_missing_producer_pin(self):
        del self.i['pins']['producer_tree'];self.repin()
        with self.assertRaises(f.Refused):self.verify()
    def test_wrong_producer_source(self):
        self.i['pins']['producer_head']='0'*40;self.repin()
        with self.assertRaises(f.Refused):self.verify()
    def test_incomplete_evidence(self):
        (self.root/'fixture-summary.json').unlink()
        with self.assertRaises(f.Refused):self.verify()
    def test_forged_evidence_with_fresh_checksums(self):
        p=self.root/'fixture-raw-01-01.json';d=json.loads(p.read_text());d['outcome']='invented';p.write_bytes(f.canonical(d))
        manifest=''.join(f'{f.digest(p.read_bytes())}  {p.name}\n' for p in sorted(self.root.iterdir()) if p.name!='fixture-artifacts.sha256').encode()
        (self.root/'fixture-artifacts.sha256').write_bytes(manifest)
        self.i['manifest_sha256']=f.digest(manifest);self.c['evidence_manifest_sha256']=f.digest(manifest);self.repin()
        with self.assertRaises(f.Refused):self.verify()
    def test_noncanonical_duplicate_missing_contract(self):
        for raw in (b'{"x":1,"x":2}\n',b'{}',b'\xff',b'',b'x'*16385):
            a=replace(self.a,issuance_sha256=f.digest(raw))
            with self.subTest(length=len(raw)),self.assertRaises(f.Refused):m.verify_fixture_admission(self.root,raw,f.canonical(self.c),a,m.ACK)
    def test_wrong_ack_and_official_loader(self):
        for ack in (None,'EXECUTE','VALIDATE_EXCLUDED_SUBMISSION_BOUNDARY_ONLY'):
            with self.subTest(ack=ack),self.assertRaises(f.Refused):self.verify(ack)
        with self.assertRaises(f.Refused):m.load_official(self.verify(),ack='EXECUTE')


if __name__=='__main__':unittest.main(verbosity=2)
