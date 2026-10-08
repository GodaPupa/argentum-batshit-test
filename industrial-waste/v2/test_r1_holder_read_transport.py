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
from r1_holder_read_transport import HolderReadEndpoint, encode_request, decode_response, binding_context
TK=b"t"*32
HK = b'h' * 32
WK = b'w' * 32
SK = b's' * 32

def h(s): return hashlib.sha256(s.encode()).hexdigest()


class TransportTests(unittest.TestCase):
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
        self.binding={k:h(k) for k in ('client','holder','witness','instance','source')}
        self.endpoint=HolderReadEndpoint(self.holder,TK,self.binding)

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

    def req(self,n='nonce'):return encode_request(TK,self.binding,h(n))
    def test_positive_and_holder_signature(self):
        req=self.req();raw=self.endpoint.handle(req);snapshot=decode_response(TK,self.binding,req,raw)
        v=verify_snapshot(HK,h('holder'),h('witness'),h('instance'),h('nonce'),snapshot)
        self.assertEqual(v['revision'],1);self.assertFalse(v['admission_authority'])
    def test_no_journal_mutation(self):
        before=(self.path.read_bytes(),self.hp.read_bytes());self.endpoint.handle(self.req())
        self.assertEqual(before,(self.path.read_bytes(),self.hp.read_bytes()))
    def test_duplicate_request(self):
        self.endpoint.handle(self.req())
        with self.assertRaises(Rejected):self.endpoint.handle(self.req())
    def test_wrong_request_key(self):
        with self.assertRaises(Rejected):self.endpoint.handle(encode_request(b'x'*32,self.binding,h('nonce')))
    def test_wrong_source(self):
        b=dict(self.binding,source=h('other'))
        with self.assertRaises(Rejected):self.endpoint.handle(encode_request(TK,b,h('nonce')))
    def test_wrong_client(self):
        b=dict(self.binding,client=h('other'))
        with self.assertRaises(Rejected):self.endpoint.handle(encode_request(TK,b,h('nonce')))
    def test_mutation_operation_rejected(self):
        r=seal(TK,'holder-read-request',binding_context(self.binding),{'operation':'RETAIN','nonce':h('nonce')})
        with self.assertRaises(Rejected):self.endpoint.handle(r)
    def test_extra_fields_rejected(self):
        r=seal(TK,'holder-read-request',binding_context(self.binding),{'operation':'SNAPSHOT','nonce':h('nonce'),'path':'/tmp/other'})
        with self.assertRaises(Rejected):self.endpoint.handle(r)
    def test_response_reflection_rejected(self):
        raw=self.endpoint.handle(self.req())
        with self.assertRaises(Rejected):self.endpoint.handle(raw)
    def test_response_wrong_request(self):
        raw=self.endpoint.handle(self.req())
        with self.assertRaises(Rejected):decode_response(TK,self.binding,self.req('other'),raw)
    def test_response_wrong_key(self):
        raw=self.endpoint.handle(self.req())
        with self.assertRaises(Rejected):decode_response(b'x'*32,self.binding,self.req(),raw)
    def test_substituted_response_payload(self):
        req=self.req();v=authenticate(TK,self.endpoint.handle(req),'holder-read-response',binding_context(self.binding));v['nonce']=h('other')
        with self.assertRaises(Rejected):decode_response(TK,self.binding,req,seal(TK,'holder-read-response',binding_context(self.binding),v))
    def test_snapshot_failure_burns_live_nonce(self):
        with patch.object(self.holder,'snapshot',side_effect=OSError('unavailable')):
            with self.assertRaises(OSError):self.endpoint.handle(self.req())
        with self.assertRaises(Rejected):self.endpoint.handle(self.req())
    def test_shared_key(self):
        with self.assertRaises(Rejected):HolderReadEndpoint(self.holder,HK,self.binding)
    def test_wrong_holder_identity(self):
        with self.assertRaises(Rejected):HolderReadEndpoint(self.holder,TK,dict(self.binding,holder=h('other')))
    def test_fake_holder(self):
        with self.assertRaises(Rejected):HolderReadEndpoint(object(),TK,self.binding)
    def test_copied_binding(self):
        req=self.req();self.binding['source']=h('mutated');self.endpoint.handle(req)
    def test_capacity_fail_closed(self):
        with patch('r1_holder_read_transport.MAX_REQUESTS',1):
            self.endpoint.handle(self.req())
            with self.assertRaises(Rejected):self.endpoint.handle(self.req('second'))
    def test_concurrent_replay_one_winner(self):
        results=[]
        def call():
            try:self.endpoint.handle(self.req());results.append(True)
            except Rejected:results.append(False)
        ts=[threading.Thread(target=call) for _ in range(8)]
        for t in ts:t.start()
        for t in ts:t.join()
        self.assertEqual(sum(results),1)
    def test_restart_replay_limit_explicit(self):
        self.endpoint.handle(self.req());other=HolderReadEndpoint(self.holder,TK,self.binding)
        other.handle(self.req()) # no persistent server replay claim
    def test_malformed_request(self):
        with self.assertRaises(ValueError):self.endpoint.handle(b'{}')
    def test_oversize_request(self):
        with self.assertRaises(ValueError):self.endpoint.handle(b' '*20000)
if __name__=='__main__':unittest.main(verbosity=2)
