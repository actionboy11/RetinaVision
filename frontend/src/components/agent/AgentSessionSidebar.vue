<script setup lang="ts">
import { ChatDotRound, Close } from '@element-plus/icons-vue'

import type { AgentChatSession } from '@/types/agent'
import { formatDateTime, formatEmpty } from '@/utils/format'

defineProps<{
  sessions: AgentChatSession[]
  activeSessionId: number | null
  compact?: boolean
  closable?: boolean
  disabled?: boolean
}>()

const emit = defineEmits<{
  select: [sessionId: number]
  create: []
  close: []
}>()
</script>

<template>
  <aside class="session-sidebar" :class="{ compact }">
    <div class="session-toolbar">
      <strong v-if="!compact">历史会话</strong>
      <el-button
        type="primary"
        :icon="ChatDotRound"
        :circle="compact"
        size="small"
        :disabled="disabled"
        @click="emit('create')"
      >
        <span v-if="!compact">新会话</span>
      </el-button>
      <el-button v-if="closable" :icon="Close" circle text aria-label="关闭会话列表" @click="emit('close')" />
    </div>

    <div v-if="sessions.length === 0" class="session-empty">
      {{ compact ? '无' : '暂无历史会话' }}
    </div>
    <div v-else class="session-list">
      <button
        v-for="session in sessions"
        :key="session.id"
        class="session-item"
        :class="{ active: session.id === activeSessionId }"
        type="button"
        :disabled="disabled"
        :title="formatEmpty(session.title)"
        @click="emit('select', Number(session.id))"
      >
        <span v-if="compact" class="session-initial">{{ formatEmpty(session.title).slice(0, 1) }}</span>
        <template v-else>
          <span>{{ formatEmpty(session.title) }}</span>
          <small>{{ formatDateTime(session.updatedAt) }}</small>
        </template>
      </button>
    </div>
  </aside>
</template>

<style scoped>
.session-sidebar {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  background: #f8fafb;
}

.session-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 52px;
  padding: 10px 12px;
  border-bottom: 1px solid #e3e8ee;
}

.session-toolbar strong {
  color: #263244;
  font-size: 13px;
}

.session-list {
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  display: flex;
  width: 100%;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
  margin-bottom: 4px;
  padding: 9px 10px;
  cursor: pointer;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #425166;
  text-align: left;
}

.session-item:hover {
  background: #eef3f5;
}

.session-item:disabled {
  cursor: not-allowed;
  opacity: .6;
}

.session-item.active {
  border-color: #bfd1d8;
  background: #e8f1f3;
  color: #155d70;
}

.session-item > span,
.session-item small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item small,
.session-empty {
  color: #8792a2;
  font-size: 11px;
}

.session-empty {
  padding: 20px 12px;
  text-align: center;
}

.session-sidebar.compact .session-toolbar {
  justify-content: center;
  padding: 10px 6px;
}

.session-sidebar.compact .session-list {
  padding: 8px 6px;
}

.session-sidebar.compact .session-item {
  align-items: center;
  padding: 8px 4px;
}

.session-initial {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border-radius: 5px;
  background: #e9eef1;
  font-weight: 700;
}
</style>
