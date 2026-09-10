/**
 * Deckbuilder toolbar control for browsing the signed-in user's cloud-saved decks. Saving is handled
 * by the deckbuilder's unified Save button (which routes to the account when signed in, or to
 * localStorage when not), so this control is purely the cloud "My decks" browser plus a sign-in
 * affordance when anonymous. Kept self-contained so the (large) DeckbuilderPage only provides a load
 * callback.
 */
import { useEffect, useState } from 'react'
import type React from 'react'
import {
  type DeckDetail,
  type DeckSummary,
  deleteDeck as apiDeleteDeck,
  getDeck,
  listDecks,
} from '@/api/account'
import { LoginModal } from '@/components/auth/LoginModal'
import { SavedDeckList, type SavedDeckListItem } from '@/components/deckbuilder/SavedDeckList'
import { useAuthStore } from '@/store/authStore'

interface AccountDeckBarProps {
  /** Apply a loaded cloud deck (with its id, so the builder can overwrite it on the next save). */
  onLoad: (detail: DeckDetail) => void
  className?: string
}

export function AccountDeckBar({ onLoad, className }: AccountDeckBarProps) {
  const status = useAuthStore((s) => s.status)
  const accountsEnabled = useAuthStore((s) => s.accountsEnabled)
  const init = useAuthStore((s) => s.init)
  const [loginOpen, setLoginOpen] = useState(false)
  const [listOpen, setListOpen] = useState(false)
  const [decks, setDecks] = useState<DeckSummary[]>([])

  useEffect(() => {
    if (status === 'idle') void init()
  }, [status, init])

  const refresh = () => {
    void listDecks().then(setDecks).catch(() => setDecks([]))
  }

  useEffect(() => {
    if (listOpen) refresh()
  }, [listOpen])

  const handleLoad = async (id: number) => {
    try {
      const detail = await getDeck(id)
      onLoad(detail)
      setListOpen(false)
    } catch {
      /* not found / not signed in — leave the builder as-is */
    }
  }

  const handleDelete = async (id: number) => {
    await apiDeleteDeck(id)
    setDecks((prev) => prev.filter((d) => d.id !== id))
  }

  // No accounts subsystem on this server → no cloud-deck controls at all.
  if (!accountsEnabled) return null

  if (status !== 'authenticated') {
    return (
      <div className={className}>
        <button type="button" style={styles.button} onClick={() => setLoginOpen(true)}>
          Sign in
        </button>
        <LoginModal open={loginOpen} onClose={() => setLoginOpen(false)} />
      </div>
    )
  }

  const items: SavedDeckListItem[] = decks.map((d) => ({
    key: `c:${d.id}`,
    name: d.name,
    online: true,
    ...(d.format ? { format: d.format } : {}),
  }))

  return (
    <div className={className} style={styles.wrap}>
      <button type="button" style={styles.button} onClick={() => setListOpen((v) => !v)}>
        My decks
      </button>
      {listOpen && (
        <div style={styles.popover}>
          <SavedDeckList
            decks={items}
            onOpen={(item) => void handleLoad(Number(item.key.slice(2)))}
            onDelete={(item) => void handleDelete(Number(item.key.slice(2)))}
          />
        </div>
      )}
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  wrap: { position: 'relative', display: 'inline-flex', gap: 8 },
  button: {
    padding: '6px 12px',
    borderRadius: 8,
    border: '1px solid #2a2a3e',
    backgroundColor: '#1a1a2e',
    color: '#fff',
    fontSize: 13,
    cursor: 'pointer',
  },
  popover: {
    position: 'absolute',
    top: '100%',
    right: 0,
    marginTop: 6,
    minWidth: 280,
    maxHeight: 320,
    overflowY: 'auto',
    backgroundColor: '#14141f',
    border: '1px solid #2a2a3e',
    borderRadius: 10,
    padding: 8,
    zIndex: 1500,
    boxShadow: '0 8px 24px rgba(0,0,0,0.5)',
  },
}
