# v0.9 Position 1 — Engine Coverage Batch AB Gate

Purpose: qualify Veteran Beastrider's Afterlife by composing already-qualified creature-destruction,
no-regeneration, target-controller token-creation, and token-keyword rails without changing
Izzet Science v0.7, Veteran Beastrider's frozen identity, or any gameplay engine primitive.

## Frozen boundaries

- Izzet Science v0.7 remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through AA remain unchanged.
- Batch AA is accepted from Actions run **35999269636** and audited artifact
  `izzet-v09-position1-engine-batch-aa` (artifact ID **10807656778**, SHA-256
  `01772830b10cd133ae2ae1c6462d43f183a21f9b0d2413f5d3fd0431c4488ace`).
- Batch AA accepted source SHA is
  `47c4f8411aa53fc55df8c72f85d2ffa29459d4f4`;
  formal acceptance record HEAD is
  `f0408b6fc162708f6f8cae84adc3f9607cf57f22`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.

## Why Afterlife is Batch AB

Afterlife is the smallest remaining Veteran blocker that can be expressed entirely from already
qualified generic primitives:

- `Effects.Destroy(..., noRegenerate = true)` already models "It can't be regenerated";
- Generous Gift already qualified target-controller token creation after destruction;
- generic token definitions already support color, creature type, and keyword sets.

Current Oracle text:
"Destroy target creature. It can't be regenerated. Its controller creates a 1/1 white Spirit
creature token with flying."

Therefore Batch AB adds no new executor, decision type, target-routing path, or engine mechanic.

## Acceptance

1. Afterlife is `{2}{W}`, Instant.
2. It targets exactly one creature.
3. On legal resolution, the target creature is destroyed with the no-regeneration flag.
4. The destroyed creature's controller, not necessarily Afterlife's controller, creates exactly one
   1/1 white Spirit creature token with flying.
5. If the sole target is illegal at resolution, the spell does not resolve and no Spirit is created.
6. No new engine executor, decision type, target-routing path, or token primitive is introduced.
7. Canonical MIR snapshot is reblessed through a fail-closed workflow.
8. Full golden card snapshots pass.
9. Expected post-implementation unresolved count is exactly 25.
10. Afterlife is absent from unresolved output and no Izzet identity becomes unresolved.
11. Official games/seeds/outcomes remain `0/0/0` and the exact v0.7 control remains unchanged.

Infrastructure, compilation, fixture, snapshot, or semantic failures are qualification history only,
not evidence about deck strength.
