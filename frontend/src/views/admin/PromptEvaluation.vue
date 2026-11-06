<script setup lang="ts">
import { Refresh, VideoPlay } from '@element-plus/icons-vue'

import { usePromptEvaluation } from '@/composables/use-prompt-evaluation'
import type { PromptEvaluationRun } from '@/types/prompt'

const {
  isAdmin,
  runs,
  selected,
  candidateId,
  decision,
  score,
  note,
  listLoading,
  detailLoading,
  starting,
  reviewing,
  releasing,
  candidateVersions,
  canRelease,
  statusText,
  errorText,
  readableDraft,
  loadRuns,
  loadDetail,
  start,
  review,
  release,
} = usePromptEvaluation()
</script>

<template>
  <section class="evaluation-page">
    <header class="page-heading">
      <div>
        <h2>报告草稿 Prompt 评测</h2>
        <p>固定合成样本 · 当前版与候选版对照</p>
      </div>
      <el-button :icon="Refresh" :loading="listLoading" @click="loadRuns">刷新</el-button>
    </header>

    <el-alert
      type="warning"
      show-icon
      :closable="false"
      title="自动校验只检查格式、安全措辞和声明；管理员须完成离线人工评审后才可启用。评测不会写入病例或正式报告。"
    />

    <section v-if="isAdmin" class="run-controls">
      <label for="candidate-version">候选版本</label>
      <el-select
        id="candidate-version"
        v-model="candidateId"
        placeholder="选择未启用版本"
        style="width: 200px"
      >
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
        :loading="starting"
        @click="start"
        >运行评测</el-button
      >
    </section>

    <section class="runs-section">
      <h3>评测记录</h3>
      <el-table
        v-loading="listLoading"
        :data="runs"
        border
        empty-text="暂无评测记录"
        highlight-current-row
        @row-click="(row: PromptEvaluationRun) => loadDetail(row.id)"
      >
        <el-table-column prop="id" label="编号" width="75" />
        <el-table-column prop="createdAt" label="提交时间" min-width="175" />
        <el-table-column prop="sampleVersion" label="样本集" min-width="145" />
        <el-table-column label="版本对比" min-width="150">
          <template #default="{ row }: { row: PromptEvaluationRun }">
            #{{ row.baselineVersionId }} → #{{ row.candidateVersionId }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }: { row: PromptEvaluationRun }">
            <el-tag
              :type="
                row.status === 'FAILED' ? 'danger' : row.status === 'COMPLETED' ? 'success' : 'info'
              "
            >
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="自动校验" width="105">
          <template #default="{ row }: { row: PromptEvaluationRun }">
            {{ row.automatedPass === null ? '-' : row.automatedPass ? '通过' : '未通过' }}
          </template>
        </el-table-column>
        <el-table-column label="人工评审" width="105">
          <template #default="{ row }: { row: PromptEvaluationRun }">
            {{
              row.reviewDecision === 'APPROVED'
                ? '批准'
                : row.reviewDecision === 'REJECTED'
                  ? '拒绝'
                  : '待评审'
            }}
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section v-if="selected" v-loading="detailLoading" class="detail-section">
      <div class="detail-heading">
        <div>
          <h3>评测 #{{ selected.id }}</h3>
          <p>{{ selected.provider }} / {{ selected.model }} · {{ selected.sampleVersion }}</p>
        </div>
        <el-button v-if="canRelease" type="primary" :loading="releasing" @click="release"
          >启用候选版本</el-button
        >
      </div>
      <el-alert
        v-if="selected.failureReason"
        type="error"
        :closable="false"
        :title="selected.failureReason"
      />

      <div v-if="selected.result?.cases?.length" class="cases">
        <div v-for="item in selected.result.cases" :key="item.caseId" class="case-row">
          <h4>{{ item.title }}</h4>
          <div class="case-context">
            <span>合成输入</span>
            <span>眼别：{{ item.context.case?.eyeSide || '-' }}</span>
            <span
              >年龄/性别：{{ item.context.case?.age || '-' }} /
              {{ item.context.case?.gender || '-' }}</span
            >
            <span
              >质量：{{ item.context.image?.qualityStatus || '-' }} /
              {{ item.context.image?.qualityScore ?? '-' }}</span
            >
            <span>血管面积比例：{{ item.context.result?.resultJson?.vesselAreaRatio ?? '-' }}</span>
          </div>
          <div class="comparison">
            <div
              v-for="side in ['baseline', 'candidate'] as const"
              :key="side"
              class="comparison-side"
            >
              <div class="side-heading">
                <strong>{{ side === 'baseline' ? '当前版' : '候选版' }}</strong>
                <el-tag :type="item[side].passed ? 'success' : 'danger'" size="small">
                  {{ errorText(item[side].errorCode) }}
                </el-tag>
                <span>{{ item[side].latencyMs }} ms</span>
              </div>
              <dl v-if="readableDraft(item[side])">
                <template v-for="entry in readableDraft(item[side])" :key="entry[0]">
                  <dt>{{ entry[0] }}</dt>
                  <dd>{{ entry[1] }}</dd>
                </template>
              </dl>
              <p v-else class="empty-output">未保存不合规或无效输出</p>
            </div>
          </div>
        </div>
      </div>

      <div v-if="selected.reviewDecision" class="review-summary">
        人工评审：{{ selected.reviewDecision === 'APPROVED' ? '批准' : '拒绝' }} ·
        {{ selected.reviewScore }}/5 分<span v-if="selected.reviewNote">
          · {{ selected.reviewNote }}</span
        >
      </div>
      <div
        v-if="isAdmin && selected.status === 'COMPLETED' && !selected.reviewDecision"
        class="review-form"
      >
        <h4>管理员人工评审</h4>
        <el-radio-group v-model="decision">
          <el-radio-button label="APPROVED" :disabled="selected.automatedPass !== true"
            >批准</el-radio-button
          >
          <el-radio-button label="REJECTED">拒绝</el-radio-button>
        </el-radio-group>
        <div class="score-control"><span>质量评分</span><el-rate v-model="score" :max="5" /></div>
        <el-input
          v-model="note"
          type="textarea"
          :rows="3"
          :maxlength="500"
          show-word-limit
          placeholder="专业措辞、事实一致性及预期修改工作量"
        />
        <el-button type="primary" :loading="reviewing" @click="review">保存评测意见</el-button>
      </div>
    </section>
  </section>
</template>

<style scoped src="./PromptEvaluation.css"></style>
