<script setup lang="ts">
import type { TaskLogItem, TaskStatus } from '@/types/task'
import { operatorTypeTextMap, taskStatusTextMap } from '@/utils/enums'
import { formatDateTime, formatEmpty } from '@/utils/format'

defineProps<{
  logs: TaskLogItem[]
}>()

const getStatusText = (status: string | null) => {
  if (!status) {
    return '-'
  }

  return taskStatusTextMap[status as TaskStatus] || status
}

const getTimelineTitle = (log: TaskLogItem) => {
  const toStatus = getStatusText(log.toStatus)

  if (!log.fromStatus || log.fromStatus === log.toStatus) {
    return `${toStatus} · 流程记录`
  }

  return `${getStatusText(log.fromStatus)} -> ${toStatus}`
}
</script>

<template>
  <el-empty v-if="logs.length === 0" description="暂无任务日志" />

  <el-timeline v-else>
    <el-timeline-item
      v-for="log in logs"
      :key="log.id"
      :timestamp="formatDateTime(log.createdAt)"
      placement="top"
    >
      <div class="timeline-item">
        <div class="timeline-title">
          {{ getTimelineTitle(log) }}
        </div>
        <div class="timeline-message">{{ formatEmpty(log.message) }}</div>
        <div class="timeline-meta">
          {{ operatorTypeTextMap[log.operatorType] }}
        </div>
      </div>
    </el-timeline-item>
  </el-timeline>
</template>

<style scoped>
.timeline-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.timeline-title {
  color: #111827;
  font-weight: 600;
}

.timeline-message {
  color: #374151;
  line-height: 22px;
}

.timeline-meta {
  color: #6b7280;
  font-size: 13px;
}
</style>
