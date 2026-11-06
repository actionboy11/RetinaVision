<script setup lang="ts">
import type { AgentComparison, AgentComparisonSnapshot } from '@/types/agent'
import {
  displayDelta,
  displayEyeSide,
  displayModel,
  displayQualityStatus,
  displayRatio,
  displayReportStatus,
  displayReviewStatus,
  displayScore,
  displayTaskType,
} from '@/utils/agent-display'
import { formatDateTime } from '@/utils/format'

defineProps<{
  comparison?: AgentComparison | null
}>()

const dateText = (snapshot?: AgentComparisonSnapshot | null) => {
  const value = snapshot?.finishedAt || snapshot?.resultCreatedAt
  return value ? formatDateTime(value) : '暂无数据'
}
</script>

<template>
  <div v-if="comparison" class="comparison">
    <div class="comparison-meta">
      <span>{{ displayEyeSide(comparison.eyeSide) }}</span>
      <span>{{ displayTaskType(comparison.taskType) }}</span>
    </div>
    <div class="snapshot-grid">
      <section>
        <h4>基线结果</h4>
        <dl>
          <dt>结果时间</dt>
          <dd>{{ dateText(comparison.baseline) }}</dd>
          <dt>图像质量</dt>
          <dd>
            {{ displayQualityStatus(comparison.baseline?.qualityStatus) }} /
            {{ displayScore(comparison.baseline?.qualityScore) }}
          </dd>
          <dt>血管面积占比</dt>
          <dd>{{ displayRatio(comparison.baseline?.vesselAreaRatio) }}</dd>
          <dt>模型版本</dt>
          <dd>{{ displayModel(comparison.baseline) }}</dd>
          <dt>审核状态</dt>
          <dd>{{ displayReviewStatus(comparison.baseline?.reviewStatus) }}</dd>
          <dt>报告状态</dt>
          <dd>{{ displayReportStatus(comparison.baseline?.reportStatus) }}</dd>
        </dl>
      </section>
      <section>
        <h4>目标结果</h4>
        <dl>
          <dt>结果时间</dt>
          <dd>{{ dateText(comparison.target) }}</dd>
          <dt>图像质量</dt>
          <dd>
            {{ displayQualityStatus(comparison.target?.qualityStatus) }} /
            {{ displayScore(comparison.target?.qualityScore) }}
          </dd>
          <dt>血管面积占比</dt>
          <dd>{{ displayRatio(comparison.target?.vesselAreaRatio) }}</dd>
          <dt>模型版本</dt>
          <dd>{{ displayModel(comparison.target) }}</dd>
          <dt>审核状态</dt>
          <dd>{{ displayReviewStatus(comparison.target?.reviewStatus) }}</dd>
          <dt>报告状态</dt>
          <dd>{{ displayReportStatus(comparison.target?.reportStatus) }}</dd>
        </dl>
      </section>
    </div>
    <div class="delta-strip">
      <span
        >质量评分变化 <strong>{{ displayDelta(comparison.qualityScoreDelta) }}</strong></span
      >
      <span
        >血管面积变化
        <strong>{{ displayDelta(comparison.vesselAreaRatioDelta, true) }}</strong></span
      >
      <span
        >模型版本 <strong>{{ comparison.modelChanged ? '发生变化' : '保持一致' }}</strong></span
      >
    </div>
    <ul v-if="comparison.notes?.length" class="notes">
      <li v-for="(note, index) in comparison.notes" :key="`${index}-${note}`">{{ note }}</li>
    </ul>
  </div>
  <div v-else class="compact-empty">暂无可展示的对比结果</div>
</template>

<style scoped src="./AgentComparison.css"></style>
