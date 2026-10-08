"""Excluded synthetic RECOVER publication fixtures; no worker or gameplay."""
import hashlib
import json
import os
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch
from r1_receiver_evidence import canonical, digest, Rejected
from r1_supervisor_channel import seal, authenticate
from r1_anchor_witness import AnchorWitness

from r1_checkpoint_holder import CheckpointHolder, verify_snapshot
from r1_checkpoint_recovery import RecoveryHolder
HK = b'h' * 32
WK = b'w' * 32
SK = b's' * 32

def h(s): return hashlib.sha256(s.encode()).hexdigest()


class RecoveryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / 'journal'
        self.members = {h('member'): {k:h(k) for k in ('definition','loader','module','origin')}}
        self.bindings = {k:h(k) for k in ('source','tree','runtime','process','channel','receiver')}
        self.bindings['policy'] = digest(self.members)
        self.context = h('context'); self.launch = h('launch')
        events = [('RESERVED', {'launch':self.launch,'challenge':h('challenge'),'bindings':self.bindings}),
                  ('BOUND',{}), ('ATTEMPT',dict(id=h('attempt'),member=h('member'),family='ordinary',**self.members[h('member')])),
                  ('RESULT',{'id':h('attempt'),'result':'DEFINED'}),('CUTOFF',{}),('CONSUMED_EVIDENCE',{})]
        rows=[]; previous='0'*64
        for i,(kind,data) in enumerate(events):
            raw=canonical({'seq':i,'previous':previous,'kind':kind,'data':data});rows.append(raw);previous=hashlib.sha256(raw).hexdigest()
        self.transcript=b'\n'.join(rows)+b'\n'
        self.anchor={'launch':self.launch,'challenge':h('challenge'),'rows':len(rows),'tail':previous,'sha256':hashlib.sha256(self.transcript).hexdigest()}
        self.signed=seal(SK,'receiver-anchor',self.context,self.anchor)
        self.anchor_hash=hashlib.sha256(self.signed).hexdigest()
        self.w=self.open(); self.cp=self.w.checkpoint()
        self.hp=Path(self.tmp.name)/'holder'
        self.holder=self.hopen(create=True)
        self.holder.enroll(h('witness'),h('instance'),self.cp)

    def open(self,cp=None,path=None):
        w=AnchorWitness(self.path if path is None else path,WK,SK,h('witness'),cp)
        self.addCleanup(w.close);return w

    def hopen(self,create=False,**kw):
        v=RecoveryHolder(kw.get('path',self.hp),kw.get('key',HK),h('holder'),
                           kw.get('registry',{h('witness'):WK}),create=create)
        self.addCleanup(v.close);return v

    def snapshot(self):
        raw=self.holder.snapshot(h('witness'),h('instance'),h('fresh-request'))
        return verify_snapshot(HK,h('holder'),h('witness'),h('instance'),h('fresh-request'),raw)

    def retain(self,revision=1,**kw):
        return self.holder.retain(h('witness'),kw.get('instance',h('instance')),revision,
                                 kw.get('witness',self.w),self.context,self.signed,
                                 kw.get('transcript',self.transcript),self.bindings,self.members)

    def recover(self,revision=1,**kw):
        result=self.holder.recover(kw.get('wid',h('witness')),kw.get('instance',h('instance')),
                revision,kw.get('path',str(self.path)),kw.get('key',SK))
        self.w=result['witness'];self.addCleanup(self.w.close)
        self.assertFalse(result['admission_authority'])
        return result

    def test_positive_genesis_recovery_then_retain(self):
        self.w.close();r=self.recover();self.assertEqual(r['holder_revision'],3)
        self.assertEqual(self.snapshot()['checkpoint'].encode(),self.w.checkpoint())
        self.assertEqual(self.retain(3)['holder_revision'],5)

    def test_retained_recovery_abandons(self):
        self.retain();self.w.close();self.recover(3)
        cp=self.w.checkpoint()
        with self.assertRaises(Rejected):self.w.consume(cp,self.launch,self.context,self.anchor_hash)
        self.assertEqual(self.snapshot()['revision'],5)

    def test_recovered_launch_cannot_be_reissued(self):
        self.retain();self.w.close();self.recover(3)
        with self.assertRaises(Rejected):self.retain(5)

    def test_repeated_clean_recovery(self):
        self.w.close();self.recover();self.w.close();self.recover(3)
        self.assertEqual(self.snapshot()['revision'],5)

    def test_holder_reopen_preserves_recovery(self):
        self.w.close();self.recover();before=self.snapshot()
        self.holder.close();self.holder=self.hopen();self.assertEqual(before,self.snapshot())
        self.retain(3)

    def test_predecessor_reader_fails_closed_on_new_grammar(self):
        self.w.close();self.recover();self.holder.close()
        with self.assertRaises(Rejected):CheckpointHolder(self.hp,HK,h('holder'),{h('witness'):WK})

    def test_reads_accepted_holder_journal(self):
        self.holder.close()
        old=CheckpointHolder(self.hp,HK,h('holder'),{h('witness'):WK});old.close()
        self.holder=self.hopen();self.w.close();self.recover()

    def test_pending_retain_cannot_recover(self):
        with self.assertRaises(ValueError):self.retain(transcript=b'bad')
        self.w.close()
        with self.assertRaises(Rejected):self.recover(2)
        self.assertNotIn('operation',self.snapshot()['pending'])

    def test_stale_revision(self):
        self.w.close();self.recover();self.w.close()
        with self.assertRaises(Rejected):self.recover(1)

    def test_bool_revision(self):
        with self.assertRaises(Rejected):self.recover(True)

    def test_wrong_instance(self):
        with self.assertRaises(Rejected):self.recover(instance=h('clone'))

    def test_unknown_witness(self):
        with self.assertRaises(Rejected):self.recover(wid=h('other'))

    def test_already_open_witness_fences_intent(self):
        with self.assertRaises(BlockingIOError):self.recover()
        self.assertEqual(self.snapshot()['pending']['operation'],'RECOVER')
        self.w.close()
        with self.assertRaises(Rejected):self.recover(2)

    def test_independent_reopen_not_imported(self):
        self.w.close();self.w=self.open(self.cp);self.w.close()
        with self.assertRaises(Rejected):self.recover()
        self.assertEqual(self.snapshot()['revision'],2)

    def test_wrong_supervisor_key_fences_retained_state(self):
        self.retain();self.w.close()
        with self.assertRaises(Rejected):self.recover(3,key=b'x'*32)
        self.assertEqual(self.snapshot()['pending']['operation'],'RECOVER')

    def test_shared_key_rejected_before_intent(self):
        with self.assertRaises(Rejected):self.recover(key=HK)
        self.assertIsNone(self.snapshot()['pending'])

    def test_path_alias_rejected_before_intent(self):
        with self.assertRaises(Rejected):self.recover(path=str(self.path)+'/../journal')
        self.assertIsNone(self.snapshot()['pending'])

    def test_symlink_rejected_with_durable_fence(self):
        self.w.close();p=Path(self.tmp.name)/'link';p.symlink_to(self.path)
        with self.assertRaises(OSError):self.recover(path=str(p))
        self.assertEqual(self.snapshot()['revision'],2)

    def test_missing_journal_fences(self):
        self.w.close();self.path.unlink()
        with self.assertRaises(FileNotFoundError):self.recover()
        self.assertIsNotNone(self.snapshot()['pending'])

    def test_intent_fsync_failure_never_opens_witness(self):
        self.w.close();before=self.path.read_bytes()
        with patch('os.fsync',side_effect=OSError('intent uncertain')):
            with self.assertRaises(OSError):self.recover()
        self.assertEqual(self.path.read_bytes(),before)
        self.holder.close();self.holder=self.hopen()
        with self.assertRaises(Rejected):self.recover(2)

    def test_lost_intent_bytes_precedes_no_witness_mutation(self):
        self.w.close();before=self.hp.read_bytes();wb=self.path.read_bytes()
        with patch('os.fsync',side_effect=OSError('intent uncertain')):
            with self.assertRaises(OSError):self.recover()
        self.assertEqual(self.path.read_bytes(),wb)
        self.holder.close();self.hp.write_bytes(before);self.holder=self.hopen();self.recover()

    def test_lost_recover_bytes_remain_fenced(self):
        self.retain();self.w.close();before=self.path.read_bytes();original=os.fsync
        def sync(fd):
            if fd!=self.holder._fd:raise OSError('witness RECOVER uncertainty')
            return original(fd)
        with patch('os.fsync',side_effect=sync):
            with self.assertRaises(OSError):self.recover(3)
        self.path.write_bytes(before)
        self.holder.close();self.holder=self.hopen()
        with self.assertRaises(Rejected):self.recover(4)

    def test_publication_failure_closes_witness_and_fences(self):
        self.w.close();original=self.holder._append
        def append(kind,data):
            if kind=='RECOVERY_PUBLISH':raise OSError('publication unavailable')
            original(kind,data)
        with patch.object(self.holder,'_append',side_effect=append):
            with self.assertRaises(OSError):self.recover()
        self.assertEqual(self.snapshot()['revision'],2)
        self.holder.close();self.holder=self.hopen()
        with self.assertRaises(Rejected):self.recover(2)
        # OS lock released even though no live witness handle was returned.
        fd=os.open(self.path,os.O_RDWR)
        try:
            import fcntl
            fcntl.flock(fd,fcntl.LOCK_EX|fcntl.LOCK_NB)
        finally:os.close(fd)

    def test_publication_ack_loss_durable_state(self):
        self.w.close();original=self.holder._append
        def append(kind,data):
            original(kind,data)
            if kind=='RECOVERY_PUBLISH':raise OSError('ack lost')
        with patch.object(self.holder,'_append',side_effect=append):
            with self.assertRaises(OSError):self.recover()
        self.holder.close();self.holder=self.hopen()
        self.assertEqual(self.snapshot()['revision'],3)
        with self.assertRaises(Rejected):self.recover(1)
        self.recover(3) # distinct exact-CAS recovery, not replay of old mutation

    def test_concurrent_recovery_single_winner(self):
        self.w.close();results=[]
        def run():
            try:results.append(self.holder.recover(h('witness'),h('instance'),1,str(self.path),SK))
            except Rejected:results.append(None)
        ts=[threading.Thread(target=run) for _ in range(8)]
        for t in ts:t.start()
        for t in ts:t.join()
        winners=[r for r in results if r is not None]
        self.assertEqual(len(winners),1);self.addCleanup(winners[0]['witness'].close)
        self.assertEqual(self.snapshot()['revision'],3)

    def recovery_record(self):
        before=self.w.checkpoint();self.w.close();self.recover()
        raw=self.path.read_bytes().splitlines()[-1]
        return before,self.w.checkpoint(),raw

    def test_forged_recovery_tail(self):
        before,after,raw=self.recovery_record();v=authenticate(WK,after,'witness-checkpoint',h('witness'));v['tail']=h('wrong')
        with self.assertRaises(Rejected):self.holder._verify_recovery(h('witness'),before,seal(WK,'witness-checkpoint',h('witness'),v),raw)

    def test_wrong_recovery_kind(self):
        before,after,raw=self.recovery_record();v=json.loads(raw);v['kind']='RETAIN'
        with self.assertRaises(Rejected):self.holder._verify_recovery(h('witness'),before,after,canonical(v))

    def test_wrong_recovery_previous(self):
        before,after,raw=self.recovery_record();v=json.loads(raw);v['previous']=h('wrong')
        with self.assertRaises(Rejected):self.holder._verify_recovery(h('witness'),before,after,canonical(v))

    def test_bool_record_sequence(self):
        before,after,raw=self.recovery_record();v=json.loads(raw);v['seq']=True
        with self.assertRaises(Rejected):self.holder._verify_recovery(h('witness'),before,after,canonical(v))

    def test_duplicate_abandonment(self):
        before,after,raw=self.recovery_record();v=json.loads(raw);v['data']['abandoned']=[h('launch'),h('launch')]
        with self.assertRaises(Rejected):self.holder._verify_recovery(h('witness'),before,after,canonical(v))

    def test_retain_publish_cannot_clear_recovery(self):
        with self.assertRaises(BlockingIOError):self.recover()
        with self.assertRaises(Rejected):self.holder._apply('PUBLISH',{'witness':h('witness'),'checkpoint':self.cp.decode()})
        self.assertIsNotNone(self.snapshot()['pending'])

    def test_recovery_publish_cannot_clear_retain(self):
        with self.assertRaises(ValueError):self.retain(transcript=b'bad')
        with self.assertRaises(Rejected):self.holder._apply('RECOVERY_PUBLISH',{'witness':h('witness'),'checkpoint':self.cp.decode(),'record':'{}'})

    def test_forked_recovery_refused(self):
        with patch('os.getpid',return_value=os.getpid()+1):
            with self.assertRaises(Rejected):self.recover()

    def test_short_writes(self):
        self.w.close();original=os.write
        with patch('os.write',side_effect=lambda fd,b:original(fd,b[:7])):self.recover()
        self.assertEqual(self.snapshot()['revision'],3)

    def test_zero_write_no_witness_mutation(self):
        self.w.close();before=self.path.read_bytes()
        with patch('os.write',return_value=0):
            with self.assertRaises(OSError):self.recover()
        self.assertEqual(self.path.read_bytes(),before)

if __name__=='__main__':unittest.main(verbosity=2)
