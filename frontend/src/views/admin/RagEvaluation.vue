<script setup lang="ts">
import { Refresh, VideoPlay } from '@element-plus/icons-vue'

import { useRagEvaluation } from '@/composables/use-rag-evaluation'
import type { RagEvaluationRun } from '@/types/prompt'

const {
  isAdmin,
  runs,
  selected,
  candidateId,
  decision,
  score,
  note,
  loading,
  actionLoading,
  candidateVersions,
  canRelease,
  loadRuns,
  loadDetail,
  start,
  review,
  release,
} = useRagEvaluation()
</script>

<template>
  <section class="rag-evaluation">
    <header class="heading">
      <div>
        <h2>RAG 检索与引用评测</h2>
        <p>固定合成知识片段 · 独立评测索引</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadRuns">刷新</el-button>
    </header>
    <el-alert
      type="warning"
      show-icon
      :closable="false"
      title="Hit@3 和 MRR 衡量检索排序；逐字引用校验只验证来源，不能证明医学事实性。管理员须逐题人工评审后才可启用。"
    />

    <div v-if="isAdmin" class="controls">
      <span>候选版本</span>
      <el-select v-model="candidateId" placeholder="选择未启用版本" style="width: 180px">
        <el-option
          v-for="version in candidateVersions"
          :key="version.id"
          :label="`v${version.version}`"
          :value="version.id"
        />
      </el-select>
      <el-button
        type="primary"
        :icon="VideoPlay"
        :disabled="!candidateId"
        :loading="actionLoading"
        @click="start"
        >运行评测</el-button
      >
    </div>

    <el-table
      :data="runs"
      border
      empty-text="暂无评测记录"
      :row-class-name="() => 'clickable'"
      @row-click="(row: RagEvaluationRun) => loadDetail(row.id)"
    >
      <el-table-column prop="id" label="编号" width="80" />
      <el-table-column prop="createdAt" label="提交时间" min-width="170" />
      <el-table-column prop="sampleVersion" label="样本集" width="110" />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column label="自动校验" width="110">
        <template #default="{ row }: { row: RagEvaluationRun }">{{
          row.automatedPass === null ? '-' : row.automatedPass ? '通过' : '未通过'
        }}</template>
      </el-table-column>
      <el-table-column label="人工评审" width="110">
        <template #default="{ row }: { row: RagEvaluationRun }">{{
          row.reviewDecision === 'APPROVED'
            ? '批准'
            : row.reviewDecision === 'REJECTED'
              ? '拒绝'
              : '待评审'
        }}</template>
      </el-table-column>
    </el-table>

    <section v-if="selected" class="detail">
      <div class="heading">
        <div>
          <h3>评测 #{{ selected.id }}</h3>
          <p>
            {{ selected.provider }} / {{ selected.model }} · {{ selected.embeddingModel }} · 阈值
            {{ selected.scoreThreshold }} · {{ selected.sampleVersion }}
          </p>
        </div>
        <el-button v-if="canRelease" type="primary" :loading="actionLoading" @click="release"
          >启用候选版本</el-button
        >
      </div>
      <el-alert
        v-if="selected.failureReason"
        type="error"
        :title="selected.failureReason"
        :closable="false"
      />
      <div v-if="selected.result" class="metrics">
        <div>
          Hit@3 <strong>{{ (selected.result.retrieval.hitAt3 * 100).toFixed(1) }}%</strong>
        </div>
        <div>
          MRR <strong>{{ selected.result.retrieval.mrr.toFixed(3) }}</strong>
        </div>
        <div>
          候选回答
          <strong>{{ selected.result.generationPassed ? '全部可核验' : '存在未核验回答' }}</strong>
        </div>
      </div>
      <div v-for="item in selected.result?.cases || []" :key="item.caseId" class="case-row">
        <div class="case-title">
          <strong>{{ item.title }}</strong
          ><span>预期 #{{ item.expectedChunkId }} · 排名 {{ item.rank || '未命中' }}</span>
        </div>
        <p class="question">{{ item.question }}</p>
        <p class="hit-list">
          检索命中：{{
            item.retrievedChunkIds.length
              ? item.retrievedChunkIds.map((id) => `#${id}`).join('、')
              : '无'
          }}
        </p>
        <div class="comparison">
          <div v-for="side in ['baseline', 'candidate'] as const" :key="side" class="answer">
            <div class="answer-heading">
              <strong>{{ side === 'baseline' ? '当前版' : '候选版' }}</strong>
              <el-tag size="small" :type="item[side].passed ? 'success' : 'danger'">{{
                item[side].passed ? '引用可核验' : item[side].errorCode
              }}</el-tag>
              <span>{{ item[side].latencyMs }} ms</span>
            </div>
            <p>{{ item[side].answer || '无可展示的有效回答' }}</p>
            <ul v-if="item[side].citations.length">
              <li v-for="citation in item[side].citations" :key="citation.chunkId">
                #{{ citation.chunkId }} {{ citation.documentTitle }}：{{ citation.snippet }}
              </li>
            </ul>
          </div>
        </div>
      </div>
      <p v-if="selected.reviewDecision" class="reviewed">
        人工评审：{{ selected.reviewDecision === 'APPROVED' ? '批准' : '拒绝' }} ·
        {{ selected.reviewScore }}/5 分 · {{ selected.reviewNote || '无备注' }}
      </p>
      <div
        v-if="isAdmin && selected.status === 'COMPLETED' && !selected.reviewDecision"
        class="review-form"
      >
        <h3>管理员人工评审</h3>
        <el-radio-group v-model="decision"
          ><el-radio-button label="APPROVED" :disabled="selected.automatedPass !== true"
            >批准</el-radio-button
          ><el-radio-button label="REJECTED">拒绝</el-radio-button></el-radio-group
        >
        <div class="score"><span>质量评分</span><el-rate v-model="score" :max="5" /></div>
        <el-input
          v-model="note"
          type="textarea"
          :rows="3"
          :maxlength="500"
          show-word-limit
          placeholder="逐题说明事实性、引用支撑性和需改进之处"
        />
        <el-button type="primary" :loading="actionLoading" @click="review">保存复核意见</el-button>
      </div>
    </section>
  </section>
</template>

<style scoped src="./RagEvaluation.css"></style>
