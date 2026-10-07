import type { DateTimeString, ID, PageQuery } from './common'

export type TaskType = 'VESSEL_SEGMENTATION' | 'IMAGE_QUALITY_CHECK'

export type TaskStatus =
  | 'CREATED'
  | 'WAITING'
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'
  | 'RETRYING'
  | 'CANCELED'

export type OperatorType = 'USER' | 'SYSTEM' | 'WORKER' | 'ADMIN'

export type ImageQualityStatus = 'NOT_CHECKED' | 'CHECKING' | 'PASS' | 'WARNING' | 'FAIL' | 'ERROR'

export interface ImageQualitySummary {
  status: ImageQualityStatus | null
  score: number | null
  resultId: ID | null
  taskId: ID | null
  checkedAt: DateTimeString | null
}

export interface TaskListQuery extends PageQuery {
  status?: TaskStatus
  taskType?: TaskType
  caseNo?: string
  keyword?: string
}

export interface TaskListItem {
  id: ID
  taskNo: string
  caseId: ID
  caseNo: string
  imageFileId: ID
  originalFilename: string
  taskType: TaskType
  status: TaskStatus
  priority: number
  /** @deprecated 历史质量门控字段，仅用于兼容旧任务。 */
  qualityOverride?: boolean
  /** @deprecated 历史质量门控字段，仅用于兼容旧任务。 */
  qualityOverrideReason?: string
  retryCount: number
  maxRetryCount: number
  errorMessage: string | null
  submittedBy: ID
  submittedByName: string
  submittedAt: DateTimeString
  startedAt: DateTimeString | null
  finishedAt: DateTimeString | null
  updatedAt: DateTimeString
}

export interface CreateTaskRequest {
  caseId: ID
  imageFileId: ID
  taskType: TaskType
  priority: number
  /** @deprecated 后端已忽略该字段，质量检测仅供医生决策参考。 */
  qualityOverride?: boolean
  /** @deprecated 后端已忽略该字段，质量检测仅供医生决策参考。 */
  qualityOverrideReason?: string
}

export interface CreateTaskResponse {
  id: ID
  taskNo: string
  status: 'WAITING'
  message: string
}

export interface TaskDetail extends TaskListItem {
  imagePreviewUrl: string
  qualitySummary: ImageQualitySummary | null
}

export interface RetryTaskResponse {
  id: ID
  taskNo: string
  status: 'WAITING' | 'RETRYING'
  retryCount: number
}

export interface VesselSegmentationResult {
  vesselAreaRatio: number
  imageQualityScore?: number | null
  processingTimeMs: number
  modelVersion: string
  conclusion: string
}

export interface ImageQualityResult {
  grade: 'PASS' | 'WARNING' | 'FAIL'
  score: number
  metrics: Record<string, number>
  reasons: string[]
}

export interface AnalysisResult {
  id: ID
  taskId: ID
  resultType: TaskType
  resultJson:
    | VesselSegmentationResult
    | ImageQualityResult
    | Record<string, unknown>
  maskPreviewUrl: string | null
  reportDownloadUrl: string | null
  modelName: string
  modelVersion: string
  processingTimeMs: number
  createdAt: DateTimeString
  updatedAt: DateTimeString
  qualitySummary: ImageQualitySummary | null
}

export interface TaskLogItem {
  id: ID
  taskId: ID
  fromStatus: string | null
  toStatus: string
  message: string
  operatorType: OperatorType
  createdAt: DateTimeString
}

export interface AnalysisResultComparisonSnapshot {
  resultId: ID
  taskId: ID
  imageFileId: ID
  qualityScore: number | null
  qualityStatus: ImageQualityStatus | null
  vesselAreaRatio: number | null
  modelName: string | null
  modelVersion: string | null
  reviewStatus: 'PENDING' | 'APPROVED' | 'REJECTED' | 'NEEDS_CHANGES' | null
  reportStatus: 'DRAFT' | 'SIGNED' | 'SUPERSEDED' | null
  finishedAt: DateTimeString | null
  resultCreatedAt: DateTimeString | null
}

export interface AnalysisResultComparison {
  caseId: ID
  eyeSide: 'LEFT' | 'RIGHT' | 'BOTH'
  taskType: TaskType
  baseline: AnalysisResultComparisonSnapshot
  target: AnalysisResultComparisonSnapshot
  qualityScoreDelta: number | null
  vesselAreaRatioDelta: number | null
  modelChanged: boolean
  reviewStatusChanged: boolean
  reportStatusChanged: boolean
  notes: string[]
}
