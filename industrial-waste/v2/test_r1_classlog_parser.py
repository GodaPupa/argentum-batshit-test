#!/usr/bin/env python3
"""Synthetic collector regression only: never executes capture.main or any worker."""
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest

SOURCE=Path(__file__).with_name("capture_supplemental_hermetic_r1.py")
SPEC=importlib.util.spec_from_file_location("industrial_capture_parser_under_test",SOURCE)
CAPTURE=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CAPTURE)

class IndustrialClasslogParserTest(unittest.TestCase):
    def parse(self,text):
        with tempfile.TemporaryDirectory(prefix="industrial-parser-synthetic-") as directory:
            path=Path(directory)/"classload.log"
            path.write_bytes(text if isinstance(text,bytes) else text.encode("utf-8"))
            return CAPTURE.parse_classlog(path)

    def test_01_standard_hotspot_record(self):
        rows,digest=self.parse("[info][class,load] java.lang.Object source: shared objects file\n")
        self.assertEqual(rows,[{"name":"java.lang.Object","normalized_name":"java.lang.Object","source":"shared objects file","hidden":False,"generated_or_hidden":False}])
        self.assertEqual(digest,hashlib.sha256(CAPTURE.canonical([("java.lang.Object","shared objects file")])).hexdigest())

    def test_02_hidden_name_normalization_unchanged(self):
        rows,_=self.parse("[0.5s][info][class,load] sample.Factory$Lambda/0x000abc source: __JVM_LookupDefineClass__\n")
        self.assertEqual(rows[0]["normalized_name"],"sample.Factory$Lambda/0x<HIDDEN>")
        self.assertTrue(rows[0]["hidden"])
        self.assertTrue(rows[0]["generated_or_hidden"])

    def test_03_duplicate_rows_keep_multiplicity(self):
        line="[info][class,load] sample.Generated source: __ClassDefiner__\n"
        rows,digest=self.parse(line*2)
        one,one_digest=self.parse(line)
        self.assertEqual(rows,one*2)
        self.assertNotEqual(digest,one_digest)
        self.assertFalse(CAPTURE.generated_hidden_repeat_equivalence(rows,one)["equal"])

    def test_04_non_classload_noise_is_ignored(self):
        rows,_=self.parse("[info][gc] unrelated\n[info][class,load] sample.Card source: file:/sample/cards.jar\n")
        self.assertEqual(len(rows),1)
        self.assertEqual(rows[0]["source"],"file:/sample/cards.jar")

    def test_05_empty_log_rejected(self):
        with self.assertRaisesRegex(RuntimeError,"Empty or incomplete"):
            self.parse("")

    def test_06_noise_only_log_rejected(self):
        with self.assertRaisesRegex(RuntimeError,"Empty or incomplete"):
            self.parse("[info][gc] unrelated\n")

    def test_07_malformed_tagged_record_cannot_be_silently_dropped(self):
        with self.assertRaisesRegex(RuntimeError,"Malformed class-load record.*:2"):
            self.parse("[info][class,load] java.lang.Object source: shared objects file\n[info][class,load] sample.Broken without-source\n")

    def test_08_empty_source_rejected(self):
        with self.assertRaisesRegex(RuntimeError,"Malformed class-load record"):
            self.parse("[info][class,load] sample.Broken source:   \n")

    def test_09_invalid_utf8_rejected(self):
        with self.assertRaises(UnicodeDecodeError):
            self.parse(b"[info][class,load] sample.Broken source: \xff\n")

    def test_10_accepted_vm_multiplicity_rule_unchanged(self):
        line="[info][class,load] sample.Factory$Lambda/0xabcd source: __JVM_LookupDefineClass__\n"
        first,_=self.parse(line)
        second,_=self.parse(line*2)
        result=CAPTURE.generated_hidden_repeat_equivalence(first,second)
        self.assertTrue(result["equal"])
        self.assertEqual(len(result["vm_lookup_define_multiplicity_delta"]),1)
        self.assertEqual(result["first_vm_lookup_define_rows"],1)
        self.assertEqual(result["second_vm_lookup_define_rows"],2)

if __name__=="__main__":
    unittest.main(verbosity=2)
