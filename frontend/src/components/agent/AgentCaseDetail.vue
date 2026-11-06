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

<style scoped src="./AgentCaseDetail.css"></style>
