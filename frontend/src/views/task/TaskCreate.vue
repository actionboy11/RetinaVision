<script setup lang="ts">
import { Back, Picture, Refresh, View } from '@element-plus/icons-vue'
import { type FormInstance } from '@/plugins/element-plus-api'
import { ref } from 'vue'

import ImagePreview from '@/components/ImagePreview.vue'
import { useTaskCreate } from '@/composables/use-task-create'
import type { ImageFileItem } from '@/types/image'
import {
  caseStatusTagTypeMap,
  caseStatusTextMap,
  eyeSideTextMap,
  imageQualityStatusTagTypeMap,
  imageQualityStatusTextMap,
  imageStatusTagTypeMap,
  imageStatusTextMap,
  patientGenderTextMap,
  taskTypeTextMap,
} from '@/utils/enums'
import { formatAge, formatEmpty, formatFileSize } from '@/utils/format'

// 模板 ref 必须由 SFC 自身声明，编译器才能静态识别并回填表单实例
const formRef = ref<FormInstance>()

const {
  caseLoading,
  imageLoading,
  qualityRetryLoadingId,
  submitLoading,
  caseOptions,
  selectedCase,
  imageList,
  imageSelectionError,
  previewVisible,
  previewState,
  form,
  rules,
  selectedImage,
  canOverrideQuality,
  canBackToImages,
  loadCaseOptions,
  handleCaseChange,
  getPreviewUrl,
  openPreview,
  isSelectableImage,
  selectImage,
  retryQualityCheck,
  refreshImages,
  handleSubmit,
  goToTasks,
  goToCases,
  goToImages,
} = useTaskCreate({ formRef })
</script>

<template>
  <section class="task-create-page">
    <div class="page-heading">
      <div>
        <h2>创建分析任务</h2>
        <p>选择病例和眼底图像，提交 AI 分析任务，由后端异步处理。</p>
      </div>
      <div class="heading-actions">
        <el-button :icon="Back" plain @click="goToTasks">返回任务列表</el-button>
        <el-button plain @click="goToCases">返回病例列表</el-button>
        <el-button v-if="canBackToImages" plain @click="goToImages"> 返回图像管理 </el-button>
      </div>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div class="form-section">
        <div class="section-title">
          <h3>选择病例</h3>
          <p>默认仅搜索正常状态病例，支持按病例编号或匿名患者编号查询。</p>
        </div>

        <el-form-item label="病例" prop="caseId">
          <el-select
            v-model="form.caseId"
            :loading="caseLoading"
            class="wide-control"
            clearable
            filterable
            placeholder="请输入关键词搜索病例"
            remote
            :remote-method="loadCaseOptions"
            @change="handleCaseChange"
          >
            <el-option
              v-for="caseItem in caseOptions"
              :key="caseItem.id"
              :label="`${caseItem.caseNo} / ${caseItem.patientNo || caseItem.patientCode}`"
              :value="caseItem.id"
            >
              <div class="case-option">
                <span>{{ caseItem.caseNo }}</span>
                <small>{{ caseItem.patientNo || caseItem.patientCode }}</small>
              </div>
            </el-option>
          </el-select>
        </el-form-item>

        <div v-loading="caseLoading" class="info-grid">
          <template v-if="selectedCase">
            <div class="info-item">
              <span>病例编号</span>
              <strong>{{ formatEmpty(selectedCase.caseNo) }}</strong>
            </div>
            <div class="info-item">
              <span>匿名患者编号</span>
              <strong>{{ formatEmpty(selectedCase.patientNo || selectedCase.patientCode) }}</strong>
            </div>
            <div class="info-item">
              <span>年龄</span>
              <strong>{{ formatAge(selectedCase.patientAge) }}</strong>
            </div>
            <div class="info-item">
              <span>性别</span>
              <strong>{{ patientGenderTextMap[selectedCase.patientGender] }}</strong>
            </div>
            <div class="info-item">
              <span>眼别</span>
              <strong>{{ eyeSideTextMap[selectedCase.eyeSide] }}</strong>
            </div>
            <div class="info-item">
              <span>状态</span>
              <el-tag :type="caseStatusTagTypeMap[selectedCase.status]">
                {{ caseStatusTextMap[selectedCase.status] }}
              </el-tag>
            </div>
            <div class="info-item">
              <span>创建时间</span>
              <strong>{{ formatEmpty(selectedCase.createdAt) }}</strong>
            </div>
          </template>
          <el-empty v-else description="请选择病例" />
        </div>
      </div>

      <div class="form-section">
        <div class="section-title">
          <div>
            <h3>选择图像</h3>
            <p>质量检测状态和评分仅供医生参考，医生可结合图像情况自主决定是否继续分析。</p>
          </div>
          <el-button
            v-if="form.caseId"
            :icon="Refresh"
            :loading="imageLoading"
            plain
            @click="refreshImages"
          >
            刷新状态
          </el-button>
        </div>

        <div v-if="imageSelectionError" class="field-error">{{ imageSelectionError }}</div>

        <el-empty
          v-if="form.caseId && !imageLoading && imageList.length === 0"
          description="当前病例暂无图像，请先上传图像"
        >
          <el-button type="primary" :icon="Picture" @click="goToImages"> 去上传图像 </el-button>
        </el-empty>

        <el-table
          v-else
          v-loading="imageLoading"
          :data="imageList"
          border
          empty-text="请先选择病例"
          row-key="id"
          @row-click="selectImage"
        >
          <el-table-column label="选择" width="70">
            <template #default="{ row }: { row: ImageFileItem }">
              <el-radio
                :disabled="!isSelectableImage(row)"
                :model-value="form.imageFileId"
                :value="row.id"
                @change="selectImage(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="缩略图" width="110">
            <template #default="{ row }: { row: ImageFileItem }">
              <el-image :src="getPreviewUrl(row)" class="thumb" fit="cover">
                <template #error>
                  <div class="thumb-placeholder">无图</div>
                </template>
              </el-image>
            </template>
          </el-table-column>
          <el-table-column label="文件名" min-width="180">
            <template #default="{ row }: { row: ImageFileItem }">
              {{ formatEmpty(row.originalFilename) }}
            </template>
          </el-table-column>
          <el-table-column label="类型" width="90">
            <template #default="{ row }: { row: ImageFileItem }">
              {{ formatEmpty(row.fileType) }}
            </template>
          </el-table-column>
          <el-table-column label="大小" width="110">
            <template #default="{ row }: { row: ImageFileItem }">
              {{ formatFileSize(row.fileSize) }}
            </template>
          </el-table-column>
          <el-table-column label="尺寸" width="120">
            <template #default="{ row }: { row: ImageFileItem }">
              {{
                row.imageWidth && row.imageHeight ? `${row.imageWidth} x ${row.imageHeight}` : '-'
              }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }: { row: ImageFileItem }">
              <el-tag :type="imageStatusTagTypeMap[row.status]">
                {{ imageStatusTextMap[row.status] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="质量检测" width="120">
            <template #default="{ row }: { row: ImageFileItem }">
              <el-tag :type="imageQualityStatusTagTypeMap[row.qualityStatus]">
                {{ imageQualityStatusTextMap[row.qualityStatus] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="上传时间" min-width="170">
            <template #default="{ row }: { row: ImageFileItem }">
              {{ formatEmpty(row.uploadedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="190">
            <template #default="{ row }: { row: ImageFileItem }">
              <el-button link type="primary" :icon="View" @click.stop="openPreview(row)">
                预览
              </el-button>
              <el-button
                v-if="
                  canOverrideQuality &&
                  (row.qualityStatus === 'ERROR' || row.qualityStatus === 'NOT_CHECKED')
                "
                link
                type="warning"
                :icon="Refresh"
                :loading="qualityRetryLoadingId === row.id"
                @click.stop="retryQualityCheck(row)"
              >
                重新质检
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-alert
          v-if="selectedImage"
          :closable="false"
          :type="
            selectedImage.qualityStatus === 'FAIL'
              ? 'error'
              : selectedImage.qualityStatus === 'WARNING'
                ? 'warning'
                : 'info'
          "
          :title="`图像质量：${selectedImage.qualityStatus}，评分：${selectedImage.qualityScore ?? '-'}`"
          show-icon
          class="quality-alert"
        />
      </div>

      <div class="form-section">
        <div class="section-title">
          <h3>任务参数</h3>
          <p>优先级 1 最高，5 普通，9 最低。</p>
        </div>

        <div class="params-grid">
          <el-form-item label="任务类型" prop="taskType">
            <el-select v-model="form.taskType" class="wide-control" placeholder="请选择任务类型">
              <el-option
                v-for="(label, value) in taskTypeTextMap"
                :key="value"
                :label="label"
                :value="value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="优先级" prop="priority">
            <el-input-number
              v-model="form.priority"
              :max="9"
              :min="1"
              class="wide-control"
              controls-position="right"
            />
          </el-form-item>
        </div>
      </div>

      <div class="submit-bar">
        <el-button @click="goToTasks">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          提交任务
        </el-button>
      </div>
    </el-form>

    <ImagePreview
      v-model="previewVisible"
      :filename="previewState.filename"
      :image-url="previewState.imageUrl"
      :title="previewState.title"
    />
  </section>
</template>

<style scoped src="./TaskCreate.css"></style>
