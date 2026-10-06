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

export interface ModelPerformanceItem {
  modelName: string
  modelVersion: string
  resultType: TaskType | string
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
  level: 'CRITICAL' | 'WARNING' | 'INFO' | string
  reason: string
  taskId: ID | null
  taskNo: string | null
  resultId: ID | null
  caseId: ID | null
  taskType: TaskType | string | null
  status: TaskStatus | string | null
  createdAt: DateTimeString | null
}
