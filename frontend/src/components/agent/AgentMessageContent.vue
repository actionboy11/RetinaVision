<script setup lang="ts">
import { computed } from 'vue'

import AgentCaseDetail from '@/components/agent/AgentCaseDetail.vue'
import AgentCaseList from '@/components/agent/AgentCaseList.vue'
import AgentComparison from '@/components/agent/AgentComparison.vue'
import AgentEvidence from '@/components/agent/AgentEvidence.vue'
import AgentMarkdown from '@/components/agent/AgentMarkdown.vue'
import AgentMetrics from '@/components/agent/AgentMetrics.vue'
import PatientCaseList from '@/components/agent/PatientCaseList.vue'
import PatientCaseProgress from '@/components/agent/PatientCaseProgress.vue'
import PatientSignedReport from '@/components/agent/PatientSignedReport.vue'
import AgentTaskList from '@/components/agent/AgentTaskList.vue'
import AgentTaskDetail from '@/components/agent/AgentTaskDetail.vue'
import AgentClinicalQueue from '@/components/agent/AgentClinicalQueue.vue'
import type {
  AgentAction,
  AgentCaseListPayload,
  AgentChatResponse,
  PatientAgentCaseListPayload,
} from '@/types/agent'
import { skillNameMap } from '@/utils/agent-display'

const props = defineProps<{
  content: string
  structured?: AgentChatResponse
}>()

const emit = defineEmits<{
  query: [value: string]
  action: [value: AgentAction]
}>()

// 已提供独立操作区的结构化卡片，不再重复渲染底部操作条
const SELF_ACTION_TYPES = [
  'TASK_LIST',
  'TASK_DETAIL',
  'CLINICAL_QUEUE',
  'CASE_PROGRESS',
  'SIGNED_REPORT',
]
const PAGING_ACTION_TYPES = ['NEXT_PAGE', 'PREVIOUS_PAGE']

const visibleActions = computed(() =>
  (props.structured?.actions ?? []).filter((item) => !PAGING_ACTION_TYPES.includes(item.type)),
)

const showActionBar = computed(() => {
  const data = props.structured?.data
  if (!data || SELF_ACTION_TYPES.includes(data.type)) return false
  if (data.type === 'CASE_LIST' && props.structured?.skill?.code === 'MY_CASE_LIST') return false
  return visibleActions.value.length > 0
})

/** 按字段判别两种 CASE_LIST payload，不再用 as 强转把医生数据当作患者数据渲染。 */
const isPatientCaseList = (
  payload: AgentCaseListPayload | PatientAgentCaseListPayload,
): payload is PatientAgentCaseListPayload => {
  const cases = payload.cases
  return (
    Array.isArray(cases) &&
    cases.length > 0 &&
    cases.every((item) => 'qualityMessage' in item && 'signedReportAvailable' in item)
  )
}

const toPatientCaseList = (
  payload: AgentCaseListPayload | PatientAgentCaseListPayload,
): PatientAgentCaseListPayload => (isPatientCaseList(payload) ? payload : { cases: [] })

const toDoctorCaseList = (
  payload: AgentCaseListPayload | PatientAgentCaseListPayload,
): AgentCaseListPayload => (isPatientCaseList(payload) ? { cases: [] } : payload)
</script>

<template>
  <AgentMarkdown :content="content" />

  <template v-if="structured">
    <div v-if="structured.skill" class="skill-label">
      {{ skillNameMap[structured.skill.code] || structured.skill.name }}
    </div>

    <AgentMetrics
      v-if="structured.data?.type === 'METRICS'"
      :metrics="structured.data.payload"
      @query="emit('query', $event)"
    />
    <PatientCaseList
      v-else-if="structured.data?.type === 'CASE_LIST' && structured.skill?.code === 'MY_CASE_LIST'"
      :payload="toPatientCaseList(structured.data.payload)"
      :pagination="structured.pagination"
      :actions="structured.actions"
      @action="emit('action', $event)"
    />
    <AgentCaseList
      v-else-if="structured.data?.type === 'CASE_LIST'"
      :payload="toDoctorCaseList(structured.data.payload)"
      :pagination="structured.pagination"
      :actions="structured.actions"
      @query="emit('query', $event)"
      @action="emit('action', $event)"
    />
    <AgentCaseDetail
      v-else-if="structured.data?.type === 'CASE_DETAIL'"
      :detail="structured.data.payload.caseDetail"
    />
    <AgentTaskList
      v-else-if="structured.data?.type === 'TASK_LIST'"
      :payload="structured.data.payload"
      :pagination="structured.pagination"
      :actions="structured.actions"
      @action="emit('action', $event)"
    />
    <AgentTaskDetail
      v-else-if="structured.data?.type === 'TASK_DETAIL'"
      :detail="structured.data.payload.taskDetail"
      :actions="structured.actions"
      @action="emit('action', $event)"
    />
    <AgentClinicalQueue
      v-else-if="structured.data?.type === 'CLINICAL_QUEUE'"
      :payload="structured.data.payload"
      :pagination="structured.pagination"
      :actions="structured.actions"
      @action="emit('action', $event)"
    />
    <AgentComparison
      v-else-if="structured.data?.type === 'COMPARISON'"
      :comparison="structured.data.payload.comparison"
    />
    <PatientCaseProgress
      v-else-if="structured.data?.type === 'CASE_PROGRESS'"
      :payload="structured.data.payload"
      :actions="structured.actions"
      @action="emit('action', $event)"
    />
    <PatientSignedReport
      v-else-if="structured.data?.type === 'SIGNED_REPORT'"
      :payload="structured.data.payload"
      :actions="structured.actions"
      @action="emit('action', $event)"
    />

    <div v-if="showActionBar" class="message-actions">
      <el-button
        v-for="action in visibleActions"
        :key="`${action.type}-${action.targetId || ''}`"
        size="small"
        @click="emit('action', action)"
      >
        {{ action.label }}
      </el-button>
    </div>

    <AgentEvidence :tool-calls="structured.toolCalls" :citations="structured.citations" />
  </template>
</template>

<style scoped src="./AgentMessageContent.css"></style>
