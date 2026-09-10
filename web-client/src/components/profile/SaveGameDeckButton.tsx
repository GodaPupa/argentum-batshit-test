/**
 * "Save deck" control for one seat in the recent-games deck viewer ({@link DeckViewModal}).
 *
 * A recorded game deck is just a name→copies list (the registry-enriched {@link GameDeckCard} lines
 * carry no printing pins), so saving it produces a name-only deck — exactly what the deckbuilder
 * accepts. The save routes through {@link useSaveDeck}, so it lands in the user's account when signed
 * in and in the browser library otherwise, matching every other "save a deck" button in the app.
 *
 * Clicking expands an inline name field (prefilled with a sensible default) so the player can title
 * the deck before it is saved — important because account saves upsert by name and would otherwise
 * silently overwrite a same-named deck.
 */
import { useState } from 'react'
import type React from 'react'
import type { GameDeckParticipant } from '@/api/account'
import { useSaveDeck } from '@/store/useSaveDeck'

/** Build the default deck name from the seat and the game's date (YYYY-MM-DD prefix of the ISO ts). */
function defaultName(p: GameDeckParticipant, endedAt: string): string {
  const date = endedAt.slice(0, 10)
  const who = p.isSelf ? 'My deck' : `${p.playerName}’s deck`
  return date ? `${who} · ${date}` : who
}

type Phase =
  | { kind: 'idle' }
  | { kind: 'editing'; name: string }
  | { kind: 'saving' }
  | { kind: 'done'; online: boolean }
  | { kind: 'error' }

export function SaveGameDeckButton({
  participant,
  endedAt,
}: {
  participant: GameDeckParticipant
  endedAt: string
}) {
  const { save, isLoggedIn } = useSaveDeck()
  const [phase, setPhase] = useState<Phase>({ kind: 'idle' })

  // No decklist recorded for this seat — nothing to save.
  if (participant.cards.length === 0) return null

  const doSave = async (name: string) => {
    const trimmed = name.trim() || defaultName(participant, endedAt)
    const cards: Record<string, number> = {}
    for (const c of participant.cards) cards[c.cardName] = c.copies
    setPhase({ kind: 'saving' })
    try {
      const result = await save({ name: trimmed, cards })
      setPhase({ kind: 'done', online: result.online })
    } catch {
      setPhase({ kind: 'error' })
    }
  }

  if (phase.kind === 'done') {
    return (
      <span style={styles.feedback} title={phase.online ? 'Saved to your account' : 'Saved to this browser'}>
        Saved {phase.online ? 'online' : 'locally'} ✓
      </span>
    )
  }

  if (phase.kind === 'error') {
    return (
      <button type="button" style={styles.button} onClick={() => setPhase({ kind: 'idle' })}>
        Save failed — retry
      </button>
    )
  }

  if (phase.kind === 'editing') {
    return (
      <form
        style={styles.editRow}
        onSubmit={(e) => {
          e.preventDefault()
          void doSave(phase.name)
        }}
      >
        <input
          autoFocus
          style={styles.input}
          value={phase.name}
          onChange={(e) => setPhase({ kind: 'editing', name: e.target.value })}
          aria-label="Deck name"
        />
        <button type="submit" style={styles.saveBtn}>
          Save
        </button>
        <button type="button" style={styles.cancelBtn} onClick={() => setPhase({ kind: 'idle' })}>
          Cancel
        </button>
      </form>
    )
  }

  if (phase.kind === 'saving') {
    return <span style={styles.feedback}>Saving…</span>
  }

  return (
    <button
      type="button"
      style={styles.button}
      onClick={() => setPhase({ kind: 'editing', name: defaultName(participant, endedAt) })}
      title={isLoggedIn ? 'Save this deck to your account' : 'Save this deck to your browser'}
    >
      Save deck
    </button>
  )
}

const styles: Record<string, React.CSSProperties> = {
  button: {
    background: '#23233a',
    border: '1px solid #3a3a55',
    color: '#cdd0e6',
    borderRadius: 8,
    padding: '4px 10px',
    fontSize: 12,
    fontWeight: 600,
    cursor: 'pointer',
  },
  feedback: { color: '#5bd16e', fontSize: 12, fontWeight: 600 },
  editRow: { display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap' },
  input: {
    background: '#0f0f1a',
    border: '1px solid #3a3a55',
    color: '#e6e8f2',
    borderRadius: 8,
    padding: '4px 8px',
    fontSize: 12,
    minWidth: 0,
    flex: '1 1 120px',
  },
  saveBtn: {
    background: '#4456d6',
    border: '1px solid #5566e6',
    color: '#fff',
    borderRadius: 8,
    padding: '4px 10px',
    fontSize: 12,
    fontWeight: 600,
    cursor: 'pointer',
  },
  cancelBtn: {
    background: 'none',
    border: 'none',
    color: '#999',
    fontSize: 12,
    cursor: 'pointer',
  },
}
