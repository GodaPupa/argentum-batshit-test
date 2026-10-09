"""One offline qualification; retains each actual invocation outside the checkout."""
import base64
import copy
import json
import os
from pathlib import Path
import unittest

import monster_tron_replication_exclusion_inventory as v


def row(identity="EXCLUDED_ALPHA", status="RETIRED", reservation=None):
    return dict(identity=identity, status=status, reservation=reservation)


def source(sid, rows):
    return dict(realm=v.REALM, source_id=sid,
                members=[dict(member_id="EXCLUDED_MEMBER", rows=rows)])


def bind(sources):
    pairs = [(s["source_id"], v.canonical(s)) for s in sources]
    catalogue = dict(realm=v.REALM, official_bindings=None, sources=[
        dict(source_id=s["source_id"], sha256=v.digest(raw), members=[
            dict(member_id=m["member_id"], sha256=v.digest(v.canonical(m)))
            for m in s["members"]]) for s, (_, raw) in zip(sources, pairs)])
    raw = v.canonical(catalogue)
    return raw, v.digest(raw), pairs


class InventoryTest(unittest.TestCase):
    def setUp(self):
        self.sources = [source("EXCLUDED_SOURCE_A", [row(), row("EXCLUDED_BETA", "FIXTURE")]),
                        source("EXCLUDED_SOURCE_B", [row()])]
        self.calls = 0
        self.output = Path(os.environ["PEST_INVENTORY_TEST_OUTPUT"]) / self._testMethodName
        self.output.mkdir(parents=True, exist_ok=False)

    def invoke(self, args, expected_refusal=False, ack=v.ACK):
        self.calls += 1
        raw, pin, pairs = args
        record = dict(catalogue_base64=base64.b64encode(raw).decode(), pin=pin, ack=ack,
            sources=[dict(source_id=sid, raw_base64=base64.b64encode(b).decode()) for sid, b in pairs],
            expected_refusal=expected_refusal)
        destination = self.output / f"{self.calls:03d}.json"
        destination.write_text(json.dumps(record, indent=2) + "\n")
        try:
            result = v.verify_inventory(raw, pin, pairs, ack)
        except Exception as error:
            record["exception_type"] = type(error).__name__
            record["exception"] = str(error)
            destination.write_text(json.dumps(record, indent=2) + "\n")
            if not expected_refusal:
                raise
            self.assertIsInstance(error, v.Refused)
            return
        record["result_base64"] = base64.b64encode(result).decode()
        destination.write_text(json.dumps(record, indent=2) + "\n")
        self.assertFalse(expected_refusal, "unexpected acceptance")
        return json.loads(result)

    def refuse(self, sources=None):
        self.invoke(bind(self.sources if sources is None else sources), True)

    def test_union_and_complete_cross_source_provenance(self):
        before = copy.deepcopy(self.sources)
        d = self.invoke(bind(self.sources))
        self.assertEqual([e["identity"] for e in d["entries"]], ["EXCLUDED_ALPHA", "EXCLUDED_BETA"])
        self.assertEqual(len(d["entries"][0]["provenance"]), 2)
        self.assertEqual(d["official_bindings"], None)
        for k in ("live_inventory_complete", "draw_eligible", "provenance_authenticated",
                  "historical_execution_proven", "execution_authorized"):
            self.assertIs(d[k], False)
        self.assertEqual(before, self.sources)

    def test_source_pair_order_is_byte_deterministic(self):
        args = bind(self.sources)
        a = self.invoke(args)
        b = self.invoke((args[0], args[1], list(reversed(args[2]))))
        self.assertEqual(a, b)

    def test_row_and_catalogue_order_preserve_union(self):
        a = self.invoke(bind(self.sources))
        self.sources.reverse()
        self.sources[1]["members"][0]["rows"].reverse()
        b = self.invoke(bind(self.sources))
        self.assertEqual(a["entries"], b["entries"])
        self.assertNotEqual(a["catalogue_sha256"], b["catalogue_sha256"])

    def test_compatible_reservation_repeated_across_sources(self):
        ss = [source(s, [row(status="RESERVED", reservation="EXCLUDED_RESERVATION")])
              for s in ("EXCLUDED_SOURCE_A", "EXCLUDED_SOURCE_B")]
        self.assertEqual(len(self.invoke(bind(ss))["entries"][0]["provenance"]), 2)

    def test_nonreservation_status_provenance_retained(self):
        self.sources[1]["members"][0]["rows"][0]["status"] = "REJECTED"
        self.assertEqual(len(self.invoke(bind(self.sources))["entries"][0]["provenance"]), 2)

    def test_missing_source(self):
        a, h, p = bind(self.sources); self.invoke((a, h, p[:-1]), True)

    def test_extra_source(self):
        a, h, p = bind(self.sources); self.invoke((a, h, p + [("EXCLUDED_EXTRA", p[0][1])]), True)

    def test_duplicate_supplied_source(self):
        a, h, p = bind(self.sources); self.invoke((a, h, [p[0], p[0]]), True)

    def test_duplicate_catalogue_source(self):
        self.sources[1]["source_id"] = self.sources[0]["source_id"]; self.refuse()

    def test_wrong_source_identity(self):
        a, h, p = bind(self.sources)
        self.invoke((a, h, [("EXCLUDED_OTHER", p[0][1]), p[1]]), True)

    def test_source_drift(self):
        a, h, p = bind(self.sources); self.invoke((a, h, [(p[0][0], p[0][1] + b" "), p[1]]), True)

    def test_catalogue_drift_and_missing_pin(self):
        a, h, p = bind(self.sources)
        self.invoke((a + b" ", h, p), True); self.invoke((a, "", p), True)

    def test_member_digest_drift(self):
        a, _, p = bind(self.sources); d = json.loads(a)
        d["sources"][0]["members"][0]["sha256"] = "0" * 64
        a = v.canonical(d); self.invoke((a, v.digest(a), p), True)

    def test_missing_and_extra_catalogue_member(self):
        for mode in ("missing", "extra"):
            a, _, p = bind(self.sources); d = json.loads(a)
            if mode == "missing":
                d["sources"][0]["members"][0]["member_id"] = "EXCLUDED_OTHER"
            else:
                d["sources"][0]["members"].append(dict(member_id="EXCLUDED_EXTRA", sha256="0"*64))
            a = v.canonical(d); self.invoke((a, v.digest(a), p), True)

    def test_duplicate_member(self):
        self.sources[0]["members"] *= 2; self.refuse()

    def test_duplicate_row_same_member(self):
        self.sources[0]["members"][0]["rows"] *= 2; self.refuse()

    def test_duplicate_row_across_members(self):
        self.sources[0]["members"].append(dict(member_id="EXCLUDED_SECOND", rows=[row()]))
        self.refuse()

    def test_conflicting_reservations(self):
        self.refuse([source("EXCLUDED_SOURCE_A", [row(status="RESERVED", reservation="EXCLUDED_R1")]),
                     source("EXCLUDED_SOURCE_B", [row(status="RESERVED", reservation="EXCLUDED_R2")])])

    def test_reservation_and_retired_conflict(self):
        self.sources[0]["members"][0]["rows"][0] = row(status="RESERVED", reservation="EXCLUDED_R1")
        self.refuse()

    def test_malformed_identity_status_reservation(self):
        for field, value in [("identity", 123), ("identity", "official_seed"),
                             ("status", "UNKNOWN"), ("status", True),
                             ("reservation", "EXCLUDED_UNEXPECTED")]:
            ss = copy.deepcopy(self.sources); ss[0]["members"][0]["rows"][0][field] = value
            self.refuse(ss)
        self.refuse([source("EXCLUDED_SOURCE_A", [row(status="RESERVED")])])

    def test_official_and_authority_escalation(self):
        for field, value in [("official_bindings", {}), ("official_bindings", False),
                             ("execution_authorized", True), ("provenance_authenticated", True)]:
            a, _, p = bind(self.sources); d = json.loads(a); d[field] = value
            a = v.canonical(d); self.invoke((a, v.digest(a), p), True)
        ss = copy.deepcopy(self.sources); ss[0]["members"][0]["rows"][0]["seed"] = 123
        self.refuse(ss)

    def test_encoding_duplicate_key_and_empty_catalogue(self):
        _, _, p = bind(self.sources)
        for a in (b'{"realm":1,"realm":2}\n', b'\xff', b'{}\n', b'[]\n', b'{"x":NaN}\n'):
            self.invoke((a, v.digest(a), p), True)
        self.refuse([])

    def test_wrong_ack_and_size_bounds(self):
        self.invoke(bind(self.sources), True, ack="EXECUTE")
        a = b" " * (v.MAX_BYTES + 1); self.invoke((a, v.digest(a), []), True)

    def test_coherent_omission_is_not_live_completeness(self):
        whole = self.invoke(bind(self.sources))
        # Caller omits the designated source from both catalogue and supplied bytes.
        # Correspondence can pass; no inference of real-world completeness is possible.
        omitted = self.invoke(bind([self.sources[1]]))
        self.assertEqual(len(whole["entries"]), 2)
        self.assertEqual(len(omitted["entries"]), 1)
        self.assertEqual(omitted["completeness"], "RELATIVE_TO_SUPPLIED_CATALOGUE_ONLY")
        self.assertIs(omitted["live_inventory_complete"], False)
        self.assertIs(omitted["draw_eligible"], False)


if __name__ == "__main__":
    unittest.main(verbosity=2)
