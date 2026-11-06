import { ElMessage } from '@/plugins/element-plus-api'
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
import type { CaseAnalysisTimelineItem } from '@/types/case'
import type {
  AnalysisResult,
  AnalysisResultComparison,
  TaskDetail,
  TaskLogItem,
  TaskStatus,
} from '@/types/task'
import { ignoreHandledError } from '@/utils/async-action'
import type { WorkflowStage } from '@/utils/clinical-workflow'

type DetailTab = 'overview' | 'result' | 'clinical'

const detailTabs: DetailTab[] = ['overview', 'result', 'clinical']
const clinicalStages: WorkflowStage[] = ['feedback', 'correction', 'draft', 'review']

const statusDescriptionMap: Record<TaskStatus, string> = {
  CREATED: '任务已创建，等待进入队列。',
  WAITING: '任务正在排队，等待 Worker 处理。',
  RUNNING: '任务正在分析中，请稍后刷新查看结果。',
  SUCCESS: '任务已完成，可以查看分析结果。',
  FAILED: '任务执行失败，请查看错误原因或返回任务列表重试。',
  RETRYING: '任务正在重新排队。',
  CANCELED: '任务已取消。',
}

/**
 * 任务详情页的逻辑层：详情/结果/日志加载、图片 Blob URL 生命周期、历史结果对比与
 * URL query 驱动的页签同步。组件只保留面板编排与展示映射。
 */
export const useTaskDetail = () => {
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
  const clinicalStage = ref<WorkflowStage>()

  const taskId = computed(() => {
    const rawTaskId = Array.isArray(route.params.taskId)
      ? route.params.taskId[0]
      : route.params.taskId
    const parsedTaskId = Number(rawTaskId)
    return Number.isInteger(parsedTaskId) && parsedTaskId > 0 ? parsedTaskId : null
  })

  const isUnfinished = computed(() =>
    ['CREATED', 'WAITING', 'RUNNING', 'RETRYING'].includes(taskDetail.value?.status || ''),
  )

  const statusDescription = computed(() => {
    const status = taskDetail.value?.status
    return status ? statusDescriptionMap[status] : '正在加载任务状态。'
  })

  const hasClinicalWorkflow = computed(
    () => analysisResult.value?.resultType === 'VESSEL_SEGMENTATION',
  )
  const defaultTab = computed<DetailTab>(() =>
    taskDetail.value?.status === 'SUCCESS' && hasClinicalWorkflow.value ? 'result' : 'overview',
  )
  const resultJsonText = computed(() =>
    analysisResult.value ? JSON.stringify(analysisResult.value.resultJson || {}, null, 2) : '',
  )

  const queryValue = (value: unknown) => (Array.isArray(value) ? value[0] : value)

  const navigateWithQuery = (tab: DetailTab, stage?: WorkflowStage, replace = false) => {
    const query = { ...route.query, tab } as Record<string, string | string[] | undefined>
    if (tab === 'clinical' && stage) query.stage = stage
    else delete query.stage
    if (replace) void router.replace({ query })
    else void router.push({ query })
  }

  const syncNavigationFromRoute = () => {
    if (!taskDetail.value) return
    const requestedTab = queryValue(route.query.tab)
    const validTab =
      typeof requestedTab === 'string' && detailTabs.includes(requestedTab as DetailTab)
        ? (requestedTab as DetailTab)
        : null
    const nextTab =
      validTab === 'clinical' && !hasClinicalWorkflow.value
        ? defaultTab.value
        : validTab || defaultTab.value
    const requestedStage = queryValue(route.query.stage)

    clinicalStage.value =
      typeof requestedStage === 'string' && clinicalStages.includes(requestedStage as WorkflowStage)
        ? (requestedStage as WorkflowStage)
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

  const handleStageChange = (stage: WorkflowStage) => {
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
    try {
      const blob = await getImagePreviewBlob(imageId)
      replaceObjectUrl(originalImageUrl, blob)
    } catch (error) {
      // 原图只影响图片预览，失败时不应让整个详情页进入错误态
      ignoreHandledError(error)
    }
  }

  const loadMaskImage = async () => {
    replaceObjectUrl(maskImageUrl)
    const resultId = analysisResult.value?.id
    if (!resultId) return
    try {
      const blob = await getResultMaskBlob(resultId)
      replaceObjectUrl(maskImageUrl, blob)
    } catch (error) {
      // 遮罩图缺失不应把已成功加载的分析结果误报为「分析结果加载失败」
      ignoreHandledError(error)
    }
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
      const timeline = await getCaseAnalysisTimeline(detail.caseId, {
        taskType: currentResult.resultType,
      })
      comparisonCandidates.value = timeline.items.filter(
        (item) =>
          typeof item.resultId === 'number' &&
          item.resultId !== currentResult.id &&
          item.taskStatus === 'SUCCESS',
      )
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

  const continueSegmentation = () =>
    router.push({
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

  const goBack = () => {
    void router.push('/tasks')
  }

  onMounted(() => void loadDetail())
  watch(
    () => [route.query.tab, route.query.stage],
    () => syncNavigationFromRoute(),
  )
  onBeforeUnmount(() => {
    replaceObjectUrl(originalImageUrl)
    replaceObjectUrl(maskImageUrl)
  })

  return {
    loading,
    refreshing,
    resultLoading,
    logsLoading,
    detailError,
    resultError,
    logsError,
    taskDetail,
    analysisResult,
    comparisonCandidates,
    selectedBaselineResultId,
    comparisonLoading,
    comparisonError,
    comparison,
    taskLogs,
    previewVisible,
    previewUrl,
    previewTitle,
    previewFilename,
    originalImageUrl,
    maskImageUrl,
    activeTab,
    clinicalStage,
    isUnfinished,
    statusDescription,
    hasClinicalWorkflow,
    resultJsonText,
    handleTabChange,
    handleStageChange,
    openPreview,
    loadLogs,
    handleCompare,
    continueSegmentation,
    handleRefresh,
    goBack,
  }
}
