import type { UserRole } from '@/types/auth'

export const getRoleHome = (role?: UserRole | null) => {
  if (role === 'ADMIN') return '/dashboard'
  if (role === 'DOCTOR') return '/doctor/reviews'
  if (role === 'RESEARCHER') return '/knowledge'
  return '/cases'
}
