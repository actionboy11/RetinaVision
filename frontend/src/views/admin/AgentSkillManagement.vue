<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import {
  activateAgentSkillVersion,
  evaluateAgentSkillVersion,
  listAgentSkillExecutions,
  listAgentSkills,
  listAgentSkillVersions,
} from '@/api/agent-skill'
import type { AgentSkillExecutionItem, AgentSkillItem, AgentSkillVersionItem } from '@/types/agent-skill'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const router = useRouter()
const actionLoading = ref(false)
const skills = ref<AgentSkillItem[]>([])
const versions = ref<AgentSkillVersionItem[]>([])
const selected = ref<AgentSkillItem | null>(null)
const visible = ref(false)
const executions = ref<AgentSkillExecutionItem[]>([])

const load = async () => {
  loading.value = true
  try {
    const [skillItems, executionItems] = await Promise.all([listAgentSkills(), listAgentSkillExecutions()])
    skills.value = skillItems
    executions.value = executionItems
  } finally { loading.value = false }
}

const openVersions = async (skill: AgentSkillItem) => {
  selected.value = skill
  versions.value = await listAgentSkillVersions(skill.skillCode)
  visible.value = true
}

const evaluate = async (version: AgentSkillVersionItem) => {
  if (!selected.value) return
  actionLoading.value = true
  try {
    const result = await evaluateAgentSkillVersion(selected.value.skillCode, Number(version.id))
    visible.value = false
    ElMessage.success('评测已进入队列')
    await router.push({ path: '/agent-evaluations', query: { runId: String(result.id) } })
  } finally { actionLoading.value = false }
}

const activate = async (version: AgentSkillVersionItem) => {
  if (!selected.value) return
  await ElMessageBox.confirm(`确认启用 ${selected.value.name} v${version.version}？`, '切换 Skill 版本', { type: 'warning' })
  actionLoading.value = true
  try {
    await activateAgentSkillVersion(selected.value.skillCode, Number(version.id))
    ElMessage.success('Skill 版本已启用')
    await load()
    await openVersions(skills.value.find((item) => item.skillCode === selected.value?.skillCode) || selected.value)
  } finally { actionLoading.value = false }
}

onMounted(load)
</script>

<template>
  <section class="skill-page" v-loading="loading">
    <header class="page-heading">
      <div><h2>Agent Skill 管理</h2><p>查看智能助手业务技能、评测状态和当前启用版本。</p></div>
      <div class="heading-actions">
        <el-button @click="router.push('/agent-evaluations')">Agent 评测</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </header>
    <div class="table-panel">
      <el-table :data="skills" stripe>
        <el-table-column prop="name" label="Skill" min-width="170" />
        <el-table-column prop="skillCode" label="编码" min-width="230" />
        <el-table-column prop="description" label="用途" min-width="300" />
        <el-table-column label="状态" width="100"><template #default="scope"><el-tag size="small" type="success">{{ scope.row.status }}</el-tag></template></el-table-column>
        <el-table-column prop="activeVersionId" label="启用版本 ID" width="120" />
        <el-table-column label="更新时间" width="170"><template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="100"><template #default="scope"><el-button link type="primary" @click="openVersions(scope.row)">查看版本</el-button></template></el-table-column>
      </el-table>
    </div>

    <div class="table-panel">
      <div class="section-heading"><h3>最近执行</h3><span>最多显示最近 100 次，不包含完整参数或患者临床内容。</span></div>
      <el-table :data="executions" stripe empty-text="暂无 Skill 执行记录">
        <el-table-column prop="skillCode" label="Skill" min-width="220" />
        <el-table-column prop="skillVersion" label="版本" width="80" />
        <el-table-column label="结果" width="90"><template #default="scope"><el-tag :type="scope.row.success ? 'success' : 'danger'" size="small">{{ scope.row.success ? '成功' : '失败' }}</el-tag></template></el-table-column>
        <el-table-column label="置信度" width="100"><template #default="scope">{{ scope.row.confidence == null ? '-' : scope.row.confidence.toFixed(2) }}</template></el-table-column>
        <el-table-column prop="latencyMs" label="耗时(ms)" width="110" />
        <el-table-column prop="errorType" label="错误类型" min-width="140"><template #default="scope">{{ scope.row.errorType || '-' }}</template></el-table-column>
        <el-table-column label="执行时间" width="170"><template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template></el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="visible" :title="selected ? `${selected.name} · 版本` : 'Skill 版本'" width="820px">
      <el-table :data="versions" v-loading="actionLoading">
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column prop="workflowPrompt" label="执行说明" min-width="260" show-overflow-tooltip />
        <el-table-column prop="answerStyle" label="回答风格" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.active ? 'success' : 'info'" size="small">{{ scope.row.active ? '已启用' : '未启用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="170"><template #default="scope"><el-button link @click="evaluate(scope.row)">运行评测</el-button><el-button link type="primary" :disabled="scope.row.active" @click="activate(scope.row)">启用</el-button></template></el-table-column>
      </el-table>
    </el-dialog>
  </section>
</template>

<style scoped>
.skill-page { display: flex; flex-direction: column; gap: 16px; }
.page-heading, .table-panel { border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; }
.page-heading { display: flex; justify-content: space-between; gap: 16px; padding: 18px; }
.page-heading h2 { margin: 0; font-size: 22px; }
.page-heading p { margin: 6px 0 0; color: #6b7280; }
.table-panel { padding: 16px; }
.section-heading { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 12px; }
.section-heading h3 { margin: 0; font-size: 16px; }
.section-heading span { color: #6b7280; font-size: 13px; }
.heading-actions { display: flex; gap: 8px; }
</style>
