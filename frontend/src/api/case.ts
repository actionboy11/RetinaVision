import request from '@/utils/request'

import type {
  CaseDetail,
  CaseAnalysisTimeline,
  CaseTrendSummary,
  CaseListItem,
  CaseListQuery,
  CreateCaseRequest,
  UpdateCaseRequest,
  EyeSide,
  PatientCaseProgress,
  PatientSignedReport,
} from '@/types/case'
import type { TaskType } from '@/types/task'
import type { PageResult } from '@/types/common'

export const getCasePage = (
  params: CaseListQuery,
): Promise<PageResult<CaseListItem>> => {
  return request.get<unknown, PageResult<CaseListItem>>('/cases', { params })
}

export const getCaseDetail = (caseId: number): Promise<CaseDetail> => {
  return request.get<unknown, CaseDetail>(`/cases/${caseId}`)
}

export const createCase = (
  data: CreateCaseRequest,
): Promise<CaseDetail> => {
  return request.post<unknown, CaseDetail>('/cases', data)
}

export const updateCase = (
  caseId: number,
  data: UpdateCaseRequest,
): Promise<CaseDetail> => {
  return request.put<unknown, CaseDetail>(`/cases/${caseId}`, data)
}

export const assignCaseDoctor = (
  caseId: number,
  assignedDoctorId: number,
): Promise<CaseDetail> => {
  return request.put<unknown, CaseDetail>(`/cases/${caseId}/doctor-assignment`, {
    assignedDoctorId,
  })
}

export const deleteCase = (caseId: number): Promise<boolean> => {
  return request.delete<unknown, boolean>(`/cases/${caseId}`)
}

export const submitCase = (caseId: number): Promise<CaseDetail> =>
  request.post<unknown, CaseDetail>(`/cases/${caseId}/submit`)

export const withdrawCase = (caseId: number): Promise<CaseDetail> =>
  request.post<unknown, CaseDetail>(`/cases/${caseId}/withdraw`)

export const getPatientCaseProgress = (caseId: number): Promise<PatientCaseProgress> =>
  request.get<unknown, PatientCaseProgress>(`/cases/${caseId}/progress`)

export const getPatientSignedReports = (caseId: number): Promise<PatientSignedReport[]> =>
  request.get<unknown, PatientSignedReport[]>(`/cases/${caseId}/signed-reports`)

export const getPatientSignedReportDownloadUrl = (caseId: number, resultId: number, version: number) =>
  `/cases/${caseId}/signed-reports/${resultId}/${version}/download`

export const downloadPatientSignedReport = (
  caseId: number,
  resultId: number,
  version: number,
): Promise<Blob> => request.get<unknown, Blob>(
  getPatientSignedReportDownloadUrl(caseId, resultId, version),
  { responseType: 'blob' },
)

export const getCaseAnalysisTimeline = (
  caseId: number,
  params?: {
    eyeSide?: EyeSide
    taskType?: TaskType
    startTime?: string
    endTime?: string
  },
): Promise<CaseAnalysisTimeline> => {
  return request.get<unknown, CaseAnalysisTimeline>(
    `/cases/${caseId}/analysis-timeline`,
    { params },
  )
}

export const generateCaseTrendSummary = (
  caseId: number,
  params?: {
    eyeSide?: EyeSide
    taskType?: TaskType
  },
): Promise<CaseTrendSummary> => {
  return request.post<unknown, CaseTrendSummary>(
    `/cases/${caseId}/trend-summary/ai-generate`,
    null,
    { params },
  )
}
