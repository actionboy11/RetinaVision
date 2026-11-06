<script setup lang="ts">
import { CircleClose, Plus, Refresh, Search, View } from '@element-plus/icons-vue'

import StatusTag from '@/components/StatusTag.vue'
import { useTaskList } from '@/composables/use-task-list'
import type { TaskListItem } from '@/types/task'
import { taskStatusTextMap, taskTypeTextMap } from '@/utils/enums'
import { formatDateTime, formatEmpty, formatRetryCount } from '@/utils/format'

const {
  loading,
  retryLoadingId,
  cancelLoadingId,
  errorMessage,
  tableData,
  total,
  query,
  canRetry,
  canCancel,
  handleSearch,
  handleReset,
  handlePageChange,
  handlePageSizeChange,
  goToCreate,
  goToDetail,
  handleRetry,
  handleCancel,
} = useTaskList()
</script>

<template>
  <section class="task-list-page">
    <div class="page-heading">
      <div>
        <h2>分析任务</h2>
        <p>查看 AI 分析任务状态，支持失败重试和等待任务取消。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="goToCreate"> 创建任务 </el-button>
    </div>

    <div class="filter-panel">
      <el-form :inline="true" :model="query" class="filter-form">
        <el-form-item label="关键词">
          <el-input
            v-model.trim="query.keyword"
            clearable
            placeholder="请输入任务编号或病例编号"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="任务状态">
          <el-select v-model="query.status" clearable placeholder="全部状态">
            <el-option
              v-for="(label, value) in taskStatusTextMap"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任务类型">
          <el-select v-model="query.taskType" clearable placeholder="全部类型">
            <el-option
              v-for="(label, value) in taskTypeTextMap"
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
      <el-table v-loading="loading" :data="tableData" border empty-text="暂无任务数据">
        <el-table-column label="任务编号" min-width="160" prop="taskNo" />
        <el-table-column label="病例编号" min-width="150" prop="caseNo" />
        <el-table-column label="原始文件名" min-width="170">
          <template #default="{ row }: { row: TaskListItem }">
            {{ formatEmpty(row.originalFilename) }}
          </template>
        </el-table-column>
        <el-table-column label="任务类型" min-width="130">
          <template #default="{ row }: { row: TaskListItem }">
            {{ taskTypeTextMap[row.taskType] }}
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="110">
          <template #default="{ row }: { row: TaskListItem }">
            <StatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="优先级" min-width="90" prop="priority" />
        <el-table-column label="重试次数" min-width="110">
          <template #default="{ row }: { row: TaskListItem }">
            {{ formatRetryCount(row.retryCount, row.maxRetryCount) }}
          </template>
        </el-table-column>
        <el-table-column label="提交人" min-width="110">
          <template #default="{ row }: { row: TaskListItem }">
            {{ formatEmpty(row.submittedByName) }}
          </template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="170">
          <template #default="{ row }: { row: TaskListItem }">
            {{ formatDateTime(row.submittedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="开始时间" min-width="170">
          <template #default="{ row }: { row: TaskListItem }">
            {{ formatDateTime(row.startedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="完成时间" min-width="170">
          <template #default="{ row }: { row: TaskListItem }">
            {{ formatDateTime(row.finishedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="170">
          <template #default="{ row }: { row: TaskListItem }">
            {{ formatDateTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" min-width="230">
          <template #default="{ row }: { row: TaskListItem }">
            <el-button link type="primary" :icon="View" @click="goToDetail(row)">
              查看详情
            </el-button>
            <el-button
              v-if="canRetry(row)"
              link
              type="warning"
              :loading="retryLoadingId === row.id"
              @click="handleRetry(row)"
            >
              重试
            </el-button>
            <el-button
              v-if="canCancel(row)"
              link
              type="danger"
              :icon="CircleClose"
              :loading="cancelLoadingId === row.id"
              @click="handleCancel(row)"
            >
              取消
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
  </section>
</template>

<style scoped src="./TaskList.css"></style>
