#!/usr/bin/env python3
from __future__ import annotations
import hashlib, json, pathlib, zipfile

ROOT = pathlib.Path(".").resolve()
BASE = ROOT / "ferocity-recycling/evidence/build/publication-M1-source-audits"
OUT = ROOT / "build/reports/ferocity-first-cell-admission-extract"
MANIFEST = BASE / "MANIFEST.json"

TARGETS = {
    "02-offline-export-resource-successor-01.zip": [
        "ferocity-recycling/support-audits/offline-export-resource-successor-01/EFFECTIVE_PROTOCOL_MATERIAL_05.json",
        "ferocity-recycling/support-audits/offline-export-resource-successor-01/SOURCE_FREEZE.json",
        "ferocity-recycling/support-audits/offline-export-resource-successor-01/proposed/assemble_export_plan.py.proposed",
        "ferocity-recycling/support-audits/offline-export-resource-successor-01/proposed/export_first_cell.py.proposed",
    ],
    "04-resource-boundary-05.zip": [
        "ferocity-recycling/runtime-audits/development-admission/resource-boundary-05/PLAN_FREEZE.json",
        "ferocity-recycling/runtime-audits/development-admission/resource-boundary-05/SOURCE_FREEZE_01.json",
        "ferocity-recycling/runtime-audits/development-admission/resource-boundary-05/SOURCE_RESOURCE_ASSESSMENT.json",
        "ferocity-recycling/runtime-audits/development-admission/resource-boundary-05/before/gym/src/test/kotlin/com/wingedsheep/gym/ferocity/FerocityDevelopmentAdmission.kt.before",
        "ferocity-recycling/runtime-audits/development-admission/resource-boundary-05/before/gym/src/test/kotlin/com/wingedsheep/gym/ferocity/FerocityJournalCodec.kt.before",
        "ferocity-recycling/runtime-audits/development-admission/resource-boundary-05/before/gym/src/test/kotlin/com/wingedsheep/gym/ferocity/FerocityJournalRecords.kt.before",
        "ferocity-recycling/runtime-audits/development-admission/resource-boundary-05/before/gym/src/test/kotlin/com/wingedsheep/gym/ferocity/FerocityTrialJournal.kt.before",
        "ferocity-recycling/runtime-audits/development-admission/resource-boundary-05/before/gym/src/test/kotlin/com/wingedsheep/gym/ferocity/FerocityTrialReplay.kt.before",
        "ferocity-recycling/runtime-audits/development-admission/resource-boundary-05/before/gym/src/test/kotlin/com/wingedsheep/gym/ferocity/FerocityTrialRunner.kt.before",
    ],
    "05-resource-boundary-06.zip": [
        "ferocity-recycling/runtime-audits/development-admission/resource-boundary-06/FerocityDevelopmentAdmission.kt.before",
        "ferocity-recycling/runtime-audits/development-admission/resource-boundary-06/PLAN_FREEZE.json",
        "ferocity-recycling/runtime-audits/development-admission/resource-boundary-06/SOURCE_FREEZE_05.before.json",
        "ferocity-recycling/runtime-audits/development-admission/resource-boundary-06/SOURCE_FREEZE_06.json",
    ],
    "08-historical-first-cell-policy.zip": [
        "ferocity-recycling/policy-development/FIRST_CELL_POLICY_PLAN.md",
        "ferocity-recycling/policy-development/artifact-pilot-v0.1/SOURCE_FREEZE.json",
        "ferocity-recycling/policy-development/artifact-pilot-v0.1/SOURCE_FREEZE_01_1.json",
    ],
}

def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def load_expected():
    manifest = json.loads(MANIFEST.read_text())
    rows = {}
    archive_rows = {}
    for group in manifest["groups"]:
        for a in group.get("artifacts", []):
            archive_rows[pathlib.Path(a["path"]).name] = a
        for m in group.get("members", []):
            rows[m["path"]] = m
    return rows, archive_rows

def safe_output_name(archive: str, member: str) -> pathlib.Path:
    leaf = member.replace("/", "__")
    return OUT / "members" / (archive.removesuffix(".zip") + "__" + leaf)

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "members").mkdir(parents=True, exist_ok=True)
    expected, archives = load_expected()
    report = {
        "schema": "ferocity-first-cell-admission-preserved-evidence-extract-v1",
        "source_only": True,
        "jvm_started": False,
        "seed_or_entropy_files_read": False,
        "game_initialized": False,
        "members": [],
    }
    for archive_name, members in TARGETS.items():
        archive_path = BASE / archive_name
        assert archive_path.is_file(), archive_path
        raw_archive = archive_path.read_bytes()
        a = archives[archive_name]
        assert sha256(raw_archive) == a["sha256"], archive_name
        with zipfile.ZipFile(archive_path) as zf:
            bad = zf.testzip()
            assert bad is None, f"CRC failure: {bad}"
            names = zf.namelist()
            assert len(names) == len(set(names)), f"duplicate ZIP members in {archive_name}"
            for member in members:
                assert not any(word in member.lower() for word in ("seed", "entropy", "game-result", "outcome"))
                assert member in names, (archive_name, member)
                data = zf.read(member)
                e = expected[member]
                actual = sha256(data)
                assert actual == e["sha256"], (member, actual, e["sha256"])
                out = safe_output_name(archive_name, member)
                out.write_bytes(data)
                report["members"].append({
                    "archive": archive_name,
                    "archive_sha256": a["sha256"],
                    "path": member,
                    "bytes": len(data),
                    "sha256": actual,
                    "manifest_sha256": e["sha256"],
                    "copied_path": out.relative_to(OUT).as_posix(),
                })
    report["members"].sort(key=lambda x:(x["archive"],x["path"]))
    report["member_count"] = len(report["members"])
    (OUT / "extract.json").write_text(json.dumps(report, sort_keys=True, indent=2) + "\n")
    print(json.dumps({
        "member_count": report["member_count"],
        "archives": sorted(TARGETS),
        "seed_or_entropy_files_read": False,
        "jvm_started": False,
        "game_initialized": False,
    }, indent=2))

if __name__ == "__main__":
    main()
