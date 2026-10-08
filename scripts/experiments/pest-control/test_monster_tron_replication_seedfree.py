"""Excluded label fixtures only. No random values, seed allocation, game or network."""
import json
import os
from pathlib import Path
import unittest
from unittest.mock import patch
import monster_tron_replication_seedfree as m

def fixture():
    return dict(realm=m.REALM,baseline=m.BASELINE,design=m.DESIGN,
        contract_sha256=m.CONTRACT_SHA256,official_seeds=None,
        slots=[dict(number=n,label=f'EXCLUDED_FIXTURE_SLOT_{n:02d}',pest_seat=((n-1)%4)//2,
            monster_seat=1-((n-1)%4)//2,starting_deck='PEST_CONTROL' if n%2 else 'MONSTER_TRON') for n in range(1,13)])

def receipt(raw):
    return dict(realm=m.REALM,claim='EXCLUDED_LOCAL_FIXTURE_NOT_A_CLAIM',run='EXCLUDED_FIXTURE_RUN',
        attempt=1,baseline=m.BASELINE,vector_digest=m.digest(raw),reserved_slots=12,execution_allowed=False)

class InputTests(unittest.TestCase):
    def test_exact_excluded_cells_and_immutable_loaded_slots(self):
        d=fixture();raw=m.canonical(d);slots=m.load_fixture(raw,m.digest(raw),m.BASELINE)
        d['slots'].clear()
        self.assertEqual(12,len(slots))
        self.assertEqual(6,sum(s.pest_seat==0 for s in slots))
        with self.assertRaises(AttributeError): slots[0].number=2

    def reject(self,mutate):
        d=fixture();mutate(d);raw=m.canonical(d)
        with self.assertRaises(m.Refused):m.load_fixture(raw,m.digest(raw),m.BASELINE)

    def test_wrong_count(self):self.reject(lambda d:d['slots'].pop())
    def test_wrong_order(self):self.reject(lambda d:d['slots'].reverse())
    def test_wrong_cell(self):self.reject(lambda d:d['slots'][0].update(pest_seat=1))
    def test_wrong_starter(self):self.reject(lambda d:d['slots'][0].update(starting_deck='MONSTER_TRON'))
    def test_duplicate_label(self):self.reject(lambda d:d['slots'][1].update(label=d['slots'][0]['label']))
    def test_bool_is_not_slot(self):self.reject(lambda d:d['slots'][0].update(number=True))
    def test_wrong_source(self):self.reject(lambda d:d.update(baseline='0'*40))
    def test_wrong_design(self):self.reject(lambda d:d.update(design='0'*40))
    def test_wrong_contract(self):self.reject(lambda d:d.update(contract_sha256='0'*64))
    def test_official_labels_rejected(self):self.reject(lambda d:d.update(realm='OFFICIAL'))
    def test_official_seed_field_rejected(self):self.reject(lambda d:d.update(official_seeds=[]))
    def test_extra_fields_rejected(self):self.reject(lambda d:d.update(execution_allowed=True))
    def test_observed_source_mismatch(self):
        raw=m.canonical(fixture())
        with self.assertRaises(m.Refused):m.load_fixture(raw,m.digest(raw),'0'*40)
    def test_digest_mismatch(self):
        with self.assertRaises(m.Refused):m.load_fixture(m.canonical(fixture()),'0'*64,m.BASELINE)
    def test_missing_vector(self):
        for raw in (None,b''):
            with self.subTest(raw=raw),self.assertRaises(m.Refused):m.load_fixture(raw,'0'*64,m.BASELINE)
    def test_duplicate_keys(self):
        raw=b'{"realm":0,"realm":1}\n'
        with self.assertRaises(m.Refused):m.load_fixture(raw,m.digest(raw),m.BASELINE)
    def test_noncanonical_and_invalid_utf8(self):
        for raw in (json.dumps(fixture()).encode(),b'\xff',b'x'*32769):
            with self.subTest(size=len(raw)),self.assertRaises(m.Refused):m.load_fixture(raw,m.digest(raw),m.BASELINE)
    def test_consumed_smoke_claims_and_attempt_two(self):
        raw=m.canonical(fixture())
        for key,value in [('claim','f165a7d19447d68a7db5984c36f1654179120eff'),('claim','1c2e253ad7f5a7652304f7c9aaafc30e547a481e'),('attempt',2),('attempt',True),('reserved_slots',4),('execution_allowed',True),('vector_digest','0'*64),('baseline','0'*40)]:
            r=receipt(raw);r[key]=value
            with self.subTest(key=key,value=value),self.assertRaises(m.Refused):m.validate_fixture_receipt(r,m.digest(raw),m.BASELINE)
    def test_official_boundary_unconditionally_closed(self):
        self.assertTrue(all(v is None for v in m.OFFICIAL_BINDINGS.values()))
        with self.assertRaises(TypeError):m.OFFICIAL_BINDINGS['vector']='invented'
        for ack in ('LOAD_FROZEN_MONSTER_TRON_R1_FOR_VALIDATION_ONLY','AUTOMATIC_ONE_SHOT_MONSTER_TRON_R1_EXECUTION','EXPLICIT_EXECUTION'):
            with self.subTest(ack=ack),self.assertRaises(m.Refused):m.load_official(fixture(),ack=ack,receipt=receipt(m.canonical(fixture())))

class EvidenceTests(unittest.TestCase):
    def setUp(self):
        self.parent=Path(os.environ['PEST_SEEDFREE_TEST_OUTPUT']).absolute()/self._testMethodName
        self.parent.mkdir(parents=True,exist_ok=False)
        self.raw=m.canonical(fixture());self.objects=[]
        self.entropy=patch.object(os,'urandom',side_effect=AssertionError('entropy forbidden'));self.entropy.start()
    def tearDown(self):
        for c in self.objects:c.close()
        self.entropy.stop()
    def coordinator(self,name='evidence'):
        c=m.FixtureEvidenceCoordinator(self.parent/name,self.raw,m.digest(self.raw),m.BASELINE,receipt(self.raw))
        self.objects.append(c);return c
    def start(self,c,n=1):
        for kind in ('ATTEMPT','INITIALIZATION_ENTRY','FIXTURE_INITIALIZED'):c.transition(kind,n)
    def pair(self,c,n=1):
        token=f'EXCLUDED_ACTION_{c.sequence+1:05d}'
        c.transition('INTENT',n,token);c.transition('RESULT',n,token)
    def complete(self,c):
        for n in range(1,13):
            self.start(c,n);self.pair(c,n);self.pair(c,n);c.transition('FIXTURE_RECORD',n)
    def test_complete_twelve_fixture_slots_and_inventory(self):
        c=self.coordinator();self.complete(c);h=c.finish();root=self.parent/'evidence'
        inventory=(root/'fixture-artifacts.sha256').read_bytes();self.assertEqual(h,m.digest(inventory))
        names=[]
        for line in inventory.decode().splitlines():
            digest,name=line.split('  ',1);names.append(name);self.assertEqual(digest,m.digest((root/name).read_bytes()))
        self.assertEqual(set(names),{p.name for p in root.iterdir()}-{'fixture-artifacts.sha256'})
        events=[json.loads(p.read_text()) for p in sorted(root.glob('[0-9]*.json'))]
        self.assertEqual(list(range(1,97)),[e['index'] for e in events])
        self.assertEqual([n for n in range(1,13) for _ in range(8)],[e['slot'] for e in events])
        summary=json.loads((root/'fixture-summary.json').read_text());self.assertFalse(summary['execution_authorized'])
        self.assertEqual(0,summary['official_initializations'])
    def test_out_of_order_poisoned_permanently(self):
        c=self.coordinator()
        with self.assertRaises(m.Refused):c.transition('ATTEMPT',2)
        with self.assertRaises(m.Refused):c.transition('ATTEMPT',1)
    def test_initialization_before_attempt_rejected(self):
        c=self.coordinator()
        with self.assertRaises(m.Refused):c.transition('FIXTURE_INITIALIZED',1)
    def test_result_without_intent(self):
        c=self.coordinator();self.start(c)
        with self.assertRaises(m.Refused):c.transition('RESULT',1,'EXCLUDED_ACTION_00001')
    def test_second_intent_without_result(self):
        c=self.coordinator();self.start(c);c.transition('INTENT',1,'EXCLUDED_ACTION_00001')
        with self.assertRaises(m.Refused):c.transition('INTENT',1,'EXCLUDED_ACTION_00002')
    def test_wrong_result_token(self):
        c=self.coordinator();self.start(c);c.transition('INTENT',1,'EXCLUDED_ACTION_00001')
        with self.assertRaises(m.Refused):c.transition('RESULT',1,'EXCLUDED_ACTION_00002')
    def test_record_with_unresolved_intent(self):
        c=self.coordinator();self.start(c);c.transition('INTENT',1,'EXCLUDED_ACTION_00001')
        with self.assertRaises(m.Refused):c.transition('FIXTURE_RECORD',1)
    def test_record_without_action_evidence(self):
        c=self.coordinator();self.start(c)
        with self.assertRaises(m.Refused):c.transition('FIXTURE_RECORD',1)
    def test_incomplete_twelve(self):
        c=self.coordinator();self.start(c);self.pair(c);c.transition('FIXTURE_RECORD',1)
        with self.assertRaises(m.Refused):c.finish()
    def test_existing_root_refuses_restart(self):
        c=self.coordinator();self.start(c);c.close()
        with self.assertRaises(FileExistsError):self.coordinator()
    def test_no_clobber_event_collision(self):
        c=self.coordinator();p=self.parent/'evidence/00001-ATTEMPT.json';p.write_bytes(b'preserve')
        with self.assertRaises(FileExistsError):c.transition('ATTEMPT',1)
        self.assertEqual(b'preserve',p.read_bytes());self.assertTrue(c.poisoned)
    def test_symlink_event_refused(self):
        c=self.coordinator();outside=self.parent/'outside';outside.write_bytes(b'preserve')
        (self.parent/'evidence/00001-ATTEMPT.json').symlink_to(outside)
        with self.assertRaises(FileExistsError):c.transition('ATTEMPT',1)
        self.assertEqual(b'preserve',outside.read_bytes())
    def test_file_fsync_failure_retains_prefix_and_poison(self):
        c=self.coordinator()
        with patch.object(os,'fsync',side_effect=OSError('injected file force uncertainty')):
            with self.assertRaises(OSError):c.transition('ATTEMPT',1)
        self.assertTrue((self.parent/'evidence/00001-ATTEMPT.json').exists())
        with self.assertRaises(m.Refused):c.transition('ATTEMPT',1)
    def test_directory_force_failure_retains_prefix_and_poison(self):
        c=self.coordinator()
        with patch.object(m,'_force_directory',side_effect=OSError('injected directory force uncertainty')):
            with self.assertRaises(OSError):c.transition('ATTEMPT',1)
        self.assertEqual('READY',c.phase);self.assertTrue(c.poisoned)
    def test_intent_failure_prevents_result(self):
        c=self.coordinator();self.start(c)
        with patch.object(c,'_write',side_effect=OSError('injected write failure')):
            with self.assertRaises(OSError):c.transition('INTENT',1,'EXCLUDED_ACTION_00001')
        with self.assertRaises(m.Refused):c.transition('RESULT',1,'EXCLUDED_ACTION_00001')
        self.assertIsNone(c.pending)
    def test_result_failure_leaves_unresolved_durable_intent(self):
        c=self.coordinator();self.start(c);c.transition('INTENT',1,'EXCLUDED_ACTION_00001')
        with patch.object(c,'_write',side_effect=OSError('injected result loss')):
            with self.assertRaises(OSError):c.transition('RESULT',1,'EXCLUDED_ACTION_00001')
        self.assertEqual('EXCLUDED_ACTION_00001',c.pending)
        with self.assertRaises(m.Refused):c.transition('FIXTURE_RECORD',1)
    def test_corrupted_evidence_prevents_completion(self):
        c=self.coordinator();self.complete(c)
        (self.parent/'evidence/00001-ATTEMPT.json').write_bytes(b'corrupt')
        with self.assertRaises(m.Refused):c.finish()
    def test_missing_evidence_prevents_completion(self):
        c=self.coordinator();self.complete(c)
        (self.parent/'evidence/00001-ATTEMPT.json').unlink()
        with self.assertRaises(m.Refused):c.finish()
    def test_unknown_evidence_prevents_completion(self):
        c=self.coordinator();self.complete(c);(self.parent/'evidence/extra').write_bytes(b'extra')
        with self.assertRaises(m.Refused):c.finish()
    def test_second_finish_and_thirteenth_slot_refused(self):
        c=self.coordinator();self.complete(c);c.finish()
        with self.assertRaises(m.Refused):c.finish()
        with self.assertRaises(m.Refused):c.transition('ATTEMPT',13)
    def test_official_labels_fail_before_files_created(self):
        bad=receipt(self.raw);bad['execution_allowed']=True
        with self.assertRaises(m.Refused):m.FixtureEvidenceCoordinator(self.parent/'never-created',self.raw,m.digest(self.raw),m.BASELINE,bad)
        self.assertFalse((self.parent/'never-created').exists())

if __name__=='__main__':unittest.main(verbosity=2)
