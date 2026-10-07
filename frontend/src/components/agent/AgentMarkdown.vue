<script setup lang="ts">
import { computed } from 'vue'

import { renderSafeMarkdown } from '@/utils/safe-markdown'

const props = defineProps<{
  content: string
}>()

const renderedContent = computed(() => renderSafeMarkdown(props.content))
</script>

<template>
  <div class="agent-markdown" v-html="renderedContent" />
</template>

<style scoped>
.agent-markdown {
  color: #263244;
  font-size: 14px;
  line-height: 1.75;
  overflow-wrap: anywhere;
}

.agent-markdown :deep(p) {
  margin: 0 0 8px;
}

.agent-markdown :deep(p:last-child) {
  margin-bottom: 0;
}

.agent-markdown :deep(h1),
.agent-markdown :deep(h2),
.agent-markdown :deep(h3),
.agent-markdown :deep(h4) {
  margin: 14px 0 6px;
  color: #172033;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.5;
}

.agent-markdown :deep(ul),
.agent-markdown :deep(ol) {
  margin: 6px 0 10px;
  padding-left: 22px;
}

.agent-markdown :deep(li + li) {
  margin-top: 3px;
}

.agent-markdown :deep(code) {
  padding: 2px 5px;
  border-radius: 4px;
  background: #eef1f5;
  color: #263244;
  font-family: Consolas, "Courier New", monospace;
  font-size: 13px;
}

.agent-markdown :deep(pre) {
  overflow: auto;
  margin: 10px 0;
  padding: 12px;
  border: 1px solid #dfe5ec;
  border-radius: 6px;
  background: #f7f8fa;
}

.agent-markdown :deep(pre code) {
  padding: 0;
  background: transparent;
}

.agent-markdown :deep(blockquote) {
  margin: 10px 0;
  padding: 4px 12px;
  border-left: 3px solid #8aa6b8;
  color: #536173;
}

.agent-markdown :deep(a) {
  color: #176b87;
  text-decoration: underline;
  text-underline-offset: 2px;
}
</style>
