/**
 * Magic-link sign-in modal: enter an email, receive a one-time sign-in link. No passwords.
 * Styled inline to match the app's other overlays (e.g. JoinQrModal).
 */
import { useEffect, useState } from 'react'
import type React from 'react'
import { requestLogin } from '@/api/account'

interface LoginModalProps {
  open: boolean
  onClose: () => void
}

export function LoginModal({ open, onClose }: LoginModalProps) {
  const [email, setEmail] = useState('')
  const [status, setStatus] = useState<'idle' | 'sending' | 'sent'>('idle')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  // Reset transient state whenever the modal is (re)opened.
  useEffect(() => {
    if (open) {
      setStatus('idle')
      setError(null)
    }
  }, [open])

  if (!open) return null

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!email.trim()) return
    setStatus('sending')
    setError(null)
    try {
      await requestLogin(email.trim())
      setStatus('sent')
    } catch (err) {
      setStatus('idle')
      setError(err instanceof Error ? err.message : 'Something went wrong')
    }
  }

  return (
    <div style={styles.backdrop} onClick={onClose} role="dialog" aria-modal="true">
      <div style={styles.panel} onClick={(e) => e.stopPropagation()}>
        <button type="button" onClick={onClose} style={styles.close} aria-label="Close">
          ×
        </button>
        {status === 'sent' ? (
          <>
            <h2 style={styles.title}>Check your email</h2>
            <p style={styles.subtitle}>
              We sent a sign-in link to <strong>{email}</strong>. Open it on this device to finish
              signing in. The link expires shortly and can be used once.
            </p>
            <button type="button" onClick={onClose} style={styles.primary}>
              Done
            </button>
          </>
        ) : (
          <>
            <h2 style={styles.title}>Sign in</h2>
            <p style={styles.subtitle}>
              Enter your email and we'll send you a one-time sign-in link — no password needed.
            </p>
            <form onSubmit={submit} style={styles.form}>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
                autoFocus
                required
                style={styles.input}
              />
              {error && <div style={styles.error}>{error}</div>}
              <button type="submit" disabled={status === 'sending'} style={styles.primary}>
                {status === 'sending' ? 'Sending…' : 'Send sign-in link'}
              </button>
            </form>
          </>
        )}
      </div>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  backdrop: {
    position: 'fixed',
    inset: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.78)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 3000,
    padding: 16,
  },
  panel: {
    position: 'relative',
    backgroundColor: '#1a1a2e',
    border: '1px solid #2a2a3e',
    borderRadius: 16,
    padding: '28px 28px 24px',
    width: '100%',
    maxWidth: 360,
    display: 'flex',
    flexDirection: 'column',
    gap: 12,
  },
  close: {
    position: 'absolute',
    top: 10,
    right: 14,
    background: 'none',
    border: 'none',
    color: '#888',
    fontSize: 24,
    cursor: 'pointer',
    lineHeight: 1,
  },
  title: { margin: 0, color: '#fff', fontSize: 22 },
  subtitle: { margin: 0, color: '#aaa', fontSize: 14, lineHeight: 1.5 },
  form: { display: 'flex', flexDirection: 'column', gap: 12, marginTop: 4 },
  input: {
    padding: '10px 12px',
    borderRadius: 8,
    border: '1px solid #2a2a3e',
    backgroundColor: '#0f0f1a',
    color: '#fff',
    fontSize: 15,
  },
  primary: {
    padding: '10px 14px',
    borderRadius: 8,
    border: 'none',
    backgroundColor: '#5b6ee1',
    color: '#fff',
    fontSize: 15,
    fontWeight: 600,
    cursor: 'pointer',
  },
  error: { color: '#ff6b6b', fontSize: 13 },
}
