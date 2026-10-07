import type { DateTimeString } from './common'

export interface TaskStatistics {
  todaySubmittedCount: number
  todaySuccessCount: number
  todayFailedCount: number
  waitingCount: number
  runningCount: number
  totalTaskCount: number
  successRate: number
  averageProcessingTimeMs: number
}

export interface QueueStatistics {
  queueName: string
  messageReadyCount: number
  messageUnackedCount: number
  consumerCount: number
  deadLetterCount: number
}

export interface TaskTrendItem {
  date: DateTimeString
  submittedCount: number
  successCount: number
  failedCount: number
}
