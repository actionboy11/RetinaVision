import type { AgentComparisonSnapshot } from '@/types/agent'
import type { CaseWorkflowStatus, EyeSide, PatientGender } from '@/types/case'
import type { ImageQualityStatus, TaskStatus, TaskType } from '@/types/task'
import {
  caseWorkflowStatusTagTypeMap,
  caseWorkflowStatusTextMap,
  eyeSideTextMap,
  imageQualityStatusTagTypeMap,
  imageQualityStatusTextMap,
  patientGenderTextMap,
  taskStatusTagTypeMap,
  taskStatusTextMap,
  taskTypeTextMap,
  type ElementTagType,
} from '@/utils/enums'
import { formatDateTime, formatEmpty } from '@/utils/format'

export const metricDefinitions = [
  { key: 'patientCount', label: '负责患者', query: '我有多少名患者？' },
  { key: 'caseCount', label: '病例总数', query: '查询我负责的病例' },
  { key: 'unsegmentedCaseCount', label: '尚未分割', query: '哪些病例还没有进行分割？' },
  { key: 'incompleteSegmentationCaseCount', label: '分割未完成', query: '哪些病例的分割还没有完成？' },
  { key: 'pendingReviewCount', label: '待审核', query: '今天有哪些待审核结果？' },
  { key: 'pendingReportCount', label: '待签发', query: '哪些结果已经审核但还没有签发？' },
] as const

export const skillNameMap: Record<string, string> = {
  DOCTOR_WORKLOAD_OVERVIEW: '工作量总览',
  ASSIGNED_CASE_SEARCH: '负责病例筛选',
  CASE_CLINICAL_SUMMARY: '病例摘要',
  CASE_FOLLOWUP_ANALYSIS: '随访结果比较',
  DOCTOR_TASK_SEARCH: '分析任务查询',
  DOCTOR_CLINICAL_QUEUE: '临床待办队列',
  MEDICAL_KNOWLEDGE_QA: '医学知识检索',
  MY_CASE_LIST: '我的检查',
  MY_CASE_PROGRESS: '检查进度',
  MY_SIGNED_REPORT: '正式报告',
  PATIENT_KNOWLEDGE_QA: '健康知识问答',
}

export const patientQualityStatusTextMap = {
  CHECKING: '正在检查图像质量',
  ACCEPTABLE: '图像可以继续处理',
  REUPLOAD_RECOMMENDED: '建议重新上传图像',
  UNAVAILABLE: '暂时无法判断图像质量',
} as const

export const patientQualityStatusTagMap: Record<string, ElementTagType> = {
  CHECKING: 'info',
  ACCEPTABLE: 'success',
  REUPLOAD_RECOMMENDED: 'warning',
  UNAVAILABLE: 'info',
}

export const toolNameMap: Record<string, string> = {
  searchMedicalKnowledge: '检索医学知识',
  listMyCases: '查询我的检查',
  getMyCaseProgress: '查询检查进度',
  explainMySignedReport: '解释已签发报告',
  getAssignedCaseSummary: '查询负责病例摘要',
  getAnalysisTask: '查询分析任务及日志',
  getCaseAnalysisTimeline: '查询病例分析时间线',
  compareRecentAnalysisResults: '比较最近两次结果',
  compareAnalysisResults: '比较指定分析结果',
  getQualityControlOverview: '查询 AI 质控总览',
  listQualityRiskAlerts: '查询风险任务',
}

export const detailDefinitions = [
  { key: 'caseNo', label: '病例编号' },
  { key: 'patientNo', label: '匿名患者编号' },
  { key: 'patientAge', label: '年龄' },
  { key: 'patientGender', label: '性别' },
  { key: 'eyeSide', label: '眼别' },
  { key: 'workflowStatus', label: '流程状态' },
  { key: 'updatedAt', label: '最近更新' },
] as const

const reviewStatusMap: Record<string, string> = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已拒绝',
  NEEDS_CHANGES: '需修改',
  CHANGES_REQUESTED: '需修改',
}

const reportStatusMap: Record<string, string> = {
  DRAFT: '草稿',
  SIGNED: '已签发',
  SUPERSEDED: '已替代',
}

export const displayWorkflowStatus = (value?: string | null) =>
  value && value in caseWorkflowStatusTextMap
    ? caseWorkflowStatusTextMap[value as CaseWorkflowStatus]
    : formatEmpty(value)

export const workflowTagType = (value?: string | null): ElementTagType =>
  value && value in caseWorkflowStatusTagTypeMap
    ? caseWorkflowStatusTagTypeMap[value as CaseWorkflowStatus]
    : 'info'

export const displayTaskStatus = (value?: string | null) =>
  value && value in taskStatusTextMap ? taskStatusTextMap[value as TaskStatus] : value ? value : '尚未分割'

export const taskTagType = (value?: string | null): ElementTagType =>
  value && value in taskStatusTagTypeMap ? taskStatusTagTypeMap[value as TaskStatus] : 'info'

export const displayQualityStatus = (value?: string | null) =>
  value && value in imageQualityStatusTextMap
    ? imageQualityStatusTextMap[value as ImageQualityStatus]
    : formatEmpty(value)

export const qualityTagType = (value?: string | null): ElementTagType =>
  value && value in imageQualityStatusTagTypeMap
    ? imageQualityStatusTagTypeMap[value as ImageQualityStatus]
    : 'info'

export const displayEyeSide = (value?: string | null) =>
  value && value in eyeSideTextMap ? eyeSideTextMap[value as EyeSide] : formatEmpty(value)

export const displayGender = (value?: string | null) =>
  value && value in patientGenderTextMap ? patientGenderTextMap[value as PatientGender] : formatEmpty(value)

export const displayTaskType = (value?: string | null) =>
  value && value in taskTypeTextMap ? taskTypeTextMap[value as TaskType] : formatEmpty(value)

export const displayReviewStatus = (value?: string | null) =>
  value ? reviewStatusMap[value] || value : '暂无记录'

export const displayReportStatus = (value?: string | null) =>
  value ? reportStatusMap[value] || value : '未签发'

export const displayDetailValue = (key: string, value: unknown) => {
  if (key === 'patientGender') return displayGender(value as string | null)
  if (key === 'eyeSide') return displayEyeSide(value as string | null)
  if (key === 'workflowStatus') return displayWorkflowStatus(value as string | null)
  if (key === 'updatedAt') return value ? formatDateTime(String(value)) : '暂无数据'
  return value === null || value === undefined || value === '' ? '暂无数据' : String(value)
}

export const displayScore = (value?: number | null) =>
  value === null || value === undefined ? '暂无数据' : value.toFixed(2)

export const displayRatio = (value?: number | null) =>
  value === null || value === undefined ? '暂无数据' : `${(value * 100).toFixed(2)}%`

export const displayDelta = (value?: number | null, ratio = false) => {
  if (value === null || value === undefined) return '暂无数据'
  const normalized = ratio ? value * 100 : value
  return `${normalized > 0 ? '+' : ''}${normalized.toFixed(2)}${ratio ? '%' : ''}`
}

export const displayModel = (snapshot?: AgentComparisonSnapshot | null) => {
  if (!snapshot?.modelName && !snapshot?.modelVersion) return '暂无数据'
  return [snapshot.modelName, snapshot.modelVersion].filter(Boolean).join(' / ')
}
