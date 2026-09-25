import importlib.util
import tempfile
import unittest
from pathlib import Path
import hashlib

HELPER = Path(__file__).resolve().parents[2] / "scripts/verify-pest-spy-batch-c-sources.py"
SPEC = importlib.util.spec_from_file_location("pest_spy_c_sources", HELPER)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class SpyBatchCSourceTest(unittest.TestCase):
    def fixture(self, root):
        path = root / MODULE.CARD_PATH
        path.parent.mkdir(parents=True)
        path.write_bytes(b"Fixed non-game source fixture\n")
        return {"schema": "pest-spy-support-batch-c-source-v1",
                "accepted_prior_source": MODULE.PRIOR_SOURCE,
                "target_card": {"name": "Nyxborn Hydra", "source_path": MODULE.CARD_PATH,
                                "prior_definition_sha256": MODULE.PRIOR_DEFINITION,
                                "compiled_definition_sha256": hashlib.sha256(path.read_bytes()).hexdigest()}}

    def test_distinct_prior_and_compiled_identities_are_verified(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.fixture(root)
            result = MODULE.verify(manifest, root)
            self.assertEqual(result["compiled_definition_sha256"], manifest["target_card"]["compiled_definition_sha256"])

    def test_tampered_compiled_bytes_fail_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.fixture(root)
            (root / MODULE.CARD_PATH).write_bytes(b"Changed bytes\n")
            with self.assertRaisesRegex(ValueError, "Compiled definition digest mismatch"):
                MODULE.verify(manifest, root)

    def test_ambiguous_prior_field_or_substituted_path_fails_closed(self):
        for mutation in (lambda card: card.update(source_definition_sha256=MODULE.PRIOR_DEFINITION),
                         lambda card: card.update(source_path="../other.kt"),
                         lambda card: card.update(prior_definition_sha256="0" * 64)):
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                manifest = self.fixture(root)
                mutation(manifest["target_card"])
                with self.assertRaises(ValueError):
                    MODULE.verify(manifest, root)


if __name__ == "__main__":
    unittest.main()
