<script setup lang="ts">
import { DataAnalysis, Refresh, VideoPause, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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
import { formatDateTime } from '@/utils/format'

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
let pollingTimer: ReturnType<typeof setInterval> | null = null

const roleSkillCodes: Record<AgentEvaluationRole, string[]> = {
  DOCTOR: [
    'DOCTOR_WORKLOAD_OVERVIEW', 'ASSIGNED_CASE_SEARCH', 'CASE_CLINICAL_SUMMARY',
    'CASE_FOLLOWUP_ANALYSIS', 'DOCTOR_TASK_SEARCH', 'DOCTOR_CLINICAL_QUEUE', 'MEDICAL_KNOWLEDGE_QA',
  ],
  PATIENT: ['MY_CASE_LIST', 'MY_CASE_PROGRESS', 'MY_SIGNED_REPORT', 'PATIENT_KNOWLEDGE_QA'],
}

const availableDatasets = computed(() => datasets.value.filter((item) => item.targetRole === form.targetRole))
const selectedDataset = computed(() => datasets.value.find((item) => Number(item.id) === form.datasetId) || null)
const running = computed(() => ['QUEUED', 'RUNNING'].includes(selectedRun.value?.status || ''))
const progressPercent = computed(() => {
  const value = selectedRun.value?.progress
  return value?.total ? Math.round(value.completed * 100 / value.total) : 0
})

const percent = (value: number | null | undefined) => value == null ? '-' : `${(value * 100).toFixed(1)}%`
const statusType = (status: string) => ({
  PASSED: 'success', FAILED: 'danger', INVALID: 'warning', CANCELLED: 'info', RUNNING: 'primary', QUEUED: 'primary',
}[status] || 'info') as 'success' | 'danger' | 'warning' | 'info' | 'primary'

const activeVersionMap = (source: Record<string, Array<{ id: number; active: boolean }>>, codes: string[]) => {
  const target: Record<string, number> = {}
  for (const code of codes) {
    const version = source[code]?.find((item) => item.active) || source[code]?.[0]
    if (version) target[code] = Number(version.id)
  }
  return target
}

const loadRuns = async () => {
  const page = await getAgentEvaluationRuns({ page: 1, pageSize: 30 })
  runs.value = page.records
}

const stopPolling = () => {
  if (pollingTimer) clearInterval(pollingTimer)
  pollingTimer = null
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
  if (updateUrl) await router.replace({ path: '/agent-evaluations', query: { runId: String(runId) } })
  stopPolling()
  if (running.value) {
    pollingTimer = setInterval(async () => {
      if (!selectedRun.value) return
      const current = await getAgentEvaluationRun(Number(selectedRun.value.id))
      selectedRun.value = current
      if (!['QUEUED', 'RUNNING'].includes(current.status)) {
        stopPolling()
        await Promise.all([loadRuns(), loadResults()])
      }
    }, 2000)
  }
}

const refresh = async () => {
  loading.value = true
  try {
    await loadRuns()
    if (selectedRun.value) await selectRun(Number(selectedRun.value.id), false)
  } finally { loading.value = false }
}

const start = async () => {
  if (!options.value || !selectedDataset.value || !form.modelKey) {
    ElMessage.warning('请选择角色、评测集和模型配置')
    return
  }
  const skillVersions = activeVersionMap(options.value.skillVersions, roleSkillCodes[form.targetRole])
  const promptVersions = activeVersionMap(options.value.promptVersions, ['AGENT_SKILL_ROUTER'])
  if (Object.keys(skillVersions).length !== roleSkillCodes[form.targetRole].length || !promptVersions.AGENT_SKILL_ROUTER) {
    ElMessage.warning('当前角色缺少可用的 Skill 或路由 Prompt 版本')
    return
  }
  try {
    await ElMessageBox.confirm(
      `本次将串行调用真实模型约 ${selectedDataset.value.sampleCount} 次，使用固定匿名数据，不会写入正式会话。确认启动？`,
      '启动 Agent 评测',
      { type: 'warning', confirmButtonText: '启动评测', cancelButtonText: '取消' },
    )
  } catch { return }
  actionLoading.value = true
  try {
    const run = await startAgentEvaluation({
      datasetId: Number(selectedDataset.value.id), targetRole: form.targetRole,
      modelKey: form.modelKey, skillVersions, promptVersions,
    })
    await loadRuns()
    await selectRun(Number(run.id))
    ElMessage.success('评测已进入队列')
  } finally { actionLoading.value = false }
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
    decision === 'APPROVED' ? '确认人工抽查通过？该运行可用于候选版本启用判断。' : '确认拒绝本次评测？',
    '提交人工评审', { type: decision === 'APPROVED' ? 'warning' : 'error' },
  )
  selectedRun.value = await reviewAgentEvaluation(Number(selectedRun.value.id), decision, reviewNote.value)
  await loadRuns()
  ElMessage.success('评审结论已保存')
}

watch(() => form.targetRole, () => {
  form.datasetId = Number(availableDatasets.value[0]?.id || 0)
})
watch(() => [resultFilters.success, resultFilters.errorType, resultFilters.page], () => { void loadResults() })

onMounted(async () => {
  loading.value = true
  try {
    const [optionData, datasetData] = await Promise.all([
      getAgentEvaluationOptions(), getAgentEvaluationDatasets(), loadRuns(),
    ])
    options.value = optionData
    datasets.value = datasetData
    form.modelKey = optionData.models[0]?.key || ''
    form.datasetId = Number(datasetData.find((item) => item.targetRole === form.targetRole)?.id || 0)
    const runId = Number(route.query.runId)
    if (runId) await selectRun(runId, false)
    else if (runs.value[0]) await selectRun(Number(runs.value[0].id), false)
  } finally { loading.value = false }
})

onBeforeUnmount(stopPolling)
</script>

<template>
  <section class="evaluation-page" v-loading="loading">
    <header class="page-heading">
      <div>
        <h2>Agent 评测中心</h2>
        <p>使用固定匿名数据验证真实模型路由、参数、上下文、结构与安全边界。</p>
      </div>
      <el-button :icon="Refresh" @click="refresh">刷新</el-button>
    </header>

    <div class="workspace">
      <aside class="run-sidebar">
        <section class="config-section">
          <div class="section-title"><el-icon><VideoPlay /></el-icon> 新建评测</div>
          <el-segmented v-model="form.targetRole" :options="[{ label: '医生', value: 'DOCTOR' }, { label: '患者', value: 'PATIENT' }]" />
          <label>匿名评测集</label>
          <el-select v-model="form.datasetId" placeholder="选择评测集">
            <el-option v-for="item in availableDatasets" :key="item.id" :label="`${item.name} · v${item.version}`" :value="Number(item.id)" />
          </el-select>
          <label>受控模型</label>
          <el-select v-model="form.modelKey">
            <el-option v-for="item in options?.models || []" :key="item.key" :label="`${item.provider} / ${item.model}`" :value="item.key" />
          </el-select>
          <div class="call-estimate">
            <strong>{{ selectedDataset?.sampleCount || 0 }}</strong>
            <span>预计 API 调用</span>
          </div>
          <el-button type="primary" :icon="VideoPlay" :loading="actionLoading" @click="start">启动评测</el-button>
        </section>

        <section class="run-list">
          <div class="section-title">最近运行</div>
          <button v-for="item in runs" :key="item.id" class="run-item" :class="{ active: selectedRun?.id === item.id }" @click="selectRun(Number(item.id))">
            <span class="run-main"><b>#{{ item.id }}</b><span>{{ item.targetRole === 'DOCTOR' ? '医生' : '患者' }}</span></span>
            <span class="run-meta"><el-tag size="small" :type="statusType(item.status)">{{ item.status }}</el-tag>{{ formatDateTime(item.createdAt) }}</span>
          </button>
          <el-empty v-if="!runs.length" :image-size="48" description="暂无评测运行" />
        </section>
      </aside>

      <main class="run-detail">
        <el-empty v-if="!selectedRun" :image-size="80" description="选择一次评测运行查看详情" />
        <template v-else>
          <div class="detail-heading">
            <div>
              <div class="title-line"><h3>运行 #{{ selectedRun.id }}</h3><el-tag :type="statusType(selectedRun.status)">{{ selectedRun.status }}</el-tag></div>
              <p>{{ selectedRun.provider }} / {{ selectedRun.model }} · 数据集 v{{ selectedRun.datasetVersion }} · {{ formatDateTime(selectedRun.createdAt) }}</p>
            </div>
            <el-button v-if="running" :icon="VideoPause" @click="cancel">取消运行</el-button>
          </div>

          <section v-if="running" class="progress-band">
            <div><b>{{ selectedRun.progress.completed }} / {{ selectedRun.progress.total }}</b><span>逐条串行执行中</span></div>
            <el-progress :percentage="progressPercent" :stroke-width="10" />
          </section>

          <section v-if="selectedRun.metrics" class="metric-strip">
            <div><span>路由准确率</span><b>{{ percent(selectedRun.metrics.routingAccuracy) }}</b></div>
            <div><span>参数准确率</span><b>{{ percent(selectedRun.metrics.parameterAccuracy) }}</b></div>
            <div><span>查询正确率</span><b>{{ percent(selectedRun.metrics.queryAccuracy) }}</b></div>
            <div><span>结构通过率</span><b>{{ percent(selectedRun.metrics.structurePassRate) }}</b></div>
            <div><span>安全通过率</span><b>{{ percent(selectedRun.metrics.safetyPassRate) }}</b></div>
            <div><span>P95 耗时</span><b>{{ selectedRun.metrics.p95LatencyMs }} ms</b></div>
          </section>

          <section class="binding-section">
            <div class="section-title"><el-icon><DataAnalysis /></el-icon> 版本快照</div>
            <div class="binding-list">
              <span v-for="item in selectedRun.bindings" :key="`${item.type}-${item.code}`">{{ item.code }} · {{ item.versionLabel }}</span>
            </div>
          </section>

          <section class="results-section">
            <div class="results-toolbar">
              <div class="section-title">样例明细 <small>{{ resultTotal }} 条</small></div>
              <div class="filters">
                <el-select v-model="resultFilters.success" style="width: 120px"><el-option label="仅失败" value="false" /><el-option label="全部" value="" /><el-option label="仅成功" value="true" /></el-select>
                <el-select v-model="resultFilters.errorType" clearable placeholder="失败类型" style="width: 190px">
                  <el-option v-for="type in ['ROUTING_ERROR','ARGUMENT_ERROR','QUERY_ASSERTION_ERROR','STRUCTURE_ERROR','SAFETY_ERROR','CONTEXT_ERROR','CITATION_ERROR','MODEL_ERROR','INFRASTRUCTURE_ERROR']" :key="type" :label="type" :value="type" />
                </el-select>
              </div>
            </div>
            <el-table :data="results" stripe empty-text="当前筛选下暂无样例">
              <el-table-column prop="category" label="类别" width="115" />
              <el-table-column prop="inputSummary" label="匿名问题" min-width="260" show-overflow-tooltip />
              <el-table-column label="Skill" min-width="210"><template #default="{ row }"><span>{{ row.expectedSkill || '应拒绝' }}</span><span class="actual-skill">{{ row.actualSkill || '-' }}</span></template></el-table-column>
              <el-table-column label="结果" width="90"><template #default="{ row }"><el-tag size="small" :type="row.success ? 'success' : 'danger'">{{ row.success ? '通过' : '失败' }}</el-tag></template></el-table-column>
              <el-table-column prop="errorType" label="失败类型" min-width="160"><template #default="{ row }">{{ row.errorType || '-' }}</template></el-table-column>
              <el-table-column prop="latencyMs" label="耗时(ms)" width="100" />
            </el-table>
            <el-pagination v-if="resultTotal > resultFilters.pageSize" v-model:current-page="resultFilters.page" :page-size="resultFilters.pageSize" :total="resultTotal" layout="prev, pager, next" />
          </section>

          <section v-if="['PASSED', 'FAILED'].includes(selectedRun.status)" class="review-bar">
            <el-input v-model="reviewNote" maxlength="1000" show-word-limit placeholder="填写人工抽查说明" />
            <el-button type="danger" plain @click="review('REJECTED')">拒绝</el-button>
            <el-button type="primary" :disabled="selectedRun.status !== 'PASSED' || !selectedRun.automatedPass" @click="review('APPROVED')">批准</el-button>
          </section>
        </template>
      </main>
    </div>
  </section>
</template>

<style scoped>
.evaluation-page { display: flex; flex-direction: column; gap: 14px; min-width: 0; }
.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.page-heading h2, .detail-heading h3 { margin: 0; }
.page-heading p, .detail-heading p { margin: 5px 0 0; color: #64748b; }
.workspace { display: grid; grid-template-columns: 300px minmax(0, 1fr); min-height: 680px; border: 1px solid #dfe5ec; background: #fff; }
.run-sidebar { border-right: 1px solid #e5eaf0; background: #f8fafc; }
.config-section, .run-list { display: flex; flex-direction: column; gap: 10px; padding: 16px; }
.config-section { border-bottom: 1px solid #e5eaf0; }
.config-section label { color: #475569; font-size: 13px; }
.section-title { display: flex; align-items: center; gap: 7px; font-weight: 700; color: #0f172a; }
.section-title small { color: #64748b; font-weight: 400; }
.call-estimate { display: flex; align-items: baseline; justify-content: space-between; padding: 10px 12px; border: 1px solid #dbe4ee; background: #fff; }
.call-estimate strong { font-size: 22px; color: #0f766e; }
.call-estimate span { color: #64748b; font-size: 12px; }
.run-list { max-height: 420px; overflow: auto; }
.run-item { display: flex; flex-direction: column; gap: 7px; width: 100%; padding: 10px; border: 1px solid transparent; background: transparent; color: inherit; text-align: left; cursor: pointer; }
.run-item:hover, .run-item.active { border-color: #bfdbfe; background: #eff6ff; }
.run-main, .run-meta { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.run-meta { color: #64748b; font-size: 12px; }
.run-detail { min-width: 0; padding: 18px; }
.detail-heading, .title-line, .results-toolbar, .review-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.title-line { justify-content: flex-start; }
.progress-band { display: grid; grid-template-columns: 170px 1fr; align-items: center; gap: 20px; margin: 18px 0; padding: 14px; background: #f0f7ff; }
.progress-band div { display: flex; flex-direction: column; }
.progress-band span { color: #64748b; font-size: 12px; }
.metric-strip { display: grid; grid-template-columns: repeat(6, minmax(100px, 1fr)); border: 1px solid #e2e8f0; margin: 18px 0; }
.metric-strip div { display: flex; flex-direction: column; gap: 5px; padding: 12px; border-right: 1px solid #e2e8f0; }
.metric-strip div:last-child { border-right: 0; }
.metric-strip span { color: #64748b; font-size: 12px; }
.metric-strip b { font-size: 18px; }
.binding-section, .results-section { margin-top: 18px; }
.binding-list { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
.binding-list span { padding: 5px 8px; border: 1px solid #dbe4ee; color: #475569; font-size: 12px; }
.filters { display: flex; gap: 8px; }
.results-section :deep(.el-table) { margin-top: 10px; }
.actual-skill { display: block; color: #64748b; font-size: 12px; }
.results-section :deep(.el-pagination) { justify-content: flex-end; margin-top: 12px; }
.review-bar { position: sticky; bottom: 0; margin: 18px -18px -18px; padding: 12px 18px; border-top: 1px solid #dbe4ee; background: rgba(255, 255, 255, .96); }
.review-bar .el-input { flex: 1; }
@media (max-width: 1100px) { .metric-strip { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 760px) {
  .workspace { grid-template-columns: 1fr; }
  .run-sidebar { border-right: 0; border-bottom: 1px solid #e5eaf0; }
  .metric-strip { grid-template-columns: repeat(2, 1fr); }
  .progress-band { grid-template-columns: 1fr; }
  .results-toolbar, .review-bar { align-items: stretch; flex-wrap: wrap; }
  .filters { width: 100%; }
}
</style>
