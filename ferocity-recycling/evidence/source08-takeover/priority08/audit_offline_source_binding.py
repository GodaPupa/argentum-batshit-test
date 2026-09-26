"""Read-only source/deck inventory for the existing offline exporter; never runs it."""
from pathlib import Path
import hashlib
import json
import zipfile

ROOT = Path(__file__).parent
BASE = "ferocity-recycling/tools/first-cell-export/"
def sha(b): return hashlib.sha256(b).hexdigest()
source = json.loads((ROOT / "offline-export-exact-source-inputs.json").read_text())
tree = {v["path"]: v for v in json.loads((ROOT.parent / "retrieved-inputs.json").read_text())["tree"]["tree"]}
getters = json.loads(source[BASE + "GETTERS.json"]["content"])
freeze = json.loads(source[BASE + "SOURCE_FREEZE.json"]["content"])
with zipfile.ZipFile(ROOT.parent / "original-10914678223.zip") as z:
    compiled_raw = z.read("qualification/compiled-inputs-before.json")
    compiled = json.loads(compiled_raw)
pins = {}
for path, value in source.items():
    b = value["content"].encode()
    blob = hashlib.sha1(f"blob {len(b)}\0".encode() + b).hexdigest()
    assert blob == value["sha"] == tree[path]["sha"]
    assert len(b) == tree[path]["size"]
    pins[path] = {"bytes": len(b), "sha256": sha(b), "git_blob_sha1": blob}
assert len(getters["definitions"]) == 36
names = [x["name"] for x in getters["definitions"]]
assert len(set(names)) == 36
union, decks = set(), []
for record in getters["decks"]:
    path = record["path"]
    assert pins[path]["sha256"] == record["sha256"]
    d = json.loads(source[path]["content"])
    assert sum(row["count"] for row in d["main"]) == 60
    assert len({row["name"] for row in d["main"]}) == len(d["main"])
    union.update(row["name"] for row in d["main"])
    decks.append({"path": path, "id": d["id"], "main_count": 60, "raw_file_sha256": pins[path]["sha256"]})
assert len(union) == 33 and set(names) == union | {"Blood", "Clue", "Wicked Role"}
getter_sources = sorted({x["sourcePath"] for x in getters["definitions"]})
assert len(getter_sources) == 34
for path in getter_sources: assert pins[path]["sha256"] == compiled[path], path
historical_differences = []
for record in freeze["files"] + freeze["observed_exact_getter_sources"]:
    path = record["path"]
    if pins[path]["sha256"] != record["sha256"]:
        historical_differences.append({"path": path, "historical_freeze_sha256": record["sha256"], "source08_sha256": pins[path]["sha256"]})
report = {
    "schema": "ferocity-source08-offline-export-source-inventory-v1",
    "status": "VERIFIED_SOURCE_INVENTORY_NOT_EXPORT_PLAN_OR_ADMISSION",
    "source": "3a4f99a7653839506e96d19e6639f58d9e8c5ced",
    "tree": "56c6b8dd46dc112cdb70db496fd9d0c3e915e8a6",
    "exact_source_files_verified": pins,
    "decks": decks,
    "main_deck_union_count": 33,
    "raw_definition_getter_count": 36,
    "unique_getter_source_files": 34,
    "getter_sources_equal_observed_gym_compiled_source_map": True,
    "observed_gym_compiled_source_map_sha256": sha(compiled_raw),
    "historical_exporter_freeze_differences": historical_differences,
    "scope": "Exact public source inventory and frozen first-cell getter/deck geometry only. Historical freeze differences are preserved, not silently rebound or treated as defects. No card definitions were loaded and no production bundle or export plan was created.",
    "remaining_export_prerequisites": ["complete common-source acceptance", "reviewed exact runtime restoration and source/dependency/Java/policy/protocol closure", "source-bound export plan review", "final bundle/admission verification before entropy"],
    "no_calibration_override": "The recovered consumed calibration sequence and unresolved scope44 adoption remain unchanged. This inventory grants no new resource command.",
    "new_jvms": 0, "new_exports": 0, "new_entropy": 0, "new_games": 0
}
(ROOT / "offline-export-source-binding-audit.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
print(json.dumps({"files": len(pins), "getter_sources": len(getter_sources), "decks": len(decks), "union": len(union), "historical_differences": historical_differences}))
