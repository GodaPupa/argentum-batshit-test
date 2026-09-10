/**
 * Read-only public profile for any player, at /u/:userId. Fetches the bundled
 * `/api/stats/users/{id}` response and renders the shared {@link StatsDashboard} (plus a compact
 * recent-games list). No auth required — profiles are public — and decklists are never exposed here
 * (the deck viewer stays on the owner's own profile).
 *
 * When the viewer is signed in and looking at someone else, a friend control is offered. The
 * relationship is derived entirely from the already-loaded friends store (friends / outgoing /
 * incoming lists are all keyed by account id, which equals this profile's userId), so no extra
 * request or endpoint is needed — sending or accepting reuses the same store actions as the friends
 * page.
 */
import { useEffect, useState } from 'react'
import type React from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { type PublicProfile, ProfileNotFoundError, fetchPublicProfile } from '@/api/account'
import { StatsDashboard } from '@/components/profile/StatsDashboard'
import { useAuthStore } from '@/store/authStore'
import { useFriendsStore } from '@/store/friendsStore'

export function PublicProfilePage() {
  const navigate = useNavigate()
  const { userId } = useParams<{ userId: string }>()
  const [profile, setProfile] = useState<PublicProfile | null>(null)
  const [state, setState] = useState<'loading' | 'ready' | 'notfound' | 'error'>('loading')

  useEffect(() => {
    if (!userId) return
    let live = true
    setState('loading')
    fetchPublicProfile(userId)
      .then((p) => {
        if (!live) return
        setProfile(p)
        setState('ready')
      })
      .catch((e) => {
        if (!live) return
        setState(e instanceof ProfileNotFoundError ? 'notfound' : 'error')
      })
    return () => {
      live = false
    }
  }, [userId])

  // Friend control — only meaningful for a signed-in viewer looking at someone else's profile.
  const authUser = useAuthStore((s) => s.user)
  const authStatus = useAuthStore((s) => s.status)
  const accountsEnabled = useAuthStore((s) => s.accountsEnabled)
  const initAuth = useAuthStore((s) => s.init)
  const friends = useFriendsStore((s) => s.friends)
  const incoming = useFriendsStore((s) => s.incoming)
  const outgoing = useFriendsStore((s) => s.outgoing)
  const loadFriends = useFriendsStore((s) => s.load)
  const sendRequest = useFriendsStore((s) => s.sendRequest)
  const acceptRequest = useFriendsStore((s) => s.accept)

  const [friendBusy, setFriendBusy] = useState(false)
  const [friendError, setFriendError] = useState<string | null>(null)

  useEffect(() => {
    if (authStatus === 'idle') void initAuth()
  }, [authStatus, initAuth])

  // Pull the friends/requests lists once we know we're signed in, so the relationship is current
  // even when landing straight on this page (they may already be loaded from sign-in).
  useEffect(() => {
    if (authStatus === 'authenticated') void loadFriends()
  }, [authStatus, loadFriends])

  const isOwnProfile = !!authUser && authUser.id === userId
  const canBefriend =
    authStatus === 'authenticated' && accountsEnabled && !isOwnProfile && !!userId
  const isFriend = friends.some((f) => f.accountId === userId)
  const incomingRequest = incoming.find((r) => r.accountId === userId)
  const outgoingPending = outgoing.some((r) => r.accountId === userId)

  const addFriend = async () => {
    if (!userId) return
    setFriendBusy(true)
    setFriendError(null)
    try {
      await sendRequest(userId)
    } catch (e) {
      setFriendError(e instanceof Error ? e.message : 'Could not send the request.')
    } finally {
      setFriendBusy(false)
    }
  }

  const acceptFriend = async () => {
    if (!incomingRequest) return
    setFriendBusy(true)
    setFriendError(null)
    try {
      await acceptRequest(incomingRequest.requestId)
    } catch (e) {
      setFriendError(e instanceof Error ? e.message : 'Could not accept the request.')
    } finally {
      setFriendBusy(false)
    }
  }

  return (
    <div style={styles.wrap}>
      <div style={styles.container}>
        <div style={styles.header}>
          <button type="button" style={styles.link} onClick={() => navigate(-1)}>
            ← Back
          </button>
          <button type="button" style={styles.link} onClick={() => navigate('/')}>
            Home
          </button>
        </div>

        {state === 'loading' && <p style={styles.muted}>Loading…</p>}
        {state === 'notfound' && (
          <>
            <h1 style={styles.title}>Player not found</h1>
            <p style={styles.muted}>This profile doesn’t exist or is no longer available.</p>
          </>
        )}
        {state === 'error' && (
          <>
            <h1 style={styles.title}>Couldn’t load profile</h1>
            <p style={styles.muted}>Something went wrong fetching this player’s stats.</p>
          </>
        )}
        {state === 'ready' && profile && (
          <>
            <h1 style={styles.title}>{profile.displayName}</h1>
            <p style={styles.muted}>Player profile</p>
            {canBefriend && (
              <div style={styles.friendRow}>
                {isFriend ? (
                  <span style={styles.friendBadge}>✓ Friends</span>
                ) : incomingRequest ? (
                  <button
                    type="button"
                    style={styles.primary}
                    disabled={friendBusy}
                    onClick={() => void acceptFriend()}
                  >
                    {friendBusy ? 'Accepting…' : 'Accept friend request'}
                  </button>
                ) : outgoingPending ? (
                  <span style={styles.pending}>Friend request sent</span>
                ) : (
                  <button
                    type="button"
                    style={styles.primary}
                    disabled={friendBusy}
                    onClick={() => void addFriend()}
                  >
                    {friendBusy ? 'Sending…' : '+ Add friend'}
                  </button>
                )}
                {friendError && <span style={styles.friendError}>{friendError}</span>}
              </div>
            )}
            <StatsDashboard
              stats={profile.stats}
              ratings={profile.ratings}
              ratingHistory={profile.ratingHistory}
              colors={profile.colors}
              cardTypes={profile.cardTypes}
              curve={profile.curve}
              creatureTypes={profile.creatureTypes}
              modes={profile.modes}
              sets={profile.sets}
              topCards={profile.topCards}
              opponents={profile.opponents}
              tournaments={profile.tournaments}
              recentGames={profile.recentGames}
            />
          </>
        )}
      </div>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  wrap: { height: '100vh', overflowY: 'auto', backgroundColor: '#0a0a15', padding: '32px 16px' },
  container: { maxWidth: 960, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 14 },
  header: { display: 'flex', justifyContent: 'space-between' },
  link: { background: 'none', border: 'none', color: '#8b9bff', cursor: 'pointer', fontSize: 14, padding: 0 },
  title: { margin: '4px 0 0', color: '#fff', fontSize: 28 },
  muted: { margin: 0, color: '#888', fontSize: 14 },
  friendRow: { display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap', marginTop: 2 },
  primary: {
    alignSelf: 'flex-start',
    padding: '9px 16px',
    borderRadius: 8,
    border: 'none',
    backgroundColor: '#5b6ee1',
    color: '#fff',
    fontWeight: 600,
    fontSize: 14,
    cursor: 'pointer',
  },
  friendBadge: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 6,
    padding: '7px 14px',
    borderRadius: 8,
    border: '1px solid #2f5540',
    backgroundColor: 'rgba(91,209,110,0.10)',
    color: '#5bd16e',
    fontWeight: 600,
    fontSize: 14,
  },
  pending: {
    display: 'inline-flex',
    alignItems: 'center',
    padding: '7px 14px',
    borderRadius: 8,
    border: '1px solid #2a2a3e',
    backgroundColor: '#14141f',
    color: '#888',
    fontSize: 14,
  },
  friendError: { color: '#ff6b6b', fontSize: 13 },
}
