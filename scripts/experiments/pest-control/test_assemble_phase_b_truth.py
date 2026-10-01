"""Synthetic author tests; no accepted truth files or raw oracle are read/executed."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

MODULE = Path(__file__).with_name("assemble_phase_b_truth.py")
spec = importlib.util.spec_from_file_location("phase_b_truth_test_subject", MODULE)
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


def row(name, value=True):
    return {"atom": {"physical_lands": [["Forest", 2]], "m2_opening_candidate": False,
                     "early_spell": name}, "development_functional": value}


class PhaseBTruthAssemblyTest(unittest.TestCase):
    def setUp(self):
        self.base = {"schema": "pest-monster-london-m5-atomic-truth-bank-v1", "rows": [row("A"), row("B", False)]}
        self.supp = {"schema": "pest-current-pair-phase-u-m5-truth-supplement-v1", "rows": [row("C")],
                     "source_unbanked_atoms_sha256": m.UNBANKED_SHA256,
                     "source_worklist_sha256": m.WORKLIST_SHA256,
                     "raw_controller_blob": m.RAW_CONTROLLER_BLOB}

    def assemble(self, base=None, supp=None, counts=(2, 1)):
        b = m.canonical(self.base if base is None else base)
        s = m.canonical(self.supp if supp is None else supp)
        with mock.patch.multiple(m, BASE_SHA256=hashlib.sha256(b).hexdigest(),
                                 SUPPLEMENT_SHA256=hashlib.sha256(s).hexdigest(),
                                 BASE_ROWS=counts[0], SUPPLEMENT_ROWS=counts[1]):
            return m.assemble_accepted_truth(b, s)

    def test_valid_exact_disjoint_merge(self):
        value = json.loads(self.assemble())
        self.assertEqual(value["row_count"], 3)
        self.assertEqual([r["atom"]["early_spell"] for r in value["rows"]], ["A", "B", "C"])
        self.assertEqual(value["official_counters_delta"], 0)
        self.assertFalse(value["oracle_invoked"])
        self.assertFalse(value["behavioral_comparison_performed"])

    def test_production_cardinality_with_synthetic_rows(self):
        self.base["rows"] = [row("base-" + str(i)) for i in range(6850)]
        self.supp["rows"] = [row("supp-" + str(i)) for i in range(692)]
        self.assertEqual(json.loads(self.assemble(counts=(6850, 692)))["row_count"], 7542)

    def test_inputs_are_not_mutated(self):
        before = copy.deepcopy((self.base, self.supp)); self.assemble()
        self.assertEqual((self.base, self.supp), before)

    def test_canonical_output_independent_of_input_row_order(self):
        a = json.loads(self.assemble()); self.base["rows"].reverse()
        b = json.loads(self.assemble()); a.pop("source_sha256"); b.pop("source_sha256")
        self.assertEqual(a, b)

    def test_default_production_pins_refuse_synthetic_input(self):
        with self.assertRaisesRegex(ValueError, "digest mismatch"):
            m.assemble_accepted_truth(m.canonical(self.base), m.canonical(self.supp))

    def test_base_byte_drift_rejected(self):
        with self.assertRaises(ValueError):
            m._load(b'{"schema":"x"}', "0" * 64, "x")

    def test_supplement_byte_drift_rejected(self):
        b=m.canonical(self.base)
        with mock.patch.multiple(m, BASE_SHA256=hashlib.sha256(b).hexdigest(), BASE_ROWS=2):
            with self.assertRaisesRegex(ValueError, "digest mismatch"):
                m.assemble_accepted_truth(b, m.canonical(self.supp))

    def test_create_only_output(self):
        with tempfile.TemporaryDirectory() as t:
            p=Path(t)/"combined.json"; m.write_new(p, b"first")
            with self.assertRaises(FileExistsError):m.write_new(p, b"second")
            self.assertEqual(p.read_bytes(), b"first")

    def test_output_symlink_refused(self):
        with tempfile.TemporaryDirectory() as t:
            p=Path(t)/"out"; target=Path(t)/"untouched"; target.write_bytes(b"original");p.symlink_to(target)
            with self.assertRaises(FileExistsError):m.write_new(p, b"replacement")
            self.assertEqual(target.read_bytes(), b"original")

    def test_input_symlink_refused(self):
        with tempfile.TemporaryDirectory() as t:
            p=Path(t)/"in"; target=Path(t)/"truth";target.write_bytes(b"x");p.symlink_to(target)
            with self.assertRaises(ValueError):m._read_regular(p)

    def test_output_parent_alias_refused(self):
        with tempfile.TemporaryDirectory() as t:
            d=Path(t)/"real";d.mkdir();a=Path(t)/"alias";a.symlink_to(d,target_is_directory=True)
            with self.assertRaises(ValueError):m.write_new(a/"new", b"x")

    def test_duplicate_json_keys(self):
        raw=b'{"schema":"x","schema":"x"}'
        with self.assertRaisesRegex(ValueError,"duplicate JSON"):
            m._load(raw,hashlib.sha256(raw).hexdigest(),"x")

    def test_invalid_utf8(self):
        raw=b'\xff'
        with self.assertRaises(UnicodeDecodeError):m._load(raw,hashlib.sha256(raw).hexdigest(),"x")

    def test_nonfinite_json(self):
        raw=b'{"schema":"x","extra":NaN}'
        with self.assertRaises(ValueError):m._load(raw,hashlib.sha256(raw).hexdigest(),"x")


def negative(name, mutate):
    def test(self):
        mutate(self)
        with self.assertRaises((ValueError,TypeError,KeyError)): self.assemble()
    test.__name__="test_"+name
    setattr(PhaseBTruthAssemblyTest,test.__name__,test)

negative("wrong_base_schema",lambda s:s.base.update(schema="other"))
negative("wrong_supplement_schema",lambda s:s.supp.update(schema="other"))
negative("wrong_unbanked_pin",lambda s:s.supp.update(source_unbanked_atoms_sha256="0"*64))
negative("wrong_worklist_pin",lambda s:s.supp.update(source_worklist_sha256="0"*64))
negative("wrong_controller_pin",lambda s:s.supp.update(raw_controller_blob="0"*40))
negative("missing_base_row",lambda s:s.base["rows"].pop())
negative("extra_supplement_row",lambda s:s.supp["rows"].append(row("D")))
negative("same_value_overlap",lambda s:s.supp.update(rows=[row("A")]))
negative("conflicting_overlap",lambda s:s.supp.update(rows=[row("A",False)]))
negative("duplicate_base_atom",lambda s:s.base.update(rows=[row("A"),row("A")]))
negative("unknown_truth_null",lambda s:s.supp["rows"][0].update(development_functional=None))
negative("truth_integer_not_boolean",lambda s:s.supp["rows"][0].update(development_functional=1))
negative("truth_string_not_boolean",lambda s:s.supp["rows"][0].update(development_functional="false"))
negative("m2_integer_not_boolean",lambda s:s.supp["rows"][0]["atom"].update(m2_opening_candidate=0))
negative("unknown_row_field",lambda s:s.supp["rows"][0].update(hidden_order=[]))
negative("unknown_atom_field",lambda s:s.supp["rows"][0]["atom"].update(hidden_order=[]))
negative("empty_spell",lambda s:s.supp["rows"][0]["atom"].update(early_spell=""))
negative("unsorted_lands",lambda s:s.supp["rows"][0]["atom"].update(physical_lands=[["Swamp",1],["Forest",1]]))
negative("repeated_land_name",lambda s:s.supp["rows"][0]["atom"].update(physical_lands=[["Forest",1],["Forest",1]]))
negative("boolean_land_count",lambda s:s.supp["rows"][0]["atom"].update(physical_lands=[["Forest",True]]))
negative("zero_land_count",lambda s:s.supp["rows"][0]["atom"].update(physical_lands=[["Forest",0]]))
negative("too_many_lands",lambda s:s.supp["rows"][0]["atom"].update(physical_lands=[["Forest",7],["Swamp",1]]))
negative("rows_not_list",lambda s:s.base.update(rows={}))
negative("missing_rows",lambda s:s.base.pop("rows"))

if __name__ == "__main__": unittest.main(verbosity=2)
