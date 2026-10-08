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
CK=b"c"*32
from r1_verified_holder_read import VerifiedHolderRead
HK = b'h' * 32
WK = b'w' * 32
SK = b's' * 32

def h(s): return hashlib.sha256(s.encode()).hexdigest()


class CompositionTests(unittest.TestCase):
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
        self.clientpath=Path(self.tmp.name)/"client"
        self.reader=self.ropen(create=True)

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

    def ropen(self,create=False,**kw):
        r=VerifiedHolderRead(self.clientpath,CK,kw.get('tk',TK),HK,WK,
                            kw.get('binding',self.binding),1,self.cp,create=create)
        self.addCleanup(r.close);return r

    def reopen(self):
        self.reader.close();self.reader=self.ropen()

    def read(self,exchange=None):
        r=self.reader.read(self.endpoint.handle if exchange is None else exchange)
        self.assertFalse(r['admission_authority']);return r

    def wrap_snapshot(self,request,snapshot):
        req=authenticate(TK,request,'holder-read-request',binding_context(self.binding))
        return seal(TK,'holder-read-response',binding_context(self.binding),
                    {'request_sha256':hashlib.sha256(request).hexdigest(),'nonce':req['nonce'],
                     'snapshot':snapshot.decode(),'admission_authority':False})

    def alter(self,request,change):
        raw=decode_response(TK,self.binding,request,self.endpoint.handle(request))
        value=authenticate(HK,raw,'checkpoint-holder-snapshot',h('holder'))
        change(value)
        return self.wrap_snapshot(request,seal(HK,'checkpoint-holder-snapshot',h('holder'),value))

    def test_positive_three_authentication_layers(self):
        self.assertEqual(self.read()['checkpoint'],self.cp)
        self.retain();self.assertEqual(self.read()['revision'],3)

    def test_issue_durable_before_carrier(self):
        def exchange(req):
            rows=[json.loads(x) for x in self.clientpath.read_bytes().splitlines()]
            self.assertEqual(rows[-1]['payload']['kind'],'ISSUE')
            return self.endpoint.handle(req)
        self.read(exchange)
        self.assertEqual(json.loads(self.clientpath.read_bytes().splitlines()[-1])['payload']['kind'],'ACCEPT')

    def test_clean_restart_floor_and_endpoint_restart(self):
        self.retain();self.read();self.reopen()
        self.endpoint=HolderReadEndpoint(self.holder,TK,self.binding)
        self.assertEqual(self.read()['revision'],3)

    def test_holder_recovery_progress(self):
        self.read();self.w.close();r=self.holder.recover(h('witness'),h('instance'),1,str(self.path),SK)
        self.w=r['witness'];self.addCleanup(self.w.close)
        self.assertEqual(self.read()['revision'],3)

    def test_transport_mac_valid_holder_mac_invalid(self):
        def exchange(req):
            raw=decode_response(TK,self.binding,req,self.endpoint.handle(req))
            v=authenticate(HK,raw,'checkpoint-holder-snapshot',h('holder'))
            return self.wrap_snapshot(req,seal(b'x'*32,'checkpoint-holder-snapshot',h('holder'),v))
        with self.assertRaises(Rejected):self.read(exchange)
        self.reopen()
        with self.assertRaises(Rejected):self.read()

    def test_transport_holder_valid_witness_mac_invalid(self):
        def change(v):
            cp=authenticate(WK,v['checkpoint'].encode(),'witness-checkpoint',h('witness'))
            v['checkpoint']=seal(b'x'*32,'witness-checkpoint',h('witness'),cp).decode()
        with self.assertRaises(Rejected):self.read(lambda req:self.alter(req,change))

    def test_nested_nonce_substitution(self):
        with self.assertRaises(Rejected):self.read(lambda req:self.alter(req,lambda v:v.update(nonce=h('old'))))

    def test_nested_instance_substitution(self):
        with self.assertRaises(Rejected):self.read(lambda req:self.alter(req,lambda v:v.update(instance=h('other'))))

    def test_authenticated_regression_after_restart(self):
        self.retain();self.read();self.reopen()
        with self.assertRaises(Rejected):self.read(lambda req:self.alter(req,lambda v:v.update(revision=1,checkpoint=self.cp.decode())))

    def test_authenticated_same_revision_fork(self):
        cp=seal(WK,'witness-checkpoint',h('witness'),{'rows':2,'tail':h('fork'),'witness':h('witness')}).decode()
        with self.assertRaises(Rejected):self.read(lambda req:self.alter(req,lambda v:v.update(checkpoint=cp)))

    def test_pending_holder_refused_even_with_valid_transport(self):
        with self.assertRaises(ValueError):self.retain(transcript=b'bad')
        with self.assertRaises(Rejected):self.read()

    def test_outer_mac_invalid(self):
        def exchange(req):
            v=authenticate(TK,self.endpoint.handle(req),'holder-read-response',binding_context(self.binding))
            return seal(b'x'*32,'holder-read-response',binding_context(self.binding),v)
        with self.assertRaises(Rejected):self.read(exchange)

    def test_old_reply_replay(self):
        replies=[]
        def exchange(req):
            replies.append(self.endpoint.handle(req));return replies[-1]
        self.read(exchange)
        with self.assertRaises(Rejected):self.read(lambda req:replies[0])

    def test_exchange_failure_no_retry_and_restart_fence(self):
        calls=[]
        def exchange(req):calls.append(req);raise OSError('wire unavailable')
        with self.assertRaises(OSError):self.read(exchange)
        with self.assertRaises(Rejected):self.read(exchange)
        self.assertEqual(len(calls),1);self.reopen()
        with self.assertRaises(Rejected):self.read(exchange)
        self.assertEqual(len(calls),1)

    def test_response_loss_after_endpoint_read(self):
        def exchange(req):self.endpoint.handle(req);raise OSError('reply lost')
        with self.assertRaises(OSError):self.read(exchange)
        self.reopen()
        with self.assertRaises(Rejected):self.read()

    def test_issue_write_uncertainty_never_calls_carrier(self):
        calls=[]
        with patch('os.fsync',side_effect=OSError('issue uncertain')):
            with self.assertRaises(OSError):self.read(lambda req:calls.append(req))
        self.assertEqual(calls,[])

    def test_accept_fsync_uncertainty_persisted_floor(self):
        self.retain()
        def exchange(req):
            raw=self.endpoint.handle(req)
            # Inject only after ISSUE and endpoint read; restored by outer context.
            patcher=patch('os.fsync',side_effect=OSError('accept uncertain'));patcher.start();self.addCleanup(patcher.stop)
            return raw
        try:
            with self.assertRaises(OSError):self.read(exchange)
        finally:patch.stopall()
        self.reopen();self.assertEqual(self.read()['revision'],3)

    def test_lost_accept_bytes_leave_issue_pending(self):
        before=[]
        def exchange(req):
            before.append(self.clientpath.read_bytes())
            return self.endpoint.handle(req)
        self.read(exchange);self.reader.close()
        self.clientpath.write_bytes(before[0]) # model lost last ACCEPT, retained ISSUE
        self.reader=self.ropen()
        with self.assertRaises(Rejected):self.read()

    def test_malformed_outer_response(self):
        with self.assertRaises(ValueError):self.read(lambda req:b'{}')
        with self.assertRaises(Rejected):self.read()

    def test_key_alias_rejected_before_storage(self):
        self.reader.close();self.clientpath=Path(self.tmp.name)/'alias-client'
        with self.assertRaises(Rejected):self.ropen(create=True,tk=CK)
        self.assertFalse(self.clientpath.exists())

    def test_binding_copy(self):
        self.binding['source']=h('changed')
        self.read()

    def test_wrong_source_label(self):
        self.reader.close();self.clientpath=Path(self.tmp.name)/'other-client'
        self.reader=self.ropen(create=True,binding=dict(self.binding,source=h('other')))
        with self.assertRaises(Rejected):self.read()

    def test_no_holder_or_witness_mutation(self):
        before=(self.hp.read_bytes(),self.path.read_bytes());self.read()
        self.assertEqual(before,(self.hp.read_bytes(),self.path.read_bytes()))

    def test_concurrent_reads_serial_distinct_challenges(self):
        requests=[];results=[]
        def exchange(req):requests.append(req);return self.endpoint.handle(req)
        def read():results.append(self.reader.read(exchange))
        ts=[threading.Thread(target=read) for _ in range(4)]
        for t in ts:t.start()
        for t in ts:t.join()
        self.assertEqual(len(results),4);self.assertEqual(len(set(requests)),4)

if __name__=='__main__':unittest.main(verbosity=2)
