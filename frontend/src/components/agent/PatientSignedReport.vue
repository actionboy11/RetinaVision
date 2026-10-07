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

const reportAction = (caseId?: number) => props.actions?.find(
  (item) => item.type === 'VIEW_SIGNED_REPORT' && (caseId === undefined || item.targetId === caseId),
)
const pageAction = (type: AgentAction['type']) => props.actions?.find((item) => item.type === type)
</script>

<template>
  <div class="patient-reports">
    <article v-if="payload.report" class="report-detail">
      <header>
        <div>
          <strong>{{ payload.report.caseNo }} · 正式报告 V{{ payload.report.version }}</strong>
          <span>{{ payload.report.signerName }} · {{ formatDateTime(payload.report.signedAt) }}</span>
        </div>
        <el-tag type="success" size="small">医生已签发</el-tag>
      </header>
      <dl>
        <div><dt>检查所见</dt><dd>{{ payload.report.findings || '报告未填写此项' }}</dd></div>
        <div><dt>报告结论</dt><dd>{{ payload.report.conclusion || '报告未填写此项' }}</dd></div>
        <div><dt>医生建议</dt><dd>{{ payload.report.recommendation || '报告未填写此项' }}</dd></div>
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
        >打开正式报告</el-button>
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
          >查看报告</el-button>
        </article>
      </div>
      <div v-if="pageAction('PREVIOUS_PAGE') || pageAction('NEXT_PAGE')" class="pagination-actions">
        <el-button v-if="pageAction('PREVIOUS_PAGE')" size="small" @click="emit('action', pageAction('PREVIOUS_PAGE')!)">上一页</el-button>
        <el-button v-if="pageAction('NEXT_PAGE')" size="small" @click="emit('action', pageAction('NEXT_PAGE')!)">下一页</el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.patient-reports { margin-top: 12px; }
.report-detail, .report-list article { padding: 14px; border: 1px solid #dfe5ec; border-radius: 7px; background: #fbfcfd; }
.report-detail header, .report-list article { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; }
.report-detail header > div, .report-list article > div { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.report-detail header span, .report-list article span { color: #7a8798; font-size: 12px; }
.report-detail dl { display: grid; gap: 10px; margin: 14px 0; }
.report-detail dl div { padding-top: 10px; border-top: 1px solid #e7ebf0; }
.report-detail dt { margin-bottom: 5px; color: #66758a; font-size: 12px; }
.report-detail dd { margin: 0; color: #334155; font-size: 13px; line-height: 1.65; white-space: pre-wrap; }
.explanation { padding: 12px; border-left: 3px solid #5f95a5; background: #eef6f8; color: #334155; }
.explanation p { margin: 6px 0 0; font-size: 13px; line-height: 1.65; white-space: pre-wrap; }
.report-actions, .pagination-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; }
.report-list { display: grid; gap: 9px; }
.report-list p { margin: 7px 0 0; color: #465568; font-size: 13px; line-height: 1.5; }
.compact-empty { padding: 18px 0; color: #8a95a5; text-align: center; }
@media (max-width: 560px) { .report-detail header, .report-list article { flex-direction: column; } }
</style>
