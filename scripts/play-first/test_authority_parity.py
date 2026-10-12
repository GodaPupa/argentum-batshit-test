"""Excluded read-only source-contract regression; never authorizes a game."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
ADAPTER = ROOT / 'gym/src/test/kotlin/com/wingedsheep/gym/PlayFirstIsolatedPestMonsterTronTest.kt'
RECEIVER = ROOT / 'scripts/play-first/strict_receiving.py'
CONSTRUCTION_ONLY = {6081552107, 6084057209, 6084643413, 6086416012}


class AuthorizationDenylistParityTests(unittest.TestCase):
    def test_non_gameplay_comment_denylists_match_exactly(self):
        kotlin = ADAPTER.read_text(encoding='utf-8')
        python = RECEIVER.read_text(encoding='utf-8')
        kotlin_match = re.search(r'authorizationComment\s*!in\s*setOf\s*\(([^)]*)\)', kotlin, re.S)
        python_match = re.search(r"case\['authorizationComment'\]\s+not in\s*\(([^)]*)\)", python, re.S)
        self.assertIsNotNone(kotlin_match, 'Kotlin preflight denylist is missing')
        self.assertIsNotNone(python_match, 'Python receiving denylist is missing')
        kotlin_ids = {int(x) for x in re.findall(r'\b[0-9]+(?=L?\b)', kotlin_match.group(1))}
        python_ids = {int(x) for x in re.findall(r'\b[0-9]+\b', python_match.group(1))}
        self.assertEqual(kotlin_ids, CONSTRUCTION_ONLY, 'Kotlin adapter may accept a construction-only comment')
        self.assertEqual(python_ids, CONSTRUCTION_ONLY, 'Receiver must reject the same construction-only comments')
        self.assertEqual(kotlin_ids, python_ids)


    def test_trace_source_parent_matches_next_successor_parent(self):
        # Applying these local changes as ONE child of frozen V2 requires the
        # emitted sourceParent to identify the actual V2 parent commit.
        kotlin = ADAPTER.read_text(encoding='utf-8')
        self.assertIn('put("sourceParent", "9a4230e0d2acbc38b1e8a7d3e6c3cda5dd41e459")', kotlin)


if __name__ == '__main__':
    unittest.main()
