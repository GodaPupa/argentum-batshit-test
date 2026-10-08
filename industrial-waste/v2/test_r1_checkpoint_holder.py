"""Excluded deterministic holder fixtures; no network, VM or experimental worker."""
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
HK = b'h' * 32
WK = b'w' * 32
SK = b's' * 32

def h(s): return hashlib.sha256(s.encode()).hexdigest()


class HolderTests(unittest.TestCase):
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
        v=CheckpointHolder(kw.get('path',self.hp),kw.get('key',HK),h('holder'),
                           kw.get('registry',{h('witness'):WK}),create=create)
        self.addCleanup(v.close);return v

    def snapshot(self):
        raw=self.holder.snapshot(h('witness'),h('instance'),h('fresh-request'))
        return verify_snapshot(HK,h('holder'),h('witness'),h('instance'),h('fresh-request'),raw)

    def retain(self,revision=1,**kw):
        return self.holder.retain(h('witness'),kw.get('instance',h('instance')),revision,
                                 kw.get('witness',self.w),self.context,self.signed,
                                 kw.get('transcript',self.transcript),self.bindings,self.members)

    def test_positive_publication_and_reopen(self):
        r=self.retain();self.assertFalse(r['admission_authority']);self.assertEqual(r['holder_revision'],3)
        state=self.snapshot();self.assertIsNone(state['pending'])
        self.assertEqual(state['checkpoint'].encode(),self.w.checkpoint())
        self.holder.close();self.holder=self.hopen();self.assertEqual(self.snapshot(),state)

    def test_duplicate_enrollment(self):
        with self.assertRaises(Rejected):self.holder.enroll(h('witness'),h('other'),self.cp)

    def test_unknown_enrollment(self):
        with self.assertRaises(Rejected):self.holder.enroll(h('other'),h('instance2'),self.cp)

    def test_duplicate_instance(self):
        self.holder.close();self.hp=Path(self.tmp.name)/'two-holder'
        self.holder=self.hopen(create=True,registry={h('witness'):WK,h('second'):b'2'*32})
        self.holder.enroll(h('witness'),h('instance'),self.cp)
        cp=seal(b'2'*32,'witness-checkpoint',h('second'),{'witness':h('second'),'rows':1,'tail':h('tail')})
        with self.assertRaises(Rejected):self.holder.enroll(h('second'),h('instance'),cp)

    def test_stale_cas(self):
        self.retain()
        with self.assertRaises(Rejected):self.retain()
        self.assertEqual(self.snapshot()['revision'],3)

    def test_bool_revision(self):
        with self.assertRaises(Rejected):self.retain(True)

    def test_wrong_instance(self):
        with self.assertRaises(Rejected):self.retain(instance=h('clone'))

    def test_clone_race_one_intent(self):
        results=[]
        def run():
            try:self.retain();results.append('ok')
            except Rejected:results.append('rejected')
        ts=[threading.Thread(target=run) for _ in range(8)]
        for t in ts:t.start()
        for t in ts:t.join()
        self.assertEqual(sorted(results),['ok']+['rejected']*7)
        self.assertEqual(self.snapshot()['revision'],3)

    def test_replay_after_latest_snapshot(self):
        self.retain()
        with self.assertRaises(Rejected):self.retain(3)

    def test_snapshot_nonce_substitution(self):
        raw=self.holder.snapshot(h('witness'),h('instance'),h('old'))
        with self.assertRaises(Rejected):verify_snapshot(HK,h('holder'),h('witness'),h('instance'),h('new'),raw)

    def test_snapshot_wrong_key(self):
        raw=self.holder.snapshot(h('witness'),h('instance'),h('fresh-request'))
        with self.assertRaises(Rejected):verify_snapshot(b'x'*32,h('holder'),h('witness'),h('instance'),h('fresh-request'),raw)

    def test_snapshot_wrong_holder(self):
        raw=self.holder.snapshot(h('witness'),h('instance'),h('fresh-request'))
        with self.assertRaises(Rejected):verify_snapshot(HK,h('wrong'),h('witness'),h('instance'),h('fresh-request'),raw)

    def test_checkpoint_substitution(self):
        self.w.retain(self.cp,self.context,self.signed,self.transcript,self.bindings,self.members)
        with self.assertRaises(Rejected):self.retain()
        self.assertIsNone(self.snapshot()['pending'])

    def test_fake_witness(self):
        with self.assertRaises(Rejected):self.retain(witness=object())

    def test_invalid_transcript_persists_intent(self):
        with self.assertRaises(ValueError):self.retain(transcript=b'bad')
        self.assertIsNotNone(self.snapshot()['pending'])
        self.holder.close();self.holder=self.hopen()
        with self.assertRaises(Rejected):self.retain(2)

    def test_lost_retain_bytes_still_fenced(self):
        before=self.path.read_bytes()
        original=os.fsync
        def fail_witness(fd):
            if fd==self.w._fd:raise OSError('witness uncertain')
            return original(fd)
        with patch('os.fsync',side_effect=fail_witness):
            with self.assertRaises(OSError):self.retain()
        self.w.close();self.path.write_bytes(before)
        self.w=self.open(self.cp) # witness alone has no launch tombstone
        self.holder.close();self.holder=self.hopen()
        self.assertEqual(self.snapshot()['pending']['launch'],self.launch)
        with self.assertRaises(Rejected):self.retain(2)

    def test_intent_ack_loss_never_calls_witness(self):
        with patch('r1_checkpoint_holder.os.fsync',side_effect=OSError('holder uncertainty')):
            with self.assertRaises(OSError):self.retain()
        self.assertEqual(self.w.checkpoint(),self.cp)
        self.holder.close();self.holder=self.hopen()
        self.assertIsNotNone(self.snapshot()['pending'])
        with self.assertRaises(Rejected):self.retain(2)

    def test_lost_intent_bytes_no_witness_mutation(self):
        before=self.hp.read_bytes()
        with patch('r1_checkpoint_holder.os.fsync',side_effect=OSError('holder uncertainty')):
            with self.assertRaises(OSError):self.retain()
        self.holder.close();self.hp.write_bytes(before)
        self.assertEqual(self.w.checkpoint(),self.cp)
        self.holder=self.hopen();self.retain() # no prior witness attempt took place

    def test_publish_loss_leaves_intent(self):
        original=self.holder._append
        def append(kind,data):
            if kind=='PUBLISH':raise OSError('publication unavailable')
            return original(kind,data)
        with patch.object(self.holder,'_append',side_effect=append):
            with self.assertRaises(OSError):self.retain()
        self.assertNotEqual(self.w.checkpoint(),self.cp)
        self.holder.close();self.holder=self.hopen()
        with self.assertRaises(Rejected):self.retain(2)

    def test_publish_ack_lost_but_durable(self):
        original=self.holder._append
        def append(kind,data):
            original(kind,data)
            if kind=='PUBLISH':raise OSError('ack lost')
        with patch.object(self.holder,'_append',side_effect=append):
            with self.assertRaises(OSError):self.retain()
        self.holder.close();self.holder=self.hopen()
        self.assertEqual(self.snapshot()['revision'],3)
        with self.assertRaises(Rejected):self.retain(3)

    def test_partial_storage(self):
        self.holder.close()
        with self.hp.open('ab') as f:f.write(b'{')
        with self.assertRaises(Rejected):self.hopen()

    def test_corrupt_storage(self):
        self.holder.close();self.hp.write_bytes(self.hp.read_bytes().replace(h('instance').encode(),h('bad').encode()))
        with self.assertRaises(Rejected):self.hopen()

    def test_reordered_storage(self):
        self.retain();self.holder.close();rows=self.hp.read_bytes().splitlines(keepends=True)
        rows[1],rows[2]=rows[2],rows[1];self.hp.write_bytes(b''.join(rows))
        with self.assertRaises(Rejected):self.hopen()

    def test_wrong_holder_key(self):
        self.holder.close()
        with self.assertRaises(Rejected):self.hopen(key=b'x'*32)

    def test_wrong_witness_key(self):
        self.holder.close()
        with self.assertRaises(Rejected):self.hopen(registry={h('witness'):b'x'*32})

    def test_duplicate_provisioned_keys(self):
        with self.assertRaises(Rejected):self.hopen(path=Path(self.tmp.name)/'bad',create=True,registry={h('witness'):HK})

    def test_second_writer(self):
        with self.assertRaises(BlockingIOError):self.hopen()

    def test_symlink(self):
        p=Path(self.tmp.name)/'alias';p.symlink_to(self.hp)
        with self.assertRaises(OSError):self.hopen(path=p)

    def test_hardlink(self):
        self.holder.close();os.link(self.hp,Path(self.tmp.name)/'hard')
        with self.assertRaises(Rejected):self.hopen()

    def test_path_escape(self):
        with self.assertRaises(Rejected):self.hopen(path=str(self.hp)+'/../bad')

    def test_missing_storage(self):
        self.holder.close();self.hp.unlink()
        with self.assertRaises(FileNotFoundError):self.hopen()

    def test_no_reset_existing(self):
        with self.assertRaises(FileExistsError):self.hopen(create=True)

    def test_forked_holder(self):
        with patch('r1_checkpoint_holder.os.getpid',return_value=os.getpid()+1):
            with self.assertRaises(Rejected):self.snapshot()

    def test_short_write(self):
        original=os.write
        with patch('os.write',side_effect=lambda fd,b:original(fd,b[:7])):self.retain()
        self.assertEqual(self.snapshot()['revision'],3)

    def test_zero_write_no_local_mutation(self):
        with patch('os.write',return_value=0):
            with self.assertRaises(OSError):self.retain()
        self.assertEqual(self.w.checkpoint(),self.cp)
        with self.assertRaises(Rejected):self.snapshot()

    def test_mutable_bindings_failure_no_reissue(self):
        self.bindings['source']=h('changed')
        with self.assertRaises(ValueError):self.retain()
        with self.assertRaises(Rejected):self.retain(2)

    def test_local_rollback_only_detected(self):
        before=self.path.read_bytes();self.retain();cp=self.snapshot()['checkpoint'].encode()
        self.w.close();self.path.write_bytes(before)
        with self.assertRaises(Rejected):self.open(cp)

if __name__=='__main__':unittest.main(verbosity=2)
