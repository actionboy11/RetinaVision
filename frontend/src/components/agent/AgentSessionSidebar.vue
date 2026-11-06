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
      <el-button
        v-if="closable"
        :icon="Close"
        circle
        text
        aria-label="关闭会话列表"
        @click="emit('close')"
      />
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
        <span v-if="compact" class="session-initial">{{
          formatEmpty(session.title).slice(0, 1)
        }}</span>
        <template v-else>
          <span>{{ formatEmpty(session.title) }}</span>
          <small>{{ formatDateTime(session.updatedAt) }}</small>
        </template>
      </button>
    </div>
  </aside>
</template>

<style scoped src="./AgentSessionSidebar.css"></style>
