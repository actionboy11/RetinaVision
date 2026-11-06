import type { DateTimeString, ID } from './common'

export type UserRole = 'ADMIN' | 'DOCTOR' | 'RESEARCHER' | 'USER'

/** 可分配给用户的可信角色（ADMIN 不可通过管理接口分配）。 */
export type AssignableRole = Extract<UserRole, 'USER' | 'DOCTOR' | 'RESEARCHER'>

export interface AdminUser {
  id: ID
  username: string
  realName: string
  roleCode: UserRole
  professionalNo: string | null
  roleAssignedBy: ID | null
  roleAssignedAt: DateTimeString | null
  status: number
}

export interface AssignUserRoleRequest {
  roleCode: AssignableRole
  professionalNo?: string
}

export interface DoctorOption {
  id: ID
  displayName: string
  professionalNo: string | null
}

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
