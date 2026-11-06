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

<style scoped src="./AgentMarkdown.css"></style>
