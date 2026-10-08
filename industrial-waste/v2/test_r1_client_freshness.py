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
from r1_client_freshness import FreshnessClient
CK = b"c"*32
HK = b'h' * 32
WK = b'w' * 32
SK = b's' * 32

def h(s): return hashlib.sha256(s.encode()).hexdigest()


class FreshnessTests(unittest.TestCase):
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
        self.clientpath=Path(self.tmp.name)/'client'
        self.binding={k:h(k) for k in ('client','holder','witness','instance')}
        self.client=self.copen(create=True)

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

    def copen(self,create=False,**kw):
        c=FreshnessClient(kw.get('path',self.clientpath),kw.get('key',CK),HK,WK,
                         kw.get('binding',self.binding),kw.get('revision',1),self.cp,create=create)
        self.addCleanup(c.close);return c

    def response(self,nonce,**changes):
        raw=self.holder.snapshot(h('witness'),h('instance'),nonce)
        if changes:
            v=authenticate(HK,raw,'checkpoint-holder-snapshot',h('holder'));v.update(changes)
            return seal(HK,'checkpoint-holder-snapshot',h('holder'),v)
        return raw

    def poll(self):
        result=self.client.accept(self.response(self.client.issue()))
        self.assertFalse(result['admission_authority']);return result

    def reopen(self):
        self.client.close();self.client=self.copen()

    def test_positive_and_same_state_new_challenge(self):
        n=self.client.issue();self.client.accept(self.response(n));m=self.client.issue()
        self.assertNotEqual(n,m);self.client.accept(self.response(m))

    def test_floor_survives_restart_and_rejects_rollback(self):
        old=self.cp;self.retain();self.assertEqual(self.poll()['revision'],3);self.reopen()
        nonce=self.client.issue()
        with self.assertRaises(Rejected):self.client.accept(self.response(nonce,revision=1,checkpoint=old.decode()))

    def test_progress_through_accepted_recovery(self):
        self.poll();self.w.close()
        result=self.holder.recover(h('witness'),h('instance'),1,str(self.path),SK)
        self.w=result['witness'];self.addCleanup(self.w.close)
        self.assertEqual(self.poll()['revision'],3);self.reopen();self.assertEqual(self.poll()['revision'],3)

    def test_old_nonce_replay(self):
        n=self.client.issue();raw=self.response(n);self.client.accept(raw);self.client.issue()
        with self.assertRaises(Rejected):self.client.accept(raw)

    def test_duplicate_response(self):
        raw=self.response(self.client.issue());self.client.accept(raw)
        with self.assertRaises(Rejected):self.client.accept(raw)

    def test_restart_pending_cannot_accept_or_refill(self):
        raw=self.response(self.client.issue());self.reopen()
        with self.assertRaises(Rejected):self.client.issue()
        with self.assertRaises(Rejected):self.client.accept(raw)

    def test_unsolicited_response(self):
        with self.assertRaises(Rejected):self.client.accept(self.response(h('unsolicited')))

    def test_concurrent_issue_one_winner(self):
        results=[]
        def issue():
            try:results.append(self.client.issue())
            except Rejected:results.append(None)
        ts=[threading.Thread(target=issue) for _ in range(8)]
        for t in ts:t.start()
        for t in ts:t.join()
        self.assertEqual(sum(x is not None for x in results),1)

    def test_holder_pending_refused(self):
        with self.assertRaises(ValueError):self.retain(transcript=b'bad')
        with self.assertRaises(Rejected):self.poll()

    def test_same_revision_equivocation(self):
        nonce=self.client.issue();cp=seal(WK,'witness-checkpoint',h('witness'),{'witness':h('witness'),'rows':2,'tail':h('fork')})
        with self.assertRaises(Rejected):self.client.accept(self.response(nonce,checkpoint=cp.decode()))

    def test_higher_revision_same_height_fork(self):
        nonce=self.client.issue();cp=seal(WK,'witness-checkpoint',h('witness'),{'witness':h('witness'),'rows':1,'tail':h('fork')})
        with self.assertRaises(Rejected):self.client.accept(self.response(nonce,revision=3,checkpoint=cp.decode()))

    def test_higher_revision_without_checkpoint_progress(self):
        n=self.client.issue()
        with self.assertRaises(Rejected):self.client.accept(self.response(n,revision=3))

    def test_checkpoint_height_regression(self):
        self.retain();self.poll();n=self.client.issue()
        with self.assertRaises(Rejected):self.client.accept(self.response(n,revision=5,checkpoint=self.cp.decode()))

    def test_wrong_holder_key(self):
        n=self.client.issue();raw=self.response(n);v=authenticate(HK,raw,'checkpoint-holder-snapshot',h('holder'))
        with self.assertRaises(Rejected):self.client.accept(seal(b'x'*32,'checkpoint-holder-snapshot',h('holder'),v))

    def test_wrong_witness_checkpoint_key(self):
        n=self.client.issue();v=authenticate(WK,self.cp,'witness-checkpoint',h('witness'));cp=seal(b'x'*32,'witness-checkpoint',h('witness'),v)
        with self.assertRaises(Rejected):self.client.accept(self.response(n,checkpoint=cp.decode()))

    def test_wrong_instance(self):
        n=self.client.issue()
        with self.assertRaises(Rejected):self.client.accept(self.response(n,instance=h('clone')))

    def test_bool_revision(self):
        n=self.client.issue()
        with self.assertRaises(Rejected):self.client.accept(self.response(n,revision=True))

    def test_malformed_response_poisons(self):
        self.client.issue()
        with self.assertRaises(ValueError):self.client.accept(b'{}')
        with self.assertRaises(Rejected):self.client.issue()

    def test_issue_fsync_uncertainty_no_nonce_return(self):
        with patch('os.fsync',side_effect=OSError('uncertain issue')):
            with self.assertRaises(OSError):self.client.issue()
        self.reopen()
        with self.assertRaises(Rejected):self.client.issue()

    def test_accept_fsync_uncertainty_durable_floor(self):
        self.retain();raw=self.response(self.client.issue())
        with patch('os.fsync',side_effect=OSError('uncertain accept')):
            with self.assertRaises(OSError):self.client.accept(raw)
        self.reopen();self.assertEqual(self.poll()['revision'],3)

    def test_lost_accept_bytes_leaves_pending(self):
        raw=self.response(self.client.issue());before=self.clientpath.read_bytes()
        with patch('os.fsync',side_effect=OSError('uncertain accept')):
            with self.assertRaises(OSError):self.client.accept(raw)
        self.client.close();self.clientpath.write_bytes(before);self.client=self.copen()
        with self.assertRaises(Rejected):self.client.issue()
        with self.assertRaises(Rejected):self.client.accept(raw)

    def test_bootstrap_substitution(self):
        self.client.close()
        with self.assertRaises(Rejected):self.copen(revision=3)

    def test_binding_mutation_does_not_change_client(self):
        self.binding['instance']=h('changed');self.poll()

    def test_wrong_client_key_on_reopen(self):
        self.client.close()
        with self.assertRaises(Rejected):self.copen(key=b'x'*32)

    def test_reordered_journal(self):
        self.poll();self.client.close();rows=self.clientpath.read_bytes().splitlines(keepends=True)
        rows[1],rows[2]=rows[2],rows[1];self.clientpath.write_bytes(b''.join(rows))
        with self.assertRaises(Rejected):self.copen()

    def test_partial_journal(self):
        self.client.close()
        with self.clientpath.open('ab') as f:f.write(b'{')
        with self.assertRaises(Rejected):self.copen()

    def test_second_writer(self):
        with self.assertRaises(BlockingIOError):self.copen()

    def test_symlink(self):
        p=Path(self.tmp.name)/'alias';p.symlink_to(self.clientpath)
        with self.assertRaises(OSError):self.copen(path=p)

    def test_missing_journal_not_recreated(self):
        self.client.close();self.clientpath.unlink()
        with self.assertRaises(FileNotFoundError):self.copen()

    def test_forked_client(self):
        with patch('os.getpid',return_value=os.getpid()+1):
            with self.assertRaises(Rejected):self.client.issue()

    def test_short_writes(self):
        original=os.write
        with patch('os.write',side_effect=lambda fd,b:original(fd,b[:9])):self.poll()

    def test_zero_write_no_issue(self):
        with patch('os.write',return_value=0):
            with self.assertRaises(OSError):self.client.issue()
        with self.assertRaises(Rejected):self.client.issue()

if __name__=='__main__':unittest.main(verbosity=2)
