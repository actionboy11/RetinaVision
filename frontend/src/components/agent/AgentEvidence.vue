<script setup lang="ts">
import { computed } from 'vue'

import type { AgentCitation, AgentToolCallSummary } from '@/types/agent'
import { toolNameMap } from '@/utils/agent-display'

const props = defineProps<{
  toolCalls?: AgentToolCallSummary[]
  citations?: AgentCitation[]
}>()

const total = computed(() => (props.toolCalls?.length || 0) + (props.citations?.length || 0))
const scoreText = (value: number) => Number.isFinite(value) ? value.toFixed(2) : '暂无数据'
</script>

<template>
  <el-collapse v-if="total" class="evidence">
    <el-collapse-item :title="`查询依据 · ${total} 项`" name="evidence">
      <div v-if="toolCalls?.length" class="tool-list">
        <div v-for="(tool, index) in toolCalls" :key="`${tool.toolName}-${index}`" class="tool-item">
          <el-tag :type="tool.success ? 'success' : 'danger'" size="small" effect="plain">
            {{ tool.success ? '完成' : '失败' }}
          </el-tag>
          <span>{{ toolNameMap[tool.toolName] || '执行只读查询' }}</span>
          <small>{{ tool.latencyMs }} ms</small>
        </div>
      </div>
      <div v-if="citations?.length" class="citation-list">
        <article v-for="citation in citations" :key="citation.chunkId" class="citation-item">
          <div>
            <strong>{{ citation.documentTitle }}</strong>
            <small>{{ citation.source }} · 相似度 {{ scoreText(citation.score) }}</small>
          </div>
          <p>{{ citation.snippet }}</p>
        </article>
      </div>
    </el-collapse-item>
  </el-collapse>
</template>

<style scoped>
.evidence {
  margin-top: 12px;
  border-top: 1px solid #e3e8ee;
  border-bottom: 0;
}

.evidence :deep(.el-collapse-item__header) {
  height: 38px;
  color: #607080;
  font-size: 12px;
}

.tool-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  padding: 6px 0;
  color: #425166;
  font-size: 12px;
}

.tool-item small,
.citation-item small {
  color: #8792a2;
}

.citation-item {
  padding: 8px 0;
  border-top: 1px solid #edf0f4;
}

.citation-item > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.citation-item strong {
  color: #334155;
  font-size: 12px;
}

.citation-item p {
  margin: 5px 0 0;
  color: #5f6d7c;
  font-size: 12px;
  line-height: 1.65;
}
</style>
