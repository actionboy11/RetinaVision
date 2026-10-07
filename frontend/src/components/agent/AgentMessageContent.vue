<script setup lang="ts">
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

defineProps<{
  content: string
  structured?: AgentChatResponse
}>()

const emit = defineEmits<{
  query: [value: string]
  action: [value: AgentAction]
}>()

const asPatientCaseList = (payload: AgentCaseListPayload | PatientAgentCaseListPayload) =>
  payload as PatientAgentCaseListPayload

const asDoctorCaseList = (payload: AgentCaseListPayload | PatientAgentCaseListPayload) =>
  payload as AgentCaseListPayload
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
      :payload="asPatientCaseList(structured.data.payload)"
      :pagination="structured.pagination"
      :actions="structured.actions"
      @action="emit('action', $event)"
    />
    <AgentCaseList
      v-else-if="structured.data?.type === 'CASE_LIST'"
      :payload="asDoctorCaseList(structured.data.payload)"
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

    <div
      v-if="!['TASK_LIST', 'TASK_DETAIL', 'CLINICAL_QUEUE', 'CASE_PROGRESS', 'SIGNED_REPORT'].includes(structured.data?.type || '')
        && !(structured.data?.type === 'CASE_LIST' && structured.skill?.code === 'MY_CASE_LIST')
        && structured.actions?.some((item) => !['NEXT_PAGE', 'PREVIOUS_PAGE'].includes(item.type))"
      class="message-actions"
    >
      <el-button
        v-for="action in structured.actions.filter((item) => !['NEXT_PAGE', 'PREVIOUS_PAGE'].includes(item.type))"
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

<style scoped>
.skill-label {
  display: inline-flex;
  margin-top: 10px;
  padding: 3px 7px;
  border: 1px solid #cad9df;
  border-radius: 4px;
  color: #476875;
  font-size: 11px;
}

.message-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
</style>
