import type { DateTimeString, ID, ReviewStatusValue } from './common'

export type FeedbackVerdict = 'ACCEPTED' | 'PARTIAL' | 'INCORRECT'

/** 提交医生审核表单时允许的状态（不含后端历史别名 NEEDS_CHANGES）。 */
export type ReviewStatus = Exclude<ReviewStatusValue, 'NEEDS_CHANGES'>

export type CorrectionStatus = 'DRAFT' | 'SUBMITTED' | 'ACCEPTED' | 'REJECTED'

export type ReportStatus = 'DRAFT' | 'SIGNED' | 'SUPERSEDED'

export interface Feedback {
  id: ID
  verdict: FeedbackVerdict
  issueCodes: string
  comment: string
  submittedBy: ID
  createdAt: DateTimeString
}

export interface Correction {
  id: ID
  version: number
  correctedMaskObjectKey: string
  reason: string
  status: CorrectionStatus
  submittedBy: ID
  createdAt: DateTimeString
}

export interface Review {
  correctionVersion: number | null
  status: ReviewStatus
  findings: string
  conclusion: string
  recommendation: string
  reviewerNameSnapshot: string
  professionalNoSnapshot: string
  version: number
}

export interface Report {
  version: number
  status: ReportStatus
  correctionVersion: number | null
  draftJson: string
  reportSha256: string | null
  signerNameSnapshot: string | null
  professionalNoSnapshot: string | null
  signedAt: DateTimeString | null
}
