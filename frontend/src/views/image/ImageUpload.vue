<script setup lang="ts">
import {
  Back,
  Delete,
  Plus,
  Refresh,
  UploadFilled,
  View,
} from '@element-plus/icons-vue'
import {
  ElMessage,
  ElMessageBox,
  type UploadRequestOptions,
} from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getCaseAnalysisTimeline, getCaseDetail } from '@/api/case'
import {
  deleteImage,
  getCaseImages,
  getImagePreviewBlob,
  uploadCaseImage,
} from '@/api/image'
import ImagePreview from '@/components/ImagePreview.vue'
import { useAuthStore } from '@/stores/auth'
import type { CaseDetail } from '@/types/case'
import type { CaseAnalysisTimeline } from '@/types/case'
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
import { formatDateTime, formatFileSize, formatNullable, formatPercent, formatScore } from '@/utils/format'

type UploadError = Parameters<UploadRequestOptions['onError']>[0]

const MAX_FILE_SIZE = 20 * 1024 * 1024
const ALLOWED_EXTENSIONS = ['png', 'jpg', 'jpeg', 'tif', 'tiff']
const ALLOWED_MIME_TYPES = [
  'image/png',
  'image/jpeg',
  'image/jpg',
  'image/tiff',
]

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const isDoctor = computed(() => authStore.user?.roleCode === 'DOCTOR')
const isPatient = computed(() => authStore.user?.roleCode === 'USER')
const canModifyImages = computed(() => isDoctor.value || (
  isPatient.value && caseDetail.value?.workflowStatus === 'DRAFT'
))

const loading = ref(false)
const caseLoading = ref(false)
const uploadLoading = ref(false)
const deleteLoadingId = ref<number | null>(null)
const errorMessage = ref('')
const caseDetail = ref<CaseDetail | null>(null)
const timelineLoading = ref(false)
const timelineError = ref('')
const timeline = ref<CaseAnalysisTimeline | null>(null)
const imageList = ref<ImageFileItem[]>([])
const imagePreviewUrls = reactive<Record<number, string>>({})
const previewVisible = ref(false)
const previewState = reactive({
  imageUrl: '',
  title: '图像预览',
  filename: '',
})

const caseId = computed(() => {
  const rawCaseId = Array.isArray(route.params.caseId)
    ? route.params.caseId[0]
    : route.params.caseId
  const parsedCaseId = Number(rawCaseId)
  return Number.isInteger(parsedCaseId) && parsedCaseId > 0 ? parsedCaseId : null
})

const validateCaseId = () => {
  if (caseId.value) {
    return true
  }

  ElMessage.error('病例 ID 不合法')
  void router.replace('/cases')
  return false
}

const loadCaseDetail = async () => {
  if (!caseId.value) {
    return
  }

  caseLoading.value = true

  try {
    caseDetail.value = await getCaseDetail(caseId.value)
  } catch (error) {
    const message = error instanceof Error ? error.message : '病例详情加载失败'
    ElMessage.error(message)
  } finally {
    caseLoading.value = false
  }
}

const clearImagePreviewUrls = () => {
  Object.values(imagePreviewUrls).forEach((url) => URL.revokeObjectURL(url))
  Object.keys(imagePreviewUrls).forEach((key) => delete imagePreviewUrls[Number(key)])
}

const loadImagePreview = async (image: ImageFileItem) => {
  const blob = await getImagePreviewBlob(image.id)
  imagePreviewUrls[image.id] = URL.createObjectURL(blob)
}

const loadImages = async () => {
  if (!caseId.value) {
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    const images = await getCaseImages(caseId.value)
    clearImagePreviewUrls()
    imageList.value = images
    await Promise.allSettled(images.map(loadImagePreview))
  } catch (error) {
    const message = error instanceof Error ? error.message : '图像列表加载失败'
    errorMessage.value = message
    imageList.value = []
  } finally {
    loading.value = false
  }
}

const loadTimeline = async () => {
  if (!caseId.value || !isDoctor.value) {
    return
  }

  timelineLoading.value = true
  timelineError.value = ''

  try {
    timeline.value = await getCaseAnalysisTimeline(caseId.value, {
      taskType: 'VESSEL_SEGMENTATION',
    })
  } catch (error) {
    timelineError.value = error instanceof Error ? error.message : '随访趋势加载失败'
    timeline.value = null
  } finally {
    timelineLoading.value = false
  }
}

const loadPageData = async () => {
  if (!validateCaseId()) {
    return
  }

  await Promise.all([loadCaseDetail(), loadImages(), isDoctor.value ? loadTimeline() : Promise.resolve()])
}

const timelineItems = computed(() => timeline.value?.items ?? [])

const maxVesselRatio = computed(() => {
  const values = timelineItems.value
    .map((item) => item.vesselAreaRatio)
    .filter((value): value is number => typeof value === 'number')
  return Math.max(...values, 0.01)
})

const vesselBarWidth = (ratio: number | null) => {
  if (typeof ratio !== 'number') {
    return '0%'
  }
  return `${Math.min(100, Math.max(4, (ratio / maxVesselRatio.value) * 100))}%`
}

const statusText = (value: string | null | undefined) => value || '-'

const getFileExtension = (filename: string) => {
  return filename.split('.').pop()?.toLowerCase() || ''
}

const isSupportedFile = (file: File) => {
  const extension = getFileExtension(file.name)
  return ALLOWED_EXTENSIONS.includes(extension) || ALLOWED_MIME_TYPES.includes(file.type)
}

const createUploadError = (message: string, options: UploadRequestOptions) => {
  const error = new Error(message) as UploadError
  error.status = 0
  error.method = 'POST'
  error.url = options.action
  return error
}

const beforeUpload = (file: File) => {
  if (!isSupportedFile(file)) {
    ElMessage.error('仅支持 png、jpg、jpeg、tif、tiff 格式')
    return false
  }

  if (file.size > MAX_FILE_SIZE) {
    ElMessage.error('单个文件不能超过 20MB')
    return false
  }

  return true
}

const handleUpload = async (options: UploadRequestOptions) => {
  if (!caseId.value) {
    options.onError(createUploadError('病例 ID 不合法', options))
    return
  }

  const file = options.file

  if (!beforeUpload(file)) {
    options.onError(createUploadError('文件不符合上传要求', options))
    return
  }

  uploadLoading.value = true

  try {
    const result = await uploadCaseImage(caseId.value, file)
    options.onSuccess(result)
    ElMessage.success('上传成功')
    await loadImages()
  } catch (error) {
    const message = error instanceof Error ? error.message : '上传失败'
    options.onError(createUploadError(message, options))
    ElMessage.error(message)
  } finally {
    uploadLoading.value = false
  }
}

const getPreviewUrl = (image: ImageFileItem) => {
  return imagePreviewUrls[image.id] || ''
}

const openPreview = (image: ImageFileItem) => {
  previewState.imageUrl = getPreviewUrl(image)
  previewState.filename = image.originalFilename || ''
  previewState.title = '图像预览'
  previewVisible.value = true
}

const handleDelete = async (image: ImageFileItem) => {
  try {
    await ElMessageBox.confirm(
      `确认删除图像 ${image.originalFilename || image.id} 吗？`,
      '删除图像',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  deleteLoadingId.value = image.id

  try {
    await deleteImage(image.id)
    ElMessage.success('删除成功')
    await loadImages()
  } catch (error) {
    const message = error instanceof Error ? error.message : '删除失败'
    ElMessage.error(message)
  } finally {
    deleteLoadingId.value = null
  }
}

const goBack = () => {
  void router.push('/cases')
}

const goToCreateTask = (image: ImageFileItem) => {
  if (!caseId.value) {
    return
  }

  void router.push({
    path: '/tasks/create',
    query: {
      caseId: caseId.value,
      imageFileId: image.id,
    },
  })
}

const formatImageSize = (image: ImageFileItem) => {
  if (!image.imageWidth || !image.imageHeight) {
    return '-'
  }

  return `${image.imageWidth} x ${image.imageHeight}`
}

onMounted(() => {
  void loadPageData()
})

onBeforeUnmount(() => {
  clearImagePreviewUrls()
})
</script>

<template>
  <section class="image-page">
    <div class="page-heading">
      <div>
        <h2>{{ isPatient ? '检查图像' : '图像管理' }}</h2>
        <p>{{ isPatient ? '在提交检查前上传眼底图像，系统会自动完成图像质量检测。' : '管理当前病例图像并开展医生侧分析任务。' }}</p>
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
        <div
          v-for="item in timelineItems"
          :key="item.taskId"
          class="trend-row"
        >
          <div class="trend-time">
            <strong>{{ formatDateTime(item.finishedAt || item.resultCreatedAt) }}</strong>
            <span>{{ taskTypeTextMap[item.taskType] || item.taskType }}</span>
          </div>
          <div class="trend-metrics">
            <div class="trend-metric">
              <span>图像质量</span>
              <strong>
                {{ item.qualityStatus || '-' }}
                {{ typeof item.qualityScore === 'number' ? `/ ${formatScore(item.qualityScore)}` : '' }}
              </strong>
            </div>
            <div class="trend-metric trend-vessel">
              <span>血管面积比例</span>
              <div class="trend-bar">
                <i :style="{ width: vesselBarWidth(item.vesselAreaRatio) }" />
              </div>
              <strong>{{ typeof item.vesselAreaRatio === 'number' ? formatPercent(item.vesselAreaRatio) : '-' }}</strong>
            </div>
            <div class="trend-metric">
              <span>审核/报告</span>
              <strong>{{ statusText(item.reviewStatus) }} / {{ statusText(item.reportStatus) }}</strong>
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
        drag
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          拖拽图像到此处，或 <em>点击上传</em>
        </div>
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
        <el-button :icon="Refresh" :loading="loading" plain @click="loadImages">
          刷新
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="imageList"
        border
        empty-text="暂无图像数据"
      >
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
            <el-tag :type="row.qualityStatus === 'PASS' ? 'success' : row.qualityStatus === 'WARNING' ? 'warning' : row.qualityStatus === 'FAIL' || row.qualityStatus === 'ERROR' ? 'danger' : 'info'">
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
            <el-button link type="primary" :icon="View" @click="openPreview(row)">
              预览
            </el-button>
            <el-button v-if="isDoctor" link type="primary" :icon="Plus" @click="goToCreateTask(row)">
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

<style scoped>
.image-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-heading,
.panel-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-heading h2,
.panel-title h3 {
  margin: 0;
  color: #111827;
}

.page-heading h2 {
  font-size: 22px;
  line-height: 30px;
}

.panel-title h3 {
  font-size: 16px;
  line-height: 24px;
}

.page-heading p,
.panel-title p {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 14px;
}

.case-panel,
.trend-panel,
.upload-panel,
.table-panel {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.case-panel {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  min-height: 96px;
  padding: 18px;
}

.case-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.case-item span {
  color: #6b7280;
  font-size: 12px;
}

.case-item strong {
  color: #111827;
  font-size: 14px;
  font-weight: 600;
}

.upload-panel,
.trend-panel,
.table-panel {
  padding: 16px;
}

.trend-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.trend-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.trend-row {
  display: grid;
  grid-template-columns: minmax(160px, 0.28fr) minmax(0, 1fr);
  gap: 16px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f9fafb;
}

.trend-time {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.trend-time strong,
.trend-metric strong {
  color: #111827;
  font-size: 14px;
  line-height: 22px;
}

.trend-time span,
.trend-metric span {
  color: #6b7280;
  font-size: 12px;
}

.trend-metrics {
  display: grid;
  grid-template-columns: minmax(120px, 0.8fr) minmax(180px, 1.4fr) minmax(140px, 1fr);
  gap: 12px;
}

.trend-metric {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
}

.trend-bar {
  overflow: hidden;
  width: 100%;
  height: 8px;
  border-radius: 999px;
  background: #e5e7eb;
}

.trend-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #1f7a8c;
}

.trend-note {
  margin-top: 2px;
}

.upload-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.upload-icon {
  color: #1f7a8c;
  font-size: 42px;
}

.upload-tip {
  color: #8a95a8;
  font-size: 12px;
}

.uploading-alert,
.error-alert {
  margin: 0;
}

.table-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.thumb {
  width: 72px;
  height: 72px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f9fafb;
}

.thumb-placeholder {
  display: grid;
  width: 72px;
  height: 72px;
  place-items: center;
  color: #9ca3af;
  font-size: 12px;
}

@media (max-width: 900px) {
  .trend-row,
  .trend-metrics {
    grid-template-columns: 1fr;
  }
}
</style>
