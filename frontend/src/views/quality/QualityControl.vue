<script setup lang="ts">
import { Refresh, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import {
  getModelPerformance,
  getQualityOverview,
  getReviewStatistics,
  getRiskAlerts,
} from '@/api/quality-control'
import type {
  ModelPerformanceItem,
  QualityControlOverview,
  ReviewStatistics,
  RiskAlertItem,
} from '@/types/quality-control'
import { taskTypeTextMap } from '@/utils/enums'
import {
  EMPTY_TEXT,
  formatDurationMs,
  formatEmpty,
  formatPercent,
} from '@/utils/format'

const router = useRouter()

const loading = ref(false)
const refreshing = ref(false)
const overview = ref<QualityControlOverview | null>(null)
const reviewStatistics = ref<ReviewStatistics | null>(null)
const modelPerformance = ref<ModelPerformanceItem[]>([])
const riskAlerts = ref<RiskAlertItem[]>([])
const errors = ref<string[]>([])

const overviewCards = computed(() => [
  { label: '总任务数', value: overview.value?.taskTotalCount, tone: 'neutral' },
  { label: '成功率', value: formatPercent(overview.value?.successRate), tone: 'success' },
  { label: '失败率', value: formatPercent(overview.value?.failedRate), tone: 'danger' },
  { label: '平均耗时', value: formatDurationMs(overview.value?.averageProcessingTimeMs), tone: 'primary' },
  { label: '重试任务', value: overview.value?.retryTaskCount, tone: 'warning' },
  { label: '已签发报告', value: overview.value?.signedReportCount, tone: 'success' },
  { label: '已审核未签发', value: overview.value?.approvedUnsignedCount, tone: 'warning' },
  { label: '血管分割结果', value: overview.value?.vesselResultCount, tone: 'primary' },
])

const qualityRows = computed(() => distributionRows(overview.value?.qualityDistribution))
const reviewRows = computed(() => distributionRows(reviewStatistics.value?.reviewDistribution))

const vesselRiskText = computed(() => {
  if (!overview.value) {
    return EMPTY_TEXT
  }
  return `低值 ${overview.value.abnormalLowVesselRatioCount} / 高值 ${overview.value.abnormalHighVesselRatioCount}`
})

const loadData = async (isRefresh = false) => {
  if (isRefresh) {
    refreshing.value = true
  } else {
    loading.value = true
  }
  errors.value = []

  const [overviewResult, reviewResult, modelResult, riskResult] = await Promise.allSettled([
    getQualityOverview(),
    getReviewStatistics(),
    getModelPerformance(),
    getRiskAlerts(),
  ])

  if (overviewResult.status === 'fulfilled') {
    overview.value = overviewResult.value
  } else {
    overview.value = null
    errors.value.push(errorText(overviewResult.reason, '质控总览加载失败'))
  }

  if (reviewResult.status === 'fulfilled') {
    reviewStatistics.value = reviewResult.value
  } else {
    reviewStatistics.value = null
    errors.value.push(errorText(reviewResult.reason, '审核统计加载失败'))
  }

  if (modelResult.status === 'fulfilled') {
    modelPerformance.value = modelResult.value
  } else {
    modelPerformance.value = []
    errors.value.push(errorText(modelResult.reason, '模型表现加载失败'))
  }

  if (riskResult.status === 'fulfilled') {
    riskAlerts.value = riskResult.value
  } else {
    riskAlerts.value = []
    errors.value.push(errorText(riskResult.reason, '风险提醒加载失败'))
  }

  loading.value = false
  refreshing.value = false
  if (isRefresh) {
    ElMessage[errors.value.length ? 'warning' : 'success'](
      errors.value.length ? '部分质控数据刷新失败' : 'AI 质控数据已刷新',
    )
  }
}

const goTask = (taskId: number | null) => {
  if (!taskId) {
    return
  }
  void router.push(`/tasks/${taskId}`)
}

const errorText = (error: unknown, fallback: string) => (
  error instanceof Error ? error.message : fallback
)

const distributionRows = (source?: Record<string, number>) => Object.entries(source || {}).map(([name, count]) => ({
  name,
  count,
}))

const riskTagType = (level: string) => {
  if (level === 'CRITICAL') return 'danger'
  if (level === 'WARNING') return 'warning'
  return 'info'
}

onMounted(() => {
  void loadData()
})
</script>

<template>
  <section class="quality-page">
    <div class="page-heading">
      <div>
        <h2>AI 质控</h2>
        <p>追踪 AI 任务、模型版本、医生审核反馈和高风险结果，用于系统评估与复核管理。</p>
      </div>
      <el-button type="primary" :icon="Refresh" :loading="refreshing" @click="loadData(true)">
        刷新数据
      </el-button>
    </div>

    <div v-loading="loading" class="quality-content">
      <div v-if="errors.length" class="alert-stack">
        <el-alert
          v-for="error in errors"
          :key="error"
          :title="error"
          show-icon
          type="warning"
          :closable="false"
        />
      </div>

      <section class="stat-grid">
        <div
          v-for="card in overviewCards"
          :key="card.label"
          class="stat-card"
          :class="`tone-${card.tone}`"
        >
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value">{{ typeof card.value === 'number' ? formatEmpty(card.value) : card.value }}</div>
        </div>
      </section>

      <section class="two-column">
        <div class="panel">
          <div class="panel-title">图像质量分布</div>
          <el-table :data="qualityRows" border empty-text="暂无质量统计">
            <el-table-column prop="name" label="状态" />
            <el-table-column prop="count" label="数量" />
          </el-table>
        </div>

        <div class="panel">
          <div class="panel-title">医生审核反馈</div>
          <div class="review-rate">
            <div>
              <span>通过率</span>
              <strong>{{ formatPercent(reviewStatistics?.approvedRate) }}</strong>
            </div>
            <div>
              <span>需修改率</span>
              <strong>{{ formatPercent(reviewStatistics?.needsChangeRate) }}</strong>
            </div>
            <div>
              <span>拒绝率</span>
              <strong>{{ formatPercent(reviewStatistics?.rejectedRate) }}</strong>
            </div>
          </div>
          <el-table :data="reviewRows" border empty-text="暂无审核统计">
            <el-table-column prop="name" label="状态" />
            <el-table-column prop="count" label="数量" />
          </el-table>
        </div>
      </section>

      <section class="panel">
        <div class="panel-title">模型版本效果对比</div>
        <el-alert
          class="panel-alert"
          type="info"
          show-icon
          :closable="false"
          title="不同模型版本、图像质量和样本量会影响比较结果；该表用于质控管理，不构成临床结论。"
        />
        <el-table :data="modelPerformance" border empty-text="暂无模型表现数据">
          <el-table-column label="模型" min-width="170">
            <template #default="{ row }: { row: ModelPerformanceItem }">
              {{ row.modelName }} / {{ row.modelVersion }}
            </template>
          </el-table-column>
          <el-table-column label="结果类型" min-width="150">
            <template #default="{ row }: { row: ModelPerformanceItem }">
              {{ taskTypeTextMap[row.resultType as keyof typeof taskTypeTextMap] || row.resultType }}
            </template>
          </el-table-column>
          <el-table-column prop="resultCount" label="结果数" min-width="90" />
          <el-table-column label="成功率" min-width="100">
            <template #default="{ row }: { row: ModelPerformanceItem }">{{ formatPercent(row.successRate) }}</template>
          </el-table-column>
          <el-table-column label="平均耗时" min-width="110">
            <template #default="{ row }: { row: ModelPerformanceItem }">{{ formatDurationMs(row.averageProcessingTimeMs) }}</template>
          </el-table-column>
          <el-table-column label="平均血管比例" min-width="130">
            <template #default="{ row }: { row: ModelPerformanceItem }">{{ formatPercent(row.averageVesselAreaRatio) }}</template>
          </el-table-column>
          <el-table-column label="审核通过率" min-width="120">
            <template #default="{ row }: { row: ModelPerformanceItem }">{{ formatPercent(row.reviewApprovedRate) }}</template>
          </el-table-column>
          <el-table-column label="需修改率" min-width="110">
            <template #default="{ row }: { row: ModelPerformanceItem }">{{ formatPercent(row.reviewNeedsChangeRate) }}</template>
          </el-table-column>
          <el-table-column label="失败率" min-width="100">
            <template #default="{ row }: { row: ModelPerformanceItem }">{{ formatPercent(row.failedRate) }}</template>
          </el-table-column>
        </el-table>
      </section>

      <section class="panel">
        <div class="risk-heading">
          <div>
            <div class="panel-title">高风险结果提醒</div>
            <p>血管面积比例异常：{{ vesselRiskText }}</p>
          </div>
          <el-icon><WarningFilled /></el-icon>
        </div>
        <el-table :data="riskAlerts" border empty-text="暂无风险提醒">
          <el-table-column label="级别" min-width="100">
            <template #default="{ row }: { row: RiskAlertItem }">
              <el-tag :type="riskTagType(row.level)">{{ row.level }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" min-width="230" />
          <el-table-column prop="taskNo" label="任务编号" min-width="150" />
          <el-table-column label="任务类型" min-width="150">
            <template #default="{ row }: { row: RiskAlertItem }">
              {{ row.taskType ? taskTypeTextMap[row.taskType as keyof typeof taskTypeTextMap] || row.taskType : '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" min-width="110" />
          <el-table-column prop="createdAt" label="时间" min-width="170" />
          <el-table-column fixed="right" label="操作" min-width="100">
            <template #default="{ row }: { row: RiskAlertItem }">
              <el-button link type="primary" :disabled="!row.taskId" @click="goTask(row.taskId)">
                查看任务
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>
  </section>
</template>

<style scoped>
.quality-page {
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

.page-heading p,
.risk-heading p {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 14px;
}

.quality-content {
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.stat-card,
.panel {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.stat-card {
  padding: 16px;
}

.stat-label {
  color: #6b7280;
  font-size: 13px;
}

.stat-value {
  margin-top: 10px;
  color: #111827;
  font-size: 24px;
  font-weight: 700;
  line-height: 32px;
}

.tone-primary { border-color: #bfdbfe; }
.tone-success { border-color: #bbf7d0; }
.tone-warning { border-color: #fde68a; }
.tone-danger { border-color: #fecaca; }

.two-column {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.panel {
  margin-top: 16px;
  padding: 18px;
}

.two-column .panel {
  margin-top: 0;
}

.panel-title {
  margin-bottom: 14px;
  color: #111827;
  font-size: 16px;
  font-weight: 600;
}

.panel-alert {
  margin-bottom: 14px;
}

.review-rate {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.review-rate div {
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f9fafb;
}

.review-rate span {
  display: block;
  color: #6b7280;
  font-size: 12px;
}

.review-rate strong {
  display: block;
  margin-top: 6px;
  color: #111827;
  font-size: 20px;
}

.risk-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.risk-heading .el-icon {
  color: #f59e0b;
  font-size: 24px;
}

@media (max-width: 1080px) {
  .stat-grid,
  .two-column {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .page-heading,
  .risk-heading {
    flex-direction: column;
  }

  .stat-grid,
  .two-column,
  .review-rate {
    grid-template-columns: 1fr;
  }
}
</style>
