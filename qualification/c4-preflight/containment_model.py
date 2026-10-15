"""Synthetic lifecycle specification ONLY. No kernel operations or PASS authority.

Every input is untrusted fixture data. A successful model transition is not an
attestation; this module cannot execute a worker or authorize qualification.
"""
from copy import deepcopy
import hashlib
import json


class Refusal(ValueError):
    pass


class ContainmentModel:
    def __init__(self, expected_source, expected_manifest, used_run_ids):
        self.source = expected_source
        self.manifest = expected_manifest
        self.used = used_run_ids
        self.state = 'NEW'
        self.identity = None
        self.events = []
        self.baseline = None
        self.last = None
        self.positive = []
        self.gaps = []
        self.jvm_seen = False

    def record(self, kind, payload):
        # Hash chaining protects accidental edits only, not supervisor forgery.
        previous = self.events[-1]['digest'] if self.events else None
        item = {'kind': kind, 'payload': deepcopy(payload), 'previous': previous}
        item['digest'] = hashlib.sha256(json.dumps(item, sort_keys=True).encode()).hexdigest()
        self.events.append(item)

    def require(self, condition, reason):
        if not condition:
            self.gaps.append(reason)
            self.state = 'REFUSED'
            raise Refusal(reason)

    def phase(self, expected):
        self.require(self.state == expected, 'invalid lifecycle transition: '+self.state)

    def create(self, facts):
        self.phase('NEW')
        self.record('creation-input', facts)
        self.require(facts.get('run_id') and facts['run_id'] not in self.used, 'missing/reused run identity')
        self.require(facts.get('source') == self.source and facts.get('manifest') == self.manifest,
                     'source or manifest mismatch')
        self.require(facts.get('version') == 2 and facts.get('group') not in (None, '', '/'), 'root or non-v2 group')
        for key in ('controller_exists', 'delegation_verified', 'mount_identity_verified',
                    'memory_controller', 'hierarchical_events', 'exclusive_new_group',
                    'fresh_output', 'fresh_home', 'fresh_gradle_home', 'fresh_tmp', 'fresh_xdg',
                    'supervisor_outside', 'supervisor_evidence_protected', 'kill_supported',
                    'ancestor_migration_denied', 'lifecycle_channel_exclusive'):
            self.require(facts.get(key) is True, 'unverified prerequisite: '+key)
        self.require(facts.get('populated') == 0 and type(facts.get('populated')) is int, 'prepopulated/unknown group')
        for key in ('boot_id', 'cgroup_identity', 'mount_identity', 'launcher_identity'):
            self.require(bool(facts.get(key)), 'missing identity: '+key)
        self.identity = {k: deepcopy(facts[k]) for k in ('run_id', 'boot_id', 'cgroup_identity', 'mount_identity', 'launcher_identity')}
        self.used.add(facts['run_id'])
        self.counter(facts.get('counters'), facts.get('tick'), self.identity)
        self.require(self.last == {'oom': 0, 'oom_kill': 0}, 'nonzero/nonfresh creation counters')
        self.baseline = deepcopy(self.last)
        self.state = 'CREATED'

    def counter(self, values, tick, identity):
        self.record('counter', {'values': values, 'tick': tick, 'identity': identity})
        self.require(identity == self.identity, 'counter/run/launcher identity changed')
        self.require(type(tick) is int and tick > getattr(self, 'tick', -1), 'invalid counter ordering')
        self.require(isinstance(values, dict) and set(values) == {'oom', 'oom_kill'} and
                     all(type(v) is int and v >= 0 for v in values.values()), 'missing/malformed counters')
        self.require(self.last is None or all(values[k] >= self.last[k] for k in values), 'counter reset')
        if self.last is not None:
            delta = {k: values[k] - self.last[k] for k in values}
            if any(delta.values()):
                self.positive.append({'from_tick': self.tick, 'to_tick': tick, 'delta': delta})
        elif any(values.values()):
            self.positive.append({'creation_nonzero': deepcopy(values)})
        self.last = deepcopy(values)
        self.tick = tick

    def preexec(self, facts):
        self.phase('CREATED'); self.record('preexec', facts)
        self.require(facts.get('identity') == self.identity, 'preexec identity mismatch')
        self.require(type(facts.get('uid')) is int and facts['uid'] > 0 and
                     type(facts.get('gid')) is int and facts['gid'] > 0, 'worker privilege unknown/root')
        self.require(facts.get('groups') == [] and facts.get('capabilities') == [] and
                     facts.get('no_new_privs') == 1, 'privileges/capabilities/NoNewPrivs not sealed')
        self.require(facts.get('joined_before_exec') is True, 'worker not joined pre-exec')
        self.require(facts.get('writable_cgroup_procs') is False and facts.get('ancestor_write') is False,
                     'worker can migrate through cgroup filesystem')
        for key in ('namespace_view_verified', 'no_userns_privilege_recovery', 'no_external_net_route',
                    'no_external_unix_ipc', 'private_daemon_endpoints', 'daemon_protocol_versions_verified',
                    'cold_compile_semantics_preserved'):
            self.require(facts.get(key) is True, 'unverified isolation: '+key)
        self.state = 'RUNNING'

    def audit(self, facts):
        self.phase('RUNNING'); self.record('supplementary-process-audit', facts)
        self.require(facts.get('identity') == self.identity, 'audit identity mismatch')
        self.require(facts.get('namespace_visible') is True, 'hidden/incomparable PID namespace')
        self.require(facts.get('inside') is True and facts.get('start_identity_verified') is True,
                     'process escaped/migrated/reused/unverified')
        if facts.get('pinned_java_inode') is True and facts.get('launch_bound') is True:
            self.jvm_seen = True
        # A short-lived or double-forked process must be covered by kernel policy;
        # this supplementary audit is NOT the containment proof.

    def external_ipc(self, facts):
        self.phase('RUNNING'); self.record('ipc', facts)
        self.require(facts.get('connection_denied') is True,
                     'possible external daemon reuse (JDK identity does not make it safe)')

    def launcher_exit(self, identity):
        self.phase('RUNNING'); self.require(identity == self.identity, 'exit identity mismatch')
        self.record('launcher-exit', identity); self.state = 'DRAINING'

    def drained(self, facts):
        self.phase('DRAINING'); self.record('drain', facts)
        self.require(facts.get('identity') == self.identity, 'drain identity mismatch')
        self.require(facts.get('supervisor_outside') is True and facts.get('kill_supported') is True,
                     'no safe drain mechanism')
        self.require(facts.get('bounded') is True and facts.get('populated') == 0 and
                     type(facts.get('populated')) is int, 'drain timeout or still populated')
        self.counter(facts.get('counters'), facts.get('tick'), facts.get('identity'))
        self.require(self.jvm_seen, 'no bound JVM observed')
        self.require(facts.get('counter_handles_held') is True and facts.get('evidence_sealed_before_remove') is True,
                     'final read/evidence lifetime unproven')
        self.state = 'SEALED'

    def disposition(self):
        return {'model_state': self.state, 'model_positive_oom': deepcopy(self.positive),
                'model_outcome': 'FAIL' if self.positive else ('INCOMPLETE' if self.gaps else 'SYNTHETIC_ONLY'),
                'gaps': list(self.gaps), 'status': 'BLOCKED_D2', 'qualifying': False,
                'reason': 'synthetic state model is not a trusted supervisor or kernel evidence',
                'event_chain': deepcopy(self.events)}
