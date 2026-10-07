<script setup lang="ts">
import type {
  AgentAction,
  AgentPagination,
  PatientAgentCaseListPayload,
} from '@/types/agent'
import {
  displayEyeSide,
  displayWorkflowStatus,
  patientQualityStatusTagMap,
  patientQualityStatusTextMap,
  workflowTagType,
} from '@/utils/agent-display'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{
  payload: PatientAgentCaseListPayload
  pagination?: AgentPagination | null
  actions?: AgentAction[]
}>()

const emit = defineEmits<{
  action: [value: AgentAction]
}>()

const findAction = (type: AgentAction['type'], caseId?: number) =>
  props.actions?.find((item) => item.type === type && (caseId === undefined || item.targetId === caseId))
</script>

<template>
  <div class="patient-case-list">
    <div v-if="!payload.cases?.length" class="compact-empty">当前没有符合条件的检查</div>
    <div v-else class="case-grid">
      <article v-for="item in payload.cases" :key="item.caseId" class="case-item">
        <div class="case-title">
          <div>
            <strong>{{ item.caseNo }}</strong>
            <span>{{ displayEyeSide(item.eyeSide) }} · {{ formatDateTime(item.updatedAt) }}</span>
          </div>
          <el-tag :type="workflowTagType(item.workflowStatus)" size="small">
            {{ displayWorkflowStatus(item.workflowStatus) }}
          </el-tag>
        </div>

        <div class="quality-line">
          <el-tag :type="patientQualityStatusTagMap[item.qualityStatus] || 'info'" size="small" effect="plain">
            {{ patientQualityStatusTextMap[item.qualityStatus] || '图像质量状态待确认' }}
          </el-tag>
          <span>{{ item.qualityMessage }}</span>
        </div>

        <div class="case-actions">
          <span>{{ item.signedReportAvailable ? '已有正式报告' : '暂无正式报告' }}</span>
          <div>
            <el-button
              v-if="findAction('GO_TO_IMAGE_UPLOAD', item.caseId)"
              size="small"
              @click="emit('action', findAction('GO_TO_IMAGE_UPLOAD', item.caseId)!)"
            >
              重新上传图像
            </el-button>
            <el-button
              v-if="findAction('VIEW_CASE_PROGRESS', item.caseId)"
              size="small"
              type="primary"
              plain
              @click="emit('action', findAction('VIEW_CASE_PROGRESS', item.caseId)!)"
            >
              查看进度
            </el-button>
          </div>
        </div>
      </article>
    </div>

    <div v-if="pagination" class="pagination-bar">
      <span>第 {{ pagination.page }} 页，共 {{ pagination.total }} 条</span>
      <div>
        <el-button
          size="small"
          :disabled="!pagination.hasPrevious || !findAction('PREVIOUS_PAGE')"
          @click="findAction('PREVIOUS_PAGE') && emit('action', findAction('PREVIOUS_PAGE')!)"
        >上一页</el-button>
        <el-button
          size="small"
          :disabled="!pagination.hasNext || !findAction('NEXT_PAGE')"
          @click="findAction('NEXT_PAGE') && emit('action', findAction('NEXT_PAGE')!)"
        >下一页</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.patient-case-list { margin-top: 12px; }
.case-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.case-item { padding: 13px; border: 1px solid #dfe5ec; border-radius: 7px; background: #fbfcfd; }
.case-title, .case-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.case-title > div { display: flex; min-width: 0; flex-direction: column; gap: 3px; }
.case-title strong { overflow: hidden; color: #263244; text-overflow: ellipsis; white-space: nowrap; }
.case-title span, .case-actions > span, .pagination-bar { color: #7a8798; font-size: 12px; }
.quality-line { display: flex; align-items: flex-start; gap: 8px; margin: 12px 0; color: #526173; font-size: 12px; line-height: 1.5; }
.quality-line span { min-width: 0; overflow-wrap: anywhere; }
.case-actions > div { display: flex; gap: 6px; }
.pagination-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-top: 10px; }
.compact-empty { padding: 18px 0; color: #8a95a5; text-align: center; }
@media (max-width: 760px) { .case-grid { grid-template-columns: 1fr; } }
@media (max-width: 460px) { .case-actions { align-items: flex-start; flex-direction: column; } }
</style>
