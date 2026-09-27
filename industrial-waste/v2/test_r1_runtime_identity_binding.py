from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("r1_runtime_identity_binding.py")
SPEC = importlib.util.spec_from_file_location("r1_runtime_identity_binding", MODULE_PATH)
assert SPEC and SPEC.loader
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


class RuntimeIdentityBindingTests(unittest.TestCase):
    def test_digest_is_path_and_byte_sensitive(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            a = root / "a.txt"
            b = root / "b.txt"
            a.write_text("alpha", encoding="utf-8")
            b.write_text("beta", encoding="utf-8")
            first = module._digest_files(root, [a, b])
            b.write_text("BETA", encoding="utf-8")
            second = module._digest_files(root, [a, b])
            self.assertNotEqual(first["sha256"], second["sha256"])

    def test_empty_surface_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with self.assertRaisesRegex(ValueError, "surface is empty"):
                module._digest_files(root, [])

    def test_live_binding_closes_only_runtime_identity_bindings(self):
        root = Path(__file__).resolve().parents[2]
        result = module.bind(root, "0" * 40)
        self.assertEqual(result["status"], "QUALIFIED_SEED_FREE_RUNTIME_IDENTITY_BINDING_ONLY")
        self.assertEqual(
            result["bindings_closed"],
            ["effective_rules_archive_and_digest", "compiled_card_snapshot_digest"],
        )
        self.assertEqual(
            result["remaining_runtime_bindings_after_this_gate"],
            [
                "official_r1_corpus_runner",
                "official_r1_artifact_schema_and_completeness_contract",
                "official_complete_metric_projection",
            ],
        )
        self.assertEqual(
            result["official_counters"],
            {
                "allocations_initialized": 0,
                "actions_submitted": 0,
                "comparative_outcomes_exposed": 0,
            },
        )
        self.assertFalse(result["claim_creation_authorized"])
        self.assertFalse(result["initialization_admitted"])
        self.assertFalse(result["execution_authorized"])
        self.assertGreater(result["runtime_identity"]["rules_engine_sources"]["files"], 0)
        self.assertGreater(result["runtime_identity"]["canonical_card_snapshots"]["files"], 0)


if __name__ == "__main__":
    unittest.main()
