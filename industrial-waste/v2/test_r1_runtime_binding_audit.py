from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("r1_runtime_binding_audit.py")
SPEC = importlib.util.spec_from_file_location("r1_runtime_binding_audit", MODULE_PATH)
assert SPEC and SPEC.loader
audit_module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit_module)


class RuntimeBindingAuditTests(unittest.TestCase):
    def test_protocol_geometry_rejects_any_sample_expansion(self):
        protocol = {
            "protocol_id": audit_module.PROTOCOL_ID,
            "candidate_families": ["COMPACT_LOOP", "RECURSIVE_EGGS", "LEAN_TRON_HYBRID"],
            "comparator": "IMMUTABLE_SUBMITTED_V1_0",
            "design": {
                "corpus_rows": 64,
                "allocations_per_deck": 128,
                "deck_count": 4,
                "total_allocations": 513,
                "own_turn_cap": 8,
                "submitted_action_cap": 4000,
                "first_draw_schedules_per_row": ["PLAY_SKIP_FIRST_DRAW", "DRAW_TAKE_FIRST_DRAW"],
            },
        }
        corpus = {"labels": ["x"], "rows": []}
        with self.assertRaisesRegex(ValueError, "total_allocations"):
            audit_module.verify_protocol_geometry(protocol, corpus)

    def test_bound_blob_guard_detects_byte_drift(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            raw = next(iter(audit_module.EXPECTED_BLOBS))
            path = root / raw
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("drift", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "bound file drift"):
                audit_module.verify_exact_bound_files(root)

    def test_live_audit_binds_components_but_refuses_initialization(self):
        root = Path(__file__).resolve().parents[2]
        result = audit_module.audit(root, "0" * 40)
        self.assertEqual(result["status"], "BLOCKED_MISSING_RUNTIME_BINDINGS")
        self.assertEqual(result["frozen_geometry"]["total_allocations"], 512)
        self.assertEqual(result["official_counters"]["allocations_initialized"], 0)
        self.assertFalse(result["claim_creation_authorized"])
        self.assertFalse(result["initialization_admitted"])
        self.assertFalse(result["execution_authorized"])
        self.assertEqual(
            result["missing_runtime_bindings"],
            [
                "effective_rules_archive_and_digest",
                "compiled_card_snapshot_digest",
                "official_r1_corpus_runner",
                "official_r1_artifact_schema_and_completeness_contract",
                "official_complete_metric_projection",
            ],
        )


if __name__ == "__main__":
    unittest.main()
