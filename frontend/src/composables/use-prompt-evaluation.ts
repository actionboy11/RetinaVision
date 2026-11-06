import { ElMessage, ElMessageBox } from '@/plugins/element-plus-api'
import { computed, onMounted, onUnmounted, ref } from 'vue'

import {
  activatePromptTemplateVersion,
  getPromptEvaluationRun,
  getPromptEvaluationRuns,
  getPromptTemplateVersions,
  reviewPromptEvaluation,
  startPromptEvaluation,
} from '@/api/prompt'
import { useAuthStore } from '@/stores/auth'
import type {
  PromptEvaluationOutcome,
  PromptEvaluationRun,
  PromptTemplateVersion,
} from '@/types/prompt'

const statusTextMap: Record<PromptEvaluationRun['status'], string> = {
  QUEUED: '排队中',
  RUNNING: '评测中',
  COMPLETED: '已完成',
  FAILED: '失败',
}

const errorTextMap: Record<string, string> = {
  INVALID_JSON: 'JSON 格式错误',
  INVALID_FIELD: '字段缺失或超长',
  INVALID_DISCLAIMER: '固定声明不一致',
  UNSAFE_WORDING: '含不合规诊断措辞',
  LLM_ERROR: '模型调用失败',
}

/** Prompt 离线评测页的逻辑层：评测运行、候选版本、人工评审与发布。 */
export const usePromptEvaluation = () => {
  const authStore = useAuthStore()
  const isAdmin = computed(() => authStore.user?.roleCode === 'ADMIN')
  const runs = ref<PromptEvaluationRun[]>([])
  const versions = ref<PromptTemplateVersion[]>([])
  const selected = ref<PromptEvaluationRun | null>(null)
  const candidateId = ref<number | null>(null)
  const decision = ref<'APPROVED' | 'REJECTED'>('APPROVED')
  const score = ref(3)
  const note = ref('')
  const listLoading = ref(false)
  const detailLoading = ref(false)
  const starting = ref(false)
  const reviewing = ref(false)
  const releasing = ref(false)
  let polling: ReturnType<typeof setInterval> | null = null

  const candidateVersions = computed(() => versions.value.filter((version) => !version.active))
  const candidateAlreadyActive = computed(() =>
    versions.value.some(
      (version) => version.id === selected.value?.candidateVersionId && version.active,
    ),
  )
  const canRelease = computed(
    () =>
      isAdmin.value &&
      selected.value?.status === 'COMPLETED' &&
      selected.value.automatedPass === true &&
      selected.value.reviewDecision === 'APPROVED' &&
      !candidateAlreadyActive.value,
  )

  const statusText = (status: PromptEvaluationRun['status']) => statusTextMap[status]
  const errorText = (code: string | null) => errorTextMap[code || ''] || code || '通过'

  const readableDraft = (outcome: PromptEvaluationOutcome) => {
    if (!outcome.passed || !outcome.output) return null
    try {
      const data = JSON.parse(outcome.output) as Record<string, string>
      return [
        ['医生所见', data.findings],
        ['辅助结论', data.conclusion],
        ['处理建议', data.recommendation],
        ['结果解释', data.explanation],
      ]
    } catch {
      return null
    }
  }

  const loadRuns = async () => {
    if (listLoading.value) return
    listLoading.value = true
    try {
      runs.value = await getPromptEvaluationRuns()
      if (selected.value && runs.value.some((item) => item.id === selected.value?.id)) {
        const latest = runs.value.find((item) => item.id === selected.value?.id)
        if (latest && latest.status !== selected.value.status) await loadDetail(latest.id)
      }
    } catch {
      // 错误提示已由请求拦截器统一处理
    } finally {
      listLoading.value = false
    }
  }

  const loadDetail = async (id: number) => {
    detailLoading.value = true
    try {
      selected.value = await getPromptEvaluationRun(id)
      decision.value =
        selected.value.reviewDecision ||
        (selected.value.automatedPass === true ? 'APPROVED' : 'REJECTED')
      score.value = selected.value.reviewScore || 3
      note.value = selected.value.reviewNote || ''
    } catch {
      selected.value = null
    } finally {
      detailLoading.value = false
    }
  }

  const loadVersions = async () => {
    if (!isAdmin.value) return
    try {
      versions.value = await getPromptTemplateVersions('REPORT_DRAFT_GENERATION')
      if (!candidateVersions.value.some((version) => version.id === candidateId.value)) {
        candidateId.value = candidateVersions.value[0]?.id || null
      }
    } catch {
      versions.value = []
    }
  }

  const start = async () => {
    if (!candidateId.value) return
    try {
      await ElMessageBox.confirm(
        '本次将对固定合成样本分别调用当前版和候选版，可能产生模型费用。继续吗？',
        '启动离线评测',
        { confirmButtonText: '启动', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }
    starting.value = true
    try {
      const run = await startPromptEvaluation(candidateId.value)
      await loadRuns()
      await loadDetail(run.id)
      ElMessage.success('评测已提交')
    } catch {
      // 错误提示已由请求拦截器统一处理
    } finally {
      starting.value = false
    }
  }

  const review = async () => {
    if (!selected.value) return
    try {
      await ElMessageBox.confirm(
        '确认保存本次人工评审意见？批准后可启用该 Prompt 版本。',
        '保存评测意见',
        { confirmButtonText: '确认保存', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }
    reviewing.value = true
    try {
      selected.value = await reviewPromptEvaluation(
        selected.value.id,
        decision.value === 'APPROVED',
        score.value,
        note.value,
      )
      await loadRuns()
      ElMessage.success('人工评审意见已保存')
    } catch {
      // 错误提示已由请求拦截器统一处理
    } finally {
      reviewing.value = false
    }
  }

  const release = async () => {
    if (!selected.value || !canRelease.value) return
    try {
      await ElMessageBox.confirm(
        '确认启用该版本？后续真实报告草稿调用将立即使用它；已发布旧版可回滚。',
        '发布 Prompt 版本',
        { confirmButtonText: '确认启用', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }
    releasing.value = true
    try {
      await activatePromptTemplateVersion(
        'REPORT_DRAFT_GENERATION',
        selected.value.candidateVersionId,
      )
      await loadVersions()
      ElMessage.success('候选 Prompt 已启用')
    } catch {
      // 后端会拒绝过期评测结果或未经审批的版本
    } finally {
      releasing.value = false
    }
  }

  onMounted(async () => {
    await Promise.all([loadRuns(), loadVersions()])
    polling = setInterval(() => {
      if (runs.value.some((run) => run.status === 'QUEUED' || run.status === 'RUNNING'))
        void loadRuns()
    }, 3000)
  })

  onUnmounted(() => {
    if (polling) clearInterval(polling)
  })

  return {
    isAdmin,
    runs,
    selected,
    candidateId,
    decision,
    score,
    note,
    listLoading,
    detailLoading,
    starting,
    reviewing,
    releasing,
    candidateVersions,
    canRelease,
    statusText,
    errorText,
    readableDraft,
    loadRuns,
    loadDetail,
    start,
    review,
    release,
  }
}
