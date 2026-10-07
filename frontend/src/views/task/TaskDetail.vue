<script setup lang="ts">
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getCaseAnalysisTimeline } from '@/api/case'
import { getImagePreviewBlob } from '@/api/image'
import {
  compareAnalysisResults,
  getResultMaskBlob,
  getTaskDetail,
  getTaskLogs,
  getTaskResult,
} from '@/api/task'
import ClinicalWorkflowPanel from '@/components/ClinicalWorkflowPanel.vue'
import ImagePreview from '@/components/ImagePreview.vue'
import StatusTag from '@/components/StatusTag.vue'
import TaskOverviewPanel from '@/components/TaskOverviewPanel.vue'
import TaskResultPanel from '@/components/TaskResultPanel.vue'
import type { CaseAnalysisTimelineItem } from '@/types/case'
import type {
  AnalysisResult,
  AnalysisResultComparison,
  TaskDetail,
  TaskLogItem,
  TaskStatus,
} from '@/types/task'
import { taskStatusTextMap, taskTypeTextMap } from '@/utils/enums'
import { formatDateTime, formatEmpty } from '@/utils/format'

type DetailTab = 'overview' | 'result' | 'clinical'
type ClinicalStage = 'feedback' | 'correction' | 'draft' | 'review'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const refreshing = ref(false)
const resultLoading = ref(false)
const logsLoading = ref(false)
const detailError = ref('')
const resultError = ref('')
const logsError = ref('')
const taskDetail = ref<TaskDetail | null>(null)
const analysisResult = ref<AnalysisResult | null>(null)
const comparisonCandidates = ref<CaseAnalysisTimelineItem[]>([])
const selectedBaselineResultId = ref<number | null>(null)
const comparisonLoading = ref(false)
const comparisonError = ref('')
const comparison = ref<AnalysisResultComparison | null>(null)
const taskLogs = ref<TaskLogItem[]>([])
const previewVisible = ref(false)
const previewUrl = ref('')
const previewTitle = ref('')
const previewFilename = ref('')
const originalImageUrl = ref('')
const maskImageUrl = ref('')
const activeTab = ref<DetailTab>('overview')
const clinicalStage = ref<ClinicalStage>()

const detailTabs: DetailTab[] = ['overview', 'result', 'clinical']
const clinicalStages: ClinicalStage[] = ['feedback', 'correction', 'draft', 'review']

const taskId = computed(() => {
  const rawTaskId = Array.isArray(route.params.taskId) ? route.params.taskId[0] : route.params.taskId
  const parsedTaskId = Number(rawTaskId)
  return Number.isInteger(parsedTaskId) && parsedTaskId > 0 ? parsedTaskId : null
})

const isUnfinished = computed(() => (
  ['CREATED', 'WAITING', 'RUNNING', 'RETRYING'].includes(taskDetail.value?.status || '')
))

const statusDescription = computed(() => {
  const descriptionMap: Record<TaskStatus, string> = {
    CREATED: '任务已创建，等待进入队列。',
    WAITING: '任务正在排队，等待 Worker 处理。',
    RUNNING: '任务正在分析中，请稍后刷新查看结果。',
    SUCCESS: '任务已完成，可以查看分析结果。',
    FAILED: '任务执行失败，请查看错误原因或返回任务列表重试。',
    RETRYING: '任务正在重新排队。',
    CANCELED: '任务已取消。',
  }
  const status = taskDetail.value?.status
  return status ? descriptionMap[status] : '正在加载任务状态。'
})

const hasClinicalWorkflow = computed(() => analysisResult.value?.resultType === 'VESSEL_SEGMENTATION')
const defaultTab = computed<DetailTab>(() => (
  taskDetail.value?.status === 'SUCCESS' && hasClinicalWorkflow.value ? 'result' : 'overview'
))
const resultJsonText = computed(() => (
  analysisResult.value ? JSON.stringify(analysisResult.value.resultJson || {}, null, 2) : ''
))

const queryValue = (value: unknown) => Array.isArray(value) ? value[0] : value

const navigateWithQuery = (tab: DetailTab, stage?: ClinicalStage, replace = false) => {
  const query = { ...route.query, tab } as Record<string, string | string[] | undefined>
  if (tab === 'clinical' && stage) query.stage = stage
  else delete query.stage
  if (replace) void router.replace({ query })
  else void router.push({ query })
}

const syncNavigationFromRoute = () => {
  if (!taskDetail.value) return
  const requestedTab = queryValue(route.query.tab)
  const validTab = typeof requestedTab === 'string' && detailTabs.includes(requestedTab as DetailTab)
    ? requestedTab as DetailTab
    : null
  const nextTab = validTab === 'clinical' && !hasClinicalWorkflow.value
    ? defaultTab.value
    : validTab || defaultTab.value
  const requestedStage = queryValue(route.query.stage)

  clinicalStage.value = typeof requestedStage === 'string'
    && clinicalStages.includes(requestedStage as ClinicalStage)
    ? requestedStage as ClinicalStage
    : undefined
  activeTab.value = nextTab

  if (requestedTab !== nextTab || (nextTab !== 'clinical' && route.query.stage)) {
    navigateWithQuery(nextTab, clinicalStage.value, true)
  }
}

const handleTabChange = (name: string | number) => {
  const nextTab = String(name) as DetailTab
  activeTab.value = nextTab
  if (nextTab === 'clinical' && !clinicalStage.value) clinicalStage.value = 'feedback'
  navigateWithQuery(nextTab, clinicalStage.value)
}

const handleStageChange = (stage: ClinicalStage) => {
  clinicalStage.value = stage
  if (activeTab.value === 'clinical') navigateWithQuery('clinical', stage)
}

const openPreview = (url: string, title: string, filename = '') => {
  if (!url) return
  previewUrl.value = url
  previewTitle.value = title
  previewFilename.value = filename
  previewVisible.value = true
}

const replaceObjectUrl = (target: typeof originalImageUrl, blob?: Blob) => {
  if (target.value) URL.revokeObjectURL(target.value)
  target.value = blob ? URL.createObjectURL(blob) : ''
}

const loadOriginalImage = async () => {
  replaceObjectUrl(originalImageUrl)
  const imageId = taskDetail.value?.imageFileId
  if (!imageId) return
  const blob = await getImagePreviewBlob(imageId)
  replaceObjectUrl(originalImageUrl, blob)
}

const loadMaskImage = async () => {
  replaceObjectUrl(maskImageUrl)
  const resultId = analysisResult.value?.id
  if (!resultId) return
  const blob = await getResultMaskBlob(resultId)
  replaceObjectUrl(maskImageUrl, blob)
}

const loadComparisonCandidates = async () => {
  comparisonCandidates.value = []
  selectedBaselineResultId.value = null
  comparison.value = null
  comparisonError.value = ''
  const currentResult = analysisResult.value
  const detail = taskDetail.value
  if (!currentResult || !detail || currentResult.resultType !== 'VESSEL_SEGMENTATION') return

  try {
    const timeline = await getCaseAnalysisTimeline(detail.caseId, { taskType: currentResult.resultType })
    comparisonCandidates.value = timeline.items.filter((item) => (
      typeof item.resultId === 'number'
      && item.resultId !== currentResult.id
      && item.taskStatus === 'SUCCESS'
    ))
  } catch (error) {
    comparisonError.value = error instanceof Error ? error.message : '历史结果加载失败'
  }
}

const loadResult = async (id: number) => {
  analysisResult.value = null
  resultError.value = ''
  if (taskDetail.value?.status !== 'SUCCESS') return
  resultLoading.value = true
  try {
    analysisResult.value = await getTaskResult(id)
    await loadMaskImage()
    await loadComparisonCandidates()
  } catch (error) {
    resultError.value = error instanceof Error ? error.message : '分析结果加载失败'
  } finally {
    resultLoading.value = false
  }
}

const loadLogs = async (id: number) => {
  logsLoading.value = true
  logsError.value = ''
  try {
    taskLogs.value = await getTaskLogs(id)
  } catch (error) {
    logsError.value = error instanceof Error ? error.message : '任务日志加载失败'
    taskLogs.value = []
  } finally {
    logsLoading.value = false
  }
}

const loadDetail = async (showRefreshing = false) => {
  if (!taskId.value) {
    ElMessage.error('任务 ID 不合法')
    void router.push('/tasks')
    return
  }
  if (showRefreshing) refreshing.value = true
  else loading.value = true
  detailError.value = ''

  try {
    taskDetail.value = await getTaskDetail(taskId.value)
    await Promise.all([loadOriginalImage(), loadResult(taskId.value), loadLogs(taskId.value)])
    syncNavigationFromRoute()
  } catch (error) {
    detailError.value = error instanceof Error ? error.message : '任务详情加载失败'
    taskDetail.value = null
    analysisResult.value = null
    taskLogs.value = []
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

const handleCompare = async () => {
  const baselineId = selectedBaselineResultId.value
  const targetId = analysisResult.value?.id
  if (!baselineId || !targetId) {
    ElMessage.warning('请选择一个历史结果作为基线')
    return
  }
  comparisonLoading.value = true
  comparisonError.value = ''
  try {
    comparison.value = await compareAnalysisResults(baselineId, targetId)
  } catch (error) {
    comparison.value = null
    comparisonError.value = error instanceof Error ? error.message : '结果对比失败'
  } finally {
    comparisonLoading.value = false
  }
}

const continueSegmentation = () => router.push({
  path: '/tasks/create',
  query: {
    caseId: String(taskDetail.value?.caseId ?? ''),
    imageFileId: String(taskDetail.value?.imageFileId ?? ''),
    taskType: 'VESSEL_SEGMENTATION',
  },
})

const handleRefresh = async () => {
  await loadDetail(true)
  if (!detailError.value) ElMessage.success('任务状态已刷新')
}

onMounted(() => void loadDetail())
watch(() => [route.query.tab, route.query.stage], () => syncNavigationFromRoute())
onBeforeUnmount(() => {
  replaceObjectUrl(originalImageUrl)
  replaceObjectUrl(maskImageUrl)
})
</script>

<template>
  <section class="task-detail-page">
    <div class="page-heading">
      <div>
        <h2>任务详情</h2>
        <p>集中查看任务执行、分析结果与临床处理。</p>
      </div>
      <div class="heading-actions">
        <el-button :icon="ArrowLeft" @click="router.push('/tasks')">返回任务列表</el-button>
        <el-button type="primary" :icon="Refresh" :loading="refreshing" @click="handleRefresh">刷新状态</el-button>
      </div>
    </div>

    <el-alert v-if="detailError" :title="detailError" show-icon type="error" />

    <div v-loading="loading" class="detail-content">
      <el-empty v-if="!loading && !taskDetail && !detailError" description="暂无任务详情" />
      <template v-if="taskDetail">
        <section class="status-summary">
          <div class="status-primary">
            <StatusTag :status="taskDetail.status" />
            <div>
              <div class="status-title">{{ taskStatusTextMap[taskDetail.status] }}</div>
              <div class="status-description">{{ statusDescription }}</div>
            </div>
          </div>
          <div class="summary-facts">
            <div><span>任务编号</span><strong>{{ formatEmpty(taskDetail.taskNo) }}</strong></div>
            <div><span>病例编号</span><strong>{{ formatEmpty(taskDetail.caseNo) }}</strong></div>
            <div><span>任务类型</span><strong>{{ taskTypeTextMap[taskDetail.taskType] }}</strong></div>
            <div><span>完成时间</span><strong>{{ formatDateTime(taskDetail.finishedAt) }}</strong></div>
          </div>
        </section>

        <el-tabs v-model="activeTab" class="detail-tabs" @tab-change="handleTabChange">
          <el-tab-pane label="任务概览" name="overview">
            <TaskOverviewPanel
              :task="taskDetail"
              :original-image-url="originalImageUrl"
              :logs="taskLogs"
              :logs-loading="logsLoading"
              :logs-error="logsError"
              @preview="openPreview"
            />
          </el-tab-pane>
          <el-tab-pane label="分析结果" name="result">
            <TaskResultPanel
              v-model:baseline-result-id="selectedBaselineResultId"
              :task="taskDetail"
              :result="analysisResult"
              :result-loading="resultLoading"
              :result-error="resultError"
              :unfinished="isUnfinished"
              :original-image-url="originalImageUrl"
              :mask-image-url="maskImageUrl"
              :comparison-candidates="comparisonCandidates"
              :comparison-loading="comparisonLoading"
              :comparison-error="comparisonError"
              :comparison="comparison"
              :result-json-text="resultJsonText"
              @preview="openPreview"
              @compare="handleCompare"
              @continue-segmentation="continueSegmentation"
            />
          </el-tab-pane>
          <el-tab-pane label="临床工作台" name="clinical" :disabled="!hasClinicalWorkflow">
            <ClinicalWorkflowPanel
              v-if="analysisResult?.resultType === 'VESSEL_SEGMENTATION'"
              :result-id="analysisResult.id"
              :stage="clinicalStage"
              @stage-change="handleStageChange"
              @workflow-updated="loadLogs(taskDetail.id)"
            />
            <el-empty v-else description="当前任务没有可用的临床工作流" :image-size="80" />
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>

    <ImagePreview v-model="previewVisible" :filename="previewFilename" :image-url="previewUrl" :title="previewTitle" />
  </section>
</template>

<style scoped>
.task-detail-page { display: flex; flex-direction: column; gap: 14px; }
.page-heading, .status-summary, .status-primary, .heading-actions { display: flex; align-items: center; }
.page-heading { align-items: flex-start; justify-content: space-between; gap: 16px; }
.page-heading h2, .page-heading p { margin: 0; }
.page-heading h2 { color: #111827; font-size: 22px; line-height: 30px; }
.page-heading p { margin-top: 4px; color: #6b7280; font-size: 13px; }
.heading-actions { flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.detail-content { min-height: 320px; }
.status-summary { justify-content: space-between; gap: 24px; padding: 14px 16px; border: 1px solid #e5e7eb; border-radius: 8px; background: #ffffff; }
.status-primary { min-width: 250px; gap: 12px; }
.status-title { color: #111827; font-size: 17px; font-weight: 700; }
.status-description { margin-top: 2px; color: #6b7280; font-size: 12px; }
.summary-facts { display: grid; grid-template-columns: repeat(4, minmax(130px, 1fr)); gap: 8px 22px; flex: 1; max-width: 920px; }
.summary-facts span, .summary-facts strong { display: block; }
.summary-facts span { margin-bottom: 3px; color: #6b7280; font-size: 11px; }
.summary-facts strong { overflow: hidden; color: #111827; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.detail-tabs { margin-top: 14px; padding: 0 16px 16px; border: 1px solid #e5e7eb; border-radius: 8px; background: #ffffff; }
.detail-tabs :deep(.el-tabs__header) { margin-bottom: 16px; }
.detail-tabs :deep(.el-tabs__item) { height: 48px; padding: 0 22px; font-weight: 600; }

@media (max-width: 1100px) {
  .status-summary { align-items: flex-start; flex-direction: column; }
  .summary-facts { width: 100%; max-width: none; grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 640px) {
  .page-heading { flex-direction: column; }
  .heading-actions { width: 100%; justify-content: flex-start; }
  .summary-facts { grid-template-columns: 1fr; }
  .detail-tabs { padding: 0 10px 12px; }
  .detail-tabs :deep(.el-tabs__nav-wrap) { overflow-x: auto; }
  .detail-tabs :deep(.el-tabs__item) { padding: 0 14px; }
}
</style>
