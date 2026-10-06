<script setup lang="ts">
import { Delete, Edit, Picture, Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  createCase,
  assignCaseDoctor,
  deleteCase,
  getCaseDetail,
  getCasePage,
  submitCase,
  updateCase,
  withdrawCase,
} from '@/api/case'
import { createOfflinePatient, getMyPatientProfile, listDoctorPatients } from '@/api/patient'
import { getDoctorOptions, type DoctorOption } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
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
import {
  caseStatusTagTypeMap,
  caseStatusTextMap,
  caseWorkflowStatusTagTypeMap,
  caseWorkflowStatusTextMap,
  eyeSideTextMap,
  patientGenderTextMap,
} from '@/utils/enums'
import { formatAge, formatNullable } from '@/utils/format'

type DialogMode = 'create' | 'edit'

interface CaseFormModel {
  patientId?: number
  patientAge?: number
  patientGender: PatientGender | ''
  eyeSide: EyeSide | ''
  diagnosisNote: string
  status: Exclude<CaseStatus, 'DELETED'>
  assignedDoctorId?: number
}

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
const formRef = ref<FormInstance>()
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

const dialogTitle = computed(() =>
  dialogMode.value === 'create' ? '新建病例' : '编辑病例',
)
const patientMode = computed(() => isPatient.value ? String(route.query.mode || 'cases') : 'doctor')
const pageTitle = computed(() => {
  if (!isPatient.value) return '患者管理'
  if (patientMode.value === 'progress') return '检查进度'
  if (patientMode.value === 'reports') return '正式报告'
  if (patientMode.value === 'new') return '新建检查'
  return '我的病例'
})
const pageDescription = computed(() => {
  if (!isPatient.value) return '管理本人负责的匿名患者病例并开展分析与审核。'
  if (patientMode.value === 'progress') return '选择一条检查查看图像质量、医生处理状态和签发进度。'
  if (patientMode.value === 'reports') return '正式报告仅在负责医生完成审核并签发后提供下载。'
  return '创建检查申请、上传眼底图像并查看医生处理进度。'
})

const rules: FormRules<CaseFormModel> = {
  patientId: [{
    validator: (_rule, value, callback) => {
      if (isDoctor.value && !value) callback(new Error('请选择匿名患者'))
      else callback()
    },
    trigger: 'change',
  }],
  patientAge: [
    {
      type: 'number',
      min: 0,
      max: 120,
      message: '年龄范围为 0-120',
      trigger: 'change',
    },
  ],
  patientGender: [
    { required: true, message: '请选择性别', trigger: 'change' },
  ],
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
    await ElMessageBox.confirm('提交后将由负责医生处理，关键资料和负责医生将不能再修改。', '提交检查', {
      confirmButtonText: '确认提交', cancelButtonText: '取消', type: 'warning',
    })
  } catch { return }
  workflowLoadingId.value = row.id
  try {
    await submitCase(row.id)
    ElMessage.success('检查申请已提交')
    await loadCases()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交失败')
  } finally { workflowLoadingId.value = null }
}

const handleWithdrawCase = async (row: CaseListItem) => {
  try {
    await ElMessageBox.confirm('撤回将结束本次检查申请；如需重新检查，请新建一条申请。', '撤回检查', {
      confirmButtonText: '确认撤回', cancelButtonText: '取消', type: 'warning',
    })
  } catch { return }
  workflowLoadingId.value = row.id
  try {
    await withdrawCase(row.id)
    ElMessage.success('检查申请已撤回')
    await loadCases()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '撤回失败')
  } finally { workflowLoadingId.value = null }
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
  if (needsDoctorSelection.value) {
    const [profile, doctorList] = await Promise.all([getMyPatientProfile(), getDoctorOptions()])
    myPatientProfile.value = profile
    doctors.value = doctorList
  } else if (isDoctor.value) {
    patients.value = await listDoctorPatients()
  }
  if (patientMode.value === 'new') openCreateDialog()
})

watch(() => route.query.mode, (mode) => {
  if (isPatient.value && mode === 'new') openCreateDialog()
})
</script>

<template>
  <section class="case-page">
    <div class="page-heading">
      <div>
        <h2>{{ pageTitle }}</h2>
        <p>{{ pageDescription }}</p>
      </div>
      <el-button v-if="!isPatient || patientMode === 'cases' || patientMode === 'new'" type="primary" :icon="Plus" @click="openCreateDialog">
        {{ isPatient ? '新建检查' : '新建病例' }}
      </el-button>
    </div>

    <div class="filter-panel">
      <el-form :inline="true" :model="query" class="filter-form">
        <el-form-item label="关键词">
          <el-input
            v-model.trim="query.keyword"
            clearable
            placeholder="请输入病例编号或匿名患者编号"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="病例状态">
          <el-select v-model="query.status" clearable placeholder="全部状态">
            <el-option
              v-for="(label, value) in caseStatusTextMap"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="眼别">
          <el-select v-model="query.eyeSide" clearable placeholder="全部眼别">
            <el-option
              v-for="(label, value) in eyeSideTextMap"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :icon="Search"
            :loading="loading"
            @click="handleSearch"
          >
            查询
          </el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      class="error-alert"
      show-icon
      type="error"
    />

    <div class="table-panel">
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        empty-text="暂无病例数据"
      >
        <el-table-column label="病例编号" min-width="150" prop="caseNo" />
        <el-table-column label="匿名患者编号" min-width="160" prop="patientNo" />
        <el-table-column label="年龄" min-width="80">
          <template #default="{ row }: { row: CaseListItem }">
            {{ formatAge(row.patientAge) }}
          </template>
        </el-table-column>
        <el-table-column label="性别" min-width="90">
          <template #default="{ row }: { row: CaseListItem }">
            {{ patientGenderTextMap[row.patientGender] }}
          </template>
        </el-table-column>
        <el-table-column label="眼别" min-width="90">
          <template #default="{ row }: { row: CaseListItem }">
            {{ eyeSideTextMap[row.eyeSide] }}
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }: { row: CaseListItem }">
            <el-tag :type="caseStatusTagTypeMap[row.status]">
              {{ caseStatusTextMap[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="检查进度" min-width="120">
          <template #default="{ row }: { row: CaseListItem }">
            <el-tag :type="caseWorkflowStatusTagTypeMap[row.workflowStatus]">
              {{ caseWorkflowStatusTextMap[row.workflowStatus] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建人" min-width="110">
          <template #default="{ row }: { row: CaseListItem }">
            {{ formatNullable(row.createdByName) }}
          </template>
        </el-table-column>
        <el-table-column label="负责医生" min-width="150">
          <template #default="{ row }: { row: CaseListItem }">
            <span>{{ row.assignedDoctorName || '待分配' }}</span>
            <small v-if="row.assignedDoctorProfessionalNo" class="doctor-no">
              {{ row.assignedDoctorProfessionalNo }}
            </small>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }: { row: CaseListItem }">
            {{ formatNullable(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="170">
          <template #default="{ row }: { row: CaseListItem }">
            {{ formatNullable(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" min-width="360">
          <template #default="{ row }: { row: CaseListItem }">
            <el-button link type="primary" :icon="Picture" @click="goToImages(row)">
              {{ isPatient ? '检查图像' : '图像管理' }}
            </el-button>
            <el-button v-if="isPatient" link type="primary" @click="goToProgress(row)">
              查看进度
            </el-button>
            <el-button v-if="isPatient && row.workflowStatus === 'DRAFT'" link type="success" :loading="workflowLoadingId === row.id" @click="handleSubmitCase(row)">
              提交检查
            </el-button>
            <el-button v-if="isPatient && row.workflowStatus === 'SUBMITTED'" link type="warning" :loading="workflowLoadingId === row.id" @click="handleWithdrawCase(row)">
              撤回
            </el-button>
            <el-button v-if="isDoctor" link type="primary" @click="goToCreateTask(row)">
              创建任务
            </el-button>
            <el-button v-if="isDoctor || row.workflowStatus === 'DRAFT'" link type="primary" :icon="Edit" @click="openEditDialog(row)">
              编辑
            </el-button>
            <el-button
              v-if="isPatient && row.workflowStatus === 'DRAFT'"
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

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handlePageSizeChange"
        />
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="560px"
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
      >
        <el-form-item label="匿名患者" prop="patientId">
          <el-input v-if="isPatient" :model-value="myPatientProfile?.patientNo || '系统正在生成'" disabled />
          <div v-else class="patient-selector">
            <el-select v-model="form.patientId" class="full-width" filterable placeholder="请选择本人负责的匿名患者" :disabled="dialogMode === 'edit'">
              <el-option v-for="patient in patients" :key="patient.id" :label="patient.patientNo" :value="patient.id" />
            </el-select>
            <el-button v-if="dialogMode === 'create'" :icon="Plus" @click="handleCreateOfflinePatient">新建线下患者</el-button>
          </div>
        </el-form-item>
        <el-form-item label="年龄" prop="patientAge">
          <el-input-number
            v-model="form.patientAge"
            :max="120"
            :min="0"
            controls-position="right"
            placeholder="请输入年龄"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="性别" prop="patientGender">
          <el-select
            v-model="form.patientGender"
            class="full-width"
            placeholder="请选择性别"
          >
            <el-option
              v-for="(label, value) in patientGenderTextMap"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="眼别" prop="eyeSide">
          <el-select
            v-model="form.eyeSide"
            class="full-width"
            placeholder="请选择眼别"
          >
            <el-option
              v-for="(label, value) in eyeSideTextMap"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="dialogMode === 'edit'" label="病例状态" prop="status">
          <el-select v-model="form.status" class="full-width" placeholder="请选择状态">
            <el-option label="正常" value="ACTIVE" />
            <el-option label="已归档" value="ARCHIVED" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isPatient" label="负责医生" prop="assignedDoctorId">
          <el-select
            v-model="form.assignedDoctorId"
            class="full-width"
            filterable
            placeholder="请选择负责医生"
          >
            <el-option
              v-for="doctor in doctors"
              :key="doctor.id"
              :label="doctor.professionalNo ? `${doctor.displayName}（${doctor.professionalNo}）` : doctor.displayName"
              :value="doctor.id"
            />
          </el-select>
          <div class="assignment-hint">病例产生分析任务后将不能更换负责医生。</div>
        </el-form-item>
        <el-form-item label="诊断备注" prop="diagnosisNote">
          <el-input
            v-model.trim="form.diagnosisNote"
            maxlength="512"
            placeholder="请输入诊断备注"
            rows="4"
            show-word-limit
            type="textarea"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.case-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-heading h2 {
  margin: 0;
  color: #111827;
  font-size: 22px;
  line-height: 30px;
}

.page-heading p {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 14px;
}

.filter-panel,
.table-panel {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.filter-panel {
  padding: 18px 18px 0;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
}

.error-alert {
  margin: 0;
}

.table-panel {
  padding: 16px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}

.full-width {
  width: 100%;
}

.patient-selector {
  display: grid;
  width: 100%;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.doctor-no,
.assignment-hint {
  display: block;
  color: #909399;
  font-size: 12px;
}
</style>
