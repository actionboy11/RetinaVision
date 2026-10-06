<script setup lang="ts">
import type { AgentAction, AgentTaskDetail } from '@/types/agent'
import { displayTaskStatus, displayTaskType, taskTagType } from '@/utils/agent-display'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{ detail?: AgentTaskDetail | null; actions?: AgentAction[] }>()
const emit = defineEmits<{ action: [value: AgentAction] }>()
const primaryAction = () => props.actions?.find((item) => item.type === 'VIEW_TASK' || item.type === 'VIEW_REVIEW')
</script>

<template>
  <div v-if="detail" class="task-detail">
    <div class="detail-summary">
      <div><span>任务编号</span><strong>{{ detail.taskNo }}</strong></div>
      <div><span>病例 / 患者</span><strong>{{ detail.caseNo }} · {{ detail.patientNo }}</strong></div>
      <div><span>任务类型</span><strong>{{ displayTaskType(detail.taskType) }}</strong></div>
      <div><span>任务状态</span><el-tag :type="taskTagType(detail.status)" size="small">{{ displayTaskStatus(detail.status) }}</el-tag></div>
      <div><span>重试次数</span><strong>{{ detail.retryCount }} / {{ detail.maxRetryCount }}</strong></div>
      <div><span>最近更新</span><strong>{{ formatDateTime(detail.updatedAt) }}</strong></div>
    </div>
    <p v-if="detail.errorSummary" class="error-summary">{{ detail.errorSummary }}</p>
    <section class="logs">
      <div class="section-heading"><h4>最近任务日志</h4><small>最多显示 5 条</small></div>
      <div v-if="!detail.logs?.length" class="compact-empty">暂无任务日志</div>
      <ol v-else>
        <li v-for="log in detail.logs.slice(0, 5)" :key="`${log.createdAt}-${log.toStatus}`">
          <div><strong>{{ displayTaskStatus(log.fromStatus) }} → {{ displayTaskStatus(log.toStatus) }}</strong><time>{{ formatDateTime(log.createdAt) }}</time></div>
          <p>{{ log.message }}</p>
        </li>
      </ol>
    </section>
    <el-button v-if="primaryAction()" type="primary" plain size="small" @click="emit('action', primaryAction()!)">
      {{ primaryAction()!.label }}
    </el-button>
  </div>
  <div v-else class="compact-empty">暂无任务详情</div>
</template>

<style scoped>
.task-detail { display: grid; gap: 12px; margin-top: 12px; }
.detail-summary { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); border-top: 1px solid #dfe5ec; }
.detail-summary > div { display: flex; min-height: 48px; flex-direction: column; justify-content: center; gap: 4px; border-bottom: 1px solid #edf0f4; }
.detail-summary span, .section-heading small { color: #7a8798; font-size: 11px; }
.detail-summary strong { overflow: hidden; color: #263244; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.error-summary { margin: 0; padding: 8px 10px; border-left: 3px solid #d45b52; background: #fff5f4; color: #a63d37; font-size: 12px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; }
.section-heading h4 { margin: 0; color: #263244; font-size: 13px; }
.logs ol { margin: 8px 0 0; padding: 0; list-style: none; border-top: 1px solid #e4e9ee; }
.logs li { padding: 8px 0; border-bottom: 1px solid #edf0f4; }
.logs li > div { display: flex; justify-content: space-between; gap: 12px; font-size: 12px; }
.logs time { color: #8792a2; }
.logs p { margin: 4px 0 0; color: #5f6d7c; font-size: 12px; line-height: 1.5; }
.task-detail > .el-button { justify-self: end; }
.compact-empty { padding: 18px 0; color: #8a95a5; text-align: center; }
@media (max-width: 680px) { .detail-summary { grid-template-columns: 1fr; } }
</style>
