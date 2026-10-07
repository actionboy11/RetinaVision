import type { DateTimeString, ID } from './common'

export type PatientProfileSource = 'ACCOUNT' | 'OFFLINE' | 'LEGACY'

export interface PatientProfile {
  id: ID
  patientNo: string
  source: PatientProfileSource
  createdAt: DateTimeString
  updatedAt: DateTimeString
}

export interface LegacyPatientProfile {
  id: ID
  patientNo: string
  legacyPatientCode: string
  accountUserId: ID | null
  accountUsername: string | null
  caseCount: number
  createdAt: DateTimeString
}
