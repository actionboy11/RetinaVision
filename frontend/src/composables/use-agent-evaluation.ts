import { ElMessage, ElMessageBox } from '@/plugins/element-plus-api'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  cancelAgentEvaluation,
  getAgentEvaluationDatasets,
  getAgentEvaluationOptions,
  getAgentEvaluationResults,
  getAgentEvaluationRun,
  getAgentEvaluationRuns,
  reviewAgentEvaluation,
  startAgentEvaluation,
} from '@/api/agent-evaluation'
import type {
  AgentEvaluationDataset,
  AgentEvaluationOptions,
  AgentEvaluationResult,
  AgentEvaluationRole,
  AgentEvaluationRun,
} from '@/types/agent-evaluation'

const roleSkillCodes: Record<AgentEvaluationRole, string[]> = {
  DOCTOR: [
    'DOCTOR_WORKLOAD_OVERVIEW',
    'ASSIGNED_CASE_SEARCH',
    'CASE_CLINICAL_SUMMARY',
    'CASE_FOLLOWUP_ANALYSIS',
    'DOCTOR_TASK_SEARCH',
    'DOCTOR_CLINICAL_QUEUE',
    'MEDICAL_KNOWLEDGE_QA',
  ],
  PATIENT: ['MY_CASE_LIST', 'MY_CASE_PROGRESS', 'MY_SIGNED_REPORT', 'PATIENT_KNOWLEDGE_QA'],
}

const activeVersionMap = (
  source: Record<string, Array<{ id: number; active: boolean }>>,
  codes: string[],
) => {
  const target: Record<string, number> = {}
  for (const code of codes) {
    const version = source[code]?.find((item) => item.active) || source[code]?.[0]
    if (version) target[code] = Number(version.id)
  }
  return target
}

/**
 * Agent 评测页的逻辑层：运行列表/结果筛选、自调度轮询、启动/取消/人工评审。
 * 组件只保留模板与格式化映射。
 */
export const useAgentEvaluation = () => {
  const route = useRoute()
  const router = useRouter()

  const loading = ref(false)
  const actionLoading = ref(false)
  const options = ref<AgentEvaluationOptions | null>(null)
  const datasets = ref<AgentEvaluationDataset[]>([])
  const runs = ref<AgentEvaluationRun[]>([])
  const selectedRun = ref<AgentEvaluationRun | null>(null)
  const results = ref<AgentEvaluationResult[]>([])
  const resultTotal = ref(0)
  const reviewNote = ref('')
  const resultFilters = reactive({ success: 'false', errorType: '', page: 1, pageSize: 20 })
  const form = reactive({ datasetId: 0, targetRole: 'DOCTOR' as AgentEvaluationRole, modelKey: '' })
  let pollingTimer: ReturnType<typeof setTimeout> | null = null

  const availableDatasets = computed(() =>
    datasets.value.filter((item) => item.targetRole === form.targetRole),
  )
  const selectedDataset = computed(
    () => datasets.value.find((item) => Number(item.id) === form.datasetId) || null,
  )
  const running = computed(() => ['QUEUED', 'RUNNING'].includes(selectedRun.value?.status || ''))
  const progressPercent = computed(() => {
    const value = selectedRun.value?.progress
    return value?.total ? Math.round((value.completed * 100) / value.total) : 0
  })

  const percent = (value: number | null | undefined) =>
    value == null ? '-' : `${(value * 100).toFixed(1)}%`
  const statusType = (status: string) =>
    (({
      PASSED: 'success',
      FAILED: 'danger',
      INVALID: 'warning',
      CANCELLED: 'info',
      RUNNING: 'primary',
      QUEUED: 'primary',
    })[status] || 'info') as 'success' | 'danger' | 'warning' | 'info' | 'primary'

  const loadRuns = async () => {
    const page = await getAgentEvaluationRuns({ page: 1, pageSize: 30 })
    runs.value = page.records
  }

  const stopPolling = () => {
    if (pollingTimer) clearTimeout(pollingTimer)
    pollingTimer = null
  }

  /**
   * 自调度轮询：只在上一轮请求结束后才排下一轮，避免慢请求导致并发重入。
   * runId 用于丢弃已切换运行后的过期响应。
   */
  const pollRun = async (runId: number) => {
    const stillActive = () => selectedRun.value && Number(selectedRun.value.id) === runId
    if (!stillActive()) return

    const scheduleNext = () => {
      if (stillActive())
        pollingTimer = setTimeout(() => {
          void pollRun(runId)
        }, 2000)
    }

    try {
      const current = await getAgentEvaluationRun(runId)
      if (!stillActive()) return
      selectedRun.value = current

      if (['QUEUED', 'RUNNING'].includes(current.status)) {
        scheduleNext()
        return
      }
      stopPolling()
      await Promise.all([loadRuns(), loadResults()])
    } catch {
      // 单次轮询失败不中断轮询，下一轮继续；错误提示已由请求拦截器统一处理
      scheduleNext()
    }
  }

  const loadResults = async () => {
    if (!selectedRun.value) return
    const page = await getAgentEvaluationResults(Number(selectedRun.value.id), {
      success: resultFilters.success === '' ? undefined : resultFilters.success === 'true',
      errorType: resultFilters.errorType || undefined,
      page: resultFilters.page,
      pageSize: resultFilters.pageSize,
    })
    results.value = page.records
    resultTotal.value = page.total
  }

  const selectRun = async (runId: number, updateUrl = true) => {
    selectedRun.value = await getAgentEvaluationRun(runId)
    reviewNote.value = selectedRun.value.reviewNote || ''
    await loadResults()
    if (updateUrl)
      await router.replace({ path: '/agent-evaluations', query: { runId: String(runId) } })
    stopPolling()
    if (running.value && runId) {
      pollingTimer = setTimeout(() => {
        void pollRun(runId)
      }, 2000)
    }
  }

  const refresh = async () => {
    loading.value = true
    try {
      await loadRuns()
      if (selectedRun.value) await selectRun(Number(selectedRun.value.id), false)
    } finally {
      loading.value = false
    }
  }

  const start = async () => {
    if (!options.value || !selectedDataset.value || !form.modelKey) {
      ElMessage.warning('请选择角色、评测集和模型配置')
      return
    }
    const skillVersions = activeVersionMap(
      options.value.skillVersions,
      roleSkillCodes[form.targetRole],
    )
    const promptVersions = activeVersionMap(options.value.promptVersions, ['AGENT_SKILL_ROUTER'])
    if (
      Object.keys(skillVersions).length !== roleSkillCodes[form.targetRole].length ||
      !promptVersions.AGENT_SKILL_ROUTER
    ) {
      ElMessage.warning('当前角色缺少可用的 Skill 或路由 Prompt 版本')
      return
    }
    try {
      await ElMessageBox.confirm(
        `本次将串行调用真实模型约 ${selectedDataset.value.sampleCount} 次，使用固定匿名数据，不会写入正式会话。确认启动？`,
        '启动 Agent 评测',
        { type: 'warning', confirmButtonText: '启动评测', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    actionLoading.value = true
    try {
      const run = await startAgentEvaluation({
        datasetId: Number(selectedDataset.value.id),
        targetRole: form.targetRole,
        modelKey: form.modelKey,
        skillVersions,
        promptVersions,
      })
      await loadRuns()
      await selectRun(Number(run.id))
      ElMessage.success('评测已进入队列')
    } finally {
      actionLoading.value = false
    }
  }

  const cancel = async () => {
    if (!selectedRun.value) return
    await cancelAgentEvaluation(Number(selectedRun.value.id))
    ElMessage.success('已请求取消，当前模型请求结束后停止')
    await selectRun(Number(selectedRun.value.id), false)
  }

  const review = async (decision: 'APPROVED' | 'REJECTED') => {
    if (!selectedRun.value) return
    await ElMessageBox.confirm(
      decision === 'APPROVED'
        ? '确认人工抽查通过？该运行可用于候选版本启用判断。'
        : '确认拒绝本次评测？',
      '提交人工评审',
      { type: decision === 'APPROVED' ? 'warning' : 'error' },
    )
    selectedRun.value = await reviewAgentEvaluation(
      Number(selectedRun.value.id),
      decision,
      reviewNote.value,
    )
    await loadRuns()
    ElMessage.success('评审结论已保存')
  }

  watch(
    () => form.targetRole,
    () => {
      form.datasetId = Number(availableDatasets.value[0]?.id || 0)
    },
  )
  // 切换筛选条件时重置页码，避免停留在越界页码导致列表空白。
  // 页码从非 1 变为 1 时会由下方 page watch 统一触发一次加载，避免重复请求。
  watch(
    () => [resultFilters.success, resultFilters.errorType],
    () => {
      if (resultFilters.page !== 1) resultFilters.page = 1
      else void loadResults()
    },
  )
  watch(
    () => resultFilters.page,
    () => {
      void loadResults()
    },
  )

  onMounted(async () => {
    loading.value = true
    try {
      const [optionData, datasetData] = await Promise.all([
        getAgentEvaluationOptions(),
        getAgentEvaluationDatasets(),
        loadRuns(),
      ])
      options.value = optionData
      datasets.value = datasetData
      form.modelKey = optionData.models[0]?.key || ''
      form.datasetId = Number(
        datasetData.find((item) => item.targetRole === form.targetRole)?.id || 0,
      )
      const runId = Number(route.query.runId)
      if (runId) await selectRun(runId, false)
      else if (runs.value[0]) await selectRun(Number(runs.value[0].id), false)
    } finally {
      loading.value = false
    }
  })

  onBeforeUnmount(stopPolling)

  return {
    loading,
    actionLoading,
    options,
    datasets,
    runs,
    selectedRun,
    results,
    resultTotal,
    reviewNote,
    resultFilters,
    form,
    availableDatasets,
    selectedDataset,
    running,
    progressPercent,
    percent,
    statusType,
    selectRun,
    refresh,
    start,
    cancel,
    review,
  }
}
