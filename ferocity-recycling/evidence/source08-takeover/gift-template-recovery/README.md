# Exact historical Gift template recovery

Record cut: 2026-09-26 21:35 UTC. **The exact historical Gift template and original resource-calibration attempts are recovered. The existing calibration sequence has stopped; it is not unspent.** This record preserves existing bytes, corrects the earlier takeover status, and consumes no new resource-probe command or experimental allocation. No deck-performance evidence changed.

## Bound target and completed retrieval

The accepted resource component pins a 471,219-byte historical Gift journal with SHA-256 `fa4cbc67fc5482d298ddf7e92968349b6eba584c4328c851d2bda86ce0277c5a`. Its recorded location is:

`ferocity-recycling/evidence/inline-token-provenance/fixed-8482829704501321957/fresh-gift/journals/09e7e48187b556594a17d549db795fbbc49ffb9067172f14fd45eb796b2448e0.jsonl`

The contemporaneous byte count, digest and nine-record inventory are retained inside [resource-boundary-05](https://github.com/GodaPupa/argentum-batshit-test/blob/3a4f99a7653839506e96d19e6639f58d9e8c5ced/ferocity-recycling/evidence/build/publication-M1-source-audits/04-resource-boundary-05.zip), member `ferocity-recycling/runtime-audits/development-admission/resource-boundary-05/observed-before/OBSERVED_JOURNAL_STORAGE.json`. The [probe source](https://github.com/GodaPupa/argentum-batshit-test/blob/3a4f99a7653839506e96d19e6639f58d9e8c5ced/gym/src/test/kotlin/com/wingedsheep/gym/ferocity/FerocityJournalResourceProbe.kt) enforces that exact digest. [Resource source review 05](https://github.com/GodaPupa/argentum-batshit-test/blob/3a4f99a7653839506e96d19e6639f58d9e8c5ced/ferocity-recycling/support-audits/resource-boundary-root-review-05/REVIEW.json) declares at most three noninitializing calibration processes.

The isolated retrieval source is commit `5771a2cd5343610f2a4f5142269ca6a235ed9aec`, tree `5670f1a78a53c8c36b3e45a6f2aa26c648397cc4`, branch `lab/ferocity-historical-gift-retrieval-20260926`. Its [workflow run 36272829768](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36272829768), push attempt 1, succeeded. It read exactly one immutable existing Git archive and performed ZIP/hash inspection. The only subprocess calls were Git identity/inventory reads. The source was reviewed by actual nonauthor agent `/root/sphinx` before publication; that review grants no gameplay or resource-calibration admission.

| Preserved object | Bytes | SHA-256 |
|---|---:|---|
| Original retrieval artifact `10915908757` | 3,312,109 | `1bc6598d892115c59f9f6a152d994a87a31db47a93b61681ea8af80af8d641ce` |
| Original published outer archive | 3,077,462 | `b99335efcfc04767cce9ea34b074b76a73f1ce47050787f9c89fc90763c66e11` |
| Original nested fixed-process archive | 223,731 | `004da1b12dde092b96623b31531f10d8291ad16fdba3c7d001c74d03fd6410de` |

The outer archive is `ferocity-recycling/evidence/build/integrated-bundle-publication-v4-2/integrated-v4-2-bundle-04-part-01.zip` at source08, Git blob `a2d9f217df8112094bdb49f1319d6ae7d0f4d7f6`. Its size and both ZIP SHA-256 values were independently checked against the immutable [publication manifest](https://github.com/GodaPupa/argentum-batshit-test/blob/3a4f99a7653839506e96d19e6639f58d9e8c5ced/ferocity-recycling/evidence/build/integrated-bundle-publication-v4-2/publication-manifest.json). The original retrieval artifact contains the original outer ZIP, the original nested ZIP and the complete inspection manifest. Their CRCs and all recorded member sizes/hashes were checked after download: 13 outer members and 23 nested members.

## Actual result and limits

The nested archive contains two historical card-definition-bundle journals, of 35,732 and 35,730 bytes. Their SHA-256 values are `93dcf4e99e78b6af455fbfd031e016f5eaef4b0885ed9df0e7ce8ca557d3135d` and `088e42011af658d5352d7f71f6a7d0041686f6e4ae2718e979522f1aa89c022c`. **Neither is the predeclared Gift template.** `retrieval-manifest.json` records an empty target-match list. These journals are not substituted into the probe.

### Legitimate local recovery

After the archive inspection, a direct read of the historically recorded `/tmp/ferocity-qualified-build-a63kaiqe/checkout` established that this checkout still exists. Its original `fresh-gift` directory contains the exact 471,219-byte journal, matching claim and allocation files, definitions, original command/logs and fresh-replay request/receipt. All ten files were copied read-only with source stat and SHA-256 checks before/after copying. `recovered-original-fresh-gift.zip` is a newly created ZIP container holding those unchanged original member bytes; its SHA-256 is `09c013ca983083a9189ad7c9a1ef033dc1284d94a0e322b06d55390b02011c00` and its size is 158,706 bytes.

The three template identities agree with the original scope-41 and scope-44 plans: journal `fa4cbc67…`, claim `8ed0bdd0…`, allocation `d7b1c601…`. The historical Java executable also still exists at `/tmp/ferocity-qualified-build-a63kaiqe/toolchain/jdk-21.0.12.1+1/bin/java`, with the required SHA-256 `2a207f5e7d075afa01d97f8048389a64432a44c4a5af0f5e77d6e286ec5f401d`. It was hashed, not launched by this recovery.

The earlier bounded search checked 28 compact publication manifests, recursively inspected the 18 small archives enumerated in `earlier-small-archive-inspection.json`, and queried source08 Git ancestry for the exact target path and containing evidence directory. Those path-history responses were empty. These unsuccessful archive/history checks remain documented alongside the successful recovery; they were never proof that original bytes did not exist elsewhere.

## Recovered actual resource execution

The same historical checkout retains previously executed scopes 41 and 44. The records are later untracked files beside detached checkout HEAD `2a99c52bcfb869ecc0c32443c2b77765abde4b65`; this HEAD alone is not their full runtime identity. Their exact M1 source freeze, full source and compiled-input maps, qualified gym receipt/classpath manifest, runner/plan freezes and raw attempt files are preserved. They do not constitute source08 receiving acceptance.

| Historical purpose | Supervisor attempts | Actual JVM launches | Observed result |
|---|---:|---:|---|
| Scope 41 write | 1 | 0 | Claimed; 768 MiB free-space preflight refused. Raw supervisor observation: 641,425,408 bytes available. |
| Scope 44 write | 1 | 1 | Exit 0; synthetic writer produced 134,166,907 bytes and 2,302 records under the declared 2 GiB heap and 128 MiB per-file limit. |
| Scope 44 read-single | 1 | 0 | Claimed; 768 MiB free-space preflight refused. Raw supervisor observation: 608,149,504 bytes available. |
| Scope 44 read-double | 0 | 0 | Retired by the frozen stop rule after the reader prerequisite failure. |

The wrapper's capacity observations were taken at different times and differ slightly from the raw supervisor preflight values above; both are preserved. All three supervisor hash chains validate from their exact claim hashes. The synthetic journal SHA-256 is `b45815c56592d2dc6bce43963590518a2b2d83510e1ea6aecb4b14a3bfe91752`. Its full original bytes validate through 2,302 envelopes: one HEADER, one INITIALIZED, 1,150 INTENT and 1,150 RESULT records, with no END. A static Python byte/hash audit does not perform the unexecuted JVM reader calibration or engine replay.

The historical writer receipt reports 16,261,617,831 elapsed nanoseconds, sampled maximum heap 714,266,000 bytes and process VmHWM 960,796 kB. These are observations for a repeated small fixed Gift state. There is no reader memory observation, no universal capacity guarantee and no game outcome. The complete original synthetic journal is retained in `recovered-resource-attempts-41-44` parts; it is not reconstructed from the writer report.

### Authority and remaining allowance

Scope 41 has its contemporaneous independent source review. Scope 44's frozen plan describes an environment successor permitting one additional write supervisor attempt after scope 41's zero-JVM refusal, preserving the remaining reader purposes and the three-JVM ceiling. Its actual claimed attempts, writer launch and stopped sequence are established by original bytes. The distinct adoption/approval provenance for that environment successor has not yet been fully reconciled; this recovery does not retroactively approve it.

Scope 44's explicit stop is: any failure of its additional write/preflight or either reader ends the sequence, with no further launch or budget extension. Its final assessment marks read-double retired and forbids another local calibration without a new explicit environment disposition. Therefore **no calibration command is currently admitted**, despite only one historical JVM launch. No new reader, replacement write, alternate root or scope identifier is authorized by this preservation work.

The takeover initially reported these purposes unspent while examining remote source08 evidence. That statement was incomplete and is corrected by these newly recovered original attempts. Official randomized development/evaluation/confirmation/postboard counts remain zero in both the historical local ledger and immutable source08 ledger. The one-use source08 browser bank remains consumed by its original browser attempt and was not rerun.

No active process with its executable or working directory under the historical checkout/toolchain was observed during the recovery. That is a time-specific ownership observation, not permission to alter or clean that checkout; it was kept read-only. Complete source08 receiving review, exact offline card bundle, source/dependency/policy/protocol binding and independent D2 admission remain separate requirements. The next resource work is reconciliation of the original environment disposition and a properly reviewed prospective technical remedy, preserving all consumed attempts and the current stop; it is not another invocation of the old sequence.

## Durable files

`original-retrieval-artifact-10915908757.zip` is the complete original Actions retrieval archive, containing both older archive byte streams and the inspection manifest. `recovered-original-fresh-gift.zip` preserves the ten recovered template files. `recovered-resource-attempts-41-44` is a newly created lossless ZIP, split into parts below 8 MiB for transport, preserving 46 original files and 143,429,170 raw bytes, including the full synthetic journal; the whole ZIP is 14,610,172 bytes with SHA-256 `0ebeef79e317991770e11a5668765599e4e7d8eeed5d65303211796f4f5ec425`. Reassemble it by concatenating its parts in ascending index order and verify the whole/part digests in `publication-manifest.json` before opening.

The recovery manifests record every original member and its digest. `recovered-attempt-static-audit.json` states the exact nonexecuting audit scope. Workflow metadata, source review, preservation review and the earlier archive/path-history observations retain their distinct scopes. No original historical checkout file was changed or deleted.
