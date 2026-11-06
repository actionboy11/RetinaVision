<script setup lang="ts">
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'

import ClinicalWorkflowPanel from '@/components/ClinicalWorkflowPanel.vue'
import ImagePreview from '@/components/ImagePreview.vue'
import StatusTag from '@/components/StatusTag.vue'
import TaskOverviewPanel from '@/components/TaskOverviewPanel.vue'
import TaskResultPanel from '@/components/TaskResultPanel.vue'
import { useTaskDetail } from '@/composables/use-task-detail'
import { taskStatusTextMap, taskTypeTextMap } from '@/utils/enums'
import { formatDateTime, formatEmpty } from '@/utils/format'

const {
  loading,
  refreshing,
  resultLoading,
  logsLoading,
  detailError,
  resultError,
  logsError,
  taskDetail,
  analysisResult,
  comparisonCandidates,
  selectedBaselineResultId,
  comparisonLoading,
  comparisonError,
  comparison,
  taskLogs,
  previewVisible,
  previewUrl,
  previewTitle,
  previewFilename,
  originalImageUrl,
  maskImageUrl,
  activeTab,
  clinicalStage,
  isUnfinished,
  statusDescription,
  hasClinicalWorkflow,
  resultJsonText,
  handleTabChange,
  handleStageChange,
  openPreview,
  loadLogs,
  handleCompare,
  continueSegmentation,
  handleRefresh,
  goBack,
} = useTaskDetail()
</script>

<template>
  <section class="task-detail-page">
    <div class="page-heading">
      <div>
        <h2>任务详情</h2>
        <p>集中查看任务执行、分析结果与临床处理。</p>
      </div>
      <div class="heading-actions">
        <el-button :icon="ArrowLeft" @click="goBack">返回任务列表</el-button>
        <el-button type="primary" :icon="Refresh" :loading="refreshing" @click="handleRefresh"
          >刷新状态</el-button
        >
      </div>
    </div>

    <el-alert v-if="detailError" :title="detailError" show-icon type="error" />

    <div v-loading="loading" class="detail-content">
      <el-empty v-if="!loading && !taskDetail && !detailError" description="暂无任务详情" />
      <template v-if="taskDetail">
        <section class="status-summary">
          <div class="status-primary">
            <StatusTag :status="taskDetail.status" />
            <div>
              <div class="status-title">{{ taskStatusTextMap[taskDetail.status] }}</div>
              <div class="status-description">{{ statusDescription }}</div>
            </div>
          </div>
          <div class="summary-facts">
            <div>
              <span>任务编号</span><strong>{{ formatEmpty(taskDetail.taskNo) }}</strong>
            </div>
            <div>
              <span>病例编号</span><strong>{{ formatEmpty(taskDetail.caseNo) }}</strong>
            </div>
            <div>
              <span>任务类型</span><strong>{{ taskTypeTextMap[taskDetail.taskType] }}</strong>
            </div>
            <div>
              <span>完成时间</span><strong>{{ formatDateTime(taskDetail.finishedAt) }}</strong>
            </div>
          </div>
        </section>

        <el-tabs v-model="activeTab" class="detail-tabs" @tab-change="handleTabChange">
          <el-tab-pane label="任务概览" name="overview">
            <TaskOverviewPanel
              :task="taskDetail"
              :original-image-url="originalImageUrl"
              :logs="taskLogs"
              :logs-loading="logsLoading"
              :logs-error="logsError"
              @preview="openPreview"
            />
          </el-tab-pane>
          <el-tab-pane label="分析结果" name="result">
            <TaskResultPanel
              v-model:baseline-result-id="selectedBaselineResultId"
              :task="taskDetail"
              :result="analysisResult"
              :result-loading="resultLoading"
              :result-error="resultError"
              :unfinished="isUnfinished"
              :original-image-url="originalImageUrl"
              :mask-image-url="maskImageUrl"
              :comparison-candidates="comparisonCandidates"
              :comparison-loading="comparisonLoading"
              :comparison-error="comparisonError"
              :comparison="comparison"
              :result-json-text="resultJsonText"
              @preview="openPreview"
              @compare="handleCompare"
              @continue-segmentation="continueSegmentation"
            />
          </el-tab-pane>
          <el-tab-pane label="临床工作台" name="clinical" :disabled="!hasClinicalWorkflow">
            <ClinicalWorkflowPanel
              v-if="analysisResult?.resultType === 'VESSEL_SEGMENTATION'"
              :result-id="analysisResult.id"
              :stage="clinicalStage"
              @stage-change="handleStageChange"
              @workflow-updated="loadLogs(taskDetail.id)"
            />
            <el-empty v-else description="当前任务没有可用的临床工作流" :image-size="80" />
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>

    <ImagePreview
      v-model="previewVisible"
      :filename="previewFilename"
      :image-url="previewUrl"
      :title="previewTitle"
    />
  </section>
</template>

<style scoped src="./TaskDetail.css"></style>
