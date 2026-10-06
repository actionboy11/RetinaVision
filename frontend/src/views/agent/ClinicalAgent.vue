<script setup lang="ts">
import { ChatDotRound, Expand, Fold, Menu, MoreFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import {
  askAgent,
  createAgentSession,
  listAgentMessages,
  listAgentSessions,
} from '@/api/agent'
import AgentComposer from '@/components/agent/AgentComposer.vue'
import AgentMessageList from '@/components/agent/AgentMessageList.vue'
import AgentSessionSidebar from '@/components/agent/AgentSessionSidebar.vue'
import { useAuthStore } from '@/stores/auth'
import type {
  AgentAction,
  AgentChatResponse,
  AgentChatSession,
  AgentUiMessage,
} from '@/types/agent'

const authStore = useAuthStore()
const router = useRouter()

const sessions = ref<AgentChatSession[]>([])
const messages = ref<AgentUiMessage[]>([])
const activeSessionId = ref<number | null>(null)
const question = ref('')
const loading = ref(false)
const sending = ref(false)
const sidebarCollapsed = ref(false)
const mobileSidebarVisible = ref(false)
const compactViewport = ref(false)
const messageListRef = ref<InstanceType<typeof AgentMessageList> | null>(null)
const disclaimer = ref('智能助手仅提供只读辅助查询，不构成诊断、治疗建议或报告签发依据。')
const waitingText = ref('正在查询可访问的数据，请稍候...')
let sessionLoadSequence = 0

const isDoctor = computed(() => authStore.user?.roleCode === 'DOCTOR')
const isPatient = computed(() => authStore.user?.roleCode === 'USER')
const assistantTitle = computed(() => {
  if (isPatient.value) return '检查助手'
  if (isDoctor.value) return '临床工作助手'
  return '平台运维助手'
})
const assistantDescription = computed(() => {
  if (isPatient.value) return '查询本人检查进度、理解已签发报告或了解通俗医学知识。'
  if (isDoctor.value) return '用自然语言查询本人负责的患者、病例、任务待办和随访结果。'
  return '查询平台质控、风险任务或检索知识库内容。'
})
const quickQuestions = computed(() => {
  if (isPatient.value) {
    return [
      '列出我的检查申请和当前状态',
      '查看我最近一次检查的处理进度',
      '解释我最近一份已签发报告中的医学术语',
      '为什么眼底图像需要先做图像质量检测？',
    ]
  }
  if (isDoctor.value) {
    return [
      '我有多少名患者？',
      '哪些病例还没有进行分割？',
      '查询我负责的病例',
      '查询我的分析任务',
      '哪些血管分割任务失败了？',
      '今天有哪些待审核结果？',
      '哪些结果已经审核但还没有签发？',
      '最近一个月有哪些右眼分割失败的病例？',
    ]
  }
  return [
    '汇总当前 AI 质控总体情况',
    '列出当前需要重点处理的风险任务',
    '图像质量失败通常会影响哪些分析指标？',
  ]
})
const composerPlaceholder = computed(() => {
  if (isPatient.value) return '例如：查看我最近一次检查的进度'
  if (isDoctor.value) return '例如：哪些病例还没有分割？'
  return '输入质控、风险任务或知识库问题'
})

const scrollToBottom = async () => {
  await nextTick()
  await messageListRef.value?.scrollToBottom()
}

const updateViewport = () => {
  compactViewport.value = window.innerWidth <= 900
  if (!compactViewport.value) mobileSidebarVisible.value = false
}

const parseStructured = (value?: string | null): AgentChatResponse | undefined => {
  if (!value) return undefined
  try {
    return JSON.parse(value) as AgentChatResponse
  } catch {
    return undefined
  }
}

const loadSessions = async () => {
  sessions.value = await listAgentSessions()
}

const selectSession = async (sessionId: number) => {
  if (sending.value) return
  const sequence = ++sessionLoadSequence
  activeSessionId.value = sessionId
  const stored = await listAgentMessages(sessionId)
  if (sequence !== sessionLoadSequence) return
  messages.value = stored.map((message) => ({
    ...message,
    structured: parseStructured(message.structuredContentJson),
  }))
  const latestStructured = [...messages.value].reverse().find((item) => item.structured)?.structured
  if (latestStructured?.disclaimer) disclaimer.value = latestStructured.disclaimer
  mobileSidebarVisible.value = false
  await scrollToBottom()
}

const newSession = async () => {
  if (sending.value) return
  sessionLoadSequence += 1
  const session = await createAgentSession()
  activeSessionId.value = Number(session.id)
  messages.value = []
  question.value = ''
  mobileSidebarVisible.value = false
  await loadSessions()
}

const inferWaitingText = (text: string) => {
  if (/为什么|是什么|知识|解释|医学|视网膜/.test(text)) return '正在检索医学知识并核对引用...'
  if (/比较|趋势|最近两次/.test(text)) return '正在整理历史结果并进行结构化比较...'
  if (/任务|待审核|待签发/.test(text)) return '正在查询任务和临床待办...'
  return '正在查询当前账号可访问的数据...'
}

const sendText = async (text: string, existingMessage?: AgentUiMessage) => {
  const normalized = text.trim()
  if (!normalized || sending.value) return

  let userMessage = existingMessage
  if (!userMessage) {
    userMessage = {
      id: -Date.now(),
      sessionId: activeSessionId.value ?? 0,
      role: 'USER',
      content: normalized,
      createdAt: new Date().toISOString(),
    }
    messages.value.push(userMessage)
  } else {
    userMessage.failed = false
    userMessage.failureReason = undefined
  }

  question.value = ''
  waitingText.value = inferWaitingText(normalized)
  sending.value = true
  await scrollToBottom()

  try {
    const response = await askAgent({ sessionId: activeSessionId.value, question: normalized })
    activeSessionId.value = Number(response.sessionId)
    messages.value.push({
      id: -(Date.now() + 1),
      sessionId: response.sessionId,
      role: 'ASSISTANT',
      content: response.answer,
      structured: response,
      createdAt: new Date().toISOString(),
    })
    disclaimer.value = response.disclaimer || disclaimer.value
    await loadSessions()
  } catch (error) {
    userMessage.failed = true
    userMessage.failureReason = error instanceof Error ? error.message : '查询失败，请重新尝试'
    question.value = normalized
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

const send = async () => {
  if (!question.value.trim()) {
    ElMessage.warning('请输入需要查询的问题')
    return
  }
  await sendText(question.value)
}

const retry = async (message: AgentUiMessage) => {
  await sendText(message.content, message)
}

const handleSuggestedQuery = async (value: string) => {
  await sendText(value)
}

const runAction = async (action: AgentAction) => {
  if (action.type === 'NEXT_PAGE' || action.type === 'PREVIOUS_PAGE') {
    await sendText(action.type === 'NEXT_PAGE' ? '继续' : '上一页')
    return
  }

  const path = action.targetPath || ''
  if (/^\/(cases\/\d+\/images|tasks\/\d+(\?tab=clinical&stage=review)?|doctor\/reviews)$/.test(path)) {
    await router.push(path)
    return
  }
  ElMessage.warning('该页面操作不可用')
}

onMounted(async () => {
  updateViewport()
  window.addEventListener('resize', updateViewport)
  loading.value = true
  try {
    await loadSessions()
    if (sessions.value[0]) await selectSession(Number(sessions.value[0].id))
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateViewport)
})
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
          @click="mobileSidebarVisible = true"
        />
        <div>
          <h2>{{ assistantTitle }}</h2>
          <p>{{ assistantDescription }}</p>
        </div>
      </div>
      <el-button type="primary" :icon="ChatDotRound" :disabled="sending" @click="newSession">新会话</el-button>
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
          @click="sidebarCollapsed = !sidebarCollapsed"
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
        @close="mobileSidebarVisible = false"
      />
    </el-drawer>
  </section>
</template>

<style scoped>
.agent-page {
  display: flex;
  height: calc(100vh - 112px);
  min-height: 580px;
  flex-direction: column;
  gap: 12px;
}

.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex: 0 0 auto;
  padding: 2px 0;
}

.heading-copy {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.page-heading h2 {
  margin: 0;
  color: #172033;
  font-size: 22px;
  letter-spacing: 0;
}

.page-heading p {
  margin: 4px 0 0;
  color: #718096;
  font-size: 13px;
}

.agent-workspace {
  display: grid;
  grid-template-columns: 232px minmax(0, 1fr);
  overflow: hidden;
  flex: 1;
  min-height: 0;
  border: 1px solid #dfe5ec;
  border-radius: 8px;
  background: #fff;
}

.agent-workspace.sidebar-collapsed {
  grid-template-columns: 54px minmax(0, 1fr);
}

.chat-panel {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  border-left: 1px solid #e3e8ee;
  background: #fbfcfd;
}

.sidebar-toggle {
  position: absolute;
  z-index: 2;
  top: 10px;
  left: 10px;
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  cursor: pointer;
  border: 1px solid #dce3e9;
  border-radius: 5px;
  background: #fff;
  color: #607080;
}

.chat-footer {
  flex: 0 0 auto;
  padding: 12px clamp(16px, 3vw, 34px) 10px;
  border-top: 1px solid #e3e8ee;
  background: #fff;
}

.disclaimer {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  color: #8792a2;
  font-size: 11px;
  line-height: 1.5;
}

:global(.session-drawer .el-drawer__body) {
  padding: 0;
}

:global(.session-drawer .session-sidebar) {
  height: 100%;
}

@media (max-width: 900px) {
  .agent-page {
    height: calc(100vh - 96px);
    min-height: 520px;
  }

  .agent-workspace,
  .agent-workspace.sidebar-collapsed {
    grid-template-columns: 1fr;
  }

  .chat-panel {
    border-left: 0;
  }
}

@media (max-width: 620px) {
  .agent-page {
    height: calc(100vh - 82px);
    gap: 8px;
  }

  .page-heading h2 {
    font-size: 18px;
  }

  .page-heading p {
    display: none;
  }

  .page-heading > .el-button {
    padding-inline: 10px;
  }

  .chat-footer {
    padding: 10px 12px 8px;
  }
}
</style>
