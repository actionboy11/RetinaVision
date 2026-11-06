import { ElMessage } from '@/plugins/element-plus-api'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, type Ref } from 'vue'
import { useRouter } from 'vue-router'

import { askAgent, createAgentSession, listAgentMessages, listAgentSessions } from '@/api/agent'
import { useAuthStore } from '@/stores/auth'
import type {
  AgentAction,
  AgentChatResponse,
  AgentChatSession,
  AgentUiMessage,
} from '@/types/agent'

export interface AgentMessageListHandle {
  scrollToBottom: () => void | Promise<void>
}

export interface UseClinicalAgentOptions {
  /** AgentMessageList 的组件模板 ref，必须由 SFC 声明 */
  messageListRef: Ref<AgentMessageListHandle | null>
}

const parseStructured = (value?: string | null): AgentChatResponse | undefined => {
  if (!value) return undefined
  try {
    return JSON.parse(value) as AgentChatResponse
  } catch {
    return undefined
  }
}

const inferWaitingText = (text: string) => {
  if (/为什么|是什么|知识|解释|医学|视网膜/.test(text)) return '正在检索医学知识并核对引用...'
  if (/比较|趋势|最近两次/.test(text)) return '正在整理历史结果并进行结构化比较...'
  if (/任务|待审核|待签发/.test(text)) return '正在查询任务和临床待办...'
  return '正在查询当前账号可访问的数据...'
}

/**
 * 临床智能助手页的逻辑层：会话切换（带竞态序号）、消息发送/重试、快捷问题、
 * 动作跳转、视口与侧栏状态。组件只保留模板与子组件编排。
 */
export const useClinicalAgent = (options: UseClinicalAgentOptions) => {
  const { messageListRef } = options

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
        '我有哪些检查？',
        '我最近一次检查到哪一步了？',
        '哪些图像需要重新上传？',
        '查看我的最新正式报告',
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

  const openMobileSidebar = () => {
    mobileSidebarVisible.value = true
  }
  const closeMobileSidebar = () => {
    mobileSidebarVisible.value = false
  }
  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value
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
    const latestStructured = [...messages.value]
      .reverse()
      .find((item) => item.structured)?.structured
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
    if (
      /^\/(cases\/\d+\/(progress(\?section=reports)?|images)|tasks\/\d+(\?tab=clinical&stage=review)?|doctor\/reviews)$/.test(
        path,
      )
    ) {
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

  return {
    sessions,
    messages,
    activeSessionId,
    question,
    loading,
    sending,
    sidebarCollapsed,
    mobileSidebarVisible,
    compactViewport,
    disclaimer,
    waitingText,
    assistantTitle,
    assistantDescription,
    quickQuestions,
    composerPlaceholder,
    openMobileSidebar,
    closeMobileSidebar,
    toggleSidebar,
    selectSession,
    newSession,
    send,
    retry,
    handleSuggestedQuery,
    runAction,
  }
}
