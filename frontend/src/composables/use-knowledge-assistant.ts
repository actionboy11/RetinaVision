import { ElMessage, ElMessageBox } from '@/plugins/element-plus-api'
import { computed, nextTick, onMounted, reactive, ref, type Ref } from 'vue'

import {
  askKnowledge,
  createKnowledgeDocument,
  createKnowledgeSession,
  deleteKnowledgeDocument,
  listKnowledgeDocuments,
  listKnowledgeMessages,
  listKnowledgeSessions,
  reindexKnowledgeDocument,
  updateKnowledgeDocumentStatus,
} from '@/api/knowledge'
import { useAuthStore } from '@/stores/auth'
import type {
  KnowledgeAudience,
  KnowledgeChatMessage,
  KnowledgeChatSession,
  KnowledgeCitation,
  KnowledgeDocument,
} from '@/types/knowledge'
import { ignoreHandledError } from '@/utils/async-action'

type UiMessage = {
  role: 'USER' | 'ASSISTANT'
  content: string
  citations: KnowledgeCitation[]
}

export interface UseKnowledgeAssistantOptions {
  /** 消息滚动容器的模板 ref，必须由 SFC 声明 */
  messagesRef: Ref<HTMLElement | null>
}

const categoryOptions = [
  { label: '医学基础', value: 'MEDICAL_BASE' },
  { label: 'AI 结果解释', value: 'AI_RESULT_EXPLANATION' },
  { label: '临床工作流', value: 'WORKFLOW' },
  { label: '常见问题', value: 'FAQ' },
  { label: '安全边界', value: 'SAFETY' },
]

const audienceOptions = [
  { label: '所有角色', value: 'PUBLIC' },
  { label: '患者', value: 'PATIENT' },
  { label: '临床医生', value: 'CLINICAL' },
  { label: '研究员', value: 'RESEARCH' },
  { label: '管理员', value: 'ADMIN' },
]

/**
 * 医疗知识助手页的逻辑层：会话/消息、知识文档管理与引用展示状态。
 * 组件只保留模板与格式化映射。
 */
export const useKnowledgeAssistant = (options: UseKnowledgeAssistantOptions) => {
  const { messagesRef } = options

  const auth = useAuthStore()
  const admin = computed(() => auth.user?.roleCode === 'ADMIN')

  const sessions = ref<KnowledgeChatSession[]>([])
  const documents = ref<KnowledgeDocument[]>([])
  const messages = ref<UiMessage[]>([])
  const activeSessionId = ref<number | null>(null)
  const loading = ref(false)
  const sending = ref(false)
  const documentsLoading = ref(false)
  const question = ref('')
  const latestDisclaimer = ref(
    '医疗知识助手仅供资料检索和理解参考，不构成诊断、治疗建议或报告签发依据。',
  )

  const documentForm = reactive({
    title: '',
    source: '',
    category: 'MEDICAL_BASE',
    audience: 'PUBLIC' as KnowledgeAudience,
    content: '',
  })

  const currentCitations = computed(() => {
    const lastAssistant = [...messages.value].reverse().find((item) => item.role === 'ASSISTANT')
    return lastAssistant?.citations ?? []
  })

  const parseCitations = (value: string | null): KnowledgeCitation[] => {
    if (!value) return []
    try {
      const parsed = JSON.parse(value) as KnowledgeCitation[]
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }

  const categoryLabel = (value: string) =>
    categoryOptions.find((item) => item.value === value)?.label ?? value
  const audienceLabel = (value: string) =>
    audienceOptions.find((item) => item.value === value)?.label ?? value

  const statusType = (status: string) => {
    if (status === 'ACTIVE') return 'success'
    if (status === 'FAILED') return 'danger'
    if (status === 'DISABLED') return 'info'
    return 'warning'
  }

  const toUiMessage = (message: KnowledgeChatMessage): UiMessage => ({
    role: message.role,
    content: message.content,
    citations: parseCitations(message.citationsJson),
  })

  const scrollMessagesToBottom = async () => {
    await nextTick()
    const element = messagesRef.value
    if (element) {
      element.scrollTop = element.scrollHeight
    }
  }

  const loadSessions = async () => {
    try {
      sessions.value = await listKnowledgeSessions()
    } catch (error) {
      ignoreHandledError(error)
    }
  }

  const loadDocuments = async () => {
    if (!admin.value) return
    documentsLoading.value = true
    try {
      documents.value = await listKnowledgeDocuments()
    } catch (error) {
      ignoreHandledError(error)
    } finally {
      documentsLoading.value = false
    }
  }

  const selectSession = async (sessionId: number) => {
    activeSessionId.value = sessionId
    try {
      const data = await listKnowledgeMessages(sessionId)
      messages.value = data.map(toUiMessage)
      await scrollMessagesToBottom()
    } catch (error) {
      ignoreHandledError(error)
    }
  }

  const newSession = async () => {
    try {
      const session = await createKnowledgeSession()
      activeSessionId.value = session.id
      messages.value = []
      await loadSessions()
    } catch (error) {
      ignoreHandledError(error)
    }
  }

  const send = async () => {
    const text = question.value.trim()
    if (!text) {
      ElMessage.warning('请输入要咨询的问题')
      return
    }

    sending.value = true
    messages.value.push({ role: 'USER', content: text, citations: [] })
    messages.value.push({
      role: 'ASSISTANT',
      content: '正在检索知识库并生成回答，请稍候...',
      citations: [],
    })
    question.value = ''
    await scrollMessagesToBottom()
    try {
      const response = await askKnowledge({ sessionId: activeSessionId.value, question: text })
      activeSessionId.value = response.sessionId
      latestDisclaimer.value = response.disclaimer
      messages.value[messages.value.length - 1] = {
        role: 'ASSISTANT',
        content: response.answer,
        citations: response.citations,
      }
      await loadSessions()
      await scrollMessagesToBottom()
    } catch (error) {
      // 保留会话中的提问，把占位消息替换为失败提示；不再向上抛出，
      // 否则模板事件会产生未处理的 Promise rejection。
      messages.value[messages.value.length - 1] = {
        role: 'ASSISTANT',
        content: '回答生成失败，请稍后重试。',
        citations: [],
      }
      ignoreHandledError(error)
      await scrollMessagesToBottom()
    } finally {
      sending.value = false
    }
  }

  const submitDocument = async () => {
    if (!documentForm.title.trim() || !documentForm.content.trim()) {
      ElMessage.warning('请填写知识标题和内容')
      return
    }

    documentsLoading.value = true
    try {
      await createKnowledgeDocument({
        title: documentForm.title,
        source: documentForm.source,
        category: documentForm.category,
        audience: documentForm.audience,
        content: documentForm.content,
      })
      documentForm.title = ''
      documentForm.source = ''
      documentForm.category = 'MEDICAL_BASE'
      documentForm.audience = 'PUBLIC'
      documentForm.content = ''
      await loadDocuments()
      ElMessage.success('知识文档已入库并索引')
    } catch (error) {
      ignoreHandledError(error)
    } finally {
      documentsLoading.value = false
    }
  }

  const reindex = async (id: number) => {
    documentsLoading.value = true
    try {
      await reindexKnowledgeDocument(id)
      await loadDocuments()
      ElMessage.success('已重建索引')
    } catch (error) {
      ignoreHandledError(error)
    } finally {
      documentsLoading.value = false
    }
  }

  const toggleStatus = async (document: KnowledgeDocument) => {
    const nextStatus = document.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
    documentsLoading.value = true
    try {
      await updateKnowledgeDocumentStatus(document.id, nextStatus)
      await loadDocuments()
      ElMessage.success(nextStatus === 'ACTIVE' ? '文档已启用' : '文档已停用')
    } catch (error) {
      ignoreHandledError(error)
    } finally {
      documentsLoading.value = false
    }
  }

  const removeDocument = async (document: KnowledgeDocument) => {
    try {
      await ElMessageBox.confirm(
        `确认删除知识文档「${document.title}」？删除后会同步移除向量索引。`,
        '删除知识文档',
        { type: 'warning' },
      )
    } catch {
      return // 用户取消，或二次确认框关闭
    }
    documentsLoading.value = true
    try {
      await deleteKnowledgeDocument(document.id)
      await loadDocuments()
      ElMessage.success('知识文档已删除')
    } catch (error) {
      ignoreHandledError(error)
    } finally {
      documentsLoading.value = false
    }
  }

  onMounted(async () => {
    loading.value = true
    try {
      await Promise.all([loadSessions(), loadDocuments()])
      if (sessions.value[0]) {
        await selectSession(sessions.value[0].id)
      }
    } catch (error) {
      ignoreHandledError(error)
    } finally {
      loading.value = false
    }
  })

  return {
    admin,
    sessions,
    documents,
    messages,
    activeSessionId,
    loading,
    sending,
    documentsLoading,
    question,
    latestDisclaimer,
    documentForm,
    currentCitations,
    categoryOptions,
    audienceOptions,
    categoryLabel,
    audienceLabel,
    statusType,
    selectSession,
    newSession,
    send,
    submitDocument,
    reindex,
    toggleStatus,
    removeDocument,
  }
}
