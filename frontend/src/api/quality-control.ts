import request from '@/utils/request'

import type {
  ModelPerformanceItem,
  QualityControlOverview,
  ReviewStatistics,
  RiskAlertItem,
} from '@/types/quality-control'

export const getQualityOverview = (): Promise<QualityControlOverview> => {
  return request.get<unknown, QualityControlOverview>('/quality-control/overview')
}

export const getModelPerformance = (): Promise<ModelPerformanceItem[]> => {
  return request.get<unknown, ModelPerformanceItem[]>('/quality-control/model-performance')
}

export const getReviewStatistics = (): Promise<ReviewStatistics> => {
  return request.get<unknown, ReviewStatistics>('/quality-control/review-statistics')
}

export const getRiskAlerts = (): Promise<RiskAlertItem[]> => {
  return request.get<unknown, RiskAlertItem[]>('/quality-control/risk-alerts')
}
