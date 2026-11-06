import { ElMessage, ElMessageBox } from '@/plugins/element-plus-api'
import { computed, onMounted, reactive, ref, watch, type Ref } from 'vue'

import {
  addCorrection,
  addFeedback,
  downloadReportVersion,
  generateReportDraft,
  getReportDraft,
  getReview,
  listCorrections,
  listFeedback,
  listReports,
  signReport,
  submitReview,
} from '@/api/clinical'
import { useAuthStore } from '@/stores/auth'
import type {
  Correction,
  Feedback,
  FeedbackVerdict,
  Report,
  Review,
  ReviewStatus,
} from '@/types/clinical'
import { ignoreHandledError } from '@/utils/async-action'
import {
  createEmptyDraftForm,
  parseDraftJson,
  WORKFLOW_STAGES,
  type DraftForm,
  type WorkflowStage,
} from '@/utils/clinical-workflow'

export interface UseClinicalWorkflowOptions {
  /** 用 getter 传入，保证 props 变更时响应式追踪不丢失 */
  resultId: () => number
  stage: () => WorkflowStage | undefined
  /** 模板 ref 必须由 SFC 声明，编译器才能静态识别并回填 DOM 元素 */
  fileInput: Ref<HTMLInputElement | null>
  onStageChange: (stage: WorkflowStage) => void
  onWorkflowUpdated: () => void
}

/**
 * 临床闭环面板的逻辑层：角色权限、数据加载、表单状态、提交/签发/下载与步骤编排。
 * 组件本体只保留模板渲染与纯展示映射，便于单测和复用。
 */
export const useClinicalWorkflow = (options: UseClinicalWorkflowOptions) => {
  const { resultId, stage, fileInput, onStageChange, onWorkflowUpdated } = options

  const auth = useAuthStore()
  const roleCode = computed(() => auth.user?.roleCode ?? '')
  const doctor = computed(() => roleCode.value === 'DOCTOR')
  const canCorrect = computed(() => ['DOCTOR', 'RESEARCHER'].includes(roleCode.value))

  const feedback = ref<Feedback[]>([])
  const corrections = ref<Correction[]>([])
  const review = ref<Review | null>(null)
  const reports = ref<Report[]>([])
  const loading = ref(false)
  const maskFile = ref<File | null>(null)
  const draftParseError = ref('')
  const draftRaw = ref<Record<string, unknown>>({})

  const submittingFeedback = ref(false)
  const submittingCorrection = ref(false)
  const savingReview = ref(false)
  const generatingDraft = ref(false)
  const signingReport = ref(false)
  const showAllReports = ref(false)

  const feedbackForm = reactive<{ verdict: FeedbackVerdict; comment: string }>({
    verdict: 'ACCEPTED',
    comment: '',
  })

  const correctionForm = reactive({
    reason: '',
    correctedResultJson: '{}',
  })

  const reviewForm = reactive<{
    status: ReviewStatus
    correctionVersion: number | null
    findings: string
    conclusion: string
    recommendation: string
  }>({
    status: 'PENDING',
    correctionVersion: null,
    findings: '',
    conclusion: '',
    recommendation: '',
  })

  const draftForm = reactive<DraftForm>(createEmptyDraftForm())

  const signedReports = computed(() => reports.value.filter((item) => item.status !== 'DRAFT'))
  // 不依赖后端返回顺序，取真实最大版本号
  const latestCorrectionVersion = computed(() =>
    corrections.value.reduce((max, item) => Math.max(max, item.version), 0),
  )

  const llmDraftMeta = computed(() => {
    const provider =
      typeof draftRaw.value.llmProvider === 'string' ? draftRaw.value.llmProvider : ''
    const model = typeof draftRaw.value.llmModel === 'string' ? draftRaw.value.llmModel : ''
    const generatedAt =
      typeof draftRaw.value.llmGeneratedAt === 'string' ? draftRaw.value.llmGeneratedAt : ''

    return {
      provider,
      model,
      generatedAt,
      visible: Boolean(provider || model || generatedAt),
    }
  })

  const hasAiDraftReference = computed(() =>
    Boolean(
      draftForm.findings.trim() ||
      draftForm.conclusion.trim() ||
      draftForm.recommendation.trim() ||
      draftForm.explanation.trim() ||
      llmDraftMeta.value.visible,
    ),
  )

  const savedReviewReadyForSign = computed(
    () =>
      review.value?.status === 'APPROVED' &&
      Boolean(
        review.value.findings?.trim() &&
        review.value.conclusion?.trim() &&
        review.value.recommendation?.trim(),
      ),
  )

  const activeStep = computed(() => {
    if (signedReports.value.length > 0 || review.value) return 3
    if (hasAiDraftReference.value) return 2
    if (corrections.value.length > 0) return 1
    return 0
  })

  const recommendedStage = computed<WorkflowStage>(
    () => WORKFLOW_STAGES[activeStep.value]?.key ?? 'feedback',
  )
  const currentStage = computed<WorkflowStage>(() => {
    const requested = stage()
    return requested && WORKFLOW_STAGES.some((item) => item.key === requested)
      ? requested
      : recommendedStage.value
  })
  const orderedReports = computed(() => [...reports.value].sort((a, b) => b.version - a.version))
  const visibleReports = computed(() =>
    showAllReports.value ? orderedReports.value : orderedReports.value.slice(0, 1),
  )

  const selectStage = (nextStage: WorkflowStage) => onStageChange(nextStage)
  const toggleAllReports = () => {
    showAllReports.value = !showAllReports.value
  }

  const parseDraft = (draftJson: string | null | undefined) => {
    // 空草稿时保留当前表单内容，仅清空原始 JSON 与错误提示（沿用既有行为）
    if (!draftJson || !draftJson.trim()) {
      draftParseError.value = ''
      draftRaw.value = {}
      return
    }

    const parsed = parseDraftJson(draftJson)
    draftParseError.value = parsed.error
    draftRaw.value = parsed.raw
    Object.assign(draftForm, parsed.form)
  }

  const load = async () => {
    loading.value = true
    try {
      // 相互独立的读取并行发起，避免串行 await 造成请求瀑布
      const [feedbackList, reportList, correctionList, reviewData, reportDraft] = await Promise.all(
        [
          listFeedback(resultId()),
          listReports(resultId()),
          canCorrect.value ? listCorrections(resultId()) : Promise.resolve<Correction[]>([]),
          doctor.value ? getReview(resultId()) : Promise.resolve<Review | null>(null),
          doctor.value ? getReportDraft(resultId()) : Promise.resolve<Report | null>(null),
        ],
      )
      feedback.value = feedbackList
      reports.value = reportList
      corrections.value = correctionList
      review.value = reviewData

      if (reviewData) {
        reviewForm.status = reviewData.status
        reviewForm.correctionVersion = reviewData.correctionVersion
        reviewForm.findings = reviewData.findings ?? ''
        reviewForm.conclusion = reviewData.conclusion ?? ''
        reviewForm.recommendation = reviewData.recommendation ?? ''
      }
      if (reportDraft) parseDraft(reportDraft.draftJson)
    } catch (error) {
      ignoreHandledError(error)
    } finally {
      if (!stage()) onStageChange(recommendedStage.value)
      loading.value = false
    }
  }

  const sendFeedback = async () => {
    submittingFeedback.value = true
    try {
      await addFeedback(resultId(), {
        ...feedbackForm,
        issueCodes: [],
      })
      feedbackForm.comment = ''
      feedback.value = await listFeedback(resultId())
      ElMessage.success('反馈已提交')
    } catch (error) {
      ignoreHandledError(error)
    } finally {
      submittingFeedback.value = false
    }
  }

  const pickFile = (event: Event) => {
    maskFile.value = (event.target as HTMLInputElement).files?.[0] ?? null
  }

  const resetCorrectionForm = () => {
    maskFile.value = null
    correctionForm.reason = ''
    correctionForm.correctedResultJson = '{}'
    if (fileInput.value) {
      fileInput.value.value = ''
    }
  }

  const sendCorrection = async () => {
    if (!maskFile.value || !correctionForm.reason.trim()) {
      ElMessage.warning('请选择 PNG、JPEG 或 TIFF 图片并填写修正原因')
      return
    }

    submittingCorrection.value = true
    try {
      const data = new FormData()
      data.append('file', maskFile.value)
      data.append('reason', correctionForm.reason)
      data.append('correctedResultJson', correctionForm.correctedResultJson)
      data.append('expectedVersion', String(latestCorrectionVersion.value))

      await addCorrection(resultId(), data)
      corrections.value = await listCorrections(resultId())
      resetCorrectionForm()
      ElMessage.success('修正版本已提交')
    } catch (error) {
      ignoreHandledError(error)
    } finally {
      submittingCorrection.value = false
    }
  }

  const sendReview = async () => {
    savingReview.value = true
    try {
      review.value = await submitReview(resultId(), {
        ...reviewForm,
        expectedVersion: review.value?.version ?? 0,
      })
      onWorkflowUpdated()
      ElMessage.success('审核已保存')
    } catch (error) {
      ignoreHandledError(error)
    } finally {
      savingReview.value = false
    }
  }

  const hasReviewFormContent = () =>
    Boolean(
      reviewForm.findings.trim() ||
      reviewForm.conclusion.trim() ||
      reviewForm.recommendation.trim(),
    )

  const copyDraftToReview = async (askBeforeOverwrite: boolean) => {
    const findings = draftForm.findings.trim()
    const conclusion = draftForm.conclusion.trim()
    const recommendation = draftForm.recommendation.trim()

    if (!findings && !conclusion && !recommendation) {
      ElMessage.warning('暂无可带入医生审核的 AI 草稿内容')
      return false
    }

    if (askBeforeOverwrite && hasReviewFormContent()) {
      try {
        await ElMessageBox.confirm(
          'AI 草稿会覆盖当前医生审核表单中的所见、结论和建议，是否继续？',
          '带入医生审核',
          { type: 'warning' },
        )
      } catch {
        return false
      }
    }

    reviewForm.findings = findings
    reviewForm.conclusion = conclusion
    reviewForm.recommendation = recommendation
    return true
  }

  const generateDraft = async () => {
    const hasManualDraft = Boolean(
      draftForm.findings.trim() || draftForm.conclusion.trim() || draftForm.recommendation.trim(),
    )
    if (hasManualDraft) {
      try {
        await ElMessageBox.confirm(
          'AI 生成会覆盖当前草稿中的所见、结论和建议，是否继续？',
          'AI 生成草稿',
          {
            type: 'warning',
          },
        )
      } catch {
        return // 用户取消覆盖
      }
    }

    generatingDraft.value = true
    try {
      const reportDraft = await generateReportDraft(resultId())
      parseDraft(reportDraft.draftJson)
      const copied = await copyDraftToReview(true)
      onWorkflowUpdated()
      ElMessage.success(copied ? 'AI 草稿已生成并带入医生审核表单，请复核后保存' : 'AI 草稿已生成')
    } catch (error) {
      ignoreHandledError(error)
    } finally {
      generatingDraft.value = false
    }
  }

  const sign = async () => {
    if (!savedReviewReadyForSign.value) {
      ElMessage.warning('请先保存完整且已通过的医生审核意见后再签发 PDF')
      return
    }

    try {
      await ElMessageBox.confirm('签发后本版本不可修改，确认签发 PDF？', '签发报告', {
        type: 'warning',
      })
    } catch {
      return // 用户取消签发
    }

    signingReport.value = true
    try {
      await signReport(resultId())
      reports.value = await listReports(resultId())
      onWorkflowUpdated()
      ElMessage.success('报告已签发')
    } catch (error) {
      ignoreHandledError(error)
    } finally {
      signingReport.value = false
    }
  }

  const download = async (report: Report) => {
    let blob: Blob
    try {
      blob = await downloadReportVersion(resultId(), report.version)
    } catch (error) {
      ignoreHandledError(error)
      return
    }
    const url = URL.createObjectURL(blob)
    const link = globalThis.document.createElement('a')
    link.href = url
    link.download = `report-${resultId()}-v${report.version}.pdf`
    link.click()
    // 部分浏览器在 click 后异步读取 Blob URL，同步 revoke 会中断下载
    setTimeout(() => URL.revokeObjectURL(url), 0)
  }

  onMounted(load)
  // 父组件复用本组件切换结果时重新加载，避免展示上一个 resultId 的陈旧数据
  watch(resultId, () => {
    void load()
  })

  return {
    doctor,
    canCorrect,
    feedback,
    corrections,
    review,
    reports,
    loading,
    draftParseError,
    submittingFeedback,
    submittingCorrection,
    savingReview,
    generatingDraft,
    signingReport,
    showAllReports,
    feedbackForm,
    correctionForm,
    reviewForm,
    draftForm,
    latestCorrectionVersion,
    llmDraftMeta,
    activeStep,
    currentStage,
    visibleReports,
    selectStage,
    toggleAllReports,
    pickFile,
    sendFeedback,
    sendCorrection,
    sendReview,
    copyDraftToReview,
    generateDraft,
    sign,
    download,
  }
}
