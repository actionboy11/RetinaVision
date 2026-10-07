import request from '@/utils/request'

export interface DoctorOption {
  id: number
  displayName: string
  professionalNo: string | null
}

export const getDoctorOptions = (): Promise<DoctorOption[]> => {
  return request.get<unknown, DoctorOption[]>('/users/doctors')
}
