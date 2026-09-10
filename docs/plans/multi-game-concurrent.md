# Plan: Play multiple games simultaneously via deep links

## Goal
A player can have several games in progress at once, each addressable by its own URL
(`/game/:gameSessionId`). Opening a link views that game; opening two links in two tabs lets you
play both at the same time. The app stops auto-hijacking you into "the active game." Tournament
match-start *notifies + links* instead of forcing you into the board.

Decisions (locked):
- **One WebSocket per tab.** Each tab = own page load = own store + own socket, bound to one game.
- **Tournament: notify-don't-hijack.** Match-ready shows a link; you click to open.

## Core insight: most of the substrate already exists
- Inbound action routing is already per-socket: `getGameSession()` uses
  `playerSession.currentGameSessionId`, and `playerSession` is keyed by `ws.id`
  (`SessionRegistry.playerSessions`).
- Outbound game-state routing is already per-game: `broadcastStateUpdate`
  (`GamePlayHandler.kt:715-749`) sends to the socket stored *inside each `GameSession`'s*
  `PlayerSession`, set at `associatePlayer` (`GameSession.kt:1349-1352`).
- `SessionRegistry.wsToToken` is already `Map<wsId, token>` — structurally many sockets → one token.

**The only thing forcing one game per player is the identity/reconnect layer:**
- token in `localStorage` (shared across tabs) → 2nd tab triggers `handleReconnect`.
- `handleReconnect` closes the previous socket via `SessionReplaced` (`ConnectionHandler.kt:159-169`).
- `PlayerIdentity.webSocketSession` is singular; `PlayerIdentity.currentGameSessionId` is a scalar.

## Design: per-connection identity
Introduce a **per-tab `connectionId`** (stored in `sessionStorage`, which is per-tab) alongside the
durable per-player **`token`** (stays in `localStorage`, shared). A player identity holds a *map* of
connections; each connection is bound to one context (a game id) derived from the tab's URL.

- New tab (new `connectionId`) → **add** a connection, never close others.
- Same tab refresh (same `connectionId`) → swap that connection's socket, resume its bound game.
- The **URL drives context**: the tab tells the server "view game X"; the server validates the
  player is a seat (or spectator) and serves that game's state to that socket.

---

## Phase 1 — Server: multi-connection identity (highest risk)
`PlayerIdentity` (`session/PlayerIdentity.kt`):
- Replace `webSocketSession: WebSocketSession?` with
  `connections: ConcurrentHashMap<String /*connectionId*/, Connection>` where
  `Connection(connectionId, ws, boundGameId: String?)`.
- `isConnected = isAi || connections.values.any { it.ws.isOpen }`.
- Replace scalar `currentGameSessionId` with derivation from connections (or keep an
  `activeGameSessionIds: MutableSet<String>` maintained on game start/over for the home list).
- Keep `toPlayerSession()` working (used by GameSession/SealedSession) — build from the relevant
  connection's socket.

`SessionRegistry`:
- `register`/`mapWsToToken` already keyed by wsId — keep. Add helper to add a connection to an
  identity without evicting siblings. Keep `removeOldWsMapping` but scope it to a single connectionId,
  not "every wsId for this token."

`ClientMessage.Connect`: add `connectionId: String?` and `viewContext` (gameId/lobbyId from URL, optional).

`handleConnect` / `handleReconnect` (`ConnectionHandler.kt`):
- If identity exists AND `connectionId` matches an existing connection → reconnect *that* connection
  (swap socket, resume its bound game). **Do not** send `SessionReplaced` to sibling connections.
- If identity exists but `connectionId` is new → add a new connection; bind to `viewContext`; leave
  siblings alone.
- Only send `SessionReplaced` when the *same* connectionId reconnects on a different live socket
  (true single-tab takeover) — or drop the concept entirely for the multi-game case.

## Phase 2 — Server: URL-driven game binding
- New `ClientMessage.ViewGame(gameSessionId)` (or fold into Connect's `viewContext`): bind this
  connection to a game.
  - Validate `identity.playerId` is a seat in that `GameSession` (or a permitted spectator).
  - Set the registry `PlayerSession(ws.id).currentGameSessionId = gameSessionId` (inbound routing).
  - `gameSession.associatePlayer(thisTabsPlayerSession)` so outbound broadcasts reach *this* socket.
  - Send full state (clear delta cache for this player) like the existing reconnect-to-game path
    (`ConnectionHandler.kt:226-275`).
- This replaces auto-resume-from-scalar with explicit, validated, link-driven binding.

## Phase 3 — Server: fan-out fixes for identity-level broadcasts
Audit every `identity.webSocketSession` send site and fan out to all of the identity's open
connections (or the subset bound to the relevant lobby):
- `broadcastOnlinePlayersCount`, tournament reconnect/disconnect broadcasts, lobby updates
  (`ConnectionHandler.kt:95-104, 124-131, 337-344, 524-531, 619-624`), and similar in
  `LobbyHandler`/`TournamentMatchHandler`.
- Game-state broadcasts already go through `GameSession` sockets — leave as-is.

## Phase 4 — Server: per-connection disconnect / auto-concede (delicate)
Today disconnect is identity-level and may auto-concede the single game. Re-express per connection:
- On socket close, find the connection's `boundGameId`. Remove that connection.
- Only start the 2-min in-game auto-concede timer for that game if **no other open connection of this
  identity is bound to that game**.
- Online-count "offline" only when the identity has zero open connections.
- Carefully port the existing stale-reconnect guards (`ConnectionHandler.kt:289-322`) to the
  per-connection model. **This is the main correctness risk** — preserve every race guard.

## Phase 5 — Client: deep-linkable game route + per-tab binding
- Add route `/game/:gameSessionId` (`main.tsx`) → renders the existing GameBoard for that id.
- Per-tab `connectionId`: generate once, store in `sessionStorage`; send on `Connect`.
- On `/game/:id` mount: ensure connected (shared `argentum-token`), send `ViewGame(id)`, set store
  `sessionId = id` from the URL (not from handlers).
- Keep URL in sync (already done for tournaments, `App.tsx:102-113`).
- `SessionReplaced`: stop treating a sibling tab as a takeover; only the same-connectionId case (if kept).

## Phase 6 — Client: home "your active games" + stop auto-hijack
- Server includes the player's `activeGameSessionIds` (+ opponent names) in `Connected`/`Reconnected`,
  or a new `ActiveGames` message.
- Home renders an "In progress" list, each row linking to `/game/:id`. Remove the implicit
  auto-open of the active game in connection/lobby handlers.

## Phase 7 — Tournament: notify-don't-hijack
- On match start (`TournamentMatchHandler.startSingleMatch` ~`:412-560`): still create the
  `GameSession` and set the match's `gameSessionId`, but **do not** auto-set the client `sessionId`.
- New `ServerMessage.TournamentMatchReady(gameSessionId, opponentName)` → tournament overlay shows
  "Your match vs X is ready → Open" linking to `/game/:id`.
- First `ViewGame` from each player associates their socket + serves state (Phase 2). State produced
  before they open is fine — `ViewGame` sends full state.

## Phase 8 — Tests
- E2E (`e2e-scenarios/`): same player opens two games in two tabs; both playable independently; an
  action in game A doesn't touch game B; refresh resumes the correct game per tab; tournament
  match-ready link opens the game.
- Server: connection-model unit coverage where feasible (game-server is E2E-heavy).

## Risks / edge cases
- **Disconnect/concede race rewrite (Phase 4)** — highest risk; many existing guards to preserve.
- **Same game opened in two tabs** — `associatePlayer` is last-writer-wins for the live socket.
  v1: accept last-wins (or guard the route to one tab per game). Fanning out to a *set* of sockets
  per seat is a possible follow-up, not v1.
- **Backward compat** — `Connect` without `connectionId` (old clients / dev-scenario links) must keep
  working: synthesize a connectionId server-side and behave as today (single connection).
- AI games (`AiWebSocketSession`) and spectating (`currentSpectatingGameId`, already separate) are
  unaffected.
- Account/auth linking happens per identity, not per connection — unchanged.
