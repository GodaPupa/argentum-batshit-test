/**
 * Landing page for a magic-link click (`/login/verify?token=…`). Exchanges the token for a session,
 * stores it, and bounces home. Shown only briefly; renders an error with a retry path on failure.
 */
import { useEffect, useRef, useState } from 'react'
import type React from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { verifyLogin } from '@/api/account'
import { useAuthStore } from '@/store/authStore'

export function LoginVerifyPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const setSession = useAuthStore((s) => s.setSession)
  const [error, setError] = useState<string | null>(null)
  const ran = useRef(false)

  useEffect(() => {
    if (ran.current) return
    ran.current = true
    const token = searchParams.get('token')
    if (!token) {
      setError('Missing sign-in token.')
      return
    }
    verifyLogin(token)
      .then((login) => {
        setSession(login)
        navigate('/', { replace: true })
      })
      .catch((err) => setError(err instanceof Error ? err.message : 'Sign-in failed.'))
  }, [searchParams, navigate, setSession])

  return (
    <div style={styles.wrap}>
      <div style={styles.card}>
        {error ? (
          <>
            <h2 style={styles.title}>Sign-in failed</h2>
            <p style={styles.text}>{error}</p>
            <button type="button" style={styles.button} onClick={() => navigate('/', { replace: true })}>
              Back home
            </button>
          </>
        ) : (
          <>
            <h2 style={styles.title}>Signing you in…</h2>
            <p style={styles.text}>One moment.</p>
          </>
        )}
      </div>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  wrap: {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#0a0a15',
    padding: 16,
  },
  card: {
    backgroundColor: '#1a1a2e',
    border: '1px solid #2a2a3e',
    borderRadius: 16,
    padding: 32,
    maxWidth: 380,
    textAlign: 'center',
    display: 'flex',
    flexDirection: 'column',
    gap: 12,
  },
  title: { margin: 0, color: '#fff', fontSize: 22 },
  text: { margin: 0, color: '#aaa', fontSize: 14 },
  button: {
    padding: '10px 14px',
    borderRadius: 8,
    border: 'none',
    backgroundColor: '#5b6ee1',
    color: '#fff',
    fontWeight: 600,
    cursor: 'pointer',
  },
}
