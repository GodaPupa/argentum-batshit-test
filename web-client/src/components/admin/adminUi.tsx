/**
 * Shared chrome and primitives for the admin dashboard's sub-screens (hub, stats, players). The whole
 * app runs inside `#root { height:100%; overflow:hidden }` (the game board is a fixed full-screen
 * surface), so a normal tall page is clipped with no way to scroll. Every admin screen therefore makes
 * itself its OWN scroll container via {@link AdminScreen} (`height:100vh; overflowY:auto`) rather than
 * relying on document scroll — this is the fix for the dashboard running off-screen.
 */
import type React from 'react'

export const adminTheme = {
  bg: '#0a0a12',
  panel: '#13131f',
  panelAlt: '#171724',
  border: '#23233a',
  borderSoft: '#1c1c2c',
  accent: '#8b9bff',
  accentSolid: '#5b6ee1',
  text: '#f1f1f6',
  textSecondary: '#a6a6bd',
  textMuted: '#6c6c82',
  good: '#5bd16e',
  bad: '#e15b6e',
} as const

/**
 * A full-height, self-scrolling admin screen with a sticky header (back affordance + title + optional
 * subtitle + right-aligned slot) and a centered content column.
 */
export function AdminScreen({
  title,
  subtitle,
  onBack,
  backLabel = '← Back',
  right,
  children,
}: {
  title: string
  subtitle?: string | undefined
  onBack?: (() => void) | undefined
  backLabel?: string
  right?: React.ReactNode
  children: React.ReactNode
}) {
  return (
    <div style={shell.screen}>
      <div style={shell.topbar}>
        <div style={shell.topbarInner}>
          <div style={shell.titleBlock}>
            {onBack && (
              <button type="button" style={shell.backBtn} onClick={onBack}>
                {backLabel}
              </button>
            )}
            <div>
              <h1 style={shell.title}>{title}</h1>
              {subtitle && <p style={shell.subtitle}>{subtitle}</p>}
            </div>
          </div>
          {right && <div style={shell.right}>{right}</div>}
        </div>
      </div>
      <div style={shell.container}>{children}</div>
    </div>
  )
}

export function Panel({
  title,
  subtitle,
  action,
  children,
  span2,
}: {
  title?: string
  subtitle?: string
  action?: React.ReactNode
  children: React.ReactNode
  span2?: boolean
}) {
  return (
    <div style={span2 ? { ...panelStyle.panel, gridColumn: '1 / -1' } : panelStyle.panel}>
      {(title || action) && (
        <div style={panelStyle.panelHead}>
          <div style={panelStyle.panelTitleBlock}>
            {title && <h2 style={panelStyle.panelTitle}>{title}</h2>}
            {subtitle && <p style={panelStyle.panelSubtitle}>{subtitle}</p>}
          </div>
          {action}
        </div>
      )}
      {children}
    </div>
  )
}

export function StatCard({ label, value, accent }: { label: string; value: React.ReactNode; accent?: boolean }) {
  return (
    <div style={panelStyle.metric}>
      <div style={accent ? { ...panelStyle.metricValue, color: adminTheme.accent } : panelStyle.metricValue}>
        {value ?? '—'}
      </div>
      <div style={panelStyle.metricLabel}>{label}</div>
    </div>
  )
}

export function Table({ head, children }: { head: React.ReactNode[]; children: React.ReactNode }) {
  return (
    <div style={tableStyle.wrap}>
      <table style={tableStyle.table}>
        <thead>
          <tr>
            {head.map((h, i) => (
              <th key={i} style={i === 0 ? tableStyle.th : tableStyle.thNum}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  )
}

export const chartTooltipStyle: React.CSSProperties = {
  backgroundColor: adminTheme.panelAlt,
  border: `1px solid ${adminTheme.border}`,
  borderRadius: 8,
  color: adminTheme.text,
  fontSize: 12,
}

const shell: Record<string, React.CSSProperties> = {
  screen: {
    height: '100vh',
    overflowY: 'auto',
    overflowX: 'hidden',
    backgroundColor: adminTheme.bg,
    color: adminTheme.text,
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
  },
  topbar: {
    position: 'sticky',
    top: 0,
    zIndex: 10,
    backgroundColor: 'rgba(10,10,18,0.86)',
    backdropFilter: 'blur(10px)',
    WebkitBackdropFilter: 'blur(10px)',
    borderBottom: `1px solid ${adminTheme.border}`,
  },
  topbarInner: {
    maxWidth: 1040,
    margin: '0 auto',
    padding: '14px 20px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  titleBlock: { display: 'flex', alignItems: 'center', gap: 14, minWidth: 0 },
  backBtn: {
    flexShrink: 0,
    background: 'none',
    border: `1px solid ${adminTheme.border}`,
    color: adminTheme.accent,
    cursor: 'pointer',
    fontSize: 13,
    borderRadius: 8,
    padding: '7px 12px',
  },
  title: { margin: 0, color: adminTheme.text, fontSize: 20, fontWeight: 700, letterSpacing: -0.2 },
  subtitle: { margin: '2px 0 0', color: adminTheme.textMuted, fontSize: 13 },
  right: { display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 },
  container: {
    maxWidth: 1040,
    margin: '0 auto',
    padding: '24px 20px 64px',
    display: 'flex',
    flexDirection: 'column',
    gap: 18,
  },
}

const panelStyle: Record<string, React.CSSProperties> = {
  panel: {
    backgroundColor: adminTheme.panel,
    border: `1px solid ${adminTheme.border}`,
    borderRadius: 14,
    padding: 18,
  },
  panelHead: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
    marginBottom: 14,
  },
  panelTitleBlock: { display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 },
  panelTitle: { margin: 0, color: adminTheme.text, fontSize: 15, fontWeight: 600 },
  panelSubtitle: { margin: 0, color: adminTheme.textMuted, fontSize: 12 },
  metric: {
    flex: '1 1 130px',
    backgroundColor: adminTheme.panel,
    border: `1px solid ${adminTheme.border}`,
    borderRadius: 14,
    padding: '16px 14px',
    textAlign: 'center',
  },
  metricValue: { color: adminTheme.text, fontSize: 26, fontWeight: 700, lineHeight: 1.1 },
  metricLabel: {
    color: adminTheme.textMuted,
    fontSize: 11,
    marginTop: 6,
    textTransform: 'uppercase',
    letterSpacing: 0.6,
  },
}

const tableStyle: Record<string, React.CSSProperties> = {
  wrap: { overflowX: 'auto' },
  table: { width: '100%', borderCollapse: 'collapse', fontSize: 13 },
  th: {
    textAlign: 'left',
    color: adminTheme.textMuted,
    fontWeight: 600,
    padding: '8px 10px',
    borderBottom: `1px solid ${adminTheme.border}`,
    whiteSpace: 'nowrap',
  },
  thNum: {
    textAlign: 'right',
    color: adminTheme.textMuted,
    fontWeight: 600,
    padding: '8px 10px',
    borderBottom: `1px solid ${adminTheme.border}`,
    whiteSpace: 'nowrap',
  },
}

export const cellStyle: Record<string, React.CSSProperties> = {
  td: { textAlign: 'left', color: adminTheme.textSecondary, padding: '8px 10px', borderBottom: `1px solid ${adminTheme.borderSoft}` },
  tdNum: { textAlign: 'right', color: adminTheme.textSecondary, padding: '8px 10px', borderBottom: `1px solid ${adminTheme.borderSoft}` },
  muted: { color: adminTheme.textMuted, fontSize: 13, margin: 0 },
}
