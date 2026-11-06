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
import { ref } from 'vue'

import { useKnowledgeAssistant } from '@/composables/use-knowledge-assistant'
import { formatDateTime, formatEmpty } from '@/utils/format'

// 消息滚动容器的模板 ref 必须由 SFC 自身声明，编译器才能静态识别并回填 DOM 元素
const messagesRef = ref<HTMLElement | null>(null)

const {
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
} = useKnowledgeAssistant({ messagesRef })
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
          <el-option
            v-for="item in audienceOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
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
        <template v-else>
          <button
            v-for="session in sessions"
            :key="session.id"
            class="session-item"
            :class="{ active: session.id === activeSessionId }"
            @click="selectSession(session.id)"
          >
            <span>{{ formatEmpty(session.title) }}</span>
            <small>{{ formatDateTime(session.updatedAt) }}</small>
          </button>
        </template>
      </aside>

      <main class="chat-panel">
        <div ref="messagesRef" class="messages">
          <el-empty v-if="messages.length === 0" description="请输入问题开始咨询" />
          <template v-else>
            <div
              v-for="(message, index) in messages"
              :key="`${message.role}-${index}`"
              class="message"
              :class="message.role.toLowerCase()"
            >
              <div class="message-role">{{ message.role === 'USER' ? '我' : '知识助手' }}</div>
              <div class="message-content">{{ message.content }}</div>
            </div>
          </template>
        </div>
        <div class="ask-bar">
          <el-input
            v-model="question"
            type="textarea"
            :rows="3"
            placeholder="例如：视网膜血管面积比在报告中应该如何解释？"
            @keydown.ctrl.enter.prevent="send"
          />
          <el-button
            type="primary"
            :icon="Search"
            :loading="sending"
            :disabled="sending"
            @click="send"
          >
            提问
          </el-button>
        </div>
        <el-alert :title="latestDisclaimer" type="info" show-icon :closable="false" />
      </main>

      <aside class="citation-panel">
        <div class="section-title">引用来源</div>
        <el-empty v-if="currentCitations.length === 0" description="暂无引用" />
        <template v-else>
          <div v-for="citation in currentCitations" :key="citation.chunkId" class="citation-card">
            <div class="citation-title">{{ citation.documentTitle }}</div>
            <div class="citation-meta">
              {{ citation.source }} · 相似度 {{ citation.score.toFixed(2) }}
            </div>
            <p>{{ citation.snippet }}</p>
          </div>
        </template>
      </aside>
    </div>
  </section>
</template>

<style scoped src="./KnowledgeAssistant.css"></style>
