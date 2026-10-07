import type { DateTimeString } from './common'

export type ComponentStatus = 'UP' | 'DEGRADED' | 'DOWN'

export interface AiRuntimeStatus {
  status: ComponentStatus
  reachable: boolean
  ready: boolean
  modelLoaded: boolean
  modelName: string | null
  modelVersion: string | null
  device: string | null
  busy: boolean
  startedAt: DateTimeString | null
  totalRequests: number | null
  successCount: number | null
  failureCount: number | null
  lastInferenceTimeMs: number | null
  lastSuccessAt: DateTimeString | null
  lastError: string | null
}

export interface QueueRuntimeStatus {
  status: ComponentStatus
  error: string | null
  queueName: string | null
  messageReadyCount: number | null
  messageUnackedCount: number | null
  consumerCount: number | null
  deadLetterCount: number | null
}

export interface TaskRuntimeStatus {
  todaySubmittedCount: number
  todaySuccessCount: number
  todayFailedCount: number
  waitingCount: number
  runningCount: number
  retryingCount: number
  totalTaskCount: number
  successRate: number
  averageProcessingTimeMs: number
}

export interface SystemStatus {
  overallStatus: ComponentStatus
  checkedAt: DateTimeString
  ai: AiRuntimeStatus
  queue: QueueRuntimeStatus
  tasks: TaskRuntimeStatus
}
