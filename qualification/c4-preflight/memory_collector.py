#!/usr/bin/env python3
"""Read-only cgroup discovery. Missing evidence is never numeric zero."""
import json
import os
from pathlib import Path, PurePosixPath
import re
import time


class EvidenceError(ValueError):
    pass


def unescape(value):
    return re.sub(r'\\([0-7]{3})', lambda m: chr(int(m[1], 8)), value)


def absolute(value):
    p = PurePosixPath(value)
    if not p.is_absolute() or '..' in p.parts or '\x00' in value or value.endswith(' (deleted)'):
        raise EvidenceError('unsafe or deleted cgroup path')
    return p


def memberships(text):
    rows = []
    for line in text.splitlines():
        fields = line.split(':', 2)
        if len(fields) != 3 or not fields[0].isdigit():
            raise EvidenceError('malformed /proc/self/cgroup')
        hierarchy, controllers, group = fields
        absolute(group)
        if hierarchy == '0' and controllers == '':
            rows.append({'version': 2, 'hierarchy': hierarchy, 'group': group})
        elif 'memory' in controllers.split(','):
            rows.append({'version': 1, 'hierarchy': hierarchy, 'group': group})
    if not rows:
        raise EvidenceError('no memory-capable cgroup membership')
    if len({(r['version'], r['hierarchy']) for r in rows}) != len(rows):
        raise EvidenceError('duplicate cgroup membership')
    return rows


def mounts(text):
    rows = []
    for line in text.splitlines():
        fields = line.split()
        if '-' not in fields:
            raise EvidenceError('malformed mountinfo separator')
        sep = fields.index('-')
        if sep < 6 or len(fields) != sep + 4:
            raise EvidenceError('malformed mountinfo fields')
        fs = fields[sep + 1]
        options = set(fields[5].split(',')) | set(fields[sep + 3].split(','))
        if fs == 'cgroup2' or (fs == 'cgroup' and 'memory' in options):
            root, point = unescape(fields[3]), unescape(fields[4])
            absolute(root); absolute(point)
            rows.append({'version': 2 if fs == 'cgroup2' else 1, 'mount_id': fields[0],
                         'device': fields[2], 'root': root, 'mountpoint': point,
                         'options': sorted(options)})
    return rows


def counters(text, required):
    result = {}
    for line in text.splitlines():
        pair = line.split()
        if len(pair) != 2 or not re.fullmatch(r'[0-9]+', pair[1]) or pair[0] in result:
            raise EvidenceError('malformed, negative or duplicate counter')
        result[pair[0]] = int(pair[1])
    if not set(required) <= result.keys():
        raise EvidenceError('required cumulative OOM counters missing')
    return result


class Reader:
    """root permits synthetic filesystems; production defaults to real /."""
    def __init__(self, root='/', pid=None):
        self.root = Path(root)
        self.pid = os.getpid() if pid is None else pid

    def path(self, name):
        return self.root / str(absolute(name)).lstrip('/')

    def read(self, name):
        return self.path(name).read_text()

    def identity(self, name):
        stat = self.path(name).stat()
        return [stat.st_dev, stat.st_ino]


def sample(reader=None):
    reader = reader or Reader()
    receipt = {'schema': 1, 'available': False, 'counters': None,
               'monotonic_ns': time.monotonic_ns(), 'pid': reader.pid, 'discovery': {}}
    discovery = receipt['discovery']
    try:
        cg = discovery['proc_self_cgroup'] = reader.read('/proc/self/cgroup')
        mi = discovery['proc_self_mountinfo'] = reader.read('/proc/self/mountinfo')
        boot = reader.read('/proc/sys/kernel/random/boot_id').strip()
        if not re.fullmatch(r'[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}', boot):
            raise EvidenceError('invalid boot identity')
        found, rejected = {}, []
        for member in memberships(cg):
            for mount in mounts(mi):
                if member['version'] != mount['version']:
                    continue
                group, root = absolute(member['group']), absolute(mount['root'])
                candidates = []
                if group.is_relative_to(root):
                    candidates.append(('mount-root-relative', group.relative_to(root)))
                # A cgroup namespace may expose membership relative to its own root.
                # This candidate is accepted only with direct PID membership evidence.
                if root != PurePosixPath('/'):
                    candidates.append(('namespace-relative', group.relative_to('/')))
                for mapping, relative in candidates:
                    directory = str(absolute(mount['mountpoint']) / relative)
                    try:
                        procs = reader.read(directory + '/cgroup.procs')
                        ids = procs.split()
                        if any(not re.fullmatch('[0-9]+', n) for n in ids) or str(reader.pid) not in ids:
                            raise EvidenceError('collector PID not in candidate cgroup')
                        filename = 'memory.events' if member['version'] == 2 else 'memory.oom_control'
                        path = directory + '/' + filename
                        raw = reader.read(path)
                        required = ['oom', 'oom_kill'] if member['version'] == 2 else ['oom_kill']
                        values = counters(raw, required)
                        identity = reader.identity(path)
                        hierarchical = member['version'] == 2 and 'memory_localevents' not in mount['options']
                        item = {'version': member['version'], 'hierarchy': member['hierarchy'],
                                'group': member['group'], 'mount': mount, 'mapping': mapping,
                                'directory': directory, 'counter_path': path, 'counter_identity': identity,
                                'raw_counters': raw, 'counters': {k: values[k] for k in required},
                                'cgroup_procs': procs, 'boot_id': boot,
                                'scope': 'subtree_oom_events_and_kills' if hierarchical else 'local_or_kills_only',
                                'qualifying_scope': hierarchical}
                        # Duplicate aliases of one kernel counter are not distinct sources.
                        found.setdefault(tuple(identity), item)
                    except (OSError, ValueError) as exc:
                        rejected.append({'directory': directory, 'mapping': mapping, 'reason': str(exc)})
        discovery['rejected'] = rejected
        # On hybrid hosts a valid legacy memory controller takes precedence over a
        # unified hierarchy without a memory controller; unavailable rows stay rejected.
        if len(found) != 1:
            raise EvidenceError('no unique verified memory counter: ' + str(len(found)))
        receipt.update(next(iter(found.values())))
        # Fail on membership or mount changes during discovery (do not follow migration).
        if reader.read('/proc/self/cgroup') != cg or reader.read('/proc/self/mountinfo') != mi:
            raise EvidenceError('membership or mounts changed during discovery')
        receipt['available'] = True
    except (OSError, ValueError) as exc:
        receipt.update(available=False, counters=None, reason=str(exc))
    return receipt


def compare(before, after):
    result = {'status': 'INCOMPLETE', 'deltas': None, 'qualifying': False}
    if not before.get('available') or not after.get('available'):
        return dict(result, reason='counter evidence unavailable')
    identity_keys = ['pid', 'version', 'hierarchy', 'group', 'counter_identity', 'boot_id', 'scope']
    if any(before.get(k) != after.get(k) for k in identity_keys):
        return dict(result, reason='counter identity, scope or membership changed')
    if after['monotonic_ns'] <= before['monotonic_ns']:
        return dict(result, reason='invalid sample ordering')
    a, b = before['counters'], after['counters']
    if set(a) != set(b) or any(type(v) is not int or v < 0 for v in list(a.values()) + list(b.values())):
        return dict(result, reason='invalid counter schema or values')
    deltas = {key: b[key] - a[key] for key in a}
    if any(v < 0 for v in deltas.values()):
        return dict(result, reason='counter reset or decrease')
    if any(deltas.values()):
        return dict(result, status='OOM_EVENTS_OBSERVED', deltas=deltas, reason='positive cumulative counter change')
    return dict(result, status='OBSERVED_ZERO', deltas=deltas,
                qualifying=bool(before.get('qualifying_scope') and after.get('qualifying_scope')),
                reason='zero change in observed counters; scope remains explicit')


def assess(samples):
    """Require every interval; a transient missing sample is not erased at the end."""
    if len(samples) < 2:
        return {'status': 'INCOMPLETE', 'deltas': None, 'qualifying': False, 'reason': 'two samples required'}
    intervals = [compare(a, b) for a, b in zip(samples, samples[1:])]
    complete = all(x['status'] != 'INCOMPLETE' for x in intervals)
    result = compare(samples[0], samples[-1])
    result['intervals_complete'] = complete
    result['qualifying'] = complete and all(x['qualifying'] for x in intervals) and result['qualifying']
    if not complete:
        result.update(status='INCOMPLETE', deltas=None, reason='one or more intervals unverifiable')
    elif any(x['status'] == 'OOM_EVENTS_OBSERVED' for x in intervals):
        result.update(status='OOM_EVENTS_OBSERVED', qualifying=False)
    return result


if __name__ == '__main__':
    result = sample()
    print(json.dumps(result, indent=2))
    raise SystemExit(0 if result['available'] and result['qualifying_scope'] else 2)
