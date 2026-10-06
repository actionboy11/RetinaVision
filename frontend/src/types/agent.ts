import type { DateTimeString, ID } from './common'
import type { CaseWorkflowStatus, EyeSide, PatientGender } from './case'
import type { ImageQualityStatus, TaskStatus, TaskType } from './task'

export interface AgentChatSession {
  id: ID
  title: string
  createdAt: DateTimeString
  updatedAt: DateTimeString
}

export interface AgentChatMessage {
  id: ID
  sessionId: ID
  role: 'USER' | 'ASSISTANT'
  content: string
  structuredContentJson?: string | null
  createdAt: DateTimeString
}

export interface AgentSkillSummary {
  code: string
  name: string
  version: number
}

export interface AgentMetricsPayload {
  patientCount?: number
  caseCount?: number
  unsegmentedCaseCount?: number
  incompleteSegmentationCaseCount?: number
  pendingReviewCount?: number
  pendingReportCount?: number
}

export interface AgentCaseSummary {
  caseId: ID
  caseNo: string
  patientId?: ID | null
  patientNo: string
  patientAge?: number | null
  patientGender?: PatientGender | null
  eyeSide?: EyeSide | null
  workflowStatus?: CaseWorkflowStatus | null
  qualityStatus?: ImageQualityStatus | null
  qualityScore?: number | null
  segmentationStatus?: TaskStatus | null
  reviewStatus?: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CHANGES_REQUESTED' | null
  reportStatus?: 'DRAFT' | 'SIGNED' | 'SUPERSEDED' | null
  latestTaskId?: ID | null
  updatedAt?: DateTimeString | null
}

export interface AgentCaseListPayload {
  cases?: AgentCaseSummary[]
}

export interface AgentCaseDetail {
  caseId?: ID | null
  caseNo?: string | null
  patientNo?: string | null
  patientAge?: number | null
  patientGender?: PatientGender | null
  eyeSide?: EyeSide | null
  workflowStatus?: CaseWorkflowStatus | null
  updatedAt?: DateTimeString | null
}

export interface AgentCaseDetailPayload {
  caseDetail?: AgentCaseDetail | null
}

export interface AgentTaskLog {
  fromStatus?: TaskStatus | null
  toStatus: TaskStatus
  message: string
  operatorType?: string | null
  createdAt: DateTimeString
}

export interface AgentTaskSummary {
  taskId: ID
  taskNo: string
  caseId: ID
  caseNo: string
  patientNo: string
  taskType: TaskType
  status: TaskStatus
  retryCount: number
  maxRetryCount: number
  errorSummary?: string | null
  submittedAt: DateTimeString
  startedAt?: DateTimeString | null
  finishedAt?: DateTimeString | null
  updatedAt: DateTimeString
}

export interface AgentTaskDetail extends AgentTaskSummary {
  logs: AgentTaskLog[]
}

export interface AgentTaskListPayload {
  tasks?: AgentTaskSummary[]
}

export interface AgentTaskDetailPayload {
  taskDetail?: AgentTaskDetail | null
}

export interface AgentClinicalQueueItem {
  taskId: ID
  taskNo: string
  resultId: ID
  caseId: ID
  caseNo: string
  patientNo: string
  eyeSide?: EyeSide | null
  resultType: string
  qualityStatus?: ImageQualityStatus | null
  qualityScore?: number | null
  reviewStatus?: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CHANGES_REQUESTED' | null
  reportStatus?: 'DRAFT' | 'SIGNED' | 'SUPERSEDED' | null
  finishedAt?: DateTimeString | null
  resultCreatedAt: DateTimeString
}

export interface AgentClinicalQueuePayload {
  queueType?: 'PENDING_REVIEW' | 'PENDING_REPORT'
  items?: AgentClinicalQueueItem[]
}

export interface AgentComparisonSnapshot {
  resultId?: ID | null
  taskId?: ID | null
  imageFileId?: ID | null
  qualityScore?: number | null
  qualityStatus?: ImageQualityStatus | null
  vesselAreaRatio?: number | null
  modelName?: string | null
  modelVersion?: string | null
  reviewStatus?: 'PENDING' | 'APPROVED' | 'REJECTED' | 'NEEDS_CHANGES' | null
  reportStatus?: 'DRAFT' | 'SIGNED' | 'SUPERSEDED' | null
  finishedAt?: DateTimeString | null
  resultCreatedAt?: DateTimeString | null
}

export interface AgentComparison {
  caseId?: ID | null
  eyeSide?: EyeSide | null
  taskType?: TaskType | null
  baseline?: AgentComparisonSnapshot | null
  target?: AgentComparisonSnapshot | null
  qualityScoreDelta?: number | null
  vesselAreaRatioDelta?: number | null
  modelChanged?: boolean
  reviewStatusChanged?: boolean
  reportStatusChanged?: boolean
  notes?: string[]
}

export interface AgentComparisonPayload {
  comparison?: AgentComparison | null
}

export type AgentStructuredData =
  | { type: 'METRICS'; payload: AgentMetricsPayload }
  | { type: 'CASE_LIST'; payload: AgentCaseListPayload }
  | { type: 'CASE_DETAIL'; payload: AgentCaseDetailPayload }
  | { type: 'TASK_LIST'; payload: AgentTaskListPayload }
  | { type: 'TASK_DETAIL'; payload: AgentTaskDetailPayload }
  | { type: 'CLINICAL_QUEUE'; payload: AgentClinicalQueuePayload }
  | { type: 'COMPARISON'; payload: AgentComparisonPayload }

export interface AgentPagination {
  page: number
  pageSize: number
  total: number
  hasPrevious: boolean
  hasNext: boolean
}

export interface AgentAction {
  type: 'NEXT_PAGE' | 'PREVIOUS_PAGE' | 'VIEW_CASE' | 'VIEW_TASK' | 'VIEW_REVIEW'
  label: string
  targetId?: number | null
  targetPath?: string | null
}

export interface AgentToolCallSummary {
  toolName: string
  success: boolean
  latencyMs: number
}

export interface AgentCitation {
  documentId: ID
  chunkId: ID
  documentTitle: string
  source: string
  snippet: string
  score: number
}

export interface AgentChatResponse {
  sessionId: ID
  answer: string
  skill?: AgentSkillSummary | null
  data?: AgentStructuredData | null
  pagination?: AgentPagination | null
  actions: AgentAction[]
  toolCalls: AgentToolCallSummary[]
  citations: AgentCitation[]
  disclaimer: string
}

export interface AgentUiMessage extends AgentChatMessage {
  structured?: AgentChatResponse
  failed?: boolean
  failureReason?: string
}
