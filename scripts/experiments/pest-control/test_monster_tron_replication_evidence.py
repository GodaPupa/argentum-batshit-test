"""Read-only verification of copied accepted evidence; no Git commits or producer replay."""
import json
import os
from pathlib import Path
import shutil
import unittest
from unittest.mock import patch

import monster_tron_replication_evidence as m
import monster_tron_replication_seedfree as f

PINS = ('c6dda4867f79043d070756ea2d2b32ee75cd02e5811c5ad38ab81e0c39f2faf0',
        '12c7a301c571eccff46e4064e40b0094db1e0535','161612340614ba03641694f328ac2b13c06dd8ae',
        '118d64c2f9a5a2dad800e051f217d5773ccf352a229d1de4542470ff51fb7a46',28470)


class EvidenceAuditTests(unittest.TestCase):
    def setUp(self):
        self.area=Path(os.environ['PEST_SEALED_TEST_OUTPUT']).absolute()/self._testMethodName
        self.area.mkdir(parents=True,exist_ok=False)
        self.root=self.area/'evidence'
        shutil.copytree(Path(os.environ['PEST_SEALED_SOURCE_EVIDENCE']),self.root,symlinks=True)
        self.entropy=patch.object(os,'urandom',side_effect=AssertionError('entropy forbidden'));self.entropy.start()
    def tearDown(self):self.entropy.stop()
    def verify(self,pins=PINS):return m.verify_fixture_evidence(self.root,*pins)
    def reseal(self):
        # Adversarial fixture edit only: checksums alone must not bless semantic corruption.
        rows=[]
        for p in sorted(self.root.iterdir()):
            if p.name!=m.MANIFEST:rows.append(f'{f.digest(p.read_bytes())}  {p.name}\n')
        (self.root/m.MANIFEST).write_text(''.join(rows))
    def edit(self,name,mutate,reseal=True):
        p=self.root/name;d=json.loads(p.read_text());mutate(d);p.write_bytes(f.canonical(d))
        if reseal:self.reseal()
    def snapshot(self):return {p.name:p.read_bytes() for p in self.root.iterdir()}

    def test_exact_accepted_evidence_and_no_writes(self):
        before=self.snapshot()
        with patch.object(m.s,'_evaluate',side_effect=AssertionError('producer forbidden')):
            result=self.verify()
        self.assertEqual(before,self.snapshot())
        self.assertEqual(12,result.slots);self.assertEqual(24,result.token_records)
        self.assertFalse(result.execution_authorized)
        self.assertEqual('82124243d93ea0427f0bd04cae2d318b6e569c6551ebd73af7bcef60c349c15c',result.manifest_sha256)
    def test_wrong_external_pins(self):
        for index in range(5):
            p=list(PINS);p[index]=1 if index==4 else '0'*len(p[index])
            with self.subTest(index=index),self.assertRaises(f.Refused):self.verify(tuple(p))
    def test_missing_member(self):
        (self.root/'00004-INTENT.json').unlink()
        with self.assertRaises(f.Refused):self.verify()
    def test_extra_member(self):
        (self.root/'extra').write_bytes(b'extra')
        with self.assertRaises(f.Refused):self.verify()
    def test_partial_prefix(self):
        (self.root/'fixture-summary.json').unlink()
        with self.assertRaises(f.Refused):self.verify()
    def test_stale_checksum(self):
        self.edit('fixture-raw-01-01.json',lambda d:d.update(payload_sha256='0'*64),False)
        with self.assertRaises(f.Refused):self.verify()
    def test_rehashed_wrong_raw_payload(self):
        self.edit('fixture-raw-01-01.json',lambda d:d.update(payload_sha256='0'*64))
        with self.assertRaises(f.Refused):self.verify()
    def test_rehashed_winner_in_raw(self):
        self.edit('fixture-raw-01-01.json',lambda d:d.update(outcome='PEST_WIN'))
        with self.assertRaises(f.Refused):self.verify()
    def test_rehashed_wrong_slot_order(self):
        self.edit('00004-INTENT.json',lambda d:d.update(slot=2))
        with self.assertRaises(f.Refused):self.verify()
    def test_rehashed_result_without_matching_intent(self):
        self.edit('00005-RESULT.json',lambda d:d['payload'].update(token='EXCLUDED_ACTION_00002'))
        with self.assertRaises(f.Refused):self.verify()
    def test_rehashed_wrong_event_index(self):
        self.edit('00004-INTENT.json',lambda d:d.update(index=5))
        with self.assertRaises(f.Refused):self.verify()
    def test_rehashed_event_rename(self):
        (self.root/'00004-INTENT.json').rename(self.root/'00004-RESULT.json');self.reseal()
        with self.assertRaises(f.Refused):self.verify()
    def test_rehashed_summary_authority(self):
        self.edit('fixture-summary.json',lambda d:d.update(execution_authorized=True))
        with self.assertRaises(f.Refused):self.verify()
    def test_rehashed_official_counter(self):
        self.edit('fixture-summary.json',lambda d:d.update(official_actions=1))
        with self.assertRaises(f.Refused):self.verify()
    def test_rehashed_receipt_claim(self):
        self.edit('fixture-receipt.json',lambda d:d.update(claim='f165a7d19447d68a7db5984c36f1654179120eff'))
        with self.assertRaises(f.Refused):self.verify()
    def test_rehashed_attempt_two(self):
        self.edit('fixture-receipt.json',lambda d:d.update(attempt=2))
        with self.assertRaises(f.Refused):self.verify()
    def test_rehashed_wrong_checkout(self):
        self.edit('fixture-checkout.json',lambda d:d.update(tree='0'*40))
        with self.assertRaises(f.Refused):self.verify()
    def test_bad_profile_component_even_with_new_profile_pin(self):
        self.edit('fixture-submission-profile.json',lambda d:d['components'].update({m.s.SELF:'0'*64}))
        pins=list(PINS);pins[0]=f.digest((self.root/'fixture-submission-profile.json').read_bytes())
        with self.assertRaises(f.Refused):self.verify(tuple(pins))
    def test_rehashed_vector_cell_even_with_new_digest_bindings(self):
        self.edit('fixture-input.json',lambda d:d['slots'][0].update(pest_seat=1))
        h=f.digest((self.root/'fixture-input.json').read_bytes())
        self.edit('fixture-receipt.json',lambda d:d.update(vector_digest=h))
        self.edit('fixture-submission-profile.json',lambda d:d.update(fixture_digest=h))
        pins=list(PINS);pins[0]=f.digest((self.root/'fixture-submission-profile.json').read_bytes())
        with self.assertRaises(f.Refused):self.verify(tuple(pins))
    def test_manifest_duplicate_line(self):
        p=self.root/m.MANIFEST;b=p.read_bytes();p.write_bytes(b+b.splitlines(keepends=True)[0])
        with self.assertRaises(f.Refused):self.verify()
    def test_manifest_reordered(self):
        p=self.root/m.MANIFEST;p.write_bytes(b''.join(reversed(p.read_bytes().splitlines(keepends=True))))
        with self.assertRaises(f.Refused):self.verify()
    def test_symlink_member_refused(self):
        p=self.root/'fixture-summary.json';outside=self.area/'outside';p.rename(outside);p.symlink_to(outside)
        with self.assertRaises(OSError):self.verify()
    def test_hardlink_member_refused(self):
        os.link(self.root/'fixture-summary.json',self.area/'hardlink')
        with self.assertRaises(f.Refused):self.verify()
    def test_fifo_member_refused_without_blocking(self):
        p=self.root/'fixture-summary.json';p.unlink();os.mkfifo(p)
        with self.assertRaises(f.Refused):self.verify()
    def test_oversize_member_refused(self):
        (self.root/'fixture-summary.json').write_bytes(b'x'*131073)
        with self.assertRaises(f.Refused):self.verify()
    def test_duplicate_json_keys_refused(self):
        p=self.root/'fixture-receipt.json';p.write_bytes(b'{"attempt":1,"attempt":1}\n');self.reseal()
        with self.assertRaises(f.Refused):self.verify()
    def test_official_loader_stays_closed(self):
        result=self.verify()
        with self.assertRaises(f.Refused):m.load_official(result,ack='EXECUTE')
        self.assertTrue(all(x is None for x in f.OFFICIAL_BINDINGS.values()))


if __name__=='__main__':unittest.main(verbosity=2)
