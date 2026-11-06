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

<style scoped src="./AgentMetrics.css"></style>
