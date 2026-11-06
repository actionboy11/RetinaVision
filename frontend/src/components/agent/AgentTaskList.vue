<script setup lang="ts">
import { computed } from 'vue'

import type { AgentAction, AgentPagination, AgentTaskListPayload } from '@/types/agent'
import { displayTaskStatus, displayTaskType, taskTagType } from '@/utils/agent-display'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{
  payload: AgentTaskListPayload
  pagination?: AgentPagination | null
  actions?: AgentAction[]
}>()

const emit = defineEmits<{ action: [value: AgentAction] }>()

const MAX_VISIBLE_ROWS = 10

const actionFor = (type: AgentAction['type'], targetId?: number | string | null) =>
  props.actions?.find(
    (item) =>
      item.type === type && (targetId == null || String(item.targetId) === String(targetId)),
  ) ?? null

// 每行预算一次 action，避免模板中重复查找与非空断言
const rows = computed(() =>
  (props.payload.tasks ?? []).map((item) => ({
    item,
    action: actionFor('VIEW_TASK', item.taskId),
  })),
)
const visibleRows = computed(() => rows.value.slice(0, MAX_VISIBLE_ROWS))
const hasMoreRows = computed(() => rows.value.length > MAX_VISIBLE_ROWS)
const previousAction = computed(() => actionFor('PREVIOUS_PAGE'))
const nextAction = computed(() => actionFor('NEXT_PAGE'))
</script>

<template>
  <div class="task-list-wrap">
    <div v-if="!payload.tasks?.length" class="compact-empty">没有符合条件的任务</div>
    <div v-else class="task-table">
      <div class="task-head" aria-hidden="true">
        <span>序号</span><span>任务与病例</span><span>类型</span><span>状态</span> <span>重试</span
        ><span>最近更新</span><span>操作</span>
      </div>
      <div v-for="(row, index) in visibleRows" :key="row.item.taskId" class="task-row">
        <span class="row-index">{{ index + 1 }}</span>
        <div class="identity">
          <strong>{{ row.item.taskNo }}</strong>
          <small>{{ row.item.caseNo }} · {{ row.item.patientNo }}</small>
          <small v-if="row.item.errorSummary" class="error-summary">{{
            row.item.errorSummary
          }}</small>
        </div>
        <span>{{ displayTaskType(row.item.taskType) }}</span>
        <el-tag :type="taskTagType(row.item.status)" size="small" effect="light">
          {{ displayTaskStatus(row.item.status) }}
        </el-tag>
        <span>{{ row.item.retryCount }} / {{ row.item.maxRetryCount }}</span>
        <small>{{ formatDateTime(row.item.updatedAt) }}</small>
        <el-button
          link
          type="primary"
          :disabled="!row.action"
          @click="row.action && emit('action', row.action)"
        >
          查看任务
        </el-button>
      </div>
    </div>
    <div v-if="hasMoreRows" class="task-more">
      仅显示前 {{ MAX_VISIBLE_ROWS }} 条，共 {{ rows.length }} 条
    </div>
    <div v-if="pagination" class="pagination-bar">
      <span>第 {{ pagination.page }} 页，共 {{ pagination.total }} 条</span>
      <div>
        <el-button
          size="small"
          :disabled="!pagination.hasPrevious || !previousAction"
          @click="previousAction && emit('action', previousAction)"
          >上一页</el-button
        >
        <el-button
          size="small"
          :disabled="!pagination.hasNext || !nextAction"
          @click="nextAction && emit('action', nextAction)"
          >下一页</el-button
        >
      </div>
    </div>
  </div>
</template>

<style scoped src="./AgentTaskList.css"></style>
