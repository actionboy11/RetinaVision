import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from '@/plugins/element-plus-api'
import { computed, onMounted, reactive, ref, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getCaseDetail, getCasePage } from '@/api/case'
import { getCaseImages, getImagePreviewUrl, requestImageQualityCheck } from '@/api/image'
import { createTask } from '@/api/task'
import { useAuthStore } from '@/stores/auth'
import type { CaseDetail, CaseListItem } from '@/types/case'
import type { ImageFileItem } from '@/types/image'
import type { CreateTaskRequest, TaskType } from '@/types/task'
import { imageQualityStatusTextMap } from '@/utils/enums'

export interface TaskCreateForm {
  caseId?: number
  imageFileId?: number
  taskType?: TaskType
  priority: number
}

export interface UseTaskCreateOptions {
  /** 模板 ref 由 SFC 声明，编译器需要静态识别后才能回填表单实例 */
  formRef: Ref<FormInstance | undefined>
}

/**
 * 创建分析任务页的逻辑层：病例远程搜索、图像选择与质检复核、表单校验与任务提交。
 * 组件只保留展示模板与枚举/格式化映射。
 */
export const useTaskCreate = (options: UseTaskCreateOptions) => {
  const { formRef } = options

  const route = useRoute()
  const router = useRouter()
  const authStore = useAuthStore()

  const caseLoading = ref(false)
  const imageLoading = ref(false)
  const qualityRetryLoadingId = ref<number | null>(null)
  const submitLoading = ref(false)
  const caseOptions = ref<CaseListItem[]>([])
  const selectedCase = ref<CaseDetail | null>(null)
  const imageList = ref<ImageFileItem[]>([])
  const imageSelectionError = ref('')
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
    // imageFileId 由图像选择区的自定义提示校验（见 validateBeforeSubmit），
    // 不再依赖隐藏 form-item 触发 Element Plus 的必填规则。
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

  const selectedImage = computed(
    () => imageList.value.find((image) => image.id === form.imageFileId) || null,
  )
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
    imageSelectionError.value = ''
    selectedCase.value = null
    imageList.value = []

    if (!caseId) {
      return
    }

    await Promise.all([loadCaseDetail(caseId), loadImages(caseId)])
  }

  const getPreviewUrl = (image: ImageFileItem) => image.previewUrl || getImagePreviewUrl(image.id)

  const openPreview = (image: ImageFileItem) => {
    previewState.imageUrl = getPreviewUrl(image)
    previewState.filename = image.originalFilename || ''
    previewState.title = '图像预览'
    previewVisible.value = true
  }

  const isSelectableImage = (image: ImageFileItem) => image.status !== 'DELETED'

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
    imageSelectionError.value = ''
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
      imageSelectionError.value = '请选择图像'
      ElMessage.error('请选择图像')
      return false
    }

    if (selectedImage.value.status === 'DELETED') {
      imageSelectionError.value = '已删除图像不能创建任务'
      ElMessage.error('已删除图像不能创建任务')
      return false
    }

    imageSelectionError.value = ''

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
    if (
      selectedImage.value.qualityStatus === 'PASS' ||
      selectedImage.value.qualityStatus === 'WARNING'
    ) {
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
    await Promise.all([loadCaseDetail(queryCaseId), loadImages(queryCaseId, queryImageFileId)])
  }

  onMounted(() => {
    void initializeFromQuery()
  })

  return {
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
  }
}
