<script setup lang="ts">
import { computed } from 'vue'

import type { ComponentStatus, SystemStatus } from '@/types/system'
import { formatDurationMs, formatEmpty, formatPercent } from '@/utils/format'

const props = defineProps<{
  status: SystemStatus | null
  loading: boolean
  error: string
}>()

const tagType = (status?: ComponentStatus) => {
  if (status === 'UP') return 'success'
  if (status === 'DEGRADED') return 'warning'
  return 'danger'
}

const aiDisplayStatus = computed(() => {
  if (!props.status?.ai.reachable) return '不可用'
  return props.status.ai.busy ? '推理中' : '就绪'
})
</script>

<template>
  <section v-loading="loading" class="system-status-panel">
    <div class="panel-heading">
      <div class="panel-title">系统运行状态</div>
      <el-tag
        v-if="status"
        :type="tagType(status.overallStatus)"
        effect="light"
      >
        {{ status.overallStatus }}
      </el-tag>
    </div>

    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    />

    <div v-else-if="status" class="status-grid">
      <article class="status-card">
        <div class="card-heading">
          <span>AI 服务</span>
          <el-tag :type="tagType(status.ai.status)" size="small">
            {{ aiDisplayStatus }}
          </el-tag>
        </div>
        <dl>
          <div><dt>模型</dt><dd>{{ formatEmpty(status.ai.modelName) }}</dd></div>
          <div><dt>版本</dt><dd>{{ formatEmpty(status.ai.modelVersion) }}</dd></div>
          <div><dt>设备</dt><dd>{{ formatEmpty(status.ai.device) }}</dd></div>
          <div><dt>最近推理</dt><dd>{{ formatDurationMs(status.ai.lastInferenceTimeMs) }}</dd></div>
        </dl>
        <el-alert
          v-if="status.ai.lastError"
          class="component-error"
          :title="status.ai.lastError"
          type="warning"
          :closable="false"
        />
      </article>

      <article class="status-card">
        <div class="card-heading">
          <span>RabbitMQ</span>
          <el-tag :type="tagType(status.queue.status)" size="small">
            {{ status.queue.status }}
          </el-tag>
        </div>
        <dl>
          <div><dt>等待消息</dt><dd>{{ formatEmpty(status.queue.messageReadyCount) }}</dd></div>
          <div><dt>处理中</dt><dd>{{ formatEmpty(status.queue.messageUnackedCount) }}</dd></div>
          <div><dt>消费者</dt><dd>{{ formatEmpty(status.queue.consumerCount) }}</dd></div>
          <div><dt>死信</dt><dd>{{ formatEmpty(status.queue.deadLetterCount) }}</dd></div>
        </dl>
        <el-alert
          v-if="status.queue.error"
          class="component-error"
          :title="status.queue.error"
          type="warning"
          :closable="false"
        />
      </article>

      <article class="status-card">
        <div class="card-heading"><span>长期任务指标</span></div>
        <dl>
          <div><dt>总任务数</dt><dd>{{ formatEmpty(status.tasks.totalTaskCount) }}</dd></div>
          <div><dt>成功率</dt><dd>{{ formatPercent(status.tasks.successRate) }}</dd></div>
          <div><dt>重试中</dt><dd>{{ formatEmpty(status.tasks.retryingCount) }}</dd></div>
          <div><dt>平均耗时</dt><dd>{{ formatDurationMs(status.tasks.averageProcessingTimeMs) }}</dd></div>
        </dl>
      </article>
    </div>

    <el-empty v-else description="暂无系统状态" :image-size="72" />
  </section>
</template>

<style scoped>
.system-status-panel {
  min-height: 180px;
  margin-bottom: 16px;
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.panel-heading,
.card-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-title {
  color: #111827;
  font-size: 16px;
  font-weight: 600;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 14px;
}

.status-card {
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f9fafb;
}

.card-heading {
  color: #111827;
  font-weight: 600;
}

dl {
  margin: 12px 0 0;
}

dl div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 5px 0;
}

dt {
  color: #6b7280;
}

dd {
  margin: 0;
  color: #111827;
  text-align: right;
}

.component-error {
  margin-top: 10px;
}

@media (max-width: 1000px) {
  .status-grid {
    grid-template-columns: 1fr;
  }
}
</style>
