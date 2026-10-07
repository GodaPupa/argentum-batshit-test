import copy
import hashlib
import json
import os
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch

from r1_receiver_evidence import ReceiverEvidence, Rejected, canonical, digest


def h(s):
    return hashlib.sha256(s.encode()).hexdigest()


class EvidenceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.members = {h('member'): {k: h(k) for k in ('definition', 'loader', 'module', 'origin')}}
        self.bindings = {k: h(k) for k in ('source', 'tree', 'runtime', 'process', 'channel', 'receiver')}
        self.bindings['policy'] = digest(self.members)
        self.path = Path(self.tmp.name) / 'launch'
        self.r = ReceiverEvidence(self.path, self.bindings, self.members)
        self.addCleanup(self.r.close)
        self.envelope = self.r.challenge()

    def raw(self, kind, data=None, **changes):
        e = dict(self.envelope, kind=kind, data={} if data is None else data)
        e.update(changes)
        return canonical(e)

    def send(self, kind, data=None):
        result = self.r.accept(self.raw(kind, data))
        self.assertFalse(result['admission_authority'])
        self.envelope.update({k: result[k] for k in ('seq', 'previous')})
        return result

    def attempt(self, **changes):
        row = dict(id=h('attempt'), member=h('member'), family='ordinary', **self.members[h('member')])
        row.update(changes)
        return row

    def bound(self):
        self.send('BIND')

    def defined(self):
        self.bound()
        self.send('ATTEMPT', self.attempt())
        self.send('RESULT', {'id': h('attempt'), 'result': 'DEFINED'})

    def rows(self):
        return [json.loads(x) for x in (self.path / 'events.jsonl').read_bytes().splitlines()]

    def reject(self, raw):
        with self.assertRaises((Rejected, OSError)):
            self.r.accept(raw)
        with self.assertRaises(Rejected):
            self.r.accept(self.raw('BIND'))

    def test_positive_chain_single_use(self):
        self.defined()
        self.send('CUTOFF')
        self.assertEqual(self.send('CONSUME_EVIDENCE')['state'], 'CONSUMED')
        with self.assertRaises(Rejected):
            self.r.accept(self.raw('CONSUME_EVIDENCE'))
        previous = '0' * 64
        for i, row in enumerate(self.rows()):
            self.assertEqual((row['seq'], row['previous']), (i, previous))
            previous = digest(row)
        self.assertEqual(len(self.rows()), 6)

    def test_receiver_freshness(self):
        second = ReceiverEvidence(Path(self.tmp.name) / 'second', self.bindings, self.members)
        self.addCleanup(second.close)
        other = second.challenge()
        self.assertNotEqual(other['launch'], self.envelope['launch'])
        self.assertNotEqual(other['challenge'], self.envelope['challenge'])
        self.reject(canonical(dict(other, kind='BIND', data={})))

    def test_stale_challenge(self):
        self.reject(self.raw('BIND', challenge=h('stale')))

    def test_wrong_launch(self):
        self.reject(self.raw('BIND', launch=h('wrong')))

    def test_each_wrong_identity(self):
        for key in self.bindings:
            with self.subTest(key=key):
                r = ReceiverEvidence(Path(self.tmp.name) / key, self.bindings, self.members)
                try:
                    e = r.challenge()
                    e['bindings'][key] = h('wrong')
                    with self.assertRaises(Rejected):
                        r.accept(canonical(dict(e, kind='BIND', data={})))
                finally:
                    r.close()

    def test_replay_event(self):
        raw = self.raw('BIND')
        self.r.accept(raw)
        self.reject(raw)

    def test_reordered_sequence(self):
        self.reject(self.raw('BIND', seq=2))

    def test_bool_sequence(self):
        self.reject(self.raw('BIND', seq=True))

    def test_wrong_previous(self):
        self.reject(self.raw('BIND', previous=h('wrong')))

    def test_unknown_attempt_recorded_before_failure(self):
        self.bound()
        self.reject(self.raw('ATTEMPT', self.attempt(member=h('unknown'))))
        self.assertEqual([r['kind'] for r in self.rows()][-2:], ['ATTEMPT', 'FAILED'])

    def test_hidden_family_refused(self):
        self.bound()
        self.reject(self.raw('ATTEMPT', self.attempt(family='LambdaForm')))

    def test_each_definition_binding(self):
        for key in ('definition', 'loader', 'module', 'origin'):
            with self.subTest(key=key):
                r = ReceiverEvidence(Path(self.tmp.name) / key, self.bindings, self.members)
                try:
                    e = r.challenge()
                    result = r.accept(canonical(dict(e, kind='BIND', data={})))
                    e.update({k: result[k] for k in ('seq', 'previous')})
                    with self.assertRaises(Rejected):
                        r.accept(canonical(dict(e, kind='ATTEMPT', data=self.attempt(**{key: h('wrong')}))))
                finally:
                    r.close()

    def test_duplicate_attempt(self):
        self.defined()
        self.reject(self.raw('ATTEMPT', self.attempt()))

    def test_missing_attempt(self):
        self.bound()
        self.reject(self.raw('RESULT', {'id': h('attempt'), 'result': 'DEFINED'}))

    def test_wrong_result_id(self):
        self.bound()
        self.send('ATTEMPT', self.attempt())
        self.reject(self.raw('RESULT', {'id': h('wrong'), 'result': 'DEFINED'}))

    def test_failed_definition(self):
        self.bound()
        self.send('ATTEMPT', self.attempt())
        self.reject(self.raw('RESULT', {'id': h('attempt'), 'result': 'THREW'}))

    def test_pending_at_cutoff(self):
        self.bound()
        self.send('ATTEMPT', self.attempt())
        self.reject(self.raw('CUTOFF'))

    def test_empty_at_cutoff(self):
        self.bound()
        self.reject(self.raw('CUTOFF'))

    def test_late_attempt(self):
        self.defined()
        self.send('CUTOFF')
        self.reject(self.raw('ATTEMPT', self.attempt(id=h('late'))))

    def test_early_consume(self):
        self.reject(self.raw('CONSUME_EVIDENCE'))

    def test_rollback(self):
        self.bound()
        self.reject(self.raw('BIND'))

    def test_refill(self):
        self.bound()
        self.reject(self.raw('REFILL'))

    def test_unknown_field(self):
        self.reject(self.raw('BIND', extra=True))

    def test_duplicate_json_field(self):
        raw = self.raw('BIND')
        self.reject(b'{"kind":"BIND",' + raw[1:])

    def test_noncanonical_json(self):
        self.reject(b' ' + self.raw('BIND'))

    def test_malformed_json(self):
        self.reject(b'{')

    def test_oversize(self):
        self.reject(b' ' * 16385)

    def test_mutable_inputs_copied(self):
        self.members[h('member')]['definition'] = h('changed')
        self.bindings['source'] = h('changed')
        self.bound()
        row = self.attempt(definition=h('definition'))
        self.send('ATTEMPT', row)
        row['definition'] = h('changed')
        self.send('RESULT', {'id': h('attempt'), 'result': 'DEFINED'})

    def test_restart_refused(self):
        self.r.close()
        with self.assertRaises(FileExistsError):
            ReceiverEvidence(self.path, self.bindings, self.members)

    def test_symlink_parent(self):
        link = Path(self.tmp.name) / 'alias'
        link.symlink_to(self.tmp.name, target_is_directory=True)
        with self.assertRaises(OSError):
            ReceiverEvidence(link / 'new', self.bindings, self.members)

    def test_symlink_leaf(self):
        link = Path(self.tmp.name) / 'alias'
        link.symlink_to(self.path, target_is_directory=True)
        with self.assertRaises(FileExistsError):
            ReceiverEvidence(link, self.bindings, self.members)

    def test_path_escape(self):
        with self.assertRaises(Rejected):
            ReceiverEvidence(str(self.path) + '/../escape', self.bindings, self.members)

    def test_policy_digest(self):
        wrong = dict(self.bindings, policy=h('wrong'))
        with self.assertRaises(Rejected):
            ReceiverEvidence(Path(self.tmp.name) / 'wrong', wrong, self.members)

    def test_short_writes(self):
        real_write = os.write
        with patch('r1_receiver_evidence.os.write', side_effect=lambda fd, b: real_write(fd, b[:7])):
            self.bound()
        self.assertEqual(self.rows()[-1]['kind'], 'BOUND')

    def test_zero_write_latches(self):
        with patch('r1_receiver_evidence.os.write', return_value=0):
            self.reject(self.raw('BIND'))

    def test_fsync_uncertainty_latches(self):
        with patch('r1_receiver_evidence.os.fsync', side_effect=OSError('injected durability failure')):
            self.reject(self.raw('BIND'))
        # A persisted-looking row is not an acknowledged transition; no reopening.
        self.assertEqual(self.rows()[-1]['kind'], 'BOUND')

    def test_concurrent_duplicate_consumption(self):
        self.defined()
        self.send('CUTOFF')
        raw = self.raw('CONSUME_EVIDENCE')
        outcomes = []
        def consume():
            try:
                self.r.accept(raw)
                outcomes.append('consumed')
            except Rejected:
                outcomes.append('rejected')
        threads = [threading.Thread(target=consume) for _ in range(8)]
        for t in threads: t.start()
        for t in threads: t.join()
        self.assertEqual(outcomes.count('consumed'), 1)
        self.assertEqual(outcomes.count('rejected'), 7)


if __name__ == '__main__':
    unittest.main(verbosity=2)
