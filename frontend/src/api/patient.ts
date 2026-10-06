import request from '@/utils/request'
import type { LegacyPatientProfile, PatientProfile } from '@/types/patient'

export const getMyPatientProfile = () =>
  request.get<unknown, PatientProfile>('/patients/me')

export const listDoctorPatients = () =>
  request.get<unknown, PatientProfile[]>('/patients')

export const createOfflinePatient = () =>
  request.post<unknown, PatientProfile>('/patients/offline')

export const listLegacyPatientProfiles = () =>
  request.get<unknown, LegacyPatientProfile[]>('/admin/patient-profiles/legacy')

export const linkLegacyPatientAccount = (patientId: number, userId: number) =>
  request.put<unknown, PatientProfile>(`/admin/patient-profiles/${patientId}/account-link`, { userId })
