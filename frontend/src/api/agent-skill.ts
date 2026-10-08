import request from '@/utils/request'
import type {
  AgentSkillExecutionItem,
  AgentSkillItem,
  AgentSkillVersionItem,
} from '@/types/agent-skill'
import type { AgentEvaluationRun } from '@/types/agent-evaluation'

export const listAgentSkills = () => request.get<unknown, AgentSkillItem[]>('/agent-skills')

export const listAgentSkillVersions = (skillCode: string) =>
  request.get<unknown, AgentSkillVersionItem[]>(`/agent-skills/${skillCode}/versions`)

export const evaluateAgentSkillVersion = (skillCode: string, versionId: number) =>
  request.post<unknown, AgentEvaluationRun>(`/agent-skills/${skillCode}/versions/${versionId}/evaluate`)

export const activateAgentSkillVersion = (skillCode: string, versionId: number) =>
  request.put<unknown, void>(`/agent-skills/${skillCode}/active-version`, { versionId })

export const listAgentSkillExecutions = (params?: { skillCode?: string; success?: boolean }) =>
  request.get<unknown, AgentSkillExecutionItem[]>('/agent-skill-executions', { params })
