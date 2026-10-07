<script setup lang="ts">
import { Refresh, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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
import type { PromptEvaluationOutcome, PromptEvaluationRun, PromptTemplateVersion } from '@/types/prompt'

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
const candidateAlreadyActive = computed(() => versions.value.some(
  (version) => version.id === selected.value?.candidateVersionId && version.active,
))
const canRelease = computed(() => isAdmin.value && selected.value?.status === 'COMPLETED'
  && selected.value.automatedPass === true && selected.value.reviewDecision === 'APPROVED'
  && !candidateAlreadyActive.value)

const statusText = (status: PromptEvaluationRun['status']) => ({
  QUEUED: '排队中',
  RUNNING: '评测中',
  COMPLETED: '已完成',
  FAILED: '失败',
}[status])

const errorText = (code: string | null) => ({
  INVALID_JSON: 'JSON 格式错误',
  INVALID_FIELD: '字段缺失或超长',
  INVALID_DISCLAIMER: '固定声明不一致',
  UNSAFE_WORDING: '含不合规诊断措辞',
  LLM_ERROR: '模型调用失败',
}[code || ''] || code || '通过')

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
    // The request interceptor already shows the backend error.
  } finally {
    listLoading.value = false
  }
}

const loadDetail = async (id: number) => {
  detailLoading.value = true
  try {
    selected.value = await getPromptEvaluationRun(id)
    decision.value = selected.value.reviewDecision ||
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
    await ElMessageBox.confirm('本次将对固定合成样本分别调用当前版和候选版，可能产生模型费用。继续吗？',
      '启动离线评测', { confirmButtonText: '启动', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  starting.value = true
  try {
    const run = await startPromptEvaluation(candidateId.value)
    await loadRuns()
    await loadDetail(run.id)
    ElMessage.success('评测已提交')
  } catch {
    // The request interceptor already shows the backend error.
  } finally {
    starting.value = false
  }
}

const review = async () => {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm('确认保存本次人工评审意见？批准后可启用该 Prompt 版本。',
      '保存评测意见', { confirmButtonText: '确认保存', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  reviewing.value = true
  try {
    selected.value = await reviewPromptEvaluation(selected.value.id, decision.value === 'APPROVED',
      score.value, note.value)
    await loadRuns()
    ElMessage.success('人工评审意见已保存')
  } catch {
    // The request interceptor already shows the backend error.
  } finally {
    reviewing.value = false
  }
}

const release = async () => {
  if (!selected.value || !canRelease.value) return
  try {
    await ElMessageBox.confirm('确认启用该版本？后续真实报告草稿调用将立即使用它；已发布旧版可回滚。',
      '发布 Prompt 版本', { confirmButtonText: '确认启用', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  releasing.value = true
  try {
    await activatePromptTemplateVersion('REPORT_DRAFT_GENERATION', selected.value.candidateVersionId)
    await loadVersions()
    ElMessage.success('候选 Prompt 已启用')
  } catch {
    // The backend rejects stale evaluations or versions without approval.
  } finally {
    releasing.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadRuns(), loadVersions()])
  polling = setInterval(() => {
    if (runs.value.some((run) => run.status === 'QUEUED' || run.status === 'RUNNING')) void loadRuns()
  }, 3000)
})

onUnmounted(() => {
  if (polling) clearInterval(polling)
})
</script>

<template>
  <section class="evaluation-page">
    <header class="page-heading">
      <div>
        <h2>报告草稿 Prompt 评测</h2>
        <p>固定合成样本 · 当前版与候选版对照</p>
      </div>
      <el-button :icon="Refresh" :loading="listLoading" @click="loadRuns">刷新</el-button>
    </header>

    <el-alert type="warning" show-icon :closable="false"
      title="自动校验只检查格式、安全措辞和声明；管理员须完成离线人工评审后才可启用。评测不会写入病例或正式报告。" />

    <section v-if="isAdmin" class="run-controls">
      <label for="candidate-version">候选版本</label>
      <el-select id="candidate-version" v-model="candidateId" placeholder="选择未启用版本" style="width: 200px">
        <el-option v-for="version in candidateVersions" :key="version.id"
          :label="`v${version.version}`" :value="version.id" />
      </el-select>
      <el-button type="primary" :icon="VideoPlay" :disabled="!candidateId"
        :loading="starting" @click="start">运行评测</el-button>
    </section>

    <section class="runs-section">
      <h3>评测记录</h3>
      <el-table v-loading="listLoading" :data="runs" border empty-text="暂无评测记录"
        highlight-current-row @row-click="(row: PromptEvaluationRun) => loadDetail(row.id)">
        <el-table-column prop="id" label="编号" width="75" />
        <el-table-column prop="createdAt" label="提交时间" min-width="175" />
        <el-table-column prop="sampleVersion" label="样本集" min-width="145" />
        <el-table-column label="版本对比" min-width="150">
          <template #default="{ row }: { row: PromptEvaluationRun }">
            #{{ row.baselineVersionId }} → #{{ row.candidateVersionId }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }: { row: PromptEvaluationRun }">
            <el-tag :type="row.status === 'FAILED' ? 'danger' : row.status === 'COMPLETED' ? 'success' : 'info'">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="自动校验" width="105">
          <template #default="{ row }: { row: PromptEvaluationRun }">
            {{ row.automatedPass === null ? '-' : row.automatedPass ? '通过' : '未通过' }}
          </template>
        </el-table-column>
        <el-table-column label="人工评审" width="105">
          <template #default="{ row }: { row: PromptEvaluationRun }">
            {{ row.reviewDecision === 'APPROVED' ? '批准' : row.reviewDecision === 'REJECTED' ? '拒绝' : '待评审' }}
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section v-if="selected" v-loading="detailLoading" class="detail-section">
      <div class="detail-heading">
        <div>
          <h3>评测 #{{ selected.id }}</h3>
          <p>{{ selected.provider }} / {{ selected.model }} · {{ selected.sampleVersion }}</p>
        </div>
        <el-button v-if="canRelease" type="primary" :loading="releasing" @click="release">启用候选版本</el-button>
      </div>
      <el-alert v-if="selected.failureReason" type="error" :closable="false" :title="selected.failureReason" />

      <div v-if="selected.result?.cases?.length" class="cases">
        <div v-for="item in selected.result.cases" :key="item.caseId" class="case-row">
          <h4>{{ item.title }}</h4>
          <div class="case-context">
            <span>合成输入</span>
            <span>眼别：{{ item.context.case?.eyeSide || '-' }}</span>
            <span>年龄/性别：{{ item.context.case?.age || '-' }} / {{ item.context.case?.gender || '-' }}</span>
            <span>质量：{{ item.context.image?.qualityStatus || '-' }} / {{ item.context.image?.qualityScore ?? '-' }}</span>
            <span>血管面积比例：{{ item.context.result?.resultJson?.vesselAreaRatio ?? '-' }}</span>
          </div>
          <div class="comparison">
            <div v-for="side in (['baseline', 'candidate'] as const)" :key="side" class="comparison-side">
              <div class="side-heading">
                <strong>{{ side === 'baseline' ? '当前版' : '候选版' }}</strong>
                <el-tag :type="item[side].passed ? 'success' : 'danger'" size="small">
                  {{ errorText(item[side].errorCode) }}
                </el-tag>
                <span>{{ item[side].latencyMs }} ms</span>
              </div>
              <dl v-if="readableDraft(item[side])">
                <template v-for="entry in readableDraft(item[side])" :key="entry[0]">
                  <dt>{{ entry[0] }}</dt><dd>{{ entry[1] }}</dd>
                </template>
              </dl>
              <p v-else class="empty-output">未保存不合规或无效输出</p>
            </div>
          </div>
        </div>
      </div>

      <div v-if="selected.reviewDecision" class="review-summary">
        人工评审：{{ selected.reviewDecision === 'APPROVED' ? '批准' : '拒绝' }} ·
        {{ selected.reviewScore }}/5 分<span v-if="selected.reviewNote"> · {{ selected.reviewNote }}</span>
      </div>
      <div v-if="isAdmin && selected.status === 'COMPLETED' && !selected.reviewDecision" class="review-form">
        <h4>管理员人工评审</h4>
        <el-radio-group v-model="decision">
          <el-radio-button label="APPROVED" :disabled="selected.automatedPass !== true">批准</el-radio-button>
          <el-radio-button label="REJECTED">拒绝</el-radio-button>
        </el-radio-group>
        <div class="score-control"><span>质量评分</span><el-rate v-model="score" :max="5" /></div>
        <el-input v-model="note" type="textarea" :rows="3" :maxlength="500"
          show-word-limit placeholder="专业措辞、事实一致性及预期修改工作量" />
        <el-button type="primary" :loading="reviewing" @click="review">保存评测意见</el-button>
      </div>
    </section>
  </section>
</template>

<style scoped>
.evaluation-page { display: flex; flex-direction: column; gap: 18px; }
.page-heading, .detail-heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
h2, h3, h4, p { margin: 0; }
h2 { font-size: 22px; }
h3 { font-size: 16px; }
h4 { font-size: 14px; }
.page-heading p, .detail-heading p { margin-top: 5px; color: #6b7280; font-size: 13px; }
.run-controls { display: flex; gap: 10px; align-items: center; }
.run-controls label { color: #374151; font-size: 13px; }
.runs-section, .detail-section { border-top: 1px solid #e5e7eb; padding-top: 18px; }
.runs-section h3 { margin-bottom: 12px; }
.detail-section { display: flex; flex-direction: column; gap: 16px; }
.case-row { border-top: 1px solid #e5e7eb; padding: 15px 0; }
.case-row h4 { margin-bottom: 10px; }
.case-context { display: flex; flex-wrap: wrap; gap: 8px 20px; margin-bottom: 10px; color: #4b5563; font-size: 12px; }
.comparison { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.comparison-side { min-width: 0; border: 1px solid #e5e7eb; border-radius: 6px; padding: 12px; }
.side-heading { display: flex; gap: 8px; align-items: center; color: #6b7280; font-size: 12px; }
.side-heading strong { margin-right: auto; color: #111827; font-size: 14px; }
dl { margin: 10px 0 0; font-size: 13px; }
dt { color: #6b7280; margin-top: 10px; }
dd { margin: 3px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.6; }
.empty-output { margin-top: 12px; color: #9ca3af; font-size: 13px; }
.review-summary { color: #374151; font-size: 13px; }
.review-form { display: flex; flex-direction: column; align-items: flex-start; gap: 12px; border-top: 1px solid #e5e7eb; padding-top: 16px; width: 100%; }
.score-control { display: flex; align-items: center; gap: 12px; font-size: 13px; }
@media (max-width: 760px) { .comparison { grid-template-columns: 1fr; } .page-heading, .detail-heading { flex-direction: column; } .run-controls { flex-wrap: wrap; } }
</style>
