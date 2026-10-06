import request from '@/utils/request'

import type {
  QueueStatistics,
  TaskStatistics,
  TaskTrendItem,
} from '@/types/statistics'

export const getTaskStatistics = (): Promise<TaskStatistics> => {
  return request.get<unknown, TaskStatistics>('/admin/statistics/tasks')
}

export const getQueueStatistics = (): Promise<QueueStatistics> => {
  return request.get<unknown, QueueStatistics>('/admin/statistics/queue')
}

export const getTaskTrend = (days: number): Promise<TaskTrendItem[]> => {
  return request.get<unknown, TaskTrendItem[]>(
    '/admin/statistics/task-trend',
    {
      params: {
        days,
      },
    },
  )
}
