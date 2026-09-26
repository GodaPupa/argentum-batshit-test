# Source08 original evidence — lossless preservation

Evidence source: `3a4f99a7653839506e96d19e6639f58d9e8c5ced`, tree `56c6b8dd46dc112cdb70db496fd9d0c3e915e8a6`.

The publication preserves **all eleven original ZIP archives**, byte for byte, in 32 parts of at most 8 MiB. Nothing was rerun to obtain these bytes. The manifests name their original artifact IDs, lengths, SHA-256 values, ordered offsets and each part's SHA-256/Git blob identity. The complete archives include raw XML, logs, receipts, before/after source maps, the observed gym runtime, browser reports, all four browser traces and the receipt-listed hidden Playwright metadata.

- Deterministic run: [36268324461](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36268324461), attempt 1, 508 unique fresh cases, zero failures/errors/skips.
- Browser run: [36268319777](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36268319777), attempt 1, four cases passed with zero retries. Its one-use source08 bank is consumed.
- Retrieval-only run: [36270532730](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36270532730), source `45bd52f967d9880e9792f30e1f00d40c68711c66`. It split already-existing bytes without tests, browser execution, initialization or entropy.

The audit reports state their exact scope. All observed source maps remained unchanged. Thirty-nine changed Kotlin source blobs were independently fetched and matched to Git blob SHA-1 and recorded SHA-256. This audit does not claim a fresh independent download of every unchanged source blob. The browser and JVM source maps agree on all 23,852 common entries. Component checks and this preservation review are not complete runtime or D2 gameplay admission.

## Restore and verify an original archive

Run this from this directory in a checkout with the published parts. It writes only new ZIP files to a new output directory.

```python
from pathlib import Path
import hashlib, json, zipfile

root = Path(".")
manifest = json.loads((root / "publication-manifest.json").read_text())
out = root / "reassembled"
out.mkdir(exist_ok=False)
for item in manifest["originals"]:
    target = out / ("original-" + str(item["id"]) + ".zip")
    with target.open("xb") as stream:
        for part in item["parts"]:
            data = (root / part["path"]).read_bytes()
            assert len(data) == part["bytes"]
            assert hashlib.sha256(data).hexdigest() == part["sha256"]
            assert stream.tell() == part["offset"]
            stream.write(data)
    assert target.stat().st_size == item["bytes"]
    with target.open("rb") as stream:
        assert hashlib.file_digest(stream, "sha256").hexdigest() == item["sha256"]
    with zipfile.ZipFile(target) as archive:
        assert archive.testzip() is None
```

## Preserved failure and boundaries

Postboard-B run `36268324422`, job `108477324521`, failed its source binder on `StackResolver.kt` before card tests. The decoded original job log is preserved; it reports no Gradle build results and the behavioral step was skipped. Its original failure is not silently cleared and its source manifest is not repinned. This is a qualification-source mismatch, not a Ferocity performance result.

The operative D3 ceiling remains 240 under the pre-existing reconciliation amendment; see the authority audit. No deck, pilot revision allowance, entropy allocation, experimental stopping rule or performance outcome changed. Research admission remains closed.
