import type { DateTimeString, ID, PageResult } from './common'

export type AgentEvaluationRole = 'DOCTOR' | 'PATIENT'
export type AgentEvaluationStatus = 'QUEUED' | 'RUNNING' | 'PASSED' | 'FAILED' | 'INVALID' | 'CANCELLED'

export interface EvaluationVersionOption { id: ID; version: number; active: boolean }
export interface AgentEvaluationOptions {
  models: Array<{ key: string; provider: string; model: string }>
  skillVersions: Record<string, EvaluationVersionOption[]>
  promptVersions: Record<string, EvaluationVersionOption[]>
}

export interface AgentEvaluationDataset {
  id: ID
  datasetCode: string
  name: string
  targetRole: AgentEvaluationRole
  version: number
  status: string
  sampleCount: number
  description: string
}

export interface AgentEvaluationRun {
  id: ID
  datasetId: ID
  datasetVersion: number
  targetRole: AgentEvaluationRole
  modelKey: string
  provider: string
  model: string
  status: AgentEvaluationStatus
  progress: { completed: number; total: number }
  metrics: {
    routingAccuracy: number
    parameterAccuracy: number
    queryAccuracy: number
    structurePassRate: number
    safetyPassRate: number
    citationPassRate: number | null
    averageLatencyMs: number
    p95LatencyMs: number
  } | null
  automatedPass: boolean | null
  reviewDecision: 'PENDING' | 'APPROVED' | 'REJECTED'
  reviewNote: string | null
  bindings: Array<{ type: string; code: string; versionId: ID; versionLabel: string }>
  createdAt: DateTimeString
  completedAt: DateTimeString | null
}

export interface AgentEvaluationResult {
  caseId: ID
  category: string
  inputSummary: string
  expectedSkill: string | null
  actualSkill: string | null
  expectedArguments: Record<string, string>
  actualArguments: Record<string, string>
  success: boolean
  errorType: string | null
  errorSummary: string | null
  latencyMs: number
}

export interface StartAgentEvaluationRequest {
  datasetId: ID
  targetRole: AgentEvaluationRole
  modelKey: string
  skillVersions: Record<string, ID>
  promptVersions: Record<string, ID>
}

export type AgentEvaluationRunPage = PageResult<AgentEvaluationRun>
export type AgentEvaluationResultPage = PageResult<AgentEvaluationResult>
