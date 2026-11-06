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
      <el-tag v-if="status" :type="tagType(status.overallStatus)" effect="light">
        {{ status.overallStatus }}
      </el-tag>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />

    <div v-else-if="status" class="status-grid">
      <article class="status-card">
        <div class="card-heading">
          <span>AI 服务</span>
          <el-tag :type="tagType(status.ai.status)" size="small">
            {{ aiDisplayStatus }}
          </el-tag>
        </div>
        <dl>
          <div>
            <dt>模型</dt>
            <dd>{{ formatEmpty(status.ai.modelName) }}</dd>
          </div>
          <div>
            <dt>版本</dt>
            <dd>{{ formatEmpty(status.ai.modelVersion) }}</dd>
          </div>
          <div>
            <dt>设备</dt>
            <dd>{{ formatEmpty(status.ai.device) }}</dd>
          </div>
          <div>
            <dt>最近推理</dt>
            <dd>{{ formatDurationMs(status.ai.lastInferenceTimeMs) }}</dd>
          </div>
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
          <div>
            <dt>等待消息</dt>
            <dd>{{ formatEmpty(status.queue.messageReadyCount) }}</dd>
          </div>
          <div>
            <dt>处理中</dt>
            <dd>{{ formatEmpty(status.queue.messageUnackedCount) }}</dd>
          </div>
          <div>
            <dt>消费者</dt>
            <dd>{{ formatEmpty(status.queue.consumerCount) }}</dd>
          </div>
          <div>
            <dt>死信</dt>
            <dd>{{ formatEmpty(status.queue.deadLetterCount) }}</dd>
          </div>
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
          <div>
            <dt>总任务数</dt>
            <dd>{{ formatEmpty(status.tasks.totalTaskCount) }}</dd>
          </div>
          <div>
            <dt>成功率</dt>
            <dd>{{ formatPercent(status.tasks.successRate) }}</dd>
          </div>
          <div>
            <dt>重试中</dt>
            <dd>{{ formatEmpty(status.tasks.retryingCount) }}</dd>
          </div>
          <div>
            <dt>平均耗时</dt>
            <dd>{{ formatDurationMs(status.tasks.averageProcessingTimeMs) }}</dd>
          </div>
        </dl>
      </article>
    </div>

    <el-empty v-else description="暂无系统状态" :image-size="72" />
  </section>
</template>

<style scoped src="./SystemStatusPanel.css"></style>
