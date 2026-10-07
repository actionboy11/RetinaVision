<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

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
import { formatDateTime, formatEmpty } from '@/utils/format'

type DraftForm = {
  findings: string
  conclusion: string
  recommendation: string
  explanation: string
  disclaimer: string
}

type WorkflowStage = 'feedback' | 'correction' | 'draft' | 'review'

const props = defineProps<{ resultId: number; stage?: WorkflowStage }>()
const emit = defineEmits<{
  workflowUpdated: []
  stageChange: [stage: WorkflowStage]
}>()

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
const fileInput = ref<HTMLInputElement | null>(null)
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

const draftForm = reactive<DraftForm>({
  findings: '',
  conclusion: '',
  recommendation: '',
  explanation: '',
  disclaimer: 'AI 辅助分析，不等同于独立医学诊断。',
})

const feedbackVerdictTextMap: Record<FeedbackVerdict, string> = {
  ACCEPTED: '接受',
  PARTIAL: '部分正确',
  INCORRECT: '不正确',
}

const correctionStatusTextMap: Record<Correction['status'], string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  ACCEPTED: '已采纳',
  REJECTED: '已拒绝',
}

const reportStatusTextMap: Record<Report['status'], string> = {
  DRAFT: '草稿',
  SIGNED: '已签发',
  SUPERSEDED: '历史版本',
}

const signedReports = computed(() => reports.value.filter((item) => item.status !== 'DRAFT'))
const latestCorrectionVersion = computed(
  () => corrections.value[corrections.value.length - 1]?.version ?? 0,
)

const llmDraftMeta = computed(() => {
  const provider = typeof draftRaw.value.llmProvider === 'string' ? draftRaw.value.llmProvider : ''
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
  if (signedReports.value.length > 0) return 3
  if (review.value) return 3
  if (hasAiDraftReference.value) return 2
  if (corrections.value.length > 0) return 1
  return 0
})

const workflowStages: Array<{ key: WorkflowStage; title: string }> = [
  { key: 'feedback', title: '人工反馈' },
  { key: 'correction', title: '修正版本' },
  { key: 'draft', title: 'AI 草稿参考' },
  { key: 'review', title: '医生审核与签发' },
]

const recommendedStage = computed<WorkflowStage>(() => workflowStages[activeStep.value]?.key ?? 'feedback')
const currentStage = computed<WorkflowStage>(() => (
  props.stage && workflowStages.some((item) => item.key === props.stage)
    ? props.stage
    : recommendedStage.value
))
const orderedReports = computed(() => [...reports.value].sort((a, b) => b.version - a.version))
const visibleReports = computed(() => showAllReports.value ? orderedReports.value : orderedReports.value.slice(0, 1))

const selectStage = (stage: WorkflowStage) => emit('stageChange', stage)

const stepStatus = (index: number) => {
  if (activeStep.value > index) return 'finish'
  if (activeStep.value === index) return 'process'
  return 'wait'
}

const reportStatusType = (status: Report['status']) => {
  if (status === 'SIGNED') return 'success'
  if (status === 'SUPERSEDED') return 'info'
  return 'warning'
}

const correctionStatusType = (status: Correction['status']) => {
  if (status === 'ACCEPTED') return 'success'
  if (status === 'REJECTED') return 'danger'
  if (status === 'SUBMITTED') return 'warning'
  return 'info'
}

const correctionStatusText = (status: Correction['status']) => correctionStatusTextMap[status]

const reportStatusText = (status: Report['status']) => reportStatusTextMap[status]

const parseDraft = (draftJson: string | null | undefined) => {
  draftParseError.value = ''

  if (!draftJson || !draftJson.trim()) {
    draftRaw.value = {}
    return
  }

  try {
    const parsed = JSON.parse(draftJson) as Record<string, unknown>
    draftRaw.value = parsed
    draftForm.findings = typeof parsed.findings === 'string' ? parsed.findings : ''
    draftForm.conclusion = typeof parsed.conclusion === 'string' ? parsed.conclusion : ''
    draftForm.recommendation =
      typeof parsed.recommendation === 'string' ? parsed.recommendation : ''
    draftForm.explanation = typeof parsed.explanation === 'string' ? parsed.explanation : ''
    draftForm.disclaimer =
      typeof parsed.disclaimer === 'string'
        ? parsed.disclaimer
        : 'AI 辅助分析，不等同于独立医学诊断。'
  } catch {
    draftRaw.value = {}
    draftParseError.value = '报告草稿格式异常，请重新保存草稿。'
    draftForm.findings = ''
    draftForm.conclusion = ''
    draftForm.recommendation = ''
    draftForm.explanation = ''
    draftForm.disclaimer = 'AI 辅助分析，不等同于独立医学诊断。'
  }
}

const load = async () => {
  loading.value = true
  try {
    const [feedbackList, reportList] = await Promise.all([
      listFeedback(props.resultId),
      listReports(props.resultId),
    ])
    feedback.value = feedbackList
    reports.value = reportList

    if (canCorrect.value) {
      corrections.value = await listCorrections(props.resultId)
    }

    if (doctor.value) {
      review.value = await getReview(props.resultId)
      if (review.value) {
        reviewForm.status = review.value.status
        reviewForm.correctionVersion = review.value.correctionVersion
        reviewForm.findings = review.value.findings ?? ''
        reviewForm.conclusion = review.value.conclusion ?? ''
        reviewForm.recommendation = review.value.recommendation ?? ''
      }

      const reportDraft = await getReportDraft(props.resultId)
      parseDraft(reportDraft.draftJson)
    }
  } finally {
    if (!props.stage) emit('stageChange', recommendedStage.value)
    loading.value = false
  }
}

const sendFeedback = async () => {
  submittingFeedback.value = true
  try {
    await addFeedback(props.resultId, {
      ...feedbackForm,
      issueCodes: [],
    })
    feedbackForm.comment = ''
    feedback.value = await listFeedback(props.resultId)
    ElMessage.success('反馈已提交')
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

    await addCorrection(props.resultId, data)
    corrections.value = await listCorrections(props.resultId)
    resetCorrectionForm()
    ElMessage.success('修正版本已提交')
  } finally {
    submittingCorrection.value = false
  }
}

const sendReview = async () => {
  savingReview.value = true
  try {
    review.value = await submitReview(props.resultId, {
      ...reviewForm,
      expectedVersion: review.value?.version ?? 0,
    })
    emit('workflowUpdated')
    ElMessage.success('审核已保存')
  } finally {
    savingReview.value = false
  }
}

const hasReviewFormContent = () =>
  Boolean(
    reviewForm.findings.trim() || reviewForm.conclusion.trim() || reviewForm.recommendation.trim(),
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
    await ElMessageBox.confirm('AI 生成会覆盖当前草稿中的所见、结论和建议，是否继续？', 'AI 生成草稿', {
      type: 'warning',
    })
  }

  generatingDraft.value = true
  try {
    const reportDraft = await generateReportDraft(props.resultId)
    parseDraft(reportDraft.draftJson)
    const copied = await copyDraftToReview(true)
    emit('workflowUpdated')
    ElMessage.success(
      copied ? 'AI 草稿已生成并带入医生审核表单，请复核后保存' : 'AI 草稿已生成',
    )
  } finally {
    generatingDraft.value = false
  }
}

const sign = async () => {
  if (!savedReviewReadyForSign.value) {
    ElMessage.warning('请先保存完整且已通过的医生审核意见后再签发 PDF')
    return
  }

  await ElMessageBox.confirm('签发后本版本不可修改，确认签发 PDF？', '签发报告', {
    type: 'warning',
  })

  signingReport.value = true
  try {
    await signReport(props.resultId)
    reports.value = await listReports(props.resultId)
    emit('workflowUpdated')
    ElMessage.success('报告已签发')
  } finally {
    signingReport.value = false
  }
}

const download = async (report: Report) => {
  const blob = await downloadReportVersion(props.resultId, report.version)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `report-${props.resultId}-v${report.version}.pdf`
  link.click()
  URL.revokeObjectURL(url)
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="workflow-panel">
    <div class="workflow-header">
      <div>
        <div class="workflow-title">临床闭环</div>
        <div class="workflow-subtitle">
          按步骤处理反馈、修正、AI 草稿和正式审核，每次只展开当前工作区。
        </div>
      </div>
      <el-tag v-if="doctor" type="success">医生工作台</el-tag>
      <el-tag v-else-if="canCorrect" type="warning">研究员视图</el-tag>
      <el-tag v-else type="info">用户视图</el-tag>
    </div>

    <el-steps class="workflow-steps" :active="activeStep" finish-status="success" align-center>
      <el-step
        v-for="(item, index) in workflowStages"
        :key="item.key"
        :title="item.title"
        :status="stepStatus(index)"
        :class="{ 'is-selected': currentStage === item.key }"
        @click="selectStage(item.key)"
      />
    </el-steps>

    <div class="workflow-grid">
      <section v-show="currentStage === 'feedback'" class="workflow-section">
        <div class="section-heading">
          <div>
            <h3>1. 人工反馈</h3>
            <p>记录对 AI 结果的评价，不会覆盖原始结果。</p>
          </div>
        </div>

        <el-form class="feedback-form">
          <el-form-item label="结论">
            <el-select v-model="feedbackForm.verdict">
              <el-option label="接受" value="ACCEPTED" />
              <el-option label="部分正确" value="PARTIAL" />
              <el-option label="不正确" value="INCORRECT" />
            </el-select>
          </el-form-item>
          <el-form-item label="说明">
            <el-input
              v-model="feedbackForm.comment"
              maxlength="500"
              placeholder="可以说明 AI 结果哪里好、哪里需要修改"
              show-word-limit
            />
          </el-form-item>
          <el-button
            type="primary"
            :loading="submittingFeedback"
            @click="sendFeedback"
          >
            提交反馈
          </el-button>
        </el-form>

        <div class="history-list">
          <el-empty v-if="feedback.length === 0" description="暂无反馈" />
          <div v-for="item in feedback" v-else :key="item.id" class="history-item">
            <div class="history-main">
              <el-tag size="small">{{ feedbackVerdictTextMap[item.verdict] }}</el-tag>
              <span>{{ formatEmpty(item.comment) }}</span>
            </div>
            <span class="history-time">{{ formatDateTime(item.createdAt) }}</span>
          </div>
        </div>
      </section>

      <section v-if="canCorrect" v-show="currentStage === 'correction'" class="workflow-section">
        <div class="section-heading">
          <div>
            <h3>2. 修正版本</h3>
            <p>上传人工修正 mask，系统会按版本保存，不覆盖 AI 原始 mask。</p>
          </div>
          <el-tag type="info">当前 V{{ latestCorrectionVersion }}</el-tag>
        </div>

        <el-form label-position="top" class="correction-form">
          <el-form-item label="修正 mask">
            <input
              ref="fileInput"
              type="file"
              accept="image/png,image/jpeg,image/tiff,.tif,.tiff"
              @change="pickFile"
            />
            <div class="hint">支持 PNG、JPEG、TIFF；后端会统一保存为二值 PNG。</div>
          </el-form-item>
          <el-form-item label="修正原因">
            <el-input
              v-model="correctionForm.reason"
              placeholder="例如：局部血管断裂，需要人工补全"
            />
          </el-form-item>
          <div class="section-actions">
            <el-button
              type="primary"
              plain
              :loading="submittingCorrection"
              @click="sendCorrection"
            >
              上传修正版本
            </el-button>
          </div>
        </el-form>

        <div class="compact-table">
          <el-empty v-if="corrections.length === 0" description="暂无修正版本" />
          <el-table v-else :data="corrections" size="small">
            <el-table-column label="版本" width="90">
              <template #default="{ row }">V{{ row.version }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="correctionStatusType(row.status)" size="small">
                  {{ correctionStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="原因" show-overflow-tooltip />
          </el-table>
        </div>
      </section>

      <section v-if="doctor" v-show="currentStage === 'draft'" class="workflow-section">
        <div class="section-heading">
          <div>
            <h3>3. AI 草稿参考</h3>
            <p>先由大模型生成报告草稿和结果解释，只作为医生审核前的参考。</p>
          </div>
        </div>

        <el-alert
          v-if="draftParseError"
          :title="draftParseError"
          type="warning"
          show-icon
          :closable="false"
          class="draft-alert"
        />
        <el-alert
          title="AI 草稿仅供医生参考，不是正式审核意见；PDF 只采用医生审核保存后的内容。"
          type="info"
          show-icon
          :closable="false"
          class="draft-alert"
        />

        <el-form label-position="top" class="draft-form">
          <el-form-item label="AI 草稿所见">
            <el-input v-model="draftForm.findings" type="textarea" :rows="3" readonly />
          </el-form-item>
          <el-form-item label="AI 草稿结论">
            <el-input v-model="draftForm.conclusion" type="textarea" :rows="3" readonly />
          </el-form-item>
          <el-form-item label="AI 草稿建议">
            <el-input v-model="draftForm.recommendation" type="textarea" :rows="3" readonly />
          </el-form-item>
          <el-form-item label="AI 结果解释">
            <el-input v-model="draftForm.explanation" type="textarea" :rows="3" readonly />
          </el-form-item>
          <div v-if="llmDraftMeta.visible" class="llm-meta">
            <span>生成来源：{{ formatEmpty(llmDraftMeta.provider) }}</span>
            <span>模型：{{ formatEmpty(llmDraftMeta.model) }}</span>
            <span>生成时间：{{ formatDateTime(llmDraftMeta.generatedAt) }}</span>
          </div>
          <el-form-item label="固定声明">
            <el-input v-model="draftForm.disclaimer" readonly />
          </el-form-item>
          <div class="section-actions">
            <el-button type="primary" plain :loading="generatingDraft" @click="generateDraft">
              AI 生成草稿
            </el-button>
            <el-button plain @click="copyDraftToReview(true)">带入医生审核</el-button>
          </div>
        </el-form>
      </section>

      <section v-show="currentStage === 'review'" class="workflow-section">
        <div class="section-heading">
          <div>
            <h3>4. 医生审核与签发</h3>
            <p>医生在参考 AI 草稿后保存正式审核意见；PDF 只以医生审核意见为准。</p>
          </div>
          <el-tag v-if="review" type="success">审核版本 V{{ review.version }}</el-tag>
          <el-tag v-else type="info">尚未审核</el-tag>
        </div>

        <template v-if="doctor">
          <el-form label-position="top" class="review-form">
            <div class="form-row">
              <el-form-item label="审核状态">
                <el-select v-model="reviewForm.status">
                  <el-option label="待审核" value="PENDING" />
                  <el-option label="需修改" value="CHANGES_REQUESTED" />
                  <el-option label="通过" value="APPROVED" />
                  <el-option label="拒绝" value="REJECTED" />
                </el-select>
              </el-form-item>
              <el-form-item label="采用结果">
                <el-select v-model="reviewForm.correctionVersion">
                  <el-option label="AI 原始结果" :value="null" />
                  <el-option
                    v-for="item in corrections"
                    :key="item.version"
                    :label="`修正版本 V${item.version}`"
                    :value="item.version"
                  />
                </el-select>
              </el-form-item>
            </div>
            <el-form-item label="医生所见">
              <el-input v-model="reviewForm.findings" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="审核结论">
              <el-input v-model="reviewForm.conclusion" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="处理建议">
              <el-input v-model="reviewForm.recommendation" type="textarea" :rows="3" />
            </el-form-item>
            <div class="section-actions sticky-actions">
              <el-button type="primary" :loading="savingReview" @click="sendReview">
                保存审核
              </el-button>
              <el-button type="danger" :loading="signingReport" @click="sign">
                签发 PDF
              </el-button>
            </div>
          </el-form>
        </template>

        <div class="report-history">
          <div class="history-title">报告历史</div>
          <el-empty v-if="reports.length === 0" description="暂无报告" />
          <el-table v-else :data="visibleReports" size="small">
            <el-table-column label="版本" width="90">
              <template #default="{ row }">V{{ row.version }}</template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="reportStatusType(row.status)" size="small">
                  {{ reportStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="签发时间" min-width="160">
              <template #default="{ row }">{{ formatDateTime(row.signedAt) }}</template>
            </el-table-column>
            <el-table-column label="SHA-256" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ formatEmpty(row.reportSha256) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button
                  v-if="row.status !== 'DRAFT'"
                  link
                  type="primary"
                  @click="download(row)"
                >
                  下载
                </el-button>
                <span v-else class="muted">未签发</span>
              </template>
            </el-table-column>
          </el-table>
          <el-button
            v-if="reports.length > 1"
            link
            type="primary"
            class="history-toggle"
            @click="showAllReports = !showAllReports"
          >
            {{ showAllReports ? '收起历史版本' : `查看全部 ${reports.length} 个版本` }}
          </el-button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.workflow-panel {
  min-height: 360px;
}

.workflow-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.workflow-title {
  color: #111827;
  font-size: 18px;
  font-weight: 700;
}

.workflow-subtitle {
  margin-top: 4px;
  color: #6b7280;
  font-size: 13px;
}

.workflow-steps {
  margin: 0 0 14px;
  padding: 14px 18px;
  overflow-x: auto;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f9fafb;
}

.workflow-steps :deep(.el-step) {
  cursor: pointer;
}

.workflow-steps :deep(.el-step.is-selected .el-step__title) {
  color: #409eff;
  font-weight: 700;
}

.workflow-grid {
  display: grid;
  grid-template-columns: 1fr;
}

.workflow-section {
  min-width: 0;
  padding: 16px 0 0;
  border-top: 1px solid #eef2f7;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.section-heading h3 {
  margin: 0;
  color: #111827;
  font-size: 16px;
  font-weight: 700;
}

.section-heading p {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 13px;
}

.feedback-form,
.correction-form,
.review-form,
.draft-form {
  padding: 14px;
  border-radius: 10px;
  background: #f9fafb;
}

.feedback-form {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: flex-start;
}

.feedback-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.hint {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
}

.section-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.sticky-actions {
  position: sticky;
  z-index: 3;
  bottom: 0;
  margin: 0 -14px -14px;
  padding: 12px 14px;
  border-top: 1px solid #e5e7eb;
  background: rgb(249 250 251 / 96%);
  backdrop-filter: blur(6px);
}

.history-list {
  margin-top: 14px;
}

.history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #eef2f7;
}

.history-item:last-child {
  border-bottom: 0;
}

.history-main {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
  color: #374151;
}

.history-main span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-time,
.muted {
  color: #909399;
  font-size: 12px;
  white-space: nowrap;
}

.compact-table,
.report-history {
  margin-top: 14px;
}

.history-title {
  margin: 8px 0 12px;
  color: #374151;
  font-weight: 600;
}

.history-toggle {
  margin-top: 8px;
}

.draft-alert {
  margin-bottom: 12px;
}

.llm-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin: -8px 0 16px;
  color: #6b7280;
  font-size: 12px;
}

@media (max-width: 1080px) {
  .feedback-form,
  .form-row {
    grid-template-columns: 1fr;
  }

  .section-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 700px) {
  .workflow-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .workflow-steps {
    justify-content: flex-start;
  }

  .workflow-steps :deep(.el-step) {
    min-width: 150px;
    flex-basis: 150px !important;
  }

  .feedback-form,
  .correction-form,
  .review-form,
  .draft-form {
    padding: 12px;
  }
}
</style>
