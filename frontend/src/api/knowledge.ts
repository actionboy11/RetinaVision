import request from '@/utils/request'
import type {
  CreateKnowledgeDocumentRequest,
  KnowledgeChatMessage,
  KnowledgeChatResponse,
  KnowledgeChatSession,
  KnowledgeDocument,
  KnowledgeDocumentStatus,
} from '@/types/knowledge'

export const createKnowledgeDocument = (data: CreateKnowledgeDocumentRequest) =>
  request.post<unknown, KnowledgeDocument>('/knowledge/documents', data)

export const listKnowledgeDocuments = () =>
  request.get<unknown, KnowledgeDocument[]>('/knowledge/documents')

export const reindexKnowledgeDocument = (id: number) =>
  request.post<unknown, KnowledgeDocument>(`/knowledge/documents/${id}/reindex`)

export const updateKnowledgeDocumentStatus = (id: number, status: KnowledgeDocumentStatus) =>
  request.put<unknown, KnowledgeDocument>(`/knowledge/documents/${id}/status`, { status })

export const deleteKnowledgeDocument = (id: number) =>
  request.delete<unknown, void>(`/knowledge/documents/${id}`)

export const createKnowledgeSession = () =>
  request.post<unknown, KnowledgeChatSession>('/knowledge/chat/sessions')

export const listKnowledgeSessions = () =>
  request.get<unknown, KnowledgeChatSession[]>('/knowledge/chat/sessions')

export const listKnowledgeMessages = (sessionId: number) =>
  request.get<unknown, KnowledgeChatMessage[]>(`/knowledge/chat/sessions/${sessionId}/messages`)

export const askKnowledge = (data: { sessionId: number | null; question: string }) =>
  request.post<unknown, KnowledgeChatResponse>('/knowledge/chat', data)
