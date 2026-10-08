"""Excluded deterministic local Git fixtures; no official seeds, claims or games."""
from dataclasses import FrozenInstanceError
import json
import os
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

import monster_tron_replication_checkout as m
import monster_tron_replication_seedfree as f
from test_monster_tron_replication_seedfree import fixture, receipt


class CheckoutTests(unittest.TestCase):
    def setUp(self):
        self.area = Path(os.environ['PEST_CHECKOUT_TEST_OUTPUT']).absolute() / self._testMethodName
        self.area.mkdir(parents=True, exist_ok=False)
        self.repo = self.area / 'excluded-checkout'
        self.repo.mkdir()
        self.env = dict(PATH=os.defpath, LC_ALL='C', GIT_CONFIG_NOSYSTEM='1',
                        GIT_CONFIG_GLOBAL=os.devnull, GIT_AUTHOR_NAME='Excluded fixture',
                        GIT_AUTHOR_EMAIL='fixture@example.invalid', GIT_COMMITTER_NAME='Excluded fixture',
                        GIT_COMMITTER_EMAIL='fixture@example.invalid',
                        GIT_AUTHOR_DATE='2000-01-01T00:00:00Z', GIT_COMMITTER_DATE='2000-01-01T00:00:00Z')
        self.git('init', '--quiet', '--template=', '--initial-branch=excluded-fixture')
        (self.repo / 'source.txt').write_bytes(b'EXCLUDED DETERMINISTIC SOURCE\n')
        (self.repo / '.gitignore').write_text('ignored-output\n')
        self.git('add', '.'); self.git('-c', 'commit.gpgsign=false', 'commit', '--quiet', '-m', 'Excluded fixture only')
        self.head = self.git('rev-parse', 'HEAD').strip().decode()
        self.tree = self.git('rev-parse', 'HEAD^{tree}').strip().decode()
        self.raw = f.canonical(fixture())
        self.boundaries = []
        self.entropy = patch.object(os, 'urandom', side_effect=AssertionError('entropy prohibited'))
        self.entropy.start()

    def tearDown(self):
        for boundary in self.boundaries:
            boundary.close()
        self.entropy.stop()

    def git(self, *args):
        return subprocess.run(['git', '-C', str(self.repo), *args], env=self.env,
                              check=True, capture_output=True).stdout

    def attest(self):
        return m.attest_checkout(self.repo, self.head, self.tree)

    def boundary(self, **overrides):
        args = dict(checkout=self.repo, expected_head=self.head, expected_tree=self.tree,
                    evidence_root=self.area / 'evidence', raw=self.raw,
                    expected_digest=f.digest(self.raw), receipt=receipt(self.raw), ack=m.FIXTURE_ACK)
        args.update(overrides)
        b = m.AttestedFixtureBoundary(**args)
        self.boundaries.append(b)
        return b

    def complete(self, b):
        for n in range(1, 13):
            for kind in ('ATTEMPT', 'INITIALIZATION_ENTRY', 'FIXTURE_INITIALIZED'):
                b.transition(kind, n)
            b.transition('INTENT', n, 'EXCLUDED_ACTION_00001')
            b.transition('RESULT', n, 'EXCLUDED_ACTION_00001')
            b.transition('FIXTURE_RECORD', n)

    def test_exact_real_checkout_measurement(self):
        observed = self.attest()
        self.assertEqual((self.head, self.tree, 2), (observed.head, observed.tree, observed.tracked_files))
        self.assertFalse(observed.execution_authorized)
        self.assertEqual(observed, self.attest())
        with self.assertRaises(FrozenInstanceError): observed.head = '0' * 40

    def test_wrong_head(self):
        with self.assertRaises(f.Refused): m.attest_checkout(self.repo, '0' * 40, self.tree)
    def test_wrong_tree(self):
        with self.assertRaises(f.Refused): m.attest_checkout(self.repo, self.head, '0' * 40)
    def test_nonexact_pins(self):
        for pin in ('HEAD', '', None, self.head[:12]):
            with self.subTest(pin=pin), self.assertRaises(f.Refused): m.attest_checkout(self.repo, pin, self.tree)
    def test_dirty_tracked_bytes(self):
        (self.repo / 'source.txt').write_bytes(b'changed')
        with self.assertRaises(f.Refused): self.attest()
    def test_assume_unchanged_does_not_hide_drift(self):
        self.git('update-index', '--assume-unchanged', 'source.txt')
        (self.repo / 'source.txt').write_bytes(b'changed')
        with self.assertRaises(f.Refused): self.attest()
    def test_skip_worktree_does_not_hide_drift(self):
        self.git('update-index', '--skip-worktree', 'source.txt')
        (self.repo / 'source.txt').write_bytes(b'changed')
        with self.assertRaises(f.Refused): self.attest()
    def test_staged_drift_even_with_restored_working_bytes(self):
        p = self.repo / 'source.txt'; old = p.read_bytes(); p.write_bytes(b'changed')
        self.git('add', 'source.txt'); p.write_bytes(old)
        with self.assertRaises(f.Refused): self.attest()
    def test_untracked_file(self):
        (self.repo / 'extra').write_bytes(b'excluded')
        with self.assertRaises(f.Refused): self.attest()
    def test_ignored_file(self):
        (self.repo / 'ignored-output').write_bytes(b'excluded')
        with self.assertRaises(f.Refused): self.attest()
    def test_empty_untracked_directory(self):
        (self.repo / 'extra-directory').mkdir()
        with self.assertRaises(f.Refused): self.attest()
    def test_missing_file(self):
        (self.repo / 'source.txt').unlink()
        with self.assertRaises(f.Refused): self.attest()
    def test_executable_mode_drift(self):
        (self.repo / 'source.txt').chmod(0o755)
        with self.assertRaises(f.Refused): self.attest()
    def test_symlink_substitution(self):
        p = self.repo / 'source.txt'; p.unlink(); p.symlink_to('.gitignore')
        with self.assertRaises(f.Refused): self.attest()
    def test_root_alias(self):
        alias = self.area / 'alias'; alias.symlink_to(self.repo, target_is_directory=True)
        with self.assertRaises(f.Refused): m.attest_checkout(alias, self.head, self.tree)
    def test_git_environment_injection_ignored(self):
        with patch.dict(os.environ, GIT_DIR='/nonexistent', GIT_WORK_TREE='/nonexistent', GIT_INDEX_FILE='/nonexistent'):
            self.assertEqual(self.head, self.attest().head)
    def test_git_failure_refuses_before_evidence(self):
        with patch.object(m, '_git', side_effect=f.Refused('injected read failure')):
            with self.assertRaises(f.Refused): self.boundary()
        self.assertFalse((self.area / 'evidence').exists())
    def test_complete_attested_fixture_inventory(self):
        b = self.boundary(); self.complete(b); b.finish()
        root = self.area / 'evidence'
        measured = json.loads((root / 'fixture-checkout.json').read_text())
        self.assertEqual(self.head, measured['head']); self.assertEqual(self.tree, measured['tree'])
        names = []
        for line in (root / 'fixture-artifacts.sha256').read_text().splitlines():
            digest, name = line.split('  ', 1); names.append(name)
            self.assertEqual(digest, f.digest((root / name).read_bytes()))
        self.assertIn('fixture-checkout.json', names)
        self.assertEqual(set(names), {p.name for p in root.iterdir()} - {'fixture-artifacts.sha256'})
        summary = json.loads((root / 'fixture-summary.json').read_text())
        self.assertEqual([0, 0, 0], [summary[k] for k in ('official_initializations', 'official_actions', 'official_outcomes')])
    def test_source_drift_before_completion_poisoned(self):
        b = self.boundary(); self.complete(b); (self.repo / 'source.txt').write_bytes(b'changed')
        with self.assertRaises(f.Refused): b.finish()
        with self.assertRaises(f.Refused): b.finish()
        self.assertFalse((self.area / 'evidence/fixture-summary.json').exists())
    def test_wrong_ack_before_creation(self):
        for ack in (None, 'EXECUTE', 'LOAD_FROZEN_MONSTER_TRON_R1_FOR_VALIDATION_ONLY'):
            with self.subTest(ack=ack), self.assertRaises(f.Refused): self.boundary(ack=ack)
        self.assertFalse((self.area / 'evidence').exists())
    def test_official_loading_stays_closed(self):
        with self.assertRaises(f.Refused): m.load_official(self.attest(), ack=m.FIXTURE_ACK)
        self.assertTrue(all(x is None for x in f.OFFICIAL_BINDINGS.values()))
    def test_consumed_claim_and_attempt_two(self):
        for k, v in [('claim', 'f165a7d19447d68a7db5984c36f1654179120eff'), ('attempt', 2)]:
            r = receipt(self.raw); r[k] = v
            with self.subTest(field=k), self.assertRaises(f.Refused): self.boundary(receipt=r)
        self.assertFalse((self.area / 'evidence').exists())
    def test_wrong_count_order_cell_and_source(self):
        for mutate in (lambda d: d['slots'].pop(), lambda d: d['slots'].reverse(),
                       lambda d: d['slots'][0].update(pest_seat=1), lambda d: d.update(baseline='0' * 40)):
            d = fixture(); mutate(d); raw = f.canonical(d)
            with self.assertRaises(f.Refused): self.boundary(raw=raw, expected_digest=f.digest(raw), receipt=receipt(raw))
        self.assertFalse((self.area / 'evidence').exists())
    def test_missing_vector_before_creation(self):
        with self.assertRaises(f.Refused): self.boundary(raw=None)
        self.assertFalse((self.area / 'evidence').exists())
    def test_evidence_inside_checkout_refused(self):
        with self.assertRaises(f.Refused): self.boundary(evidence_root=self.repo / 'output')
        self.assertFalse((self.repo / 'output').exists())
    def test_attestation_force_failure_preserves_prefix(self):
        original = f.FixtureEvidenceCoordinator._write
        def fail_measurement(c, name, raw):
            if name == 'fixture-checkout.json': raise OSError('injected attestation write failure')
            return original(c, name, raw)
        with patch.object(f.FixtureEvidenceCoordinator, '_write', fail_measurement):
            with self.assertRaises(OSError): self.boundary()
        self.assertTrue((self.area / 'evidence/fixture-receipt.json').exists())
        with self.assertRaises(FileExistsError): self.boundary()
    def test_incomplete_evidence_refused(self):
        b = self.boundary(); b.transition('ATTEMPT', 1)
        with self.assertRaises(f.Refused): b.finish()
    def test_attestation_record_corruption_refused(self):
        b = self.boundary(); self.complete(b)
        (self.area / 'evidence/fixture-checkout.json').write_bytes(b'changed')
        with self.assertRaises(f.Refused): b.finish()
    def test_result_without_intent_refused(self):
        b = self.boundary()
        for kind in ('ATTEMPT', 'INITIALIZATION_ENTRY', 'FIXTURE_INITIALIZED'): b.transition(kind, 1)
        with self.assertRaises(f.Refused): b.transition('RESULT', 1, 'EXCLUDED_ACTION_00001')


if __name__ == '__main__':
    unittest.main(verbosity=2)
