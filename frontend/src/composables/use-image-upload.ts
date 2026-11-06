import { ElMessage, ElMessageBox, type UploadRequestOptions } from '@/plugins/element-plus-api'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getCaseAnalysisTimeline, getCaseDetail } from '@/api/case'
import { deleteImage, getCaseImages, getImagePreviewBlob, uploadCaseImage } from '@/api/image'
import { useAuthStore } from '@/stores/auth'
import type { CaseAnalysisTimeline, CaseDetail } from '@/types/case'
import type { ImageFileItem } from '@/types/image'

type UploadError = Parameters<UploadRequestOptions['onError']>[0]

const MAX_FILE_SIZE = 20 * 1024 * 1024
const ALLOWED_EXTENSIONS = ['png', 'jpg', 'jpeg', 'tif', 'tiff']
const ALLOWED_MIME_TYPES = ['image/png', 'image/jpeg', 'image/jpg', 'image/tiff']

/**
 * 图像管理页的逻辑层：病例详情、图像列表与 Blob 预览、随访趋势、上传/删除与权限判定。
 * 组件只保留展示模板与枚举/格式化映射。
 */
export const useImageUpload = () => {
  const route = useRoute()
  const router = useRouter()
  const authStore = useAuthStore()
  const isDoctor = computed(() => authStore.user?.roleCode === 'DOCTOR')
  const isPatient = computed(() => authStore.user?.roleCode === 'USER')

  const loading = ref(false)
  const caseLoading = ref(false)
  // 用计数器而非布尔标志，多文件并发上传时先完成的文件不会提前关闭 loading
  const uploadingCount = ref(0)
  const uploadLoading = computed(() => uploadingCount.value > 0)
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

  const canModifyImages = computed(
    () => isDoctor.value || (isPatient.value && caseDetail.value?.workflowStatus === 'DRAFT'),
  )

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

    await Promise.all([
      loadCaseDetail(),
      loadImages(),
      isDoctor.value ? loadTimeline() : Promise.resolve(),
    ])
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

  const getFileExtension = (filename: string) => filename.split('.').pop()?.toLowerCase() || ''

  const ACCEPTED_FILES = ALLOWED_EXTENSIONS.map((ext) => `.${ext}`).join(',')

  /** 返回错误原因，通过时返回空字符串。扩展名与 MIME 需同时满足，避免伪造 MIME 绕过校验。 */
  const validateFile = (file: File): string => {
    const extension = getFileExtension(file.name)
    const mimeAllowed = file.type === '' || ALLOWED_MIME_TYPES.includes(file.type)
    if (!ALLOWED_EXTENSIONS.includes(extension) || !mimeAllowed) {
      return '仅支持 png、jpg、jpeg、tif、tiff 格式'
    }
    if (file.size > MAX_FILE_SIZE) return '单个文件不能超过 20MB'
    return ''
  }

  const createUploadError = (message: string, options: UploadRequestOptions) => {
    const error = new Error(message) as UploadError
    error.status = 0
    error.method = 'POST'
    error.url = options.action
    return error
  }

  const beforeUpload = (file: File) => {
    const reason = validateFile(file)
    if (reason) {
      ElMessage.error(reason)
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

    // el-upload 的 before-upload 已负责提示；此处静默复核，防止自定义 http-request 绕过校验
    const validationError = validateFile(file)
    if (validationError) {
      options.onError(createUploadError(validationError, options))
      return
    }

    uploadingCount.value += 1
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
      uploadingCount.value -= 1
    }
  }

  const getPreviewUrl = (image: ImageFileItem) => imagePreviewUrls[image.id] || ''

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

  return {
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
  }
}
