import type { DateTimeString, ID, PageQuery } from './common'
import type { ImageQualityStatus, TaskStatus, TaskType } from './task'

export type PatientGender = 'MALE' | 'FEMALE' | 'UNKNOWN'

export type EyeSide = 'LEFT' | 'RIGHT' | 'BOTH'

export type CaseStatus = 'ACTIVE' | 'ARCHIVED' | 'DELETED'
export type CaseWorkflowStatus = 'DRAFT' | 'SUBMITTED' | 'IN_REVIEW' | 'COMPLETED' | 'WITHDRAWN'

export interface CaseListQuery extends PageQuery {
  keyword?: string
  status?: CaseStatus
  eyeSide?: EyeSide
}

export interface CaseListItem {
  id: ID
  caseNo: string
  patientId: ID
  patientNo: string
  patientCode: string
  patientAge: number | null
  patientGender: PatientGender
  eyeSide: EyeSide
  status: CaseStatus
  workflowStatus: CaseWorkflowStatus
  createdBy: ID
  createdByName: string
  assignedDoctorId: ID | null
  assignedDoctorName: string | null
  assignedDoctorProfessionalNo: string | null
  createdAt: DateTimeString
  updatedAt: DateTimeString
}

export interface CaseDetail extends CaseListItem {
  diagnosisNote: string | null
}

export interface CreateCaseRequest {
  patientId?: ID
  patientAge?: number
  patientGender: PatientGender
  eyeSide: EyeSide
  diagnosisNote?: string
  assignedDoctorId?: ID
}

export interface PatientCaseProgress {
  caseId: ID
  caseNo: string
  patientNo: string
  workflowStatus: CaseWorkflowStatus
  imageCount: number
  qualityStatus: ImageQualityStatus
  analysisStatus: TaskStatus | null
  signedReportCount: number
  updatedAt: DateTimeString
}

export interface PatientSignedReport {
  resultId: ID
  version: number
  status: 'SIGNED' | 'SUPERSEDED'
  signedAt: DateTimeString
  signerName: string
  sha256: string
}

export interface UpdateCaseRequest {
  patientAge?: number
  patientGender?: PatientGender
  eyeSide?: EyeSide
  diagnosisNote?: string
  status?: Exclude<CaseStatus, 'DELETED'>
}

export interface CaseAnalysisTimelineItem {
  taskId: ID
  taskNo: string | null
  taskType: TaskType
  taskStatus: TaskStatus
  imageFileId: ID
  resultId: ID | null
  qualityScore: number | null
  qualityStatus: ImageQualityStatus | null
  qualityTaskId: ID | null
  qualityResultId: ID | null
  qualityCheckedAt: DateTimeString | null
  vesselAreaRatio: number | null
  modelName: string | null
  modelVersion: string | null
  processingTimeMs: number | null
  reviewStatus: 'PENDING' | 'APPROVED' | 'REJECTED' | 'NEEDS_CHANGES' | null
  reportStatus: 'DRAFT' | 'SIGNED' | 'SUPERSEDED' | null
  reportVersion: number | null
  submittedAt: DateTimeString | null
  finishedAt: DateTimeString | null
  resultCreatedAt: DateTimeString | null
}

export interface CaseAnalysisTimeline {
  caseId: ID
  eyeSide: EyeSide
  items: CaseAnalysisTimelineItem[]
}

export interface CaseTrendSummary {
  caseId: ID
  summary: string
  recommendation: string
  disclaimer: string
  llmProvider: string
  llmModel: string
  generatedAt: DateTimeString
}
