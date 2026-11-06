import { ElMessage, ElMessageBox } from '@/plugins/element-plus-api'
import { computed, onMounted, onUnmounted, ref } from 'vue'

import {
  activatePromptTemplateVersion,
  getPromptTemplateVersions,
  getRagEvaluationRun,
  getRagEvaluationRuns,
  reviewRagEvaluation,
  startRagEvaluation,
} from '@/api/prompt'
import { useAuthStore } from '@/stores/auth'
import type { PromptTemplateVersion, RagEvaluationRun } from '@/types/prompt'

/** RAG 离线评测页的逻辑层：评测运行、候选版本、人工评审与发布。 */
export const useRagEvaluation = () => {
  const auth = useAuthStore()
  const isAdmin = computed(() => auth.user?.roleCode === 'ADMIN')
  const runs = ref<RagEvaluationRun[]>([])
  const versions = ref<PromptTemplateVersion[]>([])
  const selected = ref<RagEvaluationRun | null>(null)
  const candidateId = ref<number | null>(null)
  const decision = ref<'APPROVED' | 'REJECTED'>('REJECTED')
  const score = ref(3)
  const note = ref('')
  const loading = ref(false)
  const actionLoading = ref(false)
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

  const loadRuns = async () => {
    loading.value = true
    try {
      runs.value = await getRagEvaluationRuns()
      const current = runs.value.find((run) => run.id === selected.value?.id)
      if (current && current.status !== selected.value?.status) await loadDetail(current.id)
    } catch {
      runs.value = []
    } finally {
      loading.value = false
    }
  }

  const loadDetail = async (id: number) => {
    loading.value = true
    try {
      selected.value = await getRagEvaluationRun(id)
      decision.value = selected.value.reviewDecision || 'REJECTED'
      score.value = selected.value.reviewScore || 3
      note.value = selected.value.reviewNote || ''
    } catch {
      selected.value = null
    } finally {
      loading.value = false
    }
  }

  const loadVersions = async () => {
    if (!isAdmin.value) return
    try {
      versions.value = await getPromptTemplateVersions('RAG_KNOWLEDGE_CHAT')
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
        '将对固定合成样本调用 Embedding、Qdrant 和两版 LLM，可能产生模型费用。',
        '启动 RAG 评测',
        { type: 'warning', confirmButtonText: '启动', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    actionLoading.value = true
    try {
      const run = await startRagEvaluation(candidateId.value)
      await loadRuns()
      await loadDetail(run.id)
      ElMessage.success('RAG 评测已提交')
    } catch {
      // 错误提示已由请求拦截器统一处理
    } finally {
      actionLoading.value = false
    }
  }

  const review = async () => {
    if (!selected.value) return
    try {
      await ElMessageBox.confirm(
        '请确认已逐题检查回答的事实性，以及引用原文是否真正支持回答。',
        '保存人工评审',
        { type: 'warning', confirmButtonText: '确认保存', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    actionLoading.value = true
    try {
      selected.value = await reviewRagEvaluation(
        selected.value.id,
        decision.value === 'APPROVED',
        score.value,
        note.value,
      )
      await loadRuns()
      ElMessage.success('人工评审已保存')
    } catch {
      // 错误提示已由请求拦截器统一处理
    } finally {
      actionLoading.value = false
    }
  }

  const release = async () => {
    if (!selected.value || !canRelease.value) return
    try {
      await ElMessageBox.confirm(
        '启用后真实知识问答将使用该版本；只有逐字核验的引用会展示。',
        '启用 RAG Prompt',
        { type: 'warning', confirmButtonText: '确认启用', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    actionLoading.value = true
    try {
      await activatePromptTemplateVersion('RAG_KNOWLEDGE_CHAT', selected.value.candidateVersionId)
      await loadVersions()
      ElMessage.success('RAG Prompt 已启用')
    } catch {
      // 后端会校验审批是否为当前版本
    } finally {
      actionLoading.value = false
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
    loading,
    actionLoading,
    candidateVersions,
    canRelease,
    loadRuns,
    loadDetail,
    start,
    review,
    release,
  }
}
