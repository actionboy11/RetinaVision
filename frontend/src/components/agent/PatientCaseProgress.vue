<script setup lang="ts">
import type { AgentAction, PatientAgentProgressPayload } from '@/types/agent'
import { patientQualityStatusTagMap, patientQualityStatusTextMap } from '@/utils/agent-display'
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
        <el-tag
          :type="patientQualityStatusTagMap[payload.progress.qualityStatus] || 'info'"
          size="small"
        >
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
          >重新上传图像</el-button
        >
        <el-button
          v-if="findAction('VIEW_CASE_PROGRESS')"
          size="small"
          type="primary"
          plain
          @click="emit('action', findAction('VIEW_CASE_PROGRESS')!)"
          >打开完整进度</el-button
        >
      </div>
    </template>
  </div>
</template>

<style scoped src="./PatientCaseProgress.css"></style>
