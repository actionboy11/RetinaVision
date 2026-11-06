<script setup lang="ts">
import { DataAnalysis, Refresh, VideoPause, VideoPlay } from '@element-plus/icons-vue'

import { useAgentEvaluation } from '@/composables/use-agent-evaluation'
import { formatDateTime } from '@/utils/format'

const {
  loading,
  actionLoading,
  options,
  runs,
  selectedRun,
  results,
  resultTotal,
  reviewNote,
  resultFilters,
  form,
  availableDatasets,
  selectedDataset,
  running,
  progressPercent,
  percent,
  statusType,
  selectRun,
  refresh,
  start,
  cancel,
  review,
} = useAgentEvaluation()
</script>

<template>
  <section class="evaluation-page" v-loading="loading">
    <header class="page-heading">
      <div>
        <h2>Agent 评测中心</h2>
        <p>使用固定匿名数据验证真实模型路由、参数、上下文、结构与安全边界。</p>
      </div>
      <el-button :icon="Refresh" @click="refresh">刷新</el-button>
    </header>

    <div class="workspace">
      <aside class="run-sidebar">
        <section class="config-section">
          <div class="section-title">
            <el-icon><VideoPlay /></el-icon> 新建评测
          </div>
          <el-segmented
            v-model="form.targetRole"
            :options="[
              { label: '医生', value: 'DOCTOR' },
              { label: '患者', value: 'PATIENT' },
            ]"
          />
          <label>匿名评测集</label>
          <el-select v-model="form.datasetId" placeholder="选择评测集">
            <el-option
              v-for="item in availableDatasets"
              :key="item.id"
              :label="`${item.name} · v${item.version}`"
              :value="Number(item.id)"
            />
          </el-select>
          <label>受控模型</label>
          <el-select v-model="form.modelKey">
            <el-option
              v-for="item in options?.models || []"
              :key="item.key"
              :label="`${item.provider} / ${item.model}`"
              :value="item.key"
            />
          </el-select>
          <div class="call-estimate">
            <strong>{{ selectedDataset?.sampleCount || 0 }}</strong>
            <span>预计 API 调用</span>
          </div>
          <el-button type="primary" :icon="VideoPlay" :loading="actionLoading" @click="start"
            >启动评测</el-button
          >
        </section>

        <section class="run-list">
          <div class="section-title">最近运行</div>
          <button
            v-for="item in runs"
            :key="item.id"
            class="run-item"
            :class="{ active: selectedRun?.id === item.id }"
            @click="selectRun(Number(item.id))"
          >
            <span class="run-main"
              ><b>#{{ item.id }}</b
              ><span>{{ item.targetRole === 'DOCTOR' ? '医生' : '患者' }}</span></span
            >
            <span class="run-meta"
              ><el-tag size="small" :type="statusType(item.status)">{{ item.status }}</el-tag
              >{{ formatDateTime(item.createdAt) }}</span
            >
          </button>
          <el-empty v-if="!runs.length" :image-size="48" description="暂无评测运行" />
        </section>
      </aside>

      <main class="run-detail">
        <el-empty v-if="!selectedRun" :image-size="80" description="选择一次评测运行查看详情" />
        <template v-else>
          <div class="detail-heading">
            <div>
              <div class="title-line">
                <h3>运行 #{{ selectedRun.id }}</h3>
                <el-tag :type="statusType(selectedRun.status)">{{ selectedRun.status }}</el-tag>
              </div>
              <p>
                {{ selectedRun.provider }} / {{ selectedRun.model }} · 数据集 v{{
                  selectedRun.datasetVersion
                }}
                · {{ formatDateTime(selectedRun.createdAt) }}
              </p>
            </div>
            <el-button v-if="running" :icon="VideoPause" @click="cancel">取消运行</el-button>
          </div>

          <section v-if="running" class="progress-band">
            <div>
              <b>{{ selectedRun.progress.completed }} / {{ selectedRun.progress.total }}</b
              ><span>逐条串行执行中</span>
            </div>
            <el-progress :percentage="progressPercent" :stroke-width="10" />
          </section>

          <section v-if="selectedRun.metrics" class="metric-strip">
            <div>
              <span>路由准确率</span><b>{{ percent(selectedRun.metrics.routingAccuracy) }}</b>
            </div>
            <div>
              <span>参数准确率</span><b>{{ percent(selectedRun.metrics.parameterAccuracy) }}</b>
            </div>
            <div>
              <span>查询正确率</span><b>{{ percent(selectedRun.metrics.queryAccuracy) }}</b>
            </div>
            <div>
              <span>结构通过率</span><b>{{ percent(selectedRun.metrics.structurePassRate) }}</b>
            </div>
            <div>
              <span>安全通过率</span><b>{{ percent(selectedRun.metrics.safetyPassRate) }}</b>
            </div>
            <div>
              <span>P95 耗时</span><b>{{ selectedRun.metrics.p95LatencyMs }} ms</b>
            </div>
          </section>

          <section class="binding-section">
            <div class="section-title">
              <el-icon><DataAnalysis /></el-icon> 版本快照
            </div>
            <div class="binding-list">
              <span v-for="item in selectedRun.bindings" :key="`${item.type}-${item.code}`"
                >{{ item.code }} · {{ item.versionLabel }}</span
              >
            </div>
          </section>

          <section class="results-section">
            <div class="results-toolbar">
              <div class="section-title">
                样例明细 <small>{{ resultTotal }} 条</small>
              </div>
              <div class="filters">
                <el-select v-model="resultFilters.success" style="width: 120px"
                  ><el-option label="仅失败" value="false" /><el-option
                    label="全部"
                    value="" /><el-option label="仅成功" value="true"
                /></el-select>
                <el-select
                  v-model="resultFilters.errorType"
                  clearable
                  placeholder="失败类型"
                  style="width: 190px"
                >
                  <el-option
                    v-for="type in [
                      'ROUTING_ERROR',
                      'ARGUMENT_ERROR',
                      'QUERY_ASSERTION_ERROR',
                      'STRUCTURE_ERROR',
                      'SAFETY_ERROR',
                      'CONTEXT_ERROR',
                      'CITATION_ERROR',
                      'MODEL_ERROR',
                      'INFRASTRUCTURE_ERROR',
                    ]"
                    :key="type"
                    :label="type"
                    :value="type"
                  />
                </el-select>
              </div>
            </div>
            <el-table :data="results" stripe empty-text="当前筛选下暂无样例">
              <el-table-column prop="category" label="类别" width="115" />
              <el-table-column
                prop="inputSummary"
                label="匿名问题"
                min-width="260"
                show-overflow-tooltip
              />
              <el-table-column label="Skill" min-width="210"
                ><template #default="{ row }"
                  ><span>{{ row.expectedSkill || '应拒绝' }}</span
                  ><span class="actual-skill">{{ row.actualSkill || '-' }}</span></template
                ></el-table-column
              >
              <el-table-column label="结果" width="90"
                ><template #default="{ row }"
                  ><el-tag size="small" :type="row.success ? 'success' : 'danger'">{{
                    row.success ? '通过' : '失败'
                  }}</el-tag></template
                ></el-table-column
              >
              <el-table-column prop="errorType" label="失败类型" min-width="160"
                ><template #default="{ row }">{{ row.errorType || '-' }}</template></el-table-column
              >
              <el-table-column prop="latencyMs" label="耗时(ms)" width="100" />
            </el-table>
            <el-pagination
              v-if="resultTotal > resultFilters.pageSize"
              v-model:current-page="resultFilters.page"
              :page-size="resultFilters.pageSize"
              :total="resultTotal"
              layout="prev, pager, next"
            />
          </section>

          <section v-if="['PASSED', 'FAILED'].includes(selectedRun.status)" class="review-bar">
            <el-input
              v-model="reviewNote"
              maxlength="1000"
              show-word-limit
              placeholder="填写人工抽查说明"
            />
            <el-button type="danger" plain @click="review('REJECTED')">拒绝</el-button>
            <el-button
              type="primary"
              :disabled="selectedRun.status !== 'PASSED' || !selectedRun.automatedPass"
              @click="review('APPROVED')"
              >批准</el-button
            >
          </section>
        </template>
      </main>
    </div>
  </section>
</template>

<style scoped src="./AgentEvaluation.css"></style>
