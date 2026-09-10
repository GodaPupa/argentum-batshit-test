import React from 'react'
import { createPortal } from 'react-dom'
import type { ClientPlayerEffect } from '@/types'
import { useResponsiveContext } from '../board/shared'

/**
 * The Ring emblem (CR 701.54c) — a flavorful, gilded badge that replaces the generic
 * effect chip for The Ring. It shows how far the Ring has tempted the player as four
 * inscribed pips and, on hover, lights up exactly the cumulative abilities the emblem
 * currently grants (one per tempt, capped at four).
 *
 * The four ability texts never change (they're fixed by the rules), so they live here
 * as display copy. Everything dynamic — the tempt count and the Ring-bearer line —
 * comes from the server via `effect.progress` and `effect.description`.
 */

// CR 701.54c — gained in order, one per tempt, kept for the rest of the game.
const RING_ABILITIES: readonly string[] = [
  'Your Ring-bearer is legendary and can’t be blocked by creatures with greater power.',
  'Whenever your Ring-bearer attacks, draw a card, then discard a card.',
  'Whenever your Ring-bearer becomes blocked by a creature, that creature’s controller sacrifices it at end of combat.',
  'Whenever your Ring-bearer deals combat damage to a player, each opponent loses 3 life.',
]

const GOLD = '#f2d472'
const GOLD_DIM = 'rgba(212, 175, 55, 0.45)'

export function TheRingBadge({ effect }: { effect: ClientPlayerEffect }) {
  const responsive = useResponsiveContext()
  const badgeRef = React.useRef<HTMLDivElement>(null)
  // Anchor rect for the portal tooltip; null = not hovered. A portal is required
  // because the badge lives inside the HUD's overflow-clipped life-display column,
  // which would otherwise crop the tooltip (it opens upward, off the bottom edge).
  const [anchor, setAnchor] = React.useState<DOMRect | null>(null)

  const temptCount = effect.progress?.current ?? 0
  const total = effect.progress?.total ?? RING_ABILITIES.length
  const active = Math.min(temptCount, total)

  // The server packs the Ring-bearer sentence as the second clause of the description.
  const bearerLine = effect.description?.split('.').map(s => s.trim()).find(s => /ring-bearer/i.test(s))

  const fontSize = responsive.fontSize.small

  return (
    <div
      ref={badgeRef}
      style={{
        position: 'relative',
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        padding: responsive.isMobile ? '2px 7px' : '4px 9px',
        borderRadius: 5,
        cursor: 'help',
        fontSize,
        color: GOLD,
        // Dark Mordor backing with a faint inner gilt glow that intensifies with temptation.
        background: 'radial-gradient(circle at 30% 20%, rgba(60, 44, 8, 0.95) 0%, rgba(14, 10, 4, 0.97) 90%)',
        border: `1px solid ${GOLD_DIM}`,
        boxShadow: active >= total
          ? `0 0 9px 1px rgba(212, 175, 55, 0.55), inset 0 0 6px rgba(212, 175, 55, 0.3)`
          : `0 0 5px rgba(212, 175, 55, ${0.12 + active * 0.08}), inset 0 1px 1px rgba(0, 0, 0, 0.5)`,
      }}
      onMouseEnter={() => setAnchor(badgeRef.current?.getBoundingClientRect() ?? null)}
      onMouseLeave={() => setAnchor(null)}
    >
      <i
        className="ms ms-ability-the-ring-tempts-you"
        aria-hidden
        style={{
          fontSize: fontSize * 1.25,
          lineHeight: 1,
          color: GOLD,
          filter: 'drop-shadow(0 0 2px rgba(212, 175, 55, 0.8))',
        }}
      />
      <span style={{ fontWeight: 600, letterSpacing: '0.4px', whiteSpace: 'nowrap' }}>The Ring</span>
      {/* Four inscribed pips — one lights per tempt; the fourth completes the band. */}
      <span style={{ display: 'flex', gap: 3, alignItems: 'center' }}>
        {RING_ABILITIES.map((_, i) => {
          const lit = i < active
          return (
            <span
              key={i}
              style={{
                width: fontSize * 0.55,
                height: fontSize * 0.55,
                borderRadius: '50%',
                background: lit ? GOLD : 'transparent',
                border: `1px solid ${lit ? GOLD : GOLD_DIM}`,
                boxShadow: lit ? `0 0 4px rgba(212, 175, 55, 0.8)` : 'none',
              }}
            />
          )
        })}
      </span>

      {anchor && (
        <TheRingTooltip
          anchor={anchor}
          temptCount={temptCount}
          active={active}
          bearerLine={bearerLine}
        />
      )}
    </div>
  )
}

const TOOLTIP_WIDTH = 300
const VIEWPORT_PADDING = 8

function TheRingTooltip({
  anchor,
  temptCount,
  active,
  bearerLine,
}: {
  anchor: DOMRect
  temptCount: number
  active: number
  bearerLine: string | undefined
}) {
  // Horizontally centre on the badge, clamped into the viewport.
  const vw = window.innerWidth
  const rawLeft = anchor.left + anchor.width / 2 - TOOLTIP_WIDTH / 2
  const left = Math.max(VIEWPORT_PADDING, Math.min(rawLeft, vw - TOOLTIP_WIDTH - VIEWPORT_PADDING))
  // Open above the badge; fall back to below when there isn't room (the player's
  // own badge sits near the bottom edge, the opponent's near the top).
  const openAbove = anchor.top > window.innerHeight / 2

  return createPortal(
    <div
      style={{
        position: 'fixed',
        left,
        ...(openAbove
          ? { bottom: window.innerHeight - anchor.top + 6 }
          : { top: anchor.bottom + 6 }),
        width: TOOLTIP_WIDTH,
        padding: '10px 12px',
        borderRadius: 6,
        zIndex: 2500,
        pointerEvents: 'none',
        textAlign: 'left',
        background: 'linear-gradient(160deg, rgba(26, 18, 6, 0.98) 0%, rgba(8, 6, 3, 0.98) 100%)',
        border: `1px solid ${GOLD_DIM}`,
        boxShadow: '0 4px 18px rgba(0, 0, 0, 0.55), inset 0 0 14px rgba(212, 175, 55, 0.12)',
        color: '#e8dcc0',
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'baseline',
          justifyContent: 'space-between',
          gap: 8,
          marginBottom: 8,
          paddingBottom: 6,
          borderBottom: `1px solid ${GOLD_DIM}`,
        }}
      >
        <span style={{ color: GOLD, fontWeight: 700, fontSize: 14, letterSpacing: '0.5px' }}>
          The Ring
        </span>
        <span style={{ fontSize: 11, color: GOLD, opacity: 0.85, whiteSpace: 'nowrap' }}>
          Tempted {temptCount}{temptCount === 1 ? ' time' : ' times'}
        </span>
      </div>

      <ol style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 6 }}>
        {RING_ABILITIES.map((text, i) => {
          const lit = i < active
          return (
            <li
              key={i}
              style={{
                display: 'flex',
                gap: 7,
                fontSize: 11.5,
                lineHeight: 1.35,
                color: lit ? '#f5ead0' : '#7c7059',
                opacity: lit ? 1 : 0.65,
              }}
            >
              <span
                style={{
                  flex: '0 0 auto',
                  width: 13,
                  height: 13,
                  marginTop: 1,
                  borderRadius: '50%',
                  background: lit ? GOLD : 'transparent',
                  border: `1px solid ${lit ? GOLD : GOLD_DIM}`,
                  boxShadow: lit ? '0 0 4px rgba(212, 175, 55, 0.7)' : 'none',
                }}
              />
              <span style={lit ? { textShadow: '0 0 6px rgba(212, 175, 55, 0.25)' } : undefined}>
                {text}
              </span>
            </li>
          )
        })}
      </ol>

      {bearerLine && (
        <div
          style={{
            marginTop: 8,
            paddingTop: 6,
            borderTop: `1px solid ${GOLD_DIM}`,
            fontSize: 11.5,
            color: GOLD,
            fontStyle: 'italic',
          }}
        >
          {bearerLine}{/^.*[.!?]$/.test(bearerLine) ? '' : '.'}
        </div>
      )}
    </div>,
    document.body,
  )
}
