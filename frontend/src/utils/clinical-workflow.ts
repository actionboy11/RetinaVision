import type { Correction, FeedbackVerdict, Report } from '@/types/clinical'

/**
 * ClinicalWorkflowPanel 的纯展示逻辑与常量。
 * 抽离到此处后，组件本体只保留状态编排与接口调用，便于单独阅读/复用/测试。
 */

export interface DraftForm {
  findings: string
  conclusion: string
  recommendation: string
  explanation: string
  disclaimer: string
}

export type WorkflowStage = 'feedback' | 'correction' | 'draft' | 'review'

export const DEFAULT_DRAFT_DISCLAIMER = 'AI 辅助分析，不等同于独立医学诊断。'

export const createEmptyDraftForm = (): DraftForm => ({
  findings: '',
  conclusion: '',
  recommendation: '',
  explanation: '',
  disclaimer: DEFAULT_DRAFT_DISCLAIMER,
})

export const WORKFLOW_STAGES: Array<{ key: WorkflowStage; title: string }> = [
  { key: 'feedback', title: '人工反馈' },
  { key: 'correction', title: '修正版本' },
  { key: 'draft', title: 'AI 草稿参考' },
  { key: 'review', title: '医生审核与签发' },
]

export const feedbackVerdictTextMap: Record<FeedbackVerdict, string> = {
  ACCEPTED: '接受',
  PARTIAL: '部分正确',
  INCORRECT: '不正确',
}

export const correctionStatusTextMap: Record<Correction['status'], string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  ACCEPTED: '已采纳',
  REJECTED: '已拒绝',
}

export const reportStatusTextMap: Record<Report['status'], string> = {
  DRAFT: '草稿',
  SIGNED: '已签发',
  SUPERSEDED: '历史版本',
}

export const reportStatusType = (status: Report['status']) => {
  if (status === 'SIGNED') return 'success'
  if (status === 'SUPERSEDED') return 'info'
  return 'warning'
}

export const correctionStatusType = (status: Correction['status']) => {
  if (status === 'ACCEPTED') return 'success'
  if (status === 'REJECTED') return 'danger'
  if (status === 'SUBMITTED') return 'warning'
  return 'info'
}

export const stepStatus = (activeStep: number, index: number) => {
  if (activeStep > index) return 'finish'
  if (activeStep === index) return 'process'
  return 'wait'
}

export interface ParsedDraft {
  raw: Record<string, unknown>
  form: DraftForm
  error: string
}

/** 解析后端返回的 draftJson；格式异常时降级为空草稿并返回可展示的错误文案。 */
export const parseDraftJson = (draftJson: string | null | undefined): ParsedDraft => {
  if (!draftJson || !draftJson.trim()) {
    return { raw: {}, form: createEmptyDraftForm(), error: '' }
  }

  try {
    const raw = JSON.parse(draftJson) as Record<string, unknown>
    const stringField = (key: string, fallback = '') =>
      typeof raw[key] === 'string' ? (raw[key] as string) : fallback

    return {
      raw,
      form: {
        findings: stringField('findings'),
        conclusion: stringField('conclusion'),
        recommendation: stringField('recommendation'),
        explanation: stringField('explanation'),
        disclaimer: stringField('disclaimer', DEFAULT_DRAFT_DISCLAIMER),
      },
      error: '',
    }
  } catch {
    return {
      raw: {},
      form: createEmptyDraftForm(),
      error: '报告草稿格式异常，请重新保存草稿。',
    }
  }
}
