import styles from './SetIcon.module.css'

/**
 * Renders a Magic: The Gathering set symbol from the Keyrune icon font
 * (https://keyrune.andrewgioia.com). Keyrune exposes one glyph per set under
 * the class `ss ss-<lowercase set code>` (e.g. `ss ss-blb` for Bloomburrow).
 *
 * Sets Keyrune doesn't cover simply render no glyph (zero-width), so this
 * degrades gracefully for any unknown code — no broken-image box.
 */
export function SetIcon({
  code,
  className,
  title,
}: {
  /** Set code as the server reports it, e.g. `BLB`, `ONS`, `por`. Case-insensitive. */
  code: string
  className?: string | undefined
  /** Tooltip text; defaults to no tooltip so callers can omit it. */
  title?: string | undefined
}) {
  const classes = ['ss', `ss-${code.toLowerCase()}`, styles.setIcon, className]
    .filter(Boolean)
    .join(' ')
  return <i className={classes} aria-hidden {...(title ? { title } : {})} />
}
