# Round 2 trace archive index

All five final archives use exact gameplay source `d015679dcd7d256a6ea54a518435e5adce9542df`, run `39199428038`. Each contains four seat rotations, each with one candidate and one control directory. Every game directory preserves full `actions.jsonl`, `decisions.jsonl`, initial/final state, audit, setup and summary. All raw ZIPs match GitHub SHA256 receipts.

| Seed index | Archive | Artifact ID | SHA256 |
| --- | --- | --- | --- |
| 0 | [final-seed-0.zip](evidence/final-seed-0.zip) | 12163597113 | `eb950e22d53112a55e1dfd408e50f8caaaa9b5e845179879f1f352e19ff1905f` |
| 1 | [final-seed-1.zip](evidence/final-seed-1.zip) | 12163577075 | `43a3b3acfd771315fe48e8b3e10c70ec167a46567d8ed30c6fb7814bc2b52aa0` |
| 2 | [final-seed-2.zip](evidence/final-seed-2.zip) | 12163063102 | `fd980ce02c78487d954c9e288194dc3f5a8db46f29db89b1e01de34494bc6587` |
| 3 | [final-seed-3.zip](evidence/final-seed-3.zip) | 12163895377 | `08bdb51cd072208f50596936ef1de0c47ff72f1b35f36d0c9aac69823e862e81` |
| 4 | [final-seed-4.zip](evidence/final-seed-4.zip) | 12164645073 | `bfd95252f730dc35b1256271951d8d20151945f4bb72d44ac08b8d79ff188079` |

Seed indices 0–4 correspond to seeds 202610190801–202610190805. Seat directories use zero-based indices; Seat 1 is seat-0.

The isolated evidence branch also preserves the excluded first-attempt archives (ten genuine games; later rotations were cached), original failed policy/rules validation archives, and before/after discovery probes. They are not counted in the final 40 attempts. Source/run/artifact receipts are in [run-bindings.json](run-bindings.json). The original source archive remains [original-12161685788.zip](../../tools/manual-round2/original-12161685788.zip), SHA256 `27823f20e05ad0d10e9803a2d4b3408417ade366cbc9392fb67678370e8eb1f4`.
