import type { DateTimeString, ID } from './common'

export interface DoctorReviewReminderItem {
  taskId: ID
  resultId: ID
  caseNo: string | null
  originalFilename: string | null
  finishedAt: DateTimeString | null
  reason: string
}

export interface DoctorReviewReminder {
  pendingReviewCount: number
  overdueReviewCount: number
  pendingReportCount: number
  overdueReportCount: number
  overdueThresholdMinutes: number
  latestOverdueItems: DoctorReviewReminderItem[]
}
