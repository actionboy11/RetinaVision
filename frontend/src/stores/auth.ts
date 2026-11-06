import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { getCurrentUser, login, logout } from '@/api/auth'
import type { CurrentUser, LoginRequest } from '@/types/auth'
import { getToken, removeToken, setToken, tokenMatchesUser, USER_STORAGE_KEY } from '@/utils/token'

const readStoredUser = (): CurrentUser | null => {
  const storedUser = localStorage.getItem(USER_STORAGE_KEY)

  if (!storedUser) {
    return null
  }

  try {
    return JSON.parse(storedUser) as CurrentUser
  } catch {
    localStorage.removeItem(USER_STORAGE_KEY)
    return null
  }
}

const saveStoredUser = (currentUser: CurrentUser) => {
  localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(currentUser))
}

const removeStoredUser = () => {
  localStorage.removeItem(USER_STORAGE_KEY)
}

export const useAuthStore = defineStore('auth', () => {
  const storedToken = getToken()
  const storedUser = readStoredUser()
  // token 与 user 必须成对存在，且 JWT 中的身份与本地用户信息一致，会话才算有效
  const hasValidStoredSession = Boolean(
    storedToken && storedUser && tokenMatchesUser(storedToken, storedUser),
  )

  if (storedToken && !hasValidStoredSession) {
    removeToken()
    removeStoredUser()
  }

  const token = ref(hasValidStoredSession ? storedToken : '')
  const user = ref<CurrentUser | null>(hasValidStoredSession ? storedUser : null)
  const sessionValidated = ref(false)

  const isLoggedIn = computed(() => Boolean(token.value))

  const resetAuth = () => {
    token.value = ''
    user.value = null
    sessionValidated.value = false
    removeToken()
    removeStoredUser()
  }

  const loginAction = async (data: LoginRequest) => {
    const result = await login(data)

    if (!tokenMatchesUser(result.token, result.user)) {
      resetAuth()
      throw new Error('登录身份校验失败，请重新登录')
    }

    token.value = result.token
    user.value = result.user
    sessionValidated.value = true
    setToken(result.token)
    saveStoredUser(result.user)

    return result
  }

  const fetchCurrentUser = async () => {
    const currentUser = await getCurrentUser()

    if (!tokenMatchesUser(token.value, currentUser)) {
      resetAuth()
      throw new Error('登录身份已发生变化，请重新登录')
    }

    user.value = currentUser
    sessionValidated.value = true
    saveStoredUser(currentUser)

    return currentUser
  }

  const logoutAction = async () => {
    try {
      await logout()
    } catch {
      // 服务端登出不可用时，仍必须清理本地登录态
    } finally {
      resetAuth()
    }
  }

  return {
    token,
    user,
    isLoggedIn,
    sessionValidated,
    loginAction,
    fetchCurrentUser,
    logoutAction,
    resetAuth,
  }
})
