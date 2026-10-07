"""Synthetic integration: tiny post-exec relay holds PUBLIC fixture key only.
No official worker, JVM, corpus, secrets, seeds or experimental outcomes.
"""
import hashlib
import json
import os
from pathlib import Path
import socket
import struct
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from r1_receiver_evidence import canonical, decode, digest, Rejected
from r1_peer_identity import start_ticks
from r1_supervisor_channel import SupervisorChannel, authenticate, seal, verify_export

KEY = b'industrial-fixture-key-only-0001'
assert len(KEY) == 32

def h(s): return hashlib.sha256(s.encode()).hexdigest()

CHILD = r'''
import sys,socket,json,struct,hashlib
sys.path.insert(0,sys.argv[2])
from r1_supervisor_channel import seal,authenticate,_read_frame
from r1_receiver_evidence import canonical,decode,digest
key=b'industrial-fixture-key-only-0001'
s=socket.socket(socket.AF_UNIX);s.connect(sys.argv[1]);s.settimeout(5)
hello=_read_frame(s,5); outer=decode(hello);context=outer['context']
payload=authenticate(key,hello,'receiver-hello',context)
assert context==digest({'supervisor':hashlib.sha256(b'supervisor').hexdigest(),'challenge':payload['challenge']})
e=payload['challenge'];print(hello.decode(),flush=True);last=None
for line in sys.stdin:
 c=json.loads(line);mode=c.get('mode','normal')
 if mode=='idle': print('IDLE',flush=True);continue
 frame=dict(e,kind=c.get('kind','BIND'),data=c.get('data',{}))
 frame.update(c.get('changes',{}))
 raw=seal(key,'supervisor-event',context,frame)
 if mode=='wrong_key':raw=seal(b'x'*32,'supervisor-event',context,frame)
 if mode=='wrong_context':raw=seal(key,'supervisor-event','0'*64,frame)
 if mode=='reflection':raw=hello
 if mode=='replay':raw=last
 if mode=='tamper':
  out=decode(raw);out['payload']['kind']='CUTOFF';raw=canonical(out)
 if mode=='malformed':raw=b'{'
 if mode=='extra':
  out=decode(raw);out['verified']=True;raw=canonical(out)
 if mode=='oversize':s.sendall(struct.pack('!I',16385));print('SENT',flush=True);continue
 if mode=='truncated':s.sendall(struct.pack('!I',len(raw))+raw[:3]);s.shutdown(socket.SHUT_WR);print('SENT',flush=True);continue
 last=raw;s.sendall(struct.pack('!I',len(raw))+raw);print('SENT',flush=True)
 if c.get('ack',True):
  reply=_read_frame(s,5);a=authenticate(key,reply,'receiver-ack',context)
  assert a['event_sha256']==hashlib.sha256(raw).hexdigest()
  e.update({k:a['receipt'][k] for k in ('seq','previous')})
  print(reply.decode(),flush=True)
'''


class ChannelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # No skips. Local AF_UNIX prerequisite is known unavailable; run in pinned CI.
        with socket.socket(socket.AF_UNIX): pass
        fd=os.pidfd_open(os.getpid());os.close(fd)

    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        listener=socket.socket(socket.AF_UNIX);self.addCleanup(listener.close)
        socket_path=str(Path(self.tmp.name)/'socket');listener.bind(socket_path);listener.listen(1);listener.settimeout(5)
        self.child=subprocess.Popen([sys.executable,'-I','-c',CHILD,socket_path,str(Path(__file__).parent.resolve())],
                                    stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        self.addCleanup(self.stop)
        self.fd=os.pidfd_open(self.child.pid);self.addCleanup(os.close,self.fd)
        self.peer,_=listener.accept();self.addCleanup(self.peer.close)
        self.expected={'pid':self.child.pid,'uid':os.getuid(),'gid':os.getgid(),
            'start_ticks':start_ticks(Path('/proc/'+str(self.child.pid)+'/stat').read_text(),self.child.pid),
            'boot_id':Path('/proc/sys/kernel/random/boot_id').read_text().strip(),
            'executable_sha256':hashlib.sha256(Path(sys.executable).read_bytes()).hexdigest()}
        self.members={h('member'):{k:h(k) for k in ('definition','loader','module','origin')}}
        self.bindings={k:h(k) for k in ('source','tree','runtime','receiver')};self.bindings['policy']=digest(self.members)
        self.path=Path(self.tmp.name)/'evidence'
        self.channel=SupervisorChannel(self.path,KEY,h('supervisor'),self.fd,self.peer,self.expected,self.bindings,self.members,timeout=.5)
        self.addCleanup(self.channel.close)
        self.context=self.channel.start()
        raw=self.child.stdout.readline().strip().encode()
        self.hello=authenticate(KEY,raw,'receiver-hello',self.context)
        self.bound=self.hello['challenge']['bindings']

    def stop(self):
        if self.child.poll() is None:self.child.kill()
        self.child.wait(timeout=5)
        for stream in (self.child.stdin,self.child.stdout,self.child.stderr):stream.close()

    def command(self,kind='BIND',data=None,mode='normal',ack=True,changes=None):
        self.child.stdin.write(json.dumps({'kind':kind,'data':data or {},'mode':mode,'ack':ack,'changes':changes or {}})+'\n')
        self.child.stdin.flush()
        self.assertEqual(self.child.stdout.readline().strip(),'IDLE' if mode=='idle' else 'SENT')

    def send(self,kind,data=None):
        self.command(kind,data)
        result=self.channel.step()
        raw=self.child.stdout.readline().strip().encode()
        ack=authenticate(KEY,raw,'receiver-ack',self.context)
        self.assertEqual(ack['receipt'],result);self.assertFalse(result['admission_authority'])
        return result

    def attempt(self):return dict(id=h('attempt'),member=h('member'),family='ordinary',**self.members[h('member')])

    def complete(self):
        self.send('BIND');self.send('ATTEMPT',self.attempt())
        self.send('RESULT',{'id':h('attempt'),'result':'DEFINED'})
        self.send('CUTOFF');self.send('CONSUME_EVIDENCE')

    def assert_terminal(self):
        with self.assertRaises(Rejected):self.channel.step()
        with self.assertRaises(Rejected):self.channel.export()

    def reject(self,mode='normal',kind='BIND',data=None,changes=None):
        self.command(kind,data,mode,False,changes)
        with self.assertRaises((ValueError,OSError,TimeoutError)):self.channel.step()
        self.assert_terminal()

    def test_full_authenticated_export(self):
        self.complete();raw,anchor=self.channel.export()
        result=verify_export(KEY,self.context,anchor,raw,self.bound,self.members)
        self.assertTrue(result['provisioned_key_mac_valid']);self.assertFalse(result['external_rollback_resistance'])
        self.assertFalse(result['admission_authority']);self.assertFalse(result['vm_provenance_established'])
        with self.assertRaises(Rejected):self.channel.export()

    def test_wrong_key(self):self.reject('wrong_key')
    def test_payload_tamper(self):self.reject('tamper')
    def test_wrong_context(self):self.reject('wrong_context')
    def test_role_reflection(self):self.reject('reflection')
    def test_unknown_outer_field(self):self.reject('extra')
    def test_malformed(self):self.reject('malformed')
    def test_oversized_prefix(self):self.reject('oversize')
    def test_truncated_frame(self):self.reject('truncated')
    def test_timeout(self):self.reject('idle')
    def test_stale_challenge(self):self.reject(changes={'challenge':h('stale')})
    def test_wrong_launch(self):self.reject(changes={'launch':h('wrong')})
    def test_wrong_source(self):self.reject(changes={'bindings':dict(self.bound,source=h('wrong'))})
    def test_wrong_channel(self):self.reject(changes={'bindings':dict(self.bound,channel=h('wrong'))})
    def test_replay(self):self.send('BIND');self.reject('replay')
    def test_early_consume(self):self.reject(kind='CONSUME_EVIDENCE')

    def test_unknown_attempt(self):
        self.send('BIND');self.reject(kind='ATTEMPT',data=dict(self.attempt(),member=h('unknown')))
        rows=[json.loads(x) for x in (self.path/'events.jsonl').read_text().splitlines()]
        self.assertEqual([r['kind'] for r in rows][-2:],['ATTEMPT','FAILED'])

    def test_hidden(self):
        self.send('BIND');self.reject(kind='ATTEMPT',data=dict(self.attempt(),family='LambdaForm'))

    def test_wrong_result(self):
        self.send('BIND');self.send('ATTEMPT',self.attempt())
        self.reject(kind='RESULT',data={'id':h('wrong'),'result':'DEFINED'})

    def test_late_attempt(self):
        self.send('BIND');self.send('ATTEMPT',self.attempt())
        self.send('RESULT',{'id':h('attempt'),'result':'DEFINED'});self.send('CUTOFF')
        self.reject(kind='ATTEMPT',data=self.attempt())

    def test_peer_exit(self):
        self.stop()
        with self.assertRaises((ValueError,OSError)):self.channel.step()
        self.assert_terminal()

    def test_ack_uncertainty(self):
        self.command(ack=False)
        with patch('r1_supervisor_channel._send_frame',side_effect=OSError('injected lost ACK')):
            with self.assertRaises(OSError):self.channel.step()
        self.assert_terminal()

    def test_fsync_failure(self):
        self.command(ack=False)
        with patch('r1_receiver_evidence.os.fsync',side_effect=OSError('injected fsync uncertainty')):
            with self.assertRaises(OSError):self.channel.step()
        self.assert_terminal()

    def test_no_early_export(self):
        with self.assertRaises(Rejected):self.channel.export()

    def test_repeated_start(self):
        with self.assertRaises(Rejected):self.channel.start()

    def test_export_substitution(self):
        self.complete();raw,anchor=self.channel.export()
        with self.assertRaises(ValueError):verify_export(KEY,self.context,anchor,raw+b'\n',self.bound,self.members)

    def test_forged_anchor(self):
        self.complete();raw,anchor=self.channel.export();a=decode(anchor);a['payload']['tail']=h('wrong')
        with self.assertRaises(ValueError):verify_export(KEY,self.context,canonical(a),raw,self.bound,self.members)

    def test_anchor_wrong_key(self):
        self.complete();raw,anchor=self.channel.export()
        with self.assertRaises(ValueError):verify_export(b'x'*32,self.context,anchor,raw,self.bound,self.members)

    def test_anchor_wrong_context(self):
        self.complete();raw,anchor=self.channel.export()
        with self.assertRaises(ValueError):verify_export(KEY,h('other'),anchor,raw,self.bound,self.members)

    def test_storage_substitution_before_export(self):
        self.complete();path=self.path/'events.jsonl';path.write_bytes(path.read_bytes()+b'\n')
        with self.assertRaises(ValueError):self.channel.export()
        self.assert_terminal()

    def test_mutable_configuration(self):
        self.expected['pid']=1;self.bindings['source']=h('changed');self.members[h('member')]['definition']=h('changed')
        self.send('BIND')
        row=dict(id=h('attempt'),member=h('member'),family='ordinary',**{k:h(k) for k in ('definition','loader','module','origin')})
        self.send('ATTEMPT',row)


class FramingTests(unittest.TestCase):
    def test_key_length(self):
        with self.assertRaises(Rejected):seal(b'x','supervisor-event',h('context'),{})
    def test_direction_separation(self):
        raw=seal(KEY,'receiver-anchor',h('context'),{})
        with self.assertRaises(Rejected):authenticate(KEY,raw,'supervisor-event',h('context'))
    def test_duplicate_keys(self):
        raw=seal(KEY,'supervisor-event',h('context'),{})
        with self.assertRaises(Rejected):authenticate(KEY,b'{"mac":"x",'+raw[1:],'supervisor-event',h('context'))

if __name__=='__main__':unittest.main(verbosity=2)
