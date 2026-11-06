<script setup lang="ts">
import type { AgentAction, PatientAgentSignedReportPayload } from '@/types/agent'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{
  payload: PatientAgentSignedReportPayload
  actions?: AgentAction[]
}>()

const emit = defineEmits<{
  action: [value: AgentAction]
}>()

const reportAction = (caseId?: number) =>
  props.actions?.find(
    (item) =>
      item.type === 'VIEW_SIGNED_REPORT' && (caseId === undefined || item.targetId === caseId),
  )
const pageAction = (type: AgentAction['type']) => props.actions?.find((item) => item.type === type)
</script>

<template>
  <div class="patient-reports">
    <article v-if="payload.report" class="report-detail">
      <header>
        <div>
          <strong>{{ payload.report.caseNo }} · 正式报告 V{{ payload.report.version }}</strong>
          <span
            >{{ payload.report.signerName }} · {{ formatDateTime(payload.report.signedAt) }}</span
          >
        </div>
        <el-tag type="success" size="small">医生已签发</el-tag>
      </header>
      <dl>
        <div>
          <dt>检查所见</dt>
          <dd>{{ payload.report.findings || '报告未填写此项' }}</dd>
        </div>
        <div>
          <dt>报告结论</dt>
          <dd>{{ payload.report.conclusion || '报告未填写此项' }}</dd>
        </div>
        <div>
          <dt>医生建议</dt>
          <dd>{{ payload.report.recommendation || '报告未填写此项' }}</dd>
        </div>
      </dl>
      <section v-if="payload.explanationAvailable && payload.explanation" class="explanation">
        <strong>通俗解释</strong>
        <p>{{ payload.explanation }}</p>
      </section>
      <el-alert
        v-else-if="payload.explanationMessage"
        :title="payload.explanationMessage"
        type="info"
        :closable="false"
        show-icon
      />
      <div class="report-actions">
        <el-button
          v-if="reportAction(payload.report.caseId)"
          size="small"
          type="primary"
          plain
          @click="emit('action', reportAction(payload.report.caseId)!)"
          >打开正式报告</el-button
        >
      </div>
    </article>

    <template v-else>
      <div v-if="!payload.reports?.length" class="compact-empty">当前没有已签发的正式报告</div>
      <div v-else class="report-list">
        <article v-for="report in payload.reports" :key="`${report.resultId}-${report.version}`">
          <div>
            <strong>{{ report.caseNo }} · V{{ report.version }}</strong>
            <span>{{ report.signerName }} · {{ formatDateTime(report.signedAt) }}</span>
            <p>{{ report.conclusion || '报告未填写结论' }}</p>
          </div>
          <el-button
            v-if="reportAction(report.caseId)"
            size="small"
            type="primary"
            plain
            @click="emit('action', reportAction(report.caseId)!)"
            >查看报告</el-button
          >
        </article>
      </div>
      <div v-if="pageAction('PREVIOUS_PAGE') || pageAction('NEXT_PAGE')" class="pagination-actions">
        <el-button
          v-if="pageAction('PREVIOUS_PAGE')"
          size="small"
          @click="emit('action', pageAction('PREVIOUS_PAGE')!)"
          >上一页</el-button
        >
        <el-button
          v-if="pageAction('NEXT_PAGE')"
          size="small"
          @click="emit('action', pageAction('NEXT_PAGE')!)"
          >下一页</el-button
        >
      </div>
    </template>
  </div>
</template>

<style scoped src="./PatientSignedReport.css"></style>
