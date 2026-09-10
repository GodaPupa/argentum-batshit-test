/**
 * Landing-page reminder shown to guests (not signed in) listing what a free account adds:
 * cloud-saved decks, friends, and ranked play with stats. Points at the existing magic-link
 * login flow via the parent's LoginModal.
 *
 * Dismissal is permanent (localStorage) — the AuthWidget's "Log in" pill and the name-entry
 * nudge remain as quieter entry points afterwards.
 */
import { useState } from 'react'
import type React from 'react'
import { useAuthStore } from '@/store/authStore'

const DISMISS_KEY = 'argentum-account-benefits-dismissed'

const BENEFITS: { icon: string; text: string }[] = [
  { icon: '☁️', text: 'Save your decks in the cloud, on any device' },
  { icon: '👥', text: 'Add friends and see when they’re online' },
  { icon: '🏆', text: 'Ranked play with ELO and win/loss stats' },
  { icon: '🎬', text: 'Rewatch and share your game replays' },
]

export function AccountBenefitsCallout({ onCreateAccount }: { onCreateAccount: () => void }) {
  const status = useAuthStore((s) => s.status)
  const accountsEnabled = useAuthStore((s) => s.accountsEnabled)
  const [dismissed, setDismissed] = useState(() => localStorage.getItem(DISMISS_KEY) === '1')

  // Only render once auth has resolved to "not signed in" — no flash for logged-in users.
  if (!accountsEnabled || status !== 'anonymous' || dismissed) return null

  const dismiss = () => {
    localStorage.setItem(DISMISS_KEY, '1')
    setDismissed(true)
  }

  return (
    <div style={styles.banner}>
      <div style={styles.text}>
        <strong>Playing as a guest.</strong> Create a free account — one magic link, no password —
        and get:
        <ul style={styles.list}>
          {BENEFITS.map((b) => (
            <li key={b.text} style={styles.listItem}>
              <span style={styles.icon} aria-hidden="true">{b.icon}</span>
              {b.text}
            </li>
          ))}
        </ul>
      </div>
      <div style={styles.actions}>
        <button type="button" style={styles.primary} onClick={onCreateAccount}>
          Create free account
        </button>
        <button type="button" style={styles.secondary} onClick={dismiss}>
          Not now
        </button>
      </div>
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
    margin: '12px 0 0',
    textAlign: 'left',
  },
  text: { color: '#dcdcf0', fontSize: 13.5, lineHeight: 1.5, flex: '1 1 260px' },
  list: { margin: '6px 0 0', padding: 0, listStyle: 'none' },
  listItem: { display: 'flex', alignItems: 'baseline', gap: 8, marginTop: 2 },
  icon: { width: 18, textAlign: 'center', flexShrink: 0 },
  actions: { display: 'flex', flexDirection: 'column', gap: 8 },
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
