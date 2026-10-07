<script setup lang="ts">
import type { AgentAction, AgentClinicalQueuePayload, AgentPagination } from '@/types/agent'
import { displayEyeSide, displayQualityStatus, displayReportStatus, displayReviewStatus, displayScore, qualityTagType } from '@/utils/agent-display'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{ payload: AgentClinicalQueuePayload; pagination?: AgentPagination | null; actions?: AgentAction[] }>()
const emit = defineEmits<{ action: [value: AgentAction] }>()
const actionFor = (type: AgentAction['type'], targetId?: number | string | null) =>
  props.actions?.find((item) => item.type === type && (targetId == null || String(item.targetId) === String(targetId)))
</script>

<template>
  <div class="queue-wrap">
    <div class="queue-label">{{ payload.queueType === 'PENDING_REPORT' ? '已审核待签发' : '待医生审核' }}</div>
    <div v-if="!payload.items?.length" class="compact-empty">当前没有符合条件的临床待办</div>
    <div v-else class="queue-list">
      <article v-for="item in payload.items.slice(0, 10)" :key="item.taskId">
        <div class="identity"><strong>{{ item.caseNo }}</strong><small>{{ item.patientNo }} · {{ displayEyeSide(item.eyeSide) }}</small></div>
        <div class="quality"><span>图像质量</span><el-tag :type="qualityTagType(item.qualityStatus)" size="small" effect="plain">{{ displayQualityStatus(item.qualityStatus) }} / {{ displayScore(item.qualityScore) }}</el-tag></div>
        <div class="state"><span>{{ displayReviewStatus(item.reviewStatus) }}</span><small>{{ displayReportStatus(item.reportStatus) }}</small></div>
        <time>{{ formatDateTime(item.finishedAt || item.resultCreatedAt) }}</time>
        <el-button link type="primary" :disabled="!actionFor('VIEW_REVIEW', item.taskId)"
          @click="actionFor('VIEW_REVIEW', item.taskId) && emit('action', actionFor('VIEW_REVIEW', item.taskId)!)">
          {{ actionFor('VIEW_REVIEW', item.taskId)?.label || '进入工作台' }}
        </el-button>
      </article>
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
.queue-wrap { margin-top: 12px; }
.queue-label { display: inline-flex; padding: 3px 7px; border: 1px solid #cbdce3; border-radius: 4px; color: #476875; font-size: 11px; }
.queue-list { margin-top: 8px; border-top: 1px solid #dfe5ec; }
.queue-list article { display: grid; grid-template-columns: minmax(150px, 1.2fr) minmax(150px, 1fr) 100px 142px 86px; gap: 12px; align-items: center; min-height: 58px; border-bottom: 1px solid #edf0f4; font-size: 12px; }
.identity, .quality, .state { display: flex; min-width: 0; flex-direction: column; align-items: flex-start; gap: 3px; }
.identity strong, .identity small { overflow: hidden; max-width: 100%; text-overflow: ellipsis; white-space: nowrap; }
.identity strong { color: #263244; font-size: 13px; }
.identity small, .quality > span, .state small, time, .pagination-bar { color: #7a8798; font-size: 11px; }
.pagination-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-top: 10px; }
.compact-empty { padding: 18px 0; color: #8a95a5; text-align: center; }
@media (max-width: 760px) {
  .queue-list article { grid-template-columns: minmax(0, 1fr) auto; gap: 7px 12px; padding: 10px 0; }
  .queue-list article > :not(:first-child):not(:last-child) { grid-column: 1; }
  .queue-list article > :last-child { grid-column: 2; grid-row: 1 / span 2; }
}
</style>
