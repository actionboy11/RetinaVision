import type { DateTimeString, ID } from './common'
export type FeedbackVerdict = 'ACCEPTED' | 'PARTIAL' | 'INCORRECT'
export type ReviewStatus = 'PENDING' | 'CHANGES_REQUESTED' | 'APPROVED' | 'REJECTED'
export interface Feedback { id: ID; verdict: FeedbackVerdict; issueCodes: string; comment: string; submittedBy: ID; createdAt: DateTimeString }
export interface Correction { id: ID; version: number; correctedMaskObjectKey: string; reason: string; status: 'DRAFT'|'SUBMITTED'|'ACCEPTED'|'REJECTED'; submittedBy: ID; createdAt: DateTimeString }
export interface Review { correctionVersion: number|null; status: ReviewStatus; findings: string; conclusion: string; recommendation: string; reviewerNameSnapshot: string; professionalNoSnapshot: string; version: number }
export interface Report { version: number; status: 'DRAFT'|'SIGNED'|'SUPERSEDED'; correctionVersion: number|null; draftJson: string; reportSha256: string|null; signerNameSnapshot: string|null; professionalNoSnapshot: string|null; signedAt: DateTimeString|null }
