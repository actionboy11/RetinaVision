import request from '@/utils/request'
import type { DoctorOption } from '@/types/auth'

export const getDoctorOptions = (): Promise<DoctorOption[]> =>
  request.get<unknown, DoctorOption[]>('/users/doctors')
