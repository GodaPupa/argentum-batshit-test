# Exact first-cell definition export

This is an offline artifact generator for the already reviewed A3-F4, A3-N0 and MisterTwin Red main decks. It loads exactly the 33 distinct main-deck card names and the predefined Blood, Clue and Wicked Role definitions. It does not initialize a game, invoke a pilot, allocate a seed, discover the card corpus, change a card or add a class to the gameplay classpath.

The explicit getter order is in `GETTERS.json`. Every entry identifies its Kotlin source declaration, JVM holder/getter, natural card name and source origin. The Mountain entry is the existing `ArnMountain` basic-land getter, collector 77. Its actual raw metadata and intrinsic lookup alias are retained. Predefined token singleton initialization may internally construct other token definitions; only the three declared tokens enter the archived bundle or restored registry. No `TestCards` override or name-based corpus fallback is used.

## Command

After the complete source is committed, the common-source gates pass, the actual ordered classpath is captured, and the export plan is reviewed:

```bash
python /absolute/repository/ferocity-recycling/tools/first-cell-export/export_first_cell.py \
  --plan /absolute/reviewed-export-plan.json \
  --plan-sha256 REVIEWED_PLAN_SHA256 \
  --output /absolute/new-export-directory
```

The output directory must not exist. A failed or interrupted attempt is retained and never reopened by this command. The script uses the exact previously qualified `trial_watchdog.py` with a 120-second wall limit and five-second termination grace. The Java source-file launcher compiles its own small wrapper outside the application classpath. The wrapper requires the actual application classpath to equal the supplied ordered list, verifies all classpath entries before and after export, and records each getter's real loaded location. No exporter class is added to a future gameplay process.

## Export plan

The plan is a JSON object with exactly these fields:

| Field | Required value |
|---|---|
| `schemaVersion` | `1` |
| `scope` | `FEROCITY_FIRST_CELL_OFFLINE_EXPORT` |
| `repositoryPath` | Absolute, real, normalized repository directory |
| `sourceCommit` | Published logical source commit, 40 hexadecimal characters, ancestor of current HEAD |
| `sourceTreeSha256` | SHA-256 of the canonical complete compiled-input path-to-SHA map |
| `compiledInputsPath` | Repository-relative path to that exact JSON map |
| `compiledInputsSha256` | SHA-256 of the map file's exact bytes |
| `serializerSha256` | Actual compiled `FerocityJournalCodec.kt` SHA-256 |
| `classPath` | Actual ordered `FerocityRuntimePathPin` objects: `path`, `directory`, `sha256`, `files` |
| `javaExecutable` | Absolute real path to the qualified Java executable |
| `javaExecutableSha256` | Exact executable digest |
| `policySha256` | Exact digests for `artifact-control` and `red-madness`; later final admission verifies their source maps |
| `protocolSha256` | Actual effective protocol map digest; later final admission verifies its required files |
| `dependencies` | Already reviewed additional dependency key-to-SHA bindings, or `{}`. Conflicting generated bindings fail. |

Every compiled input must have the same bytes as the published logical source version and the working tree. All 36 getter declarations must belong to that compiled-input map. The frozen deck files are checked against their original exact file hashes; main-deck hashes are independently reconstructed from their names/counts. There are no caller-supplied card names, reflection methods or token recipes in this plan.

The script creates a resolved plan containing the exact source map, fixed getters and deck identities. Java calls the existing qualified `captureFerocityDefinitions` and `writeFerocityBundle` APIs. The bundle contains the raw serialized `CardDefinition` values and their actual generated `AbilityId` values. Nothing normalizes, regenerates or remaps those identities.

The Fish admission artifact uses the existing `giftEffect(TAPPED_FISH)` descriptor and the exact admitted Sazacap's Brew definition. Its four runtime source hashes are taken from the compiled-input map. There is no fabricated Fish registry definition.

## Output and acceptance boundary

The output includes `definition-bundle.json`, `inline-token-admission.json`, `artifact-validation-pins.json`, `export-receipt.json`, the exact input/resolved plans, command/source pins, preserved watchdog claim/events/stdout/stderr, process classification and a recursive artifact hash manifest. The bundle and inline admission are actually reloaded through `readFerocityBundle` and `verifyFerocityInlineTokenAdmission` before the export receipt is written.

`artifact-validation-pins.json` is only an offline validation input. Its admission field is the export-plan digest, and its ledger field is the explicit no-ledger marker. It is not a research admission or a seed ledger. The final `FerocityDevelopmentManifest` must bind the produced bundle, inline artifact, source dependencies, card-definition lookup map, actual classpath, complete qualification receipts and approved policies. The existing development CLI `verify` command must accept that final manifest before `allocate` may obtain any entropy.

An exporter success means that these exact artifacts were captured and reloaded. It supplies no game outcome, pilot-quality result, deck comparison, new mechanical qualification or independent confirmation. The existing 16-case bundle qualification and 12-case inline-token qualification remain separate gates on their common source; this wrapper adds no replacement fixture bank.
