"""Linux sampled peer/pidfd evidence, NOT channel authentication or admission.

Trusted supervisor must retain a pidfd from spawning the intended child, arrange
post-exec connect, and supply independently established expected identity. A
socket peer credential identifies connect-time credentials, NOT its current holder.
"""
import hashlib
import os
from pathlib import Path
import re
import select
import socket
import stat
import struct


class IdentityMismatch(ValueError):
    pass


def require(value, message):
    if not value:
        raise IdentityMismatch(message)


def start_ticks(raw, pid):
    """Parse kernel proc stat while allowing spaces and ')' in comm."""
    require(type(raw) is str and raw.startswith(str(pid) + ' ('), 'stat pid')
    end = raw.rfind(')')
    require(end >= 0, 'stat comm')
    fields = raw[end + 1:].split()
    require(len(fields) >= 20 and fields[0] not in ('Z', 'X', 'x'), 'stat dead/malformed')
    require(fields[19].isdigit(), 'start ticks')
    return int(fields[19])


def _alive(fd, pid):
    text = Path('/proc/self/fdinfo/' + str(fd)).read_text()
    lines = [line.split(':', 1)[1].strip() for line in text.splitlines() if line.startswith('Pid:')]
    require(lines == [str(pid)], 'pidfd target')
    poller = select.poll()
    poller.register(fd, select.POLLIN | select.POLLHUP | select.POLLERR)
    require(not poller.poll(0), 'pidfd exited/uncertain')


def _executable(pid):
    # Deliberately follow this kernel procfs magic link. Caller-supplied paths are
    # never opened; trusted genuine procfs and namespace stability are assumptions.
    with open('/proc/' + str(pid) + '/exe', 'rb', buffering=0) as stream:
        before = os.fstat(stream.fileno())
        require(stat.S_ISREG(before.st_mode) and before.st_size <= 256 * 1024 * 1024, 'executable type/size')
        hashed = hashlib.sha256()
        remaining = before.st_size
        while remaining:
            block = stream.read(min(1024 * 1024, remaining))
            require(bool(block), 'executable shortened')
            hashed.update(block)
            remaining -= len(block)
        require(stream.read(1) == b'', 'executable grew')
        after = os.fstat(stream.fileno())
        attrs = ('st_dev', 'st_ino', 'st_size', 'st_mtime_ns', 'st_ctime_ns')
        require(all(getattr(before, k) == getattr(after, k) for k in attrs), 'executable changed')
        current = os.stat('/proc/' + str(pid) + '/exe')
        require(all(getattr(after, k) == getattr(current, k) for k in attrs), 'executable replaced')
        return hashed.hexdigest(), [after.st_dev, after.st_ino, after.st_size]


def sample_peer(pidfd, peer, expected):
    """Compare a two-sample identity against trusted expectations; never authorize.

    expected fields: pid, uid, gid, start_ticks, boot_id, executable_sha256.
    Requires Linux genuine procfs, trusted supervisor and stable namespaces.
    Not atomic against exec/credential changes; not a descriptor-leak defense.
    """
    keys = {'pid', 'uid', 'gid', 'start_ticks', 'boot_id', 'executable_sha256'}
    require(type(expected) is dict and set(expected) == keys, 'expected fields')
    expected = dict(expected)
    for key in ('pid', 'uid', 'gid', 'start_ticks'):
        require(type(expected[key]) is int and expected[key] >= (1 if key == 'pid' else 0), 'expected integer')
    require(type(expected['boot_id']) is str and re.fullmatch(r'[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}', expected['boot_id']), 'boot format')
    require(type(expected['executable_sha256']) is str and re.fullmatch(r'[0-9a-f]{64}', expected['executable_sha256']), 'executable digest')
    require(type(pidfd) is int and pidfd >= 0, 'pidfd type')
    # Duplicates protect against accidental caller close/reuse after entry; caller
    # synchronization during dup itself remains a trusted-supervisor obligation.
    owned_fd = os.dup(pidfd)
    try:
        with peer.dup() as owned_peer:
            require(owned_peer.family == socket.AF_UNIX and
                    owned_peer.getsockopt(socket.SOL_SOCKET, socket.SO_TYPE) == socket.SOCK_STREAM,
                    'Unix stream required')
            owned_peer.getpeername()  # unconnected/listening descriptors fail
            credentials = struct.unpack('3i', owned_peer.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED, 12))
            require(credentials == (expected['pid'], expected['uid'], expected['gid']), 'connect-time peer mismatch')
            pid = expected['pid']
            _alive(owned_fd, pid)
            boot = Path('/proc/sys/kernel/random/boot_id').read_text().strip()
            first = start_ticks(Path('/proc/' + str(pid) + '/stat').read_text(), pid)
            executable, inode = _executable(pid)
            second = start_ticks(Path('/proc/' + str(pid) + '/stat').read_text(), pid)
            require(first == second == expected['start_ticks'], 'start/reuse mismatch')
            require(boot == expected['boot_id'] and executable == expected['executable_sha256'], 'boot/executable mismatch')
            require(Path('/proc/sys/kernel/random/boot_id').read_text().strip() == boot, 'boot changed')
            _alive(owned_fd, pid)
            return {'observed': expected, 'executable_inode': inode, 'sample_only': True,
                    'channel_authentication_established': False, 'lifetime_enforcement_established': False,
                    'admission_authority': False}
    finally:
        os.close(owned_fd)
