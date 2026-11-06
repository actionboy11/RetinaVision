export type ID = number

export type DateTimeString = string

/**
 * 医生审核状态。
 * 后端历史接口存在两个同义值：CHANGES_REQUESTED（临床审核侧）与
 * NEEDS_CHANGES（分析任务 / 病例侧）。前端统一兼容两者，
 * TODO: 后端契约统一后收敛为单一字面量，并移除此处兼容分支。
 */
export type ReviewStatusValue =
  'PENDING' | 'APPROVED' | 'REJECTED' | 'CHANGES_REQUESTED' | 'NEEDS_CHANGES'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
}

export interface PageQuery {
  pageNo?: number
  pageSize?: number
}
