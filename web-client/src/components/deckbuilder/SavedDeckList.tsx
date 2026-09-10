/**
 * Shared presentational list of a user's saved decks, reused by the profile page and the deckbuilder's
 * "My decks" popover so the two stay in visual lockstep (one place to restyle a deck row).
 *
 * Each row carries an {@link SavedDeckListItem.online} flag and shows a badge for it, so a user can
 * tell at a glance which decks are backed up to their account (Online) and which live only in this
 * browser (Browser only) and would be lost if storage is cleared.
 */
import type React from 'react'

export interface SavedDeckListItem {
  /** Stable React key — callers prefix by source (e.g. `c:<id>` cloud, `l:<id>` local). */
  readonly key: string
  readonly name: string
  readonly format?: string
  /** True = backed up in the account (cloud); false = browser-only (localStorage). */
  readonly online: boolean
}

interface SavedDeckListProps {
  decks: readonly SavedDeckListItem[]
  /** Open / load a deck. */
  onOpen: (item: SavedDeckListItem) => void
  /** Delete a deck. Omit to hide the delete affordance. */
  onDelete?: (item: SavedDeckListItem) => void
  /** Copy shown when there are no decks. */
  emptyText?: string
}

export function SavedDeckList({ decks, onOpen, onDelete, emptyText }: SavedDeckListProps) {
  if (decks.length === 0) {
    return <div style={styles.empty}>{emptyText ?? 'No saved decks yet.'}</div>
  }
  return (
    <ul style={styles.list}>
      {decks.map((deck) => (
        <li key={deck.key} style={styles.item}>
          <button type="button" style={styles.name} onClick={() => onOpen(deck)}>
            <span style={styles.nameText}>{deck.name}</span>
            {deck.format ? <span style={styles.format}> · {deck.format}</span> : null}
          </button>
          <StorageBadge online={deck.online} />
          {onDelete ? (
            <button
              type="button"
              style={styles.delete}
              title="Delete deck"
              aria-label={`Delete ${deck.name}`}
              onClick={() => onDelete(deck)}
            >
              ×
            </button>
          ) : null}
        </li>
      ))}
    </ul>
  )
}

function StorageBadge({ online }: { online: boolean }) {
  const style = online ? styles.badgeOnline : styles.badgeLocal
  return (
    <span style={{ ...styles.badge, ...style }} title={online ? 'Backed up to your account' : 'Saved only in this browser'}>
      <span style={{ ...styles.badgeDot, background: online ? '#3ecf7a' : '#8a8a99' }} aria-hidden="true" />
      {online ? 'Online' : 'Browser only'}
    </span>
  )
}

const styles: Record<string, React.CSSProperties> = {
  list: { listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 6 },
  item: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    backgroundColor: '#14141f',
    border: '1px solid #2a2a3e',
    borderRadius: 10,
    padding: '8px 12px',
  },
  name: {
    flex: 1,
    minWidth: 0,
    display: 'flex',
    alignItems: 'baseline',
    gap: 2,
    background: 'none',
    border: 'none',
    color: '#fff',
    textAlign: 'left',
    cursor: 'pointer',
    fontSize: 14,
    padding: 0,
  },
  nameText: { overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' },
  format: { color: '#888', fontSize: 12, flexShrink: 0 },
  badge: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 5,
    flexShrink: 0,
    fontSize: 11,
    fontWeight: 600,
    letterSpacing: 0.2,
    padding: '3px 8px',
    borderRadius: 999,
    border: '1px solid',
  },
  badgeOnline: { color: '#9be8bd', borderColor: 'rgba(62, 207, 122, 0.35)', backgroundColor: 'rgba(62, 207, 122, 0.1)' },
  badgeLocal: { color: '#b8b8c4', borderColor: '#3a3a4e', backgroundColor: 'rgba(255, 255, 255, 0.04)' },
  badgeDot: { width: 6, height: 6, borderRadius: '50%' },
  delete: { background: 'none', border: 'none', color: '#ff6b6b', cursor: 'pointer', fontSize: 18, lineHeight: 1, padding: '0 2px', flexShrink: 0 },
  empty: { color: '#888', fontSize: 13, padding: 8 },
}
