<script setup lang="ts">
import { Picture } from '@element-plus/icons-vue'
import { computed } from 'vue'

import type { CaseAnalysisTimelineItem } from '@/types/case'
import type {
  AnalysisResult,
  AnalysisResultComparison,
  ImageQualityResult,
  TaskDetail,
  VesselSegmentationResult,
} from '@/types/task'
import { taskTypeTextMap } from '@/utils/enums'
import {
  EMPTY_TEXT,
  formatDateTime,
  formatDurationMs,
  formatEmpty,
  formatPercent,
  formatScore,
} from '@/utils/format'

const props = defineProps<{
  task: TaskDetail
  result: AnalysisResult | null
  resultLoading: boolean
  resultError: string
  unfinished: boolean
  originalImageUrl: string
  maskImageUrl: string
  comparisonCandidates: CaseAnalysisTimelineItem[]
  comparisonLoading: boolean
  comparisonError: string
  comparison: AnalysisResultComparison | null
  resultJsonText: string
}>()

const baselineResultId = defineModel<number | null>('baselineResultId', { default: null })

const emit = defineEmits<{
  preview: [url: string, title: string, filename?: string]
  compare: []
  continueSegmentation: []
}>()

const resultJson = computed<Record<string, unknown>>(() => {
  const json = props.result?.resultJson
  return json && typeof json === 'object' ? (json as Record<string, unknown>) : {}
})

const getNumber = (key: string) => {
  const value = resultJson.value[key]
  return typeof value === 'number' ? value : null
}

const getString = (key: string) => {
  const value = resultJson.value[key]
  return typeof value === 'string' ? value : null
}

const vesselResult = computed<VesselSegmentationResult>(() => ({
  vesselAreaRatio: getNumber('vesselAreaRatio') ?? 0,
  imageQualityScore: getNumber('imageQualityScore'),
  processingTimeMs: getNumber('processingTimeMs') ?? 0,
  modelVersion: getString('modelVersion') || '',
  conclusion: getString('conclusion') || '',
}))

const qualityResult = computed<ImageQualityResult>(() => ({
  grade: (getString('grade') as ImageQualityResult['grade']) || 'FAIL',
  score: getNumber('score') ?? 0,
  metrics:
    typeof resultJson.value.metrics === 'object' && resultJson.value.metrics
      ? (resultJson.value.metrics as Record<string, number>)
      : {},
  reasons: Array.isArray(resultJson.value.reasons) ? (resultJson.value.reasons as string[]) : [],
}))

const qualityDisplay = computed(() => {
  const summary = props.result?.qualitySummary ?? props.task.qualitySummary ?? null
  const status = summary?.status || ''
  const scoreText = typeof summary?.score === 'number' ? formatScore(summary.score) : ''
  const parts = [status, scoreText].filter(Boolean)
  return parts.length > 0 ? parts.join(' / ') : EMPTY_TEXT
})

const canContinueSegmentation = computed(() => props.result?.resultType === 'IMAGE_QUALITY_CHECK')
const hasComparisonCandidates = computed(() => props.comparisonCandidates.length > 0)

const formatNullableNumber = (value: number | null) =>
  typeof value === 'number' ? formatScore(value) : '-'

const formatSignedNumber = (value: number | null) => {
  if (typeof value !== 'number') return '-'
  return `${value >= 0 ? '+' : ''}${formatScore(value)}`
}

const formatNullablePercent = (value: number | null) =>
  typeof value === 'number' ? formatPercent(value) : '-'

const formatSignedPercent = (value: number | null) => {
  if (typeof value !== 'number') return '-'
  return `${value >= 0 ? '+' : ''}${formatPercent(value)}`
}

const comparisonMetricRows = computed(() => {
  if (!props.comparison) return []
  return [
    {
      label: '图像质量评分',
      baseline: formatNullableNumber(props.comparison.baseline.qualityScore),
      target: formatNullableNumber(props.comparison.target.qualityScore),
      delta: formatSignedNumber(props.comparison.qualityScoreDelta),
    },
    {
      label: '血管面积比例',
      baseline: formatNullablePercent(props.comparison.baseline.vesselAreaRatio),
      target: formatNullablePercent(props.comparison.target.vesselAreaRatio),
      delta: formatSignedPercent(props.comparison.vesselAreaRatioDelta),
    },
    {
      label: '模型版本',
      baseline: props.comparison.baseline.modelVersion || '-',
      target: props.comparison.target.modelVersion || '-',
      delta: props.comparison.modelChanged ? '有变化' : '无变化',
    },
    {
      label: '审核状态',
      baseline: props.comparison.baseline.reviewStatus || '-',
      target: props.comparison.target.reviewStatus || '-',
      delta: props.comparison.reviewStatusChanged ? '有变化' : '无变化',
    },
    {
      label: '报告状态',
      baseline: props.comparison.baseline.reportStatus || '-',
      target: props.comparison.target.reportStatus || '-',
      delta: props.comparison.reportStatusChanged ? '有变化' : '无变化',
    },
  ]
})
</script>

<template>
  <div class="result-panel" v-loading="props.resultLoading">
    <el-empty v-if="props.unfinished" description="任务尚未完成，暂无分析结果" :image-size="80" />
    <el-empty
      v-else-if="props.task.status === 'FAILED'"
      description="任务执行失败，暂无分析结果"
      :image-size="80"
    />
    <el-empty
      v-else-if="props.task.status === 'CANCELED'"
      description="任务已取消，暂无分析结果"
      :image-size="80"
    />

    <template v-else>
      <el-alert
        v-if="props.resultError"
        :title="props.resultError"
        show-icon
        type="warning"
        :closable="false"
      />
      <el-empty
        v-if="!props.result && !props.resultLoading"
        description="任务已完成，但暂未找到分析结果"
        :image-size="80"
      />

      <template v-if="props.result">
        <div class="meta-strip">
          <div>
            <span>模型</span><strong>{{ formatEmpty(props.result.modelName) }}</strong>
          </div>
          <div>
            <span>版本</span><strong>{{ formatEmpty(props.result.modelVersion) }}</strong>
          </div>
          <div>
            <span>类型</span><strong>{{ taskTypeTextMap[props.result.resultType] }}</strong>
          </div>
          <div>
            <span>处理耗时</span
            ><strong>{{ formatDurationMs(props.result.processingTimeMs) }}</strong>
          </div>
          <div>
            <span>生成时间</span><strong>{{ formatDateTime(props.result.createdAt) }}</strong>
          </div>
        </div>

        <div
          class="visual-grid"
          :class="{ 'quality-only': props.result.resultType === 'IMAGE_QUALITY_CHECK' }"
        >
          <section class="visual-section">
            <div class="section-title">原始图像</div>
            <div class="image-frame">
              <el-image
                v-if="props.originalImageUrl"
                :src="props.originalImageUrl"
                fit="contain"
                class="result-image"
                @click="
                  emit('preview', props.originalImageUrl, '原始图像', props.task.originalFilename)
                "
              />
              <el-empty v-else description="暂无原始图像" :image-size="64" />
            </div>
            <el-button
              v-if="props.originalImageUrl"
              :icon="Picture"
              plain
              @click="
                emit('preview', props.originalImageUrl, '原始图像', props.task.originalFilename)
              "
              >放大预览</el-button
            >
          </section>

          <section v-if="props.result.resultType === 'VESSEL_SEGMENTATION'" class="visual-section">
            <div class="section-title">分割结果</div>
            <div class="image-frame">
              <el-image
                v-if="props.maskImageUrl"
                :src="props.maskImageUrl"
                fit="contain"
                class="result-image"
                @click="emit('preview', props.maskImageUrl, '分割结果图')"
              />
              <el-empty v-else description="暂无分割结果图" :image-size="64" />
            </div>
            <el-button
              v-if="props.maskImageUrl"
              :icon="Picture"
              plain
              @click="emit('preview', props.maskImageUrl, '分割结果图')"
              >放大预览</el-button
            >
          </section>

          <section class="metrics-section">
            <div class="section-title">结果指标</div>
            <div v-if="props.result.resultType === 'VESSEL_SEGMENTATION'" class="metric-grid">
              <div class="metric-item">
                <span>血管面积占比</span
                ><strong>{{ formatPercent(vesselResult.vesselAreaRatio) }}</strong>
              </div>
              <div class="metric-item">
                <span>图像质量</span><strong>{{ qualityDisplay }}</strong>
              </div>
              <div class="metric-item">
                <span>处理耗时</span
                ><strong>{{ formatDurationMs(vesselResult.processingTimeMs) }}</strong>
              </div>
              <div class="metric-item">
                <span>模型版本</span><strong>{{ formatEmpty(vesselResult.modelVersion) }}</strong>
              </div>
              <div class="metric-item metric-wide">
                <span>分析结论</span><strong>{{ formatEmpty(vesselResult.conclusion) }}</strong>
              </div>
            </div>
            <div v-else class="metric-grid">
              <div class="metric-item">
                <span>综合质量评分</span><strong>{{ formatScore(qualityResult.score) }}</strong>
              </div>
              <div class="metric-item">
                <span>模糊评分</span
                ><strong>{{ formatScore(qualityResult.metrics.sharpness ?? 0) }}</strong>
              </div>
              <div class="metric-item">
                <span>亮度评分</span
                ><strong>{{ formatScore(qualityResult.metrics.exposure ?? 0) }}</strong>
              </div>
              <div class="metric-item">
                <span>对比度评分</span
                ><strong>{{ formatScore(qualityResult.metrics.contrast ?? 0) }}</strong>
              </div>
              <div class="metric-item metric-wide">
                <span>质量结论</span
                ><strong>{{ qualityResult.grade }} {{ qualityResult.reasons.join(', ') }}</strong>
              </div>
            </div>
            <div class="result-actions">
              <el-button
                v-if="canContinueSegmentation"
                type="primary"
                @click="emit('continueSegmentation')"
              >
                继续血管分割
              </el-button>
              <span v-if="props.result.resultType === 'VESSEL_SEGMENTATION'" class="muted-text">
                正式报告请在“临床工作台”完成审核后签发。
              </span>
            </div>
          </section>
        </div>

        <el-collapse class="secondary-collapse">
          <el-collapse-item
            v-if="props.result.resultType === 'VESSEL_SEGMENTATION'"
            name="comparison"
          >
            <template #title>
              <span>历史结果对比</span>
              <el-tag class="title-tag" size="small" type="info">{{
                props.comparisonCandidates.length
              }}</el-tag>
            </template>
            <div class="comparison-heading">
              <p>选择同病例历史血管分割结果作为基线，仅比较结构化指标。</p>
              <div class="comparison-actions">
                <el-select
                  v-model="baselineResultId"
                  :disabled="!hasComparisonCandidates"
                  placeholder="选择历史结果"
                  clearable
                >
                  <el-option
                    v-for="item in props.comparisonCandidates"
                    :key="item.resultId || item.taskId"
                    :label="`${formatDateTime(item.finishedAt || item.resultCreatedAt)} · ${typeof item.vesselAreaRatio === 'number' ? formatPercent(item.vesselAreaRatio) : '-'}`"
                    :value="item.resultId"
                  />
                </el-select>
                <el-button
                  type="primary"
                  :loading="props.comparisonLoading"
                  :disabled="!hasComparisonCandidates"
                  @click="emit('compare')"
                >
                  开始对比
                </el-button>
              </div>
            </div>
            <el-alert
              v-if="props.comparisonError"
              :title="props.comparisonError"
              show-icon
              type="warning"
              :closable="false"
            />
            <el-empty
              v-if="!hasComparisonCandidates"
              description="暂无可对比的历史结果"
              :image-size="64"
            />
            <template v-else-if="props.comparison">
              <el-table :data="comparisonMetricRows" border size="small">
                <el-table-column prop="label" label="指标" min-width="130" />
                <el-table-column prop="baseline" label="基线结果" min-width="150" />
                <el-table-column prop="target" label="当前结果" min-width="150" />
                <el-table-column prop="delta" label="变化" min-width="120" />
              </el-table>
              <el-alert
                v-if="props.comparison.notes.length"
                :title="props.comparison.notes.join('；')"
                show-icon
                type="info"
                :closable="false"
              />
            </template>
            <el-alert
              title="结果对比受图像质量、拍摄条件和模型版本影响，只作为医生复核线索。"
              show-icon
              type="info"
              :closable="false"
            />
          </el-collapse-item>
          <el-collapse-item title="结果 JSON 原文" name="json">
            <pre class="json-block">{{ props.resultJsonText || EMPTY_TEXT }}</pre>
          </el-collapse-item>
        </el-collapse>
      </template>
    </template>
  </div>
</template>

<style scoped src="./TaskResultPanel.css"></style>
