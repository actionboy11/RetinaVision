import axios, { type AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from '@/plugins/element-plus-api'

import type { ApiResponse } from '@/types/common'
import type { CurrentUser } from '@/types/auth'
import { getToken, removeToken, tokenMatchesUser, USER_STORAGE_KEY } from '@/utils/token'

/** 普通 CRUD 接口的默认超时（毫秒）。 */
const DEFAULT_TIMEOUT = 15_000
/** LLM 对话、报告生成、评测等长任务接口的超时（毫秒）。 */
const LONG_TASK_TIMEOUT = 120_000

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: DEFAULT_TIMEOUT,
})

/**
 * 判断是否为长耗时接口。集中在此处维护，避免每个调用点各写一份 timeout。
 * 注意：这里只按"接口路径 + 方法"匹配，调用方显式传入的 timeout 始终优先。
 */
const isLongTask = (config: InternalAxiosRequestConfig): boolean => {
  const url = (config.url || '').split('?')[0]
  const method = (config.method || 'get').toLowerCase()

  if (/\/ai-generate$|\/evaluate$/.test(url)) return true
  if (/^\/(agent|knowledge)\/chat$/.test(url)) return true
  if (
    method === 'post' &&
    /^\/(agent-evaluations|prompt-evaluations|rag-evaluations)\/runs$/.test(url)
  ) {
    return true
  }
  if (method === 'post' && /^\/cases\/\d+\/images$/.test(url)) return true
  return false
}

const clearAuthAndRedirect = () => {
  removeToken()
  localStorage.removeItem(USER_STORAGE_KEY)
  if (window.location.pathname !== '/login') {
    ElMessage.error('登录已过期，请重新登录')
    window.location.href = '/login'
  }
}

const normalizeErrorMessage = (error: AxiosError): string => {
  if (error.code === 'ECONNABORTED' || /timeout/i.test(error.message)) {
    return '请求超时，请稍后重试'
  }
  if (error.code === 'ERR_NETWORK' || !error.response) {
    return '网络连接异常，请检查网络后重试'
  }
  if (error.response.status === 403) return '权限不足'
  if (error.response.status === 404) return '资源不存在'
  if (error.response.status >= 500) return '服务器异常，请稍后重试'
  return error.message || '网络请求异常'
}

request.interceptors.request.use((config) => {
  if (config.timeout == null || config.timeout === DEFAULT_TIMEOUT) {
    config.timeout = isLongTask(config) ? LONG_TASK_TIMEOUT : DEFAULT_TIMEOUT
  }

  const token = getToken()
  const storedUser = localStorage.getItem(USER_STORAGE_KEY)

  // 与 stores/auth.ts 保持同一约束：token 与 user 成对存在才视为有效会话，
  // 避免悬空 token 让前端展示层与后端鉴权结果不一致
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
    config.headers.Authorization = `Bearer ${token}`
  }

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

/**
 * 下载接口在 HTTP 200 时也可能返回 JSON 业务错误体（权限不足、文件不存在等）。
 * 若不拦截，调用方会把错误 JSON 当成文件保存，得到损坏文件且没有任何提示。
 */
const unwrapBlobResponse = async (response: AxiosResponse<Blob>): Promise<Blob> => {
  const blob = response.data
  if (!(blob instanceof Blob) || !/json/i.test(blob.type)) return blob

  let message = '文件下载失败，请稍后重试'
  let code: number | undefined
  try {
    const payload = JSON.parse(await blob.text()) as Partial<ApiResponse<unknown>>
    message = payload.message || message
    code = payload.code
  } catch {
    // 非 JSON 内容时保留默认提示，不作为文件返回
  }

  if (code === 40100) clearAuthAndRedirect()
  else ElMessage.error(message)
  return Promise.reject(new Error(message))
}

request.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      // 拦截器刻意返回解包后的业务数据，调用方通过 request.get<unknown, T> 声明真实类型。
      return unwrapBlobResponse(response as AxiosResponse<Blob>) as unknown as AxiosResponse
    }
    return unwrapResponse(response as AxiosResponse<ApiResponse<unknown>>) as AxiosResponse
  },
  (error: AxiosError) => {
    const payload = error.response?.data as Partial<ApiResponse<unknown>> | undefined
    const message = payload?.message || normalizeErrorMessage(error)
    if (error.response?.status === 401) clearAuthAndRedirect()
    else ElMessage.error(message)
    return Promise.reject(new Error(message))
  },
)

export default request
