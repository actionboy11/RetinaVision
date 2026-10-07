import request from '@/utils/request'
import type {
  AgentEvaluationDataset,
  AgentEvaluationOptions,
  AgentEvaluationResultPage,
  AgentEvaluationRun,
  AgentEvaluationRunPage,
  StartAgentEvaluationRequest,
} from '@/types/agent-evaluation'

export const getAgentEvaluationOptions = () =>
  request.get<unknown, AgentEvaluationOptions>('/agent-evaluations/options')

export const getAgentEvaluationDatasets = () =>
  request.get<unknown, AgentEvaluationDataset[]>('/agent-evaluations/datasets')

export const startAgentEvaluation = (payload: StartAgentEvaluationRequest) =>
  request.post<unknown, AgentEvaluationRun>('/agent-evaluations/runs', payload)

export const getAgentEvaluationRuns = (params: Record<string, unknown> = {}) =>
  request.get<unknown, AgentEvaluationRunPage>('/agent-evaluations/runs', { params })

export const getAgentEvaluationRun = (runId: number) =>
  request.get<unknown, AgentEvaluationRun>(`/agent-evaluations/runs/${runId}`)

export const getAgentEvaluationResults = (runId: number, params: Record<string, unknown> = {}) =>
  request.get<unknown, AgentEvaluationResultPage>(`/agent-evaluations/runs/${runId}/results`, { params })

export const cancelAgentEvaluation = (runId: number) =>
  request.post<unknown, void>(`/agent-evaluations/runs/${runId}/cancel`)

export const reviewAgentEvaluation = (runId: number, reviewDecision: 'APPROVED' | 'REJECTED', reviewNote: string) =>
  request.put<unknown, AgentEvaluationRun>(`/agent-evaluations/runs/${runId}/review`, {
    reviewDecision,
    reviewNote,
  })
