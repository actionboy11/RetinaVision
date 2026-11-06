import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from '@/plugins/element-plus-api'
import { computed, nextTick, onMounted, reactive, ref, watch, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  assignCaseDoctor,
  createCase,
  deleteCase,
  getCaseDetail,
  getCasePage,
  submitCase,
  updateCase,
  withdrawCase,
} from '@/api/case'
import { createOfflinePatient, getMyPatientProfile, listDoctorPatients } from '@/api/patient'
import { getDoctorOptions } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import type { DoctorOption } from '@/types/auth'
import type {
  CaseListItem,
  CaseListQuery,
  CaseStatus,
  CreateCaseRequest,
  EyeSide,
  PatientGender,
  UpdateCaseRequest,
} from '@/types/case'
import type { PatientProfile } from '@/types/patient'
import { ignoreHandledError } from '@/utils/async-action'

export type DialogMode = 'create' | 'edit'

export interface CaseFormModel {
  patientId?: number
  patientAge?: number
  patientGender: PatientGender | ''
  eyeSide: EyeSide | ''
  diagnosisNote: string
  status: Exclude<CaseStatus, 'DELETED'>
  assignedDoctorId?: number
}

export interface UseCaseListOptions {
  /** 模板 ref 由 SFC 声明，编译器需要静态识别后才能回填表单实例 */
  formRef: Ref<FormInstance | undefined>
}

/**
 * 病例列表页的逻辑层：查询/分页、新建编辑表单、删除、检查流程流转与基础资料加载。
 * 组件只保留表格/表单模板与展示映射。
 */
export const useCaseList = (options: UseCaseListOptions) => {
  const { formRef } = options

  const router = useRouter()
  const route = useRoute()
  const authStore = useAuthStore()

  const loading = ref(false)
  const submitLoading = ref(false)
  const deleteLoadingId = ref<number | null>(null)
  const workflowLoadingId = ref<number | null>(null)
  const errorMessage = ref('')
  const tableData = ref<CaseListItem[]>([])
  const total = ref(0)
  const dialogVisible = ref(false)
  const dialogMode = ref<DialogMode>('create')
  const editingCaseId = ref<number | null>(null)
  const doctors = ref<DoctorOption[]>([])
  const patients = ref<PatientProfile[]>([])
  const myPatientProfile = ref<PatientProfile | null>(null)
  const originalAssignedDoctorId = ref<number | null>(null)
  const isPatient = computed(() => authStore.user?.roleCode === 'USER')
  const isDoctor = computed(() => authStore.user?.roleCode === 'DOCTOR')
  const needsDoctorSelection = computed(() => isPatient.value)

  const query = reactive<CaseListQuery>({
    pageNo: 1,
    pageSize: 10,
    keyword: '',
    status: undefined,
    eyeSide: undefined,
  })

  const form = reactive<CaseFormModel>({
    patientId: undefined,
    patientAge: undefined,
    patientGender: '',
    eyeSide: '',
    diagnosisNote: '',
    status: 'ACTIVE',
    assignedDoctorId: undefined,
  })

  const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新建病例' : '编辑病例'))
  const patientMode = computed(() =>
    isPatient.value ? String(route.query.mode || 'cases') : 'doctor',
  )
  const pageTitle = computed(() => {
    if (!isPatient.value) return '患者管理'
    if (patientMode.value === 'progress') return '检查进度'
    if (patientMode.value === 'reports') return '正式报告'
    if (patientMode.value === 'new') return '新建检查'
    return '我的病例'
  })
  const pageDescription = computed(() => {
    if (!isPatient.value) return '管理本人负责的匿名患者病例并开展分析与审核。'
    if (patientMode.value === 'progress')
      return '选择一条检查查看图像质量、医生处理状态和签发进度。'
    if (patientMode.value === 'reports') return '正式报告仅在负责医生完成审核并签发后提供下载。'
    return '创建检查申请、上传眼底图像并查看医生处理进度。'
  })

  const rules: FormRules<CaseFormModel> = {
    patientId: [
      {
        validator: (_rule, value, callback) => {
          if (isDoctor.value && !value) callback(new Error('请选择匿名患者'))
          else callback()
        },
        trigger: 'change',
      },
    ],
    patientAge: [
      {
        type: 'number',
        min: 0,
        max: 120,
        message: '年龄范围为 0-120',
        trigger: 'change',
      },
    ],
    patientGender: [{ required: true, message: '请选择性别', trigger: 'change' }],
    eyeSide: [{ required: true, message: '请选择眼别', trigger: 'change' }],
    diagnosisNote: [
      {
        max: 512,
        message: '诊断备注不能超过 512 个字符',
        trigger: 'blur',
      },
    ],
    status: [{ required: true, message: '请选择病例状态', trigger: 'change' }],
    assignedDoctorId: [
      {
        validator: (_rule, value, callback) => {
          if (needsDoctorSelection.value && !value) callback(new Error('请选择负责医生'))
          else callback()
        },
        trigger: 'change',
      },
    ],
  }

  const buildListParams = (): CaseListQuery => ({
    pageNo: query.pageNo,
    pageSize: query.pageSize,
    keyword: query.keyword?.trim() || undefined,
    status: query.status,
    eyeSide: query.eyeSide,
  })

  const loadCases = async () => {
    loading.value = true
    errorMessage.value = ''

    try {
      const result = await getCasePage(buildListParams())
      tableData.value = result.records || []
      total.value = result.total || 0
      query.pageNo = result.pageNo || query.pageNo
      query.pageSize = result.pageSize || query.pageSize
    } catch (error) {
      const message = error instanceof Error ? error.message : '病例列表加载失败'
      errorMessage.value = message
      tableData.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  const handleSearch = () => {
    query.pageNo = 1
    void loadCases()
  }

  const handleReset = () => {
    query.pageNo = 1
    query.keyword = ''
    query.status = undefined
    query.eyeSide = undefined
    void loadCases()
  }

  const handlePageChange = (pageNo: number) => {
    query.pageNo = pageNo
    void loadCases()
  }

  const handlePageSizeChange = (pageSize: number) => {
    query.pageNo = 1
    query.pageSize = pageSize
    void loadCases()
  }

  const resetForm = () => {
    editingCaseId.value = null
    form.patientId = isPatient.value ? myPatientProfile.value?.id : undefined
    form.patientAge = undefined
    form.patientGender = ''
    form.eyeSide = ''
    form.diagnosisNote = ''
    form.status = 'ACTIVE'
    form.assignedDoctorId = undefined
    originalAssignedDoctorId.value = null
    void nextTick(() => formRef.value?.clearValidate())
  }

  const closeDialog = () => {
    dialogVisible.value = false
  }

  const openCreateDialog = () => {
    dialogMode.value = 'create'
    resetForm()
    dialogVisible.value = true
  }

  const openEditDialog = async (row: CaseListItem) => {
    dialogMode.value = 'edit'
    resetForm()
    dialogVisible.value = true

    try {
      const detail = await getCaseDetail(row.id)
      editingCaseId.value = detail.id
      form.patientId = detail.patientId
      form.patientAge = detail.patientAge ?? undefined
      form.patientGender = detail.patientGender
      form.eyeSide = detail.eyeSide
      form.diagnosisNote = detail.diagnosisNote || ''
      form.status = detail.status === 'DELETED' ? 'ARCHIVED' : detail.status
      form.assignedDoctorId = detail.assignedDoctorId ?? undefined
      originalAssignedDoctorId.value = detail.assignedDoctorId
    } catch (error) {
      const message = error instanceof Error ? error.message : '病例详情加载失败'
      ElMessage.error(message)
      dialogVisible.value = false
    }
  }

  const buildCreatePayload = (): CreateCaseRequest => ({
    patientId: isDoctor.value ? form.patientId : undefined,
    patientAge: form.patientAge,
    patientGender: form.patientGender as PatientGender,
    eyeSide: form.eyeSide as EyeSide,
    diagnosisNote: form.diagnosisNote.trim() || undefined,
    assignedDoctorId: form.assignedDoctorId,
  })

  const buildUpdatePayload = (): UpdateCaseRequest => ({
    patientAge: form.patientAge,
    patientGender: form.patientGender as PatientGender,
    eyeSide: form.eyeSide as EyeSide,
    diagnosisNote: form.diagnosisNote.trim() || undefined,
    status: form.status,
  })

  const handleSubmit = async () => {
    if (submitLoading.value) {
      return
    }

    const valid = await formRef.value?.validate().catch(() => false)

    if (!valid) {
      return
    }

    submitLoading.value = true

    try {
      if (dialogMode.value === 'create') {
        await createCase(buildCreatePayload())
        ElMessage.success('创建成功')
        dialogVisible.value = false
        query.pageNo = 1
      } else if (editingCaseId.value) {
        await updateCase(editingCaseId.value, buildUpdatePayload())
        if (
          needsDoctorSelection.value &&
          form.assignedDoctorId &&
          form.assignedDoctorId !== originalAssignedDoctorId.value
        ) {
          await assignCaseDoctor(editingCaseId.value, form.assignedDoctorId)
        }
        ElMessage.success('更新成功')
        dialogVisible.value = false
      }

      await loadCases()
    } catch (error) {
      const message = error instanceof Error ? error.message : '保存失败'
      ElMessage.error(message)
    } finally {
      submitLoading.value = false
    }
  }

  const handleDelete = async (row: CaseListItem) => {
    try {
      await ElMessageBox.confirm(
        `确认删除病例 ${row.caseNo} 吗？该操作需要后端按软删除规则处理。`,
        '删除病例',
        {
          confirmButtonText: '删除',
          cancelButtonText: '取消',
          type: 'warning',
        },
      )
    } catch {
      return
    }

    deleteLoadingId.value = row.id

    try {
      await deleteCase(row.id)
      ElMessage.success('删除成功')

      if (tableData.value.length === 1 && query.pageNo && query.pageNo > 1) {
        query.pageNo -= 1
      }

      await loadCases()
    } catch (error) {
      const message = error instanceof Error ? error.message : '删除失败'
      ElMessage.error(message)
    } finally {
      deleteLoadingId.value = null
    }
  }

  const goToImages = (row: CaseListItem) => {
    void router.push(`/cases/${row.id}/images`)
  }

  const goToCreateTask = (row: CaseListItem) => {
    void router.push({
      path: '/tasks/create',
      query: {
        caseId: row.id,
      },
    })
  }

  const goToProgress = (row: CaseListItem) => {
    void router.push(`/cases/${row.id}/progress`)
  }

  const handleSubmitCase = async (row: CaseListItem) => {
    try {
      await ElMessageBox.confirm(
        '提交后将由负责医生处理，关键资料和负责医生将不能再修改。',
        '提交检查',
        {
          confirmButtonText: '确认提交',
          cancelButtonText: '取消',
          type: 'warning',
        },
      )
    } catch {
      return
    }
    workflowLoadingId.value = row.id
    try {
      await submitCase(row.id)
      ElMessage.success('检查申请已提交')
      await loadCases()
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '提交失败')
    } finally {
      workflowLoadingId.value = null
    }
  }

  const handleWithdrawCase = async (row: CaseListItem) => {
    try {
      await ElMessageBox.confirm(
        '撤回将结束本次检查申请；如需重新检查，请新建一条申请。',
        '撤回检查',
        {
          confirmButtonText: '确认撤回',
          cancelButtonText: '取消',
          type: 'warning',
        },
      )
    } catch {
      return
    }
    workflowLoadingId.value = row.id
    try {
      await withdrawCase(row.id)
      ElMessage.success('检查申请已撤回')
      await loadCases()
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '撤回失败')
    } finally {
      workflowLoadingId.value = null
    }
  }

  const handleCreateOfflinePatient = async () => {
    try {
      const profile = await createOfflinePatient()
      patients.value.unshift(profile)
      form.patientId = profile.id
      ElMessage.success(`已创建匿名患者 ${profile.patientNo}`)
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '创建匿名患者失败')
    }
  }

  onMounted(async () => {
    await loadCases()
    try {
      if (needsDoctorSelection.value) {
        const [profile, doctorList] = await Promise.all([getMyPatientProfile(), getDoctorOptions()])
        myPatientProfile.value = profile
        doctors.value = doctorList
      } else if (isDoctor.value) {
        patients.value = await listDoctorPatients()
      }
    } catch (error) {
      ignoreHandledError(error)
      ElMessage.warning('基础资料加载失败，创建病例时下拉选项可能为空，请刷新页面重试')
    }
    if (patientMode.value === 'new') openCreateDialog()
  })

  watch(
    () => route.query.mode,
    (mode) => {
      if (isPatient.value && mode === 'new') openCreateDialog()
    },
  )

  return {
    loading,
    submitLoading,
    deleteLoadingId,
    workflowLoadingId,
    errorMessage,
    tableData,
    total,
    dialogVisible,
    dialogMode,
    doctors,
    patients,
    myPatientProfile,
    isPatient,
    isDoctor,
    query,
    form,
    rules,
    dialogTitle,
    patientMode,
    pageTitle,
    pageDescription,
    handleSearch,
    handleReset,
    handlePageChange,
    handlePageSizeChange,
    resetForm,
    closeDialog,
    openCreateDialog,
    openEditDialog,
    handleSubmit,
    handleDelete,
    goToImages,
    goToCreateTask,
    goToProgress,
    handleSubmitCase,
    handleWithdrawCase,
    handleCreateOfflinePatient,
  }
}
