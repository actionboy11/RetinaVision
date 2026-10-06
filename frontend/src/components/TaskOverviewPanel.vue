<script setup lang="ts">
import { Picture } from '@element-plus/icons-vue'

import StatusTag from '@/components/StatusTag.vue'
import TaskTimeline from '@/components/TaskTimeline.vue'
import type { TaskDetail, TaskLogItem } from '@/types/task'
import { taskTypeTextMap } from '@/utils/enums'
import { formatDateTime, formatEmpty, formatRetryCount } from '@/utils/format'

const props = defineProps<{
  task: TaskDetail
  originalImageUrl: string
  logs: TaskLogItem[]
  logsLoading: boolean
  logsError: string
}>()

const emit = defineEmits<{
  preview: [url: string, title: string, filename: string]
}>()
</script>

<template>
  <div class="overview-panel">
    <el-alert
      v-if="props.task.status === 'FAILED'"
      :title="props.task.errorMessage || '任务执行失败，但后端未返回具体错误原因'"
      show-icon
      type="error"
      :closable="false"
    />

    <div class="overview-grid">
      <section class="content-section image-section">
        <div class="section-heading">
          <div>
            <h3>原始图像</h3>
            <p>{{ formatEmpty(props.task.originalFilename) }}</p>
          </div>
        </div>
        <div v-if="props.originalImageUrl" class="image-box">
          <el-image
            :src="props.originalImageUrl"
            fit="contain"
            class="source-image"
            @click="emit('preview', props.originalImageUrl, '原始图像', props.task.originalFilename)"
          >
            <template #error>
              <el-empty description="原始图像加载失败" :image-size="72" />
            </template>
          </el-image>
          <el-button
            :icon="Picture"
            plain
            @click="emit('preview', props.originalImageUrl, '原始图像', props.task.originalFilename)"
          >
            放大预览
          </el-button>
        </div>
        <el-empty v-else description="暂无原始图像" :image-size="72" />
      </section>

      <section class="content-section log-section">
        <div class="section-heading">
          <div>
            <h3>任务日志</h3>
            <p>记录任务状态流转与临床流程事件。</p>
          </div>
          <el-tag type="info">{{ props.logs.length }} 条</el-tag>
        </div>
        <el-alert
          v-if="props.logsError"
          :title="props.logsError"
          show-icon
          type="warning"
          :closable="false"
        />
        <div v-loading="props.logsLoading" class="timeline-scroll">
          <TaskTimeline :logs="props.logs" />
        </div>
      </section>
    </div>

    <el-collapse class="more-info-collapse">
      <el-collapse-item title="更多任务信息" name="details">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="任务编号">{{ formatEmpty(props.task.taskNo) }}</el-descriptions-item>
          <el-descriptions-item label="病例编号">{{ formatEmpty(props.task.caseNo) }}</el-descriptions-item>
          <el-descriptions-item label="任务类型">{{ taskTypeTextMap[props.task.taskType] }}</el-descriptions-item>
          <el-descriptions-item label="任务状态"><StatusTag :status="props.task.status" /></el-descriptions-item>
          <el-descriptions-item label="优先级">{{ formatEmpty(props.task.priority) }}</el-descriptions-item>
          <el-descriptions-item label="重试次数">{{ formatRetryCount(props.task.retryCount, props.task.maxRetryCount) }}</el-descriptions-item>
          <el-descriptions-item label="提交人">{{ formatEmpty(props.task.submittedByName) }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatDateTime(props.task.submittedAt) }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatDateTime(props.task.startedAt) }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ formatDateTime(props.task.finishedAt) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatDateTime(props.task.updatedAt) }}</el-descriptions-item>
          <el-descriptions-item label="原始文件名">{{ formatEmpty(props.task.originalFilename) }}</el-descriptions-item>
        </el-descriptions>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style scoped>
.overview-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.overview-grid {
  display: grid;
  grid-template-columns: minmax(320px, 0.9fr) minmax(420px, 1.1fr);
  gap: 16px;
}

.content-section {
  min-width: 0;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.section-heading h3,
.section-heading p {
  margin: 0;
}

.section-heading h3 {
  color: #111827;
  font-size: 15px;
}

.section-heading p {
  margin-top: 4px;
  color: #6b7280;
  font-size: 12px;
}

.image-box {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
}

.source-image {
  width: 100%;
  height: 300px;
  cursor: zoom-in;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f9fafb;
}

.timeline-scroll {
  min-height: 180px;
  max-height: 352px;
  overflow: auto;
  padding-right: 6px;
}

.more-info-collapse {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  padding: 0 16px;
}

@media (max-width: 1080px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }

  .source-image {
    height: 260px;
  }
}

@media (max-width: 640px) {
  .content-section {
    padding: 12px;
  }

  .source-image {
    height: 220px;
  }

  :deep(.el-descriptions__body .el-descriptions__table) {
    table-layout: auto;
  }
}
</style>
