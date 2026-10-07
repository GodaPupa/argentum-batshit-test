import hashlib
import os
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
from r1_peer_identity import sample_peer, start_ticks, IdentityMismatch

CHILD = "import socket,sys; s=socket.socket(socket.AF_UNIX); s.connect(sys.argv[1]); s.sendall(b'READY'); s.recv(1)"


class PeerTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        listener = socket.socket(socket.AF_UNIX)
        self.addCleanup(listener.close)
        path = str(Path(self.tmp.name) / 'channel')
        listener.bind(path)
        listener.listen(1)
        listener.settimeout(5)
        self.child = subprocess.Popen([sys.executable, '-I', '-c', CHILD, path], stdin=subprocess.DEVNULL)
        self.addCleanup(self.stop)
        self.pidfd = os.pidfd_open(self.child.pid)
        self.addCleanup(os.close, self.pidfd)
        self.peer, _ = listener.accept()
        self.addCleanup(self.peer.close)
        self.peer.settimeout(5)
        self.assertEqual(self.peer.recv(5), b'READY')
        self.expected = {
            'pid': self.child.pid, 'uid': os.getuid(), 'gid': os.getgid(),
            'start_ticks': start_ticks(Path('/proc/'+str(self.child.pid)+'/stat').read_text(), self.child.pid),
            'boot_id': Path('/proc/sys/kernel/random/boot_id').read_text().strip(),
            'executable_sha256': hashlib.sha256(Path(sys.executable).read_bytes()).hexdigest()}

    def stop(self):
        if self.child.poll() is None:
            self.child.kill()
        self.child.wait(timeout=5)

    def reject(self, expected=None, pidfd=None, peer=None):
        with self.assertRaises((IdentityMismatch, OSError)):
            sample_peer(self.pidfd if pidfd is None else pidfd, self.peer if peer is None else peer,
                        self.expected if expected is None else expected)

    def test_postexec_connected_child(self):
        result = sample_peer(self.pidfd, self.peer, self.expected)
        self.assertEqual(result['observed'], self.expected)
        self.assertTrue(result['sample_only'])
        for k in ('channel_authentication_established', 'lifetime_enforcement_established', 'admission_authority'):
            self.assertFalse(result[k])

    def test_wrong_pid(self): self.reject(dict(self.expected, pid=os.getpid()))
    def test_wrong_uid(self): self.reject(dict(self.expected, uid=self.expected['uid']+1))
    def test_wrong_gid(self): self.reject(dict(self.expected, gid=self.expected['gid']+1))
    def test_wrong_start(self): self.reject(dict(self.expected, start_ticks=self.expected['start_ticks']+1))
    def test_wrong_boot(self): self.reject(dict(self.expected, boot_id='00000000-0000-0000-0000-000000000000'))
    def test_wrong_executable(self): self.reject(dict(self.expected, executable_sha256='0'*64))
    def test_bool_pid(self): self.reject(dict(self.expected, pid=True))
    def test_unknown_expected(self): self.reject(dict(self.expected, verified=True))

    def test_wrong_pidfd(self):
        fd = os.pidfd_open(os.getpid())
        try: self.reject(pidfd=fd)
        finally: os.close(fd)

    def test_non_pidfd(self):
        with open('/dev/null') as stream: self.reject(pidfd=stream.fileno())

    def test_exited_child(self):
        self.stop()
        self.reject()

    def test_inherited_socketpair_trap(self):
        a, b = socket.socketpair()
        try:
            # Same pre-fork creation behavior: peer credential is the parent.
            inherited = subprocess.Popen([sys.executable, '-I', '-c',
                "import socket,sys; s=socket.socket(fileno=int(sys.argv[1])); s.sendall(b'R'); s.recv(1)", str(b.fileno())], pass_fds=(b.fileno(),))
            try:
                a.settimeout(5)
                self.assertEqual(a.recv(1), b'R')
                fd = os.pidfd_open(inherited.pid)
                try:
                    expected = dict(self.expected, pid=inherited.pid,
                        start_ticks=start_ticks(Path('/proc/'+str(inherited.pid)+'/stat').read_text(), inherited.pid))
                    self.reject(expected, fd, a)
                finally: os.close(fd)
            finally:
                inherited.kill()
                inherited.wait(timeout=5)
        finally:
            a.close(); b.close()

    def test_unconnected(self):
        with socket.socket(socket.AF_UNIX) as s: self.reject(peer=s)

    def test_datagram(self):
        a,b = socket.socketpair(type=socket.SOCK_DGRAM)
        try: self.reject(peer=a)
        finally: a.close(); b.close()

    def test_proc_uncertainty(self):
        with patch('r1_peer_identity._executable', side_effect=OSError('injected procfs uncertainty')):
            self.reject()

    def test_exit_between_samples(self):
        from r1_peer_identity import _alive
        calls = []
        def alive(fd,pid):
            if calls: self.stop()
            calls.append(1)
            _alive(fd,pid)
        with patch('r1_peer_identity._alive', side_effect=alive): self.reject()
        self.assertEqual(len(calls),2)

    def test_comm_parenthesis_parser(self):
        # suffix fields 3..22: state, 18 fillers, then starttime.
        raw = '123 (a ) spaced)) S ' + '0 '*18 + '999'
        self.assertEqual(start_ticks(raw,123),999)

    def test_malformed_stat(self):
        for raw in ('123 (x)', '124 (x) S ' + '0 '*20, '123 (x) Z ' + '0 '*20):
            with self.subTest(raw=raw), self.assertRaises(IdentityMismatch): start_ticks(raw,123)


if __name__ == '__main__': unittest.main(verbosity=2)
