import { ElMessage } from '@/plugins/element-plus-api'
import { computed, onMounted, ref } from 'vue'

import { getQueueStatistics, getTaskStatistics, getTaskTrend } from '@/api/statistics'
import { getSystemStatus } from '@/api/system'
import type { QueueStatistics, TaskStatistics, TaskTrendItem } from '@/types/statistics'
import type { SystemStatus } from '@/types/system'
import { EMPTY_TEXT, formatPercent } from '@/utils/format'

const setError = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : fallback

/** 统计看板的逻辑层：并行拉取四类统计、逐项降级错误、派生卡片数据。 */
export const useDashboard = () => {
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
    if (rate === null || rate === undefined) return 0
    return Math.round(rate * 100)
  })

  const statCards = computed(() => [
    { label: '今日提交', value: taskStatistics.value?.todaySubmittedCount, tone: 'primary' },
    { label: '今日成功', value: taskStatistics.value?.todaySuccessCount, tone: 'success' },
    { label: '今日失败', value: taskStatistics.value?.todayFailedCount, tone: 'danger' },
    { label: '等待中', value: taskStatistics.value?.waitingCount, tone: 'warning' },
    { label: '运行中', value: taskStatistics.value?.runningCount, tone: 'primary' },
    { label: '总任务数', value: taskStatistics.value?.totalTaskCount, tone: 'neutral' },
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
    if (!row.submittedCount) return EMPTY_TEXT
    return formatPercent(row.successCount / row.submittedCount)
  }

  const loadDashboard = async (isRefresh = false) => {
    if (isRefresh) refreshing.value = true
    else loading.value = true

    taskStatsError.value = ''
    queueStatsError.value = ''
    trendError.value = ''
    systemStatusError.value = ''

    const [taskStatsResult, queueStatsResult, trendResult, systemStatusResult] =
      await Promise.allSettled([
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

  return {
    loading,
    refreshing,
    taskStatistics,
    queueStatistics,
    taskTrend,
    systemStatus,
    taskStatsError,
    queueStatsError,
    trendError,
    systemStatusError,
    successRatePercent,
    statCards,
    queueCards,
    getTrendSuccessRate,
    handleRefresh,
  }
}
