<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'

import {
  getQueueStatistics,
  getTaskStatistics,
  getTaskTrend,
} from '@/api/statistics'
import { getSystemStatus } from '@/api/system'
import SystemStatusPanel from '@/components/SystemStatusPanel.vue'
import type {
  QueueStatistics,
  TaskStatistics,
  TaskTrendItem,
} from '@/types/statistics'
import type { SystemStatus } from '@/types/system'
import {
  EMPTY_TEXT,
  formatDurationMs,
  formatEmpty,
  formatPercent,
} from '@/utils/format'

const loading = ref(false)
const refreshing = ref(false)
const taskStatistics = ref<TaskStatistics | null>(null)
const queueStatistics = ref<QueueStatistics | null>(null)
const taskTrend = ref<TaskTrendItem[]>([])
const systemStatus = ref<SystemStatus | null>(null)
const taskStatsError = ref('')
const queueStatsError = ref('')
const trendError = ref('')
const systemStatusError = ref('')

const successRatePercent = computed(() => {
  const rate = taskStatistics.value?.successRate

  if (rate === null || rate === undefined) {
    return 0
  }

  return Math.round(rate * 100)
})

const statCards = computed(() => [
  {
    label: '今日提交',
    value: taskStatistics.value?.todaySubmittedCount,
    tone: 'primary',
  },
  {
    label: '今日成功',
    value: taskStatistics.value?.todaySuccessCount,
    tone: 'success',
  },
  {
    label: '今日失败',
    value: taskStatistics.value?.todayFailedCount,
    tone: 'danger',
  },
  {
    label: '等待中',
    value: taskStatistics.value?.waitingCount,
    tone: 'warning',
  },
  {
    label: '运行中',
    value: taskStatistics.value?.runningCount,
    tone: 'primary',
  },
  {
    label: '总任务数',
    value: taskStatistics.value?.totalTaskCount,
    tone: 'neutral',
  },
])

const queueCards = computed(() => [
  {
    label: '待消费消息',
    value: queueStatistics.value?.messageReadyCount,
    description: '队列中等待 Worker 消费的任务',
    tone: 'warning',
  },
  {
    label: '未确认消息',
    value: queueStatistics.value?.messageUnackedCount,
    description: '正在处理但尚未确认的消息',
    tone: 'primary',
  },
  {
    label: '消费者数量',
    value: queueStatistics.value?.consumerCount,
    description: '当前接入队列的 Worker 数量',
    tone: 'success',
  },
  {
    label: '死信数量',
    value: queueStatistics.value?.deadLetterCount,
    description: '失败后进入死信队列的任务',
    tone: queueStatistics.value?.deadLetterCount ? 'danger' : 'neutral',
  },
])

const getTrendSuccessRate = (row: TaskTrendItem) => {
  if (!row.submittedCount) {
    return EMPTY_TEXT
  }

  return formatPercent(row.successCount / row.submittedCount)
}

const setError = (error: unknown, fallback: string) => {
  return error instanceof Error ? error.message : fallback
}

const loadDashboard = async (isRefresh = false) => {
  if (isRefresh) {
    refreshing.value = true
  } else {
    loading.value = true
  }

  taskStatsError.value = ''
  queueStatsError.value = ''
  trendError.value = ''
  systemStatusError.value = ''

  const [taskStatsResult, queueStatsResult, trendResult, systemStatusResult] = await Promise.allSettled([
    getTaskStatistics(),
    getQueueStatistics(),
    getTaskTrend(7),
    getSystemStatus(),
  ])

  if (taskStatsResult.status === 'fulfilled') {
    taskStatistics.value = taskStatsResult.value
  } else {
    taskStatsError.value = setError(taskStatsResult.reason, '任务统计加载失败')
    taskStatistics.value = null
  }

  if (queueStatsResult.status === 'fulfilled') {
    queueStatistics.value = queueStatsResult.value
  } else {
    queueStatsError.value = setError(queueStatsResult.reason, '队列统计加载失败')
    queueStatistics.value = null
  }

  if (trendResult.status === 'fulfilled') {
    taskTrend.value = trendResult.value || []
  } else {
    trendError.value = setError(trendResult.reason, '任务趋势加载失败')
    taskTrend.value = []
  }

  if (systemStatusResult.status === 'fulfilled') {
    systemStatus.value = systemStatusResult.value
  } else {
    systemStatusError.value = setError(systemStatusResult.reason, '系统运行状态加载失败')
  }

  loading.value = false
  refreshing.value = false

  if (isRefresh) {
    const hasError =
      Boolean(taskStatsError.value) ||
      Boolean(queueStatsError.value) ||
      Boolean(trendError.value) ||
      Boolean(systemStatusError.value)

    ElMessage[hasError ? 'warning' : 'success'](
      hasError ? '部分统计数据刷新失败' : '统计数据已刷新',
    )
  }
}

const handleRefresh = () => {
  void loadDashboard(true)
}

onMounted(() => {
  void loadDashboard()
})
</script>

<template>
  <section class="dashboard-page">
    <div class="page-heading">
      <div>
        <h2>Dashboard</h2>
        <p>展示任务运行状态、队列积压情况和系统处理效率。</p>
      </div>
      <el-button
        type="primary"
        :icon="Refresh"
        :loading="refreshing"
        @click="handleRefresh"
      >
        刷新数据
      </el-button>
    </div>

    <div v-loading="loading" class="dashboard-content">
      <div class="alert-stack">
        <el-alert
          v-if="taskStatsError"
          :title="taskStatsError"
          show-icon
          type="error"
          :closable="false"
        />
        <el-alert
          v-if="queueStatsError"
          :title="queueStatsError"
          show-icon
          type="error"
          :closable="false"
        />
        <el-alert
          v-if="trendError"
          :title="trendError"
          show-icon
          type="error"
          :closable="false"
        />
      </div>

      <SystemStatusPanel
        :status="systemStatus"
        :loading="loading"
        :error="systemStatusError"
      />

      <section class="stat-grid">
        <div
          v-for="card in statCards"
          :key="card.label"
          class="stat-card"
          :class="`tone-${card.tone}`"
        >
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value">{{ formatEmpty(card.value) }}</div>
        </div>
      </section>

      <section class="two-column">
        <div class="panel">
          <div class="panel-title">任务成功率</div>
          <div class="success-rate-box">
            <el-progress
              type="dashboard"
              :percentage="successRatePercent"
              :stroke-width="12"
              status="success"
            />
            <div class="success-rate-info">
              <div class="success-rate-label">任务成功率</div>
              <div class="success-rate-value">
                {{ formatPercent(taskStatistics?.successRate) }}
              </div>
              <div class="success-rate-meta">
                平均处理耗时：
                {{ formatDurationMs(taskStatistics?.averageProcessingTimeMs) }}
              </div>
            </div>
          </div>
        </div>

        <div class="panel">
          <div class="panel-title">队列统计</div>
          <div class="queue-name">
            队列名称：{{ formatEmpty(queueStatistics?.queueName) }}
          </div>
          <div class="queue-grid">
            <div
              v-for="card in queueCards"
              :key="card.label"
              class="queue-card"
              :class="`tone-${card.tone}`"
            >
              <div class="queue-label">{{ card.label }}</div>
              <div class="queue-value">{{ formatEmpty(card.value) }}</div>
              <div class="queue-desc">{{ card.description }}</div>
            </div>
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="panel-title">最近 7 天任务趋势</div>
        <el-table
          :data="taskTrend"
          border
          empty-text="暂无趋势数据"
        >
          <el-table-column label="日期" min-width="140" prop="date" />
          <el-table-column label="提交数" min-width="120">
            <template #default="{ row }: { row: TaskTrendItem }">
              {{ formatEmpty(row.submittedCount) }}
            </template>
          </el-table-column>
          <el-table-column label="成功数" min-width="120">
            <template #default="{ row }: { row: TaskTrendItem }">
              {{ formatEmpty(row.successCount) }}
            </template>
          </el-table-column>
          <el-table-column label="失败数" min-width="120">
            <template #default="{ row }: { row: TaskTrendItem }">
              {{ formatEmpty(row.failedCount) }}
            </template>
          </el-table-column>
          <el-table-column label="成功率" min-width="120">
            <template #default="{ row }: { row: TaskTrendItem }">
              {{ getTrendSuccessRate(row) }}
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>
  </section>
</template>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-heading h2 {
  margin: 0;
  color: #111827;
  font-size: 22px;
  line-height: 30px;
}

.page-heading p {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 14px;
}

.dashboard-content {
  min-height: 420px;
}

.alert-stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 14px;
}

.stat-card,
.panel,
.queue-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.stat-card {
  padding: 16px;
}

.stat-label,
.queue-label {
  color: #6b7280;
  font-size: 13px;
}

.stat-value {
  margin-top: 10px;
  color: #111827;
  font-size: 26px;
  font-weight: 700;
  line-height: 34px;
}

.tone-primary {
  border-color: #bfdbfe;
}

.tone-success {
  border-color: #bbf7d0;
}

.tone-warning {
  border-color: #fde68a;
}

.tone-danger {
  border-color: #fecaca;
}

.two-column {
  display: grid;
  grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr);
  gap: 16px;
  margin-top: 16px;
}

.panel {
  padding: 18px;
}

.panel-title {
  margin-bottom: 14px;
  color: #111827;
  font-size: 16px;
  font-weight: 600;
}

.success-rate-box {
  display: flex;
  align-items: center;
  gap: 22px;
}

.success-rate-label {
  color: #6b7280;
  font-size: 14px;
}

.success-rate-value {
  margin-top: 6px;
  color: #111827;
  font-size: 28px;
  font-weight: 700;
}

.success-rate-meta {
  margin-top: 10px;
  color: #6b7280;
  font-size: 14px;
}

.queue-name {
  margin-bottom: 14px;
  color: #374151;
  font-size: 14px;
}

.queue-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.queue-card {
  padding: 14px;
}

.queue-value {
  margin-top: 8px;
  color: #111827;
  font-size: 24px;
  font-weight: 700;
  line-height: 32px;
}

.queue-desc {
  margin-top: 8px;
  color: #6b7280;
  font-size: 12px;
  line-height: 18px;
}

.panel + .panel {
  margin-top: 16px;
}

@media (max-width: 1180px) {
  .stat-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .two-column,
  .queue-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .page-heading,
  .success-rate-box {
    flex-direction: column;
    align-items: stretch;
  }

  .stat-grid {
    grid-template-columns: 1fr;
  }
}
</style>
