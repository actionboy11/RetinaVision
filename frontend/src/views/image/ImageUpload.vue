<script setup lang="ts">
import { Back, Delete, Plus, Refresh, UploadFilled, View } from '@element-plus/icons-vue'

import ImagePreview from '@/components/ImagePreview.vue'
import { useImageUpload } from '@/composables/use-image-upload'
import type { ImageFileItem } from '@/types/image'
import {
  caseStatusTagTypeMap,
  caseStatusTextMap,
  eyeSideTextMap,
  imageStatusTagTypeMap,
  imageStatusTextMap,
  patientGenderTextMap,
  taskTypeTextMap,
} from '@/utils/enums'
import {
  formatDateTime,
  formatFileSize,
  formatNullable,
  formatPercent,
  formatScore,
} from '@/utils/format'

const {
  isDoctor,
  isPatient,
  canModifyImages,
  loading,
  caseLoading,
  uploadLoading,
  deleteLoadingId,
  errorMessage,
  caseDetail,
  timelineLoading,
  timelineError,
  timelineItems,
  imageList,
  previewVisible,
  previewState,
  loadImages,
  loadTimeline,
  vesselBarWidth,
  statusText,
  ACCEPTED_FILES,
  beforeUpload,
  handleUpload,
  getPreviewUrl,
  openPreview,
  handleDelete,
  goBack,
  goToCreateTask,
  formatImageSize,
} = useImageUpload()
</script>

<template>
  <section class="image-page">
    <div class="page-heading">
      <div>
        <h2>{{ isPatient ? '检查图像' : '图像管理' }}</h2>
        <p>
          {{
            isPatient
              ? '在提交检查前上传眼底图像，系统会自动完成图像质量检测。'
              : '管理当前病例图像并开展医生侧分析任务。'
          }}
        </p>
      </div>
      <el-button :icon="Back" plain @click="goBack">返回病例列表</el-button>
    </div>

    <div v-loading="caseLoading" class="case-panel">
      <template v-if="caseDetail">
        <div class="case-item">
          <span>病例编号</span>
          <strong>{{ formatNullable(caseDetail.caseNo) }}</strong>
        </div>
        <div class="case-item">
          <span>匿名患者编号</span>
          <strong>{{ formatNullable(caseDetail.patientNo) }}</strong>
        </div>
        <div class="case-item">
          <span>性别</span>
          <strong>{{ patientGenderTextMap[caseDetail.patientGender] }}</strong>
        </div>
        <div class="case-item">
          <span>眼别</span>
          <strong>{{ eyeSideTextMap[caseDetail.eyeSide] }}</strong>
        </div>
        <div class="case-item">
          <span>状态</span>
          <el-tag :type="caseStatusTagTypeMap[caseDetail.status]">
            {{ caseStatusTextMap[caseDetail.status] }}
          </el-tag>
        </div>
        <div class="case-item">
          <span>创建时间</span>
          <strong>{{ formatNullable(caseDetail.createdAt) }}</strong>
        </div>
      </template>
      <el-empty v-else description="暂无病例详情" />
    </div>

    <div v-if="isDoctor" v-loading="timelineLoading" class="trend-panel">
      <div class="panel-title">
        <div>
          <h3>随访趋势</h3>
          <p>基于同一病例的血管分割、图像质量、医生审核和报告签发记录生成，仅供医生复核参考。</p>
        </div>
        <el-button :icon="Refresh" :loading="timelineLoading" plain @click="loadTimeline">
          刷新趋势
        </el-button>
      </div>

      <el-alert
        v-if="timelineError"
        :title="timelineError"
        class="error-alert"
        show-icon
        type="warning"
        :closable="false"
      />

      <el-empty
        v-if="!timelineLoading && timelineItems.length === 0"
        description="暂无可用于随访趋势的血管分割结果"
      />

      <div v-else class="trend-list">
        <div v-for="item in timelineItems" :key="item.taskId" class="trend-row">
          <div class="trend-time">
            <strong>{{ formatDateTime(item.finishedAt || item.resultCreatedAt) }}</strong>
            <span>{{ taskTypeTextMap[item.taskType] || item.taskType }}</span>
          </div>
          <div class="trend-metrics">
            <div class="trend-metric">
              <span>图像质量</span>
              <strong>
                {{ item.qualityStatus || '-' }}
                {{
                  typeof item.qualityScore === 'number' ? `/ ${formatScore(item.qualityScore)}` : ''
                }}
              </strong>
            </div>
            <div class="trend-metric trend-vessel">
              <span>血管面积比例</span>
              <div class="trend-bar">
                <i :style="{ width: vesselBarWidth(item.vesselAreaRatio) }" />
              </div>
              <strong>{{
                typeof item.vesselAreaRatio === 'number' ? formatPercent(item.vesselAreaRatio) : '-'
              }}</strong>
            </div>
            <div class="trend-metric">
              <span>审核/报告</span>
              <strong
                >{{ statusText(item.reviewStatus) }} / {{ statusText(item.reportStatus) }}</strong
              >
            </div>
          </div>
        </div>
      </div>

      <el-alert
        class="trend-note"
        show-icon
        type="info"
        :closable="false"
        title="趋势受拍摄条件、图像质量和模型版本影响，只作为辅助复核线索，不构成自动诊断。"
      />
    </div>

    <div v-if="canModifyImages" class="upload-panel">
      <div class="panel-title">
        <div>
          <h3>上传图像</h3>
          <p>支持 png、jpg、jpeg、tif、tiff，单个文件不超过 20MB。</p>
        </div>
      </div>

      <el-upload
        :http-request="handleUpload"
        :show-file-list="false"
        :before-upload="beforeUpload"
        :accept="ACCEPTED_FILES"
        drag
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽图像到此处，或 <em>点击上传</em></div>
        <template #tip>
          <div class="upload-tip">上传后系统会自动检测图像质量；质量不合格时请重新拍摄并上传。</div>
        </template>
      </el-upload>

      <el-alert
        v-if="uploadLoading"
        class="uploading-alert"
        show-icon
        title="图像上传中，请稍候"
        type="info"
      />
    </div>

    <el-alert
      v-else-if="isPatient"
      title="检查已提交，图像已锁定。如需调整，请在医生开始分析前先撤回检查申请。"
      type="info"
      show-icon
      :closable="false"
    />

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      class="error-alert"
      show-icon
      type="error"
    />

    <div class="table-panel">
      <div class="panel-title">
        <div>
          <h3>图像列表</h3>
          <p>当前病例共 {{ imageList.length }} 张图像</p>
        </div>
        <el-button :icon="Refresh" :loading="loading" plain @click="loadImages"> 刷新 </el-button>
      </div>

      <el-table v-loading="loading" :data="imageList" border empty-text="暂无图像数据">
        <el-table-column label="缩略图" min-width="110">
          <template #default="{ row }: { row: ImageFileItem }">
            <el-image
              :preview-src-list="[getPreviewUrl(row)]"
              :src="getPreviewUrl(row)"
              class="thumb"
              fit="cover"
            >
              <template #error>
                <div class="thumb-placeholder">无图</div>
              </template>
            </el-image>
          </template>
        </el-table-column>
        <el-table-column label="原始文件名" min-width="180">
          <template #default="{ row }: { row: ImageFileItem }">
            {{ formatNullable(row.originalFilename) }}
          </template>
        </el-table-column>
        <el-table-column label="类型" min-width="90">
          <template #default="{ row }: { row: ImageFileItem }">
            {{ formatNullable(row.fileType) }}
          </template>
        </el-table-column>
        <el-table-column label="大小" min-width="110">
          <template #default="{ row }: { row: ImageFileItem }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column label="尺寸" min-width="120">
          <template #default="{ row }: { row: ImageFileItem }">
            {{ formatImageSize(row) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="120">
          <template #default="{ row }: { row: ImageFileItem }">
            <el-tag :type="imageStatusTagTypeMap[row.status]">
              {{ imageStatusTextMap[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="图像质量" min-width="150">
          <template #default="{ row }: { row: ImageFileItem }">
            <el-tag
              :type="
                row.qualityStatus === 'PASS'
                  ? 'success'
                  : row.qualityStatus === 'WARNING'
                    ? 'warning'
                    : row.qualityStatus === 'FAIL' || row.qualityStatus === 'ERROR'
                      ? 'danger'
                      : 'info'
              "
            >
              {{ row.qualityStatus }}
            </el-tag>
            <span style="margin-left: 8px">{{ row.qualityScore ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="上传人" min-width="120">
          <template #default="{ row }: { row: ImageFileItem }">
            {{ formatNullable(row.uploadedByName) }}
          </template>
        </el-table-column>
        <el-table-column label="上传时间" min-width="170">
          <template #default="{ row }: { row: ImageFileItem }">
            {{ formatNullable(row.uploadedAt) }}
          </template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" min-width="250">
          <template #default="{ row }: { row: ImageFileItem }">
            <el-button link type="primary" :icon="View" @click="openPreview(row)"> 预览 </el-button>
            <el-button
              v-if="isDoctor"
              link
              type="primary"
              :icon="Plus"
              @click="goToCreateTask(row)"
            >
              创建任务
            </el-button>
            <el-button
              v-if="canModifyImages"
              link
              type="danger"
              :icon="Delete"
              :loading="deleteLoadingId === row.id"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <ImagePreview
      v-model="previewVisible"
      :filename="previewState.filename"
      :image-url="previewState.imageUrl"
      :title="previewState.title"
    />
  </section>
</template>

<style scoped src="./ImageUpload.css"></style>
