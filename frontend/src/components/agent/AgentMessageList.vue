<script setup lang="ts">
import { nextTick, ref } from 'vue'

import AgentMessageContent from '@/components/agent/AgentMessageContent.vue'
import type { AgentAction, AgentUiMessage } from '@/types/agent'

defineProps<{
  messages: AgentUiMessage[]
  quickQuestions: string[]
  sending: boolean
  waitingText: string
  assistantLabel: string
}>()

const emit = defineEmits<{
  query: [value: string]
  action: [value: AgentAction]
  retry: [message: AgentUiMessage]
}>()

const scrollElement = ref<HTMLElement | null>(null)

const scrollToBottom = async () => {
  await nextTick()
  if (scrollElement.value) scrollElement.value.scrollTop = scrollElement.value.scrollHeight
}

defineExpose({ scrollToBottom })
</script>

<template>
  <div ref="scrollElement" class="message-list">
    <div v-if="messages.length === 0 && !sending" class="welcome">
      <div class="welcome-copy">
        <strong>直接说出你想了解的事情</strong>
        <span>助手会选择合适的只读能力，并只查询当前账号有权访问的数据。</span>
      </div>
      <div class="quick-grid">
        <button
          v-for="item in quickQuestions"
          :key="item"
          type="button"
          class="quick-question"
          @click="emit('query', item)"
        >
          {{ item }}
        </button>
      </div>
    </div>

    <article
      v-for="message in messages"
      :key="message.id"
      class="message-row"
      :class="message.role.toLowerCase()"
    >
      <div class="message-label">{{ message.role === 'USER' ? '我' : assistantLabel }}</div>
      <div class="message-body">
        <div v-if="message.role === 'USER'" class="user-text">{{ message.content }}</div>
        <AgentMessageContent
          v-else
          :content="message.content"
          :structured="message.structured"
          @query="emit('query', $event)"
          @action="emit('action', $event)"
        />
        <div v-if="message.failed" class="failure-state">
          <span>{{ message.failureReason || '查询失败，请重新尝试' }}</span>
          <el-button size="small" type="danger" plain @click="emit('retry', message)">
            重新尝试
          </el-button>
        </div>
      </div>
    </article>

    <article v-if="sending" class="message-row assistant waiting-row">
      <div class="message-label">{{ assistantLabel }}</div>
      <div class="message-body waiting-body">
        <span class="waiting-dot" />
        {{ waitingText }}
      </div>
    </article>
  </div>
</template>

<style scoped src="./AgentMessageList.css"></style>
