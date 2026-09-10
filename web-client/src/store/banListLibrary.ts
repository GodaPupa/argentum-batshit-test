/**
 * Ban list library — localStorage-backed list of saved tournament ban lists, scoped per set.
 *
 * Mirrors {@link ./deckLibrary deckLibrary}: a tiny standalone Zustand store with no WebSocket
 * integration, so the lobby ban-list editor can save/load named ban lists the same way the
 * deckbuilder saves decks. A ban list is just a named bag of oracle card names tied to the set
 * code it was built for (so the editor can offer "your ban lists for this set").
 *
 * Storage is a versioned envelope so the shape can migrate later without losing users' lists.
 *
 * ## Storage versions
 * - **v1** — `{ id, name, setCode, cards: string[], updatedAt }`.
 */
import { create } from 'zustand'

export interface SavedBanList {
  id: string
  name: string
  /** The set code this ban list was built for (e.g. "BLB"). Drives "ban lists for this set". */
  setCode: string
  /** Oracle card names to exclude from boosters. */
  cards: readonly string[]
  updatedAt: number
}

interface BanListStorageV1 {
  version: 1
  banLists: SavedBanList[]
}

type BanListStorage = BanListStorageV1

const STORAGE_KEY = 'argentum.banlists'
const STORAGE_VERSION = 1

interface BanListLibraryState {
  banLists: SavedBanList[]
  hydrated: boolean

  hydrate: () => void
  saveBanList: (input: Omit<SavedBanList, 'id' | 'updatedAt'> & { id?: string }) => SavedBanList
  deleteBanList: (id: string) => void
  renameBanList: (id: string, newName: string) => void
  getBanList: (id: string) => SavedBanList | undefined
  /** All saved ban lists for a given set code, newest first. */
  banListsForSet: (setCode: string) => SavedBanList[]
}

function loadFromStorage(): SavedBanList[] {
  if (typeof window === 'undefined') return []
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as BanListStorage
    if (!parsed || !Array.isArray(parsed.banLists)) return []
    if (parsed.version === STORAGE_VERSION) return parsed.banLists
    return []
  } catch {
    return []
  }
}

function persist(banLists: SavedBanList[]) {
  if (typeof window === 'undefined') return
  const envelope: BanListStorageV1 = { version: STORAGE_VERSION, banLists }
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(envelope))
}

function generateId(): string {
  return `banlist-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

export const useBanListLibrary = create<BanListLibraryState>((set, get) => ({
  banLists: [],
  hydrated: false,

  hydrate: () => {
    if (get().hydrated) return
    set({ banLists: loadFromStorage(), hydrated: true })
  },

  saveBanList: (input) => {
    const now = Date.now()
    const id = input.id ?? generateId()
    const existing = input.id ? get().banLists.find((b) => b.id === input.id) : undefined
    const saved: SavedBanList = {
      id,
      name: input.name,
      setCode: input.setCode,
      cards: [...input.cards],
      updatedAt: now,
    }
    const banLists = existing
      ? get().banLists.map((b) => (b.id === id ? saved : b))
      : [...get().banLists, saved]
    persist(banLists)
    set({ banLists })
    return saved
  },

  deleteBanList: (id) => {
    const banLists = get().banLists.filter((b) => b.id !== id)
    persist(banLists)
    set({ banLists })
  },

  renameBanList: (id, newName) => {
    const banLists = get().banLists.map((b) =>
      b.id === id ? { ...b, name: newName, updatedAt: Date.now() } : b
    )
    persist(banLists)
    set({ banLists })
  },

  getBanList: (id) => get().banLists.find((b) => b.id === id),

  banListsForSet: (setCode) =>
    get()
      .banLists.filter((b) => b.setCode === setCode)
      .sort((a, b) => b.updatedAt - a.updatedAt),
}))
