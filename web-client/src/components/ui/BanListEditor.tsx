/**
 * BanListEditor — host control for a tournament's booster ban list.
 *
 * A ban list is a set of oracle card names excluded from every booster pack the lobby generates
 * (see `BoosterGenerator.boosterPool`). The host adds cards by searching the catalog filtered to
 * the selected sets, removes them via chips, and can save/load named ban lists per set the same
 * way decks are saved (localStorage via {@link useBanListLibrary}).
 *
 * Server-authoritative: this component only edits the list and hands it back via [onChange] (which
 * the lobby wires to `updateLobbySettings({ bannedCardNames })`). It never decides what a booster
 * contains — the server filters the pool.
 */
import { useEffect, useMemo, useRef, useState } from 'react'
import type { CardSummary } from '@/components/deckbuilder/cardFilter'
import { useBanListLibrary } from '@/store/banListLibrary'
import styles from './BanListEditor.module.css'

interface BanListEditorProps {
  /** Currently selected set codes for the lobby. */
  readonly setCodes: readonly string[]
  /** Current ban list (oracle card names). */
  readonly bannedCardNames: readonly string[]
  /** Called with the full new ban list whenever the host adds/removes/loads cards. */
  readonly onChange: (names: string[]) => void
}

/** Module-level cache so re-opening the panel doesn't refetch the whole catalog. */
let cardCache: CardSummary[] | null = null

export function BanListEditor({ setCodes, bannedCardNames, onChange }: BanListEditorProps) {
  const [expanded, setExpanded] = useState(false)
  const [allCards, setAllCards] = useState<CardSummary[]>(cardCache ?? [])
  const [query, setQuery] = useState('')
  const [saveName, setSaveName] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  const { banLists, hydrate, saveBanList, deleteBanList, banListsForSet } = useBanListLibrary()

  useEffect(() => {
    hydrate()
  }, [hydrate])

  // Fetch the catalog once (cached across mounts).
  useEffect(() => {
    if (cardCache) return
    let cancelled = false
    fetch('/api/cards')
      .then((r) => (r.ok ? r.json() : []))
      .then((list: CardSummary[]) => {
        if (cancelled) return
        cardCache = list
        setAllCards(list)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [])

  const setCodeSet = useMemo(() => new Set(setCodes), [setCodes])
  const banned = useMemo(() => new Set(bannedCardNames.map((n) => n.toLowerCase())), [bannedCardNames])

  /** Catalog cards that belong to any selected set, excluding basic lands. */
  const candidates = useMemo(() => {
    if (setCodeSet.size === 0) return []
    return allCards
      .filter((c) => !c.basicLand)
      .filter((c) => {
        if (c.setCode && setCodeSet.has(c.setCode)) return true
        return (c.printingSetCodes ?? []).some((s) => setCodeSet.has(s))
      })
  }, [allCards, setCodeSet])

  const results = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return []
    return candidates
      .filter((c) => !banned.has(c.name.toLowerCase()) && c.name.toLowerCase().includes(q))
      .sort((a, b) => a.name.localeCompare(b.name))
      .slice(0, 25)
  }, [candidates, banned, query])

  const sortedBanned = useMemo(
    () => [...bannedCardNames].sort((a, b) => a.localeCompare(b)),
    [bannedCardNames],
  )

  const primarySet = setCodes[0]
  const savedForSet = primarySet ? banListsForSet(primarySet) : []

  function addCard(name: string) {
    if (bannedCardNames.some((n) => n.toLowerCase() === name.toLowerCase())) return
    onChange([...bannedCardNames, name])
    setQuery('')
    inputRef.current?.focus()
  }

  function removeCard(name: string) {
    onChange(bannedCardNames.filter((n) => n !== name))
  }

  function handleSave() {
    const name = saveName.trim()
    if (!name || !primarySet) return
    // Overwrite an existing list of the same name for this set, else create a new one.
    const existing = savedForSet.find((b) => b.name.toLowerCase() === name.toLowerCase())
    saveBanList({
      ...(existing ? { id: existing.id } : {}),
      name,
      setCode: primarySet,
      cards: bannedCardNames,
    })
    setSaveName('')
  }

  function handleLoad(id: string) {
    const list = banLists.find((b) => b.id === id)
    if (list) onChange([...list.cards])
  }

  return (
    <div className={styles.container}>
      <button
        type="button"
        className={styles.header}
        onClick={() => setExpanded((e) => !e)}
        aria-expanded={expanded}
      >
        <span className={styles.headerLabel}>
          Banned cards
          {bannedCardNames.length > 0 && (
            <span className={styles.countBadge}>{bannedCardNames.length}</span>
          )}
        </span>
        <span className={styles.chevron}>{expanded ? '▾' : '▸'}</span>
      </button>

      {expanded && (
        <div className={styles.body}>
          <p className={styles.caption}>
            Cards on this list never appear in generated boosters. They don&apos;t affect
            Premade-Decks tournaments.
          </p>

          {setCodes.length === 0 ? (
            <p className={styles.empty}>Select a set first to search for cards to ban.</p>
          ) : (
            <div className={styles.searchWrap}>
              <input
                ref={inputRef}
                type="text"
                className={styles.input}
                placeholder="Search cards to ban…"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && results[0]) addCard(results[0].name)
                }}
              />
              {results.length > 0 && (
                <ul className={styles.results}>
                  {results.map((c) => (
                    <li key={c.name}>
                      <button type="button" className={styles.resultItem} onClick={() => addCard(c.name)}>
                        <span className={styles.resultName}>{c.name}</span>
                        {c.setCode && <span className={styles.resultSet}>{c.setCode}</span>}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}

          {sortedBanned.length > 0 ? (
            <div className={styles.chips}>
              {sortedBanned.map((name) => (
                <span key={name} className={styles.chip}>
                  {name}
                  <button
                    type="button"
                    className={styles.chipRemove}
                    onClick={() => removeCard(name)}
                    aria-label={`Remove ${name}`}
                  >
                    ×
                  </button>
                </span>
              ))}
              <button type="button" className={styles.clearAll} onClick={() => onChange([])}>
                Clear all
              </button>
            </div>
          ) : (
            <p className={styles.empty}>No cards banned.</p>
          )}

          {/* Save / load named ban lists per set, mirroring the deck library. */}
          {primarySet && (
            <div className={styles.savedSection}>
              <div className={styles.saveRow}>
                <input
                  type="text"
                  className={styles.input}
                  placeholder={`Save ban list for ${primarySet}…`}
                  value={saveName}
                  onChange={(e) => setSaveName(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') handleSave()
                  }}
                />
                <button
                  type="button"
                  className={styles.saveButton}
                  disabled={!saveName.trim() || bannedCardNames.length === 0}
                  onClick={handleSave}
                >
                  Save
                </button>
              </div>
              {savedForSet.length > 0 && (
                <ul className={styles.savedList}>
                  {savedForSet.map((list) => (
                    <li key={list.id} className={styles.savedItem}>
                      <button
                        type="button"
                        className={styles.savedLoad}
                        title={list.cards.join(', ')}
                        onClick={() => handleLoad(list.id)}
                      >
                        {list.name}
                        <span className={styles.savedCount}>{list.cards.length}</span>
                      </button>
                      <button
                        type="button"
                        className={styles.savedDelete}
                        aria-label={`Delete ${list.name}`}
                        onClick={() => deleteBanList(list.id)}
                      >
                        ×
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
