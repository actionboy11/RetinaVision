<script setup lang="ts">
import { CircleClose, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { cancelTask, getTaskPage, retryTask } from '@/api/task'
import StatusTag from '@/components/StatusTag.vue'
import type { TaskListItem, TaskListQuery } from '@/types/task'
import { taskStatusTextMap, taskTypeTextMap } from '@/utils/enums'
import { formatDateTime, formatEmpty, formatRetryCount } from '@/utils/format'

const router = useRouter()

const loading = ref(false)
const retryLoadingId = ref<number | null>(null)
const cancelLoadingId = ref<number | null>(null)
const errorMessage = ref('')
const tableData = ref<TaskListItem[]>([])
const total = ref(0)

const query = reactive<TaskListQuery>({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  status: undefined,
  taskType: undefined,
})

const canRetry = (row: TaskListItem) => row.status === 'FAILED'

const canCancel = (row: TaskListItem) =>
  row.status === 'CREATED' || row.status === 'WAITING'

const buildParams = (): TaskListQuery => ({
  pageNo: query.pageNo,
  pageSize: query.pageSize,
  keyword: query.keyword?.trim() || undefined,
  status: query.status,
  taskType: query.taskType,
})

const loadTasks = async () => {
  loading.value = true
  errorMessage.value = ''

  try {
    const result = await getTaskPage(buildParams())
    tableData.value = result.records || []
    total.value = result.total || 0
    query.pageNo = result.pageNo || query.pageNo
    query.pageSize = result.pageSize || query.pageSize
  } catch (error) {
    const message = error instanceof Error ? error.message : '任务列表加载失败'
    errorMessage.value = message
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  query.pageNo = 1
  void loadTasks()
}

const handleReset = () => {
  query.pageNo = 1
  query.keyword = ''
  query.status = undefined
  query.taskType = undefined
  void loadTasks()
}

const handlePageChange = (pageNo: number) => {
  query.pageNo = pageNo
  void loadTasks()
}

const handlePageSizeChange = (pageSize: number) => {
  query.pageNo = 1
  query.pageSize = pageSize
  void loadTasks()
}

const goToCreate = () => {
  void router.push('/tasks/create')
}

const goToDetail = (row: TaskListItem) => {
  void router.push(`/tasks/${row.id}`)
}

const handleRetry = async (row: TaskListItem) => {
  if (!canRetry(row)) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认重试任务 ${row.taskNo} 吗？`,
      '重试任务',
      {
        confirmButtonText: '重试',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  retryLoadingId.value = row.id

  try {
    await retryTask(row.id)
    ElMessage.success('任务已重新提交')
    await loadTasks()
  } catch (error) {
    const message = error instanceof Error ? error.message : '任务重试失败'
    ElMessage.error(message)
  } finally {
    retryLoadingId.value = null
  }
}

const handleCancel = async (row: TaskListItem) => {
  if (!canCancel(row)) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认取消任务 ${row.taskNo} 吗？`,
      '取消任务',
      {
        confirmButtonText: '取消任务',
        cancelButtonText: '返回',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  cancelLoadingId.value = row.id

  try {
    await cancelTask(row.id)
    ElMessage.success('任务已取消')
    await loadTasks()
  } catch (error) {
    const message = error instanceof Error ? error.message : '任务取消失败'
    ElMessage.error(message)
  } finally {
    cancelLoadingId.value = null
  }
}

onMounted(() => {
  void loadTasks()
})
</script>

<template>
  <section class="task-list-page">
    <div class="page-heading">
      <div>
        <h2>分析任务</h2>
        <p>查看 AI 分析任务状态，支持失败重试和等待任务取消。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="goToCreate">
        创建任务
      </el-button>
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
        empty-text="暂无任务数据"
      >
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

<style scoped>
.task-list-page {
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
</style>
