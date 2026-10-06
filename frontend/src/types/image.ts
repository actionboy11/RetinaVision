import type { DateTimeString, ID } from './common'

export type ImageStatus = 'UPLOADED' | 'BOUND_TASK' | 'DELETED'
export type ImageQualityStatus = 'NOT_CHECKED' | 'CHECKING' | 'PASS' | 'WARNING' | 'FAIL' | 'ERROR'

export interface ImageFileItem {
  id: ID
  caseId: ID
  originalFilename: string
  fileType: string
  fileSize: number
  storageBucket: string
  storageObjectKey: string
  previewUrl: string
  imageWidth: number | null
  imageHeight: number | null
  status: ImageStatus
  qualityStatus: ImageQualityStatus
  qualityScore: number | null
  qualityResultId: ID | null
  qualityCheckedAt: DateTimeString | null
  uploadedBy: ID
  uploadedByName: string
  uploadedAt: DateTimeString
}
