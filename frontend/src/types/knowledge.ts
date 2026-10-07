import type { DateTimeString, ID } from './common'

export interface KnowledgeDocument {
  id: ID
  title: string
  source: string
  category: string
  audience: 'PUBLIC' | 'PATIENT' | 'CLINICAL' | 'RESEARCH' | 'ADMIN'
  status: string
  version: number
  chunkCount: number
  failureReason: string | null
  lastIndexedAt: DateTimeString | null
  createdAt: DateTimeString
  updatedAt: DateTimeString
}

export interface KnowledgeChatSession {
  id: ID
  title: string
  createdAt: DateTimeString
  updatedAt: DateTimeString
}

export interface KnowledgeChatMessage {
  id: ID
  sessionId: ID
  role: 'USER' | 'ASSISTANT'
  content: string
  citationsJson: string | null
  createdAt: DateTimeString
}

export interface KnowledgeCitation {
  documentId: ID
  documentTitle: string
  chunkId: ID
  source: string
  snippet: string
  score: number
}

export interface KnowledgeChatResponse {
  sessionId: ID
  answer: string
  citations: KnowledgeCitation[]
  disclaimer: string
}
