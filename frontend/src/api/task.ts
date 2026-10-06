import request from '@/utils/request'

import type { PageResult } from '@/types/common'
import type {
  AnalysisResult,
  AnalysisResultComparison,
  CreateTaskRequest,
  CreateTaskResponse,
  RetryTaskResponse,
  TaskDetail,
  TaskListItem,
  TaskListQuery,
  TaskLogItem,
} from '@/types/task'

export const getTaskPage = (
  params: TaskListQuery,
): Promise<PageResult<TaskListItem>> => {
  return request.get<unknown, PageResult<TaskListItem>>('/analysis-tasks', {
    params,
  })
}

export const createTask = (
  data: CreateTaskRequest,
): Promise<CreateTaskResponse> => {
  return request.post<unknown, CreateTaskResponse>('/analysis-tasks', data)
}

export const getTaskDetail = (taskId: number): Promise<TaskDetail> => {
  return request.get<unknown, TaskDetail>(`/analysis-tasks/${taskId}`)
}

export const cancelTask = (taskId: number): Promise<boolean> => {
  return request.post<unknown, boolean>(`/analysis-tasks/${taskId}/cancel`)
}

export const retryTask = (taskId: number): Promise<RetryTaskResponse> => {
  return request.post<unknown, RetryTaskResponse>(
    `/analysis-tasks/${taskId}/retry`,
  )
}

export const getTaskResult = (taskId: number): Promise<AnalysisResult> => {
  return request.get<unknown, AnalysisResult>(
    `/analysis-tasks/${taskId}/result`,
  )
}

export const getTaskLogs = (taskId: number): Promise<TaskLogItem[]> => {
  return request.get<unknown, TaskLogItem[]>(`/analysis-tasks/${taskId}/logs`)
}

export const compareAnalysisResults = (
  baselineResultId: number,
  targetResultId: number,
): Promise<AnalysisResultComparison> => {
  return request.get<unknown, AnalysisResultComparison>('/analysis-results/compare', {
    params: {
      baselineResultId,
      targetResultId,
    },
  })
}

export const getResultMaskUrl = (resultId: number): string => {
  return `/results/${resultId}/mask`
}

export const getResultReportUrl = (resultId: number): string => {
  return `/results/${resultId}/report`
}

export const getResultMaskBlob = (resultId: number): Promise<Blob> => {
  return request.get<unknown, Blob>(getResultMaskUrl(resultId), {
    responseType: 'blob',
  })
}

export const getResultReportBlob = (resultId: number): Promise<Blob> => {
  return request.get<unknown, Blob>(getResultReportUrl(resultId), {
    responseType: 'blob',
  })
}
