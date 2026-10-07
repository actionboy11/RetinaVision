<script setup lang="ts">
import { Refresh, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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
import type { RagEvaluationRun, PromptTemplateVersion } from '@/types/prompt'

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
const candidateAlreadyActive = computed(() => versions.value.some(
  (version) => version.id === selected.value?.candidateVersionId && version.active,
))
const canRelease = computed(() => isAdmin.value && selected.value?.status === 'COMPLETED'
  && selected.value.automatedPass === true && selected.value.reviewDecision === 'APPROVED'
  && !candidateAlreadyActive.value)

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
    await ElMessageBox.confirm('将对固定合成样本调用 Embedding、Qdrant 和两版 LLM，可能产生模型费用。',
      '启动 RAG 评测', { type: 'warning', confirmButtonText: '启动', cancelButtonText: '取消' })
  } catch { return }
  actionLoading.value = true
  try {
    const run = await startRagEvaluation(candidateId.value)
    await loadRuns()
    await loadDetail(run.id)
    ElMessage.success('RAG 评测已提交')
  } catch {
    // Request interceptor displays the backend error.
  } finally {
    actionLoading.value = false
  }
}

const review = async () => {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm('请确认已逐题检查回答的事实性，以及引用原文是否真正支持回答。',
      '保存人工评审', { type: 'warning', confirmButtonText: '确认保存', cancelButtonText: '取消' })
  } catch { return }
  actionLoading.value = true
  try {
    selected.value = await reviewRagEvaluation(selected.value.id, decision.value === 'APPROVED', score.value, note.value)
    await loadRuns()
    ElMessage.success('人工评审已保存')
  } catch {
    // Request interceptor displays the backend error.
  } finally {
    actionLoading.value = false
  }
}

const release = async () => {
  if (!selected.value || !canRelease.value) return
  try {
    await ElMessageBox.confirm('启用后真实知识问答将使用该版本；只有逐字核验的引用会展示。',
      '启用 RAG Prompt', { type: 'warning', confirmButtonText: '确认启用', cancelButtonText: '取消' })
  } catch { return }
  actionLoading.value = true
  try {
    await activatePromptTemplateVersion('RAG_KNOWLEDGE_CHAT', selected.value.candidateVersionId)
    await loadVersions()
    ElMessage.success('RAG Prompt 已启用')
  } catch {
    // Backend checks that the approval is current.
  } finally {
    actionLoading.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadRuns(), loadVersions()])
  polling = setInterval(() => {
    if (runs.value.some((run) => run.status === 'QUEUED' || run.status === 'RUNNING')) void loadRuns()
  }, 3000)
})
onUnmounted(() => { if (polling) clearInterval(polling) })
</script>

<template>
  <section class="rag-evaluation">
    <header class="heading">
      <div><h2>RAG 检索与引用评测</h2><p>固定合成知识片段 · 独立评测索引</p></div>
      <el-button :icon="Refresh" :loading="loading" @click="loadRuns">刷新</el-button>
    </header>
    <el-alert type="warning" show-icon :closable="false"
      title="Hit@3 和 MRR 衡量检索排序；逐字引用校验只验证来源，不能证明医学事实性。管理员须逐题人工评审后才可启用。" />

    <div v-if="isAdmin" class="controls">
      <span>候选版本</span>
      <el-select v-model="candidateId" placeholder="选择未启用版本" style="width: 180px">
        <el-option v-for="version in candidateVersions" :key="version.id"
          :label="`v${version.version}`" :value="version.id" />
      </el-select>
      <el-button type="primary" :icon="VideoPlay" :disabled="!candidateId"
        :loading="actionLoading" @click="start">运行评测</el-button>
    </div>

    <el-table :data="runs" border empty-text="暂无评测记录" :row-class-name="() => 'clickable'"
      @row-click="(row: RagEvaluationRun) => loadDetail(row.id)">
      <el-table-column prop="id" label="编号" width="80" />
      <el-table-column prop="createdAt" label="提交时间" min-width="170" />
      <el-table-column prop="sampleVersion" label="样本集" width="110" />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column label="自动校验" width="110">
        <template #default="{ row }: { row: RagEvaluationRun }">{{ row.automatedPass === null ? '-' : row.automatedPass ? '通过' : '未通过' }}</template>
      </el-table-column>
      <el-table-column label="人工评审" width="110">
        <template #default="{ row }: { row: RagEvaluationRun }">{{ row.reviewDecision === 'APPROVED' ? '批准' : row.reviewDecision === 'REJECTED' ? '拒绝' : '待评审' }}</template>
      </el-table-column>
    </el-table>

    <section v-if="selected" class="detail">
      <div class="heading">
        <div><h3>评测 #{{ selected.id }}</h3><p>{{ selected.provider }} / {{ selected.model }} · {{ selected.embeddingModel }} · 阈值 {{ selected.scoreThreshold }} · {{ selected.sampleVersion }}</p></div>
        <el-button v-if="canRelease" type="primary" :loading="actionLoading" @click="release">启用候选版本</el-button>
      </div>
      <el-alert v-if="selected.failureReason" type="error" :title="selected.failureReason" :closable="false" />
      <div v-if="selected.result" class="metrics">
        <div>Hit@3 <strong>{{ (selected.result.retrieval.hitAt3 * 100).toFixed(1) }}%</strong></div>
        <div>MRR <strong>{{ selected.result.retrieval.mrr.toFixed(3) }}</strong></div>
        <div>候选回答 <strong>{{ selected.result.generationPassed ? '全部可核验' : '存在未核验回答' }}</strong></div>
      </div>
      <div v-for="item in selected.result?.cases || []" :key="item.caseId" class="case-row">
        <div class="case-title"><strong>{{ item.title }}</strong><span>预期 #{{ item.expectedChunkId }} · 排名 {{ item.rank || '未命中' }}</span></div>
        <p class="question">{{ item.question }}</p>
        <p class="hit-list">检索命中：{{ item.retrievedChunkIds.length ? item.retrievedChunkIds.map(id => `#${id}`).join('、') : '无' }}</p>
        <div class="comparison">
          <div v-for="side in (['baseline', 'candidate'] as const)" :key="side" class="answer">
            <div class="answer-heading"><strong>{{ side === 'baseline' ? '当前版' : '候选版' }}</strong>
              <el-tag size="small" :type="item[side].passed ? 'success' : 'danger'">{{ item[side].passed ? '引用可核验' : item[side].errorCode }}</el-tag>
              <span>{{ item[side].latencyMs }} ms</span></div>
            <p>{{ item[side].answer || '无可展示的有效回答' }}</p>
            <ul v-if="item[side].citations.length"><li v-for="citation in item[side].citations" :key="citation.chunkId">
              #{{ citation.chunkId }} {{ citation.documentTitle }}：{{ citation.snippet }}</li></ul>
          </div>
        </div>
      </div>
      <p v-if="selected.reviewDecision" class="reviewed">人工评审：{{ selected.reviewDecision === 'APPROVED' ? '批准' : '拒绝' }} · {{ selected.reviewScore }}/5 分 · {{ selected.reviewNote || '无备注' }}</p>
      <div v-if="isAdmin && selected.status === 'COMPLETED' && !selected.reviewDecision" class="review-form">
        <h3>管理员人工评审</h3>
        <el-radio-group v-model="decision"><el-radio-button label="APPROVED" :disabled="selected.automatedPass !== true">批准</el-radio-button><el-radio-button label="REJECTED">拒绝</el-radio-button></el-radio-group>
        <div class="score"><span>质量评分</span><el-rate v-model="score" :max="5" /></div>
        <el-input v-model="note" type="textarea" :rows="3" :maxlength="500" show-word-limit
          placeholder="逐题说明事实性、引用支撑性和需改进之处" />
        <el-button type="primary" :loading="actionLoading" @click="review">保存复核意见</el-button>
      </div>
    </section>
  </section>
</template>

<style scoped>
.rag-evaluation { display: flex; flex-direction: column; gap: 18px; }
.heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
h2, h3, p { margin: 0; }
h2 { font-size: 20px; }
h3 { font-size: 16px; }
.heading p, .hit-list { color: #667085; font-size: 12px; margin-top: 4px; }
.controls, .score, .case-title, .answer-heading { display: flex; align-items: center; gap: 12px; }
.controls { font-size: 13px; }
.detail { border-top: 1px solid #d9dee7; padding-top: 18px; display: flex; flex-direction: column; gap: 15px; }
.metrics { display: flex; gap: 28px; padding: 12px 0; border-bottom: 1px solid #e5e7eb; font-size: 13px; }
.metrics strong { margin-left: 7px; }
.case-row { border-bottom: 1px solid #e5e7eb; padding: 12px 0; }
.case-title { justify-content: space-between; font-size: 13px; }
.case-title span { color: #667085; }
.question { margin-top: 6px; font-size: 13px; }
.comparison { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-top: 12px; }
.answer { border: 1px solid #e5e7eb; border-radius: 6px; padding: 12px; min-width: 0; overflow-wrap: anywhere; font-size: 13px; }
.answer-heading { justify-content: space-between; margin-bottom: 8px; }
.answer-heading span { color: #667085; }
.answer ul { padding-left: 18px; color: #475467; }
.review-form { display: flex; flex-direction: column; align-items: flex-start; gap: 12px; border-top: 1px solid #e5e7eb; padding-top: 16px; }
.review-form :deep(.el-textarea) { width: min(600px, 100%); }
.reviewed { font-size: 13px; }
@media (max-width: 760px) { .comparison { grid-template-columns: 1fr; } .heading, .metrics { flex-wrap: wrap; } }
</style>
