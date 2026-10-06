import request from '@/utils/request'

import type {
  CurrentUser,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
} from '@/types/auth'

export const login = (data: LoginRequest): Promise<LoginResponse> => {
  return request.post<unknown, LoginResponse>('/auth/login', data)
}

export const getCurrentUser = (): Promise<CurrentUser> => {
  return request.get<unknown, CurrentUser>('/auth/me')
}

export const logout = (): Promise<boolean> => {
  return request.post<unknown, boolean>('/auth/logout')
}

export const register = (data: RegisterRequest): Promise<boolean> => {
  return request.post<unknown, boolean>('/auth/register', data)
}
