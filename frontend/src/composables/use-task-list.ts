import { ElMessage, ElMessageBox } from '@/plugins/element-plus-api'
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { cancelTask, getTaskPage, retryTask } from '@/api/task'
import type { TaskListItem, TaskListQuery } from '@/types/task'

/** 任务列表页的逻辑层：查询/分页、重试与取消。 */
export const useTaskList = () => {
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
  const canCancel = (row: TaskListItem) => row.status === 'CREATED' || row.status === 'WAITING'

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
    if (!canRetry(row)) return

    try {
      await ElMessageBox.confirm(`确认重试任务 ${row.taskNo} 吗？`, '重试任务', {
        confirmButtonText: '重试',
        cancelButtonText: '取消',
        type: 'warning',
      })
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
    if (!canCancel(row)) return

    try {
      await ElMessageBox.confirm(`确认取消任务 ${row.taskNo} 吗？`, '取消任务', {
        confirmButtonText: '取消任务',
        cancelButtonText: '返回',
        type: 'warning',
      })
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

  return {
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
  }
}
