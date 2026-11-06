<script setup lang="ts">
import type {
  AgentAction,
  AgentCaseListPayload,
  AgentCaseSummary,
  AgentPagination,
} from '@/types/agent'
import {
  displayEyeSide,
  displayReportStatus,
  displayReviewStatus,
  displayTaskStatus,
  displayWorkflowStatus,
  taskTagType,
  workflowTagType,
} from '@/utils/agent-display'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{
  payload: AgentCaseListPayload
  pagination?: AgentPagination | null
  actions?: AgentAction[]
}>()

const emit = defineEmits<{
  query: [value: string]
  action: [value: AgentAction]
}>()

const paginationAction = (type: AgentAction['type']) =>
  props.actions?.find((item) => item.type === type)

/**
 * 优先使用后端下发的 VIEW_CASE action（携带 targetId），避免翻页后「第 N 个病例」
 * 这类页内序号指向错误数据。后端未提供 action 时，回退为带病例号的精确查询文本。
 */
const viewCase = (item: AgentCaseSummary) => {
  const action = props.actions?.find(
    (candidate) =>
      candidate.type === 'VIEW_CASE' && String(candidate.targetId) === String(item.caseId),
  )
  if (action) emit('action', action)
  else emit('query', `查看病例 ${item.caseNo}`)
}
</script>

<template>
  <div class="case-list-wrap">
    <div v-if="!payload.cases?.length" class="compact-empty">没有符合条件的病例</div>
    <div v-else class="case-list">
      <div class="case-head" aria-hidden="true">
        <span>序号</span>
        <span>病例与患者</span>
        <span>眼别</span>
        <span>流程状态</span>
        <span>分析进度</span>
        <span>最近更新</span>
        <span>操作</span>
      </div>
      <div v-for="(item, index) in payload.cases" :key="item.caseId" class="case-row">
        <span class="case-index">{{ index + 1 }}</span>
        <div class="case-identity">
          <strong>{{ item.caseNo }}</strong>
          <small>{{ item.patientNo }}</small>
        </div>
        <span>{{ displayEyeSide(item.eyeSide) }}</span>
        <el-tag :type="workflowTagType(item.workflowStatus)" size="small" effect="light">
          {{ displayWorkflowStatus(item.workflowStatus) }}
        </el-tag>
        <div class="clinical-state">
          <el-tag :type="taskTagType(item.segmentationStatus)" size="small" effect="plain">
            {{ displayTaskStatus(item.segmentationStatus) }}
          </el-tag>
          <small v-if="item.reviewStatus || item.reportStatus">
            {{ displayReviewStatus(item.reviewStatus) }} ·
            {{ displayReportStatus(item.reportStatus) }}
          </small>
        </div>
        <small>{{ item.updatedAt ? formatDateTime(item.updatedAt) : '暂无数据' }}</small>
        <el-button link type="primary" @click="viewCase(item)"> 查看病例 </el-button>
      </div>
    </div>

    <div v-if="pagination" class="pagination-bar">
      <span>第 {{ pagination.page }} 页，共 {{ pagination.total }} 条</span>
      <div>
        <el-button
          size="small"
          :disabled="!pagination.hasPrevious || !paginationAction('PREVIOUS_PAGE')"
          @click="
            paginationAction('PREVIOUS_PAGE') && emit('action', paginationAction('PREVIOUS_PAGE')!)
          "
        >
          上一页
        </el-button>
        <el-button
          size="small"
          :disabled="!pagination.hasNext || !paginationAction('NEXT_PAGE')"
          @click="paginationAction('NEXT_PAGE') && emit('action', paginationAction('NEXT_PAGE')!)"
        >
          下一页
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped src="./AgentCaseList.css"></style>
