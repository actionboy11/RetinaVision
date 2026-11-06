<script setup lang="ts">
import { ChatDotRound, Expand, Fold, Menu, MoreFilled } from '@element-plus/icons-vue'
import { ref } from 'vue'

import AgentComposer from '@/components/agent/AgentComposer.vue'
import AgentMessageList from '@/components/agent/AgentMessageList.vue'
import AgentSessionSidebar from '@/components/agent/AgentSessionSidebar.vue'
import { useClinicalAgent, type AgentMessageListHandle } from '@/composables/use-clinical-agent'

// 组件模板 ref 必须由 SFC 自身声明，编译器才能静态识别并回填组件实例
const messageListRef = ref<AgentMessageListHandle | null>(null)

const {
  activeSessionId,
  assistantDescription,
  assistantTitle,
  compactViewport,
  composerPlaceholder,
  disclaimer,
  handleSuggestedQuery,
  loading,
  messages,
  mobileSidebarVisible,
  newSession,
  question,
  quickQuestions,
  retry,
  runAction,
  selectSession,
  send,
  sending,
  sessions,
  sidebarCollapsed,
  waitingText,
  openMobileSidebar,
  closeMobileSidebar,
  toggleSidebar,
} = useClinicalAgent({ messageListRef })
</script>

<template>
  <section v-loading="loading" class="agent-page">
    <header class="page-heading">
      <div class="heading-copy">
        <el-button
          v-if="compactViewport"
          :icon="Menu"
          circle
          plain
          aria-label="打开会话列表"
          @click="openMobileSidebar"
        />
        <div>
          <h2>{{ assistantTitle }}</h2>
          <p>{{ assistantDescription }}</p>
        </div>
      </div>
      <el-button type="primary" :icon="ChatDotRound" :disabled="sending" @click="newSession"
        >新会话</el-button
      >
    </header>

    <div class="agent-workspace" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
      <AgentSessionSidebar
        v-if="!compactViewport"
        :sessions="sessions"
        :active-session-id="activeSessionId"
        :compact="sidebarCollapsed"
        :disabled="sending"
        @select="selectSession"
        @create="newSession"
      />

      <main class="chat-panel">
        <button
          v-if="!compactViewport"
          class="sidebar-toggle"
          type="button"
          :title="sidebarCollapsed ? '展开会话列表' : '折叠会话列表'"
          @click="toggleSidebar"
        >
          <el-icon><Expand v-if="sidebarCollapsed" /><Fold v-else /></el-icon>
        </button>

        <AgentMessageList
          ref="messageListRef"
          :messages="messages"
          :quick-questions="quickQuestions"
          :sending="sending"
          :waiting-text="waitingText"
          :assistant-label="assistantTitle"
          @query="handleSuggestedQuery"
          @action="runAction"
          @retry="retry"
        />

        <footer class="chat-footer">
          <AgentComposer
            v-model="question"
            :placeholder="composerPlaceholder"
            :sending="sending"
            @submit="send"
          />
          <div class="disclaimer">
            <el-icon><MoreFilled /></el-icon>
            <span>{{ disclaimer }}</span>
          </div>
        </footer>
      </main>
    </div>

    <el-drawer
      v-model="mobileSidebarVisible"
      direction="ltr"
      size="min(86vw, 320px)"
      :with-header="false"
      class="session-drawer"
    >
      <AgentSessionSidebar
        :sessions="sessions"
        :active-session-id="activeSessionId"
        closable
        :disabled="sending"
        @select="selectSession"
        @create="newSession"
        @close="closeMobileSidebar"
      />
    </el-drawer>
  </section>
</template>

<style scoped src="./ClinicalAgent.css"></style>
