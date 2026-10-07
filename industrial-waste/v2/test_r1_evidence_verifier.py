import copy
import unittest
from r1_evidence_verifier import verify, encode, sha, InvalidEvidence


def h(s): return sha(s.encode())


class VerifierTests(unittest.TestCase):
    def setUp(self):
        self.members = {h('member'): {k: h(k) for k in ('definition', 'loader', 'module', 'origin')}}
        self.bindings = {k: h(k) for k in ('source', 'tree', 'runtime', 'process', 'channel', 'receiver')}
        self.bindings['policy'] = sha(encode(self.members))
        self.events = [
            ('RESERVED', {'bindings': self.bindings, 'launch': h('launch'), 'challenge': h('challenge')}),
            ('BOUND', {}),
            ('ATTEMPT', dict(id=h('attempt'), member=h('member'), family='ordinary', **self.members[h('member')])),
            ('RESULT', {'id': h('attempt'), 'result': 'DEFINED'}),
            ('CUTOFF', {}), ('CONSUMED_EVIDENCE', {})]
        self.raw, self.anchor = self.pack(self.events)

    def pack(self, events):
        previous, rows = '0' * 64, []
        for i, (kind, data) in enumerate(events):
            raw = encode({'seq': i, 'previous': previous, 'kind': kind, 'data': data})
            rows.append(raw)
            previous = sha(raw)
        transcript = b'\n'.join(rows) + b'\n'
        return transcript, {'launch': h('launch'), 'challenge': h('challenge'),
                            'rows': len(rows), 'tail': previous, 'sha256': sha(transcript)}

    def check(self, raw=None, anchor=None):
        return verify(self.raw if raw is None else raw, self.anchor if anchor is None else anchor,
                      self.bindings, self.members)

    def reject_events(self, events):
        # Even an externally trusted digest cannot make invalid grammar/provenance consistent.
        raw, anchor = self.pack(events)
        with self.assertRaises(InvalidEvidence): self.check(raw, anchor)

    def reject_raw_reanchored(self, raw):
        anchor = dict(self.anchor, sha256=sha(raw))
        with self.assertRaises(InvalidEvidence): self.check(raw, anchor)

    def test_positive(self):
        result = self.check()
        self.assertEqual(result['ordinary_attempts'], 1)
        for key in ('admission_authority', 'anchor_authentication_established', 'vm_provenance_established'):
            self.assertFalse(result[key])

    def test_substitution(self):
        with self.assertRaises(InvalidEvidence): self.check(self.raw.replace(b'DEFINED', b'ALTERED'))

    def test_truncation(self):
        with self.assertRaises(InvalidEvidence): self.check(self.raw[:-20])

    def test_append(self):
        with self.assertRaises(InvalidEvidence): self.check(self.raw + self.raw.splitlines()[0] + b'\n')

    def test_tail_anchor(self):
        with self.assertRaises(InvalidEvidence): self.check(anchor=dict(self.anchor, tail=h('wrong')))

    def test_stale_challenge(self):
        with self.assertRaises(InvalidEvidence): self.check(anchor=dict(self.anchor, challenge=h('stale')))

    def test_wrong_launch(self):
        with self.assertRaises(InvalidEvidence): self.check(anchor=dict(self.anchor, launch=h('wrong')))

    def test_wrong_each_binding(self):
        for key in self.bindings:
            bindings = dict(self.bindings, **{key: h('wrong')})
            with self.subTest(key=key), self.assertRaises(InvalidEvidence):
                verify(self.raw, self.anchor, bindings, self.members)

    def test_missing_anchor(self):
        with self.assertRaises(InvalidEvidence): self.check(anchor={})

    def test_bool_count(self):
        with self.assertRaises(InvalidEvidence): self.check(anchor=dict(self.anchor, rows=True))

    def test_wrong_count(self):
        with self.assertRaises(InvalidEvidence): self.check(anchor=dict(self.anchor, rows=7))

    def test_unknown_anchor_field(self):
        with self.assertRaises(InvalidEvidence): self.check(anchor=dict(self.anchor, verified=True))

    def test_missing_attempt(self):
        self.reject_events(self.events[:2] + self.events[3:])

    def test_missing_result(self):
        self.reject_events(self.events[:3] + self.events[4:])

    def test_duplicate_attempt(self):
        self.reject_events(self.events[:4] + self.events[2:])

    def test_reordered(self):
        self.reject_events(self.events[:2] + [self.events[3], self.events[2]] + self.events[4:])

    def test_late_attempt(self):
        self.reject_events(self.events + [self.events[2]])

    def test_terminal_failure(self):
        self.reject_events(self.events[:4] + [('FAILED', {'reason': 'Rejected'})] + self.events[4:])

    def test_pending_cutoff(self):
        self.reject_events(self.events[:3] + [('CUTOFF', {})] + self.events[3:])

    def test_hidden(self):
        self.events[2][1]['family'] = 'LambdaForm'
        self.reject_events(self.events)

    def test_unknown_member(self):
        self.events[2][1]['member'] = h('unknown')
        self.reject_events(self.events)

    def test_wrong_definition_bindings(self):
        for key in ('definition', 'loader', 'module', 'origin'):
            events = copy.deepcopy(self.events)
            events[2][1][key] = h('wrong')
            with self.subTest(key=key): self.reject_events(events)

    def test_failed_result(self):
        self.events[3][1]['result'] = 'THREW'
        self.reject_events(self.events)

    def test_missing_consumption(self):
        self.reject_events(self.events[:-1])

    def test_rollback(self):
        self.reject_events(self.events[:4] + [('BOUND', {})] + self.events[4:])

    def test_refill(self):
        self.reject_events(self.events[:4] + [('REFILL', {})] + self.events[4:])

    def test_chain_previous(self):
        self.reject_raw_reanchored(self.raw.replace(b'"previous":"' + b'0'*64, b'"previous":"' + b'1'*64, 1))

    def test_bool_sequence(self):
        self.reject_raw_reanchored(self.raw.replace(b'"seq":1', b'"seq":true', 1))

    def test_duplicate_json_key(self):
        self.reject_raw_reanchored(b'{"seq":0,' + self.raw[1:])

    def test_noncanonical(self):
        self.reject_raw_reanchored(b' ' + self.raw)

    def test_no_newline(self):
        self.reject_raw_reanchored(self.raw[:-1])

    def test_unknown_row_field(self):
        self.reject_raw_reanchored(b'{"alien":1,' + self.raw[1:])

    def test_size_limit(self):
        with self.assertRaises(InvalidEvidence): self.check(b' ' * (8*1024*1024 + 1))

    def test_malformed(self):
        self.reject_raw_reanchored(b'{\n' * 6)

    def test_input_policy_changed(self):
        self.members[h('member')]['definition'] = h('altered')
        with self.assertRaises(InvalidEvidence): self.check()


if __name__ == '__main__': unittest.main(verbosity=2)
