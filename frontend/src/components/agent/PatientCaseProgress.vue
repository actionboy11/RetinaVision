<script setup lang="ts">
import type { AgentAction, PatientAgentProgressPayload } from '@/types/agent'
import {
  patientQualityStatusTagMap,
  patientQualityStatusTextMap,
} from '@/utils/agent-display'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{
  payload: PatientAgentProgressPayload
  actions?: AgentAction[]
}>()

const emit = defineEmits<{
  action: [value: AgentAction]
}>()

const findAction = (type: AgentAction['type']) => props.actions?.find((item) => item.type === type)
</script>

<template>
  <div class="patient-progress">
    <div v-if="payload.empty || !payload.progress" class="compact-empty">当前还没有检查记录</div>
    <template v-else>
      <div class="progress-heading">
        <div>
          <span>检查编号</span>
          <strong>{{ payload.progress.caseNo }}</strong>
        </div>
        <small>更新于 {{ formatDateTime(payload.progress.updatedAt) }}</small>
      </div>

      <ol class="stage-list">
        <li
          v-for="stage in payload.progress.stages"
          :key="stage.code"
          :class="stage.status.toLowerCase()"
        >
          <span class="stage-dot" />
          <strong>{{ stage.label }}</strong>
        </li>
      </ol>

      <div class="progress-note">
        <el-tag :type="patientQualityStatusTagMap[payload.progress.qualityStatus] || 'info'" size="small">
          {{ patientQualityStatusTextMap[payload.progress.qualityStatus] || '图像状态待确认' }}
        </el-tag>
        <p>{{ payload.progress.qualityMessage }}</p>
        <p>{{ payload.progress.message }}</p>
        <span>下一步由{{ payload.progress.nextHandler }}处理</span>
      </div>

      <div class="progress-actions">
        <el-button
          v-if="findAction('GO_TO_IMAGE_UPLOAD')"
          size="small"
          @click="emit('action', findAction('GO_TO_IMAGE_UPLOAD')!)"
        >重新上传图像</el-button>
        <el-button
          v-if="findAction('VIEW_CASE_PROGRESS')"
          size="small"
          type="primary"
          plain
          @click="emit('action', findAction('VIEW_CASE_PROGRESS')!)"
        >打开完整进度</el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.patient-progress { margin-top: 12px; padding: 14px; border: 1px solid #dfe5ec; border-radius: 7px; background: #fbfcfd; }
.progress-heading, .progress-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.progress-heading > div { display: flex; flex-direction: column; gap: 3px; }
.progress-heading span, .progress-heading small, .progress-note span { color: #7a8798; font-size: 12px; }
.stage-list { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin: 16px 0; padding: 0; list-style: none; }
.stage-list li { position: relative; display: flex; align-items: center; gap: 7px; color: #98a2b2; font-size: 12px; }
.stage-list li:not(:last-child)::after { position: absolute; top: 7px; right: 6px; left: calc(100% - 16px); height: 1px; background: #d8dee7; content: ''; }
.stage-list li.completed, .stage-list li.current { color: #176b87; }
.stage-dot { width: 10px; height: 10px; flex: 0 0 auto; border: 2px solid currentColor; border-radius: 50%; background: #fff; }
.completed .stage-dot, .current .stage-dot { background: currentColor; }
.progress-note { padding: 11px 12px; border-left: 3px solid #79a8b7; background: #f3f8fa; }
.progress-note p { margin: 7px 0; color: #465568; font-size: 13px; line-height: 1.55; }
.progress-actions { justify-content: flex-end; margin-top: 12px; }
.compact-empty { padding: 8px 0; color: #8a95a5; text-align: center; }
@media (max-width: 680px) { .stage-list { grid-template-columns: 1fr; gap: 10px; } .stage-list li::after { display: none; } }
</style>
