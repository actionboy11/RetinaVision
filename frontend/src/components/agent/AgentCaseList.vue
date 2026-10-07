<script setup lang="ts">
import type {
  AgentAction,
  AgentCaseListPayload,
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
            {{ displayReviewStatus(item.reviewStatus) }} · {{ displayReportStatus(item.reportStatus) }}
          </small>
        </div>
        <small>{{ item.updatedAt ? formatDateTime(item.updatedAt) : '暂无数据' }}</small>
        <el-button link type="primary" @click="emit('query', `查看第 ${index + 1} 个病例`)">
          查看病例
        </el-button>
      </div>
    </div>

    <div v-if="pagination" class="pagination-bar">
      <span>第 {{ pagination.page }} 页，共 {{ pagination.total }} 条</span>
      <div>
        <el-button
          size="small"
          :disabled="!pagination.hasPrevious || !paginationAction('PREVIOUS_PAGE')"
          @click="paginationAction('PREVIOUS_PAGE') && emit('action', paginationAction('PREVIOUS_PAGE')!)"
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

<style scoped>
.case-list-wrap {
  margin-top: 12px;
}

.case-list {
  border-top: 1px solid #dfe5ec;
}

.case-head,
.case-row {
  display: grid;
  grid-template-columns: 46px minmax(160px, 1.35fr) 64px minmax(92px, .8fr) minmax(92px, .8fr) 142px 74px;
  gap: 10px;
  align-items: center;
}

.case-head {
  padding: 8px 4px;
  color: #7a8798;
  font-size: 12px;
}

.case-row {
  min-height: 56px;
  padding: 8px 4px;
  border-top: 1px solid #edf0f4;
  color: #334155;
  font-size: 13px;
}

.case-index {
  color: #176b87;
  font-weight: 700;
}

.case-identity {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.clinical-state {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
}

.clinical-state small {
  overflow: hidden;
  max-width: 100%;
  color: #7a8798;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.case-identity strong,
.case-identity small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.case-identity small,
.case-row > small,
.pagination-bar {
  color: #7a8798;
  font-size: 12px;
}

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 10px;
}

.compact-empty {
  padding: 18px 0;
  color: #8a95a5;
  text-align: center;
}

@media (max-width: 900px) {
  .case-head {
    display: none;
  }

  .case-row {
    grid-template-columns: 28px minmax(0, 1fr) auto;
    gap: 6px 10px;
  }

  .case-row > :nth-child(n + 3):not(:last-child) {
    grid-column: 2;
  }

  .case-row > :last-child {
    grid-column: 3;
    grid-row: 1 / span 2;
  }
}
</style>
