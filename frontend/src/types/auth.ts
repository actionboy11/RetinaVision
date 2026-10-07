import type { ID } from './common'

export type UserRole = 'ADMIN' | 'DOCTOR' | 'RESEARCHER' | 'USER'

export interface CurrentUser {
  id: ID
  username: string
  realName: string
  roleCode: UserRole
  professionalNo?: string | null
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  user: CurrentUser
}

export interface RegisterRequest {
  username: string
  password: string
  realName: string
  roleCode?: 'USER'
}
