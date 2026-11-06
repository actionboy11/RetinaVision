import type { DateTimeString, ID } from './common'
import type { TaskStatus, TaskType } from './task'

export interface QualityControlOverview {
  taskTotalCount: number
  successRate: number
  failedRate: number
  averageProcessingTimeMs: number
  retryTaskCount: number
  qualityDistribution: Record<string, number>
  reviewDistribution: Record<string, number>
  signedReportCount: number
  approvedUnsignedCount: number
  vesselResultCount: number
  averageVesselAreaRatio: number
  abnormalLowVesselRatioCount: number
  abnormalHighVesselRatioCount: number
}

/** 风险等级由后端质控规则输出，前端按此映射标签颜色。 */
export type RiskLevel = 'CRITICAL' | 'WARNING' | 'INFO'

export interface ModelPerformanceItem {
  modelName: string
  modelVersion: string
  resultType: TaskType
  resultCount: number
  successRate: number
  averageProcessingTimeMs: number
  averageVesselAreaRatio: number
  reviewApprovedRate: number
  reviewNeedsChangeRate: number
  failedRate: number
}

export interface ReviewStatistics {
  totalReviewCount: number
  reviewDistribution: Record<string, number>
  approvedRate: number
  needsChangeRate: number
  rejectedRate: number
  signedReportCount: number
  approvedUnsignedCount: number
}

export interface RiskAlertItem {
  level: RiskLevel
  reason: string
  taskId: ID | null
  taskNo: string | null
  resultId: ID | null
  caseId: ID | null
  taskType: TaskType | null
  status: TaskStatus | null
  createdAt: DateTimeString | null
}
