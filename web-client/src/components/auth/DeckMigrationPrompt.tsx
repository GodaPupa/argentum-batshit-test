/**
 * Landing-page nudge shown to a freshly signed-in user who still has decks saved only in this
 * browser (localStorage). Offers to copy them to their account, explaining why that's worth doing.
 *
 * "Browser-only" is determined by name: a local deck whose name isn't already among the user's cloud
 * decks is a migration candidate. Dismissed per session so it doesn't nag.
 */
import { useEffect, useMemo, useState } from 'react'
import type React from 'react'
import { listDecks, upsertDeckByName } from '@/api/account'
import { useDeckLibrary } from '@/store/deckLibrary'
import { savedDeckToShared } from '@/store/useSaveDeck'
import { useAuthStore } from '@/store/authStore'

const DISMISS_KEY = 'argentum-deck-migration-dismissed'

export function DeckMigrationPrompt() {
  const status = useAuthStore((s) => s.status)
  const localDecks = useDeckLibrary((s) => s.decks)
  const hydrate = useDeckLibrary((s) => s.hydrate)
  const hydrated = useDeckLibrary((s) => s.hydrated)

  const [cloudNames, setCloudNames] = useState<Set<string> | null>(null)
  const [dismissed, setDismissed] = useState(() => sessionStorage.getItem(DISMISS_KEY) === '1')
  const [state, setState] = useState<'idle' | 'saving' | 'done'>('idle')

  useEffect(() => {
    if (!hydrated) hydrate()
  }, [hydrated, hydrate])

  // Pull the user's cloud deck names once signed in, so we only offer to migrate the ones missing.
  useEffect(() => {
    if (status !== 'authenticated') return
    void listDecks()
      .then((decks) => setCloudNames(new Set(decks.map((d) => d.name.toLowerCase()))))
      .catch(() => setCloudNames(new Set()))
  }, [status])

  const candidates = useMemo(() => {
    if (!cloudNames) return []
    return localDecks.filter((d) => !cloudNames.has(d.name.toLowerCase()))
  }, [localDecks, cloudNames])

  if (status !== 'authenticated' || dismissed || candidates.length === 0) return null

  const dismiss = () => {
    sessionStorage.setItem(DISMISS_KEY, '1')
    setDismissed(true)
  }

  const migrate = async () => {
    setState('saving')
    for (const deck of candidates) {
      try {
        await upsertDeckByName(savedDeckToShared(deck))
      } catch {
        /* skip the ones that fail; the rest still migrate */
      }
    }
    setState('done')
    window.setTimeout(dismiss, 1800)
  }

  return (
    <div style={styles.banner}>
      <div style={styles.text}>
        {state === 'done' ? (
          <strong>Decks saved to your account ✓</strong>
        ) : (
          <>
            <strong>
              You have {candidates.length} deck{candidates.length === 1 ? '' : 's'} saved only in this
              browser.
            </strong>{' '}
            Save them to your account so they're backed up, available on any device you sign in from,
            and safe if this browser's storage gets cleared.
          </>
        )}
      </div>
      {state !== 'done' && (
        <div style={styles.actions}>
          <button type="button" style={styles.primary} disabled={state === 'saving'} onClick={() => void migrate()}>
            {state === 'saving' ? 'Saving…' : `Save ${candidates.length} to my account`}
          </button>
          <button type="button" style={styles.secondary} onClick={dismiss}>
            Not now
          </button>
        </div>
      )}
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  banner: {
    display: 'flex',
    flexWrap: 'wrap',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
    backgroundColor: 'rgba(91, 110, 225, 0.12)',
    border: '1px solid #3a3a6e',
    borderRadius: 12,
    padding: '12px 16px',
    margin: '0 0 12px',
  },
  text: { color: '#dcdcf0', fontSize: 13.5, lineHeight: 1.5, flex: '1 1 260px' },
  actions: { display: 'flex', gap: 8 },
  primary: {
    padding: '8px 14px',
    borderRadius: 8,
    border: 'none',
    backgroundColor: '#5b6ee1',
    color: '#fff',
    fontWeight: 600,
    fontSize: 13,
    cursor: 'pointer',
  },
  secondary: {
    padding: '8px 14px',
    borderRadius: 8,
    border: '1px solid #2a2a3e',
    backgroundColor: 'transparent',
    color: '#aaa',
    fontSize: 13,
    cursor: 'pointer',
  },
}
