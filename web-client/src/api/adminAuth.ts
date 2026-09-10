/**
 * Auth for the admin dashboard's REST calls. There are two ways to be an admin (mirrors the server's
 * AdminAuthService): the bootstrap `X-Admin-Password`, or a signed-in account that has been promoted to
 * admin (its normal `Authorization: Bearer` token). The whole dashboard threads an `AdminAuth` value —
 * the bootstrap password when one was entered, or `null` to fall back to the signed-in account's token.
 */
import { getAuthToken } from './account'

/** The bootstrap password if the user logged in with it, or null to use the signed-in account's token. */
export type AdminAuth = string | null

/** Headers for an admin request: the bootstrap password if present, else the account's Bearer token. */
export function adminAuthHeaders(auth: AdminAuth): Record<string, string> {
  if (auth) return { 'X-Admin-Password': auth }
  const token = getAuthToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}
