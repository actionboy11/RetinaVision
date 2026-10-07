import request from '@/utils/request'
import type {
  LlmCallLog,
  LlmCallLogQuery,
  PromptTemplate,
  PromptTemplateVersion,
  PromptEvaluationRun,
  RagEvaluationRun,
} from '@/types/prompt'

export const getPromptTemplates = () =>
  request.get<unknown, PromptTemplate[]>('/prompt-templates')

export const getPromptTemplateVersions = (templateCode: string) =>
  request.get<unknown, PromptTemplateVersion[]>(`/prompt-templates/${templateCode}/versions`)

export const activatePromptTemplateVersion = (templateCode: string, versionId: number) =>
  request.put<unknown, boolean>(`/prompt-templates/${templateCode}/active-version`, { versionId })

export const getLlmCallLogs = (params: LlmCallLogQuery = {}) =>
  request.get<unknown, LlmCallLog[]>('/llm-call-logs', { params })

export const getPromptEvaluationRuns = () =>
  request.get<unknown, PromptEvaluationRun[]>('/prompt-evaluations/runs')

export const getPromptEvaluationRun = (id: number) =>
  request.get<unknown, PromptEvaluationRun>(`/prompt-evaluations/runs/${id}`)

export const startPromptEvaluation = (candidateVersionId: number) =>
  request.post<unknown, PromptEvaluationRun>('/prompt-evaluations/runs', { candidateVersionId })

export const reviewPromptEvaluation = (id: number, approved: boolean, score: number, note: string) =>
  request.put<unknown, PromptEvaluationRun>(`/prompt-evaluations/runs/${id}/review`, { approved, score, note })

export const getRagEvaluationRuns = () =>
  request.get<unknown, RagEvaluationRun[]>('/rag-evaluations/runs')

export const getRagEvaluationRun = (id: number) =>
  request.get<unknown, RagEvaluationRun>(`/rag-evaluations/runs/${id}`)

export const startRagEvaluation = (candidateVersionId: number) =>
  request.post<unknown, RagEvaluationRun>('/rag-evaluations/runs', { candidateVersionId })

export const reviewRagEvaluation = (id: number, approved: boolean, score: number, note: string) =>
  request.put<unknown, RagEvaluationRun>(`/rag-evaluations/runs/${id}/review`, { approved, score, note })
