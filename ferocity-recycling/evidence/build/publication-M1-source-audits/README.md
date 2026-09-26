# M1 source audit publication package

This package preserves the reviewed M1 source-audit history and the first-cell policy-development provenance. It adds no deck changes, pilot revisions, development allocations, gameplay outcomes, or acceptance claims. The source manifest records every selected raw member, including failed attempts and retained before-source archives.

Seven new ZIP archives contain 90 original files (1,411,916 uncompressed bytes; 558,704 compressed bytes). The other 21 selected files, the complete current `m1-controlled-source-import-34` directory, are represented by the two existing `m1-local527-publication-02/source-history-part-*.zip` archives. They were checked against builder publication manifest `841f27305914245eb548321a4e87787b05943e7118ede80d614cd3589ef50344` and against every current raw member. Those archives are referenced, not duplicated here. Their extra preserved packaging-failure member is explicitly recorded in `MANIFEST.json`.

All seven new archives are below the 3.3 MiB split threshold, so no multipart transport was needed or executed for them. The existing builder archives retain their already-selected transport unchanged, as directed by the parent publisher. They are two independent ZIPs, not raw pieces of one ZIP. `MANIFEST.json` supplies SHA-256, Git blob SHA-1 and CRC32 for every raw source member, as well as each archive's byte count and digests.

## Historical plans and current policy identity

`08-historical-first-cell-policy.zip` preserves 16 byte-identical historical top-level plans, policy notes, dependency receipts and source freezes. The original first-cell plan and Red v0.1 documents explain the prospective D1 allocation and earlier authoring boundaries; they are historical provenance. They neither create a new budget nor replace the current source-bound admission contract. The explicitly excluded `red-madness-v0.1/authoring-source-boundaries` subtree was outside this curated historical addition. No files were excluded from the seven complete source-audit directories.

At the M1 source boundary, the Red policy is `red-madness-v0.2`. Its Red successor source-freeze digest is `2a888ed51153693f7aa1340d68410e362527f6576e2f3629a357d349ed25dddb`; the actual `RedMadnessPilot.kt` digest is `6ddd9d1daa60e777775b9f66de777b87bb84034b8973c92589d0f5c2ed40cef5`. These are different artifacts. `ArtifactControlPilot.kt` remains unchanged at `3f92f97465ae3c39ce4d31b57a8f4215a802412510f251857059d4101c377251`. The M1 source manifest `41283f68392d7792131eff26877a67a5905ca254dbdeee5529da7f8c17094199` binds those implementation bytes. Frozen active lists, protocol and budget remain governed by their current source-bound records.

## Verification and publication index

`package_source_audits.py` ran once. It checked complete input inventories against the independently reviewed scan inventories, read back every written archive, verified CRC and raw member bytes, and rechecked all selected source bytes after packaging. `SCAN_AND_PACKAGING_AUDIT.json` records the independent content-review scope and the executed packaging checks. This packaging operation ran no build, Kotlin/browser scenario, or development/evaluation/confirmation game and made no Git reference mutation or publication.

`PUBLICATION_INDEX.json` lists all other files in this directory using repository-relative paths, byte counts, SHA-256 and Git blob SHA-1. Its own path is explicit but its self-digest is intentionally not embedded, which would create a recursive digest. The parent publisher receives a separate transport handoff containing the index's digest as well. Referenced builder files are listed separately and must not be uploaded twice if already in the parent publication selection.

To inspect an archive, use a ZIP reader and verify each extracted member against `MANIFEST.json`. Retired Kotlin sources stay inside the archive; extracting them into the active repository could affect filename-based test discovery. This package's existence is evidence preservation, not a new claim of experimental completion.
