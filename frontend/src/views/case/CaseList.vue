<script setup lang="ts">
import { Delete, Edit, Picture, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { type FormInstance } from '@/plugins/element-plus-api'
import { ref } from 'vue'

import { useCaseList } from '@/composables/use-case-list'
import type { CaseListItem } from '@/types/case'
import {
  caseStatusTagTypeMap,
  caseStatusTextMap,
  caseWorkflowStatusTagTypeMap,
  caseWorkflowStatusTextMap,
  eyeSideTextMap,
  patientGenderTextMap,
} from '@/utils/enums'
import { formatAge, formatNullable } from '@/utils/format'

// 模板 ref 必须由 SFC 自身声明，编译器才能静态识别并回填表单实例
const formRef = ref<FormInstance>()

const {
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
} = useCaseList({ formRef })
</script>

<template>
  <section class="case-page">
    <div class="page-heading">
      <div>
        <h2>{{ pageTitle }}</h2>
        <p>{{ pageDescription }}</p>
      </div>
      <el-button
        v-if="!isPatient || patientMode === 'cases' || patientMode === 'new'"
        type="primary"
        :icon="Plus"
        @click="openCreateDialog"
      >
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
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">
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
      <el-table v-loading="loading" :data="tableData" border empty-text="暂无病例数据">
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
            <el-button
              v-if="isPatient && row.workflowStatus === 'DRAFT'"
              link
              type="success"
              :loading="workflowLoadingId === row.id"
              @click="handleSubmitCase(row)"
            >
              提交检查
            </el-button>
            <el-button
              v-if="isPatient && row.workflowStatus === 'SUBMITTED'"
              link
              type="warning"
              :loading="workflowLoadingId === row.id"
              @click="handleWithdrawCase(row)"
            >
              撤回
            </el-button>
            <el-button v-if="isDoctor" link type="primary" @click="goToCreateTask(row)">
              创建任务
            </el-button>
            <el-button
              v-if="isDoctor || row.workflowStatus === 'DRAFT'"
              link
              type="primary"
              :icon="Edit"
              @click="openEditDialog(row)"
            >
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="匿名患者" prop="patientId">
          <el-input
            v-if="isPatient"
            :model-value="myPatientProfile?.patientNo || '系统正在生成'"
            disabled
          />
          <div v-else class="patient-selector">
            <el-select
              v-model="form.patientId"
              class="full-width"
              filterable
              placeholder="请选择本人负责的匿名患者"
              :disabled="dialogMode === 'edit'"
            >
              <el-option
                v-for="patient in patients"
                :key="patient.id"
                :label="patient.patientNo"
                :value="patient.id"
              />
            </el-select>
            <el-button
              v-if="dialogMode === 'create'"
              :icon="Plus"
              @click="handleCreateOfflinePatient"
              >新建线下患者</el-button
            >
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
          <el-select v-model="form.patientGender" class="full-width" placeholder="请选择性别">
            <el-option
              v-for="(label, value) in patientGenderTextMap"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="眼别" prop="eyeSide">
          <el-select v-model="form.eyeSide" class="full-width" placeholder="请选择眼别">
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
              :label="
                doctor.professionalNo
                  ? `${doctor.displayName}（${doctor.professionalNo}）`
                  : doctor.displayName
              "
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
        <el-button @click="closeDialog">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit"> 确定 </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped src="./CaseList.css"></style>
