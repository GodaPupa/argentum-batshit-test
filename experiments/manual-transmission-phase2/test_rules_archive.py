"""Integrity regressions for the seed-free rules archive boundary."""
from datetime import date
import hashlib
import unittest

from verify_rules_archive import verify


class RulesArchiveIntegrityTest(unittest.TestCase):
    def setUp(self):
        self.raw = b'Magic: The Gathering Comprehensive Rules\n\nThese rules are effective as of September 25, 2026.\n'
        self.manifest = {
            'ruleset_id': 'synthetic-regression-fixture',
            'effective_date': '2026-09-25',
            'effective_header': 'These rules are effective as of September 25, 2026.',
            'byte_length': len(self.raw),
            'sha256': hashlib.sha256(self.raw).hexdigest(),
            'source_url': 'https://media.wizards.com/synthetic-regression-fixture',
            'engine_semantics_qualified': False,
            'official_gameplay_authorized': False,
        }

    def test_exact_source_preserves_execution_block(self):
        result = verify(self.raw, self.manifest, date(2026, 9, 25))
        self.assertFalse(result['execution_allowed'])
        self.assertFalse(result['engine_semantics_qualified'])

    def test_cannot_apply_september_25_rules_to_september_24_admission(self):
        with self.assertRaisesRegex(ValueError, 'future-effective'):
            verify(self.raw, self.manifest, date(2026, 9, 24))

    def test_same_length_tampering_rejected(self):
        raw = self.raw.replace(b'Magic', b'MAGIC')
        with self.assertRaisesRegex(ValueError, 'digest mismatch'):
            verify(raw, self.manifest, date(2026, 9, 25))

    def test_truncated_download_rejected(self):
        with self.assertRaisesRegex(ValueError, 'length mismatch'):
            verify(self.raw[:-1], self.manifest, date(2026, 9, 25))

    def test_header_must_match_manifest_even_with_matching_digest(self):
        manifest = dict(self.manifest, effective_header='These rules are effective as of August 7, 2026.')
        with self.assertRaisesRegex(ValueError, 'header mismatch'):
            verify(self.raw, manifest, date(2026, 9, 25))

    def test_backdating_manifest_cannot_admit_future_rules(self):
        manifest = dict(self.manifest, effective_date='2026-08-07')
        with self.assertRaisesRegex(ValueError, 'header mismatch'):
            verify(self.raw, manifest, date(2026, 9, 24))

    def test_source_manifest_cannot_authorize_gameplay(self):
        manifest = dict(self.manifest, official_gameplay_authorized=True)
        with self.assertRaisesRegex(ValueError, 'Source-only'):
            verify(self.raw, manifest, date(2026, 9, 25))


if __name__ == '__main__':
    unittest.main()
