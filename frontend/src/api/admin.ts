import request from '@/utils/request'
import type { UserRole } from '@/types/auth'
export interface AdminUser {id:number;username:string;realName:string;roleCode:UserRole;professionalNo:string|null;roleAssignedBy:number|null;roleAssignedAt:string|null;status:number}
export const listUsers=()=>request.get<unknown,AdminUser[]>('/admin/users')
export const assignUserRole=(id:number,data:{roleCode:'USER'|'DOCTOR'|'RESEARCHER';professionalNo?:string})=>request.put<unknown,boolean>(`/admin/users/${id}/role`,data)
