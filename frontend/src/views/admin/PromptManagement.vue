<script setup lang="ts">
import { Refresh, Select, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import {
  activatePromptTemplateVersion,
  getLlmCallLogs,
  getPromptTemplates,
  getPromptTemplateVersions,
} from '@/api/prompt'
import type { LlmCallLog, PromptTemplate, PromptTemplateVersion } from '@/types/prompt'

const templates = ref<PromptTemplate[]>([])
const router = useRouter()
const versions = ref<PromptTemplateVersion[]>([])
const logs = ref<LlmCallLog[]>([])
const selectedCode = ref('')
const templateLoading = ref(false)
const versionLoading = ref(false)
const logLoading = ref(false)
const switchingVersionId = ref<number | null>(null)
const logFilters = ref<{ templateCode: string; success: '' | 'true' | 'false' }>({
  templateCode: '',
  success: '',
})

const selectedTemplate = computed(() =>
  templates.value.find((item) => item.templateCode === selectedCode.value) || null,
)

const loadTemplates = async () => {
  templateLoading.value = true
  try {
    templates.value = await getPromptTemplates()
    if (!selectedCode.value && templates.value.length) {
      selectedCode.value = templates.value[0].templateCode
    }
  } finally {
    templateLoading.value = false
  }
}

const loadVersions = async (templateCode: string) => {
  selectedCode.value = templateCode
  versionLoading.value = true
  try {
    versions.value = await getPromptTemplateVersions(templateCode)
  } finally {
    versionLoading.value = false
  }
}

const loadLogs = async () => {
  logLoading.value = true
  try {
    logs.value = await getLlmCallLogs({
      templateCode: logFilters.value.templateCode || undefined,
      success: logFilters.value.success === '' ? undefined : logFilters.value.success === 'true',
    })
  } finally {
    logLoading.value = false
  }
}

const switchVersion = async (version: PromptTemplateVersion) => {
  if (version.active) return
  try {
    await ElMessageBox.confirm(
      `确认将 ${selectedTemplate.value?.name || version.templateCode} 切换为 v${version.version} 吗？后续调用会立即使用该版本。`,
      '切换 Prompt 版本',
      {
        confirmButtonText: '确认切换',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  switchingVersionId.value = version.id
  try {
    await activatePromptTemplateVersion(version.templateCode, version.id)
    await Promise.all([loadTemplates(), loadVersions(version.templateCode), loadLogs()])
    ElMessage.success(`已启用 v${version.version}`)
  } finally {
    switchingVersionId.value = null
  }
}

const showVersions = async (template: PromptTemplate) => {
  await loadVersions(template.templateCode)
}

const refreshAll = async () => {
  await Promise.all([
    loadTemplates(),
    selectedCode.value ? loadVersions(selectedCode.value) : Promise.resolve(),
    loadLogs(),
  ])
  ElMessage.success('Prompt 管理数据已刷新')
}

const scenarioLabel = (scenario: string) => ({
  REPORT_DRAFT_GENERATION: 'AI 报告草稿',
  RAG_KNOWLEDGE_CHAT: 'RAG 知识问答',
  CASE_TREND_SUMMARY: '病例趋势摘要',
}[scenario] || scenario)

onMounted(async () => {
  await loadTemplates()
  await Promise.all([
    selectedCode.value ? loadVersions(selectedCode.value) : Promise.resolve(),
    loadLogs(),
  ])
})
</script>

<template>
  <section class="prompt-page">
    <div class="page-heading">
      <div>
        <h2>Prompt 管理</h2>
        <p>查看大模型场景模板、切换已审核版本，并追踪不含敏感上下文的调用摘要。</p>
      </div>
      <div class="heading-actions">
        <el-button @click="router.push('/agent-evaluations')">Agent 评测</el-button>
        <el-button @click="router.push('/prompt-evaluations')">报告草稿评测</el-button>
        <el-button type="primary" :icon="Refresh" @click="refreshAll">刷新</el-button>
      </div>
    </div>

    <el-alert
      type="warning"
      show-icon
      :closable="false"
      title="Prompt 文本仅供查看。新版本须先通过合成样本评测及管理员人工批准；已发布版本可回滚。医疗安全规则仍由后端强制执行。"
    />

    <section class="panel">
      <div class="panel-title">模板列表</div>
      <el-table v-loading="templateLoading" :data="templates" border empty-text="暂无 Prompt 模板">
        <el-table-column prop="name" label="模板" min-width="160" />
        <el-table-column label="场景" min-width="160">
          <template #default="{ row }: { row: PromptTemplate }">{{ scenarioLabel(row.scenario) }}</template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="300" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }: { row: PromptTemplate }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用版本" width="110">
          <template #default="{ row }: { row: PromptTemplate }">
            {{ row.activeVersion ? `v${row.activeVersion}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="120">
          <template #default="{ row }: { row: PromptTemplate }">
            <el-button link type="primary" :icon="View" @click="showVersions(row)">查看版本</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel">
      <div class="panel-title">
        版本详情
        <span v-if="selectedTemplate">{{ selectedTemplate.name }}</span>
      </div>
      <el-table v-loading="versionLoading" :data="versions" border empty-text="请选择模板查看版本">
        <el-table-column type="expand">
          <template #default="{ row }: { row: PromptTemplateVersion }">
            <div class="version-detail">
              <div>
                <h4>System Prompt</h4>
                <pre>{{ row.systemPrompt }}</pre>
              </div>
              <div>
                <h4>输出契约</h4>
                <pre>{{ row.outputContract }}</pre>
              </div>
              <div>
                <h4>安全规则</h4>
                <p>{{ row.safetyPolicy }}</p>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="90">
          <template #default="{ row }: { row: PromptTemplateVersion }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="180" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }: { row: PromptTemplateVersion }">
            <el-tag :type="row.active ? 'success' : 'info'">{{ row.active ? '当前启用' : '未启用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="120">
          <template #default="{ row }: { row: PromptTemplateVersion }">
            <el-button
              link
              type="primary"
              :icon="Select"
              :disabled="row.active"
              :loading="switchingVersionId === row.id"
              @click="switchVersion(row)"
            >
              启用
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel">
      <div class="log-heading">
        <div class="panel-title">最近调用</div>
        <div class="log-filters">
          <el-select v-model="logFilters.templateCode" clearable placeholder="全部模板" @change="loadLogs">
            <el-option v-for="item in templates" :key="item.templateCode" :label="item.name" :value="item.templateCode" />
          </el-select>
          <el-select v-model="logFilters.success" placeholder="全部状态" @change="loadLogs">
            <el-option label="全部状态" value="" />
            <el-option label="成功" value="true" />
            <el-option label="失败" value="false" />
          </el-select>
        </div>
      </div>
      <el-table v-loading="logLoading" :data="logs" border empty-text="暂无调用记录">
        <el-table-column prop="createdAt" label="调用时间" min-width="180" />
        <el-table-column label="场景" min-width="150">
          <template #default="{ row }: { row: LlmCallLog }">{{ scenarioLabel(row.scenario) }}</template>
        </el-table-column>
        <el-table-column label="版本" width="90">
          <template #default="{ row }: { row: LlmCallLog }">v{{ row.templateVersion }}</template>
        </el-table-column>
        <el-table-column label="供应商 / 模型" min-width="180">
          <template #default="{ row }: { row: LlmCallLog }">{{ row.provider }} / {{ row.model }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="110">
          <template #default="{ row }: { row: LlmCallLog }">{{ row.latencyMs }} ms</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }: { row: LlmCallLog }">
            <el-tag :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorSummary" label="错误摘要" min-width="180" show-overflow-tooltip />
      </el-table>
    </section>
  </section>
</template>

<style scoped>
.prompt-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-heading,
.log-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.heading-actions { display: flex; gap: 8px; }

.page-heading h2 {
  margin: 0;
  color: #111827;
  font-size: 22px;
  line-height: 30px;
}

.page-heading p {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 14px;
}

.panel {
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.panel-title {
  margin-bottom: 14px;
  color: #111827;
  font-size: 16px;
  font-weight: 600;
}

.panel-title span {
  margin-left: 10px;
  color: #6b7280;
  font-size: 13px;
  font-weight: 400;
}

.version-detail {
  display: grid;
  gap: 14px;
  padding: 4px 18px 14px 48px;
}

.version-detail h4 {
  margin: 0 0 8px;
  color: #374151;
  font-size: 13px;
}

.version-detail pre,
.version-detail p {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  color: #4b5563;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.7;
}

.log-heading .panel-title {
  margin-bottom: 0;
}

.log-filters {
  display: flex;
  gap: 10px;
}

.log-filters .el-select {
  width: 180px;
}

@media (max-width: 760px) {
  .page-heading,
  .log-heading,
  .log-filters {
    flex-direction: column;
  }

  .log-filters,
  .log-filters .el-select {
    width: 100%;
  }
}
</style>
