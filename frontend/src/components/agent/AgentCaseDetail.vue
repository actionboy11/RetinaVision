<script setup lang="ts">
import type { AgentCaseDetail } from '@/types/agent'
import { detailDefinitions, displayDetailValue } from '@/utils/agent-display'

defineProps<{
  detail?: AgentCaseDetail | null
}>()
</script>

<template>
  <div v-if="detail" class="detail-grid">
    <div v-for="item in detailDefinitions" :key="item.key" class="detail-item">
      <span>{{ item.label }}</span>
      <strong>{{ displayDetailValue(item.key, detail[item.key]) }}</strong>
    </div>
  </div>
  <div v-else class="compact-empty">暂无病例摘要</div>
</template>

<style scoped>
.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 24px;
  margin-top: 12px;
  border-top: 1px solid #dfe5ec;
}

.detail-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 42px;
  border-bottom: 1px solid #edf0f4;
}

.detail-item span {
  color: #718096;
  font-size: 13px;
}

.detail-item strong {
  color: #263244;
  font-size: 13px;
  text-align: right;
}

.compact-empty {
  padding: 18px 0;
  color: #8a95a5;
  text-align: center;
}

@media (max-width: 680px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
