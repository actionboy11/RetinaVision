import request from '@/utils/request'
import type {
  AgentChatMessage,
  AgentChatResponse,
  AgentChatSession,
} from '@/types/agent'

export const createAgentSession = () =>
  request.post<unknown, AgentChatSession>('/agent/sessions')

export const listAgentSessions = () =>
  request.get<unknown, AgentChatSession[]>('/agent/sessions')

export const listAgentMessages = (sessionId: number) =>
  request.get<unknown, AgentChatMessage[]>(`/agent/sessions/${sessionId}/messages`)

export const askAgent = (data: { sessionId: number | null; question: string }) =>
  request.post<unknown, AgentChatResponse>('/agent/chat', data)
