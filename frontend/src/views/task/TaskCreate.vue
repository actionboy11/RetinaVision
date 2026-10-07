<script setup lang="ts">
import { Back, Picture, Refresh, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

import { getCaseDetail, getCasePage } from '@/api/case'
import { getCaseImages, getImagePreviewUrl, requestImageQualityCheck } from '@/api/image'
import { createTask } from '@/api/task'
import ImagePreview from '@/components/ImagePreview.vue'
import type { CaseDetail, CaseListItem } from '@/types/case'
import type { ImageFileItem } from '@/types/image'
import type { CreateTaskRequest, TaskType } from '@/types/task'
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

interface TaskCreateForm {
  caseId?: number
  imageFileId?: number
  taskType?: TaskType
  priority: number
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const caseLoading = ref(false)
const imageLoading = ref(false)
const qualityRetryLoadingId = ref<number | null>(null)
const submitLoading = ref(false)
const caseOptions = ref<CaseListItem[]>([])
const selectedCase = ref<CaseDetail | null>(null)
const imageList = ref<ImageFileItem[]>([])
const previewVisible = ref(false)
const previewState = reactive({
  imageUrl: '',
  filename: '',
  title: '图像预览',
})

const form = reactive<TaskCreateForm>({
  caseId: undefined,
  imageFileId: undefined,
  taskType: 'VESSEL_SEGMENTATION',
  priority: 5,
})

const rules: FormRules<TaskCreateForm> = {
  caseId: [{ required: true, message: '请选择病例', trigger: 'change' }],
  imageFileId: [{ required: true, message: '请选择图像', trigger: 'change' }],
  taskType: [{ required: true, message: '请选择任务类型', trigger: 'change' }],
  priority: [
    { required: true, message: '请设置优先级', trigger: 'change' },
    {
      type: 'number',
      min: 1,
      max: 9,
      message: '优先级范围为 1-9',
      trigger: 'change',
    },
  ],
}

const selectedImage = computed(() => {
  return imageList.value.find((image) => image.id === form.imageFileId) || null
})
const canOverrideQuality = computed(() => authStore.user?.roleCode === 'DOCTOR')

const canBackToImages = computed(() => Boolean(form.caseId))

const parseQueryId = (value: unknown) => {
  const rawValue = Array.isArray(value) ? value[0] : value

  if (typeof rawValue !== 'string' && typeof rawValue !== 'number') {
    return null
  }

  const parsed = Number(rawValue)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

const loadCaseOptions = async (keyword = '') => {
  caseLoading.value = true

  try {
    const result = await getCasePage({
      pageNo: 1,
      pageSize: 20,
      keyword: keyword || undefined,
      status: 'ACTIVE',
    })
    caseOptions.value = result.records || []
  } catch (error) {
    const message = error instanceof Error ? error.message : '病例列表加载失败'
    ElMessage.error(message)
    caseOptions.value = []
  } finally {
    caseLoading.value = false
  }
}

const loadCaseDetail = async (caseId: number) => {
  caseLoading.value = true

  try {
    selectedCase.value = await getCaseDetail(caseId)
  } catch (error) {
    const message = error instanceof Error ? error.message : '病例详情加载失败'
    ElMessage.error(message)
    selectedCase.value = null
  } finally {
    caseLoading.value = false
  }
}

const loadImages = async (caseId: number, expectedImageId?: number | null) => {
  imageLoading.value = true

  try {
    imageList.value = await getCaseImages(caseId)

    if (expectedImageId) {
      const imageExists = imageList.value.some((image) => image.id === expectedImageId)

      if (imageExists) {
        form.imageFileId = expectedImageId
      } else {
        form.imageFileId = undefined
        ElMessage.warning('URL 中的图像不属于当前病例，请重新选择')
      }
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : '图像列表加载失败'
    ElMessage.error(message)
    imageList.value = []
  } finally {
    imageLoading.value = false
  }
}

const handleCaseChange = async (caseId?: number) => {
  form.imageFileId = undefined
  selectedCase.value = null
  imageList.value = []

  if (!caseId) {
    return
  }

  await Promise.all([loadCaseDetail(caseId), loadImages(caseId)])
}

const getPreviewUrl = (image: ImageFileItem) => {
  return image.previewUrl || getImagePreviewUrl(image.id)
}

const openPreview = (image: ImageFileItem) => {
  previewState.imageUrl = getPreviewUrl(image)
  previewState.filename = image.originalFilename || ''
  previewState.title = '图像预览'
  previewVisible.value = true
}

const isSelectableImage = (image: ImageFileItem) => {
  return image.status !== 'DELETED'
}

const getImageUnavailableReason = (image: ImageFileItem) => {
  if (image.status === 'DELETED') return '该图像已删除，不能创建任务'
  return ''
}

const selectImage = (image: ImageFileItem) => {
  if (!isSelectableImage(image)) {
    ElMessage.warning(getImageUnavailableReason(image))
    return
  }

  form.imageFileId = image.id
  void formRef.value?.validateField('imageFileId')
}

const retryQualityCheck = async (image: ImageFileItem) => {
  if (!canOverrideQuality.value || qualityRetryLoadingId.value !== null) return

  qualityRetryLoadingId.value = image.id
  try {
    const updatedImage = await requestImageQualityCheck(image.id)
    const index = imageList.value.findIndex((item) => item.id === image.id)
    if (index >= 0) imageList.value[index] = updatedImage
    if (form.imageFileId === image.id) form.imageFileId = undefined
    ElMessage.success('已重新发起图像质量检测，请稍后刷新查看结果')
  } catch (error) {
    const message = error instanceof Error ? error.message : '重新发起图像质量检测失败'
    ElMessage.error(message)
  } finally {
    qualityRetryLoadingId.value = null
  }
}

const refreshImages = async () => {
  if (!form.caseId) return
  await loadImages(form.caseId)
}

const validateBeforeSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)

  if (!valid) {
    return false
  }

  if (!form.caseId) {
    ElMessage.error('请选择病例')
    return false
  }

  if (!form.imageFileId || !selectedImage.value) {
    ElMessage.error('请选择图像')
    return false
  }

  if (selectedImage.value.status === 'DELETED') {
    ElMessage.error('已删除图像不能创建任务')
    return false
  }

  if (!form.taskType) {
    ElMessage.error('请选择任务类型')
    return false
  }

  if (form.priority < 1 || form.priority > 9) {
    ElMessage.error('优先级范围为 1-9')
    return false
  }

  return true
}

const confirmQualityReference = async () => {
  if (!selectedImage.value || form.taskType !== 'VESSEL_SEGMENTATION') return true
  if (selectedImage.value.qualityStatus === 'PASS' || selectedImage.value.qualityStatus === 'WARNING') {
    return true
  }

  const statusText = imageQualityStatusTextMap[selectedImage.value.qualityStatus]
  const scoreText = selectedImage.value.qualityScore ?? '无有效评分'
  try {
    await ElMessageBox.confirm(
      `当前图像质量状态为“${statusText}”，评分为“${scoreText}”。质量检测仅供参考，请确认是否基于人工判断继续血管分割。`,
      '确认继续分析',
      {
        type: 'warning',
        confirmButtonText: '继续创建任务',
        cancelButtonText: '返回检查图像',
      },
    )
    return true
  } catch {
    return false
  }
}

const handleSubmit = async () => {
  if (submitLoading.value) {
    return
  }

  const valid = await validateBeforeSubmit()

  if (!valid || !form.caseId || !form.imageFileId || !form.taskType) {
    return
  }

  if (!(await confirmQualityReference())) return

  submitLoading.value = true

  try {
    const payload: CreateTaskRequest = {
      caseId: form.caseId,
      imageFileId: form.imageFileId,
      taskType: form.taskType,
      priority: form.priority,
    }
    const result = await createTask(payload)
    ElMessage.success(result.message || '任务提交成功')

    if (result.id) {
      await router.push(`/tasks/${result.id}`)
    } else {
      await router.push('/tasks')
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : '任务提交失败'
    ElMessage.error(message)
  } finally {
    submitLoading.value = false
  }
}

const goToTasks = () => {
  void router.push('/tasks')
}

const goToCases = () => {
  void router.push('/cases')
}

const goToImages = () => {
  if (!form.caseId) {
    return
  }

  void router.push(`/cases/${form.caseId}/images`)
}

const initializeFromQuery = async () => {
  const queryCaseId = parseQueryId(route.query.caseId)
  const queryImageFileId = parseQueryId(route.query.imageFileId)

  if (route.query.caseId && !queryCaseId) {
    ElMessage.warning('URL 中的病例参数不合法，请重新选择')
  }

  if (route.query.imageFileId && !queryImageFileId) {
    ElMessage.warning('URL 中的图像参数不合法，请重新选择')
  }

  await loadCaseOptions()

  if (!queryCaseId) {
    return
  }

  form.caseId = queryCaseId
  await Promise.all([
    loadCaseDetail(queryCaseId),
    loadImages(queryCaseId, queryImageFileId),
  ])
}

onMounted(() => {
  void initializeFromQuery()
})
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
        <el-button v-if="canBackToImages" plain @click="goToImages">
          返回图像管理
        </el-button>
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

        <el-form-item prop="imageFileId" class="hidden-form-item">
          <el-input v-model="form.imageFileId" />
        </el-form-item>

        <el-empty
          v-if="form.caseId && !imageLoading && imageList.length === 0"
          description="当前病例暂无图像，请先上传图像"
        >
          <el-button type="primary" :icon="Picture" @click="goToImages">
            去上传图像
          </el-button>
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
              {{ row.imageWidth && row.imageHeight ? `${row.imageWidth} x ${row.imageHeight}` : '-' }}
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
                v-if="canOverrideQuality && (row.qualityStatus === 'ERROR' || row.qualityStatus === 'NOT_CHECKED')"
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
          :type="selectedImage.qualityStatus === 'FAIL' ? 'error' : selectedImage.qualityStatus === 'WARNING' ? 'warning' : 'info'"
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
            <el-select
              v-model="form.taskType"
              class="wide-control"
              placeholder="请选择任务类型"
            >
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

<style scoped>
.task-create-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-heading,
.section-title,
.submit-bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.heading-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.page-heading h2,
.section-title h3 {
  margin: 0;
  color: #111827;
}

.page-heading h2 {
  font-size: 22px;
  line-height: 30px;
}

.section-title h3 {
  font-size: 16px;
  line-height: 24px;
}

.page-heading p,
.section-title p {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 14px;
}

.form-section,
.submit-bar {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 16px;
  padding: 18px;
}

.wide-control {
  width: min(480px, 100%);
}

.case-option {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.case-option small {
  color: #6b7280;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  min-height: 96px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.info-item span {
  color: #6b7280;
  font-size: 12px;
}

.info-item strong {
  color: #111827;
  font-size: 14px;
  font-weight: 600;
}

.hidden-form-item {
  display: none;
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

.params-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 16px;
}

.submit-bar {
  align-items: center;
  justify-content: flex-end;
  padding: 16px 18px;
}
</style>
