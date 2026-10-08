"""Excluded deterministic token transformations; no MTG engine or official seeds."""
import json
from pathlib import Path
import unittest
from unittest.mock import patch

import monster_tron_replication_submission as m
import monster_tron_replication_seedfree as f
import test_monster_tron_replication_checkout as support
from test_monster_tron_replication_seedfree import fixture, receipt


class SubmissionTests(unittest.TestCase):
    git = support.CheckoutTests.git
    tearDown = support.CheckoutTests.tearDown

    def setUp(self):
        support.CheckoutTests.setUp(self)
        directory = self.repo / m.DIRECTORY
        directory.mkdir(parents=True)
        for name in [*m.COMPONENTS, m.SELF]:
            (directory / name).write_bytes((Path(m.__file__).parent / name).read_bytes())
        self.git('add', '.'); self.git('-c', 'commit.gpgsign=false', 'commit', '--quiet', '-m', 'Excluded components')
        self.head = self.git('rev-parse', 'HEAD').strip().decode()
        self.tree = self.git('rev-parse', 'HEAD^{tree}').strip().decode()
        self.profile = dict(realm=f.REALM, head=self.head, tree=self.tree,
            components={name:f.digest((directory/name).read_bytes()) for name in [*m.COMPONENTS,m.SELF]},
            fixture_digest=f.digest(self.raw), attempt=1, slots=12, tokens_per_slot=2,
            claim='EXCLUDED_LOCAL_FIXTURE_NOT_A_CLAIM', official_bindings=dict(f.OFFICIAL_BINDINGS))

    def boundary(self, profile=None, **overrides):
        raw = f.canonical(self.profile if profile is None else profile)
        args = dict(checkout=self.repo, evidence_root=self.area/'evidence', profile_raw=raw,
                    profile_digest=f.digest(raw), vector_raw=self.raw, receipt=receipt(self.raw), ack=m.ACK)
        args.update(overrides)
        b=m.FixtureSubmissionBoundary(**args); self.boundaries.append(b); return b

    def complete(self, b):
        for n in range(1,13):
            b.begin_slot(n)
            for token in ('EXCLUDED_ACTION_00001','EXCLUDED_ACTION_00002'): b.submit_token(token)
            b.end_slot()

    def test_complete_bound_profile_raw_results_and_inventory(self):
        b=self.boundary(); self.complete(b); b.finish(); root=self.area/'evidence'
        raws=sorted(root.glob('fixture-raw-*.json')); self.assertEqual(24,len(raws))
        for p in raws:
            d=json.loads(p.read_text()); self.assertIsNone(d['outcome']); self.assertTrue(d['no_game_played'])
        covered={}
        for line in (root/'fixture-artifacts.sha256').read_text().splitlines():
            h,name=line.split('  ',1); covered[name]=h; self.assertEqual(h,f.digest((root/name).read_bytes()))
        self.assertIn('fixture-submission-profile.json',covered)
        self.assertEqual(set(covered),{p.name for p in root.iterdir()}-{'fixture-artifacts.sha256'})

    def test_intent_forced_before_transformation_and_raw_before_result(self):
        b=self.boundary(); b.begin_slot(1); original=m._evaluate; writer=b._coordinator._write; seen=[]
        def evaluate(*args):
            self.assertIn('00004-INTENT.json',b._coordinator.hashes)
            self.assertNotIn('fixture-raw-01-01.json',b._coordinator.hashes)
            seen.append('evaluate'); return original(*args)
        def write(name,raw):
            if name=='00005-RESULT.json': self.assertIn('fixture-raw-01-01.json',b._coordinator.hashes)
            writer(name,raw)
        with patch.object(m,'_evaluate',side_effect=evaluate),patch.object(b._coordinator,'_write',side_effect=write):
            b.submit_token('EXCLUDED_ACTION_00001')
        self.assertEqual(['evaluate'],seen)

    def test_intent_write_failure_never_transforms(self):
        b=self.boundary(); b.begin_slot(1)
        with patch.object(b._coordinator,'_write',side_effect=OSError('intent write loss')),patch.object(m,'_evaluate') as evaluate:
            with self.assertRaises(OSError):b.submit_token('EXCLUDED_ACTION_00001')
            evaluate.assert_not_called()
        with self.assertRaises(f.Refused):b.submit_token('EXCLUDED_ACTION_00001')

    def test_transform_exception_keeps_unresolved_intent(self):
        b=self.boundary(); b.begin_slot(1)
        with patch.object(m,'_evaluate',side_effect=RuntimeError('excluded transform uncertainty')) as evaluate:
            with self.assertRaises(RuntimeError):b.submit_token('EXCLUDED_ACTION_00001')
            with self.assertRaises(f.Refused):b.submit_token('EXCLUDED_ACTION_00001')
            self.assertEqual(1,evaluate.call_count)
        self.assertEqual('EXCLUDED_ACTION_00001',b._coordinator.pending)
        self.assertTrue((self.area/'evidence/00004-INTENT.json').exists())
        self.assertFalse((self.area/'evidence/00005-RESULT.json').exists())

    def test_raw_write_failure_keeps_intent_without_result(self):
        b=self.boundary(); b.begin_slot(1); writer=b._coordinator._write
        def write(name,raw):
            if name.startswith('fixture-raw-'):raise OSError('raw loss')
            writer(name,raw)
        with patch.object(b._coordinator,'_write',side_effect=write):
            with self.assertRaises(OSError):b.submit_token('EXCLUDED_ACTION_00001')
        self.assertEqual('EXCLUDED_ACTION_00001',b._coordinator.pending)
        self.assertFalse((self.area/'evidence/00005-RESULT.json').exists())

    def test_result_write_failure_preserves_raw_and_no_retry(self):
        b=self.boundary(); b.begin_slot(1); writer=b._coordinator._write
        def write(name,raw):
            if name.endswith('-RESULT.json'):raise OSError('result loss')
            writer(name,raw)
        with patch.object(b._coordinator,'_write',side_effect=write):
            with self.assertRaises(OSError):b.submit_token('EXCLUDED_ACTION_00001')
        self.assertTrue((self.area/'evidence/fixture-raw-01-01.json').exists())
        self.assertEqual('EXCLUDED_ACTION_00001',b._coordinator.pending)
        with self.assertRaises(f.Refused):b.end_slot()
        with self.assertRaises(FileExistsError):self.boundary()

    def test_profile_write_failure_retains_prefix(self):
        writer=f.FixtureEvidenceCoordinator._write
        def write(c,name,raw):
            if name=='fixture-submission-profile.json':raise OSError('profile loss')
            writer(c,name,raw)
        with patch.object(f.FixtureEvidenceCoordinator,'_write',write):
            with self.assertRaises(OSError):self.boundary()
        self.assertTrue((self.area/'evidence/fixture-checkout.json').exists())
        with self.assertRaises(FileExistsError):self.boundary()

    def test_wrong_source_component_and_profile_digest(self):
        for field in ('head','tree'):
            p=dict(self.profile);p[field]='0'*40
            with self.subTest(field=field),self.assertRaises(f.Refused):self.boundary(profile=p)
        p=dict(self.profile);p['components']=dict(p['components']);p['components'][m.SELF]='0'*64
        with self.assertRaises(f.Refused):self.boundary(profile=p)
        with self.assertRaises(f.Refused):self.boundary(profile_digest='0'*64)
        self.assertFalse((self.area/'evidence').exists())

    def test_profile_attempt_claim_count_and_official_bindings(self):
        for key,value in [('attempt',2),('attempt',True),('slots',4),('tokens_per_slot',3),
                          ('claim','f165a7d19447d68a7db5984c36f1654179120eff'),('official_bindings',{'source':'invented'})]:
            p=dict(self.profile);p[key]=value
            with self.subTest(key=key),self.assertRaises(f.Refused):self.boundary(profile=p)
        self.assertFalse((self.area/'evidence').exists())

    def test_wrong_ack_and_official_loader(self):
        for ack in ('EXECUTE',m.c.FIXTURE_ACK,None):
            with self.subTest(ack=ack),self.assertRaises(f.Refused):self.boundary(ack=ack)
        with self.assertRaises(f.Refused):m.load_official(self.profile,ack=m.ACK)
        self.assertTrue(all(v is None for v in f.OFFICIAL_BINDINGS.values()))

    def test_profile_malformed_duplicate_extra_and_missing(self):
        for raw in (None,b'',b'{"realm":0,"realm":1}\n',b'\xff',b'x'*8193):
            with self.subTest(raw_type=type(raw)),self.assertRaises((f.Refused,)):
                self.boundary(profile_raw=raw,profile_digest=f.digest(raw) if type(raw) is bytes else '0'*64)
        p=dict(self.profile);p['execute']=True
        with self.assertRaises(f.Refused):self.boundary(profile=p)

    def test_vector_and_receipt_mismatch(self):
        with self.assertRaises(f.Refused):self.boundary(vector_raw=None)
        with self.assertRaises(f.Refused):self.boundary(vector_raw=b'{}')
        r=receipt(self.raw);r['attempt']=2
        with self.assertRaises(f.Refused):self.boundary(receipt=r)
        for mutate in (lambda d:d['slots'].pop(),lambda d:d['slots'].reverse(),lambda d:d['slots'][0].update(pest_seat=1)):
            d=fixture();mutate(d);raw=f.canonical(d);p=dict(self.profile);p['fixture_digest']=f.digest(raw)
            with self.assertRaises(f.Refused):self.boundary(profile=p,vector_raw=raw,receipt=receipt(raw))
        self.assertFalse((self.area/'evidence').exists())

    def test_out_of_order_slot_poisoned(self):
        b=self.boundary()
        with self.assertRaises(f.Refused):b.begin_slot(2)
        with self.assertRaises(f.Refused):b.begin_slot(1)
    def test_submit_before_slot(self):
        b=self.boundary()
        with self.assertRaises(f.Refused):b.submit_token('EXCLUDED_ACTION_00001')
    def test_wrong_token(self):
        b=self.boundary();b.begin_slot(1)
        with self.assertRaises(f.Refused):b.submit_token('EXCLUDED_ACTION_00002')
    def test_duplicate_token(self):
        b=self.boundary();b.begin_slot(1);b.submit_token('EXCLUDED_ACTION_00001')
        with self.assertRaises(f.Refused):b.submit_token('EXCLUDED_ACTION_00001')
    def test_third_token_budget(self):
        b=self.boundary();b.begin_slot(1)
        for t in ('EXCLUDED_ACTION_00001','EXCLUDED_ACTION_00002'):b.submit_token(t)
        with self.assertRaises(f.Refused):b.submit_token('EXCLUDED_ACTION_00003')
    def test_early_slot_end(self):
        b=self.boundary();b.begin_slot(1);b.submit_token('EXCLUDED_ACTION_00001')
        with self.assertRaises(f.Refused):b.end_slot()
    def test_incomplete_block(self):
        b=self.boundary()
        with self.assertRaises(f.Refused):b.finish()
    def test_raw_corruption_blocks_completion(self):
        b=self.boundary();self.complete(b);(self.area/'evidence/fixture-raw-01-01.json').write_bytes(b'changed')
        with self.assertRaises(f.Refused):b.finish()
    def test_profile_corruption_blocks_completion(self):
        b=self.boundary();self.complete(b);(self.area/'evidence/fixture-submission-profile.json').write_bytes(b'changed')
        with self.assertRaises(f.Refused):b.finish()
    def test_source_drift_blocks_completion(self):
        b=self.boundary();self.complete(b);(self.repo/'source.txt').write_bytes(b'changed')
        with self.assertRaises(f.Refused):b.finish()
    def test_second_finish_and_thirteenth_slot(self):
        b=self.boundary();self.complete(b);b.finish()
        with self.assertRaises(f.Refused):b.finish()
        with self.assertRaises(f.Refused):b.begin_slot(13)


if __name__=='__main__':unittest.main(verbosity=2)
