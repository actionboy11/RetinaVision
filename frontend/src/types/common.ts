export type ID = number

export type DateTimeString = string

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
