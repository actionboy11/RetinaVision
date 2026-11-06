import request from '@/utils/request'
import type { AdminUser, AssignUserRoleRequest } from '@/types/auth'

export const listUsers = () => request.get<unknown, AdminUser[]>('/admin/users')

export const assignUserRole = (id: number, data: AssignUserRoleRequest) =>
  request.put<unknown, boolean>(`/admin/users/${id}/role`, data)
