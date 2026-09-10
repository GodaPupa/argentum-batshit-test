/**
 * Share-link codec for scenarios — analogous to `deckbuilder/shareDeck.ts` but simpler:
 * scenarios are name-based (no per-card printing pins), so we just JSON-serialize the
 * {@link ScenarioSpec}, `deflate-raw`-compress, and base64url-encode. A short version tag
 * lets the format evolve. `decodeScenario` never throws — it returns `null` for malformed
 * or untrusted input.
 */
import type { ScenarioSpec } from './types'

const SHARE_PARAM = 's'
/** v1 payloads begin with this char. */
const V1_TAG = '1'

// --- base64url <-> bytes (Unicode-safe) ----------------------------------------------------

function bytesToBase64Url(bytes: Uint8Array): string {
  let binary = ''
  const CHUNK = 0x8000
  for (let i = 0; i < bytes.length; i += CHUNK) {
    binary += String.fromCharCode(...bytes.subarray(i, i + CHUNK))
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function base64UrlToBytes(code: string): Uint8Array {
  const b64 = code.replace(/-/g, '+').replace(/_/g, '/')
  const pad = b64.length % 4 === 0 ? '' : '='.repeat(4 - (b64.length % 4))
  const binary = atob(b64 + pad)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return bytes
}

// --- DEFLATE via native compression streams ------------------------------------------------

async function pumpThroughStream(
  bytes: Uint8Array,
  stream: GenericTransformStream,
): Promise<Uint8Array> {
  const writer = stream.writable.getWriter()
  void writer.write(bytes).catch(() => {})
  void writer.close().catch(() => {})
  return new Uint8Array(await new Response(stream.readable).arrayBuffer())
}

const RAW_DEFLATE = 'deflate-raw' as CompressionFormat
const deflate = (bytes: Uint8Array): Promise<Uint8Array> =>
  pumpThroughStream(bytes, new CompressionStream(RAW_DEFLATE))
const inflate = (bytes: Uint8Array): Promise<Uint8Array> =>
  pumpThroughStream(bytes, new DecompressionStream(RAW_DEFLATE))

// --- encode / decode -----------------------------------------------------------------------

/** Encode a scenario into a URL-safe share code. Inverse of {@link decodeScenario}. */
export async function encodeScenario(spec: ScenarioSpec): Promise<string> {
  const json = V1_TAG + JSON.stringify(spec)
  const compressed = await deflate(new TextEncoder().encode(json))
  return bytesToBase64Url(compressed)
}

/** Decode a share code back into a scenario. Returns `null` for malformed/untrusted input. */
export async function decodeScenario(code: string): Promise<ScenarioSpec | null> {
  try {
    const bytes = base64UrlToBytes(code)
    const text = new TextDecoder().decode(await inflate(bytes))
    if (text[0] !== V1_TAG) return null
    const parsed: unknown = JSON.parse(text.slice(1))
    if (!parsed || typeof parsed !== 'object') return null
    // Trust the structure loosely — the server validates card names / counts on creation.
    return parsed as ScenarioSpec
  } catch {
    return null
  }
}

/** Build a full builder URL that loads [code] on open: `<origin>/scenario?s=<code>`. */
export function buildScenarioUrl(origin: string, code: string): string {
  return `${origin}/scenario?${SHARE_PARAM}=${code}`
}

// --- full-state snapshot from a replay frame (exact position) ------------------------------
// Rather than embed the (large) serialized GameState in the URL, a snapshot link *references*
// the replay frame the server already holds — keeping the link short. On open the builder POSTs
// to /api/scenarios/from-replay-frame, which injects the stored state. Reproduces the exact
// position (stack, targets, floating effects, mana, …); not editable in the builder.

const REPLAY_PARAM = 'replay'
const FRAME_PARAM = 'frame'

/** Build a short snapshot URL referencing a replay frame: `<origin>/scenario?replay=<id>&frame=<n>`. */
export function buildReplayScenarioUrl(origin: string, gameId: string, frame: number): string {
  return `${origin}/scenario?${REPLAY_PARAM}=${encodeURIComponent(gameId)}&${FRAME_PARAM}=${frame}`
}

export {
  SHARE_PARAM as SCENARIO_SHARE_PARAM,
  REPLAY_PARAM as SCENARIO_REPLAY_PARAM,
  FRAME_PARAM as SCENARIO_FRAME_PARAM,
}
