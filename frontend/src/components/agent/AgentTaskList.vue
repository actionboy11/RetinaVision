<script setup lang="ts">
import type { AgentAction, AgentPagination, AgentTaskListPayload } from '@/types/agent'
import { displayTaskStatus, displayTaskType, taskTagType } from '@/utils/agent-display'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{
  payload: AgentTaskListPayload
  pagination?: AgentPagination | null
  actions?: AgentAction[]
}>()

const emit = defineEmits<{ action: [value: AgentAction] }>()
const actionFor = (type: AgentAction['type'], targetId?: number | string | null) =>
  props.actions?.find((item) => item.type === type && (targetId == null || String(item.targetId) === String(targetId)))
</script>

<template>
  <div class="task-list-wrap">
    <div v-if="!payload.tasks?.length" class="compact-empty">没有符合条件的任务</div>
    <div v-else class="task-table">
      <div class="task-head" aria-hidden="true">
        <span>序号</span><span>任务与病例</span><span>类型</span><span>状态</span>
        <span>重试</span><span>最近更新</span><span>操作</span>
      </div>
      <div v-for="(item, index) in payload.tasks.slice(0, 10)" :key="item.taskId" class="task-row">
        <span class="row-index">{{ index + 1 }}</span>
        <div class="identity">
          <strong>{{ item.taskNo }}</strong>
          <small>{{ item.caseNo }} · {{ item.patientNo }}</small>
          <small v-if="item.errorSummary" class="error-summary">{{ item.errorSummary }}</small>
        </div>
        <span>{{ displayTaskType(item.taskType) }}</span>
        <el-tag :type="taskTagType(item.status)" size="small" effect="light">
          {{ displayTaskStatus(item.status) }}
        </el-tag>
        <span>{{ item.retryCount }} / {{ item.maxRetryCount }}</span>
        <small>{{ formatDateTime(item.updatedAt) }}</small>
        <el-button
          link
          type="primary"
          :disabled="!actionFor('VIEW_TASK', item.taskId)"
          @click="actionFor('VIEW_TASK', item.taskId) && emit('action', actionFor('VIEW_TASK', item.taskId)!)"
        >
          查看任务
        </el-button>
      </div>
    </div>
    <div v-if="pagination" class="pagination-bar">
      <span>第 {{ pagination.page }} 页，共 {{ pagination.total }} 条</span>
      <div>
        <el-button size="small" :disabled="!pagination.hasPrevious || !actionFor('PREVIOUS_PAGE')"
          @click="actionFor('PREVIOUS_PAGE') && emit('action', actionFor('PREVIOUS_PAGE')!)">上一页</el-button>
        <el-button size="small" :disabled="!pagination.hasNext || !actionFor('NEXT_PAGE')"
          @click="actionFor('NEXT_PAGE') && emit('action', actionFor('NEXT_PAGE')!)">下一页</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.task-list-wrap { margin-top: 12px; }
.task-table { border-top: 1px solid #dfe5ec; }
.task-head, .task-row {
  display: grid;
  grid-template-columns: 42px minmax(190px, 1.45fr) minmax(92px, .8fr) 88px 64px 142px 76px;
  gap: 10px;
  align-items: center;
}
.task-head { padding: 8px 4px; color: #7a8798; font-size: 12px; }
.task-row { min-height: 58px; padding: 8px 4px; border-top: 1px solid #edf0f4; color: #334155; font-size: 13px; }
.row-index { color: #176b87; font-weight: 700; }
.identity { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.identity strong, .identity small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.identity small, .task-row > small, .pagination-bar { color: #7a8798; font-size: 12px; }
.identity .error-summary { color: #c2413b; }
.pagination-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-top: 10px; }
.compact-empty { padding: 18px 0; color: #8a95a5; text-align: center; }
@media (max-width: 900px) {
  .task-head { display: none; }
  .task-row { grid-template-columns: 28px minmax(0, 1fr) auto; gap: 6px 10px; }
  .task-row > :nth-child(n + 3):not(:last-child) { grid-column: 2; }
  .task-row > :last-child { grid-column: 3; grid-row: 1 / span 2; }
}
</style>
