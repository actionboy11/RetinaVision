import type { UserRole } from '@/types/auth'
import type { CaseStatus, CaseWorkflowStatus, EyeSide, PatientGender } from '@/types/case'
import type { ImageQualityStatus, ImageStatus } from '@/types/image'
import type { OperatorType, TaskStatus, TaskType } from '@/types/task'

export type ElementTagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

export const userRoleTextMap: Record<UserRole, string> = {
  ADMIN: '管理员',
  DOCTOR: '医生',
  RESEARCHER: '研究员',
  USER: '患者',
}

export const patientGenderTextMap: Record<PatientGender, string> = {
  MALE: '男',
  FEMALE: '女',
  UNKNOWN: '未知',
}

export const eyeSideTextMap: Record<EyeSide, string> = {
  LEFT: '左眼',
  RIGHT: '右眼',
  BOTH: '双眼',
}

export const caseStatusTextMap: Record<CaseStatus, string> = {
  ACTIVE: '正常',
  ARCHIVED: '已归档',
  DELETED: '已删除',
}

export const caseStatusTagTypeMap: Record<CaseStatus, ElementTagType> = {
  ACTIVE: 'success',
  ARCHIVED: 'info',
  DELETED: 'danger',
}

export const caseWorkflowStatusTextMap: Record<CaseWorkflowStatus, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  IN_REVIEW: '医生处理中',
  COMPLETED: '已完成',
  WITHDRAWN: '已撤回',
}

export const caseWorkflowStatusTagTypeMap: Record<CaseWorkflowStatus, ElementTagType> = {
  DRAFT: 'info',
  SUBMITTED: 'warning',
  IN_REVIEW: 'primary',
  COMPLETED: 'success',
  WITHDRAWN: 'info',
}

export const imageStatusTextMap: Record<ImageStatus, string> = {
  UPLOADED: '已上传',
  BOUND_TASK: '已绑定任务',
  DELETED: '已删除',
}

export const imageStatusTagTypeMap: Record<ImageStatus, ElementTagType> = {
  UPLOADED: 'success',
  BOUND_TASK: 'warning',
  DELETED: 'danger',
}

export const imageQualityStatusTextMap: Record<ImageQualityStatus, string> = {
  NOT_CHECKED: '未检测',
  CHECKING: '检测中',
  PASS: '通过',
  WARNING: '需关注',
  FAIL: '未通过',
  ERROR: '检测失败',
}

export const imageQualityStatusTagTypeMap: Record<ImageQualityStatus, ElementTagType> = {
  NOT_CHECKED: 'info',
  CHECKING: 'primary',
  PASS: 'success',
  WARNING: 'warning',
  FAIL: 'danger',
  ERROR: 'danger',
}

export const taskTypeTextMap: Record<TaskType, string> = {
  VESSEL_SEGMENTATION: '血管分割',
  IMAGE_QUALITY_CHECK: '图像质量检测',
}

export const taskStatusTextMap: Record<TaskStatus, string> = {
  CREATED: '已创建',
  WAITING: '排队中',
  RUNNING: '运行中',
  SUCCESS: '成功',
  FAILED: '失败',
  RETRYING: '重试中',
  CANCELED: '已取消',
}

export const taskStatusTagTypeMap: Record<TaskStatus, ElementTagType> = {
  CREATED: 'info',
  WAITING: 'warning',
  RUNNING: 'primary',
  SUCCESS: 'success',
  FAILED: 'danger',
  RETRYING: 'warning',
  CANCELED: 'info',
}

export const operatorTypeTextMap: Record<OperatorType, string> = {
  USER: '用户操作',
  SYSTEM: '系统操作',
  WORKER: 'Worker 操作',
  ADMIN: '管理员操作',
}
