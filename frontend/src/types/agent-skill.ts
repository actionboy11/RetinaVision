import type { DateTimeString, ID } from './common'

export interface AgentSkillItem {
  skillCode: string
  name: string
  description: string
  status: string
  activeVersionId: ID | null
  updatedAt: DateTimeString
}

export interface AgentSkillVersionItem {
  id: ID
  version: number
  routingExamplesJson: string
  workflowPrompt: string
  answerStyle: string
  active: boolean
  createdAt: DateTimeString
}


export interface AgentSkillExecutionItem {
  id: ID
  skillCode: string
  skillVersion: number
  confidence: number | null
  success: boolean
  latencyMs: number
  errorType: string | null
  createdAt: DateTimeString
}
