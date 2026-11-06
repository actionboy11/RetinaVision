<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'

import SystemStatusPanel from '@/components/SystemStatusPanel.vue'
import { useDashboard } from '@/composables/use-dashboard'
import type { TaskTrendItem } from '@/types/statistics'
import { formatDurationMs, formatEmpty, formatPercent } from '@/utils/format'

const {
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
} = useDashboard()
</script>

<template>
  <section class="dashboard-page">
    <div class="page-heading">
      <div>
        <h2>Dashboard</h2>
        <p>展示任务运行状态、队列积压情况和系统处理效率。</p>
      </div>
      <el-button type="primary" :icon="Refresh" :loading="refreshing" @click="handleRefresh">
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
        <el-alert v-if="trendError" :title="trendError" show-icon type="error" :closable="false" />
      </div>

      <SystemStatusPanel :status="systemStatus" :loading="loading" :error="systemStatusError" />

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
          <div class="queue-name">队列名称：{{ formatEmpty(queueStatistics?.queueName) }}</div>
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
        <el-table :data="taskTrend" border empty-text="暂无趋势数据">
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

<style scoped src="./Dashboard.css"></style>
