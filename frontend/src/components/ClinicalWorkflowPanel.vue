<script setup lang="ts">
import { ref } from 'vue'

import { useClinicalWorkflow } from '@/composables/use-clinical-workflow'
import type { Correction, Report } from '@/types/clinical'
import {
  correctionStatusTextMap,
  correctionStatusType,
  feedbackVerdictTextMap,
  reportStatusTextMap,
  reportStatusType,
  stepStatus,
  WORKFLOW_STAGES,
  type WorkflowStage,
} from '@/utils/clinical-workflow'
import { formatDateTime, formatEmpty } from '@/utils/format'

const props = defineProps<{ resultId: number; stage?: WorkflowStage }>()
const emit = defineEmits<{
  workflowUpdated: []
  stageChange: [stage: WorkflowStage]
}>()

// 模板 ref 必须由 SFC 自身声明，编译器才能静态识别并回填 DOM 元素
const fileInput = ref<HTMLInputElement | null>(null)

const {
  doctor,
  canCorrect,
  feedback,
  corrections,
  review,
  reports,
  loading,
  draftParseError,
  submittingFeedback,
  submittingCorrection,
  savingReview,
  generatingDraft,
  signingReport,
  showAllReports,
  feedbackForm,
  correctionForm,
  reviewForm,
  draftForm,
  latestCorrectionVersion,
  llmDraftMeta,
  activeStep,
  currentStage,
  visibleReports,
  selectStage,
  toggleAllReports,
  pickFile,
  sendFeedback,
  sendCorrection,
  sendReview,
  copyDraftToReview,
  generateDraft,
  sign,
  download,
} = useClinicalWorkflow({
  resultId: () => props.resultId,
  stage: () => props.stage,
  fileInput,
  onStageChange: (stage) => emit('stageChange', stage),
  onWorkflowUpdated: () => emit('workflowUpdated'),
})
</script>

<template>
  <div v-loading="loading" class="workflow-panel">
    <div class="workflow-header">
      <div>
        <div class="workflow-title">临床闭环</div>
        <div class="workflow-subtitle">
          按步骤处理反馈、修正、AI 草稿和正式审核，每次只展开当前工作区。
        </div>
      </div>
      <el-tag v-if="doctor" type="success">医生工作台</el-tag>
      <el-tag v-else-if="canCorrect" type="warning">研究员视图</el-tag>
      <el-tag v-else type="info">用户视图</el-tag>
    </div>

    <el-steps class="workflow-steps" :active="activeStep" finish-status="success" align-center>
      <el-step
        v-for="(item, index) in WORKFLOW_STAGES"
        :key="item.key"
        :title="item.title"
        :status="stepStatus(activeStep, index)"
        :class="{ 'is-selected': currentStage === item.key }"
        @click="selectStage(item.key)"
      />
    </el-steps>

    <div class="workflow-grid">
      <section v-show="currentStage === 'feedback'" class="workflow-section">
        <div class="section-heading">
          <div>
            <h3>1. 人工反馈</h3>
            <p>记录对 AI 结果的评价，不会覆盖原始结果。</p>
          </div>
        </div>

        <el-form class="feedback-form">
          <el-form-item label="结论">
            <el-select v-model="feedbackForm.verdict">
              <el-option label="接受" value="ACCEPTED" />
              <el-option label="部分正确" value="PARTIAL" />
              <el-option label="不正确" value="INCORRECT" />
            </el-select>
          </el-form-item>
          <el-form-item label="说明">
            <el-input
              v-model="feedbackForm.comment"
              maxlength="500"
              placeholder="可以说明 AI 结果哪里好、哪里需要修改"
              show-word-limit
            />
          </el-form-item>
          <el-button type="primary" :loading="submittingFeedback" @click="sendFeedback">
            提交反馈
          </el-button>
        </el-form>

        <div class="history-list">
          <el-empty v-if="feedback.length === 0" description="暂无反馈" />
          <template v-else>
            <div v-for="item in feedback" :key="item.id" class="history-item">
              <div class="history-main">
                <el-tag size="small">{{ feedbackVerdictTextMap[item.verdict] }}</el-tag>
                <span>{{ formatEmpty(item.comment) }}</span>
              </div>
              <span class="history-time">{{ formatDateTime(item.createdAt) }}</span>
            </div>
          </template>
        </div>
      </section>

      <section v-if="canCorrect" v-show="currentStage === 'correction'" class="workflow-section">
        <div class="section-heading">
          <div>
            <h3>2. 修正版本</h3>
            <p>上传人工修正 mask，系统会按版本保存，不覆盖 AI 原始 mask。</p>
          </div>
          <el-tag type="info">当前 V{{ latestCorrectionVersion }}</el-tag>
        </div>

        <el-form label-position="top" class="correction-form">
          <el-form-item label="修正 mask">
            <input
              ref="fileInput"
              type="file"
              accept="image/png,image/jpeg,image/tiff,.tif,.tiff"
              @change="pickFile"
            />
            <div class="hint">支持 PNG、JPEG、TIFF；后端会统一保存为二值 PNG。</div>
          </el-form-item>
          <el-form-item label="修正原因">
            <el-input
              v-model="correctionForm.reason"
              placeholder="例如：局部血管断裂，需要人工补全"
            />
          </el-form-item>
          <div class="section-actions">
            <el-button type="primary" plain :loading="submittingCorrection" @click="sendCorrection">
              上传修正版本
            </el-button>
          </div>
        </el-form>

        <div class="compact-table">
          <el-empty v-if="corrections.length === 0" description="暂无修正版本" />
          <el-table v-else :data="corrections" size="small">
            <el-table-column label="版本" width="90">
              <template #default="{ row }">V{{ row.version }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }: { row: Correction }">
                <el-tag :type="correctionStatusType(row.status)" size="small">
                  {{ correctionStatusTextMap[row.status] }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="原因" show-overflow-tooltip />
          </el-table>
        </div>
      </section>

      <section v-if="doctor" v-show="currentStage === 'draft'" class="workflow-section">
        <div class="section-heading">
          <div>
            <h3>3. AI 草稿参考</h3>
            <p>先由大模型生成报告草稿和结果解释，只作为医生审核前的参考。</p>
          </div>
        </div>

        <el-alert
          v-if="draftParseError"
          :title="draftParseError"
          type="warning"
          show-icon
          :closable="false"
          class="draft-alert"
        />
        <el-alert
          title="AI 草稿仅供医生参考，不是正式审核意见；PDF 只采用医生审核保存后的内容。"
          type="info"
          show-icon
          :closable="false"
          class="draft-alert"
        />

        <el-form label-position="top" class="draft-form">
          <el-form-item label="AI 草稿所见">
            <el-input v-model="draftForm.findings" type="textarea" :rows="3" readonly />
          </el-form-item>
          <el-form-item label="AI 草稿结论">
            <el-input v-model="draftForm.conclusion" type="textarea" :rows="3" readonly />
          </el-form-item>
          <el-form-item label="AI 草稿建议">
            <el-input v-model="draftForm.recommendation" type="textarea" :rows="3" readonly />
          </el-form-item>
          <el-form-item label="AI 结果解释">
            <el-input v-model="draftForm.explanation" type="textarea" :rows="3" readonly />
          </el-form-item>
          <div v-if="llmDraftMeta.visible" class="llm-meta">
            <span>生成来源：{{ formatEmpty(llmDraftMeta.provider) }}</span>
            <span>模型：{{ formatEmpty(llmDraftMeta.model) }}</span>
            <span>生成时间：{{ formatDateTime(llmDraftMeta.generatedAt) }}</span>
          </div>
          <el-form-item label="固定声明">
            <el-input v-model="draftForm.disclaimer" readonly />
          </el-form-item>
          <div class="section-actions">
            <el-button type="primary" plain :loading="generatingDraft" @click="generateDraft">
              AI 生成草稿
            </el-button>
            <el-button plain @click="copyDraftToReview(true)">带入医生审核</el-button>
          </div>
        </el-form>
      </section>

      <section v-show="currentStage === 'review'" class="workflow-section">
        <div class="section-heading">
          <div>
            <h3>4. 医生审核与签发</h3>
            <p>医生在参考 AI 草稿后保存正式审核意见；PDF 只以医生审核意见为准。</p>
          </div>
          <el-tag v-if="review" type="success">审核版本 V{{ review.version }}</el-tag>
          <el-tag v-else type="info">尚未审核</el-tag>
        </div>

        <template v-if="doctor">
          <el-form label-position="top" class="review-form">
            <div class="form-row">
              <el-form-item label="审核状态">
                <el-select v-model="reviewForm.status">
                  <el-option label="待审核" value="PENDING" />
                  <el-option label="需修改" value="CHANGES_REQUESTED" />
                  <el-option label="通过" value="APPROVED" />
                  <el-option label="拒绝" value="REJECTED" />
                </el-select>
              </el-form-item>
              <el-form-item label="采用结果">
                <el-select v-model="reviewForm.correctionVersion">
                  <el-option label="AI 原始结果" :value="null" />
                  <el-option
                    v-for="item in corrections"
                    :key="item.version"
                    :label="`修正版本 V${item.version}`"
                    :value="item.version"
                  />
                </el-select>
              </el-form-item>
            </div>
            <el-form-item label="医生所见">
              <el-input v-model="reviewForm.findings" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="审核结论">
              <el-input v-model="reviewForm.conclusion" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="处理建议">
              <el-input v-model="reviewForm.recommendation" type="textarea" :rows="3" />
            </el-form-item>
            <div class="section-actions sticky-actions">
              <el-button type="primary" :loading="savingReview" @click="sendReview">
                保存审核
              </el-button>
              <el-button type="danger" :loading="signingReport" @click="sign"> 签发 PDF </el-button>
            </div>
          </el-form>
        </template>

        <div class="report-history">
          <div class="history-title">报告历史</div>
          <el-empty v-if="reports.length === 0" description="暂无报告" />
          <el-table v-else :data="visibleReports" size="small">
            <el-table-column label="版本" width="90">
              <template #default="{ row }">V{{ row.version }}</template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }: { row: Report }">
                <el-tag :type="reportStatusType(row.status)" size="small">
                  {{ reportStatusTextMap[row.status] }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="签发时间" min-width="160">
              <template #default="{ row }">{{ formatDateTime(row.signedAt) }}</template>
            </el-table-column>
            <el-table-column label="SHA-256" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ formatEmpty(row.reportSha256) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button v-if="row.status !== 'DRAFT'" link type="primary" @click="download(row)">
                  下载
                </el-button>
                <span v-else class="muted">未签发</span>
              </template>
            </el-table-column>
          </el-table>
          <el-button
            v-if="reports.length > 1"
            link
            type="primary"
            class="history-toggle"
            @click="toggleAllReports"
          >
            {{ showAllReports ? '收起历史版本' : `查看全部 ${reports.length} 个版本` }}
          </el-button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped src="./ClinicalWorkflowPanel.css"></style>
