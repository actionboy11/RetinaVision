export type PromptTemplateStatus = 'ACTIVE' | 'DISABLED'

export interface PromptTemplate {
  templateCode: string
  name: string
  scenario: string
  description: string | null
  status: PromptTemplateStatus
  activeVersion: number | null
  updatedAt: string
}

export interface PromptTemplateVersion {
  id: number
  templateCode: string
  version: number
  systemPrompt: string
  outputContract: string
  safetyPolicy: string
  active: boolean
  createdBy: number | null
  createdAt: string
}

export interface LlmCallLog {
  id: number
  scenario: string
  templateCode: string
  templateVersion: number
  provider: string
  model: string
  success: boolean
  latencyMs: number
  errorSummary: string | null
  createdAt: string
}

export interface LlmCallLogQuery {
  scenario?: string
  templateCode?: string
  success?: boolean
  startTime?: string
  endTime?: string
}

export interface PromptEvaluationOutcome {
  passed: boolean
  output: string
  errorCode: string | null
  latencyMs: number
}

export interface PromptEvaluationCase {
  caseId: string
  title: string
  context: {
    result?: { resultJson?: { vesselAreaRatio?: number }; modelVersion?: string }
    case?: { age?: number | string; gender?: string; eyeSide?: string }
    image?: { qualityStatus?: string; qualityScore?: number | string }
  }
  baseline: PromptEvaluationOutcome
  candidate: PromptEvaluationOutcome
}

export interface PromptEvaluationRun {
  id: number
  templateCode: string
  baselineVersionId: number
  candidateVersionId: number
  sampleVersion: string
  provider: string
  model: string
  embeddingModel?: string | null
  scoreThreshold?: number | null
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  automatedPass: boolean | null
  reviewDecision: 'APPROVED' | 'REJECTED' | null
  reviewScore: number | null
  reviewNote: string | null
  reviewedBy: number | null
  reviewedAt: string | null
  createdBy: number
  createdAt: string
  completedAt: string | null
  failureReason: string | null
  result: { cases: PromptEvaluationCase[] } | null
}

export interface RagEvaluationOutcome {
  passed: boolean
  answer: string
  citations: Array<{ documentTitle: string; chunkId: number; snippet: string }>
  latencyMs: number
  errorCode: string
}

export interface RagEvaluationCase {
  caseId: string
  title: string
  question: string
  expectedChunkId: number
  retrievedChunkIds: number[]
  rank: number
  baseline: RagEvaluationOutcome
  candidate: RagEvaluationOutcome
}

export interface RagEvaluationRun extends Omit<PromptEvaluationRun, 'result'> {
  result: {
    retrieval: { hitAt3: number; mrr: number; passed: boolean }
    generationPassed: boolean
    cases: RagEvaluationCase[]
  } | null
}
