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

<style scoped>
.message-list {
  overflow-y: auto;
  flex: 1;
  min-height: 0;
  padding: 18px clamp(16px, 3vw, 34px) 24px;
  scroll-behavior: smooth;
}

.welcome {
  max-width: 760px;
  margin: 42px auto 0;
}

.welcome-copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 18px;
}

.welcome-copy strong {
  color: #172033;
  font-size: 20px;
}

.welcome-copy span {
  color: #718096;
  font-size: 13px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.quick-question {
  min-height: 46px;
  padding: 11px 13px;
  cursor: pointer;
  border: 1px solid #d9e1e8;
  border-radius: 6px;
  background: #fff;
  color: #334155;
  line-height: 1.45;
  text-align: left;
}

.quick-question:hover {
  border-color: #7aa6b7;
  background: #f6fafb;
  color: #155d70;
}

.message-row {
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr);
  gap: 12px;
  max-width: 980px;
  margin: 0 auto 18px;
}

.message-label {
  padding-top: 10px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  text-align: right;
}

.message-body {
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid #e1e6ec;
  border-radius: 7px;
  background: #fff;
}

.message-row.user .message-body {
  border-color: #cfdde5;
  background: #edf4f7;
}

.user-text {
  color: #263244;
  line-height: 1.7;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.waiting-body {
  color: #607080;
  font-size: 13px;
}

.waiting-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 8px;
  border-radius: 50%;
  background: #2f8295;
  animation: pulse 1s infinite ease-in-out;
}

.failure-state {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  color: #c2413b;
  font-size: 12px;
}

.failure-state span {
  min-width: 0;
  overflow-wrap: anywhere;
}

@keyframes pulse {
  0%, 100% { opacity: .35; }
  50% { opacity: 1; }
}

@media (max-width: 680px) {
  .message-list {
    padding: 14px 12px 18px;
  }

  .quick-grid {
    grid-template-columns: 1fr;
  }

  .message-row {
    grid-template-columns: 1fr;
    gap: 4px;
  }

  .message-label {
    padding: 0;
    text-align: left;
  }
}
</style>
