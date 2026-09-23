# Pest Control Tier-1 coverage — Mono-Blue Terror production gameplay driver

## Scope

This gate adds the production gameplay driver for an already-authorized, already-initialized
Mono-Blue Terror game.

The driver owns only gameplay advancement:

- London mulligans through `EngineAiPlayerController`;
- `AiProfile.PRODUCTION_CANDIDATE_EXPIRING` for priority and pending decisions;
- exact-one action submission;
- per-turn and total-action safety ceilings;
- canonical action/event traces;
- terminal winner/turn state;
- Mono-Blue Terror telemetry indexing; and
- canonical raw-game JSON encoding.

It owns no artifact loader, assignment decoder, evidence root, seed source, durable-attempt writer,
retry/replacement policy, or workflow entrypoint.

## Calibration unification

The accepted four-cell production-AI compatibility rehearsal now routes through:

`AuthorizedInitializer -> ProductionDriver -> encode(raw)`

rather than maintaining a duplicate AI/gameplay loop.

The existing calibration proof target remains unchanged:

`38741508238b542304a18e88b4976c83eb87a5c27801424f25e940de16d8b227`

and still requires four clean nonofficial terminals, Terror gameplay participation in all four,
zero rejected actions and zero wedges.

This gate does not consume an official frozen seed. Official games, actions and outcome exposure
remain zero until a later reviewed integration/one-shot execution gate binds the frozen assignments,
durable journal, authorized initializer, coordinator and this driver.
