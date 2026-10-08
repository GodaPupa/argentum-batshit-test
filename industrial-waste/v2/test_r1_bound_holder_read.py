"""Excluded deterministic configuration-binding qualification; no worker/game."""
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from r1_anchor_witness import AnchorWitness
from r1_bound_holder_read import BoundHolderRead
from r1_checkpoint_recovery import RecoveryHolder
from r1_holder_read_transport import HolderReadEndpoint
from r1_receiver_evidence import Rejected
from r1_verified_holder_read import VerifiedHolderRead

CK, TK, HK, WK, SK = (bytes([v])*32 for v in (1,2,3,4,5))
def h(s): return hashlib.sha256(s.encode()).hexdigest()


class BindingTests(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory(); self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.path = self.root/'client'
        self.binding = {k:h(k) for k in ('client','holder','witness','instance','source')}
        self.w = AnchorWitness(self.root/'witness', WK, SK, h('witness'))
        self.addCleanup(self.w.close); self.cp = self.w.checkpoint()
        self.holder = RecoveryHolder(self.root/'holder',HK,h('holder'),{h('witness'):WK},create=True)
        self.addCleanup(self.holder.close)
        self.holder.enroll(h('witness'),h('instance'),self.cp)
        self.endpoint = HolderReadEndpoint(self.holder,TK,self.binding)
        self.reader = self.open(create=True)

    def open(self, **kw):
        args = dict(path=self.path,client_key=CK,transport_key=TK,holder_key=HK,
                    witness_key=WK,binding=self.binding,deployment=h('deployment'),
                    initial_revision=1,initial_checkpoint=self.cp)
        args.update(kw)
        r = BoundHolderRead(**args); self.addCleanup(r.close); return r

    def read(self): return self.reader.read(self.endpoint.handle)

    def reopen(self):
        self.reader.close(); self.reader = self.open()

    def refused(self, **kw):
        self.reader.close(); before = self.path.read_bytes()
        with self.assertRaises((ValueError, OSError)): self.open(**kw)
        self.assertEqual(before,self.path.read_bytes())
        self.reader = self.open()  # rejected config did not destroy valid history

    def test_clean_restart_preserves_ordinal(self):
        requests=[]
        def exchange(req): requests.append(req); return self.endpoint.handle(req)
        self.reader.read(exchange); self.reopen(); self.reader.read(exchange)
        self.assertNotEqual(requests[0],requests[1])

    def test_source_change_refused(self):
        self.read(); self.refused(binding=dict(self.binding,source=h('changed')))

    def test_transport_key_change_refused(self):
        self.read(); self.refused(transport_key=b'z'*32)

    def test_deployment_change_refused(self):
        self.refused(deployment=h('other deployment'))

    def test_client_key_change_refused(self): self.refused(client_key=b'z'*32)
    def test_holder_key_change_refused(self): self.refused(holder_key=b'z'*32)
    def test_witness_key_change_refused(self): self.refused(witness_key=b'z'*32)

    def test_binding_identity_change_refused(self):
        for field in ('client','holder','witness','instance'):
            with self.subTest(field=field):
                self.refused(binding=dict(self.binding,**{field:h('different')}))

    def test_bootstrap_change_refused(self): self.refused(initial_revision=2)

    def test_root_role_alias_before_create(self):
        for field in ('transport_key','holder_key','witness_key'):
            with self.subTest(field=field):
                p=self.root/field
                with self.assertRaises(Rejected):self.open(path=p,create=True,**{field:CK})
                self.assertFalse(p.exists())

    def test_invalid_provisioning_before_create(self):
        for kw in ({'deployment':'bad'},{'binding':dict(self.binding,extra=h('extra'))},
                   {'client_key':b'short'},{'transport_key':bytearray(TK)}):
            with self.subTest(kw=list(kw)):
                p=self.root/'invalid'
                with self.assertRaises(Rejected):self.open(path=p,create=True,**kw)
                self.assertFalse(p.exists())

    def test_live_caller_binding_mutation_no_effect(self):
        self.binding['source']=h('mutation'); self.assertFalse(self.read()['admission_authority'])

    def test_create_cannot_overwrite_history(self): self.refused(create=True)

    def test_missing_history_does_not_auto_create(self):
        p=self.root/'absent'
        with self.assertRaises(FileNotFoundError):self.open(path=p)
        self.assertFalse(p.exists())

    def test_legacy_journal_is_not_silently_migrated(self):
        p=self.root/'legacy'
        r=VerifiedHolderRead(p,CK,TK,HK,WK,self.binding,1,self.cp,create=True)
        r.close(); before=p.read_bytes()
        with self.assertRaises(ValueError):self.open(path=p)
        self.assertEqual(before,p.read_bytes())

    def test_bound_journal_refuses_raw_root_key(self):
        self.reader.close(); before=self.path.read_bytes()
        with self.assertRaises(ValueError):
            VerifiedHolderRead(self.path,CK,TK,HK,WK,self.binding,1,self.cp)
        self.assertEqual(before,self.path.read_bytes())

    def test_pending_request_survives_reopen(self):
        calls=[]
        def lose(req): calls.append(req); raise OSError('lost response')
        with self.assertRaises(OSError):self.reader.read(lose)
        self.reopen()
        with self.assertRaises(Rejected):self.reader.read(lose)
        self.assertEqual(len(calls),1)

    def test_issue_uncertainty_prevents_exchange(self):
        calls=[]
        with patch('os.fsync',side_effect=OSError('uncertain ISSUE')):
            with self.assertRaises(OSError):self.reader.read(lambda req:calls.append(req))
        self.assertEqual(calls,[]); self.reopen()
        with self.assertRaises(Rejected):self.read()

    def test_retained_accept_after_uncertain_ack_keeps_floor(self):
        self.w.close()
        recovered=self.holder.recover(h('witness'),h('instance'),1,str(self.root/'witness'),SK)
        self.addCleanup(recovered['witness'].close)
        real_fsync=os.fsync; count=[]
        def fsync(fd):
            count.append(fd)
            if len(count)==2:raise OSError('ACCEPT sync uncertain')
            return real_fsync(fd)
        with patch('os.fsync',side_effect=fsync):
            with self.assertRaises(OSError):self.read()
        self.reopen(); self.assertEqual(self.read()['revision'],3)

    def test_symlink_refused(self):
        self.reader.close(); p=self.root/'alias';p.symlink_to(self.path)
        with self.assertRaises(OSError):self.open(path=p)

    def test_hardlink_refused(self):
        self.reader.close();p=self.root/'alias';os.link(self.path,p)
        with self.assertRaises(Rejected):self.open(path=p)

    def test_fresh_genesis_sync_failure_no_repair(self):
        p=self.root/'uncertain'
        with patch('os.fsync',side_effect=OSError('creation uncertain')):
            with self.assertRaises(OSError):self.open(path=p,create=True)
        self.assertTrue(p.exists())
        with self.assertRaises(Rejected):self.open(path=p)
        with self.assertRaises(FileExistsError):self.open(path=p,create=True)

    def test_no_holder_witness_mutation(self):
        paths=[self.root/'holder',self.root/'witness'];before=[p.read_bytes() for p in paths]
        self.read();self.assertEqual(before,[p.read_bytes() for p in paths])

if __name__=='__main__':unittest.main(verbosity=2)
