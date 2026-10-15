#!/usr/bin/env python3
"""Read-only cgroup discovery. Missing evidence is never numeric zero."""
import json
import os
from pathlib import Path, PurePosixPath
import re
import time

POSITIVE_STATUSES = {'OOM_EVENTS_OBSERVED', 'OOM_KILLS_OBSERVED', 'LOCAL_OOM_EVENTS_OBSERVED'}
# Polling alone cannot establish complete process inventory or prevent escapes.
# No environment flag or caller-supplied boolean can grant containment assurance.
CONTAINMENT_BLOCKER = 'BLOCKED_D2: no enforced, reviewed compiler/JVM containment contract; polling has blind intervals'


def diagnostics(reader, directory):
    result = {}
    for name in ('memory.current', 'memory.peak', 'memory.max'):
        item = {'path': directory + '/' + name, 'raw': None, 'value': None}
        try:
            item['raw'] = reader.read(item['path'])
            value = item['raw'].strip()
            if value == 'max' and name == 'memory.max':
                item['value'] = 'max'
            elif re.fullmatch('[0-9]+', value):
                item['value'] = int(value)
            else:
                raise EvidenceError('malformed optional diagnostic')
            item['available'] = True
        except (OSError, ValueError) as exc:
            item.update(available=False, reason=str(exc))
        result[name] = item
    return result


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
                        item['diagnostics'] = diagnostics(reader, directory) if member['version'] == 2 else {}
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
    identity_keys = ['pid', 'version', 'hierarchy', 'group', 'counter_identity', 'boot_id', 'scope', 'qualifying_scope']
    for s in (before, after):
        if any(k not in s for k in identity_keys) or s['version'] not in (1, 2) or not isinstance(s['counter_identity'], list) or len(s['counter_identity']) != 2 or any(type(v) is not int for v in s['counter_identity']):
            return dict(result, reason='missing or malformed controller identity')
    if any(before.get(k) != after.get(k) for k in identity_keys):
        return dict(result, reason='counter identity, scope or membership changed')
    if any(type(x.get('monotonic_ns')) is not int for x in (before, after)) or after['monotonic_ns'] <= before['monotonic_ns']:
        return dict(result, reason='invalid sample ordering')
    a, b = before.get('counters'), after.get('counters')
    required = {'oom', 'oom_kill'} if before.get('version') == 2 else {'oom_kill'}
    if not isinstance(a, dict) or not isinstance(b, dict) or set(a) != required or set(b) != required or any(type(v) is not int or v < 0 for v in list(a.values()) + list(b.values())):
        return dict(result, reason='invalid counter schema or values')
    deltas = {key: b[key] - a[key] for key in a}
    if any(v < 0 for v in deltas.values()):
        return dict(result, reason='counter reset or decrease')
    if any(deltas.values()):
        status = 'OOM_KILLS_OBSERVED' if before['version'] == 1 else ('OOM_EVENTS_OBSERVED' if before.get('qualifying_scope') and after.get('qualifying_scope') else 'LOCAL_OOM_EVENTS_OBSERVED')
        return dict(result, status=status, deltas=deltas, reason='positive cumulative counter change')
    full = bool(before['version'] == 2 and before['scope'] == 'subtree_oom_events_and_kills' and before.get('qualifying_scope') and after.get('qualifying_scope'))
    return dict(result, status='OBSERVED_ZERO' if full else 'INSUFFICIENT_SCOPE', deltas=deltas,
                qualifying=full,
                reason='zero change in observed counters; scope remains explicit')


def assess(samples):
    """Require every interval; a transient missing sample is not erased at the end."""
    if len(samples) < 2:
        return {'status': 'INCOMPLETE', 'deltas': None, 'qualifying': False, 'reason': 'two samples required'}
    intervals = [compare(a, b) for a, b in zip(samples, samples[1:])]
    complete = all(x['status'] != 'INCOMPLETE' for x in intervals)
    result = compare(samples[0], samples[-1])
    # Adjacent positive intervals stand independently. Also bridge unavailable
    # samples, but never bridge an available contradictory identity/reset/schema.
    candidates = [(i, i+1, r) for i, r in enumerate(intervals)]
    available = [(i, s) for i, s in enumerate(samples) if s.get('available')]
    for (i, a), (j, b) in zip(available, available[1:]):
        identity = ('pid', 'version', 'hierarchy', 'group', 'counter_identity', 'boot_id', 'scope', 'qualifying_scope')
        contradicted = any(any(k in s and s[k] != a.get(k) for k in identity) for s in samples[i+1:j])
        if j > i+1 and not contradicted:
            candidates.append((i, j, compare(a, b)))
    positives = [{'from': i, 'to': j, **r} for i, j, r in candidates if r['status'] in POSITIVE_STATUSES]
    result['positive_evidence'] = positives
    result['delta_semantics'] = 'one supporting interval only; never an aggregate over overlaps or gaps'
    result['gaps'] = [i for i, r in enumerate(intervals) if r['status'] == 'INCOMPLETE']
    result['intervals_complete'] = complete
    result['qualifying'] = complete and all(x['qualifying'] for x in intervals) and result['qualifying']
    if positives:
        # Do not aggregate overlapping intervals or invent a full-run delta.
        strongest = next((p for p in positives if p['status'] == 'OOM_EVENTS_OBSERVED'), positives[0])
        result.update(status=strongest['status'], deltas=strongest['deltas'], qualifying=False,
                      delta_interval=[strongest['from'], strongest['to']], reason='trusted positive interval; gaps retained separately')
    elif not complete:
        result.update(status='INCOMPLETE', deltas=None, reason='one or more intervals unverifiable')
    return result


def process_inventory(reader=None):
    """Read-only best-effort inventory. Any race/error is explicit, never complete."""
    reader = reader or Reader()
    rows, errors = [], []
    try:
        pids = sorted(int(p.name) for p in reader.path('/proc').iterdir() if p.name.isdigit())
    except OSError as exc:
        return {'processes': [], 'errors': [str(exc)]}
    for pid in pids:
        prefix = '/proc/' + str(pid)
        try:
            first = reader.read(prefix + '/stat')
            if first.split(' ', 1)[0] != str(pid) or ')' not in first:
                raise EvidenceError('malformed stat PID identity')
            fields = first[first.rfind(')')+2:].split()
            start = int(fields[19]); ppid = int(fields[1]); session = int(fields[3])
            cg = reader.read(prefix + '/cgroup')
            unified = [m for m in memberships(cg) if m['version'] == 2]
            if len(unified) != 1:
                raise EvidenceError('no unique unified process membership')
            exe = reader.identity(prefix + '/exe')
            namespace = reader.identity(prefix + '/ns/cgroup')
            cmdline = reader.read(prefix + '/cmdline')
            second = reader.read(prefix + '/stat')
            endfields = second[second.rfind(')')+2:].split()
            if (int(endfields[19]), int(endfields[1]), int(endfields[3])) != (start, ppid, session) or reader.read(prefix + '/cgroup') != cg or reader.identity(prefix + '/exe') != exe or reader.identity(prefix + '/ns/cgroup') != namespace:
                raise EvidenceError('process identity or membership changed during read')
            rows.append({'pid': pid, 'start': start, 'ppid': ppid, 'session': session,
                         'group': unified[0]['group'], 'exe_identity': exe, 'namespace': namespace,
                         'raw_cgroup': cg, 'raw_stat': first, 'cmdline': cmdline})
        except (OSError, ValueError, IndexError) as exc:
            errors.append({'pid': pid, 'reason': str(exc)})
    return {'processes': rows, 'errors': errors}


class ProcessScope:
    """Audit observed descendants; never certify complete containment from polling."""
    def __init__(self, controller, launch_identity, java_identity, namespace):
        self.controller = controller
        self.launch = tuple(launch_identity)  # PID and kernel start ticks, not name
        self.java = java_identity             # stat identity of pinned JAVA_HOME/bin/java
        self.namespace = namespace
        self.known = {}
        self.issues = []
        self.jvm_seen = False

    def observe(self, inventory):
        rows = inventory['processes']
        issues = list(inventory.get('errors', []))
        indexed = {}
        for r in rows:
            if r['pid'] in indexed:
                issues.append('duplicate/ambiguous PID')
            indexed[r['pid']] = r
        invalid = set()
        for pid, r in indexed.items():
            old = self.known.get(pid)
            if old and old['start'] != r['start']:
                invalid.add(pid)
                issues.append('PID reuse: ancestry binding rejected: '+str(pid))
            parent = indexed.get(r['ppid'])
            if parent and parent['start'] > r['start']:
                invalid.add(pid)
                issues.append('parent start-tick regression: '+str(pid))
        relevant = {pid for pid, r in indexed.items() if pid not in invalid and ((pid, r['start']) == self.launch or pid in self.known)}
        # Require observed parent identity; process names alone never bind a JVM.
        changed = True
        while changed:
            changed = False
            for pid, r in indexed.items():
                if pid not in relevant and pid not in invalid and r['ppid'] in relevant:
                    parent = indexed[r['ppid']]
                    if parent['start'] <= r['start']:
                        relevant.add(pid); changed = True
        if self.launch[0] not in relevant and not self.known:
            issues.append('launch identity not observed')
        for pid in self.known.keys() - indexed.keys():
            issues.append('tracked process vanished without authoritative exit identity: '+str(pid))
        for pid, r in indexed.items():
            if pid not in relevant:
                if r['exe_identity'] == self.java:
                    issues.append('unbound JVM: '+str(pid))
                continue
            old = self.known.get(pid)
            if old and (old['start'] != r['start'] or old['group'] != r['group'] or old['namespace'] != r['namespace']):
                issues.append('PID reuse, membership migration or namespace change: '+str(pid))
            if r['namespace'] != self.namespace:
                issues.append('incomparable cgroup namespace: '+str(pid))
            if not absolute(r['group']).is_relative_to(absolute(self.controller['group'])):
                issues.append('out-of-subtree process: '+str(pid))
            if r['exe_identity'] == self.java:
                self.jvm_seen = True
            self.known.setdefault(pid, dict(r))
        self.issues.extend(issues)
        return {'processes': rows, 'relevant_pids': sorted(relevant), 'issues': issues,
                'jvm_seen': self.jvm_seen, 'qualifying': False,
                'inventory_complete': False, 'containment': 'BLOCKED',
                'reason': CONTAINMENT_BLOCKER}

    def assessment(self):
        return {'status': 'BLOCKED', 'qualifying': False, 'jvm_seen': self.jvm_seen,
                'issues': self.issues + ([] if self.jvm_seen else ['no bound JVM observed']),
                'reason': CONTAINMENT_BLOCKER}


if __name__ == '__main__':
    result = sample()
    print(json.dumps(result, indent=2))
    raise SystemExit(0 if result['available'] and result['qualifying_scope'] else 2)
