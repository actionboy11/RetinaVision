import type { CurrentUser, UserRole } from '@/types/auth'

export const TOKEN_STORAGE_KEY = 'retina_token'

export const USER_STORAGE_KEY = 'retina_user'

export const getToken = (): string => {
  return localStorage.getItem(TOKEN_STORAGE_KEY) || ''
}

export const setToken = (token: string) => {
  localStorage.setItem(TOKEN_STORAGE_KEY, token)
}

export const removeToken = () => {
  localStorage.removeItem(TOKEN_STORAGE_KEY)
}

interface JwtIdentity {
  userId: number
  username: string
  roleCode: UserRole
}

const decodeBase64Url = (value: string): string => {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  const padding = '='.repeat((4 - (normalized.length % 4)) % 4)
  const binary = window.atob(normalized + padding)
  const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0))

  return new TextDecoder().decode(bytes)
}

export const readTokenIdentity = (token: string): JwtIdentity | null => {
  try {
    const payload = token.split('.')[1]
    if (!payload) return null

    const claims = JSON.parse(decodeBase64Url(payload)) as Record<string, unknown>
    const userId = Number(claims.sub)
    const username = typeof claims.username === 'string' ? claims.username : ''
    const roleCode = typeof claims.roleCode === 'string' ? claims.roleCode : ''

    if (
      !Number.isSafeInteger(userId) ||
      userId <= 0 ||
      !username ||
      !['ADMIN', 'DOCTOR', 'RESEARCHER', 'USER'].includes(roleCode)
    ) {
      return null
    }

    return {
      userId,
      username,
      roleCode: roleCode as UserRole,
    }
  } catch {
    return null
  }
}

export const tokenMatchesUser = (token: string, user: CurrentUser): boolean => {
  const identity = readTokenIdentity(token)

  return Boolean(
    identity &&
      identity.userId === Number(user.id) &&
      identity.username === user.username &&
      identity.roleCode === user.roleCode,
  )
}
