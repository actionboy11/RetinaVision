<script setup lang="ts">
import { Back, Download, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  downloadPatientSignedReport,
  getPatientCaseProgress,
  getPatientSignedReports,
} from '@/api/case'
import type { PatientCaseProgress, PatientSignedReport } from '@/types/case'
import {
  caseWorkflowStatusTagTypeMap,
  caseWorkflowStatusTextMap,
  taskStatusTagTypeMap,
  taskStatusTextMap,
} from '@/utils/enums'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const downloadingVersion = ref<number | null>(null)
const progress = ref<PatientCaseProgress | null>(null)
const reports = ref<PatientSignedReport[]>([])

const caseId = computed(() => Number(route.params.caseId))
const qualityText: Record<string, string> = {
  PASS: '图像质量通过',
  WARNING: '图像质量需关注',
  FAIL: '图像质量未通过，请重新上传清晰图像',
  ERROR: '图像质量检测异常，请联系负责医生',
  NOT_CHECKED: '等待图像质量检测',
}

const load = async () => {
  if (!Number.isInteger(caseId.value) || caseId.value <= 0) {
    ElMessage.error('病例 ID 不合法')
    await router.replace('/cases')
    return
  }
  loading.value = true
  try {
    const [progressResult, reportResult] = await Promise.all([
      getPatientCaseProgress(caseId.value),
      getPatientSignedReports(caseId.value),
    ])
    progress.value = progressResult
    reports.value = reportResult
  } finally {
    loading.value = false
  }
}

const download = async (report: PatientSignedReport) => {
  downloadingVersion.value = report.version
  try {
    const blob = await downloadPatientSignedReport(caseId.value, report.resultId, report.version)
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `RetinaVision-${progress.value?.caseNo || caseId.value}-V${report.version}.pdf`
    anchor.click()
    URL.revokeObjectURL(url)
  } finally {
    downloadingVersion.value = null
  }
}

onMounted(load)
</script>

<template>
  <section v-loading="loading" class="progress-page">
    <header class="page-heading">
      <div>
        <el-button link :icon="Back" @click="router.push('/cases')">返回我的检查</el-button>
        <h2>检查进度</h2>
        <p>这里只展示面向患者的处理进度、图像质量提示和已签发正式报告。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </header>

    <template v-if="progress">
      <div class="summary-band">
        <div><span>病例编号</span><strong>{{ progress.caseNo }}</strong></div>
        <div><span>匿名患者编号</span><strong>{{ progress.patientNo }}</strong></div>
        <div><span>检查状态</span><el-tag :type="caseWorkflowStatusTagTypeMap[progress.workflowStatus]">{{ caseWorkflowStatusTextMap[progress.workflowStatus] }}</el-tag></div>
        <div><span>更新时间</span><strong>{{ formatDateTime(progress.updatedAt) }}</strong></div>
      </div>

      <div class="content-grid">
        <section class="progress-panel">
          <h3>当前进度</h3>
          <el-steps direction="vertical" :active="progress.workflowStatus === 'COMPLETED' ? 4 : progress.workflowStatus === 'IN_REVIEW' ? 3 : progress.workflowStatus === 'SUBMITTED' ? 2 : 1" finish-status="success">
            <el-step title="准备检查资料" description="填写检查信息并上传眼底图像" />
            <el-step title="提交医生处理" description="负责医生收到检查申请" />
            <el-step title="医生分析与审核" description="医生复核图像、分析结果和报告内容" />
            <el-step title="正式报告已签发" description="签发后可在右侧下载 PDF" />
          </el-steps>
        </section>

        <section class="detail-panel">
          <h3>检查概况</h3>
          <dl>
            <div><dt>已上传图像</dt><dd>{{ progress.imageCount }} 张</dd></div>
            <div><dt>图像质量</dt><dd>{{ qualityText[progress.qualityStatus] || progress.qualityStatus }}</dd></div>
            <div><dt>医生处理</dt><dd><el-tag v-if="progress.analysisStatus" :type="taskStatusTagTypeMap[progress.analysisStatus]">{{ taskStatusTextMap[progress.analysisStatus] }}</el-tag><span v-else>尚未开始</span></dd></div>
            <div><dt>正式报告</dt><dd>{{ progress.signedReportCount }} 份</dd></div>
          </dl>
          <el-alert v-if="progress.qualityStatus === 'FAIL' || progress.qualityStatus === 'ERROR'" title="当前图像可能无法支持后续分析，请返回检查图像页面重新上传，或联系负责医生。" type="warning" show-icon :closable="false" />
        </section>
      </div>

      <section class="report-panel">
        <h3>正式报告</h3>
        <el-empty v-if="reports.length === 0" description="医生尚未签发正式报告" />
        <el-table v-else :data="reports" border>
          <el-table-column label="版本" width="100"><template #default="{ row }">V{{ row.version }}</template></el-table-column>
          <el-table-column prop="signerName" label="签发医生" min-width="140" />
          <el-table-column label="签发时间" min-width="180"><template #default="{ row }">{{ formatDateTime(row.signedAt) }}</template></el-table-column>
          <el-table-column label="操作" width="130"><template #default="{ row }"><el-button link type="primary" :icon="Download" :loading="downloadingVersion === row.version" @click="download(row)">下载 PDF</el-button></template></el-table-column>
        </el-table>
      </section>
    </template>
  </section>
</template>

<style scoped>
.progress-page { display: flex; flex-direction: column; gap: 16px; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; }
.page-heading h2 { margin: 10px 0 0; font-size: 22px; }
.page-heading p { margin: 6px 0 0; color: #6b7280; }
.summary-band, .progress-panel, .detail-panel, .report-panel { border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; }
.summary-band { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); padding: 18px 22px; }
.summary-band div { display: flex; flex-direction: column; gap: 8px; }
.summary-band span, dt { color: #6b7280; font-size: 13px; }
.content-grid { display: grid; grid-template-columns: minmax(280px, 0.8fr) minmax(360px, 1.2fr); gap: 16px; }
.progress-panel, .detail-panel, .report-panel { padding: 20px; }
h3 { margin: 0 0 18px; font-size: 16px; }
dl { margin: 0 0 18px; }
dl div { display: flex; justify-content: space-between; padding: 12px 0; border-bottom: 1px solid #f0f2f5; }
dd { margin: 0; color: #111827; }
@media (max-width: 900px) { .summary-band, .content-grid { grid-template-columns: 1fr; } }
</style>
