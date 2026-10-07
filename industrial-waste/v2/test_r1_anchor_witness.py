"""Non-corpus witness fixtures; checkpoints retained separately by test controller."""
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

WK = b'w' * 32
SK = b's' * 32

def h(s): return hashlib.sha256(s.encode()).hexdigest()


class WitnessTests(unittest.TestCase):
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

    def open(self,cp=None,path=None):
        w=AnchorWitness(self.path if path is None else path,WK,SK,h('witness'),cp)
        self.addCleanup(w.close);return w

    def retain(self):
        r=self.w.retain(self.cp,self.context,self.signed,self.transcript,self.bindings,self.members)
        self.assertFalse(r['admission_authority']);self.cp=self.w.checkpoint();return r

    def consume(self):
        r=self.w.consume(self.cp,self.launch,self.context,self.anchor_hash)
        self.assertFalse(r['admission_authority']);self.cp=self.w.checkpoint();return r

    def terminal(self):
        with self.assertRaises(Rejected):self.w.checkpoint()

    def test_positive_and_consumed_restart(self):
        self.retain();self.assertEqual(self.consume()['state'],'CONSUMED')
        self.w.close();self.w=self.open(self.cp)
        with self.assertRaises(Rejected):self.w.consume(self.w.checkpoint(),self.launch,self.context,self.anchor_hash)

    def test_duplicate_retain(self):
        self.retain()
        with self.assertRaises(Rejected):self.retain()
        self.terminal()

    def test_duplicate_consume(self):
        self.retain();self.consume()
        with self.assertRaises(Rejected):self.consume()
        self.terminal()

    def test_unknown_consume(self):
        with self.assertRaises(Rejected):self.consume()

    def test_concurrent_consume(self):
        self.retain();results=[]
        def consume():
            try:self.w.consume(self.cp,self.launch,self.context,self.anchor_hash);results.append('ok')
            except Rejected:results.append('rejected')
        ts=[threading.Thread(target=consume) for _ in range(8)]
        for t in ts:t.start()
        for t in ts:t.join()
        self.assertEqual(results.count('ok'),1);self.assertEqual(results.count('rejected'),7)

    def test_unconsumed_restart_abandons(self):
        self.retain();self.w.close();self.w=self.open(self.cp);newcp=self.w.checkpoint()
        self.assertNotEqual(newcp,self.cp)
        with self.assertRaises(Rejected):self.w.consume(newcp,self.launch,self.context,self.anchor_hash)
        rows=[json.loads(x) for x in self.path.read_bytes().splitlines()]
        self.assertEqual(rows[-1]['data'],{'abandoned':[self.launch]})

    def test_abandoned_cannot_retain(self):
        self.retain();self.w.close();self.w=self.open(self.cp);self.cp=self.w.checkpoint()
        with self.assertRaises(Rejected):self.retain()

    def test_consumed_cannot_retain_after_restart(self):
        self.retain();self.consume();self.w.close();self.w=self.open(self.cp);self.cp=self.w.checkpoint()
        with self.assertRaises(Rejected):self.retain()

    def test_journal_rollback_detected(self):
        snapshot=self.path.read_bytes();self.retain();latest=self.cp;self.w.close();self.path.write_bytes(snapshot)
        with self.assertRaises(Rejected):self.open(latest)

    def test_lost_ack_checkpoint_blocks_reopen(self):
        old=self.cp;self.retain();self.w.close()
        with self.assertRaises(Rejected):self.open(old)

    def test_stale_checkpoint_poison(self):
        old=self.cp;self.retain()
        with self.assertRaises(Rejected):self.w.consume(old,self.launch,self.context,self.anchor_hash)
        self.terminal()

    def test_forged_checkpoint(self):
        value=authenticate(WK,self.cp,'witness-checkpoint',h('witness'));value['tail']=h('wrong')
        bad=seal(WK,'witness-checkpoint',h('witness'),value)
        with self.assertRaises(Rejected):self.w.retain(bad,self.context,self.signed,self.transcript,self.bindings,self.members)

    def test_bool_checkpoint_count(self):
        value=authenticate(WK,self.cp,'witness-checkpoint',h('witness'));value['rows']=True
        bad=seal(WK,'witness-checkpoint',h('witness'),value)
        with self.assertRaises(Rejected):self.w.retain(bad,self.context,self.signed,self.transcript,self.bindings,self.members)

    def test_wrong_witness_key(self):
        self.w.close()
        with self.assertRaises(Rejected):AnchorWitness(self.path,b'x'*32,SK,h('witness'),self.cp)

    def test_wrong_witness_identity(self):
        self.w.close()
        with self.assertRaises(Rejected):AnchorWitness(self.path,WK,SK,h('other'),self.cp)

    def test_wrong_supervisor_mac(self):
        bad=seal(b'x'*32,'receiver-anchor',self.context,self.anchor)
        with self.assertRaises(Rejected):self.w.retain(self.cp,self.context,bad,self.transcript,self.bindings,self.members)

    def test_changed_transcript(self):
        with self.assertRaises(ValueError):self.w.retain(self.cp,self.context,self.signed,self.transcript+b'\n',self.bindings,self.members)

    def test_wrong_policy(self):
        self.bindings['policy']=h('wrong')
        with self.assertRaises(ValueError):self.retain()

    def test_wrong_consumption_anchor(self):
        self.retain()
        with self.assertRaises(Rejected):self.w.consume(self.cp,self.launch,self.context,h('wrong'))

    def test_wrong_consumption_context(self):
        self.retain()
        with self.assertRaises(Rejected):self.w.consume(self.cp,self.launch,h('wrong'),self.anchor_hash)

    def test_partial_record_restart(self):
        self.w.close()
        with self.path.open('ab') as s:s.write(b'{')
        with self.assertRaises(Rejected):self.open(self.cp)

    def test_full_record_substitution(self):
        self.w.close();raw=self.path.read_bytes();self.path.write_bytes(raw.replace(h('witness').encode(),h('other').encode()))
        with self.assertRaises(Rejected):self.open(self.cp)

    def test_missing_checkpoint_no_recreate(self):
        self.w.close()
        with self.assertRaises(FileExistsError):self.open()

    def test_missing_journal_no_recreate(self):
        self.w.close();self.path.unlink()
        with self.assertRaises(FileNotFoundError):self.open(self.cp)

    def test_second_writer(self):
        with self.assertRaises(BlockingIOError):self.open(self.cp)

    def test_live_size_change(self):
        with self.path.open('ab') as s:s.write(b'\n')
        with self.assertRaises(Rejected):self.retain()

    def test_short_writes(self):
        write=os.write
        with patch('r1_anchor_witness.os.write',side_effect=lambda fd,b:write(fd,b[:9])):self.retain()
        self.consume()

    def test_zero_write(self):
        with patch('r1_anchor_witness.os.write',return_value=0):
            with self.assertRaises(OSError):self.retain()
        self.terminal()

    def test_fsync_uncertainty(self):
        with patch('r1_anchor_witness.os.fsync',side_effect=OSError('injected fsync failure')):
            with self.assertRaises(OSError):self.retain()
        self.terminal();self.w.close()
        with self.assertRaises(Rejected):self.open(self.cp)

    def test_failed_consume_lost_bytes_quarantines(self):
        self.retain();snapshot=self.path.read_bytes()
        with patch('r1_anchor_witness.os.fsync',side_effect=OSError('injected consume uncertainty')):
            with self.assertRaises(OSError):self.consume()
        self.w.close();self.path.write_bytes(snapshot) # model uncommitted write lost after crash
        self.w=self.open(self.cp)
        with self.assertRaises(Rejected):self.w.consume(self.w.checkpoint(),self.launch,self.context,self.anchor_hash)

    def test_symlink_leaf(self):
        link=Path(self.tmp.name)/'alias';link.symlink_to(self.path)
        with self.assertRaises(OSError):self.open(self.cp,link)

    def test_symlink_parent(self):
        link=Path(self.tmp.name)/'alias';link.symlink_to(self.tmp.name,target_is_directory=True)
        with self.assertRaises(OSError):self.open(self.cp,link/'journal')

    def test_hardlink(self):
        self.w.close();other=Path(self.tmp.name)/'hard';os.link(self.path,other)
        with self.assertRaises(Rejected):self.open(self.cp)

    def test_path_escape(self):
        with self.assertRaises(Rejected):self.open(None,str(self.path)+'/../escape')

    def test_shared_keys_rejected(self):
        with self.assertRaises(Rejected):AnchorWitness(Path(self.tmp.name)/'new',WK,WK,h('witness'))

    def test_fork_identity_rejected(self):
        with patch('r1_anchor_witness.os.getpid',return_value=os.getpid()+1):
            with self.assertRaises(Rejected):self.w.checkpoint()

if __name__=='__main__':unittest.main(verbosity=2)
