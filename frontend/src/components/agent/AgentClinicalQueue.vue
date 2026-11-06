<script setup lang="ts">
import { computed } from 'vue'

import type { AgentAction, AgentClinicalQueuePayload, AgentPagination } from '@/types/agent'
import {
  displayEyeSide,
  displayQualityStatus,
  displayReportStatus,
  displayReviewStatus,
  displayScore,
  qualityTagType,
} from '@/utils/agent-display'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{
  payload: AgentClinicalQueuePayload
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

// 每行预算一次 action，避免模板中同一查找重复执行三次
const rows = computed(() =>
  (props.payload.items ?? []).map((item) => ({
    item,
    action: actionFor('VIEW_REVIEW', item.taskId),
  })),
)
const visibleRows = computed(() => rows.value.slice(0, MAX_VISIBLE_ROWS))
const hasMoreRows = computed(() => rows.value.length > MAX_VISIBLE_ROWS)
const previousAction = computed(() => actionFor('PREVIOUS_PAGE'))
const nextAction = computed(() => actionFor('NEXT_PAGE'))
</script>

<template>
  <div class="queue-wrap">
    <div class="queue-label">
      {{ payload.queueType === 'PENDING_REPORT' ? '已审核待签发' : '待医生审核' }}
    </div>
    <div v-if="!payload.items?.length" class="compact-empty">当前没有符合条件的临床待办</div>
    <div v-else class="queue-list">
      <article v-for="row in visibleRows" :key="row.item.taskId">
        <div class="identity">
          <strong>{{ row.item.caseNo }}</strong
          ><small>{{ row.item.patientNo }} · {{ displayEyeSide(row.item.eyeSide) }}</small>
        </div>
        <div class="quality">
          <span>图像质量</span
          ><el-tag :type="qualityTagType(row.item.qualityStatus)" size="small" effect="plain"
            >{{ displayQualityStatus(row.item.qualityStatus) }} /
            {{ displayScore(row.item.qualityScore) }}</el-tag
          >
        </div>
        <div class="state">
          <span>{{ displayReviewStatus(row.item.reviewStatus) }}</span
          ><small>{{ displayReportStatus(row.item.reportStatus) }}</small>
        </div>
        <time>{{ formatDateTime(row.item.finishedAt || row.item.resultCreatedAt) }}</time>
        <el-button
          link
          type="primary"
          :disabled="!row.action"
          @click="row.action && emit('action', row.action)"
        >
          {{ row.action?.label || '进入工作台' }}
        </el-button>
      </article>
    </div>
    <div v-if="hasMoreRows" class="queue-more">
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

<style scoped src="./AgentClinicalQueue.css"></style>
