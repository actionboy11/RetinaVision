<script setup lang="ts">
import { computed } from 'vue'

import type { AgentAction, AgentTaskDetail } from '@/types/agent'
import { displayTaskStatus, displayTaskType, taskTagType } from '@/utils/agent-display'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{ detail?: AgentTaskDetail | null; actions?: AgentAction[] }>()
const emit = defineEmits<{ action: [value: AgentAction] }>()

const primaryAction = computed(
  () =>
    props.actions?.find((item) => item.type === 'VIEW_TASK' || item.type === 'VIEW_REVIEW') ?? null,
)
</script>

<template>
  <div v-if="detail" class="task-detail">
    <div class="detail-summary">
      <div>
        <span>任务编号</span><strong>{{ detail.taskNo }}</strong>
      </div>
      <div>
        <span>病例 / 患者</span><strong>{{ detail.caseNo }} · {{ detail.patientNo }}</strong>
      </div>
      <div>
        <span>任务类型</span><strong>{{ displayTaskType(detail.taskType) }}</strong>
      </div>
      <div>
        <span>任务状态</span
        ><el-tag :type="taskTagType(detail.status)" size="small">{{
          displayTaskStatus(detail.status)
        }}</el-tag>
      </div>
      <div>
        <span>重试次数</span><strong>{{ detail.retryCount }} / {{ detail.maxRetryCount }}</strong>
      </div>
      <div>
        <span>最近更新</span><strong>{{ formatDateTime(detail.updatedAt) }}</strong>
      </div>
    </div>
    <p v-if="detail.errorSummary" class="error-summary">{{ detail.errorSummary }}</p>
    <section class="logs">
      <div class="section-heading">
        <h4>最近任务日志</h4>
        <small>最多显示 5 条</small>
      </div>
      <div v-if="!detail.logs?.length" class="compact-empty">暂无任务日志</div>
      <ol v-else>
        <li
          v-for="(log, index) in detail.logs.slice(0, 5)"
          :key="`${index}-${log.createdAt}-${log.toStatus}`"
        >
          <div>
            <strong
              >{{ displayTaskStatus(log.fromStatus) }} →
              {{ displayTaskStatus(log.toStatus) }}</strong
            ><time>{{ formatDateTime(log.createdAt) }}</time>
          </div>
          <p>{{ log.message }}</p>
        </li>
      </ol>
    </section>
    <el-button
      v-if="primaryAction"
      type="primary"
      plain
      size="small"
      @click="primaryAction && emit('action', primaryAction)"
    >
      {{ primaryAction?.label }}
    </el-button>
  </div>
  <div v-else class="compact-empty">暂无任务详情</div>
</template>

<style scoped src="./AgentTaskDetail.css"></style>
