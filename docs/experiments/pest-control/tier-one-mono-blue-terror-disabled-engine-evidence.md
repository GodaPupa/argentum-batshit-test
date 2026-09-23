# Pest Control Tier-1 coverage — disabled engine/evidence rehearsal

## Scope

This gate integrates the accepted durable attempt journal with the real engine construction surface
using exactly four hard-coded nonexperimental assignments. It does **not** decode or consume the
four official frozen seeds.

For each synthetic cell it:

1. writes the durable attempt marker;
2. writes initialization-entry intent;
3. initializes a real `GameEnvironment` with the correct seat, starting deck and fixed synthetic seed;
4. validates 53-card library / 7-card hand conservation for both frozen deck identities;
5. writes a synthetic raw opening-conservation result and durable record; and
6. after all four cells, writes a write-once publication index over the durable evidence files.

All evidence is created under an internally allocated temporary root and removed before inspection
returns. The public facade accepts only `CardRegistry` and returns digests/counters; it cannot accept
a path, assignment, official seed, environment, authorization, execution commit, callback, or action.

## Accepted proof target

- Status: `SYNTHETIC_ENGINE_EVIDENCE_REHEARSED_EXECUTION_NOT_AUTHORIZED`
- Proof SHA-256: `675d27e529b5d25bc08ce06df402f5d3d5d0ecf4df375cfe0cbb44aa61b41662`
- Synthetic real-engine initializations: `4`
- Synthetic evidence bundles published: `1`
- Attempt / initialization / record order: `1,2,3,4`
- Publication source files: `17`
- Temporary roots removed: `1`
- Official seed values exposed: `0`
- Official seeds consumed: `0`
- Official games initialized: `0/4`
- Actions submitted: `0`
- Outcome exposure: `0/4`
- Runner enabled: `false`
- Official initializer enabled: `false`
- Execution authorized: `false`

This is an integration rehearsal, not an official smoke run. The next gate can bind an authoritative
persistent evidence root and an explicit execution decision, but official initialization remains
blocked until that separate decision is reviewed and accepted.
