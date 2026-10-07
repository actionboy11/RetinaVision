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

const router = useRouter()
const loading = ref(false)
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
  try {
    const [page, reminderSummary] = await Promise.all([
      getTaskPage({
        pageNo: 1,
        pageSize: 100,
        status: 'SUCCESS',
        taskType: 'VESSEL_SEGMENTATION',
      }),
      getDoctorReviewReminders(),
    ])
    tasks.value = page.records
    reminder.value = reminderSummary
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
      <el-button :icon="RefreshRight" :loading="loading" @click="load">
        刷新待办
      </el-button>
    </div>

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
        <el-table-column prop="submittedByName" label="提交人" min-width="120" show-overflow-tooltip />
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

<style scoped>
.review-workbench {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 1440px;
  margin: 0 auto;
}

.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-heading h2 {
  margin: 2px 0 0;
  color: #111827;
  font-size: 24px;
  line-height: 34px;
  letter-spacing: 0;
}

.page-heading p {
  margin: 4px 0 0;
  color: #6b7280;
  font-size: 14px;
  line-height: 22px;
}

.heading-kicker {
  color: #1f7a8c;
  font-size: 11px;
  font-weight: 700;
  line-height: 16px;
  letter-spacing: 0;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  overflow: hidden;
  border: 1px solid #dfe5ec;
  border-radius: 8px;
  background: #ffffff;
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 92px;
  padding: 16px 18px;
  border-left: 1px solid #e8edf2;
}

.summary-item:first-child {
  border-left: 0;
}

.summary-icon {
  display: grid;
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 7px;
  font-size: 19px;
}

.review-icon {
  background: #e8f4f6;
  color: #176b79;
}

.report-icon {
  background: #eaf1fb;
  color: #315f9c;
}

.overdue-icon {
  background: #fdefef;
  color: #c43d3d;
}

.threshold-icon {
  background: #f2f4f7;
  color: #596579;
}

.summary-item span {
  display: block;
  color: #647084;
  font-size: 13px;
  line-height: 20px;
}

.summary-item strong {
  display: block;
  margin-top: 2px;
  color: #111827;
  font-size: 25px;
  line-height: 32px;
  letter-spacing: 0;
}

.summary-item strong small {
  color: #5f6b7d;
  font-size: 13px;
  font-weight: 500;
}

.summary-item.overdue strong {
  color: #c73636;
}

.workbench-alert {
  margin: 0;
  border: 1px solid #f2dfbb;
  border-radius: 6px;
}

.review-queue {
  overflow: hidden;
  border: 1px solid #dfe5ec;
  border-radius: 8px;
  background: #ffffff;
}

.queue-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 15px 18px 13px;
  border-bottom: 1px solid #e8edf2;
}

.queue-heading h3 {
  margin: 0;
  color: #172033;
  font-size: 16px;
  line-height: 24px;
  letter-spacing: 0;
}

.queue-heading p {
  margin: 2px 0 0;
  color: #7a8596;
  font-size: 12px;
  line-height: 18px;
}

.queue-count {
  flex: 0 0 auto;
  color: #667085;
  font-size: 13px;
}

.review-table {
  --el-table-border-color: #edf0f4;
  --el-table-header-bg-color: #f7f9fb;
  --el-table-header-text-color: #536074;
  --el-table-row-hover-bg-color: #f3f8fa;
  --el-table-text-color: #3f4a5d;
}

.review-table :deep(th.el-table__cell) {
  height: 44px;
  padding: 0;
  font-size: 13px;
  font-weight: 600;
}

.review-table :deep(td.el-table__cell) {
  height: 54px;
  padding: 0;
}

.review-table :deep(.overdue-row td.el-table__cell) {
  background: #fffafa;
}

.review-table :deep(.overdue-row td.el-table__cell:first-child) {
  box-shadow: inset 3px 0 0 #d94b4b;
}

.case-number {
  display: block;
  overflow: hidden;
  color: #263b59;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.image-name {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  color: #4b586b;
  white-space: nowrap;
}

.image-name .el-icon {
  flex: 0 0 auto;
  color: #8290a3;
}

.finished-at {
  color: #5c6879;
  font-variant-numeric: tabular-nums;
}

.action-arrow {
  margin-left: 5px;
}

@media (max-width: 960px) {
  .page-heading {
    flex-direction: column;
  }

  .summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .summary-item:nth-child(odd) {
    border-left: 0;
  }

  .summary-item:nth-child(n + 3) {
    border-top: 1px solid #e8edf2;
  }
}

@media (max-width: 640px) {
  .summary-strip {
    grid-template-columns: 1fr;
  }

  .summary-item {
    min-height: 78px;
    border-top: 1px solid #e8edf2;
    border-left: 0;
  }

  .summary-item:first-child {
    border-top: 0;
  }

  .queue-heading {
    align-items: flex-start;
  }
}
</style>
