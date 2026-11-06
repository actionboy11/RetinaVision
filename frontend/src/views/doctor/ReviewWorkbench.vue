<script setup lang="ts">
import {
  ArrowRight,
  Checked,
  DocumentChecked,
  Picture,
  RefreshRight,
  Timer,
  WarningFilled,
} from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getDoctorReviewReminders } from '@/api/doctor'
import { getTaskPage } from '@/api/task'
import type { DoctorReviewReminder } from '@/types/doctor'
import type { TaskListItem } from '@/types/task'
import { formatDateTime } from '@/utils/format'
import { ignoreHandledError } from '@/utils/async-action'

const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const tasks = ref<TaskListItem[]>([])
const reminder = ref<DoctorReviewReminder | null>(null)

const overdueTaskIds = computed(() => {
  return new Set(reminder.value?.latestOverdueItems.map((item) => item.taskId) ?? [])
})

const totalPendingCount = computed(() => {
  if (!reminder.value) {
    return 0
  }

  return reminder.value.pendingReviewCount + reminder.value.pendingReportCount
})

const totalOverdueCount = computed(() => {
  if (!reminder.value) {
    return 0
  }

  return reminder.value.overdueReviewCount + reminder.value.overdueReportCount
})

const load = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const [page, reminderSummary] = await Promise.all([
      getTaskPage({
        pageNo: 1,
        // 工作台是待办概览，首屏取最近 100 条；完整列表请使用「分析任务」页
        pageSize: 100,
        status: 'SUCCESS',
        taskType: 'VESSEL_SEGMENTATION',
      }),
      getDoctorReviewReminders(),
    ])
    tasks.value = page.records
    reminder.value = reminderSummary
  } catch (error) {
    loadError.value = '待办数据加载失败，请点击「刷新待办」重试'
    ignoreHandledError(error)
  } finally {
    loading.value = false
  }
}

const enterReview = (taskId: number) => {
  void router.push(`/tasks/${taskId}`)
}

const getRowClassName = ({ row }: { row: TaskListItem }) => {
  return overdueTaskIds.value.has(row.id) ? 'overdue-row' : ''
}

onMounted(load)
</script>

<template>
  <section class="review-workbench">
    <div class="page-heading">
      <div>
        <div class="heading-kicker">CLINICAL REVIEW</div>
        <h2>医生审核工作台</h2>
        <p>集中处理已完成的血管分割结果、人工修正、医生审核与报告签发。</p>
      </div>
      <el-button :icon="RefreshRight" :loading="loading" @click="load"> 刷新待办 </el-button>
    </div>

    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" />

    <div class="summary-strip">
      <div class="summary-item">
        <div class="summary-icon review-icon">
          <el-icon><Checked /></el-icon>
        </div>
        <div>
          <span>待审核</span>
          <strong>{{ reminder?.pendingReviewCount ?? 0 }}</strong>
        </div>
      </div>
      <div class="summary-item">
        <div class="summary-icon report-icon">
          <el-icon><DocumentChecked /></el-icon>
        </div>
        <div>
          <span>待签发报告</span>
          <strong>{{ reminder?.pendingReportCount ?? 0 }}</strong>
        </div>
      </div>
      <div class="summary-item overdue">
        <div class="summary-icon overdue-icon">
          <el-icon><WarningFilled /></el-icon>
        </div>
        <div>
          <span>超时待办</span>
          <strong>{{ totalOverdueCount }}</strong>
        </div>
      </div>
      <div class="summary-item threshold">
        <div class="summary-icon threshold-icon">
          <el-icon><Timer /></el-icon>
        </div>
        <div>
          <span>超时阈值</span>
          <strong>
            {{ reminder?.overdueThresholdMinutes ?? 120 }}
            <small>分钟</small>
          </strong>
        </div>
      </div>
    </div>

    <el-alert
      v-if="totalPendingCount > 0"
      class="workbench-alert"
      :type="totalOverdueCount > 0 ? 'warning' : 'info'"
      show-icon
      :closable="false"
      :title="`当前还有 ${totalPendingCount} 个医生待办，其中 ${totalOverdueCount} 个已超时。`"
    />

    <div class="review-queue">
      <div class="queue-heading">
        <div>
          <h3>审核队列</h3>
          <p>按任务完成时间展示当前可处理的血管分割结果</p>
        </div>
        <span class="queue-count">共 {{ tasks.length }} 条</span>
      </div>

      <el-table
        v-loading="loading"
        class="review-table"
        :data="tasks"
        empty-text="暂无可审核的血管分割结果"
        row-key="id"
        :row-class-name="getRowClassName"
      >
        <el-table-column label="病例号" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="case-number">{{ row.caseNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="图像" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="image-name">
              <el-icon><Picture /></el-icon>
              {{ row.originalFilename }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          prop="submittedByName"
          label="提交人"
          min-width="120"
          show-overflow-tooltip
        />
        <el-table-column label="任务完成时间" min-width="190">
          <template #default="{ row }">
            <span class="finished-at">{{ formatDateTime(row.finishedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="待办状态" min-width="120">
          <template #default="{ row }">
            <el-tag v-if="overdueTaskIds.has(row.id)" type="danger" effect="light">
              超时待办
            </el-tag>
            <el-tag v-else type="warning" effect="light">待处理</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="132" align="right">
          <template #default="{ row }">
            <el-button type="primary" plain size="small" @click="enterReview(row.id)">
              进入审核
              <el-icon class="action-arrow"><ArrowRight /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>

<style scoped src="./ReviewWorkbench.css"></style>
