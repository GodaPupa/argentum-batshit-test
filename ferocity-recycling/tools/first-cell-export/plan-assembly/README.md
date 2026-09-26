# Assemble the reviewed export plan

`MATERIAL.template.json` is deliberately incomplete. It contains no invented logical commit, source digest, classpath, Java identity, policy version or gate result. Copy it to a separate material file and populate it only from the final accepted source and runtime records. The assembly helper does not launch Java, initialize a game, create a seed ledger, choose qualification receipts or invoke the exporter.

## Material inputs

| Field | Source of the actual value |
|---|---|
| `repositoryPath` | Final canonical repository directory used by the source guard and admission |
| `sourceCommit` | Published logical source commit whose complete compiled inputs passed the common-source gates |
| `compiledInputs` | Repository-relative path and exact file digest of the complete compiled-input path-to-SHA map; no hand-selected subset |
| `classPathFile` | Absolute path and digest of a JSON array of the actual ordered `FerocityRuntimePathPin` objects captured from that accepted runtime; do not reorder or rebuild this list from an older worker |
| `javaExecutable` | Absolute real executable path and digest from that runtime |
| `policies` | The exact two final `FerocityDevelopmentPolicy` objects (`version` and complete helper/source `files` map) intended for admission |
| `protocolFiles` | The complete effective protocol path-to-SHA map intended for admission |
| `dependencies` | Reviewed additional dependency digests, or the empty map; the exporter creates its exact runtime/bundle/card/inline bindings and rejects conflicts |
| `exportOutput` | A new absolute directory outside the repository and every pinned classpath directory |

The classpath pin file is already typed data, not an unqualified replacement for a recorded runtime. The helper rechecks all supplied file and directory digests through the existing reviewed export validator. It cannot determine whether a collection of supplied gate receipts is scientifically sufficient. That remains the final common-source review and the existing `FerocityDevelopmentCli verify` contract.

The helper derives `sourceTreeSha256` from the canonical complete source map, `serializerSha256` from its actual journal codec entry, each `policySha256` from the canonical `{version,files}` object, and `protocolSha256` from the canonical protocol map. These are the same string/map shapes used by `FerocityDevelopmentManifest.toPins`; no placeholder digest is installed. Policy source entries must be present in the compiled map. The existing exporter then checks every frozen getter and deck against that source.

## Assembly command

```bash
python /absolute/repository/ferocity-recycling/tools/first-cell-export/plan-assembly/assemble_export_plan.py \
  --material /absolute/reviewed-material.json \
  --material-sha256 EXACT_MATERIAL_SHA256 \
  --output /absolute/new-plan-directory
```

This writes `export-plan.json`, an exact copy of the supplied material, and a plan assembly receipt with the plan hash and chosen export destination. Both the plan directory and planned export directory must be new and outside the repository and all classpath entries. This deliberately keeps generation writes away from qualified compiled inputs and outputs. The directories must also be disjoint. The final generated artifacts can be reviewed and copied into the project publication directory later.

Review the resulting plan hash and the final source/gate/classpath evidence before invoking the already reviewed export command. Use the **same `exportOutput`** recorded in the assembly receipt. The exporter itself does not read this assembly receipt, so that output equality is part of the reviewed invocation. Its successful raw bundle and inline-descriptor reload still does not authorize entropy: assemble the complete development manifest with actual qualification receipts and run its existing `verify` command first.

## Deferred admission fields

The following are intentionally absent from this plan helper: the produced bundle hash, Fish admission hash, raw definition lookup map, final gate receipt/XML/log/validator bindings, final admission hash and all seed-ledger values. They either do not exist until export or require the actual fresh gate records. Root must populate them from those artifacts; a green source review or assembled plan is not substituted for their existence.

The four previously reviewed exporter files remain unchanged. This companion helper is a separate offline preparation source and is not a gameplay dependency or a new test gate. At authorship only its Python syntax and material-template parse were checked. It has not assembled a final plan or run an export.
