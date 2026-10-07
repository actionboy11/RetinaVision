<script setup lang="ts">
import type { AgentMetricsPayload } from '@/types/agent'
import { metricDefinitions } from '@/utils/agent-display'

const props = defineProps<{
  metrics: AgentMetricsPayload
}>()

const emit = defineEmits<{
  query: [value: string]
}>()
</script>

<template>
  <div class="metric-grid">
    <button
      v-for="item in metricDefinitions"
      :key="item.key"
      class="metric-item"
      type="button"
      @click="emit('query', item.query)"
    >
      <span>{{ item.label }}</span>
      <strong>{{ props.metrics[item.key] ?? 0 }}</strong>
    </button>
  </div>
</template>

<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(110px, 1fr));
  gap: 8px;
  margin-top: 12px;
}

.metric-item {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
  padding: 10px 12px;
  cursor: pointer;
  border: 1px solid #dfe5ec;
  border-radius: 6px;
  background: #fff;
  text-align: left;
}

.metric-item:hover {
  border-color: #7aa6b7;
  background: #f6fafb;
}

.metric-item span {
  color: #687587;
  font-size: 12px;
}

.metric-item strong {
  color: #172033;
  font-size: 21px;
  line-height: 1.25;
}

@media (max-width: 680px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
