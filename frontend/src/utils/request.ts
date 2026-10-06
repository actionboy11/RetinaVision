import axios, { type AxiosError, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'

import type { ApiResponse } from '@/types/common'
import type { CurrentUser } from '@/types/auth'
import {
  getToken,
  removeToken,
  tokenMatchesUser,
  USER_STORAGE_KEY,
} from '@/utils/token'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

const clearAuthAndRedirect = () => {
  removeToken()
  localStorage.removeItem(USER_STORAGE_KEY)
  if (window.location.pathname !== '/login') {
    ElMessage.error('登录已过期，请重新登录')
    window.location.href = '/login'
  }
}

request.interceptors.request.use((config) => {
  const token = getToken()
  const storedUser = localStorage.getItem(USER_STORAGE_KEY)

  if (token && storedUser) {
    try {
      const user = JSON.parse(storedUser) as CurrentUser
      if (!tokenMatchesUser(token, user)) {
        clearAuthAndRedirect()
        return Promise.reject(new Error('登录身份不一致，请重新登录'))
      }
    } catch {
      clearAuthAndRedirect()
      return Promise.reject(new Error('登录信息无效，请重新登录'))
    }
  }

  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

const unwrapResponse = (response: AxiosResponse<ApiResponse<unknown>>) => {
  const result = response.data
  if (result.code === 0) return result.data
  if (result.code === 40100) clearAuthAndRedirect()
  const message = result.message || '请求失败'
  ElMessage.error(message)
  return Promise.reject(new Error(message))
}

request.interceptors.response.use(
  (response) => response.config.responseType === 'blob'
    ? response.data as AxiosResponse
    : unwrapResponse(response as AxiosResponse<ApiResponse<unknown>>) as AxiosResponse,
  (error: AxiosError) => {
    const payload = error.response?.data as Partial<ApiResponse<unknown>> | undefined
    const message = payload?.message || (error.response?.status === 403
      ? '权限不足'
      : error.response?.status === 404 ? '资源不存在' : error.message || '网络请求异常')
    if (error.response?.status === 401) clearAuthAndRedirect()
    else ElMessage.error(message)
    return Promise.reject(new Error(message))
  },
)

export default request
