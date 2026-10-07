<script setup lang="ts">
import {
  ChatDotRound,
  Collection,
  Delete,
  DocumentAdd,
  Refresh,
  Search,
  Switch,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'

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
  KnowledgeChatMessage,
  KnowledgeChatSession,
  KnowledgeCitation,
  KnowledgeDocument,
} from '@/types/knowledge'
import { formatDateTime, formatEmpty } from '@/utils/format'

type UiMessage = {
  role: 'USER' | 'ASSISTANT'
  content: string
  citations: KnowledgeCitation[]
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

const auth = useAuthStore()
const admin = computed(() => auth.user?.roleCode === 'ADMIN')

const sessions = ref<KnowledgeChatSession[]>([])
const documents = ref<KnowledgeDocument[]>([])
const messages = ref<UiMessage[]>([])
const messagesRef = ref<HTMLElement | null>(null)
const activeSessionId = ref<number | null>(null)
const loading = ref(false)
const sending = ref(false)
const documentsLoading = ref(false)
const question = ref('')
const latestDisclaimer = ref('医疗知识助手仅供资料检索和理解参考，不构成诊断、治疗建议或报告签发依据。')

const documentForm = reactive({
  title: '',
  source: '',
  category: 'MEDICAL_BASE',
  audience: 'PUBLIC' as 'PUBLIC' | 'PATIENT' | 'CLINICAL' | 'RESEARCH' | 'ADMIN',
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
  sessions.value = await listKnowledgeSessions()
}

const loadDocuments = async () => {
  if (!admin.value) return
  documentsLoading.value = true
  try {
    documents.value = await listKnowledgeDocuments()
  } finally {
    documentsLoading.value = false
  }
}

const selectSession = async (sessionId: number) => {
  activeSessionId.value = sessionId
  const data = await listKnowledgeMessages(sessionId)
  messages.value = data.map(toUiMessage)
  await scrollMessagesToBottom()
}

const newSession = async () => {
  const session = await createKnowledgeSession()
  activeSessionId.value = session.id
  messages.value = []
  await loadSessions()
}

const send = async () => {
  const text = question.value.trim()
  if (!text) {
    ElMessage.warning('请输入要咨询的问题')
    return
  }

  sending.value = true
  messages.value.push({ role: 'USER', content: text, citations: [] })
  messages.value.push({ role: 'ASSISTANT', content: '正在检索知识库并生成回答，请稍候...', citations: [] })
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
    messages.value.pop()
    await scrollMessagesToBottom()
    throw error
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
  } finally {
    documentsLoading.value = false
  }
}

const removeDocument = async (document: KnowledgeDocument) => {
  await ElMessageBox.confirm(
    `确认删除知识文档「${document.title}」？删除后会同步移除向量索引。`,
    '删除知识文档',
    { type: 'warning' },
  )
  documentsLoading.value = true
  try {
    await deleteKnowledgeDocument(document.id)
    await loadDocuments()
    ElMessage.success('知识文档已删除')
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
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section v-loading="loading" class="knowledge-page">
    <div class="page-heading">
      <div>
        <h2>医疗知识助手</h2>
        <p>基于已入库资料进行检索增强问答，回答仅供资料理解参考。</p>
      </div>
      <el-button type="primary" :icon="ChatDotRound" @click="newSession">新会话</el-button>
    </div>

    <section v-if="admin" class="admin-band">
      <div class="admin-form">
        <div class="section-title">
          <el-icon><DocumentAdd /></el-icon>
          知识库管理
        </div>
        <el-input v-model="documentForm.title" placeholder="文档标题" />
        <el-input v-model="documentForm.source" placeholder="来源，如指南名称或院内规范" />
        <el-select v-model="documentForm.category" placeholder="知识分类">
          <el-option
            v-for="item in categoryOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select v-model="documentForm.audience" placeholder="可见范围">
          <el-option v-for="item in audienceOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input
          v-model="documentForm.content"
          type="textarea"
          :rows="5"
          placeholder="粘贴纯文本或 Markdown 内容"
        />
        <el-button type="primary" :loading="documentsLoading" @click="submitDocument">
          入库并索引
        </el-button>
      </div>

      <div class="document-list">
        <div class="section-title">
          <el-icon><Collection /></el-icon>
          已入库文档
        </div>
        <el-table :data="documents" size="small" height="280" v-loading="documentsLoading">
          <el-table-column prop="title" label="标题" min-width="170" show-overflow-tooltip />
          <el-table-column label="分类" width="120">
            <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
          </el-table-column>
          <el-table-column label="受众" width="110">
            <template #default="{ row }">{{ audienceLabel(row.audience) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="chunkCount" label="分块" width="80" />
          <el-table-column label="索引时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.lastIndexedAt) }}</template>
          </el-table-column>
          <el-table-column label="失败原因" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ formatEmpty(row.failureReason) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="210" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="Refresh" @click="reindex(row.id)">
                重建
              </el-button>
              <el-button link type="primary" :icon="Switch" @click="toggleStatus(row)">
                {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
              </el-button>
              <el-button link type="danger" :icon="Delete" @click="removeDocument(row)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <div class="assistant-grid">
      <aside class="session-panel">
        <div class="section-title">历史会话</div>
        <el-empty v-if="sessions.length === 0" description="暂无会话" />
        <button
          v-for="session in sessions"
          v-else
          :key="session.id"
          class="session-item"
          :class="{ active: session.id === activeSessionId }"
          @click="selectSession(session.id)"
        >
          <span>{{ formatEmpty(session.title) }}</span>
          <small>{{ formatDateTime(session.updatedAt) }}</small>
        </button>
      </aside>

      <main class="chat-panel">
        <div ref="messagesRef" class="messages">
          <el-empty v-if="messages.length === 0" description="请输入问题开始咨询" />
          <div
            v-for="(message, index) in messages"
            v-else
            :key="`${message.role}-${index}`"
            class="message"
            :class="message.role.toLowerCase()"
          >
            <div class="message-role">{{ message.role === 'USER' ? '我' : '知识助手' }}</div>
            <div class="message-content">{{ message.content }}</div>
          </div>
        </div>
        <div class="ask-bar">
          <el-input
            v-model="question"
            type="textarea"
            :rows="3"
            placeholder="例如：视网膜血管面积比在报告中应该如何解释？"
            @keydown.ctrl.enter.prevent="send"
          />
          <el-button type="primary" :icon="Search" :loading="sending" :disabled="sending" @click="send">
            提问
          </el-button>
        </div>
        <el-alert :title="latestDisclaimer" type="info" show-icon :closable="false" />
      </main>

      <aside class="citation-panel">
        <div class="section-title">引用来源</div>
        <el-empty v-if="currentCitations.length === 0" description="暂无引用" />
        <div v-for="citation in currentCitations" v-else :key="citation.chunkId" class="citation-card">
          <div class="citation-title">{{ citation.documentTitle }}</div>
          <div class="citation-meta">
            {{ citation.source }} · 相似度 {{ citation.score.toFixed(2) }}
          </div>
          <p>{{ citation.snippet }}</p>
        </div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.knowledge-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-heading,
.admin-band,
.assistant-grid {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px;
}

.page-heading h2 {
  margin: 0;
  color: #111827;
  font-size: 22px;
}

.page-heading p {
  margin: 6px 0 0;
  color: #6b7280;
}

.admin-band {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.4fr);
  gap: 16px;
  padding: 18px;
}

.admin-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.assistant-grid {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr) 300px;
  height: clamp(520px, calc(100vh - 230px), 760px);
  min-height: 0;
}

.session-panel,
.chat-panel,
.citation-panel {
  min-width: 0;
  min-height: 0;
  padding: 16px;
}

.session-panel,
.chat-panel {
  border-right: 1px solid #e5e7eb;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
  color: #111827;
  font-weight: 700;
}

.session-item {
  display: flex;
  width: 100%;
  flex-direction: column;
  gap: 4px;
  padding: 10px;
  cursor: pointer;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #374151;
  text-align: left;
}

.session-item.active,
.session-item:hover {
  background: #eef6ff;
  color: #1d4ed8;
}

.session-item small {
  color: #909399;
}

.chat-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
}

.messages {
  overflow: auto;
  flex: 1;
  min-height: 0;
  padding-right: 6px;
  scroll-behavior: smooth;
}

.message {
  margin-bottom: 12px;
  padding: 12px;
  border-radius: 8px;
}

.message.user {
  background: #eef6ff;
}

.message.assistant {
  background: #f9fafb;
}

.message-role {
  margin-bottom: 6px;
  color: #111827;
  font-weight: 700;
}

.message-content {
  color: #374151;
  line-height: 24px;
  white-space: pre-wrap;
}

.ask-bar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: end;
  flex-shrink: 0;
  padding-top: 8px;
  border-top: 1px solid #eef2f7;
  background: #ffffff;
}

.citation-card {
  margin-bottom: 12px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f9fafb;
}

.citation-title {
  color: #111827;
  font-weight: 700;
}

.citation-meta {
  margin-top: 4px;
  color: #6b7280;
  font-size: 12px;
}

.citation-card p {
  margin: 8px 0 0;
  color: #374151;
  line-height: 22px;
}

@media (max-width: 1180px) {
  .admin-band,
  .assistant-grid {
    grid-template-columns: 1fr;
    height: auto;
  }

  .chat-panel {
    min-height: 520px;
  }

  .session-panel,
  .chat-panel {
    border-right: 0;
    border-bottom: 1px solid #e5e7eb;
  }
}
</style>
